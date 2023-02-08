package org.egov.household.household.member.validators;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.models.Error;
import org.egov.common.validator.Validator;
import org.egov.household.service.IndividualService;
import org.egov.household.web.models.HouseholdMember;
import org.egov.household.web.models.HouseholdMemberBulkRequest;
import org.egov.household.web.models.IndividualBulkResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.egov.common.utils.CommonUtils.getTenantId;
import static org.egov.common.utils.CommonUtils.notHavingErrors;

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
