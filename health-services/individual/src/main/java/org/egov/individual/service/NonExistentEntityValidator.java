package org.egov.individual.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
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
import static org.egov.common.utils.CommonUtils.getIdToObjMap;
import static org.egov.common.utils.CommonUtils.getMethod;
import static org.egov.common.utils.CommonUtils.getObjClass;
import static org.egov.common.utils.CommonUtils.notHavingErrors;

@Component
@Order(value = 3)
@Slf4j
public class NonExistentEntityValidator implements Validator<IndividualBulkRequest, Individual> {

    private final ObjectMapper objectMapper;

    private final IndividualRepository individualRepository;

    private static final Error.ErrorType ERROR_TYPE = Error.ErrorType.NON_RECOVERABLE;

    @Autowired
    public NonExistentEntityValidator(ObjectMapper objectMapper,
                                      IndividualRepository individualRepository) {
        this.objectMapper = objectMapper;
        this.individualRepository = individualRepository;
    }


    @Override
    public Map<Individual, List<Error>> validate(IndividualBulkRequest request) {
        Map<Individual, List<Error>> errorDetailsMap = new HashMap<>();
        List<Individual> individuals = request.getIndividuals();
        Class<?> objClass = getObjClass(individuals);
        Method idMethod = getMethod("getId", objClass);
        Map<String, Individual> iMap = getIdToObjMap(individuals
                .stream().filter(notHavingErrors()).collect(Collectors.toList()), idMethod);
        if (!iMap.isEmpty()) {
            List<String> individualIds = new ArrayList<>(iMap.keySet());
            List<Individual> existingIndividuals = individualRepository.findById(individualIds,
                    getIdFieldName(idMethod), false);
            List<Individual> nonExistentIndividuals = checkNonExistentEntities(iMap,
                    existingIndividuals, idMethod);
            nonExistentIndividuals.forEach(individual -> {
                Error error = Error.builder().errorMessage("Individual does not exist in db")
                        .errorCode("NON_EXISTENT_INDIVIDUAL")
                        .type(ERROR_TYPE)
                        .exception(new CustomException("NON_EXISTENT_INDIVIDUAL", "Individual does not exist in db")).build();
                populateErrorDetails(individual, error, errorDetailsMap, objectMapper);
            });
        }
        log.info("non existance validation finished");
        return errorDetailsMap;
    }
}
