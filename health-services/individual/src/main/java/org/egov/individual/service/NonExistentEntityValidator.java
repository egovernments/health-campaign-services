package org.egov.individual.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.egov.individual.repository.IndividualRepository;
import org.egov.individual.web.models.Individual;
import org.egov.individual.web.models.IndividualRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.ArrayList;
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
public class NonExistentEntityValidator implements Validator<IndividualRequest> {

    private final ObjectMapper objectMapper;

    private final IndividualRepository individualRepository;

    @Autowired
    public NonExistentEntityValidator(ObjectMapper objectMapper,
                                      IndividualRepository individualRepository) {
        this.objectMapper = objectMapper;
        this.individualRepository = individualRepository;
    }


    @Override
    public List<ErrorDetails> validate(IndividualRequest request) {
        List<ErrorDetails> errorDetailsList = new ArrayList<>();
        Method idMethod = getIdMethod(request.getIndividual());
        Map<String, Individual> iMap = getIdToObjMap(request.getIndividual()
                .stream().filter(notHavingErrors()).collect(Collectors.toList()), idMethod);
        List<String> individualIds = new ArrayList<>(iMap.keySet());
        List<Individual> existingIndividuals = individualRepository.findById(individualIds,
                getIdFieldName(idMethod), false);
        List<Individual> nonExistentIndividuals = checkNonExistentEntities(iMap,
                existingIndividuals, idMethod);
        nonExistentIndividuals.forEach(individual -> populateErrorDetails(individual,
                "NON_EXISTENT_INDIVIDUAL",
                "Individual does not exist in db", request, errorDetailsList, objectMapper));
        return errorDetailsList;
    }
}
