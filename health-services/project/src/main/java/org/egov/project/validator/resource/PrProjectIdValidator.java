package org.egov.project.validator.resource;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.Error;
import org.egov.common.models.project.ProjectResource;
import org.egov.common.models.project.ProjectResourceBulkRequest;
import org.egov.common.validator.Validator;
import org.egov.project.repository.ProjectRepository;
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
import static org.egov.common.utils.ValidatorUtils.getErrorForNonExistentRelatedEntity;

@Component
@Order(value = 6)
@Slf4j
public class PrProjectIdValidator implements Validator<ProjectResourceBulkRequest, ProjectResource> {

    private final ProjectRepository projectRepository;

    @Autowired
    public PrProjectIdValidator(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

    @Override
    public Map<ProjectResource, List<Error>> validate(ProjectResourceBulkRequest request) {
        Map<ProjectResource, List<Error>> errorDetailsMap = new HashMap<>();
        List<ProjectResource> entities = request.getProjectResource();
        Class<?> objClass = getObjClass(entities);
        Method idMethod = getMethod("getProjectId", objClass);
        Map<String, ProjectResource> eMap = getIdToObjMap(entities
                .stream().filter(notHavingErrors()).collect(Collectors.toList()), idMethod);
        if (!eMap.isEmpty()) {
            List<String> entityIds = new ArrayList<>(eMap.keySet());
            List<String> existingProjectIds = projectRepository.validateIds(entityIds,
                    getIdFieldName(idMethod));
            List<ProjectResource> invalidEntities = entities.stream().filter(notHavingErrors()).filter(entity ->
                            !existingProjectIds.contains(entity.getProjectId()))
                    .collect(Collectors.toList());
            invalidEntities.forEach(projectResource -> {
                Error error = getErrorForNonExistentRelatedEntity(projectResource.getProjectId());
                populateErrorDetails(projectResource, error, errorDetailsMap);
            });
        }
        return errorDetailsMap;
    }

}
