package org.egov.referralmanagement.validator.sideeffect;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.exception.InvalidTenantIdException;
import org.egov.common.models.Error;
import org.egov.common.models.referralmanagement.sideeffect.SideEffect;
import org.egov.common.models.referralmanagement.sideeffect.SideEffectBulkRequest;
import org.egov.common.models.referralmanagement.sideeffect.SideEffectSearch;
import org.egov.common.validator.Validator;
import org.egov.referralmanagement.repository.SideEffectRepository;
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
 * Validator class for checking the existence of SideEffect entities with the given client reference IDs.
 * This validator checks if the provided SideEffect entities already exist in the database based on their client reference IDs.
 *
 * @author kanishq-egov
 */
@Component
@Order(value = 1)
@Slf4j
public class SeExistentEntityValidator implements Validator<SideEffectBulkRequest, SideEffect> {

    private final SideEffectRepository sideEffectRepository;

    /**
     * Constructor to initialize the SideEffectRepository dependency.
     *
     * @param sideEffectRepository The repository for SideEffect entities.
     */
    public SeExistentEntityValidator(SideEffectRepository sideEffectRepository) {
        this.sideEffectRepository = sideEffectRepository;
    }

    /**
     * Validates the existence of SideEffect entities in the SideEffectBulkRequest.
     * Checks if the provided SideEffect entities already exist in the database based on their client reference IDs.
     *
     * @param request The bulk request containing SideEffect entities.
     * @return A map containing SideEffect entities and their associated error details if any duplicates are found.
     */
    @Override
    public Map<SideEffect, List<Error>> validate(SideEffectBulkRequest request) {
        // Map to hold SideEffect entities and their error details
        Map<SideEffect, List<Error>> errorDetailsMap = new HashMap<>();

        // Get the list of SideEffect entities from the request
        List<SideEffect> entities = request.getSideEffects();

        // Extract client reference IDs from SideEffect entities that do not have existing errors
        List<String> clientReferenceIdList = entities.stream()
                .filter(notHavingErrors()) // Filter out entities that already have errors
                .map(SideEffect::getClientReferenceId) // Extract client reference IDs from SideEffect entities
                .collect(Collectors.toList()); // Collect the IDs into a list

        // Create a map for quick lookup of SideEffect entities by client reference ID
        Map<String, SideEffect> map = entities.stream()
                .filter(entity -> StringUtils.hasText(entity.getClientReferenceId())) // Ensure client reference ID is not empty
                .collect(Collectors.toMap(entity -> entity.getClientReferenceId(), entity -> entity)); // Collect to a map

        // Create a search object for querying existing SideEffect entities by client reference IDs
        SideEffectSearch sideEffectSearch = SideEffectSearch.builder()
                .clientReferenceId(clientReferenceIdList) // Set the client reference IDs for the search
                .build();

        // Check if the client reference ID list is not empty before querying the database
        if (!CollectionUtils.isEmpty(clientReferenceIdList)) {
            String tenantId = getTenantId(entities);
            // Query the repository to find existing SideEffect entities with the given client reference IDs
            List<String> existingClientReferenceIds = null;
            try {
                existingClientReferenceIds = sideEffectRepository.validateClientReferenceIdsFromDB(tenantId, clientReferenceIdList, Boolean.TRUE);
                // For each existing client reference ID, add an error to the map for the corresponding SideEffect entity
                existingClientReferenceIds.forEach(clientReferenceId -> {
                    // Get a predefined error object for unique entity validation
                    Error error = getErrorForUniqueEntity();
                    // Populate error details for the individual SideEffect entity associated with the client reference ID
                    populateErrorDetails(map.get(clientReferenceId), error, errorDetailsMap);
                });
            } catch (InvalidTenantIdException exception) {
                map.values().forEach(sideEffect -> {
                    Error error = getErrorForInvalidTenantId(tenantId, exception);
                    populateErrorDetails(sideEffect, error, errorDetailsMap);
                });
            }
        }

        // Return the map containing SideEffect entities and their associated error details
        return errorDetailsMap;
    }
}
