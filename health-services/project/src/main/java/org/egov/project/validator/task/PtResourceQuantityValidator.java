package org.egov.project.validator.task;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.Error;
import org.egov.common.models.project.Task;
import org.egov.common.models.project.TaskBulkRequest;
import org.egov.common.models.project.TaskResource;
import org.egov.common.utils.CommonUtils;
import org.egov.common.validator.Validator;
import org.egov.project.config.ProjectConfiguration;
import org.egov.project.util.ProjectConstants;
import org.egov.tracer.model.CustomException;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.egov.common.utils.CommonUtils.populateErrorDetails;
import static org.egov.project.util.ProjectConstants.TASK_NOT_ALLOWED;

/**
 * The PtResourceQuantityValidator class is responsible for validating the resource quantity of tasks in a bulk request.
 * It checks whether the quantity adheres to the specified pattern defined in the project configuration.
 *
 * @author kanishq-egov
 */
@Component
@Order(value = 3)
@Slf4j
public class PtResourceQuantityValidator implements Validator<TaskBulkRequest, Task> {

    private final ProjectConfiguration projectConfiguration;

    /**
     * Constructor for PtResourceQuantityValidator.
     *
     * @param projectConfiguration The configuration containing settings for the project module.
     */
    public PtResourceQuantityValidator(ProjectConfiguration projectConfiguration) {
        this.projectConfiguration = projectConfiguration;
    }

    /**
     * Validates the resource quantity of tasks in a bulk request.
     *
     * @param request The TaskBulkRequest containing a list of tasks.
     * @return A map containing tasks with associated error details.
     */
    @Override
    public Map<Task, List<Error>> validate(TaskBulkRequest request) {
        // Map to store error details for each task
        Map<Task, List<Error>> errorDetailsMap = new HashMap<>();

        // Extract the list of tasks from the request
        List<Task> entities = request.getTasks();

        // Check if the list is not empty
        if(!entities.isEmpty()) {
            entities.forEach(task -> {
                // Extract the list of task resources
                List<TaskResource> taskResources = task.getResources();

                // Filter task resources with invalid quantities
                List<TaskResource> invalidTaskResouces = taskResources.stream()
                        .filter(taskResource ->
                                !CommonUtils.isValidPattern(
                                        Double.toString(taskResource.getQuantity()),
                                        projectConfiguration.getProjectTaskResourceQuantityPattern()
                                )
                        ).collect(Collectors.toList());

                // Check if there are invalid task resources
                if(!invalidTaskResouces.isEmpty()) {
                    Error error = Error.builder()
                            .errorMessage(ProjectConstants.TASK_NOT_ALLOWED_RESOURCE_QUANTITY_INVALID_ERROR_MESSAGE)
                            .errorCode(TASK_NOT_ALLOWED)
                            .type(Error.ErrorType.NON_RECOVERABLE)
                            .exception(new CustomException(TASK_NOT_ALLOWED, ProjectConstants.TASK_NOT_ALLOWED_RESOURCE_QUANTITY_INVALID_ERROR_MESSAGE))
                            .build();

                    // Populate error details for the task in the map
                    populateErrorDetails(task, error, errorDetailsMap);
                }
            });
        }
        return errorDetailsMap;
    }
}
