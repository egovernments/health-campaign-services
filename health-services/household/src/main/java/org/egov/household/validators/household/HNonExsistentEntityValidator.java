package org.egov.household.validators.household;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.egov.common.exception.InvalidTenantIdException;
import org.egov.common.models.Error;
import org.egov.common.models.household.Household;
import org.egov.common.models.household.HouseholdBulkRequest;
import org.egov.common.utils.CommonUtils;
import org.egov.common.validator.Validator;
import org.egov.household.repository.HouseholdRepository;
import org.egov.tracer.model.CustomException;
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
import static org.egov.common.utils.ValidatorUtils.*;
import static org.egov.household.Constants.GET_ID;
import static org.egov.household.Constants.TENANT_ID_EXCEPTION;

@Component
@Order(value = 2)
@Slf4j
public class HNonExsistentEntityValidator implements Validator<HouseholdBulkRequest, Household> {

    private final HouseholdRepository householdRepository;

    public HNonExsistentEntityValidator(HouseholdRepository householdRepository) {
        this.householdRepository = householdRepository;
    }

    @Override
    public Map<Household, List<Error>> validate(HouseholdBulkRequest request) {
        String tenantId = CommonUtils.getTenantId(request.getHouseholds());
        Map<Household, List<Error>> errorDetailsMap = new HashMap<>();
        List<Household> entities = request.getHouseholds();
        Class<?> objClass = getObjClass(entities);
        Method idMethod = getMethod(GET_ID, objClass);
        Map<String, Household> eMap = getIdToObjMap(entities
                .stream().filter(notHavingErrors()).collect(Collectors.toList()), idMethod);
        if (!eMap.isEmpty()) {
            List<String> entityIds = new ArrayList<>(eMap.keySet());

            try {
                List<Household> existingEntities = householdRepository.findById(tenantId, entityIds,
                        getIdFieldName(idMethod), false).getResponse();
                List<Household> nonExistentEntities = checkNonExistentEntities(eMap,
                        existingEntities, idMethod);
                nonExistentEntities.forEach(task -> {
                    Error error = getErrorForNonExistentEntity();
                    populateErrorDetails(task, error, errorDetailsMap);
                });
            } catch (InvalidTenantIdException exception) {
                entities.forEach(household -> {
                    if (!StringUtils.isEmpty(household.getTenantId())) {
                        Error error = getErrorForInvalidTenantId(tenantId, exception);
                        populateErrorDetails(household, error, errorDetailsMap);
                    } else {
                        Error error = getErrorForNullTenantId();
                        populateErrorDetails(household, error, errorDetailsMap);
                    }

                });
            }

        }

        return errorDetailsMap;
    }
}