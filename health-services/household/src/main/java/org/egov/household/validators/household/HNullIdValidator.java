package org.egov.household.validators.household;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.Error;
import org.egov.common.models.household.Household;
import org.egov.common.models.household.HouseholdBulkRequest;
import org.egov.common.validator.Validator;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

import static org.egov.common.utils.CommonUtils.validateForNullId;
import static org.egov.household.Constants.GET_HOUSEHOLDS;

@Component
@Order(value = 1)
@Slf4j
public class HNullIdValidator implements Validator<HouseholdBulkRequest, Household> {
    @Override
    public Map<Household, List<Error>> validate(HouseholdBulkRequest request) {
        return validateForNullId(request, GET_HOUSEHOLDS);
    }
}
