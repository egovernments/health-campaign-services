package org.egov.project.beneficiary.validators;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.Error;
import org.egov.common.validator.Validator;
import org.egov.project.web.models.BeneficiaryBulkRequest;
import org.egov.project.web.models.ProjectBeneficiary;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

import static org.egov.common.utils.CommonUtils.validateForNullId;
import static org.egov.project.Constants.GET_PROJECT_BENEFICIARIES;

@Component
@Order(value = 1)
@Slf4j
public class PbNullIdValidator implements Validator<BeneficiaryBulkRequest, ProjectBeneficiary> {

    @Override
    public Map<ProjectBeneficiary, List<Error>> validate(BeneficiaryBulkRequest request) {
        return validateForNullId(request, GET_PROJECT_BENEFICIARIES);
    }
}
