package org.egov.project.validator.beneficiary;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.Error;
import org.egov.common.models.project.BeneficiaryBulkRequest;
import org.egov.common.models.project.ProjectBeneficiary;
import org.egov.common.validator.Validator;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.egov.common.utils.CommonUtils.getIdToObjMap;
import static org.egov.common.utils.CommonUtils.notHavingErrors;
import static org.egov.common.utils.CommonUtils.populateErrorDetails;
import static org.egov.common.utils.ValidatorUtils.getErrorForUniqueEntity;

/**
 * This class, PbUniqueEntityValidator, is a Spring component that serves as a validator for ensuring the uniqueness
 * of entities (ProjectBeneficiaries) within a BeneficiaryBulkRequest. It implements the Validator interface, which
 * allows it to validate the request by checking for duplicate entities based on their IDs.
 */
@Component
@Order(value = 2)
@Slf4j
public class PbUniqueEntityValidator implements Validator<BeneficiaryBulkRequest, ProjectBeneficiary> {

    /**
     * This method validates the uniqueness of entities within a BeneficiaryBulkRequest.
     *
     * @param request The BeneficiaryBulkRequest to validate.
     * @return A map containing error details for entities that are not unique.
     */
    @Override
    public Map<ProjectBeneficiary, List<Error>> validate(BeneficiaryBulkRequest request) {
        log.info("validating unique entity");
        Map<ProjectBeneficiary, List<Error>> errorDetailsMap = new HashMap<>();
        List<ProjectBeneficiary> validProjectBeneficiaries = request.getProjectBeneficiaries()
                        .stream().filter(notHavingErrors()).collect(Collectors.toList());

        if (!validProjectBeneficiaries.isEmpty()) {

            List<String> duplicates = new ArrayList<>();
            Map<String, ProjectBeneficiary> iMap = getIdToObjMap(validProjectBeneficiaries);

            if (iMap.keySet().size() != validProjectBeneficiaries.size()) {
                // Find duplicate entities by comparing their IDs
                duplicates = iMap.keySet().stream().filter(id ->
                        validProjectBeneficiaries.stream()
                                .filter(projectBeneficiary -> projectBeneficiary.getId().equals(id)).count() > 1
                ).collect(Collectors.toList());

                // Populate error details for duplicate entities
                for (String key : duplicates) {
                    Error error = getErrorForUniqueEntity();
                    populateErrorDetails(iMap.get(key), error, errorDetailsMap);
                }
            }
        }
        return errorDetailsMap;
    }


}
