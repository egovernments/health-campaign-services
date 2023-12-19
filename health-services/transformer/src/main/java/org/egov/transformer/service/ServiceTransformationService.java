package org.egov.transformer.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
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
            transform(service,serviceIndexV2List);
        });
        if(!serviceIndexV2List.isEmpty()){
            producer.push(topic,serviceIndexV2List);
        }
    }

    private void transform(Service service,List<ServiceIndexV2> serviceIndexV2List) {
        ServiceDefinition serviceDefinition = serviceDefinitionService.getServiceDefinition(service.getServiceDefId(), service.getTenantId());
        String checkListName = serviceDefinition.getCode();
        String[] parts = serviceDefinition.getCode().split("\\.");
        String projectName = parts[0];
        String supervisorLevel = parts[2];
        // populate them from env
        String checkListToFilter = transformerProperties.getCheckListName();
        List<String> codesToFind = new ArrayList<>();
        Map<String, String> codeVsValue = new HashMap<>();
        codesToFind.forEach(code -> {
            String value = getAttribute(service, code);
            codeVsValue.put(code, value);
        });
        if (checkListName.equals(checkListToFilter)) {
            ServiceIndexV2 serviceIndexV2 = ServiceIndexV2.builder()
                    .id(service.getId())
                    .supervisorLevel(supervisorLevel)
                    .checklistName(checkListName)
                    .build();
            if (serviceIndexV2.getAttribute() == null) {
                serviceIndexV2.setAttribute(objectMapper.createObjectNode());
            } else {
                codeVsValue.forEach((code, value) -> {
                    serviceIndexV2.getAttribute().put(code, value);
                });
            }
            serviceIndexV2List.add(serviceIndexV2);
        }
    }

    public static String getAttribute(Service service, String attributeCode) {
        Optional<AttributeValue> attributeValue = service.getAttributes().stream().filter(attribute ->
                attribute.getAttributeCode().equals(attributeCode)).findFirst();
        Object value = attributeValue.get().getValue();
        return value.toString();
    }
}
