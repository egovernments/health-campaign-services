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

import static org.egov.common.utils.CommonUtils.notHavingErrors;
import static org.egov.common.utils.CommonUtils.populateErrorDetails;
import static org.egov.common.utils.ValidatorUtils.getErrorForUniqueEntity;

/**
 * Validator class for checking the existence of entities with the given client reference IDs.
 * This validator checks if the provided individual entities already exist in the database based on their client reference IDs.
 *
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
     * This method checks if any of the individual entities in the request already exist in the database,
     * based on their client reference IDs. If an entity is found to exist, an error is added to the error details map.
     *
     * @param request The bulk request containing individual entities.
     * @return A map containing individual entities and their associated error details, if any.
     */
    @Override
    public Map<Individual, List<Error>> validate(IndividualBulkRequest request) {
        // Map to hold individual entities and their associated error details
        Map<Individual, List<Error>> errorDetailsMap = new HashMap<>();

        // Get the list of individual entities from the request
        List<Individual> entities = request.getIndividuals();

        // Extract client reference IDs from individual entities that do not have errors
        List<String> clientReferenceIdList = entities.stream()
                .filter(notHavingErrors()) // Filter out entities that already have errors
                .map(Individual::getClientReferenceId) // Map to client reference IDs
                .collect(Collectors.toList()); // Collect the IDs into a list

        // Create a map of client reference ID to Individual entity for easy lookup
        Map<String, Individual> map = entities.stream()
                .filter(entity -> StringUtils.hasText(entity.getClientReferenceId())) // Ensure client reference ID is not empty
                .collect(Collectors.toMap(entity -> entity.getClientReferenceId(), entity -> entity)); // Collect to a map

        // Create a search object for querying entities by client reference IDs
        IndividualSearch individualSearch = IndividualSearch.builder()
                .clientReferenceId(clientReferenceIdList) // Set the client reference IDs for the search
                .build();

        // Check if the client reference ID list is not empty before querying the database
        if (!CollectionUtils.isEmpty(clientReferenceIdList)) {
            // Query the repository to find existing entities by client reference IDs
            List<String> existingClientReferenceIds =
                    individualRepository.validateClientReferenceIdsFromDB(clientReferenceIdList, Boolean.TRUE);

            // For each existing client reference ID, populate error details for uniqueness
            existingClientReferenceIds.forEach(clientReferenceId -> {
                // Get a predefined error object for unique entity validation
                Error error = getErrorForUniqueEntity();
                // Populate error details for the individual entity associated with the client reference ID
                populateErrorDetails(map.get(clientReferenceId), error, errorDetailsMap);
            });
        }

        // Return the map containing individual entities and their associated error details
        return errorDetailsMap;
    }

}
