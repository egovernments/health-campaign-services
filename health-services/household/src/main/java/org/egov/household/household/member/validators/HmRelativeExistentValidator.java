package org.egov.household.household.member.validators;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.Error;
import org.egov.common.models.core.EgovModel;
import org.egov.common.models.core.EgovOfflineModel;
import org.egov.common.models.household.HouseholdMember;
import org.egov.common.models.household.HouseholdMemberBulkRequest;
import org.egov.common.models.household.HouseholdMemberSearch;
import org.egov.common.models.household.Relationship;
import org.egov.common.validator.Validator;
import org.egov.household.repository.HouseholdMemberRepository;
import org.egov.tracer.model.CustomException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;

import static org.egov.common.utils.CommonUtils.getIdMethod;
import static org.egov.common.utils.CommonUtils.getIdToObjMap;
import static org.egov.common.utils.CommonUtils.notHavingErrors;
import static org.egov.common.utils.CommonUtils.populateErrorDetails;
import static org.egov.common.utils.ValidatorUtils.getErrorForInvalidRelatedEntityID;
import static org.egov.common.utils.ValidatorUtils.getErrorForNonExistentRelatedEntity;
import static org.egov.household.Constants.CLIENT_REFERENCE_ID_FIELD;
import static org.egov.household.Constants.ID_FIELD;

/**
 * Validator class for checking the non-existence of household member relatives.
 * This validator checks if the provided household member relatives do not already exist in the database.
 * @author holashchand
 */
@Component
@Order(value = 11)
@Slf4j
public class HmRelativeExistentValidator implements Validator<HouseholdMemberBulkRequest, HouseholdMember> {

    private final HouseholdMemberRepository householdMemberRepository;

    /**
     * Constructor to initialize the HouseholdMemberRepository dependency.
     *
     * @param householdMemberRepository The repository for household members.
     */
    @Autowired
    public HmRelativeExistentValidator(HouseholdMemberRepository householdMemberRepository) {
        this.householdMemberRepository = householdMemberRepository;
    }


