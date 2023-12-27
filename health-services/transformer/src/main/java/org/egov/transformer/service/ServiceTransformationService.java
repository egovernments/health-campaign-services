package org.egov.transformer.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.egov.transformer.Constants;
import org.egov.transformer.config.TransformerProperties;
import org.egov.transformer.models.downstream.ServiceIndexV2;
import org.egov.transformer.models.upstream.AttributeValue;
import org.egov.transformer.models.upstream.Service;
import org.egov.transformer.models.upstream.ServiceDefinition;
import org.egov.transformer.producer.Producer;
import org.springframework.stereotype.Component;

import java.util.*;

@Slf4j
@Component
public class ServiceTransformationService {

    private final ObjectMapper objectMapper;
    private final ServiceDefinitionService serviceDefinitionService;
    private final TransformerProperties transformerProperties;
    private final Producer producer;


    public ServiceTransformationService(ObjectMapper objectMapper, ServiceDefinitionService serviceDefinitionService, TransformerProperties transformerProperties, Producer producer) {

        this.objectMapper = objectMapper;
        this.serviceDefinitionService = serviceDefinitionService;
        this.transformerProperties = transformerProperties;
        this.producer = producer;
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
        String[] parts = serviceDefinition.getCode().split("\\.");
        String projectName = parts[0];
        String supervisorLevel = parts[2];
        // populate them from env
        String checkListToFilter = transformerProperties.getCheckListName().trim();
        List<AttributeValue> attributeValueList = service.getAttributes();
        Map<String, Map<String, String>> attributeCodeToQuestionAgeGroup = new HashMap<>();
        getAttributeCodeMappings(attributeCodeToQuestionAgeGroup);
        if (checkListName.trim().equals(checkListToFilter)) {
            attributeCodeToQuestionAgeGroup.forEach((key, value) -> {
                ServiceIndexV2 serviceIndexV2 = ServiceIndexV2.builder()
                        .id(service.getId())
                        .supervisorLevel(supervisorLevel)
                        .checklistName(checkListName)
                        .ageGroup(key)
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
            case Constants.CHILDREN_PRESENTED: {
                serviceIndexV2.setChildrenPresented(value);
                break;
            }
            case Constants.FEVER_POSITIVE: {
                serviceIndexV2.setFeverPositive(value);
                break;
            }
            case Constants.FEVER_NEGATIVE: {
                serviceIndexV2.setFeverNegative(value);
                break;
            }
            case Constants.REFERRED_CHILDREN_TO_APE: {
                serviceIndexV2.setReferredChildrenToAPE(value);
                break;
            }
            case Constants.REFERRED_CHILDREN_PRESENTED_TO_APE: {
                serviceIndexV2.setReferredChildrenPresentedToAPE(value);
                break;
            }
            case Constants.POSITIVE_MALARIA: {
                serviceIndexV2.setPositiveMalaria(value);
                break;
            }
            case Constants.NEGATIVE_MALARIA: {
                serviceIndexV2.setNegativeMalaria(value);
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
        codeVsQuestionMappingGroup1.put("SM1", Constants.CHILDREN_PRESENTED);
        codeVsQuestionMappingGroup1.put("SM3", Constants.FEVER_POSITIVE);
        codeVsQuestionMappingGroup1.put("SM5", Constants.FEVER_NEGATIVE);
        codeVsQuestionMappingGroup1.put("SM7", Constants.REFERRED_CHILDREN_TO_APE);
        codeVsQuestionMappingGroup1.put("SM9", Constants.REFERRED_CHILDREN_PRESENTED_TO_APE);
        codeVsQuestionMappingGroup1.put("SM11", Constants.POSITIVE_MALARIA);
        codeVsQuestionMappingGroup1.put("SM13", Constants.NEGATIVE_MALARIA);
    }

    private static void getGroup2Map(Map<String, String> codeVsQuestionMappingGroup2) {
        codeVsQuestionMappingGroup2.put("SM2", Constants.CHILDREN_PRESENTED);
        codeVsQuestionMappingGroup2.put("SM4", Constants.FEVER_POSITIVE);
        codeVsQuestionMappingGroup2.put("SM6", Constants.FEVER_NEGATIVE);
        codeVsQuestionMappingGroup2.put("SM8", Constants.REFERRED_CHILDREN_TO_APE);
        codeVsQuestionMappingGroup2.put("SM10", Constants.REFERRED_CHILDREN_PRESENTED_TO_APE);
        codeVsQuestionMappingGroup2.put("SM12", Constants.POSITIVE_MALARIA);
        codeVsQuestionMappingGroup2.put("SM14", Constants.NEGATIVE_MALARIA);
    }
}
