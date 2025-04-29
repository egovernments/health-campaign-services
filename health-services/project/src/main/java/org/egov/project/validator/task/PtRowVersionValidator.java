package org.egov.project.validator.task;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.exception.InvalidTenantIdException;
import org.egov.common.models.Error;
import org.egov.common.models.project.Task;
import org.egov.common.models.project.TaskBulkRequest;
import org.egov.common.validator.Validator;
import org.egov.project.repository.ProjectTaskRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.egov.common.utils.CommonUtils.getEntitiesWithMismatchedRowVersion;
import static org.egov.common.utils.CommonUtils.getIdFieldName;
import static org.egov.common.utils.CommonUtils.getIdMethod;
import static org.egov.common.utils.CommonUtils.getIdToObjMap;
import static org.egov.common.utils.CommonUtils.getTenantId;
import static org.egov.common.utils.CommonUtils.notHavingErrors;
import static org.egov.common.utils.CommonUtils.populateErrorDetails;
import static org.egov.common.utils.ValidatorUtils.getErrorForInvalidTenantId;
import static org.egov.common.utils.ValidatorUtils.getErrorForRowVersionMismatch;

@Component
@Order(value = 5)
@Slf4j
public class PtRowVersionValidator implements Validator<TaskBulkRequest, Task> {

    private final ProjectTaskRepository projectTaskRepository;

    @Autowired
    public PtRowVersionValidator(ProjectTaskRepository projectTaskRepository) {
        this.projectTaskRepository = projectTaskRepository;
    }


    @Override
    public Map<Task, List<Error>> validate(TaskBulkRequest request) {
        log.info("validating row version");
        Map<Task, List<Error>> errorDetailsMap = new HashMap<>();
        Method idMethod = getIdMethod(request.getTasks());
        String tenantId = getTenantId(request.getTasks());
        Map<String, Task> eMap = getIdToObjMap(request.getTasks().stream()
                .filter(notHavingErrors())
                .collect(Collectors.toList()), idMethod);
        if (!eMap.isEmpty()) {
            List<String> entityIds = new ArrayList<>(eMap.keySet());
            try {
                List<Task> existingEntities = projectTaskRepository.findById(tenantId, entityIds,
                        getIdFieldName(idMethod), false).getResponse();
                List<Task> entitiesWithMismatchedRowVersion =
                        getEntitiesWithMismatchedRowVersion(eMap, existingEntities, idMethod);
                entitiesWithMismatchedRowVersion.forEach(individual -> {
                    Error error = getErrorForRowVersionMismatch();
                    populateErrorDetails(individual, error, errorDetailsMap);
                });
            } catch (InvalidTenantIdException exception) {
                request.getTasks().forEach(task -> {
                    Error error = getErrorForInvalidTenantId(tenantId, exception);
                    populateErrorDetails(task, error, errorDetailsMap);
                });
            }
        }
        return errorDetailsMap;
    }
}
