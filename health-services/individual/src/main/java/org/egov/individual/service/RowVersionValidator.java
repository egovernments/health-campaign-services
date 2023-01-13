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

import static org.egov.common.utils.CommonUtils.getEntitiesWithMismatchedRowVersion;
import static org.egov.common.utils.CommonUtils.getIdFieldName;
import static org.egov.common.utils.CommonUtils.getIdMethod;
import static org.egov.common.utils.CommonUtils.getIdToObjMap;
import static org.egov.common.utils.CommonUtils.notHavingErrors;

@Component
@Order(value = 4)
public class RowVersionValidator implements Validator<IndividualRequest> {

    private final ObjectMapper objectMapper;

    private final IndividualRepository individualRepository;

    @Autowired
    public RowVersionValidator(ObjectMapper objectMapper,
                               IndividualRepository individualRepository) {
        this.objectMapper = objectMapper;
        this.individualRepository = individualRepository;
    }


    @Override
    public List<ErrorDetails> validate(IndividualRequest request) {
        List<ErrorDetails> errorDetailsList = new ArrayList<>();
        Method idMethod = getIdMethod(request.getIndividual());
        Map<String, Individual> iMap = getIdToObjMap(request.getIndividual().stream()
                .filter(notHavingErrors())
                .collect(Collectors.toList()), idMethod);
        if (!iMap.isEmpty()) {
            List<String> individualIds = new ArrayList<>(iMap.keySet());
            List<Individual> existingIndividuals = individualRepository.findById(individualIds,
                    getIdFieldName(idMethod), false);
            List<Individual> individualsWithMismatchedRowVersion =
                    getEntitiesWithMismatchedRowVersion(iMap, existingIndividuals, idMethod);
            individualsWithMismatchedRowVersion.forEach(individual ->
                    populateErrorDetails(individual, "MISMATCHED_ROW_VERSION",
                            "Row version mismatch", request, errorDetailsList, objectMapper));
        }
        return errorDetailsList;
    }
}
