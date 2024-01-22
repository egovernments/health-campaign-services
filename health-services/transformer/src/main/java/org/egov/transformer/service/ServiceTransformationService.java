package org.egov.transformer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.request.User;
import org.egov.common.models.project.Project;
import org.egov.transformer.Constants;
import org.egov.transformer.config.TransformerProperties;
import org.egov.transformer.models.downstream.ServiceIndexV2;
import org.egov.transformer.models.upstream.AttributeValue;
import org.egov.transformer.models.upstream.Service;
import org.egov.transformer.models.upstream.ServiceDefinition;
import org.egov.transformer.producer.Producer;
import org.egov.transformer.utils.CommonUtils;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Slf4j
@Component
public class ServiceTransformationService {

    private final ObjectMapper objectMapper;
    private final ServiceDefinitionService serviceDefinitionService;
    private final TransformerProperties transformerProperties;
    private final Producer producer;
    private final ProjectService projectService;
    private final UserService userService;
    private final CommonUtils commonUtils;


    public ServiceTransformationService(ObjectMapper objectMapper, ServiceDefinitionService serviceDefinitionService, TransformerProperties transformerProperties, Producer producer, ProjectService projectService, UserService userService, CommonUtils commonUtils) {

        this.objectMapper = objectMapper;
        this.serviceDefinitionService = serviceDefinitionService;
        this.transformerProperties = transformerProperties;
        this.producer = producer;
        this.projectService = projectService;
        this.userService = userService;
        this.commonUtils = commonUtils;
    }

    public void transform(List<Service> serviceList) {
        List<ServiceIndexV2> serviceIndexV2List = new ArrayList<>();
        String topic = transformerProperties.getTransformerProducerServiceIndexV2Topic();
        serviceList.forEach(service -> {
            transform(service, serviceIndexV2List);
        });
        if (!serviceIndexV2List.isEmpty()) {
            producer.push(topic, serviceIndexV2List);
        }
    }

    private void transform(Service service, List<ServiceIndexV2> serviceIndexV2List) {
        ServiceDefinition serviceDefinition = serviceDefinitionService.getServiceDefinition(service.getServiceDefId(), service.getTenantId());
        String checkListName = serviceDefinition.getCode();
        String tenantId = service.getTenantId();
        String[] parts = serviceDefinition.getCode().split("\\.");
        String projectName = parts[0];
        String supervisorLevel = parts[2];
        String projectId = null;
        if (service.getAccountId() != null) {
            projectId = service.getAccountId();
        } else {
            projectId = projectService.getProjectByName(projectName, service.getTenantId()).getId();
        }
        Map<String, String> boundaryLabelToNameMap = new HashMap<>();
        Project project = projectService.getProject(projectId, tenantId);
        String projectTypeId = project.getProjectTypeId();

        if (service.getAdditionalDetails() != null) {
            boundaryLabelToNameMap = projectService
                    .getBoundaryLabelToNameMap((String) service.getAdditionalDetails(), service.getTenantId());
        } else {
            boundaryLabelToNameMap = projectService.getBoundaryLabelToNameMapByProjectId(projectId, service.getTenantId());
        }
        log.info("boundary labels {}", boundaryLabelToNameMap.toString());
        ObjectNode boundaryHierarchy = (ObjectNode) commonUtils.getBoundaryHierarchy(tenantId, projectTypeId, boundaryLabelToNameMap);
        List<User> users = userService.getUsers(service.getTenantId(), service.getAuditDetails().getCreatedBy());
        String syncedTimeStamp = commonUtils.getTimeStampFromEpoch(service.getAuditDetails().getCreatedTime());

        // populate them from env
        String checkListToFilter = transformerProperties.getCheckListName().trim();
        List<AttributeValue> attributeValueList = service.getAttributes();
        Map<String, Map<String, String>> attributeCodeToQuestionAgeGroup = new HashMap<>();
        getAttributeCodeMappings(attributeCodeToQuestionAgeGroup);
        if (checkListName.trim().equals(checkListToFilter)) {
            String finalProjectId = projectId;
            String checklistName = serviceDefinition.getCode().split("\\.")[1];
            attributeCodeToQuestionAgeGroup.forEach((key, value) -> {
                ServiceIndexV2 serviceIndexV2 = ServiceIndexV2.builder()
                        .id(service.getId())
                        .supervisorLevel(supervisorLevel)
                        .checklistName(checklistName)
                        .ageGroup(key)
                        .tenantId(tenantId)
                        .projectId(finalProjectId)
                        .userName(userService.getUserName(users, service.getAuditDetails().getCreatedBy()))
                        .role(userService.getStaffRole(service.getTenantId(), users))
                        .createdTime(service.getAuditDetails().getCreatedTime())
                        .syncedTime(service.getAuditDetails().getCreatedTime())
                        .createdBy(service.getAuditDetails().getCreatedBy())
                        .syncedTimeStamp(syncedTimeStamp)
                        .boundaryHierarchy(boundaryHierarchy)
                        .build();

                searchAndSetAttribute(attributeValueList, value, serviceIndexV2);
                serviceIndexV2List.add(serviceIndexV2);
            });
        }
    }

