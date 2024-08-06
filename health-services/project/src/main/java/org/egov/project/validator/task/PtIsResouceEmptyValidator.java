package org.egov.project.validator.task;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.Error;
import org.egov.common.models.project.Task;
import org.egov.common.models.project.TaskBulkRequest;
import org.egov.common.validator.Validator;
import org.egov.project.config.ProjectConfiguration;
import org.egov.project.util.ProjectConstants;
import org.egov.tracer.model.CustomException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import static org.egov.common.utils.CommonUtils.populateErrorDetails;
import static org.egov.project.util.ProjectConstants.TASK_NOT_ALLOWED;

/**
 * To Validate when task resource is empty or null
 */
@Component
@Order(value = 1)
@Slf4j
public class PtIsResouceEmptyValidator implements Validator<TaskBulkRequest, Task> {

    private final ProjectConfiguration projectConfiguration;

    @Autowired
    public PtIsResouceEmptyValidator(ProjectConfiguration projectConfiguration) {
        this.projectConfiguration = projectConfiguration;
    }

    /**
     *  Returns all the invalid objects in the request based on the task resources.
     * @param request of TaskBulkRequest class
     * @return errorDetailsMap
     */
    public Map<Task, List<Error>> validate(TaskBulkRequest request) {
        Map<Task, List<Error>> errorDetailsMap = new HashMap<>();
        List<Task> entities = request.getTasks();
        if(!entities.isEmpty()) {
            entities.forEach(task -> {
                if (CollectionUtils.isEmpty(task.getResources()) &&
                        !projectConfiguration.getNoResourceStatuses().contains(task.getStatus())) {
                    /**
                     *  If the task resource is empty or null and task status is not BENEFICIARY_REFUSED it is invalid
                     */
                    String errorMessage = ProjectConstants.TASK_NOT_ALLOWED_RESOURCE_CANNOT_EMPTY_ERROR_MESSAGE
                            + String.join(ProjectConstants.OR, projectConfiguration.getNoResourceStatuses());
                    Error error = Error.builder()
                        .errorMessage(errorMessage)
                        .errorCode(TASK_NOT_ALLOWED)
                        .type(Error.ErrorType.NON_RECOVERABLE)
                        .exception(new CustomException(TASK_NOT_ALLOWED, errorMessage)).build();
                    populateErrorDetails(task, error, errorDetailsMap);
                } else if (!CollectionUtils.isEmpty(task.getResources()) && projectConfiguration.getNoResourceStatuses().contains(task.getStatus())) {
                    /**
                     *  If the task resource is not empty and task status is BENEFICIARY_REFUSED
                      */
                    Error error = Error.builder()
                            .errorMessage(ProjectConstants.TASK_NOT_ALLOWED_BENEFICIARY_REFUSED_RESOURCE_EMPTY_ERROR_MESSAGE)
                            .errorCode(TASK_NOT_ALLOWED)
                            .type(Error.ErrorType.NON_RECOVERABLE)
                            .exception(new CustomException(TASK_NOT_ALLOWED, ProjectConstants.TASK_NOT_ALLOWED_BENEFICIARY_REFUSED_RESOURCE_EMPTY_ERROR_MESSAGE)).build();
                    populateErrorDetails(task, error, errorDetailsMap);
                }
            });
        }
        return errorDetailsMap;
    }
}