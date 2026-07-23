package org.egov.transformer.transformationservice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.egov.common.models.core.Field;
import org.egov.common.models.project.Project;
import org.egov.common.models.project.useraction.UserAction;
import org.egov.transformer.config.TransformerProperties;
import org.egov.transformer.models.boundary.BoundaryHierarchyResult;
import org.egov.transformer.models.downstream.ProjectInfo;
import org.egov.transformer.models.downstream.UserActionIndexV1;
import org.egov.transformer.producer.Producer;
import org.egov.transformer.service.*;
import org.egov.transformer.utils.CommonUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.egov.transformer.Constants.*;

@Slf4j
@Component
public class UserActionTransformationService {

    private final Producer producer;
    private final TransformerProperties transformerProperties;
    private final ObjectMapper objectMapper;
    private final ProjectService projectService;
    private final BoundaryService boundaryService;
    private final UserService userService;
    private final CommonUtils commonUtils;
    private final ProjectFactoryService  projectFactoryService;

    public UserActionTransformationService(Producer producer,
                                           TransformerProperties transformerProperties,
                                           ObjectMapper objectMapper,
                                           ProjectService projectService,
                                           BoundaryService boundaryService,
                                           UserService userService, CommonUtils commonUtils,
                                           ProjectFactoryService  projectFactoryService) {
        this.producer = producer;
        this.transformerProperties = transformerProperties;
        this.objectMapper = objectMapper;
        this.projectService = projectService;
        this.boundaryService = boundaryService;
        this.userService = userService;
        this.commonUtils = commonUtils;
        this.projectFactoryService = projectFactoryService;
    }

    public void transform(List<UserAction> userActionList) {
        log.info("Transforming USER ACTION ids: {}", userActionList.stream()
                .map(UserAction::getId).collect(Collectors.toList()));

        String topic = transformerProperties.getTransformerProducerUserActionIndexV1Topic();

        List<UserActionIndexV1> userActionIndexList = userActionList.stream()
                .map(this::transform)
                .collect(Collectors.toList());

        log.info("Transformation success for USER ACTION ids: {}", userActionIndexList.stream()
                .map(UserActionIndexV1::getId)
                .collect(Collectors.toList()));
        producer.push(getTopicName(userActionList.get(0), topic), userActionIndexList);
    }

    private String getTopicName(UserAction userAction, String topic) {
        if (userAction.getAction() == null)
            return topic;
        return topic + "-" + userAction.getAction().name().toLowerCase().replace("_", "-");
    }

    private UserActionIndexV1 transform(UserAction userAction) {
        log.info("Transforming UserAction with id: {}", userAction.getId());

        String tenantId = userAction.getTenantId();
        String projectId = userAction.getProjectId();

        Project project = projectService.getProject(projectId, tenantId);
        String hierarchyType = commonUtils.getHierarchyTypeFromProject(project);

        ProjectInfo projectInfo = commonUtils.projectDetailsFromUserId(userAction.getAuditDetails().getCreatedBy(),tenantId);
        BoundaryHierarchyResult boundaryHierarchyResult = boundaryService.getBoundaryHierarchyWithLocalityCode(userAction.getBoundaryCode(), tenantId,hierarchyType);

        // Create geoPoint from latitude and longitude
        Double[] geoPoint = null;
        if (userAction.getLatitude() != null && userAction.getLongitude() != null) {
            geoPoint = new Double[]{userAction.getLongitude(), userAction.getLatitude()};
        }

        ObjectNode additionalDetails = objectMapper.createObjectNode();
        if (userAction.getAdditionalFields() != null && !CollectionUtils.isEmpty(userAction.getAdditionalFields().getFields())) {
            for (Field field : userAction.getAdditionalFields().getFields()) {
                if (field.getKey() != null && field.getValue() != null) {
                    additionalDetails.set(field.getKey(), convertToJsonNode(field.getValue()));
                }
            }
        }

        String cycleIndex = commonUtils.fetchCycleIndexFromProjectAdditionalDetails(tenantId, projectId, project.getProjectTypeId(), userAction.getAuditDetails().getCreatedTime());
        additionalDetails.put(CYCLE_INDEX, cycleIndex);

        Map<String, String> userInfoMap = userService.getUserInfo(userAction.getTenantId(), userAction.getClientAuditDetails().getCreatedBy());
        String syncedTimeStamp = commonUtils.getTimeStampFromEpoch(userAction.getAuditDetails().getLastModifiedTime());

        String campaignId = null;
        if (ObjectUtils.isNotEmpty(project) && StringUtils.isNotBlank(project.getReferenceID())) {
            campaignId = projectFactoryService.getCampaignIdFromCampaignNumber(
                    project.getTenantId(), true, project.getReferenceID()
            );
        }

        UserActionIndexV1 userActionIndex = UserActionIndexV1.builder()
                .userAction(userAction)
                .id(userAction.getId())
                .projectId(projectId)
                .projectType(projectInfo.getProjectType())
                .projectTypeId(projectInfo.getProjectTypeId())
                .additionalDetails(additionalDetails)
                .boundaryHierarchy(boundaryHierarchyResult.getBoundaryHierarchy())
                .boundaryHierarchyCode(boundaryHierarchyResult.getBoundaryHierarchyCode())
                .geoPoint(geoPoint)
                .userName(userInfoMap.get(USERNAME))
                .nameOfUser(userInfoMap.get(NAME))
                .role(userInfoMap.get(ROLE))
                .syncedTimeStamp(syncedTimeStamp)
                .syncedTime(userAction.getAuditDetails().getLastModifiedTime())
                .taskDates(commonUtils.getDateFromEpoch(userAction.getClientAuditDetails().getLastModifiedTime()))
                .syncedDate(commonUtils.getDateFromEpoch(userAction.getAuditDetails().getLastModifiedTime()))
                .build();
        userActionIndex.setProjectInfo(projectId, project.getProjectType(), projectInfo.getProjectTypeId(), project.getName(),hierarchyType);
        userActionIndex.setCampaignNumber(project.getReferenceID());
        userActionIndex.setCampaignId(campaignId);
        log.info("Successfully transformed UserAction with id: {}", userAction.getId());
        return userActionIndex;
    }

    public JsonNode convertToJsonNode(String value) {
        if (value == null) return null;
        try {
            // Try parsing string into JSON
            return objectMapper.readTree(value);
        } catch (Exception e) {
            // If parsing fails, treat it as plain string
            return TextNode.valueOf(value);
        }
    }
}