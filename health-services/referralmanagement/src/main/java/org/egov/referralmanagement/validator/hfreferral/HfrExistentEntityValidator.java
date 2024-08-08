package org.egov.referralmanagement.validator.hfreferral;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.Error;
import org.egov.common.models.referralmanagement.hfreferral.HFReferral;
import org.egov.common.models.referralmanagement.hfreferral.HFReferralBulkRequest;
import org.egov.common.models.referralmanagement.hfreferral.HFReferralSearch;
import org.egov.common.validator.Validator;
import org.egov.referralmanagement.repository.HFReferralRepository;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import static org.egov.common.utils.CommonUtils.getIdFieldName;
import static org.egov.common.utils.CommonUtils.notHavingErrors;
import static org.egov.common.utils.CommonUtils.populateErrorDetails;
import static org.egov.common.utils.ValidatorUtils.getErrorForUniqueEntity;

/**
 * Validator class for checking the existence of entities with the given client reference IDs.
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
     * Validates the existence of entities with the given client reference IDs.
     *
     * @param request The bulk request containing HFReferral entities.
     * @return A map containing HFReferral entities and their associated error details.
     */
    @Override
    public Map<HFReferral, List<Error>> validate(HFReferralBulkRequest request) {
        // Map to hold HFReferral entities and their error details
        Map<HFReferral, List<Error>> errorDetailsMap = new HashMap<>();
        // Get the list of HFReferral entities from the request
        List<HFReferral> entities = request.getHfReferrals();
        // Extract client reference IDs from HFReferral entities without errors
        List<String> clientReferenceIdList = entities.stream()
                .filter(notHavingErrors())
                .map(HFReferral::getClientReferenceId)
                .collect(Collectors.toList());
        // Create a search object for querying entities by client reference IDs
        HFReferralSearch hfReferralSearch = HFReferralSearch.builder()
                .clientReferenceId(clientReferenceIdList)
                .build();
        // Check if the client reference ID list is not empty
        if (!CollectionUtils.isEmpty(clientReferenceIdList)) {
            // Query the repository to find existing entities by client reference IDs
            List<HFReferral> existentEntities = hfReferralRepository.validateClientReferenceIdsFromDB(clientReferenceIdList);
            // For each existing entity, populate error details for uniqueness
            existentEntities.forEach(entity -> {
                Error error = getErrorForUniqueEntity();
                populateErrorDetails(entity, error, errorDetailsMap);
            });
        }
        return errorDetailsMap;
    }

}
