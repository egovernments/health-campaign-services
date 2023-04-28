package org.egov.transformer.service;


import lombok.extern.slf4j.Slf4j;
import org.egov.servicerequest.web.models.Service;
import org.egov.transformer.config.TransformerProperties;
import org.egov.transformer.enums.Operation;
import org.egov.transformer.models.downstream.ServiceTaskIndexV1;
import org.egov.transformer.producer.Producer;
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
        List<ServiceTaskIndexV1> transformedPayloadList = payloadList.stream()
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
            Transformer<Service, ServiceTaskIndexV1> {
        private final ProjectService projectService;
        private final TransformerProperties properties;

        @Autowired
        ServiceTaskIndexV1Transformer(ProjectService projectService, TransformerProperties properties) {
            this.projectService = projectService;
            this.properties = properties;
        }

        @Override
        public List<ServiceTaskIndexV1> transform(Service service) {
            Map<String, String> boundaryLabelToNameMap = projectService
                    .getBoundaryLabelToNameMap(service.getProjectId(), service.getTenantId());
            log.info("boundary labels {}", boundaryLabelToNameMap.toString());
            return Collections.singletonList(ServiceTaskIndexV1.builder()
                    .id(service.getId())
                    .projectId(service.getProjectId())
                    .serviceDefinitionId(service.getServiceDefId())
                    .supervisorLevel(service.getSupervisorLevel())
                    .checklistName(service.getCheckListName())
                    .province(boundaryLabelToNameMap.get(properties.getProvince()))
                    .district(boundaryLabelToNameMap.get(properties.getDistrict()))
                    .createdTime(service.getAuditDetails().getCreatedTime())
                    .createdBy(service.getAuditDetails().getCreatedBy())
                    .build());
        }
    }
}
