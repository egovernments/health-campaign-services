package org.egov.project.validator.task;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.Error;
import org.egov.common.models.project.Task;
import org.egov.common.models.project.TaskBulkRequest;
import org.egov.common.validator.Validator;
import org.egov.project.util.ProjectConstants;
import org.egov.tracer.model.CustomException;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.egov.common.utils.CommonUtils.populateErrorDetails;
import static org.egov.project.util.ProjectConstants.TASK_NOT_ALLOWED;

@Component
@Order(value = 1)
@Slf4j
public class PtResouceBeneficiaryRefusedValidator implements Validator<TaskBulkRequest, Task> {
    public Map<Task, List<Error>> validate(TaskBulkRequest request) {
        Map<Task, List<Error>> errorDetailsMap = new HashMap<>();
        List<Task> entities = request.getTasks();
        if(!entities.isEmpty()) {
            entities.forEach(task -> {
                if(task.getStatus() != null && task.getStatus().equals(ProjectConstants.TaskStatus.BENEFICIARY_REFUSED.toString()) && task.getResources() != null) {
                    Error error = Error.builder()
                            .errorMessage("Task not allowed as resources can not be provided when "+ProjectConstants.TaskStatus.BENEFICIARY_REFUSED)
                            .errorCode(TASK_NOT_ALLOWED)
                            .type(Error.ErrorType.NON_RECOVERABLE)
                            .exception(new CustomException(TASK_NOT_ALLOWED, "Task not allowed as resources can not be provided when "+ProjectConstants.TaskStatus.BENEFICIARY_REFUSED)).build();
                    populateErrorDetails(task, error, errorDetailsMap);
                }
            });
        }
        return errorDetailsMap;
    }
}
