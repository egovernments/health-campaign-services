package org.egov.individual.validators.household;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.Error;
import org.egov.common.models.household.Household;
import org.egov.common.models.household.HouseholdBulkRequest;
import org.egov.common.models.household.HouseholdSearch;
import org.egov.common.validator.Validator;
import org.egov.individual.repository.HouseholdRepository;
import org.egov.tracer.model.CustomException;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.egov.common.utils.CommonUtils.*;
import static org.egov.common.utils.CommonUtils.notHavingErrors;

@Slf4j
@Component
@Order(value = 3)
public class HHouseholdTypeChangeValidator implements Validator<HouseholdBulkRequest, Household> {

    private final HouseholdRepository householdRepository;

    public HHouseholdTypeChangeValidator(HouseholdRepository householdRepository) {
        this.householdRepository = householdRepository;
    }

    @Override
    public Map<Household, List<Error>> validate(HouseholdBulkRequest request) {
        // Map to hold household entities and their error details
        Map<Household, List<Error>> errorDetailsMap = new HashMap<>();
        // Get the list of household entities from the request
        List<Household> entities = request.getHouseholds();
        // Map to store entities by their IDs
        Map<String, Household> eMap = entities.stream().filter(notHavingErrors()).collect(Collectors.toMap(Household::getId, household -> household));
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
            List<Household> entitiesWithHouseholdTypeChange = changeInHouseholdType(eMap,
                    existingEntities);
            // Populate error details for non-existent entities
            entitiesWithHouseholdTypeChange.forEach(entity -> {
                Error error = Error.builder().errorMessage("Household Type change").errorCode("HOUSEHOLD_TYPE_CHANGE")
                        .type(Error.ErrorType.NON_RECOVERABLE)
                        .exception(new CustomException("HOUSEHOLD_TYPE_CHANGE", "Household Type change")).build();
                populateErrorDetails(entity, error, errorDetailsMap);
            });
        }

        return errorDetailsMap;
    }


    private List<Household> changeInHouseholdType(Map<String, Household> eMap,
            List<Household> existingEntities) {
        List<Household> entitiesWithHouseholdTypeChange = new ArrayList<>();

        for (Household existingEntity : existingEntities) {
            if (eMap.containsKey(existingEntity.getId())) {
                if (!existingEntity.getHouseholdType().equals(eMap.get(existingEntity.getId()).getHouseholdType())) {
                    entitiesWithHouseholdTypeChange.add(eMap.get(existingEntity.getId()));
                }
            }
        }
        return entitiesWithHouseholdTypeChange;
    }
}
