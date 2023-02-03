package org.egov.household.household.member.validators;

import org.egov.common.models.Error;
import org.egov.common.utils.CommonUtils;
import org.egov.common.validator.Validator;
import org.egov.household.service.HouseholdService;
import org.egov.household.web.models.Household;
import org.egov.household.web.models.HouseholdMember;
import org.egov.household.web.models.HouseholdMemberBulkRequest;
import org.egov.tracer.model.CustomException;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.egov.common.utils.CommonUtils.getIdList;
import static org.egov.common.utils.CommonUtils.getIdMethod;
import static org.egov.common.utils.CommonUtils.getSet;
import static org.egov.common.utils.CommonUtils.populateErrorDetails;
import static org.egov.household.Constants.INVALID_HOUSEHOLD;
import static org.egov.household.Constants.INVALID_HOUSEHOLD_MESSAGE;

@Component
@Order(6)
public class HouseholdValidator implements Validator<HouseholdMemberBulkRequest, HouseholdMember> {

    private final HouseholdService householdService;

    public HouseholdValidator(HouseholdService householdService) {
        this.householdService = householdService;
    }

    @Override
    public Map<HouseholdMember, List<Error>> validate(HouseholdMemberBulkRequest householdMemberBulkRequest) {
        HashMap<HouseholdMember, List<Error>> errorDetailsMap = new HashMap<>();
        List<HouseholdMember> householdMembers = householdMemberBulkRequest.getHouseholdMembers();

        Method idMethod = getIdMethod(householdMembers, "householdId",
                "householdClientReferenceId");
        String columnName = getColumnName(idMethod);

        List<String> houseHoldIds = getIdList(householdMembers, idMethod);
        List<Household> validHouseHoldIds = householdService.findById(houseHoldIds, columnName, false);
        Set<String> uniqueHoldIds = getSet(validHouseHoldIds, columnName == "id" ? "getId": "getClientReferenceId");

        List<String> invalidHouseholds = CommonUtils.getDifference(
                houseHoldIds,
                new ArrayList<>(uniqueHoldIds)
        );

        householdMembers.stream()
                .filter(householdMember -> invalidHouseholds.contains(householdMember.getHouseholdId()))
                .forEach(householdMember -> {
                    Error error = Error.builder().errorMessage(INVALID_HOUSEHOLD_MESSAGE)
                            .errorCode(INVALID_HOUSEHOLD)
                            .type(Error.ErrorType.NON_RECOVERABLE)
                            .exception(new CustomException(INVALID_HOUSEHOLD, INVALID_HOUSEHOLD_MESSAGE))
                            .build();
                    populateErrorDetails(householdMember, error, errorDetailsMap);
                });

        return errorDetailsMap;
    }

    private String getColumnName(Method idMethod) {
        String columnName = "id";
        if ("getHouseholdClientReferenceId".equals(idMethod.getName())) {
            columnName = "clientReferenceId";
        }
        return columnName;
    }
}
