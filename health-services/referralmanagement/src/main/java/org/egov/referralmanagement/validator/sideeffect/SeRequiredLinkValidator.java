package org.egov.referralmanagement.validator.sideeffect;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.egov.common.models.Error;
import org.egov.common.models.referralmanagement.sideeffect.SideEffect;
import org.egov.common.models.referralmanagement.sideeffect.SideEffectBulkRequest;
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
 * Structural link validation for side-effects. With existence validation unbundled (flags off),
 * a side-effect saved without any task link is an orphan that can never be traced back to a
 * delivery/beneficiary, and one without its own clientReferenceId is accepted by the API but
 * violates the DB NOT NULL later. Presence of the parent-task link (taskId or
 * taskClientReferenceId) is enforced even though the referenced task is allowed to not exist yet.
 * Structural, not an existence check, so it is not covered by
 * referralmanagement.relationship.validation; it is gated separately by the same
 * referralmanagement.required.link.validation flag as RmRequiredLinkValidator, which defaults to
 * false because the old service accepted standalone side-effects and rejecting them now would
 * silently discard those writes behind the 202 the bulk endpoint has already returned.
 * Mirrors Hm/Pt/Pb/RmRequiredLinkValidator.
 */
@Component
@Order(value = 1)
@Slf4j
public class SeRequiredLinkValidator implements Validator<SideEffectBulkRequest, SideEffect> {

    private static final String ERROR_CODE = "REQUIRED_LINK_MISSING";

    @Override
    public Map<SideEffect, List<Error>> validate(SideEffectBulkRequest request) {
        Map<SideEffect, List<Error>> errorDetailsMap = new HashMap<>();
        for (SideEffect sideEffect : request.getSideEffects()) {
            List<String> missing = new ArrayList<>();
            if (StringUtils.isBlank(sideEffect.getClientReferenceId())) {
                missing.add("clientReferenceId");
            }
            if (StringUtils.isBlank(sideEffect.getTaskId())
                    && StringUtils.isBlank(sideEffect.getTaskClientReferenceId())) {
                missing.add("taskId/taskClientReferenceId");
            }
            if (!missing.isEmpty()) {
                String message = "Required link field(s) missing: " + String.join(", ", missing);
                Error error = Error.builder()
                        .errorMessage(message)
                        .errorCode(ERROR_CODE)
                        .type(Error.ErrorType.NON_RECOVERABLE)
                        .exception(new CustomException(ERROR_CODE, message))
                        .build();
                log.error("side-effect {} rejected: {}", sideEffect.getClientReferenceId(), message);
                populateErrorDetails(sideEffect, error, errorDetailsMap);
            }
        }
        return errorDetailsMap;
    }
}
