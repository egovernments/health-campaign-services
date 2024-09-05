package org.egov.referralmanagement.validator;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.Error;
import org.egov.common.models.referralmanagement.Referral;
import org.egov.common.models.referralmanagement.ReferralBulkRequest;
import org.egov.common.models.referralmanagement.ReferralSearch;
import org.egov.common.validator.Validator;
import org.egov.referralmanagement.repository.ReferralRepository;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import static org.egov.common.utils.CommonUtils.notHavingErrors;
import static org.egov.common.utils.CommonUtils.populateErrorDetails;
import static org.egov.common.utils.ValidatorUtils.getErrorForUniqueEntity;

/**
 * Validator class for checking the existence of referral entities with the given client reference IDs.
 * This validator checks if the provided Referral entities already exist in the database based on their client reference IDs.
 *
 * @author kanishq-egov
 */
@Component
@Order(value = 1)
@Slf4j
public class RmExistentEntityValidator implements Validator<ReferralBulkRequest, Referral> {

    private final ReferralRepository referralRepository;

    /**
     * Constructor to initialize the ReferralRepository dependency.
     *
     * @param referralRepository The repository used to validate referral entities.
     */
    public RmExistentEntityValidator(ReferralRepository referralRepository) {
        this.referralRepository = referralRepository;
    }

    /**
     * Validates the existence of Referral entities with the given client reference IDs.
     * Checks if the provided Referral entities already exist in the database.
     *
     * @param request The bulk request containing Referral entities.
     * @return A map containing Referral entities and their associated error details if any duplicates are found.
     */
    @Override
    public Map<Referral, List<Error>> validate(ReferralBulkRequest request) {
        // Map to hold Referral entities and their error details
        Map<Referral, List<Error>> errorDetailsMap = new HashMap<>();

        // Get the list of Referral entities from the request
        List<Referral> entities = request.getReferrals();

        // Extract client reference IDs from Referral entities that do not have existing errors
        List<String> clientReferenceIdList = entities.stream()
                .filter(notHavingErrors()) // Filter out entities that already have errors
                .map(Referral::getClientReferenceId) // Extract client reference IDs from Referral entities
                .collect(Collectors.toList()); // Collect the IDs into a list

        // Create a map for quick lookup of Referral entities by client reference ID
        Map<String, Referral> map = entities.stream()
                .filter(entity -> StringUtils.hasText(entity.getClientReferenceId())) // Ensure client reference ID is not empty
                .collect(Collectors.toMap(entity -> entity.getClientReferenceId(), entity -> entity)); // Collect to a map

        // Create a search object for querying existing Referral entities by client reference IDs
        ReferralSearch referralSearch = ReferralSearch.builder()
                .clientReferenceId(clientReferenceIdList) // Set the client reference IDs for the search
                .build();

        // Check if the client reference ID list is not empty before querying the database
        if (!CollectionUtils.isEmpty(clientReferenceIdList)) {
            // Query the repository to find existing Referral entities with the given client reference IDs
            List<String> existingClientReferenceIds = referralRepository.validateClientReferenceIdsFromDB(clientReferenceIdList, Boolean.TRUE);

            // For each existing client reference ID, add an error to the map for the corresponding Referral entity
            existingClientReferenceIds.forEach(clientReferenceId -> {
                // Get a predefined error object for unique entity validation
                Error error = getErrorForUniqueEntity();
                // Populate error details for the individual Referral entity associated with the client reference ID
                populateErrorDetails(map.get(clientReferenceId), error, errorDetailsMap);
            });
        }

        // Return the map containing Referral entities and their associated error details
        return errorDetailsMap;
    }
}
