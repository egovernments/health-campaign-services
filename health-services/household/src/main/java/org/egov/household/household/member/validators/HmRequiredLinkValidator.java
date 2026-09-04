package org.egov.household.household.member.validators;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.egov.common.models.Error;
import org.egov.common.models.household.HouseholdMember;
import org.egov.common.models.household.HouseholdMemberBulkRequest;
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
 * Structural link validation for household members. With cross-entity EXISTENCE validation
 * unbundled (flags off), the client-reference links are the only keys that tie the offline-first
 * data chain together — so their PRESENCE must still be enforced even though the referenced
 * entity is allowed to not exist yet:
 *  - own clientReferenceId must be present (a null one is accepted by the API but violates the
 *    DB NOT NULL later, silently killing the whole persister batch),
 *  - a household link (householdId or householdClientReferenceId) must be present,
 *  - an individual link (individualId or individualClientReferenceId) must be present
 *    (records missing either link were silently dropped in the pipeline).
 * This is a structural check, not an existence check, so it is not covered by
 * household.member.relationship.validation. It is gated separately by
 * household.member.required.link.validation, which defaults to false because the old service
 * accepted link-less members and rejecting them now would silently discard those writes behind
 * the 202 the bulk endpoint has already returned.
 */
@Component
@Order(value = 1)
@Slf4j
public class HmRequiredLinkValidator implements Validator<HouseholdMemberBulkRequest, HouseholdMember> {

    private static final String ERROR_CODE = "REQUIRED_LINK_MISSING";

    @Override
    public Map<HouseholdMember, List<Error>> validate(HouseholdMemberBulkRequest request) {
        Map<HouseholdMember, List<Error>> errorDetailsMap = new HashMap<>();
        for (HouseholdMember member : request.getHouseholdMembers()) {
            List<String> missing = new ArrayList<>();
            if (StringUtils.isBlank(member.getClientReferenceId())) {
                missing.add("clientReferenceId");
            }
            if (StringUtils.isBlank(member.getHouseholdId())
                    && StringUtils.isBlank(member.getHouseholdClientReferenceId())) {
                missing.add("householdId/householdClientReferenceId");
            }
            if (StringUtils.isBlank(member.getIndividualId())
                    && StringUtils.isBlank(member.getIndividualClientReferenceId())) {
                missing.add("individualId/individualClientReferenceId");
            }
            if (!missing.isEmpty()) {
                String message = "Required link field(s) missing: " + String.join(", ", missing);
                Error error = Error.builder()
                        .errorMessage(message)
                        .errorCode(ERROR_CODE)
                        .type(Error.ErrorType.NON_RECOVERABLE)
                        .exception(new CustomException(ERROR_CODE, message))
                        .build();
                log.error("household member {} rejected: {}", member.getClientReferenceId(), message);
                populateErrorDetails(member, error, errorDetailsMap);
            }
        }
        return errorDetailsMap;
    }
}
