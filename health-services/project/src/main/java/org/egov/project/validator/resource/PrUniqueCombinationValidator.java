package org.egov.project.validator.resource;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.Error;
import org.egov.common.models.project.ProjectResource;
import org.egov.common.models.project.ProjectResourceBulkRequest;
import org.egov.common.validator.Validator;
import org.egov.project.repository.ProjectResourceRepository;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.egov.common.utils.CommonUtils.notHavingErrors;
import static org.egov.common.utils.CommonUtils.populateErrorDetails;
import static org.egov.common.utils.ValidatorUtils.getErrorForDuplicateMapping;
import static org.egov.project.Constants.PIPE;
import static org.egov.project.Constants.PROJECT_ID;


@Component
@Order(value = 5)
@Slf4j
public class PrUniqueCombinationValidator implements Validator<ProjectResourceBulkRequest, ProjectResource> {

    private ProjectResourceRepository projectResourceRepository;

    public PrUniqueCombinationValidator(ProjectResourceRepository projectResourceRepository) {
        this.projectResourceRepository = projectResourceRepository;
    }

    private Map<String, ProjectResource> getMap(List<ProjectResource> validEntities) {
        Map<String, ProjectResource> map = new HashMap<>();
        validEntities.forEach(entity -> {
            map.put(entity.getResource().getProductVariantId() + PIPE + entity.getProjectId(),entity);
        });
        return map;
    }

    private void validateProductVariantMappingFromRequest(List<ProjectResource> validEntities,
                                                           Map<ProjectResource, List<Error>> errorDetailsMap) {
        log.info("validating mapping from request");
        log.info("validating {} valid entities", validEntities.size());
        Map<String, ProjectResource> map = getMap(validEntities);
        if (map.keySet().size() != validEntities.size()) {
            List<String> duplicates = map.keySet().stream().filter(id ->
                    validEntities.stream().filter(entity -> {
                        String combinationId = entity.getResource().getProductVariantId() + PIPE + entity.getProjectId();
                        return combinationId.equals(id);
                    }).count() > 1).collect(Collectors.toList());
            for (String key : duplicates) {
                ProjectResource projectResource = map.get(key);
                Error error = getErrorForDuplicateMapping(projectResource.getProjectId(),
                        projectResource.getResource().getProductVariantId());
                populateErrorDetails(projectResource, error, errorDetailsMap);
            }
        }
    }

    private void validateProductVariantMappingFromDb(List<ProjectResource> validEntities,
                                                     Map<ProjectResource, List<Error>> errorDetailsMap) {

        log.info("validating mapping from db");
        log.info("validating {} valid entities", validEntities.size());
        List<String> projectIds = validEntities.stream().map(ProjectResource::getProjectId)
                .collect(Collectors.toList());
        List<ProjectResource> existingProjectResources = projectResourceRepository.findById(projectIds,
                false,  PROJECT_ID);

        Map<String, ProjectResource> existingIdMap = getMap(existingProjectResources);
        validEntities.stream().filter(entity -> {
            String combinationId = entity.getResource().getProductVariantId() + PIPE + entity.getProjectId();
            return existingIdMap.containsKey(combinationId)
                    && (entity.getId() == null || !entity.getId().equals(existingIdMap.get(combinationId).getId()));
        }).forEach(entity -> {
            Error error = getErrorForDuplicateMapping(entity.getProjectId(),
                    entity.getResource().getProductVariantId());
            populateErrorDetails(entity, error, errorDetailsMap);
        });
    }

    @Override
    public Map<ProjectResource, List<Error>> validate(ProjectResourceBulkRequest request) {
        Map<ProjectResource, List<Error>> errorDetailsMap = new HashMap<>();
        List<ProjectResource> validEntities = request.getProjectResource()
                .stream().filter(notHavingErrors()).collect(Collectors.toList());

        if (!validEntities.isEmpty()) {
            validateProductVariantMappingFromRequest(validEntities, errorDetailsMap);
            validEntities = validEntities.stream().filter(notHavingErrors()).collect(Collectors.toList());
            if (!validEntities.isEmpty()) {
                validateProductVariantMappingFromDb(validEntities, errorDetailsMap);
            }
        }

        return errorDetailsMap;
    }
}
