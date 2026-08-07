package org.egov.household.household.member.validators;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.egov.common.models.Error;
import org.egov.common.models.household.HouseholdMember;
import org.egov.common.models.household.HouseholdMemberBulkRequest;
import org.egov.common.models.household.Relationship;
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
 *    (records missing either link were silently dropped in the pipeline),
 *  - each relationship must identify its relative by server ID or client reference ID.
 * Always on: this is a structural check, not an existence check.
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
            List<Relationship> relationships = member.getMemberRelationships();
            if (relationships != null) {
                for (int index = 0; index < relationships.size(); index++) {
                    Relationship relationship = relationships.get(index);
                    if (relationship == null
                            || (StringUtils.isBlank(relationship.getRelativeId())
                            && StringUtils.isBlank(relationship.getRelativeClientReferenceId()))) {
                        missing.add("memberRelationships[" + index
                                + "].relativeId/relativeClientReferenceId");
                    }
                }
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
