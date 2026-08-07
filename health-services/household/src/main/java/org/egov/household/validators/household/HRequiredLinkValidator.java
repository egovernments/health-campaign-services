package org.egov.household.validators.household;

import org.apache.commons.lang3.StringUtils;
import org.egov.common.models.Error;
import org.egov.common.models.household.Household;
import org.egov.common.models.household.HouseholdBulkRequest;
import org.egov.common.validator.Validator;
import org.egov.tracer.model.CustomException;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.egov.common.utils.CommonUtils.populateErrorDetails;

/** Ensures a newly created offline household remains addressable before a server ID exists. */
@Component
@Order(0)
public class HRequiredLinkValidator implements Validator<HouseholdBulkRequest, Household> {

    private static final String ERROR_CODE = "REQUIRED_LINK_MISSING";

    @Override
    public Map<Household, List<Error>> validate(HouseholdBulkRequest request) {
        Map<Household, List<Error>> errors = new HashMap<>();
        for (Household household : request.getHouseholds()) {
            if (StringUtils.isBlank(household.getClientReferenceId())) {
                String message = "Required link field missing: clientReferenceId";
                Error error = Error.builder()
                        .errorCode(ERROR_CODE)
                        .errorMessage(message)
                        .type(Error.ErrorType.NON_RECOVERABLE)
                        .exception(new CustomException(ERROR_CODE, message))
                        .build();
                populateErrorDetails(household, error, errors);
            }
        }
        return errors;
    }
}
