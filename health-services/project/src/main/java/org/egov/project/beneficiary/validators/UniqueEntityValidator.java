package org.egov.project.beneficiary.validators;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.Error;
import org.egov.common.validator.Validator;
import org.egov.project.web.models.BeneficiaryBulkRequest;
import org.egov.project.web.models.ProjectBeneficiary;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.egov.common.utils.CommonUtils.getIdToObjMap;
import static org.egov.common.utils.CommonUtils.notHavingErrors;
import static org.egov.common.utils.CommonUtils.populateErrorDetails;
import static org.egov.common.utils.ValidatorUtils.getErrorForUniqueEntity;

@Component
@Order(value = 2)
@Slf4j
public class UniqueEntityValidator implements Validator<BeneficiaryBulkRequest, ProjectBeneficiary> {

    @Override
    public Map<ProjectBeneficiary, List<Error>> validate(BeneficiaryBulkRequest request) {
        Map<ProjectBeneficiary, List<Error>> errorDetailsMap = new HashMap<>();
        List<ProjectBeneficiary> validProjectBeneficiaries = request.getProjectBeneficiaries()
                        .stream().filter(notHavingErrors()).collect(Collectors.toList());
        if (!validProjectBeneficiaries.isEmpty()) {
            Map<String, ProjectBeneficiary> iMap = getIdToObjMap(validProjectBeneficiaries);
            if (iMap.keySet().size() != validProjectBeneficiaries.size()) {
                List<String> duplicates = iMap.keySet().stream().filter(id ->
                        validProjectBeneficiaries.stream()
                                .filter(projectBeneficiary -> projectBeneficiary.getId().equals(id)).count() > 1
                ).collect(Collectors.toList());
                for (String key : duplicates) {
                    Error error = getErrorForUniqueEntity();
                    populateErrorDetails(iMap.get(key), error, errorDetailsMap);
                }
            }
        }
        return errorDetailsMap;
    }
}
