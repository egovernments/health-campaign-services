package org.egov.referralmanagement.validator;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.Error;
import org.egov.common.models.referralmanagement.Referral;
import org.egov.common.models.referralmanagement.ReferralBulkRequest;
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
public class RmIsDeletedValidator implements Validator<ReferralBulkRequest, Referral> {

    @Override
    public Map<Referral, List<Error>> validate(ReferralBulkRequest request) {
        log.info("validating isDeleted field");
        HashMap<Referral, List<Error>> errorDetailsMap = new HashMap<>();
        List<Referral> validEntities = request.getReferrals();
        validEntities.stream().filter(Referral::getIsDeleted).forEach(referral -> {
            Error error = getErrorForIsDelete();
            populateErrorDetails(referral, error, errorDetailsMap);
        });
        return errorDetailsMap;
    }
}
