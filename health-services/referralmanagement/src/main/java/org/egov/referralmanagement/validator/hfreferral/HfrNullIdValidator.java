package org.egov.referralmanagement.validator.hfreferral;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.Error;
import org.egov.common.models.referralmanagement.hfreferral.HFReferral;
import org.egov.common.models.referralmanagement.hfreferral.HFReferralBulkRequest;
import org.egov.common.validator.Validator;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

import static org.egov.common.utils.CommonUtils.validateForNullId;
import static org.egov.referralmanagement.Constants.GET_HF_REFERRALS;


@Component
@Order(value = 1)
@Slf4j
public class HfrNullIdValidator implements Validator<HFReferralBulkRequest, HFReferral> {
    @Override
    public Map<HFReferral, List<Error>> validate(HFReferralBulkRequest request) {
        log.info("validating for null id");
        return validateForNullId(request, GET_HF_REFERRALS);
    }
}
