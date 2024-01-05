package org.egov.transformer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.request.User;
import org.egov.common.models.facility.Facility;
import org.egov.common.models.project.Project;
import org.egov.common.models.project.ProjectBeneficiary;
import org.egov.common.models.referralmanagement.Referral;
import org.egov.transformer.Constants;
import org.egov.transformer.config.TransformerProperties;

import org.egov.transformer.models.downstream.ReferralIndexV1;
import org.egov.transformer.producer.Producer;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;


import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.egov.transformer.Constants.*;
import static org.egov.transformer.Constants.INDIVIDUAL_ID;

@Slf4j
@Component
public class ReferralService {

    private final TransformerProperties transformerProperties;
    private final ObjectMapper objectMapper;
    private final Producer producer;
    private final UserService userService;
    private final ProjectService projectService;
    private final IndividualService individualService;
    private final FacilityService facilityService;

    public ReferralService(TransformerProperties transformerProperties,
                           ObjectMapper objectMapper, Producer producer, UserService userService, ProjectService projectService, IndividualService individualService, FacilityService facilityService) {
        this.transformerProperties = transformerProperties;
        this.objectMapper = objectMapper;
        this.producer = producer;
        this.userService = userService;
        this.projectService = projectService;
        this.individualService = individualService;
        this.facilityService = facilityService;
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
        List<User> users = userService.getUsers(referral.getTenantId(), referral.getAuditDetails().getCreatedBy());
        String tenantId = referral.getTenantId();
        ProjectBeneficiary projectBeneficiary = getProjectBeneficiary(referral, tenantId);
        Map<String, Object> individualDetails = new HashMap<>();
        Map<String, String> boundaryLabelToNameMap = new HashMap<>();
        List<JsonNode> boundaryLevelVsLabel = null;
        if (projectBeneficiary != null) {
            individualDetails = individualService.findIndividualByClientReferenceId(projectBeneficiary.getBeneficiaryClientReferenceId(), tenantId);
            String projectId = projectBeneficiary.getProjectId();
            Project project = projectService.getProject(projectId, tenantId);
            String projectTypeId = project.getProjectTypeId();
            JsonNode mdmsBoundaryData = projectService.fetchBoundaryData(tenantId, null, projectTypeId);
            boundaryLevelVsLabel = StreamSupport
                    .stream(mdmsBoundaryData.get(Constants.BOUNDARY_HIERARCHY).spliterator(), false).collect(Collectors.toList());
            boundaryLabelToNameMap = projectService.getBoundaryLabelToNameMapByProjectId(projectId, referral.getTenantId());
        }

        Facility facility = facilityService.findFacilityById(referral.getRecipientId(), tenantId);
        Map<String, String> finalBoundaryLabelToNameMap = boundaryLabelToNameMap;
        ReferralIndexV1 referralIndexV1 = ReferralIndexV1.builder()
                .referral(referral)
                .tenantId(referral.getTenantId())
                .userName(userService.getUserName(users, referral.getAuditDetails().getCreatedBy()))
                .role(userService.getStaffRole(referral.getTenantId(), users))
                .facilityName(facility.getName())
                .age(individualDetails.containsKey(AGE) ? (Integer) individualDetails.get(AGE) : null)
                .dateOfBirth(individualDetails.containsKey(DATE_OF_BIRTH) ? (Long) individualDetails.get(DATE_OF_BIRTH) : null)
                .individualId(individualDetails.containsKey(INDIVIDUAL_ID) ? (String) individualDetails.get(INDIVIDUAL_ID) : null)
                .gender(individualDetails.containsKey(GENDER) ? (String) individualDetails.get(GENDER) : null)
                .build();

        if (boundaryLevelVsLabel != null) {
            ObjectNode boundaryHierarchy = objectMapper.createObjectNode();
            referralIndexV1.setBoundaryHierarchy(boundaryHierarchy);
            boundaryLevelVsLabel.forEach(node -> {
                if (node.get(Constants.LEVEL).asInt() > 1) {
                    referralIndexV1.getBoundaryHierarchy().put(node.get(Constants.INDEX_LABEL).asText(), finalBoundaryLabelToNameMap.get(node.get(Constants.LABEL).asText()) == null ? null : finalBoundaryLabelToNameMap.get(node.get(Constants.LABEL).asText()));
                }
            });
        }

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

