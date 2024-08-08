package org.egov.household.validators.household;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.Error;
import org.egov.common.models.household.Household;
import org.egov.common.models.household.HouseholdBulkRequest;
import org.egov.common.models.household.HouseholdSearch;
import org.egov.common.validator.Validator;
import org.egov.household.repository.HouseholdRepository;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import static org.egov.common.utils.CommonUtils.getIdFieldName;
import static org.egov.common.utils.CommonUtils.notHavingErrors;
import static org.egov.common.utils.CommonUtils.populateErrorDetails;
import static org.egov.common.utils.ValidatorUtils.getErrorForUniqueEntity;

/**
 * Validator class for checking the existence of entities with the given client reference IDs.
 * This validator checks if the provided household entities already exist in the database based on their client reference IDs.
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
     *
     * @param request The bulk request containing household entities.
     * @return A map containing household entities and their associated error details.
     */
    @Override
    public Map<Household, List<Error>> validate(HouseholdBulkRequest request) {
        // Map to hold household entities and their error details
        Map<Household, List<Error>> errorDetailsMap = new HashMap<>();
        // Get the list of household entities from the request
        List<Household> entities = request.getHouseholds();
        // Extract client reference IDs from household entities without errors
        List<String> clientReferenceIdList = entities.stream()
                .filter(notHavingErrors())
                .map(Household::getClientReferenceId)
                .collect(Collectors.toList());
        // Create a search object for querying entities by client reference IDs
        HouseholdSearch householdSearch = HouseholdSearch.builder()
                .clientReferenceId(clientReferenceIdList)
                .build();
        // Check if the client reference ID list is not empty
        if (!CollectionUtils.isEmpty(clientReferenceIdList)) {
            // Query the repository to find existing entities by client reference IDs
            List<String> existingClientReferenceIds = householdRepository.validateClientReferenceIdsFromDB(clientReferenceIdList);
            // For each existing entity, populate error details for uniqueness
            existentEntities.forEach(entity -> {
                Error error = getErrorForUniqueEntity();
                populateErrorDetails(entity, error, errorDetailsMap);
            });
        }
        return errorDetailsMap;
    }

}
