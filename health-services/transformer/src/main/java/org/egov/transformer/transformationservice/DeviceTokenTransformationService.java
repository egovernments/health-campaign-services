package org.egov.transformer.transformationservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.egov.transformer.config.TransformerProperties;
import org.egov.transformer.models.boundary.BoundaryHierarchyResult;
import org.egov.transformer.models.devicetoken.DeviceToken;
import org.egov.transformer.models.downstream.*;
import org.egov.transformer.producer.Producer;
import org.egov.transformer.service.*;
import org.egov.transformer.utils.CommonUtils;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.egov.transformer.Constants.*;

@Slf4j
@Component
public class DeviceTokenTransformationService {

    private final TransformerProperties transformerProperties;
    private final Producer producer;
    private final UserService userService;
    private final BoundaryService boundaryService;
    private final CommonUtils commonUtils;
    private final ObjectMapper objectMapper;


    public DeviceTokenTransformationService(TransformerProperties transformerProperties, Producer producer, UserService userService, BoundaryService boundaryService, CommonUtils commonUtils, ObjectMapper objectMapper) {
        this.transformerProperties = transformerProperties;
        this.producer = producer;
        this.userService = userService;
        this.boundaryService = boundaryService;
        this.commonUtils = commonUtils;
        this.objectMapper = objectMapper;
    }

    public void transform(List<DeviceToken> payloadList) {
        String topic = transformerProperties.getTransformerProducerDeviceTokenIndexV1Topic();
        log.info("transforming for DEVICE TOKEN ids {}", payloadList.stream()
                .map(DeviceToken::getId).collect(Collectors.toList()));
        List<DeviceTokenIndexV1> transformedPayloadList = payloadList.stream()
                .map(this::transform)
                .collect(Collectors.toList());
        log.info("transformation successful");
        producer.push(topic, transformedPayloadList);
    }

    public DeviceTokenIndexV1 transform(DeviceToken deviceToken) {
        String tenantId = deviceToken.getTenantId();
        BoundaryHierarchyResult boundaryHierarchyResult = new BoundaryHierarchyResult();
        ProjectInfo projectInfo = commonUtils.projectDetailsFromUserId(deviceToken.getUserId(),tenantId);
        if (ObjectUtils.isNotEmpty(projectInfo) && StringUtils.isNotEmpty(projectInfo.getProjectId())) {
            String projectId = projectInfo.getProjectId();
            boundaryHierarchyResult = boundaryService.getBoundaryHierarchyWithProjectId(projectId, tenantId);
        }

        Map<String, String> userInfoMap = userService.getUserInfo(tenantId, deviceToken.getUserId());

        DeviceTokenIndexV1 deviceTokenIndexV1 = DeviceTokenIndexV1.builder()
                .deviceToken(deviceToken)
                .userName(userInfoMap.get(USERNAME))
                .role(userInfoMap.get(ROLE))
                .boundaryHierarchy(boundaryHierarchyResult.getBoundaryHierarchy())
                .boundaryHierarchyCode(boundaryHierarchyResult.getBoundaryHierarchyCode())
                .taskDates(commonUtils.getDateFromEpoch(deviceToken.getAuditDetails().getLastModifiedTime()))
                .syncedDate(commonUtils.getDateFromEpoch(deviceToken.getAuditDetails().getLastModifiedTime()))
                .build();
        deviceTokenIndexV1.setProjectInfo(projectInfo.getProjectId(), projectInfo.getProjectType(),
                projectInfo.getProjectTypeId(), projectInfo.getProjectName());
        deviceTokenIndexV1.setCampaignNumber(projectInfo.getCampaignNumber());
        deviceTokenIndexV1.setCampaignId(projectInfo.getCampaignId());

        String cycleIndex = commonUtils.fetchCycleIndexFromProjectAdditionalDetails(tenantId, deviceTokenIndexV1.getProjectId(), deviceTokenIndexV1.getProjectTypeId(), deviceToken.getAuditDetails().getCreatedTime());
        ObjectNode additionalDetails = objectMapper.createObjectNode();
        additionalDetails.put(CYCLE_INDEX, cycleIndex);
        deviceTokenIndexV1.setAdditionalDetails(additionalDetails);

        return deviceTokenIndexV1;
    }
}

