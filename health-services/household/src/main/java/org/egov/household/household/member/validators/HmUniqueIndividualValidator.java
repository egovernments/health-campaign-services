package org.egov.household.household.member.validators;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.models.Error;
import org.egov.common.models.household.HouseholdMember;
import org.egov.common.models.household.HouseholdMemberBulkRequest;
import org.egov.common.models.individual.Individual;
import org.egov.common.models.individual.IndividualBulkResponse;
import org.egov.common.validator.Validator;
import org.egov.household.repository.HouseholdMemberRepository;
import org.egov.household.service.IndividualService;
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
import static org.egov.household.utils.ValidatorUtil.getHouseholdMembersWithNonNullIndividuals;

@Component
@Order(8)
@Slf4j
public class HmUniqueIndividualValidator implements Validator<HouseholdMemberBulkRequest, HouseholdMember> {
    private final HouseholdMemberRepository householdMemberRepository;

    private final IndividualService individualService;

    public HmUniqueIndividualValidator(HouseholdMemberRepository householdMemberRepository,
                                       IndividualService individualService) {
        this.householdMemberRepository = householdMemberRepository;
        this.individualService = individualService;
    }

    @Override
    public Map<HouseholdMember, List<Error>> validate(HouseholdMemberBulkRequest householdMemberBulkRequest) {
        HashMap<HouseholdMember, List<Error>> errorDetailsMap = new HashMap<>();
        log.info("validating unique individual for household member");

        List<HouseholdMember> validHouseholdMembers = householdMemberBulkRequest.getHouseholdMembers().stream()
                .filter(notHavingErrors()).collect(Collectors.toList());

        log.info("checking non null individuals for household member");
        validHouseholdMembers = getHouseholdMembersWithNonNullIndividuals(errorDetailsMap, validHouseholdMembers);

        if(!validHouseholdMembers.isEmpty()){
            RequestInfo requestInfo = householdMemberBulkRequest.getRequestInfo();
            String tenantId = getTenantId(validHouseholdMembers);
            log.info("searching individuals for household member");
            IndividualBulkResponse searchResponse = individualService.searchIndividualBeneficiary(
                    validHouseholdMembers,
                    requestInfo,
                    tenantId
            );
            validHouseholdMembers.forEach(householdMember -> {
                Individual individual = individualService.validateIndividual(householdMember,
                        searchResponse, errorDetailsMap);
                if (individual != null) {
                    householdMember.setIndividualId(individual.getId());
                    householdMember.setIndividualClientReferenceId(individual.getClientReferenceId());

                    log.info("finding individuals mappings in household member");
                    List<HouseholdMember> individualSearchResult = householdMemberRepository
                            .findIndividual(individual.getId());
                    if(!individualSearchResult.isEmpty()) {
                        Error error = Error.builder().errorMessage(INDIVIDUAL_ALREADY_MEMBER_OF_HOUSEHOLD_MESSAGE)
                                .errorCode(INDIVIDUAL_ALREADY_MEMBER_OF_HOUSEHOLD)
                                .type(Error.ErrorType.NON_RECOVERABLE)
                                .exception(new CustomException(INDIVIDUAL_ALREADY_MEMBER_OF_HOUSEHOLD,
                                        INDIVIDUAL_ALREADY_MEMBER_OF_HOUSEHOLD_MESSAGE))
                                .build();
                        log.info("found error in individual mapping {}", error);
                        populateErrorDetails(householdMember, error, errorDetailsMap);
                    }
                }
            });
        }
        log.info("household member unique individual validation completed successfully, total errors: " + errorDetailsMap.size());

        return errorDetailsMap;
    }
}
