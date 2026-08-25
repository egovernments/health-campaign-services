package org.egov.household.validators.household;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.Error;
import org.egov.common.models.household.Household;
import org.egov.common.models.household.HouseholdBulkRequest;
import org.egov.common.validator.Validator;
import org.springframework.beans.factory.annotation.Value;
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
 * <p>
 * Off by default: household had no such check before, so a null Address.type reached the persister
 * and persisted. Enabling it with the image would start rejecting households the pipeline accepted
 * yesterday. Report-only until the logged count is known to be zero for an environment.
 */
@Component
@Order(value = 2)
@Slf4j
public class HAddressTypeValidator implements Validator<HouseholdBulkRequest, Household> {

    @Value("${hcm.validator.household.address-type.enabled:false}")
    private boolean enabled;

    @Override
    public Map<Household, List<Error>> validate(HouseholdBulkRequest request) {
        log.info("validating address type");
        Map<Household, List<Error>> errorDetailsMap = new HashMap<>();
        List<Household> households = request.getHouseholds();
        if (!households.isEmpty()) {
            List<Household> offending = households.stream()
                    .filter(household -> household.getAddress() != null
                            && household.getAddress().getType() == null)
                    .toList();
            if (!enabled) {
                if (!offending.isEmpty()) {
                    log.warn("HAddressTypeValidator disabled: {} of {} household(s) have a null "
                                    + "address type and would be rejected if "
                                    + "hcm.validator.household.address-type.enabled=true",
                            offending.size(), households.size());
                }
                return errorDetailsMap;
            }
            offending.forEach(household -> {
                Error error = getErrorForAddressType();
                populateErrorDetails(household, error, errorDetailsMap);
            });
        }
        return errorDetailsMap;
    }
}
