package org.egov.household.validators.household;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.data.query.exception.QueryBuilderException;
import org.egov.common.models.Error;
import org.egov.common.models.household.Household;
import org.egov.common.models.household.HouseholdBulkRequest;
import org.egov.common.models.household.HouseholdSearch;
import org.egov.common.validator.Validator;
import org.egov.household.repository.HouseholdRepository;
import org.egov.tracer.model.CustomException;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import static org.egov.common.utils.CommonUtils.checkNonExistentEntities;
import static org.egov.common.utils.CommonUtils.getIdToObjMap;
import static org.egov.common.utils.CommonUtils.getMethod;
import static org.egov.common.utils.CommonUtils.getObjClass;
import static org.egov.common.utils.CommonUtils.notHavingErrors;
import static org.egov.common.utils.CommonUtils.populateErrorDetails;
import static org.egov.common.utils.ValidatorUtils.getErrorForNonExistentEntity;
import static org.egov.household.Constants.GET_ID;

/**
 * Validator for checking the non-existence of household entities.
 * This validator checks if the provided household entities do not already exist in the database.
 *
 * @author kanishq-egov
 */
@Component
@Order(value = 2)
@Slf4j
public class HNonExistentEntityValidator implements Validator<HouseholdBulkRequest, Household> {

    private final HouseholdRepository householdRepository;

    public HNonExistentEntityValidator(HouseholdRepository householdRepository) {
        this.householdRepository = householdRepository;
    }

    /**
     * Validates the non-existence of entities based on their IDs and client reference IDs.
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
        // Get the class of the household entity
        Class<?> objClass = getObjClass(entities);
        // Get the method for fetching the ID of the entity
        Method idMethod = getMethod(GET_ID, objClass);
        // Map to store entities by their IDs
        Map<String, Household> eMap = getIdToObjMap(
                entities.stream().filter(notHavingErrors()).collect(Collectors.toList()), idMethod);
        // Lists to store IDs and client reference IDs
        List<String> idList = new ArrayList<>();
        List<String> clientReferenceIdList = new ArrayList<>();
        // Extract IDs and client reference IDs from household entities
        entities.forEach(household -> {
            idList.add(household.getId());
            clientReferenceIdList.add(household.getClientReferenceId());
        });
        // Check if the entity map is not empty
        if (!eMap.isEmpty()) {
            // Extract entity IDs
            List<String> entityIds = new ArrayList<>(eMap.keySet());
            // Create a search object for querying existing entities
            HouseholdSearch householdSearch = HouseholdSearch.builder()
                    .clientReferenceId(clientReferenceIdList)
                    .id(idList)
                    .build();

            List<Household> existingEntities;
            try {
                // Query the repository to find existing entities
                existingEntities = householdRepository.find(householdSearch, entities.size(), 0,
                        entities.get(0).getTenantId(), null, false).getResponse();
            } catch (Exception e) {
                // Handle query builder exception
                log.error("Search failed for Household with error: {}", e.getMessage(), e);
                throw new CustomException("HOUSEHOLD_SEARCH_FAILED", "Search Failed for Household, " + e.getMessage()); 
            }
            // Check for non-existent entities
            List<Household> nonExistentEntities = checkNonExistentEntities(eMap,
                    existingEntities, idMethod);
            // Populate error details for non-existent entities
            nonExistentEntities.forEach(entity -> {
                Error error = getErrorForNonExistentEntity();
                populateErrorDetails(entity, error, errorDetailsMap);
            });
        }

        return errorDetailsMap;
    }
}
