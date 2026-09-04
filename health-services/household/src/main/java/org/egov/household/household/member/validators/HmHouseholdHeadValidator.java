package org.egov.household.household.member.validators;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.egov.common.exception.InvalidTenantIdException;
import org.egov.common.models.Error;
import org.egov.common.models.household.HouseholdMember;
import org.egov.common.models.household.HouseholdMemberBulkRequest;
import org.egov.common.utils.CommonUtils;
import org.egov.common.validator.Validator;
import org.egov.household.config.HouseholdMemberConfiguration;
import org.egov.household.repository.HouseholdMemberRepository;
import org.egov.tracer.model.CustomException;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.egov.common.utils.CommonUtils.getIdMethod;
import static org.egov.common.utils.CommonUtils.notHavingErrors;
import static org.egov.common.utils.CommonUtils.populateErrorDetails;
import static org.egov.household.Constants.CLIENT_REFERENCE_ID_FIELD;
import static org.egov.common.utils.ValidatorUtils.getErrorForInvalidTenantId;
import static org.egov.household.Constants.*;
import static org.egov.household.Constants.HOUSEHOLD_ALREADY_HAS_HEAD;
import static org.egov.household.Constants.HOUSEHOLD_ALREADY_HAS_HEAD_MESSAGE;
import static org.egov.household.Constants.HOUSEHOLD_CLIENT_REFERENCE_ID_FIELD;
import static org.egov.household.Constants.HOUSEHOLD_ID_FIELD;
import static org.egov.household.Constants.ID_FIELD;

@Component
@Order(9)
@Slf4j
public class HmHouseholdHeadValidator implements Validator<HouseholdMemberBulkRequest, HouseholdMember> {

    private final HouseholdMemberRepository householdMemberRepository;

    private final HouseholdMemberConfiguration householdMemberConfiguration;

    public HmHouseholdHeadValidator(HouseholdMemberRepository householdMemberRepository,
                                    HouseholdMemberConfiguration householdMemberConfiguration) {
        this.householdMemberRepository = householdMemberRepository;
        this.householdMemberConfiguration = householdMemberConfiguration;
    }

    @Override
    public Map<HouseholdMember, List<Error>> validate(HouseholdMemberBulkRequest householdMemberBulkRequest) {
        HashMap<HouseholdMember, List<Error>> errorDetailsMap = new HashMap<>();
        log.debug("validating head of household member");
        List<HouseholdMember> householdMembers = householdMemberBulkRequest.getHouseholdMembers().stream()
                .filter(notHavingErrors()).collect(Collectors.toList());
        if(!householdMembers.isEmpty()){
            String tenantId = CommonUtils.getTenantId(householdMembers);
            Method householdMemberidMethod = getIdMethod(householdMembers, ID_FIELD, CLIENT_REFERENCE_ID_FIELD);
            boolean strictHeadValidation = householdMemberConfiguration.isHouseholdMemberHeadStrictValidation();

            // Group per member by whichever parent key that member actually carries. Previously a single
            // accessor was chosen for the whole batch from an arbitrary element, so a mixed batch (some
            // members carrying only householdId, others only householdClientReferenceId) produced a null
            // grouping key and threw NullPointerException("element cannot be mapped to a null key") out of
            // validate(), discarding the entire batch. Members carrying neither key are skipped here;
            // HmRequiredLinkValidator reports them when it is enabled.
            Map<String, List<HouseholdMember>> membersByHouseholdId = new LinkedHashMap<>();
            Map<String, List<HouseholdMember>> membersByHouseholdClientReferenceId = new LinkedHashMap<>();
            for (HouseholdMember householdMember : householdMembers) {
                if (StringUtils.isNotBlank(householdMember.getHouseholdId())) {
                    membersByHouseholdId
                            .computeIfAbsent(householdMember.getHouseholdId(), key -> new ArrayList<>())
                            .add(householdMember);
                } else if (StringUtils.isNotBlank(householdMember.getHouseholdClientReferenceId())) {
                    membersByHouseholdClientReferenceId
                            .computeIfAbsent(householdMember.getHouseholdClientReferenceId(), key -> new ArrayList<>())
                            .add(householdMember);
                }
            }

            membersByHouseholdId.forEach((householdId, householdMembersInHousehold) ->
                    validateHeadOfHousehold(tenantId, householdId, householdMemberidMethod, HOUSEHOLD_ID_FIELD,
                            errorDetailsMap, householdMembersInHousehold, strictHeadValidation));
            membersByHouseholdClientReferenceId.forEach((householdClientReferenceId, householdMembersInHousehold) ->
                    validateHeadOfHousehold(tenantId, householdClientReferenceId, householdMemberidMethod,
                            HOUSEHOLD_CLIENT_REFERENCE_ID_FIELD, errorDetailsMap, householdMembersInHousehold,
                            strictHeadValidation));
        }
        log.debug("household member Head validation completed successfully, total errors: " + errorDetailsMap.size());
        return errorDetailsMap;
    }

