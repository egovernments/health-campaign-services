package org.egov.referralmanagement.validator;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.Error;
import org.egov.common.models.referralmanagement.Referral;
import org.egov.common.models.referralmanagement.ReferralBulkRequest;
import org.egov.common.validator.Validator;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

import static org.egov.common.utils.CommonUtils.validateForNullId;
import static org.egov.referralmanagement.Constants.GET_REFERRALS;


@Component
@Order(value = 1)
@Slf4j
public class RmNullIdValidator implements Validator<ReferralBulkRequest, Referral> {
    @Override
    public Map<Referral, List<Error>> validate(ReferralBulkRequest request) {
        log.info("validating for null id");
        return validateForNullId(request, GET_REFERRALS);
    }
}
