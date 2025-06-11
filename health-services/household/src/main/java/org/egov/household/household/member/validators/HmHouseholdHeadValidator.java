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
import static org.egov.household.Constants.HOUSEHOLD_ALREADY_HAS_HEAD;
import static org.egov.household.Constants.HOUSEHOLD_ALREADY_HAS_HEAD_MESSAGE;
import static org.egov.household.Constants.HOUSEHOLD_CLIENT_REFERENCE_ID_FIELD;
import static org.egov.household.Constants.HOUSEHOLD_ID_FIELD;
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

   private void validateHeadOfHousehold(HouseholdMember householdMember, Method idMethod, String columnName,
                                         HashMap<HouseholdMember, List<Error>> errorDetailsMap,List<HouseholdMember> requestMembers) {

        if(householdMember.getIsHeadOfHousehold()){
        log.info("validating if household already has a head");
        List<HouseholdMember> householdMembersHeadCheck = householdMemberRepository
                .findIndividualByHousehold((String) ReflectionUtils.invokeMethod(idMethod, householdMember),
                            columnName).getResponse().stream().filter(HouseholdMember::getIsHeadOfHousehold)
                .collect(Collectors.toList());
                boolean isSameAsExistingHead = householdMembersHeadCheck.stream().allMatch(existing ->
                (existing.getIndividualId() != null && existing.getIndividualId().equals(householdMember.getIndividualId())) ||
                (existing.getId() != null && existing.getId().equals(householdMember.getId()))
        );

        boolean isReassigning = householdMembersHeadCheck.stream().anyMatch(existing ->
                requestMembers.stream().anyMatch(reqMember ->
                        ((existing.getIndividualId() != null && existing.getIndividualId().equals(reqMember.getIndividualId())) ||
                         (existing.getId() != null && existing.getId().equals(reqMember.getId())))
                        && (reqMember.getIsHeadOfHousehold() == null || !reqMember.getIsHeadOfHousehold())
                )
        );

        if (!householdMembersHeadCheck.isEmpty() && !isSameAsExistingHead && !isReassigning) {
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
