package org.egov.individual.service;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.Error;
import org.egov.common.utils.Validator;
import org.egov.individual.web.models.Individual;
import org.egov.individual.web.models.IndividualBulkRequest;
import org.egov.tracer.model.CustomException;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.egov.common.utils.CommonUtils.getMethod;
import static org.egov.common.utils.CommonUtils.getObjClass;
import static org.egov.common.utils.CommonUtils.identifyObjectsWithNullIds;
import static org.egov.common.utils.CommonUtils.notHavingErrors;

@Component
@Order(value = 1)
@Slf4j
public class NullIdValidator implements Validator<IndividualBulkRequest, Individual> {

    private static final Error.ErrorType ERROR_TYPE = Error.ErrorType.RECOVERABLE;

    @Override
    public Map<Individual, List<Error>> validate(IndividualBulkRequest request) {
        HashMap<Individual, List<Error>> errorDetailsMap = new HashMap<>();
        List<Individual> validIndividuals = request.getIndividuals()
                .stream().filter(notHavingErrors()).collect(Collectors.toList());
        if (!validIndividuals.isEmpty()) {
            Class<?> objClass = getObjClass(validIndividuals);
            Method idMethod = getMethod("getId", objClass);
            List<Individual> indWithNullIds = identifyObjectsWithNullIds(validIndividuals, idMethod);
            indWithNullIds.forEach(individual -> {
                Error error = Error.builder().errorMessage("Id cannot be null").errorCode("NULL_ID")
                        .type(ERROR_TYPE)
                        .exception(new CustomException("NULL_ID", "Id cannot be null")).build();
                populateErrorDetails(individual, error, errorDetailsMap);
            });
        }
        return errorDetailsMap;
    }
}
