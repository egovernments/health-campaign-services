package org.egov.transformer.transformationservice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.egov.common.models.core.AdditionalFields;
import org.egov.common.models.core.Field;
import org.egov.common.models.project.UserActionEnum;
import org.egov.common.models.project.useraction.UserAction;
import org.egov.transformer.config.TransformerProperties;
import org.egov.transformer.models.boundary.BoundaryHierarchyResult;
import org.egov.transformer.models.downstream.UserActionIndexV1;
import org.egov.transformer.producer.Producer;
import org.egov.transformer.service.BoundaryService;
import org.egov.transformer.service.MdmsService;
import org.egov.transformer.service.ProjectService;
import org.egov.transformer.service.UserService;
import org.egov.transformer.utils.CommonUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.Collections;
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
    private final MdmsService mdmsService;
    private final UserService userService;
    private final CommonUtils commonUtils;

    public UserActionTransformationService(Producer producer,
                                           TransformerProperties transformerProperties,
                                           ObjectMapper objectMapper,
                                           ProjectService projectService,
                                           BoundaryService boundaryService,
                                           MdmsService mdmsService, UserService userService, CommonUtils commonUtils) {
        this.producer = producer;
        this.transformerProperties = transformerProperties;
        this.objectMapper = objectMapper;
        this.projectService = projectService;
        this.boundaryService = boundaryService;
        this.mdmsService = mdmsService;
        this.userService = userService;
        this.commonUtils = commonUtils;
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
        producer.push( topic, userActionIndexList);
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

//        Project project = projectService.getProject(projectId, tenantId);
        String projectTypeIdAndType = projectService.getProjectTypeInfoByProjectId(projectId, tenantId);

        String projectTypeId;
        String projectType;
        if (!StringUtils.isEmpty(projectTypeIdAndType)) {
            String[] parts = projectTypeIdAndType.split(COLON);
            projectTypeId = parts[0];
            projectType = (parts.length > 1) ? parts[1] : "";
        } else {
            projectTypeId = "";
            projectType = "";
        }


        BoundaryHierarchyResult boundaryHierarchyResult = boundaryService.getBoundaryHierarchyWithLocalityCode(userAction.getBoundaryCode(), tenantId);
        JsonNode projectAdditionalDetails = projectService.fetchProjectAdditionalDetails(tenantId, null, projectTypeId);

        // Create geoPoint from latitude and longitude
        Double[] geoPoint = null;
        if (userAction.getLatitude() != null && userAction.getLongitude() != null) {
            geoPoint = new Double[]{userAction.getLongitude(), userAction.getLatitude()};
        }

        // Build combined additionalDetails with additionalFields as key-value pairs and MDMS data if applicable
        ObjectNode combinedAdditionalDetails = buildCombinedAdditionalDetails(userAction, projectAdditionalDetails, tenantId);
        String cycleIndex = commonUtils.fetchCycleIndexFromTime(userAction.getTenantId(), projectTypeId, userAction.getClientAuditDetails().getCreatedTime());
        combinedAdditionalDetails.put(CYCLE_INDEX, cycleIndex);

        Map<String, String> userInfoMap = userService.getUserInfo(userAction.getTenantId(), userAction.getClientAuditDetails().getCreatedBy());
        String syncedTimeStamp = commonUtils.getTimeStampFromEpoch(userAction.getAuditDetails().getLastModifiedTime());

        UserActionIndexV1 userActionIndex = UserActionIndexV1.builder()
                .userAction(userAction)
                .id(userAction.getId())
                .projectId(projectId)
                .projectType(projectType)
                .projectTypeId(projectTypeId)
                .additionalDetails(combinedAdditionalDetails)
                .boundaryHierarchy(boundaryHierarchyResult.getBoundaryHierarchy())
                .boundaryHierarchyCode(boundaryHierarchyResult.getBoundaryHierarchyCode())
                .geoPoint(geoPoint)
                .userName(userInfoMap.get(USERNAME))
                .nameOfUser(userInfoMap.get(NAME))
                .role(userInfoMap.get(ROLE))
                .userAddress(userInfoMap.get(CITY))
                .syncedTimeStamp(syncedTimeStamp)
                .syncedTime(userAction.getAuditDetails().getLastModifiedTime())
                .taskDates(commonUtils.getDateFromEpoch(userAction.getClientAuditDetails().getLastModifiedTime()))
                .syncedDate(commonUtils.getDateFromEpoch(userAction.getAuditDetails().getLastModifiedTime()))
                .userAssignedLowestBoundaryCount(boundaryService.fetchLowestChildCount(projectId, tenantId))
                .build();

        log.info("Successfully transformed UserAction with id: {}", userAction.getId());
        return userActionIndex;
    }

    private ObjectNode buildCombinedAdditionalDetails(UserAction userAction, JsonNode projectAdditionalDetails, String tenantId) {
        ObjectNode combinedDetails = objectMapper.createObjectNode();

        // Add project additional details if present
//        if (projectAdditionalDetails != null) {
//            combinedDetails.setAll((ObjectNode) projectAdditionalDetails);
//        }

        // Add additionalFields as key-value pairs
        if (userAction.getAdditionalFields() != null && !CollectionUtils.isEmpty(userAction.getAdditionalFields().getFields())) {
            for (Field field : userAction.getAdditionalFields().getFields()) {
                if (field.getKey() != null && field.getValue() != null) {
                    combinedDetails.set(field.getKey(), convertToJsonNode(field.getValue()));
                }
            }
            combinedDetails.put(TRIP_DURATION, getTripDuration(userAction.getAdditionalFields()));
            combinedDetails.put(TRIP_DISTANCE, getTripDistance(userAction.getAdditionalFields()));
        }

        return combinedDetails;
    }

    private Map<String, String> fetchUserActionDailyPlan(String tenantId) {
        try {
            return mdmsService.fetchUserActionDailyPlan(tenantId);
        } catch (Exception e) {
            log.error("Error fetching user action daily plan from MDMS for tenantId: {}", tenantId, e);
            return Collections.emptyMap();
        }
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

    private Long getLongValue(AdditionalFields additionalFields, String key) {
        if (additionalFields == null || additionalFields.getFields() == null) {
            return null;
        }

        return additionalFields.getFields().stream()
                .filter(f -> key.equals(f.getKey()))
                .map(Field::getValue)
                .map(value -> {
                    try {
                        return Long.valueOf(value);
                    } catch (NumberFormatException e) {
                        return null;
                    }
                })
                .findFirst()
                .orElse(null);
    }

    public Long getTripDuration(AdditionalFields additionalFields) {
        Long tripStartTime = getLongValue(additionalFields, TRIP_START_TIME);
        Long tripEndTime   = getLongValue(additionalFields, END_TRIP_TIME);

        if (tripStartTime != null && tripEndTime != null) {
            return tripEndTime - tripStartTime;
        }

        return 0L;
    }
    public Long getTripDistance(AdditionalFields additionalFields) {
        Long startMileage = getLongValue(additionalFields, START_MILEAGE);
        Long endMileage   = getLongValue(additionalFields, END_MILEAGE);

        if (startMileage != null && endMileage != null) {
            return endMileage - startMileage;
        }

        return 0L;
    }
}