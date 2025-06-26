package org.egov.transformer.transformationservice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.egov.common.models.project.Project;
import org.egov.transformer.config.TransformerProperties;
import org.egov.transformer.models.boundary.BoundaryHierarchyResult;
import org.egov.transformer.models.downstream.ServiceIndexV1;
import org.egov.transformer.models.upstream.Service;
import org.egov.transformer.models.upstream.ServiceDefinition;
import org.egov.transformer.producer.Producer;
import org.egov.transformer.service.*;
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
    private final BoundaryService boundaryService;
    private final ProjectFactoryService projectFactoryService;

    public ServiceTaskTransformationService(Producer producer, TransformerProperties transformerProperties, ObjectMapper objectMapper, ServiceDefinitionService serviceDefinitionService, ProjectService projectService, CommonUtils commonUtils, UserService userService, BoundaryService boundaryService, ProjectFactoryService projectFactoryService) {
        this.producer = producer;
        this.transformerProperties = transformerProperties;
        this.objectMapper = objectMapper;
        this.serviceDefinitionService = serviceDefinitionService;
        this.projectService = projectService;
        this.commonUtils = commonUtils;
        this.userService = userService;
        this.boundaryService = boundaryService;
        this.projectFactoryService = projectFactoryService;
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
        Project project = projectService.getProject(projectId, tenantId);
        String projectType = project.getProjectType();
        String projectTypeId = project.getProjectTypeId();
        JsonNode serviceAdditionalDetails = service.getAdditionalDetails();
        JsonNode serviceAdditionalFields = service.getAdditionalFields();
        String localityCode = commonUtils.getLocalityCodeFromAdditionalFields(serviceAdditionalFields, serviceAdditionalDetails);
        List<Double> geoPoint = commonUtils.getGeoPointFromAdditionalFields(serviceAdditionalFields, serviceAdditionalDetails);
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
        ObjectNode additionalDetails = objectMapper.createObjectNode();
        if(serviceAdditionalDetails != null && serviceAdditionalDetails.isObject()) {
            additionalDetails = (ObjectNode) serviceAdditionalDetails;
        }
        additionalDetails.put(CYCLE_INDEX, cycleIndex);

        ServiceIndexV1 serviceIndexV1 = ServiceIndexV1.builder()
                .id(service.getId())
                .clientReferenceId(service.getClientId())
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
        serviceIndexV1.setProjectInfo(projectId, projectType, projectTypeId, project.getName());
        serviceIndexV1.setCampaignNumber(project.getReferenceID());
        serviceIndexV1.setCampaignId(StringUtils.isNotBlank(project.getReferenceID()) ? projectFactoryService.getCampaignIdFromCampaignNumber(project.getTenantId(), true, project.getReferenceID()) : null);
        return serviceIndexV1;
    }
}
