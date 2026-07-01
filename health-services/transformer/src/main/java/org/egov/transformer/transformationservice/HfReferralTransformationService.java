package org.egov.transformer.transformationservice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.egov.common.models.project.AdditionalFields;
import org.egov.common.models.project.Project;
import org.egov.common.models.referralmanagement.hfreferral.HFReferral;
import org.egov.transformer.config.TransformerProperties;
import org.egov.transformer.models.boundary.BoundaryHierarchyResult;
import org.egov.transformer.models.downstream.HfReferralIndexV1;
import org.egov.transformer.producer.Producer;
import org.egov.transformer.service.BoundaryService;
import org.egov.transformer.service.ProjectFactoryService;
import org.egov.transformer.service.ProjectService;
import org.egov.transformer.service.UserService;
import org.egov.transformer.utils.CommonUtils;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.egov.transformer.Constants.*;

@Slf4j
@Component
public class HfReferralTransformationService {

    private final TransformerProperties transformerProperties;
    private final Producer producer;
    private final UserService userService;
    private final ProjectService projectService;
    private final BoundaryService boundaryService;
    private final ProjectFactoryService projectFactoryService;

    private final CommonUtils commonUtils;

    private final ObjectMapper objectMapper;

    public HfReferralTransformationService(TransformerProperties transformerProperties,
                                           Producer producer, UserService userService, ProjectService projectService, BoundaryService boundaryService, ProjectFactoryService projectFactoryService, CommonUtils commonUtils, ObjectMapper objectMapper) {
        this.transformerProperties = transformerProperties;
        this.producer = producer;
        this.userService = userService;
        this.projectService = projectService;
        this.boundaryService = boundaryService;
        this.projectFactoryService = projectFactoryService;
        this.commonUtils = commonUtils;
        this.objectMapper = objectMapper;
    }

    public void transform(List<HFReferral> payloadList) {
        String topic = transformerProperties.getTransformerProducerHfReferralIndexV1Topic();
        log.info("transforming for ids {}", payloadList.stream()
                .map(HFReferral::getId).collect(Collectors.toList()));
        List<HfReferralIndexV1> transformedPayloadList = payloadList.stream()
                .map(this::transform)
                .collect(Collectors.toList());
        log.info("transformation successful");
        producer.push(topic,
                transformedPayloadList);
    }

    public HfReferralIndexV1 transform(HFReferral hfReferral) {
        String tenantId = hfReferral.getTenantId();
        String projectId = hfReferral.getProjectId();
        Project project = projectService.getProject(projectId, tenantId);
        String projectTypeId = project.getProjectTypeId();
        String projectType = project.getProjectType();
        AdditionalFields additionalFields = hfReferral.getAdditionalFields();
        String localityCode = commonUtils.getLocalityCodeFromAdditionalFields(additionalFields);
        BoundaryHierarchyResult boundaryHierarchyResult = null;
        if(localityCode != null) {
            boundaryHierarchyResult = boundaryService.getBoundaryHierarchyWithLocalityCode(localityCode, tenantId);
        } else {
            boundaryHierarchyResult = boundaryService.getBoundaryHierarchyWithProjectId(projectId, tenantId);
        }

        Map<String, String> userInfoMap = userService.getUserInfo(tenantId, hfReferral.getClientAuditDetails().getCreatedBy());

        String cycleIndex = commonUtils.fetchCycleIndex(tenantId, projectId, hfReferral.getClientAuditDetails());
        ObjectNode additionalDetails = objectMapper.createObjectNode();
        additionalDetails.put(CYCLE_INDEX, cycleIndex);

        String campaignId = null;
        if  (ObjectUtils.isNotEmpty(project) && StringUtils.isNotBlank(project.getReferenceID())) {
            campaignId = projectFactoryService.getCampaignIdFromCampaignNumber(project.getTenantId(), true, project.getReferenceID());
        }

        HfReferralIndexV1 hfReferralIndexV1 = HfReferralIndexV1.builder()
                .hfReferral(hfReferral)
                .userName(userInfoMap.get(USERNAME))
                .role(userInfoMap.get(ROLE))
                .userAddress(userInfoMap.get(CITY))
                .boundaryHierarchy(boundaryHierarchyResult.getBoundaryHierarchy())
                .boundaryHierarchyCode(boundaryHierarchyResult.getBoundaryHierarchyCode())
                .taskDates(commonUtils.getDateFromEpoch(hfReferral.getClientAuditDetails().getLastModifiedTime()))
                .syncedDate(commonUtils.getDateFromEpoch(hfReferral.getAuditDetails().getLastModifiedTime()))
                .additionalDetails(additionalDetails)
                .build();
        hfReferralIndexV1.setProjectInfo(projectId, projectType, projectTypeId, project.getName());
        hfReferralIndexV1.setCampaignNumber(project.getReferenceID());
        hfReferralIndexV1.setCampaignId(campaignId);

        return hfReferralIndexV1;
    }
}

