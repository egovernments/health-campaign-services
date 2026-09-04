package org.egov.referralmanagement.validator;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.egov.common.models.Error;
import org.egov.common.models.referralmanagement.Referral;
import org.egov.common.models.referralmanagement.ReferralBulkRequest;
import org.egov.common.validator.Validator;
import org.egov.tracer.model.CustomException;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.egov.common.utils.CommonUtils.populateErrorDetails;

/**
 * Structural link validation for referrals. With existence validation unbundled (flags off), a
 * referral saved without any beneficiary link is an orphan that can never be traced back to a
 * person, and one without its own clientReferenceId is accepted by the API but violates the DB
 * NOT NULL later. Presence is enforced even though the referenced beneficiary is allowed to not
 * exist yet. Structural, not an existence check, so it is not covered by
 * referralmanagement.relationship.validation; it is gated separately by
 * referralmanagement.required.link.validation, which defaults to false because the old service
 * accepted standalone referrals and rejecting them now would silently discard those writes
 * behind the 202 the bulk endpoint has already returned.
 */
@Component
@Order(value = 1)
@Slf4j
public class RmRequiredLinkValidator implements Validator<ReferralBulkRequest, Referral> {

    private static final String ERROR_CODE = "REQUIRED_LINK_MISSING";

    @Override
    public Map<Referral, List<Error>> validate(ReferralBulkRequest request) {
        Map<Referral, List<Error>> errorDetailsMap = new HashMap<>();
        for (Referral referral : request.getReferrals()) {
            List<String> missing = new ArrayList<>();
            if (StringUtils.isBlank(referral.getClientReferenceId())) {
                missing.add("clientReferenceId");
            }
            if (StringUtils.isBlank(referral.getProjectBeneficiaryId())
                    && StringUtils.isBlank(referral.getProjectBeneficiaryClientReferenceId())) {
                missing.add("projectBeneficiaryId/projectBeneficiaryClientReferenceId");
            }
            if (!missing.isEmpty()) {
                String message = "Required link field(s) missing: " + String.join(", ", missing);
                Error error = Error.builder()
                        .errorMessage(message)
                        .errorCode(ERROR_CODE)
                        .type(Error.ErrorType.NON_RECOVERABLE)
                        .exception(new CustomException(ERROR_CODE, message))
                        .build();
                log.error("referral {} rejected: {}", referral.getClientReferenceId(), message);
                populateErrorDetails(referral, error, errorDetailsMap);
            }
        }
        return errorDetailsMap;
    }
}
