package org.egov.transformer.service;


import lombok.extern.slf4j.Slf4j;
import org.egov.transformer.config.TransformerProperties;
import org.egov.transformer.enums.Operation;
import org.egov.transformer.models.downstream.ServiceIndexV1;
import org.egov.transformer.models.upstream.Service;
import org.egov.transformer.models.upstream.ServiceDefinition;
import org.egov.common.producer.Producer;
import org.egov.transformer.service.transformer.Transformer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
@Slf4j
public abstract class ServiceTaskTransformationService implements TransformationService<Service> {

    protected final ServiceTaskIndexV1Transformer transformer;

    protected final Producer producer;

    protected final TransformerProperties properties;

    @Autowired
    protected ServiceTaskTransformationService(ServiceTaskTransformationService.ServiceTaskIndexV1Transformer transformer,
                                                Producer producer, TransformerProperties properties) {
        this.transformer = transformer;
        this.producer = producer;
        this.properties = properties;
    }

    @Override
    public void transform(List<Service> payloadList) {
        log.info("transforming for ids {}", payloadList.stream()
                .map(Service::getId).collect(Collectors.toList()));
        List<ServiceIndexV1> transformedPayloadList = payloadList.stream()
                .map(transformer::transform)
                .flatMap(Collection::stream)
                .collect(Collectors.toList());
        log.info("transformation successful");
        producer.push(getTopic(),
                transformedPayloadList);
    }

    public abstract String getTopic();

    @Override
    public Operation getOperation() {
        return Operation.SERVICE;
    }

    @Component
    static class ServiceTaskIndexV1Transformer implements
            Transformer<Service, ServiceIndexV1> {
        private final ProjectService projectService;
        private final TransformerProperties properties;
        private final ServiceDefinitionService serviceDefinitionService;

        @Autowired
        ServiceTaskIndexV1Transformer(ProjectService projectService, TransformerProperties properties, ServiceDefinitionService serviceDefinitionService) {

            this.projectService = projectService;
            this.properties = properties;
            this.serviceDefinitionService = serviceDefinitionService;
        }

        @Override
        public List<ServiceIndexV1> transform(Service service) {

            ServiceDefinition serviceDefinition = serviceDefinitionService.getServiceDefinition(service.getServiceDefId(), service.getTenantId());
            String[] parts = serviceDefinition.getCode().split("\\.");
            String projectName = parts[0];
            String supervisorLevel = parts[2];
            String projectId = projectService.getProjectByName(projectName, service.getTenantId()).getId();
            Map<String, String> boundaryLabelToNameMap = projectService.getBoundaryCodeToNameMapByProjectId(projectId, service.getTenantId());
            log.info("boundary labels {}", boundaryLabelToNameMap.toString());

            return Collections.singletonList(ServiceIndexV1.builder()
                    .id(service.getId())
                    .projectId(projectId)
                    .serviceDefinitionId(service.getServiceDefId())
                    .supervisorLevel(supervisorLevel)
                    .checklistName(serviceDefinition.getCode())
                    .province(boundaryLabelToNameMap.get(properties.getProvince()))
                    .district(boundaryLabelToNameMap.get(properties.getDistrict()))
                    .createdTime(service.getAuditDetails().getCreatedTime())
                    .createdBy(service.getAuditDetails().getCreatedBy())
                    .tenantId(service.getTenantId())
                    .userId(service.getAccountId())
                    .attributes(service.getAttributes())
                    .build());
        }
    }
}
