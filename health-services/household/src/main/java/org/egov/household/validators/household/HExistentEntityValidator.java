package org.egov.household.validators.household;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.exception.InvalidTenantIdException;
import org.egov.common.models.Error;
import org.egov.common.models.household.Household;
import org.egov.common.models.household.HouseholdBulkRequest;
import org.egov.common.models.household.HouseholdSearch;
import org.egov.common.utils.CommonUtils;
import org.egov.common.validator.Validator;
import org.egov.household.repository.HouseholdRepository;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import static org.egov.common.utils.CommonUtils.notHavingErrors;
import static org.egov.common.utils.CommonUtils.populateErrorDetails;
import static org.egov.common.utils.ValidatorUtils.*;

/**
 * Validator class for checking the existence of entities with the given client reference IDs.
 * This validator checks if the provided household entities already exist in the database based on their client reference IDs.
 *
 * @author kanishq-egov
 */
@Component
@Order(value = 1)
@Slf4j
public class HExistentEntityValidator implements Validator<HouseholdBulkRequest, Household> {

    private final HouseholdRepository householdRepository;

    /**
     * Constructor to initialize the HouseholdRepository dependency.
     *
     * @param householdRepository The repository for household entities.
     */
    public HExistentEntityValidator(HouseholdRepository householdRepository) {
        this.householdRepository = householdRepository;
    }

    /**
     * Validates the existence of entities with the given client reference IDs.
     * This method checks if any of the household entities in the request already exist in the database,
     * based on their client reference IDs. If an entity is found to exist, an error is added to the error details map.
     *
     * @param request The bulk request containing household entities.
     * @return A map containing household entities and their associated error details, if any.
     */
    @Override
    public Map<Household, List<Error>> validate(HouseholdBulkRequest request) {
        // Map to hold household entities and their associated error details
        Map<Household, List<Error>> errorDetailsMap = new HashMap<>();
        String tenantId = CommonUtils.getTenantId(request.getHouseholds()); // Extract tenant ID from the request
        // Get the list of household entities from the request
        List<Household> entities = request.getHouseholds();

        // Extract client reference IDs from household entities that do not have errors
        List<String> clientReferenceIdList = entities.stream()
                .filter(notHavingErrors()) // Filter out entities that already have errors
                .map(Household::getClientReferenceId) // Map to client reference IDs
                .collect(Collectors.toList()); // Collect the IDs into a list

        // Create a map of client reference ID to Household entity for easy lookup
        Map<String, Household> map = entities.stream()
                .filter(entity -> StringUtils.hasText(entity.getClientReferenceId())) // Ensure client reference ID is not empty
                .collect(Collectors.toMap(entity -> entity.getClientReferenceId(), entity -> entity)); // Collect to a map

        // Create a search object for querying entities by client reference IDs
        HouseholdSearch householdSearch = HouseholdSearch.builder()
                .clientReferenceId(clientReferenceIdList) // Set the client reference IDs for the search
                .build();

        // Check if the client reference ID list is not empty before querying the database
        if (!CollectionUtils.isEmpty(clientReferenceIdList)) {
            // Query the repository to find existing entities by client reference IDs

            try {
                List<String> existingClientReferenceIds =
                        householdRepository.validateClientReferenceIdsFromDB(tenantId, clientReferenceIdList, Boolean.TRUE);

                // For each existing client reference ID, populate error details for uniqueness
                existingClientReferenceIds.forEach(clientReferenceId -> {
                    // Get a predefined error object for unique entity validation
                    Error error = getErrorForUniqueEntity();
                    // Populate error details for the household entity associated with the client reference ID
                    populateErrorDetails(map.get(clientReferenceId), error, errorDetailsMap);
                });
            } catch (InvalidTenantIdException exception) {
                entities.forEach(household -> {
                    // If an exception occurs, populate error details for the household entity
                    Error error = getErrorForInvalidTenantId(tenantId, exception);
                    populateErrorDetails(household, error, errorDetailsMap);
                });
            }

        }

        // Return the map containing household entities and their associated error details
        return errorDetailsMap;
    }

}
