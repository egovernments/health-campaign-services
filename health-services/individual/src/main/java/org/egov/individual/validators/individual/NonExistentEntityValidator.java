package org.egov.individual.validators.individual;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.Error;
import org.egov.common.models.individual.Individual;
import org.egov.common.models.individual.IndividualBulkRequest;
import org.egov.common.models.individual.IndividualSearch;
import org.egov.common.validator.Validator;
import org.egov.individual.repository.IndividualRepository;
import org.egov.tracer.model.CustomException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.egov.common.utils.CommonUtils.checkNonExistentEntities;
import static org.egov.common.utils.CommonUtils.getIdFieldName;
import static org.egov.common.utils.CommonUtils.getIdMethod;
import static org.egov.common.utils.CommonUtils.getIdToObjMap;
import static org.egov.common.utils.CommonUtils.getMethod;
import static org.egov.common.utils.CommonUtils.getObjClass;
import static org.egov.common.utils.CommonUtils.notHavingErrors;
import static org.egov.common.utils.CommonUtils.populateErrorDetails;
import static org.egov.common.utils.ValidatorUtils.getErrorForNonExistentEntity;
import static org.egov.common.utils.ValidatorUtils.getErrorForNonExistentSubEntity;
import static org.egov.individual.Constants.GET_ADDRESS;
import static org.egov.individual.Constants.GET_ID;
import static org.egov.individual.Constants.GET_IDENTIFIERS;
import static org.egov.individual.Constants.GET_SKILLS;


@Component
@Order(value = 4)
@Slf4j
public class NonExistentEntityValidator implements Validator<IndividualBulkRequest, Individual> {

    private final IndividualRepository individualRepository;

    @Autowired
    public NonExistentEntityValidator(IndividualRepository individualRepository) {
        this.individualRepository = individualRepository;
    }


    @Override
    public Map<Individual, List<Error>> validate(IndividualBulkRequest request) {
        log.info("validating for existence of entity");
        Map<Individual, List<Error>> errorDetailsMap = new HashMap<>();
        List<Individual> individuals = request.getIndividuals();
        Class<?> objClass = getObjClass(individuals);
        Method idMethod = getMethod(GET_ID, objClass);
        Map<String, Individual> iMap = getIdToObjMap(individuals
                .stream().filter(notHavingErrors()).collect(Collectors.toList()), idMethod);
        // Lists to store IDs and client reference IDs
        List<String> idList = new ArrayList<>();
        List<String> clientReferenceIdList = new ArrayList<>();
        // Extract IDs and client reference IDs from individual entities
        individuals.forEach(individual -> {
            idList.add(individual.getId());
            clientReferenceIdList.add(individual.getClientReferenceId());
        });
        if (!iMap.isEmpty()) {
            // Create a search object for querying existing entities
            IndividualSearch individualSearch = IndividualSearch.builder()
                    .id(idList)
                    .clientReferenceId(clientReferenceIdList)
                    .build();

            List<Individual> existingIndividuals;
            try {
                // Query the repository to find existing entities
                existingIndividuals = individualRepository.find(individualSearch, individuals.size(), 0,
                        individuals.get(0).getTenantId(), null, false).getResponse();
            } catch (Exception e) {
                // Handle query builder exception
                log.error("Search failed for Individual with error: {}", e.getMessage(), e);
                throw new CustomException("INDIVIDUAL_SEARCH_FAILED", "Search Failed for Individual, " + e.getMessage()); 
            }
            List<Individual> nonExistentIndividuals = checkNonExistentEntities(iMap,
                    existingIndividuals, idMethod);
            nonExistentIndividuals.forEach(individual -> {
                Error error = getErrorForNonExistentEntity();
                populateErrorDetails(individual, error, errorDetailsMap);
            });

            existingIndividuals.forEach(individual -> {
                validateSubEntity(errorDetailsMap, iMap, individual,
                        individual.getAddress(), GET_ADDRESS);
                validateSubEntity(errorDetailsMap, iMap, individual,
                        individual.getSkills(), GET_SKILLS);
                validateSubEntity(errorDetailsMap, iMap, individual,
                        individual.getIdentifiers(), GET_IDENTIFIERS);
            });
        }

        return errorDetailsMap;
    }

    private <T> void validateSubEntity(Map<Individual, List<Error>> errorDetailsMap,
                                          Map<String, Individual> iMap,
                                          Individual individual,
                                          List<T> subEntities,
                                       String getSubEntityMethodName) {
        List<T> subEntitiesInReq = (List<T>) ReflectionUtils.invokeMethod(getMethod(getSubEntityMethodName, Individual.class),
                iMap.get(individual.getId()));

        if (subEntities != null && !subEntities.isEmpty()) {
            List<String> existingSubEntityIds = subEntities.stream()
                    .map(obj -> (String) ReflectionUtils.invokeMethod(getIdMethod(subEntities), obj))
                    .collect(Collectors.toList());
            if (subEntitiesInReq != null && !subEntitiesInReq.isEmpty()) {
                List<T> nonExistingSubEntities = subEntitiesInReq.stream().filter(subEntity ->  {
                    String id = (String) ReflectionUtils.invokeMethod(getMethod(GET_ID, subEntity.getClass()), subEntity);
                    return id != null && !existingSubEntityIds.contains(id);
                }).collect(Collectors.toList());

                if (!nonExistingSubEntities.isEmpty()) {
                    nonExistingSubEntities.forEach(subEntity -> {
                        Error error = getErrorForNonExistentSubEntity((String) ReflectionUtils
                                .invokeMethod(getMethod(GET_ID, subEntity.getClass()), subEntity));
                        populateErrorDetails(iMap.get(individual.getId()), error, errorDetailsMap);
                    });
                }
            }
        } else if (subEntitiesInReq != null && !subEntitiesInReq.isEmpty()) {
            subEntitiesInReq.stream()
                    .filter(subEntity ->  ReflectionUtils.invokeMethod(getMethod(GET_ID, subEntity.getClass()), subEntity) != null)
                    .forEach(subEntity -> {
                Error error = getErrorForNonExistentSubEntity((String) ReflectionUtils
                        .invokeMethod(getMethod(GET_ID, subEntity.getClass()), subEntity));
                populateErrorDetails(iMap.get(individual.getId()), error, errorDetailsMap);
            });
        }
    }
}
