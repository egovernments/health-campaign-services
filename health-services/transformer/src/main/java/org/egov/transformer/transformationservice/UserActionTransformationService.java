package org.egov.transformer.transformationservice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.core.Field;
import org.egov.common.models.project.Project;
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
        
        producer.push(topic, userActionIndexList);
    }

    private UserActionIndexV1 transform(UserAction userAction) {
        log.info("Transforming UserAction with id: {}", userAction.getId());
        
        String tenantId = userAction.getTenantId();
        String projectId = userAction.getProjectId();
        
        Project project = projectService.getProject(projectId, tenantId);
        String projectTypeId = project.getProjectTypeId();
        
        BoundaryHierarchyResult boundaryHierarchyResult = boundaryService.getBoundaryHierarchyWithLocalityCode(userAction.getBoundaryCode(), tenantId);
        JsonNode projectAdditionalDetails = projectService.fetchProjectAdditionalDetails(tenantId, null, projectTypeId);
        
        // Create geoPoint from latitude and longitude
        Double[] geoPoint = null;
        if (userAction.getLatitude() != null && userAction.getLongitude() != null) {
            geoPoint = new Double[]{userAction.getLongitude(), userAction.getLatitude()};
        }
        
        // Build combined additionalDetails with additionalFields as key-value pairs and MDMS data if applicable
        ObjectNode combinedAdditionalDetails = buildCombinedAdditionalDetails(userAction, projectAdditionalDetails, tenantId);
        Map<String, String> userInfoMap = userService.getUserInfo(userAction.getTenantId(), userAction.getClientAuditDetails().getCreatedBy());
        String syncedTimeStamp = commonUtils.getTimeStampFromEpoch(userAction.getAuditDetails().getLastModifiedTime());

        UserActionIndexV1 userActionIndex = UserActionIndexV1.builder()
                .userAction(userAction)
                .id(userAction.getId())
                .projectId(projectId)
                .projectType(project.getProjectType())
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
        if (projectAdditionalDetails != null) {
            combinedDetails.setAll((ObjectNode) projectAdditionalDetails);
        }
        
        // Add additionalFields as key-value pairs
        if (userAction.getAdditionalFields() != null && !CollectionUtils.isEmpty(userAction.getAdditionalFields().getFields())) {
            for (Field field : userAction.getAdditionalFields().getFields()) {
                if (field.getKey() != null && field.getValue() != null) {
                    combinedDetails.set(field.getKey(), convertToJsonNode(field.getValue()));
                }
            }
        }
        
        // Add MDMS data only for DAILY_PLAN action
        if (UserActionEnum.DAILY_PLAN.equals(userAction.getAction())) {
            Map<String, String> userActionDailyPlan = fetchUserActionDailyPlan(tenantId);

            try {
                Field field = userAction.getAdditionalFields().getFields().stream()
                        .filter(f -> "Data".equalsIgnoreCase(f.getKey()))
                        .findFirst()
                        .orElse(null);

                if (field != null && field.getValue() != null) {
                    List<String> items = Collections.emptyList();
                    try {
                        items = objectMapper.readValue(field.getValue(), List.class);
                    } catch (Exception e) {
                        log.error("Failed to parse field value into list for userAction: {}", userAction.getId(), e);
                    }

                    List<JsonNode> nodes = new ArrayList<>();
                    for (String item : items) {
                        try {
                            nodes.add(objectMapper.readTree(item));
                        } catch (Exception e) {
                            log.error("Failed to parse inner JSON string: {}", item, e);
                        }
                    }

                    try {
                        Map<String, List<String>> grouped = nodes.stream()
                                .filter(node -> node.hasNonNull(DAY_OF_VISIT) && node.hasNonNull(BOUNDARY_CODE_KEY))
                                .collect(Collectors.groupingBy(
                                        node -> node.get(DAY_OF_VISIT).asText(),
                                        Collectors.mapping(node -> node.get(BOUNDARY_CODE_KEY).asText(), Collectors.toList())
                                ));

                        for (Map.Entry<String, List<String>> entry : grouped.entrySet()) {
                            String day = entry.getKey().replace(" ", "_"); // "Day 1" -> "Day_1"
                            List<String> boundaries = entry.getValue();

                            String visitedBoundaries = day + VISITED_BOUNDARIES_SUFFIX;
                            combinedDetails.set(visitedBoundaries, objectMapper.valueToTree(boundaries));
                            combinedDetails.put(day, boundaries.size());

                        }
                    } catch (Exception e) {
                        log.error("Failed to group and process nodes for userAction: {}", userAction.getId(), e);
                    }
                } else {
                    log.warn("No 'Data' field found in additionalFields for userAction: {}", userAction.getId());
                }

            } catch (Exception e) {
                log.error("Unexpected error while processing DAILY_PLAN userAction: {}", userAction.getId(), e);
            }

            
            if (!userActionDailyPlan.isEmpty()) {
                combinedDetails.put(SUPERVISOR_ROLE, userActionDailyPlan.get(SUPERVISOR_ROLE_KEY));
                combinedDetails.put(SUB_BOUNDARY_TYPE, userActionDailyPlan.get(SUB_BOUNDARY_TYPE_KEY));
                combinedDetails.put(BOUNDARY_TYPE, userActionDailyPlan.get(BOUNDARY_TYPE_KEY));
            }
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
}