    /**
     * Validates the non-existence of household member relatives.
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
        if (CollectionUtils.isEmpty(householdMembers)) return errorDetailsMap;
        // Log message for validation process
        log.info("Validating non-existent household member relatives");
        String tenantId = householdMembers.get(0).getTenantId();
        // Get class and method information for ID retrieval
        log.info("getting id method for household members");
        Method idMethodHouseholdMember = getIdMethod(householdMembers, ID_FIELD, CLIENT_REFERENCE_ID_FIELD);
        // Create a map of household members with their IDs as keys
        Map<String, HouseholdMember> iMap = getIdToObjMap(householdMembers
                .stream().filter(notHavingErrors()).collect(Collectors.toList()), idMethodHouseholdMember);

        // Lists to store IDs and client reference IDs
        List<String> idList = new ArrayList<>();
        List<String> clientReferenceIdList = new ArrayList<>();
        List<String> hmClientReferenceIdList = new ArrayList<>();
        // Extract IDs and client reference IDs from household entities
        householdMembers.forEach(householdMember -> {
            if (!CollectionUtils.isEmpty(householdMember.getMemberRelationships())) {
                idList.addAll(householdMember.getMemberRelationships().stream()
                        .map(Relationship::getRelativeId)
                        .filter(id -> !ObjectUtils.isEmpty(id)).toList());
                clientReferenceIdList.addAll(householdMember.getMemberRelationships().stream()
                        .map(Relationship::getRelativeClientReferenceId)
                        .filter(id -> !ObjectUtils.isEmpty(id)).toList());
            }
            if(!ObjectUtils.isEmpty(householdMember.getClientReferenceId())) {
                hmClientReferenceIdList.add(householdMember.getClientReferenceId());
                clientReferenceIdList.add(householdMember.getClientReferenceId());
            } if (!ObjectUtils.isEmpty(householdMember.getId())) {
                idList.add(householdMember.getId());
            }

        });

        // Check if the map is not empty
        if (!iMap.isEmpty() && !CollectionUtils.isEmpty(clientReferenceIdList)) {

            List<HouseholdMember> existingHouseholdMembersByIds;
            List<HouseholdMember> existingHouseholdMembersByCRIds;
            try {
                existingHouseholdMembersByIds = householdMemberRepository.find(
                        HouseholdMemberSearch.builder().id(idList).build(), householdMembers.size(), 0,
                        tenantId, null, false).getResponse();
                existingHouseholdMembersByCRIds = householdMemberRepository.find(
                        HouseholdMemberSearch.builder().clientReferenceId(clientReferenceIdList).build(), householdMembers.size(), 0,
                        tenantId, null, false).getResponse();
            } catch (Exception e) {
                log.error("Search failed for HouseholdMember with error: {}", e.getMessage(), e);
                throw new CustomException("HOUSEHOLD_MEMBER_SEARCH_FAILED", "Search Failed for HouseholdMember, " + e.getMessage()); 
            }

            Map<String, HouseholdMember> existingRelativesIds = existingHouseholdMembersByIds.stream()
                    .collect(Collectors.toMap(
                            EgovModel::getId,
                            householdMember -> householdMember)
                    );
            Map<String, HouseholdMember> existingRelativesCRIds = existingHouseholdMembersByCRIds.stream()
                    .collect(Collectors.toMap(
                            EgovOfflineModel::getClientReferenceId,
                            householdMember -> householdMember)
                    );

            householdMembers.forEach(householdMember -> {
                if (!CollectionUtils.isEmpty(householdMember.getMemberRelationships())) {
                    householdMember.getMemberRelationships().forEach(d -> {
                        if (!ObjectUtils.isEmpty(d.getRelativeClientReferenceId()) && existingRelativesCRIds.containsKey(d.getRelativeClientReferenceId())) {
                            d.setRelativeId(existingRelativesCRIds.get(d.getRelativeClientReferenceId()).getId());
                        }
                    });
                    boolean hasInvalidSelfOrRelatives = householdMember.getMemberRelationships().stream()
                            .anyMatch(relationship -> isValidRelativeAndSelf(householdMember, relationship));
                    if (hasInvalidSelfOrRelatives) {
                        Error error = getErrorForInvalidRelatedEntityID();
                        populateErrorDetails(householdMember, error, errorDetailsMap);
                        log.error("Invalid self or relatives {}", householdMember);
                    } else {
                        List<String> nonExistingRelatives = householdMember.getMemberRelationships().stream()
                                .filter(d -> !(
                                        (!ObjectUtils.isEmpty(d.getRelativeId()) && existingRelativesIds.containsKey(d.getRelativeId()))
                                        || (!ObjectUtils.isEmpty(d.getRelativeClientReferenceId())
                                                && (existingRelativesCRIds.containsKey(d.getRelativeClientReferenceId())
                                                        || hmClientReferenceIdList.contains(d.getRelativeClientReferenceId())))
                                        )
                                ).map(d -> Optional.ofNullable(d.getRelativeId())
                                        .orElse(d.getRelativeClientReferenceId())
                                ).toList();
                        if (!CollectionUtils.isEmpty(nonExistingRelatives)) {
                            Error error = getErrorForNonExistentRelatedEntity(nonExistingRelatives);
                            populateErrorDetails(householdMember, error, errorDetailsMap);
                            log.error("Household member relative not existing {}", householdMember);
                        }
                    }

                }
            });
        }
        // Log message for validation completion
        log.debug("Household member relatives non-existent validation completed successfully, total errors: {}", errorDetailsMap.size());
        return errorDetailsMap;
    }

    private boolean isValidRelativeAndSelf(HouseholdMember householdMember, Relationship relationship) {
        return isRelativeIdMissing(relationship)
                || EqualsOrNotExists(householdMember.getId(), relationship.getRelativeId())
                || EqualsOrNotExists(householdMember.getClientReferenceId(), relationship.getRelativeClientReferenceId())
                || !EqualsOrNotExists(householdMember.getId(), relationship.getSelfId())
                || !EqualsOrNotExists(householdMember.getClientReferenceId(), relationship.getSelfClientReferenceId());
    }

    private boolean isRelativeIdMissing(Relationship relationship) {
        return ObjectUtils.isEmpty(relationship.getRelativeClientReferenceId()) && ObjectUtils.isEmpty(relationship.getRelativeId());
    }

    private static boolean EqualsOrNotExists(String value1, String value2) {
        return ObjectUtils.isEmpty(value1) || ObjectUtils.isEmpty(value2) || value1.equals(value2);
    }
}
