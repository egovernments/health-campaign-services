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
import static org.egov.common.utils.ValidatorUtils.getErrorForIsDelete;

@Component
@Order(value = 4)
@Slf4j
public class HIsDeletedValidator implements Validator<HouseholdBulkRequest, Household> {
    @Override
    public Map<Household, List<Error>> validate(HouseholdBulkRequest request) {
        HashMap<Household, List<Error>> errorDetailsMap = new HashMap<>();
        List<Household> validEntities = request.getHouseholds();
        validEntities.stream().filter(Household::getIsDeleted).forEach(individual -> {
            Error error = getErrorForIsDelete();
            populateErrorDetails(individual, error, errorDetailsMap);
        });
        return errorDetailsMap;
    }
}
