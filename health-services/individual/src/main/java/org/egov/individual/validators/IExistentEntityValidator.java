package org.egov.individual.validators;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.Error;
import org.egov.common.models.individual.Individual;
import org.egov.common.models.individual.IndividualBulkRequest;
import org.egov.common.models.individual.IndividualSearch;
import org.egov.common.validator.Validator;
import org.egov.individual.repository.IndividualRepository;
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
 * This validator checks if the provided individual entities already exist in the database based on their client reference IDs.
 * @author kanishq-egov
 */
@Component
@Order(value = 1)
@Slf4j
public class IExistentEntityValidator implements Validator<IndividualBulkRequest, Individual> {

    private final IndividualRepository individualRepository;

    /**
     * Constructor to initialize the IndividualRepository dependency.
     *
     * @param individualRepository The repository for individual entities.
     */
    public IExistentEntityValidator(IndividualRepository individualRepository) {
        this.individualRepository = individualRepository;
    }

    /**
     * Validates the existence of entities with the given client reference IDs.
     *
     * @param request The bulk request containing individual entities.
     * @return A map containing individual entities and their associated error details.
     */
    @Override
    public Map<Individual, List<Error>> validate(IndividualBulkRequest request) {
        // Map to hold individual entities and their error details
        Map<Individual, List<Error>> errorDetailsMap = new HashMap<>();
        // Get the list of individual entities from the request
        List<Individual> entities = request.getIndividuals();
        // Extract client reference IDs from individual entities without errors
        List<String> clientReferenceIdList = entities.stream()
                .filter(notHavingErrors())
                .map(Individual::getClientReferenceId)
                .collect(Collectors.toList());
        Map<String, Individual> map = entities.stream()
                .filter(individual -> StringUtils.isEmpty(individual.getClientReferenceId()))
                .collect(Collectors.toMap(entity -> entity.getClientReferenceId(), entity -> entity));
        // Create a search object for querying entities by client reference IDs
        IndividualSearch individualSearch = IndividualSearch.builder()
                .clientReferenceId(clientReferenceIdList)
                .build();
        // Check if the client reference ID list is not empty
        if (!CollectionUtils.isEmpty(clientReferenceIdList)) {
            // Query the repository to find existing entities by client reference IDs
            List<String> existingClientReferenceIds =
                    individualRepository.validateClientReferenceIdsFromDB(clientReferenceIdList);
            // For each existing entity, populate error details for uniqueness
            existingClientReferenceIds.forEach(clientReferenceId -> {
                Error error = getErrorForUniqueEntity();
                populateErrorDetails(map.get(clientReferenceId), error, errorDetailsMap);
            });
        }
        return errorDetailsMap;
    }

}

