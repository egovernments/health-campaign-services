package org.egov.household.household.member.validators;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.exception.InvalidTenantIdException;
import org.egov.common.models.Error;
import org.egov.common.models.household.HouseholdMember;
import org.egov.common.models.household.HouseholdMemberBulkRequest;
import org.egov.common.utils.CommonUtils;
import org.egov.common.validator.Validator;
import org.egov.household.repository.HouseholdMemberRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.egov.common.utils.CommonUtils.getEntitiesWithMismatchedRowVersion;
import static org.egov.common.utils.CommonUtils.getIdFieldName;
import static org.egov.common.utils.CommonUtils.getIdMethod;
import static org.egov.common.utils.CommonUtils.getIdToObjMap;
import static org.egov.common.utils.CommonUtils.notHavingErrors;
import static org.egov.common.utils.CommonUtils.populateErrorDetails;
import static org.egov.common.utils.ValidatorUtils.getErrorForInvalidTenantId;
import static org.egov.common.utils.ValidatorUtils.getErrorForRowVersionMismatch;

@Component
@Order(value = 5)
@Slf4j
public class HmRowVersionValidator implements Validator<HouseholdMemberBulkRequest, HouseholdMember> {

    private final HouseholdMemberRepository householdMemberRepository;

    @Autowired
    public HmRowVersionValidator(HouseholdMemberRepository householdMemberRepository) {
        this.householdMemberRepository = householdMemberRepository;
    }

    @Override
    public Map<HouseholdMember, List<Error>> validate(HouseholdMemberBulkRequest request){
        Map<HouseholdMember, List<Error>> errorDetailsMap = new HashMap<>();
        log.info("validating row version household member");
        String tenantId = CommonUtils.getTenantId(request.getHouseholdMembers());
        List<HouseholdMember> validHouseholdMembers = request.getHouseholdMembers().stream()
                .filter(notHavingErrors())
                .collect(Collectors.toList());
        if (!validHouseholdMembers.isEmpty()) {
            Method idMethod = getIdMethod(validHouseholdMembers);
            Map<String, HouseholdMember> iMap = getIdToObjMap(validHouseholdMembers, idMethod);
            if (!iMap.isEmpty()) {
                List<String> householdMemberIds = new ArrayList<>(iMap.keySet());
                try {
                    List<HouseholdMember> existingHouseholdMembers = householdMemberRepository.findById(tenantId, householdMemberIds,
                            getIdFieldName(idMethod), false).getResponse();
                    List<HouseholdMember> entitiesWithMismatchedRowVersion =
                            getEntitiesWithMismatchedRowVersion(iMap, existingHouseholdMembers, idMethod);
                    entitiesWithMismatchedRowVersion.forEach(householdMember -> {
                        Error error = getErrorForRowVersionMismatch();
                        populateErrorDetails(householdMember, error, errorDetailsMap);
                    });
                } catch (InvalidTenantIdException exception) {
                    validHouseholdMembers.stream().forEach(householdMember -> {
                        Error error = getErrorForInvalidTenantId(tenantId, exception);
                        populateErrorDetails(householdMember, error, errorDetailsMap);
                    });
                }

            }
        }
        log.info("household member row version validation completed successfully, total errors: " + errorDetailsMap.size());
        return errorDetailsMap;
    }
}
