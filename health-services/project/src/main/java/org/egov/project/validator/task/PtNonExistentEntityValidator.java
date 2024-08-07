package org.egov.project.validator.task;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.Error;
import org.egov.common.models.project.Task;
import org.egov.common.models.project.TaskBulkRequest;
import org.egov.common.models.project.TaskSearch;
import org.egov.common.validator.Validator;
import org.egov.project.repository.ProjectTaskRepository;
import org.egov.tracer.model.CustomException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.ReflectionUtils;

import static org.egov.common.utils.CommonUtils.checkNonExistentEntities;
import static org.egov.common.utils.CommonUtils.getIdMethod;
import static org.egov.common.utils.CommonUtils.getIdToObjMap;
import static org.egov.common.utils.CommonUtils.getMethod;
import static org.egov.common.utils.CommonUtils.getObjClass;
import static org.egov.common.utils.CommonUtils.notHavingErrors;
import static org.egov.common.utils.CommonUtils.populateErrorDetails;
import static org.egov.common.utils.ValidatorUtils.getErrorForNonExistentEntity;
import static org.egov.common.utils.ValidatorUtils.getErrorForNonExistentSubEntity;
import static org.egov.project.Constants.GET_ADDRESS;
import static org.egov.project.Constants.GET_ID;
import static org.egov.project.Constants.GET_RESOURCES;

@Component
@Order(value = 4)
@Slf4j
public class PtNonExistentEntityValidator implements Validator<TaskBulkRequest, Task> {

    private final ProjectTaskRepository projectTaskRepository;

    @Autowired
    public PtNonExistentEntityValidator(ProjectTaskRepository projectTaskRepository) {
        this.projectTaskRepository = projectTaskRepository;
    }


    @Override
    public Map<Task, List<Error>> validate(TaskBulkRequest request) {
        log.info("validating for existence of entity");
        Map<Task, List<Error>> errorDetailsMap = new HashMap<>();
        List<Task> entities = request.getTasks();
        Class<?> objClass = getObjClass(entities);
        Method idMethod = getMethod(GET_ID, objClass);
        Map<String, Task> eMap = getIdToObjMap(entities
                .stream().filter(notHavingErrors()).collect(Collectors.toList()), idMethod);
        // Lists to store IDs and client reference IDs
        List<String> idList = new ArrayList<>();
        List<String> clientReferenceIdList = new ArrayList<>();
        // Extract IDs and client reference IDs from Project Task entities
        entities.forEach(entity -> {
            idList.add(entity.getId());
            clientReferenceIdList.add(entity.getClientReferenceId());
        });
        if (!eMap.isEmpty()) {
            TaskSearch taskSearch = TaskSearch.builder()
                    .clientReferenceId(clientReferenceIdList)
                    .id(idList)
                    .build();

            List<Task> existingEntities;
            try {
                // Query the repository to find existing entities
                existingEntities = projectTaskRepository.find(taskSearch, entities.size(), 0,
                        entities.get(0).getTenantId(), null, false).getResponse();
            } catch (Exception e) {
                // Handle query builder exception
                log.error("Search failed for ProjectTask with error: {}", e.getMessage(), e);
                throw new CustomException("SEARCH_FAILED", "Search Failed for given ProjectTask, " + e.getMessage());
            }
            List<Task> nonExistentEntities = checkNonExistentEntities(eMap,
                    existingEntities, idMethod);
            nonExistentEntities.forEach(task -> {
                Error error = getErrorForNonExistentEntity();
                populateErrorDetails(task, error, errorDetailsMap);
            });

            existingEntities.forEach(task -> {
                if(task.getAddress() != null) validateSubEntity(errorDetailsMap, eMap, task,
                        Collections.singletonList(task.getAddress()),
                        GET_ADDRESS);
                if(task.getResources() != null) validateSubEntity(errorDetailsMap, eMap, task,
                        task.getResources(), GET_RESOURCES);
            });
        }

        return errorDetailsMap;
    }

    private <T> void validateSubEntity(Map<Task, List<Error>> errorDetailsMap,
                                          Map<String, Task> eMap,
                                          Task entity,
                                          List<T> subEntities,
                                       String getSubEntityMethodName) {
        Object objFromReq = ReflectionUtils.invokeMethod(getMethod(getSubEntityMethodName, Task.class),
                eMap.get(entity.getId()));
        List<T> subEntitiesInReq;
        if (objFromReq instanceof List) {
            subEntitiesInReq = (List<T>) objFromReq;
        } else {
            subEntitiesInReq = (List<T>) Collections.singletonList(objFromReq);
        }

        if (subEntities != null && !subEntities.isEmpty()) {
            List<String> existingSubEntityIds = subEntities.stream()
                    .map(obj -> (String) ReflectionUtils.invokeMethod(getIdMethod(subEntities), obj))
                    .collect(Collectors.toList());
            if (subEntitiesInReq != null && !subEntitiesInReq.isEmpty()) {
                List<T> nonExistingSubEntities = subEntitiesInReq.stream().filter(subEntity ->  {
                    String id = (String) ReflectionUtils.invokeMethod(getMethod(GET_ID, subEntity.getClass()), subEntity);
                    return id != null && !existingSubEntityIds.contains(id);
                }).collect(Collectors.toList());

                if (!nonExistingSubEntities.isEmpty()) {
                    nonExistingSubEntities.forEach(subEntity -> {
                        Error error = getErrorForNonExistentSubEntity((String) ReflectionUtils
                                .invokeMethod(getMethod(GET_ID, subEntity.getClass()), subEntity));
                        populateErrorDetails(eMap.get(entity.getId()), error, errorDetailsMap);
                    });
                }
            }
        } else if (subEntitiesInReq != null && !subEntitiesInReq.isEmpty()) {
            subEntitiesInReq.stream()
                    .filter(subEntity ->  ReflectionUtils.invokeMethod(getMethod(GET_ID, subEntity.getClass()), subEntity) != null)
                    .forEach(subEntity -> {
                Error error = getErrorForNonExistentSubEntity((String) ReflectionUtils
                        .invokeMethod(getMethod(GET_ID, subEntity.getClass()), subEntity));
                populateErrorDetails(eMap.get(entity.getId()), error, errorDetailsMap);
            });
        }
    }
}
