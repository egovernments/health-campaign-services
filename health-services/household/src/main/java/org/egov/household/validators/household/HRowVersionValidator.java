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

import static org.egov.common.utils.CommonUtils.getEntitiesWithMismatchedRowVersion;
import static org.egov.common.utils.CommonUtils.getIdFieldName;
import static org.egov.common.utils.CommonUtils.getIdMethod;
import static org.egov.common.utils.CommonUtils.getIdToObjMap;
import static org.egov.common.utils.CommonUtils.notHavingErrors;
import static org.egov.common.utils.CommonUtils.populateErrorDetails;
import static org.egov.common.utils.ValidatorUtils.*;
import static org.egov.household.Constants.TENANT_ID_EXCEPTION;

@Component
@Order(value = 5)
@Slf4j
public class HRowVersionValidator implements Validator<HouseholdBulkRequest, Household> {
    
    private final HouseholdRepository repository;

    public HRowVersionValidator(HouseholdRepository repository) {
        this.repository = repository;
    }

    @Override
    public Map<Household, List<Error>> validate(HouseholdBulkRequest request) {
        String tenantId = CommonUtils.getTenantId(request.getHouseholds());
        Map<Household, List<Error>> errorDetailsMap = new HashMap<>();
        Method idMethod = getIdMethod(request.getHouseholds());
        Map<String, Household> eMap = getIdToObjMap(request.getHouseholds().stream()
                .filter(notHavingErrors())
                .collect(Collectors.toList()), idMethod);
        if (!eMap.isEmpty()) {
            List<String> entityIds = new ArrayList<>(eMap.keySet());
            try {
                List<Household> existingEntities = repository.findById(tenantId, entityIds,
                        getIdFieldName(idMethod), false).getResponse();
                List<Household> entitiesWithMismatchedRowVersion =
                        getEntitiesWithMismatchedRowVersion(eMap, existingEntities, idMethod);
                entitiesWithMismatchedRowVersion.forEach(individual -> {
                    Error error = getErrorForRowVersionMismatch();
                    populateErrorDetails(individual, error, errorDetailsMap);
                });
            } catch (InvalidTenantIdException exception) {
                request.getHouseholds().forEach(household -> {
                    if (!StringUtils.isEmpty(household.getTenantId())) {
                        Error error = getErrorForInvalidTenantId(tenantId,exception);
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