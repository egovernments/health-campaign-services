package org.egov.project.validator.useraction;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.egov.common.models.Error;
import org.egov.common.models.core.Field;
import org.egov.common.models.project.useraction.UserAction;
import org.egov.common.models.project.useraction.UserActionBulkRequest;
import org.egov.common.validator.Validator;
import org.egov.project.util.ProjectConstants;
import org.egov.tracer.model.CustomException;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import static org.egov.common.utils.CommonUtils.populateErrorDetails;
import static org.egov.project.Constants.HOUSEHOLD_ID;

/**
 * UaStatusValidator is responsible for validating the status of UserAction entities within a UserActionBulkRequest.
 * Specifically, it ensures that UserActions with a status of 'RESOLVED' have a non-empty 'HouseholdId' field
 * in their additional details.
 */
@Component
@Order(value = 3)
@Slf4j
public class UaStatusValidator implements Validator<UserActionBulkRequest, UserAction> {

    /**
     * Validates the UserAction entities within a UserActionBulkRequest based on their status and additional fields.
     * It checks UserActions with a status of 'RESOLVED' to ensure that the 'HouseholdId' field
     * in the additional details is present and has a non-empty value.
     *
     * @param request The UserActionBulkRequest object containing the list of UserActions to be validated.
     * @return A map where each key is a UserAction that failed validation, and each value is a list of Error objects
     *         associated with that UserAction.
     */
    @Override
    public Map<UserAction, List<Error>> validate(UserActionBulkRequest request) {
        log.info("Starting validation of UserActions with status 'RESOLVED' in UserActionBulkRequest with {} entities", request.getUserActions().size());

        // Initialize a map to store UserActions with validation errors and corresponding error details
        Map<UserAction, List<Error>> errorDetailsMap = new HashMap<>();

        // Stream through the list of UserActions and filter out those that have status 'RESOLVED' but
        // do not meet the criteria of having a non-empty 'HouseholdId' field.
        List<UserAction> invalidEntities = request.getUserActions().stream()
                .filter(userAction -> ProjectConstants.TaskStatus.RESOLVED.toString().equals(userAction.getStatus()))
                .filter(userAction -> !validateResolvedStatus(userAction.getAdditionalFields().getFields()))
                .collect(Collectors.toList());

        log.info("Identified {} invalid UserActions with status 'RESOLVED' and missing 'HouseholdId'", invalidEntities.size());

        // If there are any invalid UserActions, create error details and populate them in the map
        if (!CollectionUtils.isEmpty(invalidEntities)) {
            invalidEntities.forEach(userAction -> {
                log.debug("Populating error details for UserAction with ID: {}", userAction.getId());

                // Create an Error object with details about the missing 'HouseholdId'
                Error error = Error.builder()
                        .errorMessage(HOUSEHOLD_ID + " is not present in AdditionalDetails of object.")
                        .errorCode("MISSING_HOUSEHOLD_ID")
                        .type(Error.ErrorType.NON_RECOVERABLE)
                        .exception(new CustomException("MISSING_HOUSEHOLD_ID", HOUSEHOLD_ID + " is not present in AdditionalDetails of userAction with ID: " + userAction.getId()))
                        .build();

                // Populate the error details map with the UserAction and the created error
                populateErrorDetails(userAction, error, errorDetailsMap);
            });
        }

        log.info("Completed validation of UserActions with status 'RESOLVED'");
        return errorDetailsMap;
    }

    /**
     * Checks if the 'HouseholdId' field is present and correctly set in the list of additional fields.
     *
     * @param fields The list of additional fields associated with a UserAction.
     * @return true if the 'HouseholdId' field is present and non-empty; false otherwise.
     */
    private boolean validateResolvedStatus(List<Field> fields) {
        log.debug("Validating additional fields for 'HouseholdId' presence and non-empty value");

        // Stream through the list of fields to find one with the key 'HouseholdId'
        boolean isValid = fields.stream()
                .filter(field -> field.getKey().equals(HOUSEHOLD_ID))
                // Check if the value for this field is non-empty
                .anyMatch(field -> !StringUtils.isEmpty(field.getValue()));

        log.debug("'HouseholdId' validation result: {}", isValid);
        return isValid;
    }
}
