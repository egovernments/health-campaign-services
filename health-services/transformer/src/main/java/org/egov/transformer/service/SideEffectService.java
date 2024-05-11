package org.egov.transformer.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.contract.request.User;
import org.egov.transformer.config.TransformerProperties;
import org.egov.transformer.http.client.ServiceRequestClient;
import org.springframework.stereotype.Component;
import org.egov.common.models.project.*;


import java.util.Collections;
import java.util.List;


@Component
@Slf4j
public class SideEffectService {

    private final TransformerProperties properties;

    private final ServiceRequestClient serviceRequestClient;


    public SideEffectService(TransformerProperties stockConfiguration, ServiceRequestClient serviceRequestClient) {
        this.properties = stockConfiguration;
        this.serviceRequestClient = serviceRequestClient;
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
            log.error("error while fetching Task Details for id: {},  Exception: {}", taskClientReferenceId, ExceptionUtils.getStackTrace(e));
            return Collections.emptyList();
        }

        return response.getTasks();
    }
}