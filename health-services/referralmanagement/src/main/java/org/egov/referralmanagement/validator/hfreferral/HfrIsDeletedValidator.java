package org.egov.referralmanagement.validator.hfreferral;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.Error;
import org.egov.common.models.referralmanagement.hfreferral.HFReferral;
import org.egov.common.models.referralmanagement.hfreferral.HFReferralBulkRequest;
import org.egov.common.validator.Validator;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import static org.egov.common.utils.CommonUtils.populateErrorDetails;
import static org.egov.common.utils.ValidatorUtils.getErrorForIsDelete;

/**
 * Validator for checking the 'isDeleted' field in HFReferral entities.
 *
 * Author: kanishq-egov
 */
@Component
@Order(2)
@Slf4j
public class HfrIsDeletedValidator implements Validator<HFReferralBulkRequest, HFReferral> {

    /**
     * Validates the 'isDeleted' field for each HFReferral entity in the bulk request.
     *
     * @param request The HFReferralBulkRequest containing a list of HFReferral entities
     * @return A Map containing HFReferral entities as keys and lists of errors as values
     */
    @Override
    public Map<HFReferral, List<Error>> validate(HFReferralBulkRequest request) {
        log.info("validating isDeleted field");
        HashMap<HFReferral, List<Error>> errorDetailsMap = new HashMap<>();
        List<HFReferral> validEntities = request.getHfReferrals();
        validEntities.stream().filter(HFReferral::getIsDeleted).forEach(hfReferral -> {
            Error error = getErrorForIsDelete();
            populateErrorDetails(hfReferral, error, errorDetailsMap);
        });
        return errorDetailsMap;
    }
}
