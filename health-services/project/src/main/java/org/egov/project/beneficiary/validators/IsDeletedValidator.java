package org.egov.project.beneficiary.validators;

import org.egov.common.models.Error;
import org.egov.common.validator.Validator;
import org.egov.project.web.models.BeneficiaryBulkRequest;
import org.egov.project.web.models.ProjectBeneficiary;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.egov.common.utils.CommonUtils.populateErrorDetails;
import static org.egov.common.utils.ValidatorUtils.getErrorForIsDelete;

@Component
@Order(2)
public class IsDeletedValidator implements Validator<BeneficiaryBulkRequest, ProjectBeneficiary> {

    @Override
    public Map<ProjectBeneficiary, List<Error>> validate(BeneficiaryBulkRequest request) {
        HashMap<ProjectBeneficiary, List<Error>> errorDetailsMap = new HashMap<>();
        List<ProjectBeneficiary> validProjectBeneficiaries = request.getProjectBeneficiaries();
        validProjectBeneficiaries.stream().filter(ProjectBeneficiary::getIsDeleted).forEach(projectBeneficiary -> {
            Error error = getErrorForIsDelete();
            populateErrorDetails(projectBeneficiary, error, errorDetailsMap);
        });
        return errorDetailsMap;
    }
}
