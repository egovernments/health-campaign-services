package org.egov.household.household.member.validators;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.Error;
import org.egov.common.models.household.HouseholdMember;
import org.egov.common.models.household.HouseholdMemberBulkRequest;
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

import static org.egov.common.utils.CommonUtils.checkNonExistentEntities;
import static org.egov.common.utils.CommonUtils.getIdFieldName;
import static org.egov.common.utils.CommonUtils.getIdToObjMap;
import static org.egov.common.utils.CommonUtils.getMethod;
import static org.egov.common.utils.CommonUtils.getObjClass;
import static org.egov.common.utils.CommonUtils.notHavingErrors;
import static org.egov.common.utils.CommonUtils.populateErrorDetails;
import static org.egov.common.utils.ValidatorUtils.getErrorForNonExistentEntity;
import static org.egov.household.Constants.GET_ID;

@Component
@Order(value = 4)
@Slf4j
public class HmNonExistentEntityValidator implements Validator<HouseholdMemberBulkRequest, HouseholdMember> {

    private final HouseholdMemberRepository householdMemberRepository;

    @Autowired
    public HmNonExistentEntityValidator(HouseholdMemberRepository householdMemberRepository) {
        this.householdMemberRepository = householdMemberRepository;
    }


    @Override
    public Map<HouseholdMember, List<Error>> validate(HouseholdMemberBulkRequest request) {
        Map<HouseholdMember, List<Error>> errorDetailsMap = new HashMap<>();
        List<HouseholdMember> householdMembers = request.getHouseholdMembers();
        log.info("validating non existent household member");
        Class<?> objClass = getObjClass(householdMembers);
        Method idMethod = getMethod(GET_ID, objClass);
        Map<String, HouseholdMember> iMap = getIdToObjMap(householdMembers
                .stream().filter(notHavingErrors()).collect(Collectors.toList()), idMethod);
        if (!iMap.isEmpty()) {
            List<String> householdMemberIds = new ArrayList<>(iMap.keySet());
            List<HouseholdMember> existingHouseholdMembers = householdMemberRepository.findById(householdMemberIds,
                    getIdFieldName(idMethod), false);
            List<HouseholdMember> nonExistentIndividuals = checkNonExistentEntities(iMap,
                    existingHouseholdMembers, idMethod);
            nonExistentIndividuals.forEach(householdMember -> {
                Error error = getErrorForNonExistentEntity();
                populateErrorDetails(householdMember, error, errorDetailsMap);
            });
        }
        log.info("household member non existent validation completed successfully, total errors: " + errorDetailsMap.size());
        return errorDetailsMap;
    }
}
