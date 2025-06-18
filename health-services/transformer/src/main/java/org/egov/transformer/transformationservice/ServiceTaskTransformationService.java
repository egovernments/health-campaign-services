package org.egov.transformer.transformationservice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
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
        filterChecklistsForExtraTransformation(serviceIndexV1List);
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
        Project project = projectService.getProject(projectId, tenantId);
        String projectTypeId = project.getProjectTypeId();
        JsonNode serviceAdditionalDetails = service.getAdditionalDetails();
        log.info("SERVICE ADDITION DETAILS {}", serviceAdditionalDetails);
        log.info("SERVICE ADDITION FIELDS {}", service.getAdditionalFields());

        ObjectNode additionalDetails = objectMapper.createObjectNode();
        additionalFieldsToDetails(additionalDetails, serviceAdditionalDetails);

        String localityCode = commonUtils.getLocalityCodeFromAdditionalDetails(additionalDetails);
        List<Double> geoPoint = commonUtils.getGeoPointFromAdditionalDetails(additionalDetails);
        if (localityCode != null) {
            BoundaryHierarchyResult boundaryHierarchyResult = boundaryService.getBoundaryHierarchyWithLocalityCode(localityCode, tenantId);
            boundaryHierarchy = boundaryHierarchyResult.getBoundaryHierarchy();
            boundaryHierarchyCode = boundaryHierarchyResult.getBoundaryHierarchyCode();
        } else {
            BoundaryHierarchyResult boundaryHierarchyResult = boundaryService.getBoundaryHierarchyWithProjectId(projectId, tenantId);
            boundaryHierarchy = boundaryHierarchyResult.getBoundaryHierarchy();
            boundaryHierarchyCode = boundaryHierarchyResult.getBoundaryHierarchyCode();
        }
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
        return serviceIndexV1;
    }

    private void filterChecklistsForExtraTransformation(List<ServiceIndexV1> serviceIndexV1List) {
        if (serviceIndexV1List == null || serviceIndexV1List.isEmpty()) {
            log.info("No service index data to process.");
            return;
        }
        List<String> checklistNames = Arrays.asList(transformerProperties.getChecklistsForExtraTransformation().split(","));
        List<ServiceIndexV1> checklistList = serviceIndexV1List.stream()
                .filter(service -> checklistNames.contains(service.getChecklistName()))
                .collect(Collectors.toList());
        if (CollectionUtils.isEmpty(checklistList)) return;

        log.info("{} checklist(s) will go through extra transformation", checklistList.size());

        for (ServiceIndexV1 service : checklistList) {
            JsonNode checklistInfo = mdmsService.fetchChecklistInfoFromMDMS(service.getTenantId(), service.getChecklistName());
            ObjectNode transformedChecklist = objectMapper.createObjectNode();
            List<AttributeValue> attributeValues = service.getAttributes();
            for (AttributeValue attributeValue : attributeValues) {
                String attributeCode = attributeValue.getAttributeCode();
                Object value = getValueFromChecklist(attributeValue);

                if (checklistInfo == null || !checklistInfo.has(attributeCode)) {
                    // MDMS info not found — adding raw attribute code and value
                    transformedChecklist.putPOJO(attributeCode, value);
                    continue;
                }

                JsonNode attrSpecificMap = checklistInfo.get(attributeCode);
                String keyVal = attrSpecificMap.get(KEY_VALUE).asText();
                Class<?> valueType = typeMap.getOrDefault(attrSpecificMap.get(VALUE_TYPE).asText(), Object.class);
                commonUtils.putValueBasedOnType(transformedChecklist, keyVal , value, valueType);
            }
            service.setTransformedChecklist(transformedChecklist);
        }

        String topic = transformerProperties.getTransformerProducerExtraTransformedChecklistIndexV1Topic();
        producer.push(topic, checklistList);
    }

    private Object getValueFromChecklist(AttributeValue attributeValue) {
        Object valueMap = attributeValue.getValue();

        if (valueMap instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) valueMap;
            return map.getOrDefault(VALUE, null);
        }

        return null;
    }

    public void additionalFieldsToDetails(ObjectNode additionalDetails, JsonNode serviceAdditionalFields) {
        log.info("Service additionalFields {}", serviceAdditionalFields);
        JsonNode additionalFields = serviceAdditionalFields.has("fields")
                ? serviceAdditionalFields.get("fields")
                : NullNode.getInstance();


        if (!(additionalFields instanceof List<?>)) {
            log.info("additionalFields is not of the expected type List<Field>. Skipping addition of fields.");
            return;
        }

        for (Object item : (List<?>) additionalFields) {
            if (item instanceof Field) {
                Field field = (Field) item;
                String key = field.getKey();
                String value = field.getValue();

                if (!additionalDetails.has(key)) {
                    additionalDetails.put(key, value);
                }
            } else {
                log.info("FIELD instance type not found in service AdditionalDetails");
            }
        }
    }

}
