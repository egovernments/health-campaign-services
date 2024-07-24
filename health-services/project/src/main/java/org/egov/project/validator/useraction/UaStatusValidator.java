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
 * The UaStatusValidator class is responsible for validating userActions within a UserActionBulkRequest.
 * It focuses on checking the status of userActions related to closed households. Specifically,
 * it validates if the 'HouseholdId' is present and correctly set in the additional fields of userActions
 * that have a status of 'RESOLVED'.
 */

//TODO fix this
@Component
@Order(value = 3)
@Slf4j
public class UaStatusValidator implements Validator<UserActionBulkRequest, UserAction> {

    /**
     * Validates the userActions within a UserActionBulkRequest based on their status and additional fields.
     * Specifically, it checks userActions with a status of 'RESOLVED' to ensure that the 'HouseholdId'
     * field in the additional details is present and has a non-empty value.
     *
     * @param request The UserActionBulkRequest object containing the list of userActions to be validated.
     * @return A map where each key is a UserAction that failed validation, and each value is a list of Error objects
     *         associated with that userAction.
     */
    @Override
    public Map<UserAction, List<Error>> validate(UserActionBulkRequest request) {
        log.info("validating status of closed household");

        // Initialize a map to store userActions with validation errors and corresponding error details
        Map<UserAction, List<Error>> errorDetailsMap = new HashMap<>();

        // Stream through the list of userActions and filter out those that have status 'RESOLVED' but
        // do not meet the criteria of having a non-empty 'HouseholdId' field.
        List<UserAction> invalidEntities = request.getUserActions().stream()
                .filter(userAction -> ProjectConstants.TaskStatus.RESOLVED.toString().equals(userAction.getStatus()))
                .filter(userAction -> !validateResolvedStatus(userAction.getAdditionalFields().getFields()))
                .collect(Collectors.toList());

        // If there are any invalid userActions, create error details and populate them in the map
        if (!CollectionUtils.isEmpty(invalidEntities)) {
            invalidEntities.forEach(userAction -> {
                // Create an Error object with details about the missing 'HouseholdId'
                Error error = Error.builder()
                        .errorMessage(HOUSEHOLD_ID + " is not present in AdditionalDetails of object.")
                        .errorCode("MISSING_HOUSEHOLD_ID")
                        .type(Error.ErrorType.NON_RECOVERABLE)
                        .exception(new CustomException("MISSING_HOUSEHOLD_ID", HOUSEHOLD_ID + " is not present in AdditionalDetails of object."))
                        .build();

                // Populate the error details map with the userAction and the created error
                populateErrorDetails(userAction, error, errorDetailsMap);
            });
        }

        // Return the map of userActions with validation errors
        return errorDetailsMap;
    }

    /**
     * Checks if the 'HouseholdId' field is present and correctly set in the list of additional fields.
     *
     * @param fields The list of additional fields associated with a userAction.
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
