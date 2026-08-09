package org.egov.household.validators.household;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.Error;
import org.egov.common.models.household.Household;
import org.egov.common.models.household.HouseholdBulkRequest;
import org.egov.common.validator.Validator;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.egov.common.utils.CommonUtils.populateErrorDetails;
import static org.egov.common.utils.ValidatorUtils.getErrorForAddressType;

/**
 * Guards against a null Address.type reaching the persister, mirroring individual's
 * AddressTypeValidator for household.
 * <p>
 * Note that {@code PayloadGuardrail} already runs bean validation per record ahead of this
 * validator, so for requests arriving through the controller or the bulk consumer the
 * {@code @NotNull} on Address.type is enforced before this runs. This layer still matters for a
 * payload that reaches the consumer without passing either — DLQ/park replay, or a direct produce
 * onto the topic — and it reports the condition as a domain error rather than a raw
 * field-validation failure.
 */
@Component
@Order(value = 2)
@Slf4j
public class HAddressTypeValidator implements Validator<HouseholdBulkRequest, Household> {

    @Override
    public Map<Household, List<Error>> validate(HouseholdBulkRequest request) {
        log.info("validating address type");
        Map<Household, List<Error>> errorDetailsMap = new HashMap<>();
        List<Household> households = request.getHouseholds();
        if (!households.isEmpty()) {
            households.stream()
                    .filter(household -> household.getAddress() != null
                            && household.getAddress().getType() == null)
                    .forEach(household -> {
                        Error error = getErrorForAddressType();
                        populateErrorDetails(household, error, errorDetailsMap);
                    });
        }
        return errorDetailsMap;
    }
}
