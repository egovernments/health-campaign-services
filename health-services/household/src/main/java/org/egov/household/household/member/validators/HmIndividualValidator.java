package org.egov.household.household.member.validators;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.models.Error;
import org.egov.common.validator.Validator;
import org.egov.household.service.IndividualService;
import org.egov.household.web.models.HouseholdMember;
import org.egov.household.web.models.HouseholdMemberBulkRequest;
import org.egov.household.web.models.IndividualBulkResponse;
import org.egov.tracer.model.CustomException;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.egov.common.utils.CommonUtils.getTenantId;
import static org.egov.common.utils.CommonUtils.notHavingErrors;
import static org.egov.common.utils.CommonUtils.populateErrorDetails;
import static org.egov.household.Constants.INDIVIDUAL_ALREADY_MEMBER_OF_HOUSEHOLD;
import static org.egov.household.Constants.INDIVIDUAL_ALREADY_MEMBER_OF_HOUSEHOLD_MESSAGE;
import static org.egov.household.Constants.INDIVIDUAL_CANNOT_BE_NULL;
import static org.egov.household.Constants.INDIVIDUAL_CANNOT_BE_NULL_MESSAGE;

@Component
@Order(7)
@Slf4j
public class HmIndividualValidator implements Validator<HouseholdMemberBulkRequest, HouseholdMember> {
    private final IndividualService individualService;

    public HmIndividualValidator(IndividualService individualService) {
        this.individualService = individualService;
    }

    @Override
    public Map<HouseholdMember, List<Error>> validate(HouseholdMemberBulkRequest householdMemberBulkRequest) {
        HashMap<HouseholdMember, List<Error>> errorDetailsMap = new HashMap<>();

        List<HouseholdMember> validHouseholdMembers = householdMemberBulkRequest.getHouseholdMembers().stream()
                .filter(notHavingErrors()).collect(Collectors.toList());


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

        if(!validHouseholdMembers.isEmpty()){
            RequestInfo requestInfo = householdMemberBulkRequest.getRequestInfo();
            String tenantId = getTenantId(validHouseholdMembers);

            IndividualBulkResponse searchResponse = individualService.searchIndividualBeneficiary(
                    validHouseholdMembers,
                    requestInfo,
                    tenantId
            );
            validHouseholdMembers.forEach(householdMember -> {
                individualService.validateIndividual(householdMember,
                        searchResponse, errorDetailsMap);
            });
        }

        return errorDetailsMap;
    }
}
