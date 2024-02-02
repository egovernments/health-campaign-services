package org.egov.referralmanagement.validator.sideeffect;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.Error;
import org.egov.common.models.referralmanagement.sideeffect.SideEffect;
import org.egov.common.models.referralmanagement.sideeffect.SideEffectBulkRequest;
import org.egov.common.validator.Validator;
import org.egov.referralmanagement.repository.SideEffectRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.egov.common.utils.CommonUtils.*;
import static org.egov.common.utils.ValidatorUtils.getErrorForRowVersionMismatch;

@Component
@Order(value = 5)
@Slf4j
public class SeRowVersionValidator implements Validator<SideEffectBulkRequest, SideEffect> {

    private SideEffectRepository sideEffectRepository;

    @Autowired
    public SeRowVersionValidator(SideEffectRepository sideEffectRepository) {
        this.sideEffectRepository = sideEffectRepository;
    }

    @Override
    public Map<SideEffect, List<Error>> validate(SideEffectBulkRequest sideEffectBulkRequest) {
        log.info("validating row version");
        Map<SideEffect, List<Error>> errorDetailsMap = new HashMap<>();
        Method idMethod = getIdMethod(sideEffectBulkRequest.getSideEffects());
        Map<String, SideEffect> iMap = getIdToObjMap(sideEffectBulkRequest
                .getSideEffects()
                .stream()
                .filter(notHavingErrors())
                .collect(Collectors.toList()),idMethod);
        if(!iMap.isEmpty()){
            List<String> sideEffectIds = new ArrayList<>(iMap.keySet());
            List<SideEffect> existingSideEffects = sideEffectRepository
                    .findById(sideEffectIds,false,getIdFieldName(idMethod));
            List<SideEffect> entitiesWithMismatchedRowVersion = getEntitiesWithMismatchedRowVersion(iMap, existingSideEffects, idMethod);
            entitiesWithMismatchedRowVersion.forEach(sideEffect -> {
                Error error = getErrorForRowVersionMismatch();
                populateErrorDetails(sideEffect, error, errorDetailsMap);
            });
         }
        return errorDetailsMap;
    }
}
