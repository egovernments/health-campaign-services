package org.egov.transformer.transformationservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.egov.common.models.household.AdditionalFields;
import org.egov.common.models.household.Field;
import org.egov.common.models.household.Household;
import org.egov.common.models.household.HouseholdMember;
import org.egov.common.models.household.HouseholdMemberSearch;
import org.egov.common.models.project.Project;
import org.egov.common.models.project.ProjectBeneficiary;
import org.egov.transformer.config.TransformerProperties;
import org.egov.transformer.models.boundary.BoundaryHierarchyResult;
import org.egov.transformer.models.downstream.HouseholdMemberIndexV1;
import org.egov.transformer.models.downstream.ProjectInfo;
import org.egov.transformer.producer.Producer;
import org.egov.transformer.service.*;
import org.egov.transformer.utils.CommonUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.stream.Collectors;

import static org.egov.transformer.Constants.*;
import static org.egov.transformer.Constants.GENDER;

@Slf4j
@Component
public class HouseholdMemberTransformationService {
    private final TransformerProperties transformerProperties;
    private final Producer producer;
    private final CommonUtils commonUtils;
    private final IndividualService individualService;
    private final UserService userService;
    private final HouseholdService householdService;
    private final ObjectMapper objectMapper;
    private final ProjectService projectService;
    private final BoundaryService boundaryService;

    public HouseholdMemberTransformationService(TransformerProperties transformerProperties, Producer producer, CommonUtils commonUtils, IndividualService individualService, UserService userService, HouseholdService householdService, ObjectMapper objectMapper, ProjectService projectService, BoundaryService boundaryService) {
        this.transformerProperties = transformerProperties;
        this.producer = producer;
        this.commonUtils = commonUtils;
        this.individualService = individualService;
        this.userService = userService;
        this.householdService = householdService;
        this.objectMapper = objectMapper;
        this.projectService = projectService;
        this.boundaryService = boundaryService;
    }

    public void transform(List<HouseholdMember> householdMemberList) {
        log.info("transforming for HHM id's {}", householdMemberList.stream()
                .map(HouseholdMember::getId).collect(Collectors.toList()));
        push(householdMemberList.stream()
                .map(householdMember -> transform(householdMember, null))
                .collect(Collectors.toList()));
    }

    /**
     * Re-indexes the household members of newly created / updated project beneficiaries with the beneficiary's
     * project. Covers members that were created (and transformed) before their beneficiary existed; members created
     * after the beneficiary resolve it themselves in {@link #transform(HouseholdMember, String)}.
     */
    public void transformForBeneficiaries(List<ProjectBeneficiary> projectBeneficiaries) {
        Map<String, HouseholdMember> householdMembers = new LinkedHashMap<>();
        Map<String, String> memberIdVsProjectId = new HashMap<>();
        for (ProjectBeneficiary beneficiary : projectBeneficiaries) {
            if (Boolean.TRUE.equals(beneficiary.getIsDeleted()) || StringUtils.isBlank(beneficiary.getProjectId())) {
                continue;
            }
            for (HouseholdMember householdMember : searchMembersOfBeneficiary(beneficiary)) {
                if (Boolean.TRUE.equals(householdMember.getIsDeleted())) {
                    continue;
                }
                householdMembers.put(householdMember.getClientReferenceId(), householdMember);
                memberIdVsProjectId.put(householdMember.getClientReferenceId(), beneficiary.getProjectId());
            }
        }
        if (householdMembers.isEmpty()) {
            return;
        }
        log.info("re-transforming HHM id's {} for project beneficiaries", householdMembers.values().stream()
                .map(HouseholdMember::getId).collect(Collectors.toList()));
        push(householdMembers.values().stream()
                .map(householdMember -> transform(householdMember, memberIdVsProjectId.get(householdMember.getClientReferenceId())))
                .collect(Collectors.toList()));
    }

