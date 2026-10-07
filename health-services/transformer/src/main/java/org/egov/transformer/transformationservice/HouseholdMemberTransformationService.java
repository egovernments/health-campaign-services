package org.egov.transformer.transformationservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.egov.common.models.household.AdditionalFields;
import org.egov.common.models.household.Field;
import org.egov.common.models.household.Household;
import org.egov.common.models.household.HouseholdMember;
import org.egov.common.models.project.Project;
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
    private final HouseholdTransformationService householdTransformationService;

    public HouseholdMemberTransformationService(TransformerProperties transformerProperties, Producer producer, CommonUtils commonUtils, IndividualService individualService, UserService userService, HouseholdService householdService, ObjectMapper objectMapper, ProjectService projectService, BoundaryService boundaryService, HouseholdTransformationService householdTransformationService) {
        this.transformerProperties = transformerProperties;
        this.producer = producer;
        this.commonUtils = commonUtils;
        this.individualService = individualService;
        this.userService = userService;
        this.householdService = householdService;
        this.objectMapper = objectMapper;
        this.projectService = projectService;
        this.boundaryService = boundaryService;
        this.householdTransformationService = householdTransformationService;
    }

    public void transform(List<HouseholdMember> householdMemberList) {
        transform(householdMemberList, null);
    }

    /**
     * Transforms household members against the given project, used when the project is already known from the
     * project beneficiary (member created before its beneficiary). When projectId is null the project is resolved
     * from the member's beneficiary.
     */
    public void transform(List<HouseholdMember> householdMemberList, String projectId) {
        if (CollectionUtils.isEmpty(householdMemberList)) {
            return;
        }
        log.info("transforming for HHM id's {}", householdMemberList.stream()
                .filter(Objects::nonNull)
                .map(HouseholdMember::getId).collect(Collectors.toList()));
        String topic = transformerProperties.getTransformerProducerHouseholdMemberIndexV1Topic();
        List<HouseholdMemberIndexV1> householdMemberIndexV1List = householdMemberList.stream()
                .filter(Objects::nonNull)
                .map(householdMember -> transform(householdMember, projectId))
                .collect(Collectors.toList());
        log.info("transformation success for HHM id's {}", householdMemberIndexV1List.stream()
                .map(HouseholdMemberIndexV1::getHouseholdMember)
                .map(HouseholdMember::getId)
                .collect(Collectors.toList()));
        producer.push(topic, householdMemberIndexV1List);
    }

    private HouseholdMemberIndexV1 transform(HouseholdMember householdMember, String projectId) {
        Map<String, String> boundaryHierarchy = null;
        Map<String, String> boundaryHierarchyCode = null;
        ObjectNode additionalDetails = objectMapper.createObjectNode();
        List<Double> geoPoint = null;
        String individualClientReferenceId = householdMember.getIndividualClientReferenceId();
        Map<String, Object> individualDetails = individualService.getIndividualInfo(individualClientReferenceId, householdMember.getTenantId());
        ProjectInfo projectInfo = getProjectInfo(householdMember, projectId);
        String hierarchyType = projectInfo.getHierarchyType();

        List<Household> households = householdService.searchHousehold(householdMember.getHouseholdClientReferenceId(), householdMember.getTenantId());
        // individual-based projects: the household has no beneficiary of its own and is created before the member's
        // beneficiary, so re-index it with the project once, from its head (skipped when re-indexing from a
        // beneficiary, which re-indexes the household itself)
        if (StringUtils.isBlank(projectId) && Boolean.TRUE.equals(householdMember.getIsHeadOfHousehold())
                && StringUtils.isNotBlank(projectInfo.getProjectId()) && !CollectionUtils.isEmpty(households)) {
            householdTransformationService.transform(households, projectInfo.getProjectId());
        }
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

    // Project is resolved from the project beneficiary of the member's individual (individual-based projects) or
    // household (household-based projects), not from the creator's project-staff mapping, which is ambiguous when
    // the user is staff of more than one project running at the same time.
    private ProjectInfo getProjectInfo(HouseholdMember householdMember, String projectId) {
        String tenantId = householdMember.getTenantId();
        ProjectInfo projectInfo = null;
        if (StringUtils.isNotBlank(projectId)) {
            projectInfo = projectService.getProjectInfoByProjectId(projectId, tenantId);
        }
        if (projectInfo == null || StringUtils.isBlank(projectInfo.getProjectId())) {
            projectInfo = projectService.projectInfoFromBeneficiaryClientReferenceIds(
                    Arrays.asList(householdMember.getIndividualClientReferenceId(), householdMember.getHouseholdClientReferenceId()),
                    tenantId, true);
        }
        if (projectInfo == null) {
            // member created before its beneficiary; re-indexed with the project once the beneficiary is created
            log.info("No project beneficiary yet for HHM {} (individualClientReferenceId {}, householdClientReferenceId {}), indexing without project details",
                    householdMember.getId(), householdMember.getIndividualClientReferenceId(), householdMember.getHouseholdClientReferenceId());
            projectInfo = new ProjectInfo();
        }
        return projectInfo;
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

