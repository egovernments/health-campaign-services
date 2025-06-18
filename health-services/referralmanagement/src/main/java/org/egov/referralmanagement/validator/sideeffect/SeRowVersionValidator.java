package org.egov.referralmanagement.validator.sideeffect;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.Error;
import org.egov.common.models.referralmanagement.sideeffect.SideEffect;
import org.egov.common.models.referralmanagement.sideeffect.SideEffectBulkRequest;
import org.egov.common.validator.Validator;
import org.egov.referralmanagement.repository.SideEffectRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import static org.egov.common.utils.CommonUtils.getEntitiesWithMismatchedRowVersion;
import static org.egov.common.utils.CommonUtils.getIdFieldName;
import static org.egov.common.utils.CommonUtils.getIdMethod;
import static org.egov.common.utils.CommonUtils.getIdToObjMap;
import static org.egov.common.utils.CommonUtils.notHavingErrors;
import static org.egov.common.utils.CommonUtils.populateErrorDetails;
import static org.egov.common.utils.ValidatorUtils.getErrorForRowVersionMismatch;

/*
*
* Validator for checking row version consistency of SideEffect entities in a bulk request.
* It retrieves existing SideEffect entities from the repository, compares row versions,
* and populates error details for entities with mismatched row versions.
*
* @author syed-egov
* */

@Component
@Order(value = 5)
@Slf4j
public class SeRowVersionValidator implements Validator<SideEffectBulkRequest, SideEffect> {

    private SideEffectRepository sideEffectRepository;

    @Autowired
    public SeRowVersionValidator(SideEffectRepository sideEffectRepository) {
        this.sideEffectRepository = sideEffectRepository;
    }

    /*
     *
     * @param sideEffectBulkRequest The bulk request containing SideEffect entities to be validated.
     * @return A map containing SideEffect entities with associated error details
     * for entities with mismatched row versions.
     *
     */

    @Override
    public Map<SideEffect, List<Error>> validate(SideEffectBulkRequest sideEffectBulkRequest) {
        log.info("validating row version");
        // Map to store SideEffect entities with associated error details
        Map<SideEffect, List<Error>> errorDetailsMap = new HashMap<>();
        // Get the method used for obtaining entity IDs
        Method idMethod = getIdMethod(sideEffectBulkRequest.getSideEffects());
        // Create a map of entity IDs to SideEffect entities for entities without errors
        Map<String, SideEffect> iMap = getIdToObjMap(sideEffectBulkRequest
                .getSideEffects()
                .stream()
                .filter(notHavingErrors())
                .collect(Collectors.toList()),idMethod);
        // Check if the map of IDs to SideEffect entities is not empty
        if(!iMap.isEmpty()){
            List<String> sideEffectIds = new ArrayList<>(iMap.keySet());
            // Retrieve existing SideEffect entities from the repository
            List<SideEffect> existingSideEffects = sideEffectRepository
                    .findById(sideEffectIds,false,getIdFieldName(idMethod));
            // Identify entities with mismatched row versions
            List<SideEffect> entitiesWithMismatchedRowVersion = getEntitiesWithMismatchedRowVersion(iMap, existingSideEffects, idMethod);
            // Populate error details for entities with mismatched row versions
            entitiesWithMismatchedRowVersion.forEach(sideEffect -> {
                Error error = getErrorForRowVersionMismatch();
                populateErrorDetails(sideEffect, error, errorDetailsMap);
            });
         }
        return errorDetailsMap;
    }
}