    // the beneficiary is the household for household-based projects and the individual for individual-based ones
    private List<HouseholdMember> searchMembersOfBeneficiary(ProjectBeneficiary beneficiary) {
        String tenantId = beneficiary.getTenantId();
        List<HouseholdMember> members = Collections.emptyList();
        if (StringUtils.isNotBlank(beneficiary.getBeneficiaryId())) {
            members = householdService.searchHouseholdMembers(HouseholdMemberSearch.builder()
                    .householdId(beneficiary.getBeneficiaryId()).build(), tenantId);
            if (members.isEmpty()) {
                members = householdService.searchHouseholdMembers(HouseholdMemberSearch.builder()
                        .individualId(beneficiary.getBeneficiaryId()).build(), tenantId);
            }
        } else if (StringUtils.isNotBlank(beneficiary.getBeneficiaryClientReferenceId())) {
            members = householdService.searchHouseholdMembers(HouseholdMemberSearch.builder()
                    .householdClientReferenceId(beneficiary.getBeneficiaryClientReferenceId()).build(), tenantId);
            if (members.isEmpty()) {
                members = householdService.searchHouseholdMembers(HouseholdMemberSearch.builder()
                        .individualClientReferenceId(beneficiary.getBeneficiaryClientReferenceId()).build(), tenantId);
            }
        }
        return members;
    }

    private void push(List<HouseholdMemberIndexV1> householdMemberIndexV1List) {
        String topic = transformerProperties.getTransformerProducerHouseholdMemberIndexV1Topic();
        log.info("transformation success for HHM id's {}", householdMemberIndexV1List.stream()
                .map(HouseholdMemberIndexV1::getHouseholdMember)
                .map(HouseholdMember::getId)
                .collect(Collectors.toList()));
        producer.push(topic, householdMemberIndexV1List);
    }

