package org.egov.household.utils;

import org.egov.common.models.Error;
import org.egov.common.models.household.HouseholdMember;
import org.egov.tracer.model.CustomException;

import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

import static org.egov.common.utils.CommonUtils.notHavingErrors;
import static org.egov.common.utils.CommonUtils.populateErrorDetails;
import static org.egov.household.Constants.INDIVIDUAL_CANNOT_BE_NULL;
import static org.egov.household.Constants.INDIVIDUAL_CANNOT_BE_NULL_MESSAGE;

public class ValidatorUtil {

    public static List<HouseholdMember> getHouseholdMembersWithNonNullIndividuals(HashMap<HouseholdMember, List<Error>> errorDetailsMap, List<HouseholdMember> validHouseholdMembers) {
        List<HouseholdMember> invalidHouseholdMembers =  validHouseholdMembers.stream()
                .filter(householdMember ->
                        householdMember.getIndividualId()==null && householdMember.getIndividualClientReferenceId() ==null
                ).collect(Collectors.toList());

        invalidHouseholdMembers.forEach(householdMember -> {
            Error error = Error.builder().errorMessage(INDIVIDUAL_CANNOT_BE_NULL_MESSAGE)
                    .errorCode(INDIVIDUAL_CANNOT_BE_NULL)
                    .type(Error.ErrorType.NON_RECOVERABLE)
                    .exception(new CustomException(INDIVIDUAL_CANNOT_BE_NULL,
                            INDIVIDUAL_CANNOT_BE_NULL_MESSAGE))
                    .build();
            populateErrorDetails(householdMember, error, errorDetailsMap);
        });

        validHouseholdMembers = validHouseholdMembers.stream()
                .filter(notHavingErrors()).collect(Collectors.toList());
        return validHouseholdMembers;
    }
}
