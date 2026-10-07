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
import org.egov.transformer.config.TransformerProperties;
import org.egov.transformer.models.boundary.BoundaryHierarchyResult;
import org.egov.transformer.models.devicetoken.DeviceToken;
import org.egov.transformer.models.downstream.HouseholdIndexV1;
import org.egov.transformer.models.downstream.ProjectInfo;
import org.egov.transformer.producer.Producer;
import org.egov.transformer.service.*;
import org.egov.transformer.utils.CommonUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.egov.transformer.Constants.*;

@Slf4j
@Component
public class HouseholdTransformationService {
    private final TransformerProperties transformerProperties;
    private final Producer producer;
    private final ObjectMapper objectMapper;
    private final UserService userService;
    private final CommonUtils commonUtils;
    private final ProjectService projectService;
    private final HouseholdService householdService;
    private final BoundaryService boundaryService;

    public HouseholdTransformationService(TransformerProperties transformerProperties, Producer producer, ObjectMapper objectMapper, UserService userService, CommonUtils commonUtils, ProjectService projectService, HouseholdService householdService, BoundaryService boundaryService) {
        this.transformerProperties = transformerProperties;
        this.producer = producer;
        this.objectMapper = objectMapper;
        this.userService = userService;
        this.commonUtils = commonUtils;
        this.projectService = projectService;
        this.householdService = householdService;
        this.boundaryService = boundaryService;
    }

    public void transform(List<Household> householdList) {
        transform(householdList, null);
    }

    /**
     * Transforms households against the given project, used when the project is already known from the project
     * beneficiary. When projectId is null the project is resolved from the household's project beneficiary.
     */
    public void transform(List<Household> householdList, String projectId) {
        if (CollectionUtils.isEmpty(householdList)) {
            return;
        }
        log.info("transforming for HOUSEHOLD id's {}", householdList.stream()
                .filter(Objects::nonNull)
                .map(Household::getId).collect(Collectors.toList()));
        String topic = transformerProperties.getTransformerProducerBulkHouseholdIndexV1Topic();
        List<HouseholdIndexV1> householdIndexV1List = householdList.stream()
                .filter(Objects::nonNull)
                .map(household -> transform(household, projectId))
                .collect(Collectors.toList());
        log.info("transformation success for HOUSEHOLD id's {}", householdIndexV1List.stream()
                .map(HouseholdIndexV1::getHousehold)
                .map(Household::getId)
                .collect(Collectors.toList()));
        producer.push(topic, householdIndexV1List);
    }

    private HouseholdIndexV1 transform(Household household, String projectId) {
        householdService.searchHousehold(household.getClientReferenceId(), household.getTenantId());
        Map<String, String> boundaryHierarchy = null;
        Map<String, String> boundaryHierarchyCode = null;
        ProjectInfo projectInfo = getProjectInfo(household, projectId);

        String localityCode;
        if (household.getAddress() != null
                && household.getAddress().getLocality() != null
                && household.getAddress().getLocality().getCode() != null) {
            localityCode = household.getAddress().getLocality().getCode();
            BoundaryHierarchyResult boundaryHierarchyResult = boundaryService.getBoundaryHierarchyWithLocalityCode(localityCode, household.getTenantId(), projectInfo.getHierarchyType());
            boundaryHierarchy = boundaryHierarchyResult.getBoundaryHierarchy();
            boundaryHierarchyCode = boundaryHierarchyResult.getBoundaryHierarchyCode();
        }

        Map<String, String> userInfoMap = userService.getUserInfo(household.getTenantId(), household.getAuditDetails().getCreatedBy());
        String syncedTimeStamp = commonUtils.getTimeStampFromEpoch(household.getAuditDetails().getLastModifiedTime());

        ObjectNode additionalDetails = objectMapper.createObjectNode();
        AdditionalFields additionalFields = household.getAdditionalFields();
        if (additionalFields != null && additionalFields.getFields() != null
                && !CollectionUtils.isEmpty(additionalFields.getFields())) {
            householdService.additionalFieldsToDetails(additionalDetails, additionalFields.getFields());
        }
        int pregnantWomenCount = additionalDetails.has(PREGNANTWOMEN) ? additionalDetails.get(PREGNANTWOMEN).asInt(0) : 0;
        int childrenCount = additionalDetails.has(CHILDREN) ? additionalDetails.get(CHILDREN).asInt(0) : 0;
        if (pregnantWomenCount > 0 || childrenCount > 0) {
            additionalDetails.put(ISVULNERABLE, true);
        }


        HouseholdIndexV1 householdIndexV1 = HouseholdIndexV1.builder()
                .household(household)
                .userName(userInfoMap.get(USERNAME))
                .role(userInfoMap.get(ROLE))
                .nameOfUser(userInfoMap.get(NAME))
                .userAddress(userInfoMap.get(CITY))
                .geoPoint(commonUtils.getGeoPoint(household.getAddress()))
                .boundaryHierarchy(boundaryHierarchy)
                .boundaryHierarchyCode(boundaryHierarchyCode)
                .taskDates(commonUtils.getDateFromEpoch(household.getClientAuditDetails().getLastModifiedTime()))
                .syncedDate(commonUtils.getDateFromEpoch(household.getAuditDetails().getLastModifiedTime()))
                .syncedTimeStamp(syncedTimeStamp)
                .build();
        householdIndexV1.setProjectInfo(projectInfo);

        String cycleIndex = projectService.fetchCycleIndexFromProjectAdditionalDetails(household.getTenantId(), householdIndexV1.getProjectId(), householdIndexV1.getProjectTypeId(), household.getClientAuditDetails().getCreatedTime());
        additionalDetails.put(CYCLE_INDEX, cycleIndex);
        householdIndexV1.setAdditionalDetails(additionalDetails);
        return householdIndexV1;
    }

    // Project is resolved from the project beneficiary of the household (household-based projects) or of its members'
    // individuals (individual-based projects), not from the creator's project-staff mapping, which is ambiguous when
    // the user is staff of more than one project running at the same time.
    private ProjectInfo getProjectInfo(Household household, String projectId) {
        String tenantId = household.getTenantId();
        ProjectInfo projectInfo = null;
        if (StringUtils.isNotBlank(projectId)) {
            projectInfo = projectService.getProjectInfoByProjectId(projectId, tenantId);
        }
        if (projectInfo == null || StringUtils.isBlank(projectInfo.getProjectId())) {
            List<String> beneficiaryClientReferenceIds = new ArrayList<>();
            beneficiaryClientReferenceIds.add(household.getClientReferenceId());
            if (StringUtils.isNotBlank(household.getClientReferenceId())) {
                householdService.searchHouseholdMembers(HouseholdMemberSearch.builder()
                                .householdClientReferenceId(household.getClientReferenceId()).build(), tenantId)
                        .stream()
                        .filter(Objects::nonNull)
                        .map(HouseholdMember::getIndividualClientReferenceId)
                        .forEach(beneficiaryClientReferenceIds::add);
            }
            // household is created before its beneficiary, so no wait for indexing here
            projectInfo = projectService.projectInfoFromBeneficiaryClientReferenceIds(beneficiaryClientReferenceIds, tenantId, false);
        }
        if (projectInfo == null) {
            // household created before its beneficiary; re-indexed with the project once the beneficiary is created
            log.info("No project beneficiary yet for HOUSEHOLD {} (clientReferenceId {}), indexing without project details",
                    household.getId(), household.getClientReferenceId());
            projectInfo = new ProjectInfo();
        }
        return projectInfo;
    }
}