    private void searchAndSetAttribute(List<AttributeValue> attributeValueList, Map<String, String> codeToQuestionMapping, ServiceIndexV2 serviceIndexV2) {
        attributeValueList.forEach(attributeValue -> {
            String attributeCode = attributeValue.getAttributeCode();
            if (codeToQuestionMapping.containsKey(attributeCode)) {
                String question = codeToQuestionMapping.get(attributeCode);
                setAttributeValue(serviceIndexV2, attributeValue.getValue(), question);
            }
        });
    }

    private static void setAttributeValue(ServiceIndexV2 serviceIndexV2, Object value, String question) {
        switch (question) {
            case Constants.CHILDREN_PRESENTED_US: {
                serviceIndexV2.setChildrenPresentedUS(value);
                break;
            }
            case Constants.MALARIA_POSITIVE_US: {
                serviceIndexV2.setMalariaPositiveUS(value);
                break;
            }
            case Constants.MALARIA_NEGATIVE_US: {
                serviceIndexV2.setMalariaNegativeUS(value);
                break;
            }
            case Constants.CHILDREN_PRESENTED_APE: {
                serviceIndexV2.setChildrenPresentedAPE(value);
                break;
            }
            case Constants.MALARIA_POSITIVE_APE: {
                serviceIndexV2.setMalariaPositiveAPE(value);
                break;
            }
            case Constants.MALARIA_NEGATIVE_APE: {
                serviceIndexV2.setMalariaNegativeAPE(value);
                break;
            }
        }

    }

    private static void getAttributeCodeMappings(Map<String, Map<String, String>> attributeCodeToQuestionAgeGroup) {
        Map<String, String> codeVsQuestionMappingGroup1 = new HashMap<>();
        Map<String, String> codeVsQuestionMappingGroup2 = new HashMap<>();
        getGroup1Map(codeVsQuestionMappingGroup1);
        getGroup2Map(codeVsQuestionMappingGroup2);
        attributeCodeToQuestionAgeGroup.put("3-12 months", codeVsQuestionMappingGroup1);
        attributeCodeToQuestionAgeGroup.put("13-59 months", codeVsQuestionMappingGroup2);
    }

    private static void getGroup1Map(Map<String, String> codeVsQuestionMappingGroup1) {
        codeVsQuestionMappingGroup1.put("SM1", Constants.CHILDREN_PRESENTED_US);
        codeVsQuestionMappingGroup1.put("SM3", Constants.MALARIA_POSITIVE_US);
        codeVsQuestionMappingGroup1.put("SM5", Constants.MALARIA_NEGATIVE_US);
        codeVsQuestionMappingGroup1.put("SM7", Constants.CHILDREN_PRESENTED_APE);
        codeVsQuestionMappingGroup1.put("SM9", Constants.MALARIA_POSITIVE_APE);
        codeVsQuestionMappingGroup1.put("SM11", Constants.MALARIA_NEGATIVE_APE);
    }

    private static void getGroup2Map(Map<String, String> codeVsQuestionMappingGroup2) {
        codeVsQuestionMappingGroup2.put("SM2", Constants.CHILDREN_PRESENTED_US);
        codeVsQuestionMappingGroup2.put("SM4", Constants.MALARIA_POSITIVE_US);
        codeVsQuestionMappingGroup2.put("SM6", Constants.MALARIA_NEGATIVE_US);
        codeVsQuestionMappingGroup2.put("SM8", Constants.CHILDREN_PRESENTED_APE);
        codeVsQuestionMappingGroup2.put("SM10", Constants.MALARIA_POSITIVE_APE);
        codeVsQuestionMappingGroup2.put("SM12", Constants.MALARIA_NEGATIVE_APE);
    }
}
