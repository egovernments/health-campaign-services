package org.egov.individual.validators;

import org.apache.commons.lang3.StringUtils;
import org.egov.common.models.Error;
import org.egov.common.models.individual.Individual;
import org.egov.common.models.individual.IndividualBulkRequest;
import org.egov.common.validator.Validator;
import org.egov.tracer.model.CustomException;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.egov.common.utils.CommonUtils.populateErrorDetails;

/** Ensures a newly created offline individual remains addressable before a server ID exists. */
@Component
@Order(0)
public class IRequiredLinkValidator implements Validator<IndividualBulkRequest, Individual> {

    private static final String ERROR_CODE = "REQUIRED_LINK_MISSING";

    @Override
    public Map<Individual, List<Error>> validate(IndividualBulkRequest request) {
        Map<Individual, List<Error>> errors = new HashMap<>();
        for (Individual individual : request.getIndividuals()) {
            if (StringUtils.isBlank(individual.getClientReferenceId())) {
                String message = "Required link field missing: clientReferenceId";
                Error error = Error.builder()
                        .errorCode(ERROR_CODE)
                        .errorMessage(message)
                        .type(Error.ErrorType.NON_RECOVERABLE)
                        .exception(new CustomException(ERROR_CODE, message))
                        .build();
                populateErrorDetails(individual, error, errors);
            }
        }
        return errors;
    }
}
