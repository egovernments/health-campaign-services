package org.egov.referralmanagement.validator.hfreferral;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.exception.InvalidTenantIdException;
import org.egov.common.models.Error;
import org.egov.common.models.referralmanagement.hfreferral.HFReferral;
import org.egov.common.models.referralmanagement.hfreferral.HFReferralBulkRequest;
import org.egov.common.models.referralmanagement.hfreferral.HFReferralSearch;
import org.egov.common.validator.Validator;
import org.egov.referralmanagement.repository.HFReferralRepository;
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
 * Validator class for checking the existence of HFReferral entities with the given client reference IDs.
 * This validator checks if the provided HFReferral entities already exist in the database based on their client reference IDs.
 *
 * @author kanishq-egov
 */
@Component
@Order(value = 1)
@Slf4j
public class HfrExistentEntityValidator implements Validator<HFReferralBulkRequest, HFReferral> {

    private final HFReferralRepository hfReferralRepository;

    /**
     * Constructor to initialize the HFReferralRepository dependency.
     *
     * @param hfReferralRepository The repository for HFReferral entities.
     */
    public HfrExistentEntityValidator(HFReferralRepository hfReferralRepository) {
        this.hfReferralRepository = hfReferralRepository;
    }

    /**
     * Validates the existence of HFReferral entities in the HFReferralBulkRequest.
     * Checks if the provided HFReferral entities already exist in the database based on their client reference IDs.
     *
     * @param request The bulk request containing HFReferral entities.
     * @return A map containing HFReferral entities and their associated error details if any duplicates are found.
     */
    @Override
    public Map<HFReferral, List<Error>> validate(HFReferralBulkRequest request) {
        // Map to hold HFReferral entities and their error details.
        Map<HFReferral, List<Error>> errorDetailsMap = new HashMap<>();

        // Get the list of HFReferral entities from the request.
        List<HFReferral> entities = request.getHfReferrals();

        // Extract client reference IDs from HFReferral entities that do not have existing errors.
        List<String> clientReferenceIdList = entities.stream()
                .filter(notHavingErrors()) // Filter out entities that already have errors.
                .map(HFReferral::getClientReferenceId) // Extract client reference IDs from HFReferral entities.
                .collect(Collectors.toList()); // Collect the IDs into a list.

        // Create a map for quick lookup of HFReferral entities by client reference ID.
        Map<String, HFReferral> map = entities.stream()
                .filter(entity -> StringUtils.hasText(entity.getClientReferenceId())) // Ensure client reference ID is not empty.
                .collect(Collectors.toMap(entity -> entity.getClientReferenceId(), entity -> entity)); // Collect to a map.

        // Create a search object for querying existing HFReferral entities by client reference IDs.
        HFReferralSearch hfReferralSearch = HFReferralSearch.builder()
                .clientReferenceId(clientReferenceIdList) // Set the client reference IDs for the search.
                .build();

        // Check if the client reference ID list is not empty before querying the database.
        if (!CollectionUtils.isEmpty(clientReferenceIdList)) {
            String tenantId = getTenantId(entities);
            // Query the repository to find existing HFReferral entities with the given client reference IDs.
            List<String> existingClientReferenceIds = null;
            try {
                existingClientReferenceIds = hfReferralRepository.validateClientReferenceIdsFromDB(tenantId, clientReferenceIdList, Boolean.TRUE);
                // For each existing client reference ID, add an error to the map for the corresponding HFReferral entity.
                existingClientReferenceIds.forEach(clientReferenceId -> {
                    // Get a predefined error object for unique entity validation.
                    Error error = getErrorForUniqueEntity();
                    // Populate error details for the individual HFReferral entity associated with the client reference ID.
                    populateErrorDetails(map.get(clientReferenceId), error, errorDetailsMap);
                });
            } catch (InvalidTenantIdException exception) {
                // Populating InvalidTenantIdException for all entities
                map.values().forEach(hfReferral -> {
                    Error error = getErrorForInvalidTenantId(tenantId, exception);
                    populateErrorDetails(hfReferral, error, errorDetailsMap);
                });
            }
        }

        // Return the map containing HFReferral entities and their associated error details.
        return errorDetailsMap;
    }
}
