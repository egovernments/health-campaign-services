package org.egov.household.validators.household;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.exception.InvalidTenantIdException;
import org.egov.common.models.Error;
import org.egov.common.models.core.EgovOfflineModel;
import org.egov.common.models.household.HouseHoldType;
import org.egov.common.models.household.Household;
import org.egov.common.models.household.HouseholdBulkRequest;
import org.egov.common.models.household.HouseholdSearch;
import org.egov.common.utils.CommonUtils;
import org.egov.common.validator.Validator;
import org.egov.household.repository.HouseholdRepository;
import org.egov.tracer.model.CustomException;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import static org.egov.common.utils.CommonUtils.notHavingErrors;
import static org.egov.common.utils.CommonUtils.populateErrorDetails;
import static org.egov.common.utils.ValidatorUtils.getErrorForInvalidTenantId;
import static org.egov.common.utils.ValidatorUtils.getErrorForUniqueEntity;

/**
 * Validator class for checking the existence of entities with the given client reference IDs.
 * This validator checks if the provided household entities already exist in the database based on their client reference IDs.
 *
 * @author kanishq-egov
 */
@Component
@Order(value = 1)
@Slf4j
public class HMemberCountValidator implements Validator<HouseholdBulkRequest, Household> {

    private final HouseholdRepository householdRepository;

    /**
     * Constructor to initialize the HouseholdRepository dependency.
     *
     * @param householdRepository The repository for household entities.
     */
    public HMemberCountValidator(HouseholdRepository householdRepository) {
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
        List<Household> validHouseholds = entities.stream()
                .filter(notHavingErrors()) // Filter out entities that already have errors
                .toList(); // Collect the IDs into a list

        // Create a map of client reference ID to Household entity for easy lookup
        Map<String, Household> map = entities.stream()
                .filter(entity -> StringUtils.hasText(entity.getClientReferenceId())) // Ensure client reference ID is not empty
                .collect(Collectors.toMap(EgovOfflineModel::getClientReferenceId, entity -> entity)); // Collect to a map


        // Check if the client reference ID list is not empty before querying the database
        if (!CollectionUtils.isEmpty(validHouseholds)) {

            // For each existing client reference ID, populate error details for uniqueness
            validHouseholds.forEach(household -> {
                if(household.getMemberCount() > 3) {
                    // Get a predefined error object
                    Error error = getErrorForHouseholdMemberCountExceeded();
                    // Populate error details for the household entity associated with the client reference ID
                    populateErrorDetails(household, error, errorDetailsMap);
                }

            });

        }

        // Return the map containing household entities and their associated error details
        return errorDetailsMap;
    }

    private static Error getErrorForHouseholdMemberCountExceeded() {
        return Error.builder()
                .errorMessage("Invalid Household Member Count")
                .errorCode("INVALID_HOUSEHOLD_MEMBER_COUNT")
                .type(Error.ErrorType.NON_RECOVERABLE)
                .exception(new CustomException("INVALID_HOUSEHOLD_MEMBER_COUNT", "Household with member more than 3 are not allowed"))
                .build();
    }

}