    private void validateHeadOfHousehold(String tenantId, String householdId, Method householdMemberidMethod, String householdColumnName,
                                         HashMap<HouseholdMember, List<Error>> errorDetailsMap, List<HouseholdMember> householdMembersRequest,
                                         boolean strictHeadValidation) {
        log.debug("validating if household already has a head");
        List<HouseholdMember> requestHouseholdHead = householdMembersRequest.stream().filter(HouseholdMember::getIsHeadOfHousehold).toList();

        // Validates if a household has more than 1 heads (gated: rule added after the old baseline)
        if(strictHeadValidation && requestHouseholdHead.size() > 1) {
            householdMembersRequest.forEach(householdMember -> {
                Error error = Error.builder().errorMessage(HOUSEHOLD_HAS_MORE_THAN_ONE_HEAD_MESSAGE)
                        .errorCode(HOUSEHOLD_HAS_MORE_THAN_ONE_HEAD)
                        .type(Error.ErrorType.NON_RECOVERABLE)
                        .exception(new CustomException(HOUSEHOLD_HAS_MORE_THAN_ONE_HEAD,
                                HOUSEHOLD_HAS_MORE_THAN_ONE_HEAD_MESSAGE))
                        .build();
                populateErrorDetails(householdMember, error, errorDetailsMap);
            });
            log.error("More than one head of household found for household {}", householdId);
            return;
        }
        try {
            List<HouseholdMember> existingHouseholdHead = householdMemberRepository
                    .findIndividualByHousehold(tenantId, householdId, householdColumnName).getResponse()
                    .stream().filter(HouseholdMember::getIsHeadOfHousehold).toList();

            // Validates if a household doesn't have a head (gated: rule added after the old baseline).
            // With the gate off, a member whose household head is still on the persister queue - or whose
            // household legitimately has no head - is accepted, as it was before.
            if(strictHeadValidation && requestHouseholdHead.isEmpty() && existingHouseholdHead.isEmpty()) {
                householdMembersRequest.forEach(householdMember -> {
                    Error error = Error.builder().errorMessage(HOUSEHOLD_DOES_NOT_HAVE_A_HEAD_MESSAGE)
                            .errorCode(HOUSEHOLD_DOES_NOT_HAVE_A_HEAD)
                            .type(Error.ErrorType.NON_RECOVERABLE)
                            .exception(new CustomException(HOUSEHOLD_DOES_NOT_HAVE_A_HEAD,
                                    HOUSEHOLD_DOES_NOT_HAVE_A_HEAD_MESSAGE))
                            .build();
                    populateErrorDetails(householdMember, error, errorDetailsMap);
                });
                log.error("No head of household found for household {}", householdId);
                return;
            }

            // Validate if household head is removed (gated: rule added after the old baseline)
            if(strictHeadValidation && requestHouseholdHead.isEmpty() && !existingHouseholdHead.isEmpty()) {
                HouseholdMember existingHead = existingHouseholdHead.get(0);
                String existingHeadMemberId = (String) ReflectionUtils.invokeMethod(householdMemberidMethod, existingHead);
                Optional<HouseholdMember> unassignedHouseholdHead = householdMembersRequest.stream().filter(householdMember ->
                        Objects.equals(existingHeadMemberId, ReflectionUtils.invokeMethod(householdMemberidMethod, householdMember)))
                        .findFirst();
                if(unassignedHouseholdHead.isPresent()) {
                    Error error = Error.builder().errorMessage(HOUSEHOLD_HEAD_CANNOT_BE_UNASSIGNED_MESSAGE)
                            .errorCode(HOUSEHOLD_HEAD_CANNOT_BE_UNASSIGNED)
                            .type(Error.ErrorType.NON_RECOVERABLE)
                            .exception(new CustomException(HOUSEHOLD_HEAD_CANNOT_BE_UNASSIGNED,
                                    HOUSEHOLD_HEAD_CANNOT_BE_UNASSIGNED_MESSAGE))
                            .build();
                    populateErrorDetails(unassignedHouseholdHead.get(), error, errorDetailsMap);
                    log.error("household head cannot be unassigned, error: {}", error);
                    return;
                }
            }

            // Validates if a household head reassignment is valid
            if(!existingHouseholdHead.isEmpty() && !requestHouseholdHead.isEmpty()) {
                HouseholdMember existingHead = existingHouseholdHead.get(0);
                String existingHeadMemberId = (String) ReflectionUtils.invokeMethod(householdMemberidMethod, existingHead);
                String currentHeadMemberId = (String) ReflectionUtils.invokeMethod(householdMemberidMethod, requestHouseholdHead.get(0));
                boolean isReassigning = existingHeadMemberId != null && currentHeadMemberId != null
                        && !existingHeadMemberId.equals(currentHeadMemberId);
                boolean existingHeadInRequest = householdMembersRequest.stream()
                        .anyMatch(householdMember -> {
                            String existingHeadMemberIdInRequest = (String) ReflectionUtils.invokeMethod(householdMemberidMethod, householdMember);
                            return existingHeadMemberIdInRequest != null && existingHeadMemberIdInRequest.equals(existingHeadMemberId);
                        });

                // Validate if household head is being reassigned but existing head is not present
                // in the request with isHeadOfHousehold = false
                if(isReassigning && !existingHeadInRequest) {
                    Error error = Error.builder().errorMessage(HOUSEHOLD_ALREADY_HAS_HEAD_MESSAGE)
                            .errorCode(HOUSEHOLD_ALREADY_HAS_HEAD)
                            .type(Error.ErrorType.NON_RECOVERABLE)
                            .exception(new CustomException(HOUSEHOLD_ALREADY_HAS_HEAD,
                                    HOUSEHOLD_ALREADY_HAS_HEAD_MESSAGE))
                            .build();
                    log.error("household already has a head, error: {}", error);
                    populateErrorDetails(requestHouseholdHead.get(0), error, errorDetailsMap);
                }
            }
        } catch (InvalidTenantIdException exception) {
            log.error("Invalid tenantId found for household members {}", householdMembersRequest, exception);
            householdMembersRequest.forEach(householdMember -> {
                Error error = getErrorForInvalidTenantId(tenantId, exception);
                populateErrorDetails(householdMember, error, errorDetailsMap);
            });
        }
    }
}
