package org.egov.project.validator.adverseevent;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.data.query.exception.QueryBuilderException;
import org.egov.common.models.Error;
import org.egov.common.models.adrm.adverseevent.AdverseEvent;
import org.egov.common.models.adrm.adverseevent.AdverseEventBulkRequest;
import org.egov.common.models.project.Task;
import org.egov.common.models.project.TaskSearch;
import org.egov.common.validator.Validator;
import org.egov.project.repository.ProjectTaskRepository;
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

import static org.egov.common.utils.CommonUtils.*;
import static org.egov.common.utils.ValidatorUtils.getErrorForNonExistentEntity;


@Component
@Order(value = 3)
@Slf4j
public class AdProjectTaskIdValidator implements Validator<AdverseEventBulkRequest, AdverseEvent> {
    private final ProjectTaskRepository projectTaskRepository;

    @Autowired
    public AdProjectTaskIdValidator(ProjectTaskRepository projectTaskRepository) {
        this.projectTaskRepository = projectTaskRepository;
    }


    @Override
    public Map<AdverseEvent, List<Error>> validate(AdverseEventBulkRequest request) {
        log.info("validating project task id");
        Map<AdverseEvent, List<Error>> errorDetailsMap = new HashMap<>();
        List<AdverseEvent> entities = request.getAdverseEvents();
        Map<String, List<String>> tenantIdTaskIdMap = entities.stream().collect(Collectors.groupingBy(AdverseEvent::getTenantId, Collectors.mapping(AdverseEvent::getTaskId, Collectors.toList())));
        Map<String, List<String>> tenantIdTaskReferenceIdMap = entities.stream().collect(Collectors.groupingBy(AdverseEvent::getTenantId, Collectors.mapping(AdverseEvent::getTaskClientReferenceId, Collectors.toList())));
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
                    existingTasks = projectTaskRepository.find(
                            taskSearch,
                            taskReferenceIdList.size(), 0, tenantId,null, Boolean.TRUE
                    );
                } catch (QueryBuilderException e) {
                    existingTasks = Collections.emptyList();
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
