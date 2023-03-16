package org.egov.project.validator.task;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.Error;
import org.egov.common.models.project.Task;
import org.egov.common.models.project.TaskBulkRequest;
import org.egov.common.validator.Validator;
import org.egov.project.repository.ProjectBeneficiaryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.egov.common.utils.CommonUtils.getIdList;
import static org.egov.common.utils.CommonUtils.getIdMethod;
import static org.egov.common.utils.CommonUtils.getIdToObjMap;
import static org.egov.common.utils.CommonUtils.notHavingErrors;
import static org.egov.common.utils.CommonUtils.populateErrorDetails;
import static org.egov.common.utils.ValidatorUtils.getErrorForInvalidRelatedEntityID;
import static org.egov.common.utils.ValidatorUtils.getErrorForNonExistentRelatedEntity;

@Component
@Order(value = 7)
@Slf4j
public class PtProjectBeneficiaryIdValidator implements Validator<TaskBulkRequest, Task> {

    private final ProjectBeneficiaryRepository projectBeneficiaryRepository;

    @Autowired
    public PtProjectBeneficiaryIdValidator(ProjectBeneficiaryRepository projectBeneficiaryRepository) {
        this.projectBeneficiaryRepository = projectBeneficiaryRepository;
    }


    @Override
    public Map<Task, List<Error>> validate(TaskBulkRequest request) {
        log.info("validating for project beneficiary id");
        Map<Task, List<Error>> errorDetailsMap = new HashMap<>();
        List<Task> entities = request.getTasks();
        Method idMethod = getIdMethod(entities,
                "projectBeneficiaryId", "projectBeneficiaryClientReferenceId");
        Map<String, Task> eMap = getIdToObjMap(entities
                .stream().filter(notHavingErrors()).collect(Collectors.toList()), idMethod);
        if (eMap.size() != entities.size()) {
            List<Task> invalidTasks = entities.stream().filter(t -> ReflectionUtils.invokeMethod(idMethod, t) == null)
                    .collect(Collectors.toList());
            invalidTasks.forEach(task -> {
                Error error = getErrorForInvalidRelatedEntityID();
                populateErrorDetails(task, error, errorDetailsMap);
            });
        }

        if (!eMap.isEmpty()) {
            String columnName = "id";
            if (idMethod.getName().contains("getProjectBeneficiaryClientReferenceId")) {
                columnName = "clientReferenceId";
            }
            entities = entities.stream().filter(notHavingErrors()).collect(Collectors.toList());
            List<String> existingProjectBeneficiaryIds = projectBeneficiaryRepository
                    .validateIds(getIdList(entities, idMethod),
                    columnName);
            List<Task> invalidEntities = eMap.values().stream().filter(entity ->
                    !existingProjectBeneficiaryIds
                            .contains(ReflectionUtils.invokeMethod(idMethod, entity)))
                            .collect(Collectors.toList());
            invalidEntities.forEach(task -> {
                Error error = getErrorForNonExistentRelatedEntity((String) ReflectionUtils.invokeMethod(idMethod, task));
                populateErrorDetails(task, error, errorDetailsMap);
            });
        }

        return errorDetailsMap;
    }
}
