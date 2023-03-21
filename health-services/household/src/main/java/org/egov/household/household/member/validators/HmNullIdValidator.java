package org.egov.household.household.member.validators;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.Error;
import org.egov.common.models.household.HouseholdMember;
import org.egov.common.models.household.HouseholdMemberBulkRequest;
import org.egov.common.validator.Validator;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

import static org.egov.common.utils.CommonUtils.validateForNullId;
import static org.egov.household.Constants.GET_HOUSEHOLD_MEMBERS;

@Component
@Order(value = 1)
@Slf4j
public class HmNullIdValidator implements Validator<HouseholdMemberBulkRequest, HouseholdMember> {

    @Override
    public Map<HouseholdMember, List<Error>> validate(HouseholdMemberBulkRequest request) {
        return validateForNullId(request, GET_HOUSEHOLD_MEMBERS);
    }
}
