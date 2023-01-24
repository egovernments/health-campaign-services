package org.egov.individual.service;

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

import static org.egov.common.utils.CommonUtils.notHavingErrors;

@Component
@Order(2)
public class IsDeletedValidator implements Validator<IndividualBulkRequest, Individual> {

    private static final Error.ErrorType ERROR_TYPE = Error.ErrorType.RECOVERABLE;

    @Override
    public Map<Individual, List<Error>> validate(IndividualBulkRequest request) {
        HashMap<Individual, List<Error>> errorDetailsMap = new HashMap<>();
        List<Individual> validIndividuals = request.getIndividuals()
                .stream().filter(notHavingErrors()).collect(Collectors.toList());
        validIndividuals.stream().filter(Individual::getIsDeleted).forEach(individual -> {
            Error error = Error.builder().errorMessage("isDeleted cannot be true")
                    .errorCode("IS_DELETED_TRUE")
                    .type(ERROR_TYPE)
                    .exception(new CustomException("IS_DELETED_TRUE", "isDeleted cannot be true"))
                    .build();
            populateErrorDetails(individual, error, errorDetailsMap);
        });
        return errorDetailsMap;
    }
}
