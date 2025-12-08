package org.egov.transformer.transformationservice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.egov.common.models.household.Field;
import org.egov.common.models.project.Project;
import org.egov.transformer.config.TransformerProperties;
import org.egov.transformer.models.boundary.BoundaryHierarchyResult;
import org.egov.transformer.models.downstream.ServiceIndexV1;
import org.egov.transformer.models.upstream.AttributeValue;
import org.egov.transformer.models.upstream.Service;
import org.egov.transformer.models.upstream.ServiceDefinition;
import org.egov.transformer.producer.Producer;
import org.egov.transformer.service.*;
import org.egov.transformer.utils.CommonUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.stream.Collectors;

import static org.egov.transformer.Constants.*;

@Slf4j
@Component
public class ServiceTaskTransformationService {
    private final Producer producer;
    private final TransformerProperties transformerProperties;
    private final ObjectMapper objectMapper;
    private final ServiceDefinitionService serviceDefinitionService;
    private final ProjectService projectService;
    private final CommonUtils commonUtils;
    private final UserService userService;
    private final BoundaryService boundaryService;
    private final MdmsService mdmsService;

    private static final Map<String, Class<?>> typeMap = new HashMap<>();
    static {
        typeMap.put(INTEGER, Integer.class);
        typeMap.put(STRING, String.class);
        typeMap.put(DOUBLE, Double.class);
        typeMap.put(BOOLEAN, Boolean.class);
        typeMap.put(LONG, Long.class);
    }

    public ServiceTaskTransformationService(Producer producer, TransformerProperties transformerProperties, ObjectMapper objectMapper, ServiceDefinitionService serviceDefinitionService, ProjectService projectService, CommonUtils commonUtils, UserService userService, BoundaryService boundaryService, MdmsService mdmsService) {
        this.producer = producer;
        this.transformerProperties = transformerProperties;
        this.objectMapper = objectMapper;
        this.serviceDefinitionService = serviceDefinitionService;
        this.projectService = projectService;
        this.commonUtils = commonUtils;
        this.userService = userService;
        this.boundaryService = boundaryService;
        this.mdmsService = mdmsService;
    }

    public void transform(List<Service> serviceList) {
        log.info("transforming for SERVICE TASK id's {}", serviceList.stream()
                .map(Service::getId).collect(Collectors.toList()));
        String topic = transformerProperties.getTransformerProducerServiceTaskIndexV1Topic();
        List<ServiceIndexV1> serviceIndexV1List = serviceList.stream()
                .map(this::transform)
                .collect(Collectors.toList());
        log.info("transformation success for SERVICE TASK id's {}", serviceIndexV1List.stream()
                .map(ServiceIndexV1::getId)
                .collect(Collectors.toList()));
        producer.push(topic, serviceIndexV1List);
    }

    private ServiceIndexV1 transform(Service service) {
        String tenantId = service.getTenantId();
        ServiceDefinition serviceDefinition = serviceDefinitionService.getServiceDefinition(service.getServiceDefId(), service.getTenantId());
        String[] parts = serviceDefinition.getCode().split("\\.");
        String projectName = parts[0];
        String supervisorLevel = parts[2];
        String projectId;
        if (service.getAccountId() != null) {
            projectId = service.getAccountId();
        } else {
            projectId = projectService.getProjectByName(projectName, service.getTenantId()).getId();
        }
        Map<String, String> boundaryHierarchy;
        Map<String, String> boundaryHierarchyCode;
        String projectTypeIdAndType = projectService.getProjectTypeInfoByProjectId(projectId, tenantId);

        String projectTypeId = "";
        if (!StringUtils.isEmpty(projectTypeIdAndType)) {
            String[] projectParts = projectTypeIdAndType.split(COLON);
            projectTypeId = projectParts[0];
        }

        JsonNode serviceAdditionalDetails = service.getAdditionalDetails();
        JsonNode serviceAdditionalFields = service.getAdditionalFields();

        ObjectNode additionalDetails = objectMapper.createObjectNode();

        if (serviceAdditionalFields != null && !serviceAdditionalFields.isNull()) {
            additionalFieldsToDetails(additionalDetails, serviceAdditionalFields);
        }
        if (serviceAdditionalDetails != null && JsonNodeType.OBJECT.equals(serviceAdditionalDetails.getNodeType())) {
            additionalDetails.putAll((ObjectNode) serviceAdditionalDetails);
        }

        String localityCode = commonUtils.getLocalityCodeFromAdditionalDetails(additionalDetails);
        List<Double> geoPoint = commonUtils.getGeoPointFromAdditionalDetails(additionalDetails);
        BoundaryHierarchyResult boundaryHierarchyResult;
        if (localityCode != null) {
            boundaryHierarchyResult = boundaryService.getBoundaryHierarchyWithLocalityCode(localityCode, tenantId);
        } else {
            boundaryHierarchyResult = boundaryService.getBoundaryHierarchyWithProjectId(projectId, tenantId);
        }
        boundaryHierarchy = boundaryHierarchyResult.getBoundaryHierarchy();
        boundaryHierarchyCode = boundaryHierarchyResult.getBoundaryHierarchyCode();
        String syncedTimeStamp = commonUtils.getTimeStampFromEpoch(service.getAuditDetails().getCreatedTime());
        Map<String, String> userInfoMap = userService.getUserInfo(service.getTenantId(), service.getAuditDetails().getCreatedBy());
        String cycleIndex = commonUtils.fetchCycleIndex(tenantId, projectTypeId, service.getAuditDetails());

        additionalDetails.put(CYCLE_INDEX, cycleIndex);
        additionalDetails.put(PROJECT_TYPE_ID, projectTypeId);

        ServiceIndexV1 serviceIndexV1 = ServiceIndexV1.builder()
                .id(service.getId())
                .clientReferenceId(service.getClientId())
                .projectId(projectId)
                .serviceDefinitionId(service.getServiceDefId())
                .supervisorLevel(supervisorLevel)
                .checklistName(parts[1])
                .userName(userInfoMap.get(USERNAME))
                .nameOfUser(userInfoMap.get(NAME))
                .role(userInfoMap.get(ROLE))
                .userAddress(userInfoMap.get(CITY))
                .createdTime(service.getAuditDetails().getCreatedTime())
                .taskDates(commonUtils.getDateFromEpoch(service.getAuditDetails().getLastModifiedTime()))
                .createdBy(service.getAuditDetails().getCreatedBy())
                .tenantId(service.getTenantId())
                .userId(service.getAccountId())
                .attributes(service.getAttributes())
                .syncedTime(service.getAuditDetails().getLastModifiedTime())
                .syncedTimeStamp(syncedTimeStamp)
                .boundaryHierarchy(boundaryHierarchy)
                .boundaryHierarchyCode(boundaryHierarchyCode)
                .additionalDetails(additionalDetails)
                .geoPoint(geoPoint)
                .build();

        filterChecklistsForExtraTransformation(serviceIndexV1);

        return serviceIndexV1;
    }

