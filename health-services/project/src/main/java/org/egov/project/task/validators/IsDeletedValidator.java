package org.egov.project.task.validators;

import org.egov.common.models.Error;
import org.egov.common.validator.Validator;
import org.egov.project.web.models.Task;
import org.egov.project.web.models.TaskBulkRequest;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.egov.common.utils.CommonUtils.populateErrorDetails;
import static org.egov.common.utils.ValidatorUtils.getErrorForIsDelete;

@Component
@Order(2)
public class IsDeletedValidator implements Validator<TaskBulkRequest, Task> {

    @Override
    public Map<Task, List<Error>> validate(TaskBulkRequest request) {
        HashMap<Task, List<Error>> errorDetailsMap = new HashMap<>();
        List<Task> validIndividuals = request.getTasks();
        validIndividuals.stream().filter(Task::getIsDeleted).forEach(individual -> {
            Error error = getErrorForIsDelete();
            populateErrorDetails(individual, error, errorDetailsMap);
        });
        return errorDetailsMap;
    }
}
