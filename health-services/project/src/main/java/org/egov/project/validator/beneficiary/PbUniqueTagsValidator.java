package org.egov.project.validator.beneficiary;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.Error;
import org.egov.common.models.project.BeneficiaryBulkRequest;
import org.egov.common.models.project.ProjectBeneficiary;
import org.egov.common.validator.Validator;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.egov.common.utils.CommonUtils.notHavingErrors;
import static org.egov.project.validator.beneficiary.ValidatorUtils.validateUniqueTags;

/**
 * This class, PbUniqueEntityValidator, is a Spring component that serves as a validator for ensuring the uniqueness
 * of entities (ProjectBeneficiaries) within a BeneficiaryBulkRequest. It implements the Validator interface, which
 * allows it to validate the request by checking for duplicate entities based on their Voucher Tags.
 */
@Component
@Order(value = 2)
@Slf4j
public class PbUniqueTagsValidator implements Validator<BeneficiaryBulkRequest, ProjectBeneficiary> {

    /**
     * This method validates the uniqueness of entities within a BeneficiaryBulkRequest.
     *
     * @param request The BeneficiaryBulkRequest to validate.
     * @return A map containing error details for entities that are not unique.
     */
    @Override
    public Map<ProjectBeneficiary, List<Error>> validate(BeneficiaryBulkRequest request) {
        log.info("validating unique voucher tags");
        Map<ProjectBeneficiary, List<Error>> errorDetailsMap = new HashMap<>();
        List<ProjectBeneficiary> validProjectBeneficiaries = request.getProjectBeneficiaries()
                .stream().filter(notHavingErrors()).collect(Collectors.toList());

        if (!validProjectBeneficiaries.isEmpty()) {
            validateUniqueTags(validProjectBeneficiaries, errorDetailsMap);
        }
        return errorDetailsMap;
    }
}
