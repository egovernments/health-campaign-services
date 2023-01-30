package org.egov.individual.validators;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.Error;
import org.egov.common.validator.Validator;
import org.egov.individual.repository.IndividualRepository;
import org.egov.individual.web.models.Individual;
import org.egov.individual.web.models.IndividualBulkRequest;
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
import static org.egov.common.utils.CommonUtils.populateErrorDetails;
import static org.egov.common.utils.ValidatorUtils.getErrorForNonExistentEntity;
import static org.egov.individual.Constants.GET_ID;

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
        Map<Individual, List<Error>> errorDetailsMap = new HashMap<>();
        List<Individual> individuals = request.getIndividuals();
        Class<?> objClass = getObjClass(individuals);
        Method idMethod = getMethod(GET_ID, objClass);
        Map<String, Individual> iMap = getIdToObjMap(individuals
                .stream().filter(notHavingErrors()).collect(Collectors.toList()), idMethod);
        if (!iMap.isEmpty()) {
            List<String> individualIds = new ArrayList<>(iMap.keySet());
            List<Individual> existingIndividuals = individualRepository.findById(individualIds,
                    getIdFieldName(idMethod), false);
            List<Individual> nonExistentIndividuals = checkNonExistentEntities(iMap,
                    existingIndividuals, idMethod);
            nonExistentIndividuals.forEach(individual -> {
                Error error = getErrorForNonExistentEntity();
                populateErrorDetails(individual, error, errorDetailsMap);
            });
        }
        return errorDetailsMap;
    }
}
