package org.egov.project.validator.resource;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.exception.InvalidTenantIdException;
import org.egov.common.models.Error;
import org.egov.common.models.project.ProjectResource;
import org.egov.common.models.project.ProjectResourceBulkRequest;
import org.egov.common.validator.Validator;
import org.egov.project.repository.ProjectResourceRepository;
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
public class PrRowVersionValidator implements Validator<ProjectResourceBulkRequest, ProjectResource> {
    private final ProjectResourceRepository projectResourceRepository;

    @Autowired
    public PrRowVersionValidator(ProjectResourceRepository projectResourceRepository) {
        this.projectResourceRepository = projectResourceRepository;
    }

    @Override
    public Map<ProjectResource, List<Error>> validate(ProjectResourceBulkRequest request) {
        log.info("validating row version");
        Map<ProjectResource, List<Error>> errorDetailsMap = new HashMap<>();
        Method idMethod = getIdMethod(request.getProjectResource());
        String tenantId = getTenantId(request.getProjectResource());
        Map<String, ProjectResource> eMap = getIdToObjMap(request.getProjectResource().stream()
                .filter(notHavingErrors())
                .collect(Collectors.toList()), idMethod);
        if (!eMap.isEmpty()) {
            List<String> entityIds = new ArrayList<>(eMap.keySet());
            try {
                List<ProjectResource> existingEntities = projectResourceRepository
                        .findById(tenantId, entityIds, false, getIdFieldName(idMethod));
                List<ProjectResource> entitiesWithMismatchedRowVersion =
                        getEntitiesWithMismatchedRowVersion(eMap, existingEntities, idMethod);
                entitiesWithMismatchedRowVersion.forEach(individual -> {
                    Error error = getErrorForRowVersionMismatch();
                    populateErrorDetails(individual, error, errorDetailsMap);
                });
            } catch (InvalidTenantIdException exception) {
                // Populating InvalidTenantIdException for all entities
                request.getProjectResource().forEach(projectResource -> {
                    Error error = getErrorForInvalidTenantId(tenantId, exception);
                    populateErrorDetails(projectResource, error, errorDetailsMap);
                });
            }
        }
        return errorDetailsMap;
    }
}
