package org.egov.referralmanagement.validator.sideeffect;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.Error;
import org.egov.common.models.referralmanagement.sideeffect.SideEffect;
import org.egov.common.models.referralmanagement.sideeffect.SideEffectBulkRequest;
import org.egov.common.validator.Validator;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

import static org.egov.referralmanagement.Constants.GET_SIDE_EFFECTS;
import static org.egov.common.utils.CommonUtils.validateForNullId;


@Component
@Order(value = 1)
@Slf4j
public class SeNullIdValidator implements Validator<SideEffectBulkRequest, SideEffect> {
    @Override
    public Map<SideEffect, List<Error>> validate(SideEffectBulkRequest request) {
        log.info("validating for null id");
        return validateForNullId(request, GET_SIDE_EFFECTS);
    }
}
