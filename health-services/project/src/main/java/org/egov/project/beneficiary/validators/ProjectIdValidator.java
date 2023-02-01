package org.egov.project.beneficiary.validators;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.Error;
import org.egov.common.validator.Validator;
import org.egov.project.repository.ProjectRepository;
import org.egov.project.web.models.BeneficiaryBulkRequest;
import org.egov.project.web.models.ProjectBeneficiary;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.egov.common.utils.CommonUtils.getIdFieldName;
import static org.egov.common.utils.CommonUtils.getIdToObjMap;
import static org.egov.common.utils.CommonUtils.getMethod;
import static org.egov.common.utils.CommonUtils.getObjClass;
import static org.egov.common.utils.CommonUtils.notHavingErrors;
import static org.egov.common.utils.CommonUtils.populateErrorDetails;
import static org.egov.common.utils.ValidatorUtils.getErrorForNonExistentEntity;

@Component
@Order(value = 3)
@Slf4j
public class ProjectIdValidator implements Validator<BeneficiaryBulkRequest, ProjectBeneficiary> {

    private final ProjectRepository projectRepository;

    @Autowired
    public ProjectIdValidator(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }


    @Override
    public Map<ProjectBeneficiary, List<Error>> validate(BeneficiaryBulkRequest request) {
        Map<ProjectBeneficiary, List<Error>> errorDetailsMap = new HashMap<>();
        List<ProjectBeneficiary> entities = request.getProjectBeneficiaries();
        Class<?> objClass = getObjClass(entities);
        Method idMethod = getMethod("getProjectId", objClass);
        Map<String, ProjectBeneficiary> eMap = getIdToObjMap(entities
                .stream().filter(notHavingErrors()).collect(Collectors.toList()), idMethod);
        if (!eMap.isEmpty()) {
            List<String> entityIds = new ArrayList<>(eMap.keySet());
            List<String> existingProjectIds = projectRepository.validateIds(entityIds,
                    getIdFieldName(idMethod));
            List<ProjectBeneficiary> invalidEntities = eMap.values().stream().filter(entity ->
                    !existingProjectIds.contains(entity.getProjectId()))
                            .collect(Collectors.toList());
            invalidEntities.forEach(task -> {
                Error error = getErrorForNonExistentEntity();
                populateErrorDetails(task, error, errorDetailsMap);
            });
        }

        return errorDetailsMap;
    }
}