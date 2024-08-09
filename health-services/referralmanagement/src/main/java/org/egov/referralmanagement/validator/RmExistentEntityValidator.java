package org.egov.referralmanagement.validator;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.Error;
import org.egov.common.models.individual.Individual;
import org.egov.common.models.referralmanagement.Referral;
import org.egov.common.models.referralmanagement.ReferralBulkRequest;
import org.egov.common.models.referralmanagement.ReferralSearch;
import org.egov.common.validator.Validator;
import org.egov.referralmanagement.repository.ReferralRepository;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import static org.egov.common.utils.CommonUtils.getIdFieldName;
import static org.egov.common.utils.CommonUtils.notHavingErrors;
import static org.egov.common.utils.CommonUtils.populateErrorDetails;
import static org.egov.common.utils.ValidatorUtils.getErrorForUniqueEntity;

/**
 * Validator class for checking the existence of entities with the given client reference IDs.
 * This validator checks if the provided referral entities already exist in the database based on their client reference IDs.
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
     * @param referralRepository The repository for referral entities.
     */
    public RmExistentEntityValidator(ReferralRepository referralRepository) {
        this.referralRepository = referralRepository;
    }

    /**
     * Validates the existence of entities with the given client reference IDs.
     *
     * @param request The bulk request containing referral entities.
     * @return A map containing referral entities and their associated error details.
     */
    @Override
    public Map<Referral, List<Error>> validate(ReferralBulkRequest request) {
        // Map to hold referral entities and their error details
        Map<Referral, List<Error>> errorDetailsMap = new HashMap<>();
        // Get the list of referral entities from the request
        List<Referral> entities = request.getReferrals();
        // Extract client reference IDs from referral entities without errors
        List<String> clientReferenceIdList = entities.stream()
                .filter(notHavingErrors())
                .map(Referral::getClientReferenceId)
                .collect(Collectors.toList());
        Map<String, Referral> map = entities.stream()
                .filter(individual -> StringUtils.isEmpty(individual.getClientReferenceId()))
                .collect(Collectors.toMap(entity -> entity.getClientReferenceId(), entity -> entity));
        // Create a search object for querying entities by client reference IDs
        ReferralSearch referralSearch = ReferralSearch.builder()
                .clientReferenceId(clientReferenceIdList)
                .build();
        // Check if the client reference ID list is not empty
        if (!CollectionUtils.isEmpty(clientReferenceIdList)) {
            // Query the repository to find existing entities by client reference IDs
            List<String> existingClientReferenceIds = referralRepository.validateClientReferenceIdsFromDB(clientReferenceIdList, Boolean.TRUE);
            // For each existing entity, populate error details for uniqueness
            existingClientReferenceIds.forEach(clientReferenceId -> {
                Error error = getErrorForUniqueEntity();
                populateErrorDetails(map.get(clientReferenceId), error, errorDetailsMap);
            });
        }
        return errorDetailsMap;
    }

}
