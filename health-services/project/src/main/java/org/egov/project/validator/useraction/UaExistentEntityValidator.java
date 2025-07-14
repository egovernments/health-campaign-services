package org.egov.project.validator.useraction;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.exception.InvalidTenantIdException;
import org.egov.common.models.Error;
import org.egov.common.models.project.useraction.UserAction;
import org.egov.common.models.project.useraction.UserActionBulkRequest;
import org.egov.common.models.project.useraction.UserActionSearch;
import org.egov.common.validator.Validator;
import org.egov.project.repository.UserActionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import static org.egov.common.utils.CommonUtils.getTenantId;
import static org.egov.common.utils.CommonUtils.notHavingErrors;
import static org.egov.common.utils.CommonUtils.populateErrorDetails;
import static org.egov.common.utils.ValidatorUtils.getErrorForInvalidTenantId;
import static org.egov.common.utils.ValidatorUtils.getErrorForUniqueEntity;

/**
 * Validator class for checking the existence of UserAction entities based on their client reference IDs.
 * This validator ensures that the UserAction entities in the bulk request do not have duplicate client reference IDs in the database.
 *
 * The validation checks if each UserAction entity's client reference ID already exists in the database.
 * If a duplicate ID is found, it adds an error to the map with the entity.
 *
 * @author: kanishq-egov
 */
@Component
@Order(value = 1)
@Slf4j
public class UaExistentEntityValidator implements Validator<UserActionBulkRequest, UserAction> {
    private UserActionRepository userActionRepository;

    /**
     * Constructs a UaExistentEntityValidator with the specified UserActionRepository.
     *
     * @param userActionRepository the repository used to validate UserAction entities.
     */
    @Autowired
    public UaExistentEntityValidator(UserActionRepository userActionRepository) {
        this.userActionRepository = userActionRepository;
    }

    /**
     * Validates the existence of UserAction entities in the UserActionBulkRequest.
     * Checks if the provided UserAction entities already exist in the database based on their client reference IDs.
     *
     * @param request the bulk request containing UserAction entities.
     * @return a map of UserAction entities and their associated error details.
     */
    @Override
    public Map<UserAction, List<Error>> validate(UserActionBulkRequest request) {
        // Map to hold UserAction entities and their error details.
        log.info("Validating existence of entities in UserActionBulkRequest with {} entities", request.getUserActions().size());
        Map<UserAction, List<Error>> errorDetailsMap = new HashMap<>();

        // Get the list of UserAction entities from the request.
        List<UserAction> entities = request.getUserActions();
        String tenantId = getTenantId(entities);

        // Extract client reference IDs from UserAction entities that do not have existing errors.
        List<String> clientReferenceIdList = entities.stream()
                .filter(notHavingErrors()) // Filter out entities that already have errors.
                .map(UserAction::getClientReferenceId) // Map to extract client reference IDs.
                .collect(Collectors.toList()); // Collect the IDs into a list.

        // Create a map for quick lookup of UserAction entities by client reference ID.
        Map<String, UserAction> map = entities.stream()
                .filter(entity -> StringUtils.hasText(entity.getClientReferenceId())) // Ensure client reference ID is not empty.
                .collect(Collectors.toMap(entity -> entity.getClientReferenceId(), entity -> entity)); // Collect to a map.

        // Create a search object to query for existing UserAction entities based on client reference IDs.
        UserActionSearch userActionSearch = UserActionSearch.builder()
                .clientReferenceId(clientReferenceIdList) // Set the client reference IDs for the search.
                .build();

        // Check if the client reference ID list is not empty before querying the database.
        if (!CollectionUtils.isEmpty(clientReferenceIdList)) {
            try {
                // Query the repository to find existing UserAction entities with the given client reference IDs.
                List<String> existingClientReferenceIds = userActionRepository.validateClientReferenceIdsFromDB(tenantId, clientReferenceIdList, Boolean.FALSE);
                // For each existing client reference ID, add an error to the map for the corresponding UserAction entity.
                existingClientReferenceIds.forEach(clientReferenceId -> {
                    // Get a predefined error object for unique entity validation.
                    Error error = getErrorForUniqueEntity();
                    // Populate error details for the individual UserAction entity associated with the client reference ID.
                    populateErrorDetails(map.get(clientReferenceId), error, errorDetailsMap);
                });
            } catch (InvalidTenantIdException exception) {
                // Populating InvalidTenantIdException for all entities
                entities.forEach(userAction -> {
                    Error error = getErrorForInvalidTenantId(tenantId, exception);
                    populateErrorDetails(userAction, error, errorDetailsMap);
                });
            }
        }

        // Return the map containing UserAction entities and their associated error details.
        return errorDetailsMap;
    }
}
