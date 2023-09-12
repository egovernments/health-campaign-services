package org.egov.adrm.validator.adverseevent;

import lombok.extern.slf4j.Slf4j;
import org.egov.adrm.config.AdrmConfiguration;
import org.egov.common.data.query.exception.QueryBuilderException;
import org.egov.common.http.client.ServiceRequestClient;
import org.egov.common.models.Error;
import org.egov.common.models.project.Task;
import org.egov.common.models.project.TaskBulkResponse;
import org.egov.common.models.project.TaskSearch;
import org.egov.common.models.project.TaskSearchRequest;
import org.egov.common.models.project.adverseevent.AdverseEvent;
import org.egov.common.models.project.adverseevent.AdverseEventBulkRequest;
import org.egov.common.validator.Validator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.egov.common.utils.CommonUtils.notHavingErrors;
import static org.egov.common.utils.CommonUtils.populateErrorDetails;
import static org.egov.common.utils.ValidatorUtils.getErrorForNonExistentEntity;


@Component
@Order(value = 3)
@Slf4j
public class AdProjectTaskIdValidator implements Validator<AdverseEventBulkRequest, AdverseEvent> {
    private final ServiceRequestClient serviceRequestClient;
    private final AdrmConfiguration adrmConfiguration;

    @Autowired
    public AdProjectTaskIdValidator(ServiceRequestClient serviceRequestClient, AdrmConfiguration adrmConfiguration) {
        this.serviceRequestClient = serviceRequestClient;
        this.adrmConfiguration = adrmConfiguration;
    }


    @Override
    public Map<AdverseEvent, List<Error>> validate(AdverseEventBulkRequest request) {
        log.info("validating project task id");
        Map<AdverseEvent, List<Error>> errorDetailsMap = new HashMap<>();
        List<AdverseEvent> entities = request.getAdverseEvents();
        Map<String, List<String>> tenantIdTaskIdMap = entities.stream().collect(Collectors.toMap(ad -> ad.getTenantId(), ad -> Arrays.asList(ad.getTaskId()), (e, d) -> { e.addAll(d); return e;}));
        Map<String, List<String>> tenantIdTaskReferenceIdMap = entities.stream().collect(Collectors.toMap(ad -> ad.getTenantId(), ad -> Arrays.asList(ad.getTaskClientReferenceId()), (e, d) -> { e.addAll(d); return e;}));
        List<String> tenantIds = new ArrayList<>(tenantIdTaskIdMap.keySet());
        tenantIds.forEach(tenantId -> {
            List<String> taskIdList = tenantIdTaskIdMap.get(tenantId);
            List<String> taskReferenceIdList = tenantIdTaskReferenceIdMap.get(tenantId);
            if (!taskIdList.isEmpty() || !taskReferenceIdList.isEmpty()) {
                List<Task> existingTasks;
                try {
                    taskIdList = taskIdList.stream().filter(Objects::nonNull).collect(Collectors.toList());
                    taskReferenceIdList = taskReferenceIdList.stream().filter(Objects::nonNull).collect(Collectors.toList());
                    TaskSearch taskSearch = TaskSearch.builder()
                            .id(taskIdList.isEmpty() ? null : taskIdList)
                            .clientReferenceId(taskReferenceIdList.isEmpty() ? null : taskReferenceIdList).build();
                    TaskBulkResponse response = serviceRequestClient.fetchResult(
                            new StringBuilder(adrmConfiguration.getProjectTaskHost()
                                    + adrmConfiguration.getProjectTaskSearchUrl()
                                    + "&offset=0&tenantId=" + tenantId),
                            TaskSearchRequest.builder().requestInfo(request.getRequestInfo()).task(taskSearch).build(),
                            TaskBulkResponse.class);
                    existingTasks = response.getTasks();
                } catch (QueryBuilderException e) {
                    existingTasks = Collections.emptyList();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                List<String> existingProjectTaskIds = existingTasks.stream().map(t -> t.getId()).collect(Collectors.toList());
                List<String> existingProjectReferenceTaskIds = existingTasks.stream().map(t -> t.getClientReferenceId()).collect(Collectors.toList());
                List<AdverseEvent> invalidEntities = entities.stream().filter(notHavingErrors()).filter(entity ->
                                !existingProjectTaskIds.contains(entity.getTaskId()) && !existingProjectReferenceTaskIds.contains(entity.getTaskClientReferenceId()) )
                        .collect(Collectors.toList());
                invalidEntities.forEach(adverseEvent -> {
                    Error error = getErrorForNonExistentEntity();
                    populateErrorDetails(adverseEvent, error, errorDetailsMap);
                });
            }
        });

        return errorDetailsMap;
    }
}
