package org.egov.transformer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.contract.request.User;
import org.egov.transformer.Constants;
import org.egov.transformer.config.TransformerProperties;
import org.egov.transformer.http.client.ServiceRequestClient;
import org.egov.transformer.utils.CommonUtils;
import org.springframework.stereotype.Service;
import org.egov.common.models.project.*;


import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.egov.transformer.Constants.*;


@Service
@Slf4j
public class SideEffectService {

    private final TransformerProperties properties;

    private final ServiceRequestClient serviceRequestClient;

    private final CommonUtils commonUtils;

    private final ProjectService projectService;
    private final ObjectMapper objectMapper;
    public SideEffectService(TransformerProperties stockConfiguration, ServiceRequestClient serviceRequestClient, CommonUtils commonUtils, ProjectService projectService, ObjectMapper objectMapper) {
        this.properties = stockConfiguration;
        this.serviceRequestClient = serviceRequestClient;
        this.commonUtils = commonUtils;
        this.projectService = projectService;
        this.objectMapper = objectMapper;
    }
    public ObjectNode getBoundaryHierarchyFromTaskClientRefId(String taskClientReferenceId, String tenantId){
        TaskSearchRequest taskSearchRequest = TaskSearchRequest.builder()
                .task(TaskSearch.builder().clientReferenceId(Collections.singletonList(taskClientReferenceId)).build())
                .requestInfo(RequestInfo.builder().
                        userInfo(User.builder()
                                .uuid("transformer-uuid")
                                .build())
                        .build())
                .build();
        TaskBulkResponse response;
        ObjectNode boundaryHierarchy = objectMapper.createObjectNode();
        try {
            response = serviceRequestClient.fetchResult(
                    new StringBuilder(properties.getProjectHost()
                            + properties.getProjectTaskSearchUrl()
                            + "?limit=1"
                            + "&offset=0&tenantId=" + tenantId),
                    taskSearchRequest,
                    TaskBulkResponse.class);
            Task task = response.getTasks().get(0);
            Map<String, String> boundaryLabelToNameMap = null;
            if (task.getAddress().getLocality() != null && task.getAddress().getLocality().getCode() != null) {
                boundaryLabelToNameMap = projectService
                        .getBoundaryLabelToNameMap(task.getAddress().getLocality().getCode(), tenantId);
            } else {
                boundaryLabelToNameMap = projectService
                        .getBoundaryLabelToNameMapByProjectId(task.getProjectId(), tenantId);
            }
            Project project = projectService.getProject(task.getProjectId(),tenantId);
            String projectTypeId = project.getProjectTypeId();
            JsonNode mdmsBoundaryData = projectService.fetchBoundaryData(tenantId, null,projectTypeId);
            List<JsonNode> boundaryLevelVsLabel = StreamSupport
                    .stream(mdmsBoundaryData.get(Constants.BOUNDARY_HIERARCHY).spliterator(), false).collect(Collectors.toList());
            log.info("boundary labels {}", boundaryLabelToNameMap.toString());
            Map<String, String> finalBoundaryLabelToNameMap = boundaryLabelToNameMap;

            boundaryLevelVsLabel.stream()
                    .filter(node -> node.get(LEVEL).asInt() > 1)
                    .forEach(node -> {
                        String label = node.get(INDEX_LABEL).asText();
                        String name = Optional.ofNullable(finalBoundaryLabelToNameMap.get(node.get(LABEL).asText()))
                                .orElse(null);
                        boundaryHierarchy.put(label, name);
                    });

        } catch (Exception e) {
            log.error("error while fetching Task Details: {}", ExceptionUtils.getStackTrace(e));
        }


        return boundaryHierarchy;
    }
}