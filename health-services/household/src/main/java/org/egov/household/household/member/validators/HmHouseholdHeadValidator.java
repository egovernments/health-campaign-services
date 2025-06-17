package org.egov.household.household.member.validators;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.Error;
import org.egov.common.models.household.HouseholdMember;
import org.egov.common.models.household.HouseholdMemberBulkRequest;
import org.egov.common.validator.Validator;
import org.egov.household.repository.HouseholdMemberRepository;
import org.egov.household.service.HouseholdMemberEnrichmentService;
import org.egov.household.service.HouseholdService;
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
import static org.egov.household.Constants.CLIENT_REFERENCE_ID_FIELD;
import static org.egov.household.Constants.HOUSEHOLD_ALREADY_HAS_HEAD;
import static org.egov.household.Constants.HOUSEHOLD_ALREADY_HAS_HEAD_MESSAGE;
import static org.egov.household.Constants.HOUSEHOLD_CLIENT_REFERENCE_ID_FIELD;
import static org.egov.household.Constants.HOUSEHOLD_ID_FIELD;
import static org.egov.household.Constants.ID_FIELD;
import static org.egov.household.utils.CommonUtils.getHouseholdColumnName;

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
            String columnName = getHouseholdColumnName(idMethod);
            householdMembers.forEach(householdMember -> {
                validateHeadOfHousehold(householdMember, idMethod, columnName, errorDetailsMap, householdMembers);
            });
        }
        log.debug("household member Head validation completed successfully, total errors: " + errorDetailsMap.size());
        return errorDetailsMap;
    }

    private void validateHeadOfHousehold(HouseholdMember householdMember, Method memberidMethod, String columnName,
                                         HashMap<HouseholdMember, List<Error>> errorDetailsMap, List<HouseholdMember> requestMembers) {

        if(householdMember.getIsHeadOfHousehold()) {
            log.info("validating if household already has a head");
            Method memberIdMethod = getIdMethod(requestMembers, ID_FIELD, CLIENT_REFERENCE_ID_FIELD);
            List<HouseholdMember> householdMembersHeadCheck = householdMemberRepository
                    .findIndividualByHousehold((String) ReflectionUtils.invokeMethod(memberidMethod, householdMember),
                            columnName).getResponse().stream().filter(HouseholdMember::getIsHeadOfHousehold)
                    .collect(Collectors.toList());

            boolean isSameAsExistingHead = householdMembersHeadCheck.stream()
                    .allMatch(existing -> {
                        Object existingValue = ReflectionUtils.invokeMethod(memberIdMethod, existing);
                        Object currentValue = ReflectionUtils.invokeMethod(memberIdMethod, householdMember);
                        return existingValue != null && existingValue.equals(currentValue);
                    });

            if(!householdMembersHeadCheck.isEmpty() && !isSameAsExistingHead) {
                HouseholdMember existinghead = householdMembersHeadCheck.get(0);
                Object existingValue = ReflectionUtils.invokeMethod(memberIdMethod, existinghead);
                Object currentValue = ReflectionUtils.invokeMethod(memberIdMethod, householdMember);
                boolean isReassigning = existingValue != null && currentValue != null
                        && !existingValue.equals(currentValue);

                if(!isReassigning) {
                    Error error = Error.builder().errorMessage(HOUSEHOLD_ALREADY_HAS_HEAD_MESSAGE)
                            .errorCode(HOUSEHOLD_ALREADY_HAS_HEAD)
                            .type(Error.ErrorType.NON_RECOVERABLE)
                            .exception(new CustomException(HOUSEHOLD_ALREADY_HAS_HEAD,
                                    HOUSEHOLD_ALREADY_HAS_HEAD_MESSAGE))
                            .build();
                    log.info("household already has a head, error: {}", error);
                    populateErrorDetails(householdMember, error, errorDetailsMap);
                }
            }
        }
    }
}
