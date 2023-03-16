package org.egov.household.household.member.validators;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.Error;
import org.egov.common.models.household.HouseholdMember;
import org.egov.common.models.household.HouseholdMemberBulkRequest;
import org.egov.common.validator.Validator;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.egov.common.utils.CommonUtils.populateErrorDetails;
import static org.egov.common.utils.ValidatorUtils.getErrorForIsDelete;

@Slf4j
@Component
@Order(2)
public class HmIsDeletedValidator implements Validator<HouseholdMemberBulkRequest, HouseholdMember> {

    @Override
    public Map<HouseholdMember, List<Error>> validate(HouseholdMemberBulkRequest request) {
        HashMap<HouseholdMember, List<Error>> errorDetailsMap = new HashMap<>();
        log.info("validating is deleted household member");
        List<HouseholdMember> householdMembers = request.getHouseholdMembers();
        householdMembers.stream().filter(HouseholdMember::getIsDeleted).forEach(householdMember -> {
            Error error = getErrorForIsDelete();
            log.info("validation failed for household member for is deleted: {} with error: {}", householdMember, error);
            populateErrorDetails(householdMember, error, errorDetailsMap);
        });
        log.info("household member is deleted validation completed successfully, total errors: " + errorDetailsMap.size());
        return errorDetailsMap;
    }
}
