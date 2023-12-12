package org.egov.transformer.service;


import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.request.User;
import org.egov.transformer.config.TransformerProperties;
import org.egov.transformer.enums.Operation;
import org.egov.transformer.models.downstream.ServiceIndexV1;
import org.egov.transformer.models.upstream.Service;
import org.egov.transformer.models.upstream.ServiceDefinition;
import org.egov.transformer.producer.Producer;
import org.egov.transformer.service.transformer.Transformer;
import org.egov.transformer.utils.CommonUtils;
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
    protected final CommonUtils commonUtils;
    @Autowired
    protected ServiceTaskTransformationService(ServiceTaskTransformationService.ServiceTaskIndexV1Transformer transformer,
                                               Producer producer, TransformerProperties properties, CommonUtils commonUtils) {
        this.transformer = transformer;
        this.producer = producer;
        this.properties = properties;
        this.commonUtils = commonUtils;
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
        private final CommonUtils commonUtils;
        private UserService userService;
        @Autowired
        ServiceTaskIndexV1Transformer(ProjectService projectService, TransformerProperties properties, ServiceDefinitionService serviceDefinitionService, CommonUtils commonUtils, UserService userService) {

            this.projectService = projectService;
            this.properties = properties;
            this.serviceDefinitionService = serviceDefinitionService;
            this.commonUtils = commonUtils;
            this.userService = userService;
        }

        @Override
        public List<ServiceIndexV1> transform(Service service) {

            ServiceDefinition serviceDefinition = serviceDefinitionService.getServiceDefinition(service.getServiceDefId(), service.getTenantId());
            String[] parts = serviceDefinition.getCode().split("\\.");
            String projectName = parts[0];
            String supervisorLevel = parts[2];
            String projectId = null;
            if (service.getAccountId() != null) {
                projectId = service.getAccountId();
            } else {
                projectId = projectService.getProjectByName(projectName, service.getTenantId()).getId();
            }
            Map<String, String> boundaryLabelToNameMap = null;
            if (service.getAdditionalDetails() != null) {
                boundaryLabelToNameMap = projectService
                        .getBoundaryLabelToNameMap((String) service.getAdditionalDetails(), service.getTenantId());
            } else {
                boundaryLabelToNameMap = projectService.getBoundaryLabelToNameMapByProjectId(projectId, service.getTenantId());
            }
            log.info("boundary labels {}", boundaryLabelToNameMap.toString());

            List<User> users = userService.getUsers(service.getTenantId(), service.getAuditDetails().getCreatedBy());
            String syncedTimeStamp = commonUtils.getTimeStampFromEpoch(service.getAuditDetails().getCreatedTime());

            return Collections.singletonList(ServiceIndexV1.builder()
                    .id(service.getId())
                    .clientReferenceId(service.getClientId())
                    .projectId(projectId)
                    .serviceDefinitionId(service.getServiceDefId())
                    .supervisorLevel(supervisorLevel)
                    .checklistName(serviceDefinition.getCode())
                    .userName(userService.getUserName(users,service.getAuditDetails().getCreatedBy()))
                    .role(userService.getStaffRole(service.getTenantId(),users))
                    .province(boundaryLabelToNameMap.get(properties.getProvince()))
                    .district(boundaryLabelToNameMap.get(properties.getDistrict()))
                    .administrativeProvince(boundaryLabelToNameMap != null ?
                            boundaryLabelToNameMap.get(properties.getAdministrativeProvince()) : null)
                    .locality(boundaryLabelToNameMap != null ? boundaryLabelToNameMap.get(properties.getLocality()) : null)
                    .healthFacility(boundaryLabelToNameMap != null ? boundaryLabelToNameMap.get(properties.getHealthFacility()) : null)
                    .village(boundaryLabelToNameMap != null ? boundaryLabelToNameMap.get(properties.getVillage()) : null)
                    .createdTime(service.getAuditDetails().getCreatedTime())
                    .createdBy(service.getAuditDetails().getCreatedBy())
                    .tenantId(service.getTenantId())
                    .userId(service.getAccountId())
                    .attributes(service.getAttributes())
                    .syncedTime(service.getAuditDetails().getCreatedTime())
                    .syncedTimeStamp(syncedTimeStamp)
                    .build());
        }
    }
}
