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
        householdMemberEnrichmentService.enrichHousehold(householdMembers);

        householdMembers.forEach(householdMember -> {
            validateHeadOfHousehold(householdMember);
        });

        return errorDetailsMap;
    }

    private void validateHeadOfHousehold(HouseholdMember householdMember) {
        if(householdMember.getIsHeadOfHousehold()){
            List<HouseholdMember> householdMembersHeadCheck = householdMemberRepository
                    .findIndividualByHousehold(householdMember.getHouseholdId()).stream().filter(
                            HouseholdMember::getIsHeadOfHousehold)
                    .collect(Collectors.toList());

            if(!householdMembersHeadCheck.isEmpty()){
                throw new CustomException("HOUSEHOLD_ALREADY_HAS_HEAD", householdMember.getIndividualId());
            }
        }
    }
}
