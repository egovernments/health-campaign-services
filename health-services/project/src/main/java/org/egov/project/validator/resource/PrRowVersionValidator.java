package org.egov.project.validator.resource;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.Error;
import org.egov.common.validator.Validator;
import org.egov.project.repository.ProjectResourceRepository;
import org.egov.project.web.models.ProjectResource;
import org.egov.project.web.models.ProjectResourceBulkRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.egov.common.utils.CommonUtils.*;
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
        Map<String, ProjectResource> eMap = getIdToObjMap(request.getProjectResource().stream()
                .filter(notHavingErrors())
                .collect(Collectors.toList()), idMethod);
        if (!eMap.isEmpty()) {
            List<String> entityIds = new ArrayList<>(eMap.keySet());
            List<ProjectResource> existingEntities = projectResourceRepository
                    .findById(entityIds, false, getIdFieldName(idMethod));
            List<ProjectResource> entitiesWithMismatchedRowVersion =
                    getEntitiesWithMismatchedRowVersion(eMap, existingEntities, idMethod);
            entitiesWithMismatchedRowVersion.forEach(individual -> {
                Error error = getErrorForRowVersionMismatch();
                populateErrorDetails(individual, error, errorDetailsMap);
            });
        }
        return errorDetailsMap;
    }
}
