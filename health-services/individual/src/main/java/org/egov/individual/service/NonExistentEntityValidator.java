package org.egov.individual.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.egov.individual.repository.IndividualRepository;
import org.egov.individual.web.models.Individual;
import org.egov.individual.web.models.IndividualBulkRequest;
import org.egov.tracer.model.CustomException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

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
import static org.egov.common.utils.CommonUtils.notHavingErrors;

@Component
@Order(value = 3)
public class NonExistentEntityValidator implements Validator<IndividualBulkRequest, Individual> {

    private final ObjectMapper objectMapper;

    private final IndividualRepository individualRepository;

    @Autowired
    public NonExistentEntityValidator(ObjectMapper objectMapper,
                                      IndividualRepository individualRepository) {
        this.objectMapper = objectMapper;
        this.individualRepository = individualRepository;
    }


    @Override
    public Map<Individual, List<Error>> validate(IndividualBulkRequest request) {
        Map<Individual, List<Error>> errorDetailsMap = new HashMap<>();
        Method idMethod = getIdMethod(request.getIndividuals());
        Map<String, Individual> iMap = getIdToObjMap(request.getIndividuals()
                .stream().filter(notHavingErrors()).collect(Collectors.toList()), idMethod);
        List<String> individualIds = new ArrayList<>(iMap.keySet());
        List<Individual> existingIndividuals = individualRepository.findById(individualIds,
                getIdFieldName(idMethod), false);
        List<Individual> nonExistentIndividuals = checkNonExistentEntities(iMap,
                existingIndividuals, idMethod);
        nonExistentIndividuals.forEach(individual -> {
            Error error = Error.builder().errorMessage("Individual does not exist in db").errorCode("NON_EXISTENT_INDIVIDUAL")
                    .exception(new CustomException("NON_EXISTENT_INDIVIDUAL", "Individual does not exist in db")).build();
            populateErrorDetails(individual, error, errorDetailsMap, objectMapper);
        });
        return errorDetailsMap;
    }
}
