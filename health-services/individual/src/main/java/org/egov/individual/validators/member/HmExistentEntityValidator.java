package org.egov.individual.validators.member;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.exception.InvalidTenantIdException;
import org.egov.common.models.Error;
import org.egov.common.models.household.HouseholdMember;
import org.egov.common.models.household.HouseholdMemberBulkRequest;
import org.egov.common.models.household.HouseholdMemberSearch;
import org.egov.common.utils.CommonUtils;
import org.egov.common.validator.Validator;
import org.egov.individual.repository.HouseholdMemberRepository;
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
 * This validator checks if the provided HouseholdMember entities already exist in the database based on their client reference IDs.
 *
 * @author kanishq-egov
 */
@Component
@Order(value = 1)
@Slf4j
public class HmExistentEntityValidator implements Validator<HouseholdMemberBulkRequest, HouseholdMember> {

    private final HouseholdMemberRepository householdMemberRepository;

    /**
     * Constructor to initialize the HouseholdMemberRepository dependency.
     *
     * @param householdMemberRepository The repository for HouseholdMember entities.
     */
    public HmExistentEntityValidator(HouseholdMemberRepository householdMemberRepository) {
        this.householdMemberRepository = householdMemberRepository;
    }

    /**
     * Validates the existence of entities with the given client reference IDs.
     * This method checks if any of the HouseholdMember entities in the request already exist in the database,
     * based on their client reference IDs. If an entity is found to exist, an error is added to the error details map.
     *
     * @param request The bulk request containing HouseholdMember entities.
     * @return A map containing HouseholdMember entities and their associated error details, if any.
     */
    @Override
    public Map<HouseholdMember, List<Error>> validate(HouseholdMemberBulkRequest request) {

        // Extract tenant ID from the request
        String tenantId = CommonUtils.getTenantId(request.getHouseholdMembers()); // Extract tenant ID from the request;
        // Map to hold HouseholdMember entities and their associated error details
        Map<HouseholdMember, List<Error>> errorDetailsMap = new HashMap<>();

        // Get the list of HouseholdMember entities from the request
        List<HouseholdMember> entities = request.getHouseholdMembers();

        // Extract client reference IDs from HouseholdMember entities that do not have errors
        List<String> clientReferenceIdList = entities.stream()
                .filter(notHavingErrors()) // Filter out entities that already have errors
                .map(HouseholdMember::getClientReferenceId) // Map to client reference IDs
                .collect(Collectors.toList()); // Collect the IDs into a list

        // Create a search object for querying entities by client reference IDs
        HouseholdMemberSearch householdSearch = HouseholdMemberSearch.builder()
                .clientReferenceId(clientReferenceIdList) // Set the client reference IDs for the search
                .build();

        // Create a map of client reference ID to HouseholdMember entity for easy lookup
        Map<String, HouseholdMember> map = entities.stream()
                .filter(entity -> StringUtils.hasText(entity.getClientReferenceId())) // Ensure client reference ID is not empty
                .collect(Collectors.toMap(entity -> entity.getClientReferenceId(), entity -> entity)); // Collect to a map

        // Check if the client reference ID list is not empty before querying the database
        if (!CollectionUtils.isEmpty(clientReferenceIdList)) {
            // Query the repository to find existing entities by client reference IDs
            // This will catch the exception if the tenant ID is invalid
            try {
                List<String> existingClientReferenceIds = householdMemberRepository.validateClientReferenceIdsFromDB(tenantId, clientReferenceIdList, Boolean.TRUE);

                // For each existing client reference ID, populate error details for uniqueness
                existingClientReferenceIds.forEach(clientReferenceId -> {
                    // Get a predefined error object for unique entity validation
                    Error error = getErrorForUniqueEntity();
                    // Populate error details for the HouseholdMember entity associated with the client reference ID
                    populateErrorDetails(map.get(clientReferenceId), error, errorDetailsMap);
                });
            } catch (InvalidTenantIdException exception) {
                entities.forEach( householdMember -> {
                    Error error = getErrorForInvalidTenantId(tenantId, exception);
                    log.error("validation failed for facility tenantId: {} with error :{}", householdMember.getTenantId(), error);
                    populateErrorDetails(householdMember, error, errorDetailsMap);
                });
            }

        }

        // Return the map containing HouseholdMember entities and their associated error details
        return errorDetailsMap;
    }
}
