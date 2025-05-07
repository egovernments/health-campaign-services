package org.egov.project.validator.beneficiary;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.exception.InvalidTenantIdException;
import org.egov.common.models.Error;
import org.egov.common.models.project.BeneficiaryBulkRequest;
import org.egov.common.models.project.ProjectBeneficiary;
import org.egov.common.validator.Validator;
import org.egov.project.repository.ProjectBeneficiaryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.egov.common.utils.CommonUtils.getEntitiesWithMismatchedRowVersion;
import static org.egov.common.utils.CommonUtils.getIdFieldName;
import static org.egov.common.utils.CommonUtils.getIdMethod;
import static org.egov.common.utils.CommonUtils.getIdToObjMap;
import static org.egov.common.utils.CommonUtils.getTenantId;
import static org.egov.common.utils.CommonUtils.notHavingErrors;
import static org.egov.common.utils.CommonUtils.populateErrorDetails;
import static org.egov.common.utils.ValidatorUtils.getErrorForInvalidTenantId;
import static org.egov.common.utils.ValidatorUtils.getErrorForRowVersionMismatch;

@Component
@Order(value = 5)
@Slf4j
public class PbRowVersionValidator implements Validator<BeneficiaryBulkRequest, ProjectBeneficiary> {

    private final ProjectBeneficiaryRepository projectBeneficiaryRepository;

    @Autowired
    public PbRowVersionValidator(ProjectBeneficiaryRepository projectBeneficiaryRepository) {
        this.projectBeneficiaryRepository = projectBeneficiaryRepository;
    }


    @Override
    public Map<ProjectBeneficiary, List<Error>> validate(BeneficiaryBulkRequest request) {
        log.info("validating row version");
        Map<ProjectBeneficiary, List<Error>> errorDetailsMap = new HashMap<>();
        Method idMethod = getIdMethod(request.getProjectBeneficiaries());
        String tenantId = getTenantId(request.getProjectBeneficiaries());
        Map<String, ProjectBeneficiary> iMap = getIdToObjMap(request.getProjectBeneficiaries().stream()
                .filter(notHavingErrors())
                .collect(Collectors.toList()), idMethod);
        if (!iMap.isEmpty()) {
            List<String> individualIds = new ArrayList<>(iMap.keySet());
            try {
                List<ProjectBeneficiary> existingProjectBeneficiaries = projectBeneficiaryRepository.findById(tenantId, individualIds,
                        getIdFieldName(idMethod), false).getResponse();
                List<ProjectBeneficiary> entitiesWithMismatchedRowVersion =
                        getEntitiesWithMismatchedRowVersion(iMap, existingProjectBeneficiaries, idMethod);
                entitiesWithMismatchedRowVersion.forEach(projectBeneficiary -> {
                    Error error = getErrorForRowVersionMismatch();
                    populateErrorDetails(projectBeneficiary, error, errorDetailsMap);
                });
            } catch (InvalidTenantIdException exception) {
                // Populating InvalidTenantIdException for all entities
                request.getProjectBeneficiaries().forEach(projectBeneficiary -> {
                    Error error = getErrorForInvalidTenantId(tenantId, exception);
                    populateErrorDetails(projectBeneficiary, error, errorDetailsMap);
                });
            }
        }
        return errorDetailsMap;
    }
}
