package org.egov.individual.service;

import org.egov.common.models.Error;
import org.egov.common.utils.Validator;
import org.egov.individual.web.models.Address;
import org.egov.individual.web.models.Identifier;
import org.egov.individual.web.models.Individual;
import org.egov.individual.web.models.IndividualBulkRequest;
import org.egov.tracer.model.CustomException;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@Order(2)
public class IsDeletedSubEntityValidator  implements Validator<IndividualBulkRequest, Individual> {

    private static final Error.ErrorType ERROR_TYPE = Error.ErrorType.RECOVERABLE;

    @Override
    public Map<Individual, List<Error>> validate(IndividualBulkRequest request) {
        HashMap<Individual, List<Error>> errorDetailsMap = new HashMap<>();
        List<Individual> validIndividuals = request.getIndividuals();
        for (Individual individual : validIndividuals) {
            individual.getIdentifiers().stream().filter(Identifier::getIsDeleted)
                    .forEach(identifier -> {
                        Error error = Error.builder()
                                .errorMessage("isDeleted cannot be true for identifier "
                                        + identifier.getIdentifierId())
                                .errorCode("IS_DELETED_TRUE")
                                .type(ERROR_TYPE)
                                .exception(new CustomException("IS_DELETED_TRUE",
                                        "isDeleted cannot be true for identifier "
                                                + identifier.getIdentifierId()))
                                .build();
                        populateErrorDetails(individual, error, errorDetailsMap);
                    });
            individual.getAddress().stream().filter(Address::getIsDeleted)
                    .forEach(address -> {
                        Error error = Error.builder()
                                .errorMessage("isDeleted cannot be true for address "
                                        + address.getId())
                                .errorCode("IS_DELETED_TRUE")
                                .type(ERROR_TYPE)
                                .exception(new CustomException("IS_DELETED_TRUE",
                                        "isDeleted cannot be true for address "
                                                + address.getId()))
                                .build();
                        populateErrorDetails(individual, error, errorDetailsMap);
                    });
        }
        return errorDetailsMap;
    }
}
