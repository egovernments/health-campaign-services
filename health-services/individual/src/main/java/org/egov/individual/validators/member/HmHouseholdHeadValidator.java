package org.egov.individual.validators.member;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.exception.InvalidTenantIdException;
import org.egov.common.models.Error;
import org.egov.common.models.household.HouseholdMember;
import org.egov.common.models.household.HouseholdMemberBulkRequest;
import org.egov.common.validator.Validator;
import org.egov.individual.repository.HouseholdMemberRepository;
import org.egov.individual.service.HouseholdMemberEnrichmentService;
import org.egov.individual.service.HouseholdService;
import org.egov.tracer.model.CustomException;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.egov.common.utils.CommonUtils.getIdMethod;
import static org.egov.common.utils.CommonUtils.notHavingErrors;
import static org.egov.common.utils.CommonUtils.populateErrorDetails;
import static org.egov.common.utils.ValidatorUtils.getErrorForInvalidTenantId;
import static org.egov.individual.Constants.HOUSEHOLD_ALREADY_HAS_HEAD;
import static org.egov.individual.Constants.HOUSEHOLD_ALREADY_HAS_HEAD_MESSAGE;
import static org.egov.individual.Constants.HOUSEHOLD_CLIENT_REFERENCE_ID_FIELD;
import static org.egov.individual.Constants.HOUSEHOLD_ID_FIELD;
import static org.egov.individual.util.CommonUtils.getHouseholdIdFieldForHouseholdIdMethod;

@Component
@Order(9)
@Slf4j
public class HmHouseholdHeadValidator implements Validator<HouseholdMemberBulkRequest, HouseholdMember> {

    private final HouseholdMemberRepository householdMemberRepository;

    private final HouseholdService householdService;

    private final HouseholdMemberEnrichmentService householdMemberEnrichmentService;

    public HmHouseholdHeadValidator(HouseholdMemberRepository householdMemberRepository,
                                    HouseholdService householdService,
                                    HouseholdMemberEnrichmentService householdMemberEnrichmentService) {
        this.householdMemberRepository = householdMemberRepository;
        this.householdService = householdService;
        this.householdMemberEnrichmentService = householdMemberEnrichmentService;
    }

    @Override
    public Map<HouseholdMember, List<Error>> validate(HouseholdMemberBulkRequest householdMemberBulkRequest) {
        HashMap<HouseholdMember, List<Error>> errorDetailsMap = new HashMap<>();
        log.debug("validating head of household member");
        List<HouseholdMember> householdMembers = householdMemberBulkRequest.getHouseholdMembers().stream()
                .filter(notHavingErrors()).collect(Collectors.toList());
        if(!householdMembers.isEmpty()){
            Method idMethod = getIdMethod(householdMembers, HOUSEHOLD_ID_FIELD, HOUSEHOLD_CLIENT_REFERENCE_ID_FIELD);
            String columnName = getHouseholdIdFieldForHouseholdIdMethod(idMethod);
            householdMembers.forEach(householdMember -> {
                validateHeadOfHousehold(householdMember, idMethod, columnName, errorDetailsMap);
            });
        }
        log.debug("household member Head validation completed successfully, total errors: " + errorDetailsMap.size());
        return errorDetailsMap;
    }

    private void validateHeadOfHousehold(HouseholdMember householdMember, Method idMethod, String columnName,
                                         HashMap<HouseholdMember, List<Error>> errorDetailsMap) {

        if(householdMember.getIsHeadOfHousehold()){

            // fetch the tenantId from the householdMember
            String tenantId = householdMember.getTenantId();
            log.info("validating if household already has a head");
            // this catches the error if the tenatId is not valid
            try {
                List<HouseholdMember> householdMembersHeadCheck = householdMembersHeadCheck = householdMemberRepository.findIndividualByHousehold( tenantId, (String) ReflectionUtils.invokeMethod(idMethod, householdMember),
                                columnName).getResponse().stream().filter(HouseholdMember::getIsHeadOfHousehold)
                        .collect(Collectors.toList());


                if(!householdMembersHeadCheck.isEmpty()){
                    Error error = Error.builder().errorMessage(HOUSEHOLD_ALREADY_HAS_HEAD_MESSAGE)
                            .errorCode(HOUSEHOLD_ALREADY_HAS_HEAD)
                            .type(Error.ErrorType.NON_RECOVERABLE)
                            .exception(new CustomException(HOUSEHOLD_ALREADY_HAS_HEAD,
                                    HOUSEHOLD_ALREADY_HAS_HEAD_MESSAGE))
                            .build();
                    log.info("household already has a head, error: {}", error);
                    populateErrorDetails(householdMember, error, errorDetailsMap);
                }
            } catch (InvalidTenantIdException exception) {
                Error error = getErrorForInvalidTenantId(tenantId, exception);
                populateErrorDetails(householdMember, error, errorDetailsMap);
            }
        }
    }
}
