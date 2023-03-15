package org.egov.household.household.member.validators;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.models.Error;
import org.egov.common.models.household.HouseholdMember;
import org.egov.common.models.household.HouseholdMemberBulkRequest;
import org.egov.common.models.individual.IndividualBulkResponse;
import org.egov.common.validator.Validator;
import org.egov.household.service.IndividualService;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.egov.common.utils.CommonUtils.getTenantId;
import static org.egov.common.utils.CommonUtils.notHavingErrors;
import static org.egov.household.utils.ValidatorUtil.getHouseholdMembersWithNonNullIndividuals;

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

        log.info("starting validation of household members, total household members: " + validHouseholdMembers.size());
        validHouseholdMembers = getHouseholdMembersWithNonNullIndividuals(errorDetailsMap, validHouseholdMembers);

        if(!validHouseholdMembers.isEmpty()){
            RequestInfo requestInfo = householdMemberBulkRequest.getRequestInfo();
            String tenantId = getTenantId(validHouseholdMembers);

            IndividualBulkResponse searchResponse = individualService.searchIndividualBeneficiary(
                    validHouseholdMembers,
                    requestInfo,
                    tenantId
            );
            log.info("individuals searched successfully, total count: " + searchResponse.getIndividual().size());
            validHouseholdMembers.forEach(householdMember -> {
                individualService.validateIndividual(householdMember,
                        searchResponse, errorDetailsMap);
            });
        }
        log.info("household member validation completed successfully, total errors: " + errorDetailsMap.size());
        return errorDetailsMap;
    }
}
