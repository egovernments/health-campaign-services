package org.egov.household.household.member.validators;

import org.egov.common.models.Error;
import org.egov.common.validator.Validator;
import org.egov.household.web.models.HouseholdMember;
import org.egov.household.web.models.HouseholdMemberBulkRequest;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.egov.common.utils.CommonUtils.populateErrorDetails;
import static org.egov.common.utils.ValidatorUtils.getErrorForIsDelete;

@Component
@Order(2)
public class HmIsDeletedValidator implements Validator<HouseholdMemberBulkRequest, HouseholdMember> {

    @Override
    public Map<HouseholdMember, List<Error>> validate(HouseholdMemberBulkRequest request) {
        HashMap<HouseholdMember, List<Error>> errorDetailsMap = new HashMap<>();
        List<HouseholdMember> householdMembers = request.getHouseholdMembers();
        householdMembers.stream().filter(HouseholdMember::getIsDeleted).forEach(householdMember -> {
            Error error = getErrorForIsDelete();
            populateErrorDetails(householdMember, error, errorDetailsMap);
        });
        return errorDetailsMap;
    }
}
