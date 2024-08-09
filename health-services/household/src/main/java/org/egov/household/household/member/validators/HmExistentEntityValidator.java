package org.egov.household.household.member.validators;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.Error;
import org.egov.common.models.household.HouseholdMember;
import org.egov.common.models.household.HouseholdMemberBulkRequest;
import org.egov.common.models.household.HouseholdMemberSearch;
import org.egov.common.validator.Validator;
import org.egov.household.repository.HouseholdMemberRepository;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import static org.egov.common.utils.CommonUtils.notHavingErrors;
import static org.egov.common.utils.CommonUtils.populateErrorDetails;
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
     *
     * @param request The bulk request containing HouseholdMember entities.
     * @return A map containing HouseholdMember entities and their associated error details.
     */
    @Override
    public Map<HouseholdMember, List<Error>> validate(HouseholdMemberBulkRequest request) {
        // Map to hold HouseholdMember entities and their error details
        Map<HouseholdMember, List<Error>> errorDetailsMap = new HashMap<>();
        // Get the list of HouseholdMember entities from the request
        List<HouseholdMember> entities = request.getHouseholdMembers();
        // Extract client reference IDs from HouseholdMember entities without errors
        List<String> clientReferenceIdList = entities.stream()
                .filter(notHavingErrors())
                .map(HouseholdMember::getClientReferenceId)
                .collect(Collectors.toList());
        // Create a search object for querying entities by client reference IDs
        HouseholdMemberSearch householdSearch = HouseholdMemberSearch.builder()
                .clientReferenceId(clientReferenceIdList)
                .build();
        Map<String, HouseholdMember> map = entities.stream()
                .filter(individual -> StringUtils.isEmpty(individual.getClientReferenceId()))
                .collect(Collectors.toMap(entity -> entity.getClientReferenceId(), entity -> entity));
        // Check if the client reference ID list is not empty
        if (!CollectionUtils.isEmpty(clientReferenceIdList)) {
            // Query the repository to find existing entities by client reference IDs
            List<String> existingClientReferenceIds =
                    householdMemberRepository.validateClientReferenceIdsFromDB(clientReferenceIdList, Boolean.TRUE);
            // For each existing entity, populate error details for uniqueness
            existingClientReferenceIds.forEach(clientReferenceId -> {
                Error error = getErrorForUniqueEntity();
                populateErrorDetails(map.get(clientReferenceId), error, errorDetailsMap);
            });
        }
        return errorDetailsMap;
    }

}
