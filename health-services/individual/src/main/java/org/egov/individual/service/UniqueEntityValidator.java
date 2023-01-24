package org.egov.individual.service;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.Error;
import org.egov.common.utils.Validator;
import org.egov.individual.web.models.Individual;
import org.egov.individual.web.models.IndividualBulkRequest;
import org.egov.tracer.model.CustomException;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.egov.common.utils.CommonUtils.getIdToObjMap;
import static org.egov.common.utils.CommonUtils.notHavingErrors;

@Component
@Order(value = 2)
@Slf4j
public class UniqueEntityValidator implements Validator<IndividualBulkRequest, Individual> {

    private static final Error.ErrorType ERROR_TYPE = Error.ErrorType.NON_RECOVERABLE;

    @Override
    public Map<Individual, List<Error>> validate(IndividualBulkRequest individualBulkRequest) {
        Map<Individual, List<Error>> errorDetailsMap = new HashMap<>();
        List<Individual> validIndividuals = individualBulkRequest.getIndividuals()
                        .stream().filter(notHavingErrors()).collect(Collectors.toList());
        if (!validIndividuals.isEmpty()) {
            Map<String, Individual> iMap = getIdToObjMap(validIndividuals);
            if (iMap.keySet().size() != validIndividuals.size()) {
                List<String> duplicates = iMap.keySet().stream().filter(id ->
                        validIndividuals.stream()
                                .filter(individual -> individual.getId().equals(id)).count() > 1
                ).collect(Collectors.toList());
                for (String key : duplicates) {
                    Error error = Error.builder().errorMessage("Duplicate individual")
                            .errorCode("DUPLICATE_INDIVIDUAL")
                            .type(ERROR_TYPE)
                            .exception(new CustomException("DUPLICATE_INDIVIDUAL", "Duplicate individual")).build();
                    populateErrorDetails(iMap.get(key), error, errorDetailsMap);
                }
            }
        }
        return errorDetailsMap;
    }
}
