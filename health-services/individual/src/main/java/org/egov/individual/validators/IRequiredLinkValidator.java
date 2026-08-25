package org.egov.individual.validators;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.egov.common.models.Error;
import org.egov.common.models.individual.Individual;
import org.egov.common.models.individual.IndividualBulkRequest;
import org.egov.common.validator.Validator;
import org.egov.tracer.model.CustomException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.egov.common.utils.CommonUtils.populateErrorDetails;

/**
 * Ensures a newly created offline individual remains addressable before a server ID exists.
 *
 * <p>Off by default: this rejects a shape the API accepted before, so enabling it with the image
 * would fail field payloads that used to persist. Report-only until the logged count is known to
 * be zero for a given environment, then set the flag true there.</p>
 */
@Component
@Order(0)
@Slf4j
public class IRequiredLinkValidator implements Validator<IndividualBulkRequest, Individual> {

    private static final String ERROR_CODE = "REQUIRED_LINK_MISSING";

    @Value("${hcm.validator.individual.required-link.enabled:false}")
    private boolean enabled;

    @Override
    public Map<Individual, List<Error>> validate(IndividualBulkRequest request) {
        Map<Individual, List<Error>> errors = new HashMap<>();
        int wouldReject = 0;
        for (Individual individual : request.getIndividuals()) {
            if (StringUtils.isBlank(individual.getClientReferenceId())) {
                if (!enabled) {
                    wouldReject++;
                    continue;
                }
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
        if (wouldReject > 0) {
            log.warn("IRequiredLinkValidator disabled: {} of {} individual(s) have a blank "
                            + "clientReferenceId and would be rejected if "
                            + "hcm.validator.individual.required-link.enabled=true",
                    wouldReject, request.getIndividuals().size());
        }
        return errors;
    }
}
