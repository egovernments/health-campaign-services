package org.egov.transformer.transformationservice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

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

    public UserActionTransformationService(Producer producer, 
                                         TransformerProperties transformerProperties,
                                         ObjectMapper objectMapper,
                                         ProjectService projectService,
                                         BoundaryService boundaryService,
                                         MdmsService mdmsService) {
        this.producer = producer;
        this.transformerProperties = transformerProperties;
        this.objectMapper = objectMapper;
        this.projectService = projectService;
        this.boundaryService = boundaryService;
        this.mdmsService = mdmsService;
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
        
        BoundaryHierarchyResult boundaryHierarchyResult = boundaryService.getBoundaryHierarchyWithProjectId(projectId, tenantId);
        JsonNode projectAdditionalDetails = projectService.fetchProjectAdditionalDetails(tenantId, null, projectTypeId);
        
        // Create geoPoint from latitude and longitude
        Double[] geoPoint = null;
        if (userAction.getLatitude() != null && userAction.getLongitude() != null) {
            geoPoint = new Double[]{userAction.getLongitude(), userAction.getLatitude()};
        }
        
        // Build combined additionalDetails with additionalFields as key-value pairs and MDMS data if applicable
        ObjectNode combinedAdditionalDetails = buildCombinedAdditionalDetails(userAction, projectAdditionalDetails, tenantId);
        
        UserActionIndexV1 userActionIndex = UserActionIndexV1.builder()
                .id(userAction.getId())
                .projectId(projectId)
                .projectType(project.getProjectType())
                .projectTypeId(projectTypeId)
                .tenantId(tenantId)
                .latitude(userAction.getLatitude())
                .longitude(userAction.getLongitude())
                .locationAccuracy(userAction.getLocationAccuracy())
                .boundaryCode(userAction.getBoundaryCode())
                .action(userAction.getAction().name())
                .beneficiaryTag(userAction.getBeneficiaryTag())
                .resourceTag(userAction.getResourceTag())
                .createdTime(userAction.getAuditDetails().getCreatedTime())
                .createdBy(userAction.getAuditDetails().getCreatedBy())
                .auditDetails(userAction.getAuditDetails())
                .clientAuditDetails(userAction.getClientAuditDetails())
                .additionalFields(userAction.getAdditionalFields())
                .additionalDetails(combinedAdditionalDetails)
                .boundaryHierarchy(boundaryHierarchyResult.getBoundaryHierarchy())
                .boundaryHierarchyCode(boundaryHierarchyResult.getBoundaryHierarchyCode())
                .isDeleted(userAction.getIsDeleted())
                .source(userAction.getSource())
                .rowVersion(userAction.getRowVersion())
                .applicationId(userAction.getApplicationId())
                .hasErrors(userAction.getHasErrors())
                .clientReferenceId(userAction.getClientReferenceId())
                .geoPoint(geoPoint)
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
                    combinedDetails.put(field.getKey(), field.getValue());
                }
            }
        }
        
        // Add MDMS data only for DAILY_PLAN action
        if (UserActionEnum.DAILY_PLAN.equals(userAction.getAction())) {
            Map<String, String> userActionDailyPlan = fetchUserActionDailyPlan(tenantId);
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
}