    private void filterChecklistsForExtraTransformation(ServiceIndexV1 serviceIndexV1) {

        List<String> checklistNames = Arrays.asList(transformerProperties.getChecklistsForExtraTransformation().split(","));
        if (!checklistNames.contains(serviceIndexV1.getChecklistName())) return;
        log.info("{} checklist(s) will go through extra transformation", serviceIndexV1.getClientReferenceId());

        JsonNode checklistInfo = mdmsService.fetchChecklistInfoFromMDMS(serviceIndexV1.getTenantId(), serviceIndexV1.getChecklistName());
        ObjectNode transformedChecklist = objectMapper.createObjectNode();
        List<AttributeValue> attributeValues = serviceIndexV1.getAttributes();
        for (AttributeValue attributeValue : attributeValues) {
            String attributeCode = attributeValue.getAttributeCode();
            Object value = getValueFromChecklist(attributeValue);

            if (checklistInfo == null || !checklistInfo.has(attributeCode)) {
                transformedChecklist.putPOJO(attributeCode, value);
                continue;
            }

            JsonNode attrSpecificMap = checklistInfo.get(attributeCode);
            String keyVal = attrSpecificMap.get(KEY_VALUE).asText();
            Class<?> valueType = typeMap.getOrDefault(attrSpecificMap.get(VALUE_TYPE).asText(), Object.class);
            commonUtils.putValueBasedOnType(transformedChecklist, keyVal , value, valueType);
        }
        serviceIndexV1.setTransformedChecklist(transformedChecklist);
    }

    private Object getValueFromChecklist(AttributeValue attributeValue) {
        Object valueMap = attributeValue.getValue();

        if (valueMap instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) valueMap;
            return map.getOrDefault(VALUE, null);
        }

        return null;
    }

    private void additionalFieldsToDetails(ObjectNode additionalDetails, JsonNode serviceAdditionalFields) {
        if (serviceAdditionalFields == null || serviceAdditionalFields.isNull()) {
            log.info("serviceAdditionalFields is null.");
            return;
        }

        if (serviceAdditionalFields.has("fields") && serviceAdditionalFields.get("fields").isArray()) {
            JsonNode fieldsArray = serviceAdditionalFields.get("fields");
            log.info("Processing fields array: {}", fieldsArray);
            for (JsonNode item : fieldsArray) {
                if (item.has("key") && item.has("value")) {
                    String key = item.get("key").asText();
                    JsonNode valueNode = item.get("value");
                    if (!additionalDetails.has(key)) {
                        additionalDetails.set(key, valueNode);
                    }
                } else {
                    log.info("Invalid field structure in fields array: {}", item);
                }
            }
        } else if (serviceAdditionalFields.isObject()) {
            log.info("Processing flat object: {}", serviceAdditionalFields);
            serviceAdditionalFields.fields().forEachRemaining(entry -> {
                String key = entry.getKey();
                JsonNode valueNode = entry.getValue();
                if (!additionalDetails.has(key)) {
                    additionalDetails.set(key, valueNode);
                }
            });
        } else {
            log.info("serviceAdditionalFields is not an expected structure. Skipping.");
        }
    }

}
