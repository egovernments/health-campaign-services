package org.egov.household.household.member.validators;

import java.lang.reflect.Method;
import java.util.ArrayList;
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
import org.egov.tracer.model.CustomException;
import org.springframework.beans.factory.annotation.Autowired;
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
 * Validator class for checking the non-existence of household members.
 * This validator checks if the provided household members do not already exist in the database.
 * @author kanishq-egov
 */
@Component
@Order(value = 4)
@Slf4j
public class HmNonExistentEntityValidator implements Validator<HouseholdMemberBulkRequest, HouseholdMember> {

    private final HouseholdMemberRepository householdMemberRepository;

    /**
     * Constructor to initialize the HouseholdMemberRepository dependency.
     *
     * @param householdMemberRepository The repository for household members.
     */
    @Autowired
    public HmNonExistentEntityValidator(HouseholdMemberRepository householdMemberRepository) {
        this.householdMemberRepository = householdMemberRepository;
    }


    /**
     * Validates the non-existence of household members.
     *
     * @param request The bulk request containing household members.
     * @return A map containing household members and their associated error details.
     */
    @Override
    public Map<HouseholdMember, List<Error>> validate(HouseholdMemberBulkRequest request) {
        // Map to hold household members and their error details
        Map<HouseholdMember, List<Error>> errorDetailsMap = new HashMap<>();
        // Get the list of household members from the request
        List<HouseholdMember> householdMembers = request.getHouseholdMembers();
        // Log message for validation process
        log.info("Validating non-existent household members");
        // Get class and method information for ID retrieval
        Class<?> objClass = getObjClass(householdMembers);
        Method idMethod = getMethod(GET_ID, objClass);
        // Create a map of household members with their IDs as keys
        Map<String, HouseholdMember> iMap = getIdToObjMap(householdMembers
                .stream().filter(notHavingErrors()).collect(Collectors.toList()), idMethod);

        // Lists to store IDs and client reference IDs
        List<String> idList = new ArrayList<>();
        List<String> clientReferenceIdList = new ArrayList<>();
        // Extract IDs and client reference IDs from household entities
        householdMembers.forEach(householdMember -> {
            idList.add(householdMember.getId());
            clientReferenceIdList.add(householdMember.getClientReferenceId());
        });

        // Check if the map is not empty
        if (!iMap.isEmpty()) {

            // Create a search object for querying existing entities
            HouseholdMemberSearch householdMemberSearch = HouseholdMemberSearch.builder()
                    .clientReferenceId(clientReferenceIdList)
                    .id(idList)
                    .build();

            List<HouseholdMember> existingHouseholdMembers;
            try {
                // Query the repository to find existing entities
                existingHouseholdMembers = householdMemberRepository.find(householdMemberSearch, householdMembers.size(), 0,
                        householdMembers.get(0).getTenantId(), null, false).getResponse();
            } catch (Exception e) {
                // Handle query builder exception
                log.error("Search failed for HouseholdMember with error: {}", e.getMessage(), e);
                throw new CustomException("HOUSEHOLD_MEMBER_SEARCH_FAILED", "Search Failed for HouseholdMember, " + e.getMessage()); 
            }

            // Check for non-existent household members
            List<HouseholdMember> nonExistentIndividuals = checkNonExistentEntities(iMap,
                    existingHouseholdMembers, idMethod);
            // For each non-existent household member, populate error details
            nonExistentIndividuals.forEach(householdMember -> {
                Error error = getErrorForNonExistentEntity();
                populateErrorDetails(householdMember, error, errorDetailsMap);
            });
        }
        // Log message for validation completion
        log.info("Household member non-existent validation completed successfully, total errors: " + errorDetailsMap.size());
        return errorDetailsMap;
    }
}
