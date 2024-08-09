package org.egov.referralmanagement.validator.sideeffect;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.Error;
import org.egov.common.models.individual.Individual;
import org.egov.common.models.referralmanagement.sideeffect.SideEffect;
import org.egov.common.models.referralmanagement.sideeffect.SideEffectBulkRequest;
import org.egov.common.models.referralmanagement.sideeffect.SideEffectSearch;
import org.egov.common.validator.Validator;
import org.egov.referralmanagement.repository.SideEffectRepository;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import static org.egov.common.utils.CommonUtils.getIdFieldName;
import static org.egov.common.utils.CommonUtils.notHavingErrors;
import static org.egov.common.utils.CommonUtils.populateErrorDetails;
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
     * Validates the existence of SideEffect entities with the given client reference IDs.
     *
     * @param request The bulk request containing SideEffect entities.
     * @return A map containing SideEffect entities and their associated error details.
     */
    @Override
    public Map<SideEffect, List<Error>> validate(SideEffectBulkRequest request) {
        // Map to hold SideEffect entities and their error details
        Map<SideEffect, List<Error>> errorDetailsMap = new HashMap<>();
        // Get the list of SideEffect entities from the request
        List<SideEffect> entities = request.getSideEffects();
        // Extract client reference IDs from SideEffect entities without errors
        List<String> clientReferenceIdList = entities.stream()
                .filter(notHavingErrors())
                .map(SideEffect::getClientReferenceId)
                .collect(Collectors.toList());
        // Create a search object for querying entities by client reference IDs
        SideEffectSearch sideEffectSearch = SideEffectSearch.builder()
                .clientReferenceId(clientReferenceIdList)
                .build();
        Map<String, SideEffect> map = entities.stream()
                .filter(individual -> StringUtils.isEmpty(individual.getClientReferenceId()))
                .collect(Collectors.toMap(entity -> entity.getClientReferenceId(), entity -> entity));
        // Check if the client reference ID list is not empty
        if (!CollectionUtils.isEmpty(clientReferenceIdList)) {
            // Query the repository to find existing entities by client reference IDs
            List<String> existingClientReferenceIds =
                    sideEffectRepository.validateClientReferenceIdsFromDB(clientReferenceIdList, Boolean.TRUE);
            // For each existing entity, populate error details for uniqueness
            existingClientReferenceIds.forEach(clientReferenceId -> {
                Error error = getErrorForUniqueEntity();
                populateErrorDetails(map.get(clientReferenceId), error, errorDetailsMap);
            });
        }
        return errorDetailsMap;
    }
}
