package org.egov.individual.validators;

import java.lang.reflect.Method;
import java.util.ArrayList;
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
import org.egov.tracer.model.CustomException;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import static org.egov.common.utils.CommonUtils.checkNonExistentEntities;
import static org.egov.common.utils.CommonUtils.getIdToObjMap;
import static org.egov.common.utils.CommonUtils.getMethod;
import static org.egov.common.utils.CommonUtils.getObjClass;
import static org.egov.common.utils.CommonUtils.notHavingErrors;
import static org.egov.common.utils.CommonUtils.populateErrorDetails;
import static org.egov.common.utils.ValidatorUtils.getErrorForNonExistentEntity;
import static org.egov.individual.Constants.GET_ID;

/**
 * Validator for checking the non-existence of individual entities.
 * This validator checks if the provided individual entities do not already exist in the database.
 *
 * @author kanishq-egov
 */
@Component
@Order(value = 2)
@Slf4j
public class INonExistentEntityValidator implements Validator<IndividualBulkRequest, Individual> {

    private final IndividualRepository individualRepository;

    public INonExistentEntityValidator(IndividualRepository individualRepository) {
        this.individualRepository = individualRepository;
    }

    /**
     * Validates the non-existence of entities based on their IDs and client reference IDs.
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
        // Get the class of the individual entity
        Class<?> objClass = getObjClass(entities);
        // Get the method for fetching the ID of the entity
        Method idMethod = getMethod(GET_ID, objClass);
        // Map to store entities by their IDs
        Map<String, Individual> eMap = getIdToObjMap(
                entities.stream().filter(notHavingErrors()).collect(Collectors.toList()), idMethod);
        // Lists to store IDs and client reference IDs
        List<String> idList = new ArrayList<>();
        List<String> clientReferenceIdList = new ArrayList<>();
        // Extract IDs and client reference IDs from individual entities
        entities.forEach(individual -> {
            idList.add(individual.getId());
            clientReferenceIdList.add(individual.getClientReferenceId());
        });
        // Check if the entity map is not empty
        if (!eMap.isEmpty()) {

            // Create a search object for querying existing entities
            IndividualSearch individualSearch = IndividualSearch.builder()
                    .id(idList)
                    .clientReferenceId(clientReferenceIdList)
                    .build();

            List<Individual> existingEntities;
            try {
                // Query the repository to find existing entities
                existingEntities = individualRepository.find(individualSearch, entities.size(), 0,
                        entities.get(0).getTenantId(), null, false).getResponse();
            } catch (Exception e) {
                // Handle query builder exception
                existingEntities = new ArrayList<>();
                log.error("Search failed for Individual with error: {}", e.getMessage(), e);
                throw new CustomException("INDIVIDUAL_SEARCH_FAILED", "Search Failed for Individual, " + e.getMessage()); 
            }
            // Check for non-existent entities
            List<Individual> nonExistentEntities = checkNonExistentEntities(eMap,
                    existingEntities, idMethod);
            // Populate error details for non-existent entities
            nonExistentEntities.forEach(entity -> {
                Error error = getErrorForNonExistentEntity();
                populateErrorDetails(entity, error, errorDetailsMap);
            });
        }

        return errorDetailsMap;
    }
}

