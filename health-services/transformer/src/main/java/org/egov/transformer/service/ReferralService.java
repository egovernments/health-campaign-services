package org.egov.transformer.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.facility.Facility;
import org.egov.common.models.project.Project;
import org.egov.common.models.project.ProjectBeneficiary;
import org.egov.common.models.referralmanagement.Referral;
import org.egov.transformer.config.TransformerProperties;

import org.egov.transformer.models.downstream.ReferralIndexV1;
import org.egov.transformer.producer.Producer;
import org.egov.transformer.utils.CommonUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;


import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.egov.transformer.Constants.*;
import static org.egov.transformer.Constants.INDIVIDUAL_ID;

@Slf4j
@Component
public class ReferralService {

    private final TransformerProperties transformerProperties;
    private final Producer producer;
    private final UserService userService;
    private final ProjectService projectService;
    private final IndividualService individualService;
    private final FacilityService facilityService;

    private final CommonUtils commonUtils;

    private final ObjectMapper objectMapper;

    public ReferralService(TransformerProperties transformerProperties,
                           Producer producer, UserService userService, ProjectService projectService, IndividualService individualService, FacilityService facilityService, CommonUtils commonUtils, ObjectMapper objectMapper) {
        this.transformerProperties = transformerProperties;
        this.producer = producer;
        this.userService = userService;
        this.projectService = projectService;
        this.individualService = individualService;
        this.facilityService = facilityService;
        this.commonUtils = commonUtils;
        this.objectMapper = objectMapper;
    }

    public void transform(List<Referral> payloadList) {
        String topic = transformerProperties.getTransformerProducerReferralIndexV1Topic();
        log.info("transforming for ids {}", payloadList.stream()
                .map(Referral::getId).collect(Collectors.toList()));
        List<ReferralIndexV1> transformedPayloadList = payloadList.stream()
                .map(this::transform)
                .collect(Collectors.toList());
        log.info("transformation successful");
        producer.push(topic,
                transformedPayloadList);
    }

    public ReferralIndexV1 transform(Referral referral) {
        String tenantId = referral.getTenantId();
        ProjectBeneficiary projectBeneficiary = getProjectBeneficiary(referral, tenantId);
        Map<String, Object> individualDetails = new HashMap<>();
        Map<String, String> boundaryLabelToNameMap = new HashMap<>();
        String projectTypeId = null;
        if (projectBeneficiary != null) {
            individualDetails = individualService.findIndividualByClientReferenceId(projectBeneficiary.getBeneficiaryClientReferenceId(), tenantId);
            String projectId = projectBeneficiary.getProjectId();
            Project project = projectService.getProject(projectId, tenantId);
            projectTypeId = project.getProjectTypeId();
            if (individualDetails.containsKey(ADDRESS_CODE)) {
                boundaryLabelToNameMap = projectService.getBoundaryLabelToNameMap((String) individualDetails.get(ADDRESS_CODE), tenantId);
            } else {
                boundaryLabelToNameMap = projectService.getBoundaryLabelToNameMapByProjectId(projectId, referral.getTenantId());
            }
        }
        String facilityName = Optional.of(referral)
                .filter(r -> FACILITY.equalsIgnoreCase(r.getRecipientType()))
                .map(r -> facilityService.findFacilityById(r.getRecipientId(), tenantId))
                .map(Facility::getName)
                .orElse(DEFAULT_FACILITY_NAME);

        Map<String, String> finalBoundaryLabelToNameMap = boundaryLabelToNameMap;
        ObjectNode boundaryHierarchy = (ObjectNode) commonUtils.getBoundaryHierarchy(tenantId, projectTypeId, finalBoundaryLabelToNameMap);
        Map<String, String> userInfoMap = userService.getUserInfo(tenantId, referral.getAuditDetails().getCreatedBy());

        Integer cycleIndex = commonUtils.fetchCycleIndex(tenantId, projectTypeId, referral.getAuditDetails());
        ObjectNode additionalDetails = objectMapper.createObjectNode();
        additionalDetails.put(CYCLE_NUMBER, cycleIndex);

        ReferralIndexV1 referralIndexV1 = ReferralIndexV1.builder()
                .referral(referral)
                .tenantId(referral.getTenantId())
                .userName(userInfoMap.get(USERNAME))
                .role(userInfoMap.get(ROLE))
                .userAddress(userInfoMap.get(CITY))
                .facilityName(facilityName)
                .age(individualDetails.containsKey(AGE) ? (Integer) individualDetails.get(AGE) : null)
                .dateOfBirth(individualDetails.containsKey(DATE_OF_BIRTH) ? (Long) individualDetails.get(DATE_OF_BIRTH) : null)
                .individualId(individualDetails.containsKey(INDIVIDUAL_ID) ? (String) individualDetails.get(INDIVIDUAL_ID) : null)
                .gender(individualDetails.containsKey(GENDER) ? (String) individualDetails.get(GENDER) : null)
                .boundaryHierarchy(boundaryHierarchy)
                .taskDates(commonUtils.getDateFromEpoch(referral.getClientAuditDetails().getLastModifiedTime()))
                .syncedDate(commonUtils.getDateFromEpoch(referral.getAuditDetails().getLastModifiedTime()))
                .additionalDetails(additionalDetails)
                .build();

        return referralIndexV1;
    }

    private ProjectBeneficiary getProjectBeneficiary(Referral referral, String tenantId) {
        String projectBeneficiaryClientReferenceId = referral.getProjectBeneficiaryClientReferenceId();
        ProjectBeneficiary projectBeneficiary = null;
        List<ProjectBeneficiary> projectBeneficiaries = projectService
                .searchBeneficiary(projectBeneficiaryClientReferenceId, tenantId);

        if (!CollectionUtils.isEmpty(projectBeneficiaries)) {
            projectBeneficiary = projectBeneficiaries.get(0);
        }
        return projectBeneficiary;
    }
}