    private HouseholdMemberIndexV1 transform(HouseholdMember householdMember, String beneficiaryProjectId) {
        Map<String, String> boundaryHierarchy = null;
        Map<String, String> boundaryHierarchyCode = null;
        ObjectNode additionalDetails = objectMapper.createObjectNode();
        List<Double> geoPoint = null;
        String individualClientReferenceId = householdMember.getIndividualClientReferenceId();
        Map<String, Object> individualDetails = individualService.getIndividualInfo(individualClientReferenceId, householdMember.getTenantId());
        // Resolve the project from the beneficiary of this member's individual (individual-based projects) or
        // household (household-based projects); the creator's project-staff mapping is ambiguous when the user is
        // staff of more than one project running at the same time.
        ProjectInfo projectInfo = StringUtils.isNotBlank(beneficiaryProjectId)
                ? projectService.getProjectInfoByProjectId(beneficiaryProjectId, householdMember.getTenantId())
                : projectService.projectInfoFromBeneficiaryIds(
                        Arrays.asList(householdMember.getIndividualId(), householdMember.getHouseholdId()), householdMember.getTenantId());
        if (projectInfo == null) {
            // member created before its beneficiary; re-indexed with the project once the beneficiary is created
            log.info("No project beneficiary yet for HHM {} (individualId {}, householdId {}), indexing without project details",
                    householdMember.getId(), householdMember.getIndividualId(), householdMember.getHouseholdId());
            projectInfo = new ProjectInfo();
        }
        String hierarchyType = projectInfo.getHierarchyType();

        List<Household> households = householdService.searchHousehold(householdMember.getHouseholdClientReferenceId(), householdMember.getTenantId());
        String localityCode = null;
        if (!CollectionUtils.isEmpty(households) && households.get(0).getAddress() != null
                && households.get(0).getAddress().getLocality() != null
                && households.get(0).getAddress().getLocality().getCode() != null) {
            localityCode = households.get(0).getAddress().getLocality().getCode();
            BoundaryHierarchyResult boundaryHierarchyResult = boundaryService.getBoundaryHierarchyWithLocalityCode(localityCode, householdMember.getTenantId(),hierarchyType);
            boundaryHierarchy = boundaryHierarchyResult.getBoundaryHierarchy();
            boundaryHierarchyCode = boundaryHierarchyResult.getBoundaryHierarchyCode();
            geoPoint = commonUtils.getGeoPoint(households.get(0).getAddress());

            AdditionalFields additionalFields = households.get(0).getAdditionalFields();
            if (additionalFields != null && additionalFields.getFields() != null
                    && !CollectionUtils.isEmpty(additionalFields.getFields())) {
                additionalDetails = additionalFieldsToDetails(additionalFields.getFields());
            }
        }

        if (householdMember.getAdditionalFields() != null && householdMember.getAdditionalFields().getFields() != null
                && !CollectionUtils.isEmpty(householdMember.getAdditionalFields().getFields())) {
            List<Field> fields = householdMember.getAdditionalFields().getFields();
            addToAdditionalDetails(fields, additionalDetails);
        }

        Map<String, String> userInfoMap = userService.
                getUserInfo(householdMember.getTenantId(), householdMember.getClientAuditDetails().getLastModifiedBy());
        if (individualDetails.containsKey(HEIGHT) && individualDetails.containsKey(DISABILITY_TYPE)) {
            additionalDetails.put(HEIGHT, (Integer) individualDetails.get(HEIGHT));
            additionalDetails.put(DISABILITY_TYPE,(String) individualDetails.get(DISABILITY_TYPE));
        }


        HouseholdMemberIndexV1 householdMemberIndexV1 = HouseholdMemberIndexV1.builder()
                .householdMember(householdMember)
                .boundaryHierarchy(boundaryHierarchy)
                .boundaryHierarchyCode(boundaryHierarchyCode)
                .userName(userInfoMap.get(USERNAME))
                .nameOfUser(userInfoMap.get(NAME))
                .role(userInfoMap.get(ROLE))
                .userAddress(userInfoMap.get(CITY))
                .dateOfBirth(individualDetails.containsKey(DATE_OF_BIRTH) ? (Long) individualDetails.get(DATE_OF_BIRTH) : null)
                .age(individualDetails.containsKey(AGE) ? (Integer) individualDetails.get(AGE) : null)
                .gender(individualDetails.containsKey(GENDER) ? (String) individualDetails.get(GENDER) : null)
                .geoPoint(geoPoint)
                .localityCode(localityCode)
                .taskDates(commonUtils.getDateFromEpoch(householdMember.getClientAuditDetails().getLastModifiedTime()))
                .syncedDate(commonUtils.getDateFromEpoch(householdMember.getAuditDetails().getLastModifiedTime()))
                .syncedTimeStamp(commonUtils.getTimeStampFromEpoch(householdMember.getAuditDetails().getLastModifiedTime()))
                .build();
        householdMemberIndexV1.setProjectInfo(projectInfo);
        String cycleIndex = projectService.fetchCycleIndexFromProjectAdditionalDetails(householdMember.getTenantId(), householdMemberIndexV1.getProjectId(), householdMemberIndexV1.getProjectTypeId(), householdMember.getClientAuditDetails().getCreatedTime());
        additionalDetails.put(CYCLE_INDEX, cycleIndex);
        householdMemberIndexV1.setAdditionalDetails(additionalDetails);
        return householdMemberIndexV1;
    }

    private ObjectNode additionalFieldsToDetails(List<Field> fields) {
        ObjectNode additionalDetails = objectMapper.createObjectNode();
        fields.forEach(
                f -> additionalDetails.put(f.getKey(), f.getValue())
        );
        return additionalDetails;
    }
    private void addToAdditionalDetails(List<Field> fields, ObjectNode additionalDetails) {
        fields.forEach(
                f -> additionalDetails.put(f.getKey(), f.getValue())
        );
    }
}

