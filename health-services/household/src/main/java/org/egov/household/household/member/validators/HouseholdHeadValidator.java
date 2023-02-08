package org.egov.household.household.member.validators;

import org.egov.common.models.Error;
import org.egov.common.validator.Validator;
import org.egov.household.repository.HouseholdMemberRepository;
import org.egov.household.service.HouseholdMemberEnrichmentService;
import org.egov.household.service.HouseholdService;
import org.egov.household.web.models.HouseholdMember;
import org.egov.household.web.models.HouseholdMemberBulkRequest;
import org.egov.tracer.model.CustomException;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.egov.common.utils.CommonUtils.notHavingErrors;
import static org.egov.common.utils.CommonUtils.populateErrorDetails;
import static org.egov.household.Constants.HOUSEHOLD_ALREADY_HAS_HEAD;
import static org.egov.household.Constants.HOUSEHOLD_ALREADY_HAS_HEAD_MESSAGE;
import static org.egov.household.Constants.INDIVIDUAL_ALREADY_MEMBER_OF_HOUSEHOLD;
import static org.egov.household.Constants.INDIVIDUAL_ALREADY_MEMBER_OF_HOUSEHOLD_MESSAGE;

@Component
@Order(8)
public class HouseholdHeadValidator implements Validator<HouseholdMemberBulkRequest, HouseholdMember> {

    private final HouseholdMemberRepository householdMemberRepository;

    private final HouseholdService householdService;

    private final HouseholdMemberEnrichmentService householdMemberEnrichmentService;

    public HouseholdHeadValidator(HouseholdMemberRepository householdMemberRepository,
                                  HouseholdService householdService,
                                  HouseholdMemberEnrichmentService householdMemberEnrichmentService) {
        this.householdMemberRepository = householdMemberRepository;
        this.householdService = householdService;
        this.householdMemberEnrichmentService = householdMemberEnrichmentService;
    }

    @Override
    public Map<HouseholdMember, List<Error>> validate(HouseholdMemberBulkRequest householdMemberBulkRequest) {
        HashMap<HouseholdMember, List<Error>> errorDetailsMap = new HashMap<>();

        List<HouseholdMember> householdMembers = householdMemberBulkRequest.getHouseholdMembers().stream()
                .filter(notHavingErrors()).collect(Collectors.toList());
        if(!householdMembers.isEmpty()){
            householdMemberEnrichmentService.enrichHousehold(householdMembers);

            householdMembers.forEach(householdMember -> {
                validateHeadOfHousehold(householdMember, errorDetailsMap);
            });
        }

        return errorDetailsMap;
    }

    private void validateHeadOfHousehold(HouseholdMember householdMember, HashMap<HouseholdMember, List<Error>> errorDetailsMap) {

        if(householdMember.getIsHeadOfHousehold()){
            List<HouseholdMember> householdMembersHeadCheck = householdMemberRepository
                    .findIndividualByHousehold(householdMember.getHouseholdId()).stream().filter(
                            HouseholdMember::getIsHeadOfHousehold)
                    .collect(Collectors.toList());

            if(!householdMembersHeadCheck.isEmpty()){
                Error error = Error.builder().errorMessage(HOUSEHOLD_ALREADY_HAS_HEAD_MESSAGE)
                        .errorCode(HOUSEHOLD_ALREADY_HAS_HEAD)
                        .type(Error.ErrorType.NON_RECOVERABLE)
                        .exception(new CustomException(HOUSEHOLD_ALREADY_HAS_HEAD,
                                HOUSEHOLD_ALREADY_HAS_HEAD_MESSAGE))
                        .build();
                populateErrorDetails(householdMember, error, errorDetailsMap);
            }
        }
    }
}
