package org.egov.project.validator.beneficiary;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.egov.common.models.Error;
import org.egov.common.models.project.BeneficiaryBulkRequest;
import org.egov.common.models.project.ProjectBeneficiary;
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
 * Structural link validation for project beneficiaries. With existence validation unbundled
 * (flags off), an enrollment saved without any beneficiary (household/individual) link is an
 * orphan that can never be traced back, and one without its own clientReferenceId is accepted
 * by the API but violates the DB NOT NULL later. Presence is enforced even though the referenced
 * entity is allowed to not exist yet. Structural, not an existence check, so it is not covered
 * by project.relationship.validation; it is gated separately by
 * project.required.link.validation, which defaults to false because the old service accepted
 * link-less enrollments and rejecting them now would silently discard those writes behind the
 * 202 the bulk endpoint has already returned.
 */
@Component
@Order(value = 1)
@Slf4j
public class PbRequiredLinkValidator implements Validator<BeneficiaryBulkRequest, ProjectBeneficiary> {

    private static final String ERROR_CODE = "REQUIRED_LINK_MISSING";

    @Override
    public Map<ProjectBeneficiary, List<Error>> validate(BeneficiaryBulkRequest request) {
        Map<ProjectBeneficiary, List<Error>> errorDetailsMap = new HashMap<>();
        for (ProjectBeneficiary beneficiary : request.getProjectBeneficiaries()) {
            List<String> missing = new ArrayList<>();
            if (StringUtils.isBlank(beneficiary.getClientReferenceId())) {
                missing.add("clientReferenceId");
            }
            if (StringUtils.isBlank(beneficiary.getBeneficiaryId())
                    && StringUtils.isBlank(beneficiary.getBeneficiaryClientReferenceId())) {
                missing.add("beneficiaryId/beneficiaryClientReferenceId");
            }
            if (!missing.isEmpty()) {
                String message = "Required link field(s) missing: " + String.join(", ", missing);
                Error error = Error.builder()
                        .errorMessage(message)
                        .errorCode(ERROR_CODE)
                        .type(Error.ErrorType.NON_RECOVERABLE)
                        .exception(new CustomException(ERROR_CODE, message))
                        .build();
                log.error("project beneficiary {} rejected: {}", beneficiary.getClientReferenceId(), message);
                populateErrorDetails(beneficiary, error, errorDetailsMap);
            }
        }
        return errorDetailsMap;
    }
}
