package org.egov.referralmanagement.validator.sideeffect;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.Error;
import org.egov.common.models.referralmanagement.sideeffect.SideEffect;
import org.egov.common.models.referralmanagement.sideeffect.SideEffectBulkRequest;
import org.egov.common.validator.Validator;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.egov.common.utils.CommonUtils.populateErrorDetails;
import static org.egov.common.utils.ValidatorUtils.getErrorForIsDelete;

@Component
@Order(2)
@Slf4j
public class SeIsDeletedValidator implements Validator<SideEffectBulkRequest, SideEffect> {

    @Override
    public Map<SideEffect, List<Error>> validate(SideEffectBulkRequest request) {
        log.info("validating isDeleted field");
        HashMap<SideEffect, List<Error>> errorDetailsMap = new HashMap<>();
        List<SideEffect> validIndividuals = request.getSideEffects();
        validIndividuals.stream().filter(SideEffect::getIsDeleted).forEach(individual -> {
            Error error = getErrorForIsDelete();
            populateErrorDetails(individual, error, errorDetailsMap);
        });
        return errorDetailsMap;
    }
}
