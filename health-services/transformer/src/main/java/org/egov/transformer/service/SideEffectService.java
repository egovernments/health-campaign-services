package org.egov.transformer.service;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.contract.request.User;
import org.egov.tracer.model.CustomException;
import org.egov.transformer.config.TransformerProperties;
import org.egov.transformer.http.client.ServiceRequestClient;
import org.egov.transformer.utils.CommonUtils;
import org.springframework.stereotype.Component;
import org.egov.common.models.project.*;


import java.util.Collections;
import java.util.List;
import java.util.Map;


@Component
@Slf4j
public class SideEffectService {

    private final TransformerProperties properties;

    private final ServiceRequestClient serviceRequestClient;

    private final CommonUtils commonUtils;

    private final ProjectService projectService;

    public SideEffectService(TransformerProperties stockConfiguration, ServiceRequestClient serviceRequestClient, CommonUtils commonUtils, ProjectService projectService) {
        this.properties = stockConfiguration;
        this.serviceRequestClient = serviceRequestClient;
        this.commonUtils = commonUtils;
        this.projectService = projectService;
    }

    public List<Task> getTaskFromTaskClientReferenceId(String taskClientReferenceId, String tenantId) {
        TaskSearchRequest taskSearchRequest = TaskSearchRequest.builder()
                .task(TaskSearch.builder().clientReferenceId(Collections.singletonList(taskClientReferenceId)).build())
                .requestInfo(RequestInfo.builder().
                        userInfo(User.builder()
                                .uuid("transformer-uuid")
                                .build())
                        .build())
                .build();
        TaskBulkResponse response;
        try {
            response = serviceRequestClient.fetchResult(
                    new StringBuilder(properties.getProjectHost()
                            + properties.getProjectTaskSearchUrl()
                            + "?limit=1"
                            + "&offset=0&tenantId=" + tenantId),
                    taskSearchRequest,
                    TaskBulkResponse.class);

        } catch (Exception e) {
            log.error("error while fetching Task Details: {}", ExceptionUtils.getStackTrace(e));
            throw new CustomException("TASK_FETCH_ERROR",
                    "error while fetching task details for id: " + taskClientReferenceId);
        }

        return response.getTasks();
    }

    public ObjectNode getBoundaryHierarchyFromTask(Task task, String tenantId) {
        Map<String, String> boundaryLabelToNameMap = null;
        if (task.getAddress() != null && task.getAddress().getLocality() != null && task.getAddress().getLocality().getCode() != null) {
            boundaryLabelToNameMap = projectService
                    .getBoundaryCodeToNameMap(task.getAddress().getLocality().getCode(), tenantId);
        } else {
            boundaryLabelToNameMap = projectService
                    .getBoundaryCodeToNameMapByProjectId(task.getProjectId(), tenantId);
        }
        Project project = projectService.getProject(task.getProjectId(), tenantId);
        String projectTypeId = project.getProjectTypeId();
        log.info("boundary labels {}", boundaryLabelToNameMap.toString());
        return (ObjectNode) commonUtils.getBoundaryHierarchy(tenantId, projectTypeId, boundaryLabelToNameMap);
    }
}