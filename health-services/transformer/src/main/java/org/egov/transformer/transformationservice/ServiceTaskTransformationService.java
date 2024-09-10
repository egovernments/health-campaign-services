package org.egov.transformer.transformationservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.project.Project;
import org.egov.transformer.config.TransformerProperties;
import org.egov.transformer.models.downstream.ServiceIndexV1;
import org.egov.transformer.models.upstream.*;
import org.egov.transformer.producer.Producer;
import org.egov.transformer.service.ProjectService;
import org.egov.transformer.service.ServiceDefinitionService;
import org.egov.transformer.service.UserService;
import org.egov.transformer.utils.CommonUtils;
import org.springframework.stereotype.Component;

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

    public ServiceTaskTransformationService(Producer producer, TransformerProperties transformerProperties, ObjectMapper objectMapper, ServiceDefinitionService serviceDefinitionService, ProjectService projectService, CommonUtils commonUtils, UserService userService) {
        this.producer = producer;
        this.transformerProperties = transformerProperties;
        this.objectMapper = objectMapper;
        this.serviceDefinitionService = serviceDefinitionService;
        this.projectService = projectService;
        this.commonUtils = commonUtils;
        this.userService = userService;
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
        Project project = projectService.getProject(projectId, tenantId);
        String projectTypeId = project.getProjectTypeId();
        if (service.getAdditionalDetails() != null) {
            boundaryHierarchy = projectService.getBoundaryHierarchyWithLocalityCode((String) service.getAdditionalDetails(), tenantId);
        } else {
            boundaryHierarchy = projectService.getBoundaryHierarchyWithProjectId(projectId, tenantId);
        }
        String syncedTimeStamp = commonUtils.getTimeStampFromEpoch(service.getAuditDetails().getCreatedTime());
        Map<String, String> userInfoMap = userService.getUserInfo(service.getTenantId(), service.getAuditDetails().getCreatedBy());
        String cycleIndex = commonUtils.fetchCycleIndex(tenantId, projectTypeId, service.getAuditDetails());

        log.info("NULL_POINTER_FIX_LOG, cycleIndex is: {}", cycleIndex);

        ObjectNode additionalDetails = objectMapper.createObjectNode();
        additionalDetails.put(CYCLE_INDEX, cycleIndex);
        List<Double> geoPoint = !service.getAttributes().isEmpty() ? getGeoPoint(service.getAttributes().get(0).getAdditionalFields()) : null;

        log.info("NULL_POINTER_FIX_LOG, additionalDetails is: {}", additionalDetails);
        log.info("NULL_POINTER_FIX_LOG, service.getId() is: {}", service.getId());
        log.info("NULL_POINTER_FIX_LOG, service.getClientId() is: {}", service.getClientId());
        log.info("NULL_POINTER_FIX_LOG, projectId is: {}", projectId);
        log.info("NULL_POINTER_FIX_LOG, service.getServiceDefId() is: {}", service.getServiceDefId());
        log.info("NULL_POINTER_FIX_LOG, supervisorLevel is: {}", supervisorLevel);
        log.info("NULL_POINTER_FIX_LOG, parts[1] is: {}", parts[1]);
        log.info("NULL_POINTER_FIX_LOG, userInfoMap is: {}", userInfoMap);
        log.info("NULL_POINTER_FIX_LOG, service.getAttributes() is: {}", service.getAttributes());
        log.info("NULL_POINTER_FIX_LOG, geoPoint is: {}", geoPoint);
        log.info("NULL_POINTER_FIX_LOG, boundaryHierarchy is: {}", boundaryHierarchy);
        log.info("NULL_POINTER_FIX_LOG, additionalDetails is: {}", additionalDetails);


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
                .geoPoint(geoPoint)
                .syncedTime(service.getAuditDetails().getLastModifiedTime())
                .syncedTimeStamp(syncedTimeStamp)
                .boundaryHierarchy(boundaryHierarchy)
                .additionalDetails(additionalDetails)
                .build();
        log.info("RETURING SINGLE TRANSFORMED SERVICE TASK TO COLLECT AS LIST: {}", serviceIndexV1);
        return serviceIndexV1;
    }

    private List<Double> getGeoPoint(AdditionalFields additionalFields) {
        if (additionalFields == null || additionalFields.getFields() == null || additionalFields.getFields().isEmpty()) {
            return null;
        }

        Double lat = null, lng = null;

        for (Field field : additionalFields.getFields()) {
            switch (field.getKey()) {
                case LATITUDE:
                    lat = Double.valueOf(field.getValue());
                    break;
                case LONGITUDE:
                    lng = Double.valueOf(field.getValue());
                    break;
            }
        }

        if (lat != null && lng != null) {
            return Arrays.asList(lng, lat);
        }
        return null;
    }
}
