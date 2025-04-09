package org.egov.household.household.member.validators;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.Error;
import org.egov.common.models.household.HouseHoldType;
import org.egov.common.models.household.Household;
import org.egov.common.models.household.HouseholdMember;
import org.egov.common.models.household.HouseholdMemberBulkRequest;
import org.egov.common.utils.CommonUtils;
import org.egov.common.validator.Validator;
import org.egov.household.service.HouseholdService;
import org.egov.tracer.model.CustomException;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.egov.common.utils.CommonUtils.getIdList;
import static org.egov.common.utils.CommonUtils.getIdMethod;
import static org.egov.common.utils.CommonUtils.getSet;
import static org.egov.common.utils.CommonUtils.populateErrorDetails;
import static org.egov.household.Constants.HOUSEHOLD_MEMBER_RELATIONSHIP_NOT_ALLOWED_MESSAGE;
import static org.egov.household.Constants.INVALID_HOUSEHOLD;
import static org.egov.household.Constants.INVALID_HOUSEHOLD_MEMBER_RELATIONSHIP;
import static org.egov.household.Constants.INVALID_HOUSEHOLD_MESSAGE;

@Slf4j
@Component
@Order(6)
public class HmHouseholdValidator implements Validator<HouseholdMemberBulkRequest, HouseholdMember> {

    private final HouseholdService householdService;

    /**
     * Constructor to initialize the HouseholdService dependency.
     *
     * @param householdService The service for households
     */
    public HmHouseholdValidator(HouseholdService householdService) {
        this.householdService = householdService;
    }

    /**
     * Validates the non-existence of household member households,
     * Also validates the relationships exists only for household type FAMILY
     *
     * @param householdMemberBulkRequest The bulk request containing household members.
     * @return A map containing household members and their associated error details.
     */
    @Override
    public Map<HouseholdMember, List<Error>> validate(HouseholdMemberBulkRequest householdMemberBulkRequest) {
        HashMap<HouseholdMember, List<Error>> errorDetailsMap = new HashMap<>();
        List<HouseholdMember> householdMembers = householdMemberBulkRequest.getHouseholdMembers();

        log.info("getting id method for household members");
        Method idMethod = getIdMethod(householdMembers, "householdId",
                "householdClientReferenceId");

        log.info("getting column name for id method: {}", idMethod.getName());
        String columnName = getColumnName(idMethod);

        log.info("getting household ids from household members");
        List<String> houseHoldIds = getIdList(householdMembers, idMethod);

        log.info("finding valid household ids from household service");
        List<Household> validHouseHoldIds = householdService.findById(houseHoldIds, columnName, false).getResponse();

        Map<String, Household> householdMap = validHouseHoldIds.stream().collect(Collectors.toMap(Household::getClientReferenceId, d -> d));

        log.info("getting unique household ids from valid household ids");
        Set<String> uniqueHoldIds = getSet(validHouseHoldIds, columnName == "id" ? "getId": "getClientReferenceId");

        log.info("getting invalid household ids");
        List<String> invalidHouseholds = CommonUtils.getDifference(
                houseHoldIds,
                new ArrayList<>(uniqueHoldIds)
        );

        householdMembers.stream()
                .filter(householdMember -> invalidHouseholds.contains(householdMember.getHouseholdId()))
                .forEach(householdMember -> {
                    Error error = Error.builder().errorMessage(INVALID_HOUSEHOLD_MESSAGE)
                            .errorCode(INVALID_HOUSEHOLD)
                            .type(Error.ErrorType.NON_RECOVERABLE)
                            .exception(new CustomException(INVALID_HOUSEHOLD, INVALID_HOUSEHOLD_MESSAGE))
                            .build();
                    log.info("validation failed for household member: {} with error: {}", householdMember, error);
                    populateErrorDetails(householdMember, error, errorDetailsMap);
                });

        // Validates if household type is not FAMILY and still adding relationships for household member
        householdMembers.stream()
                .filter(householdMember -> !invalidHouseholds.contains(householdMember.getHouseholdId()))
                .filter(d -> !CollectionUtils.isEmpty(d.getRelationships()))
                .forEach(householdMember -> {
                    HouseHoldType householdType = householdMap.get(householdMember.getHouseholdClientReferenceId()).getHouseholdType();
                    if (!HouseHoldType.FAMILY.equals(householdType)) {
                        Error error = Error.builder().errorMessage(HOUSEHOLD_MEMBER_RELATIONSHIP_NOT_ALLOWED_MESSAGE)
                                .errorCode(INVALID_HOUSEHOLD_MEMBER_RELATIONSHIP)
                                .type(Error.ErrorType.NON_RECOVERABLE)
                                .exception(new CustomException(INVALID_HOUSEHOLD, INVALID_HOUSEHOLD_MESSAGE))
                                .build();
                        log.info("validation failed for household member: {} with error: {}", householdMember, error);
                        populateErrorDetails(householdMember, error, errorDetailsMap);
                    }
                });

        log.info("Household member household validation completed successfully, total errors: " + errorDetailsMap.size());
        return errorDetailsMap;
    }

    private String getColumnName(Method idMethod) {
        String columnName = "id";
        if ("getHouseholdClientReferenceId".equals(idMethod.getName())) {
            columnName = "clientReferenceId";
        }
        return columnName;
    }
}
