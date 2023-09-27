<<<<<<<< HEAD:health-services/referralmanagement/src/main/java/org/egov/referralmanagement/validator/sideeffect/SeUniqueEntityValidator.java
package org.egov.referralmanagement.validator.sideeffect;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.Error;
import org.egov.common.models.referralmanagement.sideeffect.SideEffect;
import org.egov.common.models.referralmanagement.sideeffect.SideEffectBulkRequest;
========
package org.egov.referralmanagement.validator.adverseevent;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.Error;
import org.egov.common.models.referralmanagement.adverseevent.AdverseEvent;
import org.egov.common.models.referralmanagement.adverseevent.AdverseEventBulkRequest;
>>>>>>>> 51cd6f6468 (HLM-3069: changed module name to referral management):health-services/referralmanagement/src/main/java/org/egov/referralmanagement/validator/adverseevent/AdUniqueEntityValidator.java
import org.egov.common.validator.Validator;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.egov.common.utils.CommonUtils.*;
import static org.egov.common.utils.ValidatorUtils.getErrorForUniqueEntity;

@Component
@Order(value = 2)
@Slf4j
public class SeUniqueEntityValidator implements Validator<SideEffectBulkRequest, SideEffect> {

    @Override
    public Map<SideEffect, List<Error>> validate(SideEffectBulkRequest request) {
        log.info("validating unique entity");
        Map<SideEffect, List<Error>> errorDetailsMap = new HashMap<>();
        List<SideEffect> validEntities = request.getSideEffects()
                .stream().filter(notHavingErrors()).collect(Collectors.toList());
        if (!validEntities.isEmpty()) {
            Map<String, SideEffect> eMap = getIdToObjMap(validEntities);
            if (eMap.keySet().size() != validEntities.size()) {
                List<String> duplicates = eMap.keySet().stream().filter(id ->
                        validEntities.stream()
                                .filter(entity -> entity.getId().equals(id)).count() > 1
                ).collect(Collectors.toList());
                for (String key : duplicates) {
                    Error error = getErrorForUniqueEntity();
                    populateErrorDetails(eMap.get(key), error, errorDetailsMap);
                }
            }
        }
        return errorDetailsMap;
    }
}
