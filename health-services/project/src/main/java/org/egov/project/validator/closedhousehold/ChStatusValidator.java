package org.egov.project.validator.closedhousehold;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.egov.common.models.Error;
import org.egov.common.models.core.Field;
import org.egov.common.models.project.Task;
import org.egov.common.models.project.TaskBulkRequest;
import org.egov.common.validator.Validator;
import org.egov.project.util.ProjectConstants;
import org.egov.tracer.model.CustomException;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import static org.egov.common.utils.CommonUtils.populateErrorDetails;
import static org.egov.project.Constants.HOUSEHOLD_ID;


/**
 * The ChStatusValidator class is responsible for validating tasks within a TaskBulkRequest.
 * It focuses on checking the status of tasks related to closed households. Specifically,
 * it validates if the 'HouseholdId' is present and correctly set in the additional fields of tasks
 * that have a status of 'RESOLVED'.
 */
@Component
@Order(value = 3)
@Slf4j
public class ChStatusValidator implements Validator<TaskBulkRequest, Task> {

    /**
     * Validates the tasks within a TaskBulkRequest based on their status and additional fields.
     * Specifically, it checks tasks with a status of 'RESOLVED' to ensure that the 'HouseholdId'
     * field in the additional details is present and has a non-empty value.
     *
     * @param request The TaskBulkRequest object containing the list of tasks to be validated.
     * @return A map where each key is a Task that failed validation, and each value is a list of Error objects
     *         associated with that task.
     */
    @Override
    public Map<Task, List<Error>> validate(TaskBulkRequest request) {
        log.info("validating status of closed household");

        // Initialize a map to store tasks with validation errors and corresponding error details
        Map<Task, List<Error>> errorDetailsMap = new HashMap<>();

        // Stream through the list of tasks and filter out those that have status 'RESOLVED' but
        // do not meet the criteria of having a non-empty 'HouseholdId' field.
        List<Task> invalidEntities = request.getTasks().stream()
                .filter(task -> ProjectConstants.TaskStatus.RESOLVED.toString().equals(task.getStatus()))
                .filter(task -> !validateResolvedStatus(task.getAdditionalFields().getFields()))
                .collect(Collectors.toList());

        // If there are any invalid tasks, create error details and populate them in the map
        if (!CollectionUtils.isEmpty(invalidEntities)) {
            invalidEntities.forEach(task -> {
                // Create an Error object with details about the missing 'HouseholdId'
                Error error = Error.builder()
                        .errorMessage(HOUSEHOLD_ID + " is not present in AdditionalDetails of object.")
                        .errorCode("MISSING_HOUSEHOLD_ID")
                        .type(Error.ErrorType.NON_RECOVERABLE)
                        .exception(new CustomException("MISSING_HOUSEHOLD_ID", HOUSEHOLD_ID + " is not present in AdditionalDetails of object."))
                        .build();

                // Populate the error details map with the task and the created error
                populateErrorDetails(task, error, errorDetailsMap);
            });
        }

        // Return the map of tasks with validation errors
        return errorDetailsMap;
    }

    /**
     * Checks if the 'HouseholdId' field is present and correctly set in the list of additional fields.
     *
     * @param fields The list of additional fields associated with a task.
     * @return true if the 'HouseholdId' field is present and non-empty; false otherwise.
     */
    public boolean validateResolvedStatus(List<Field> fields) {
        // Stream through the list of fields to find one with the key 'HouseholdId'
        return fields.stream()
                .filter(field -> field.getKey().equals(HOUSEHOLD_ID))
                // Check if the value for this field is non-empty
                .anyMatch(field -> !StringUtils.isEmpty(field.getValue()));
    }
}
