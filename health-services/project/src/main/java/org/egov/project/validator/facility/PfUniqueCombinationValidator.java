package org.egov.project.validator.facility;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.Error;
import org.egov.common.models.project.ProjectFacility;
import org.egov.common.models.project.ProjectFacilityBulkRequest;
import org.egov.common.validator.Validator;
import org.egov.project.repository.ProjectFacilityRepository;
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
@Order(value = 8)
@Slf4j
public class PfUniqueCombinationValidator implements Validator<ProjectFacilityBulkRequest, ProjectFacility> {

    private final ProjectFacilityRepository projectFacilityRepository;

    public PfUniqueCombinationValidator(ProjectFacilityRepository projectFacilityRepository) {
        this.projectFacilityRepository = projectFacilityRepository;
    }

    @Override
    public Map<ProjectFacility, List<Error>> validate(ProjectFacilityBulkRequest request) {
        log.info("validating for project facility mapping uniqueness");
        Map<ProjectFacility, List<Error>> errorDetailsMap = new HashMap<>();

        List<ProjectFacility> validEntities = request.getProjectFacilities().stream()
                .filter(notHavingErrors())
                .collect(Collectors.toList());
        if (!validEntities.isEmpty()) {
            validateProjectFacilityMappingFromRequest(validEntities, errorDetailsMap);
            validEntities = request.getProjectFacilities().stream()
                    .filter(notHavingErrors())
                    .collect(Collectors.toList());
            if (!validEntities.isEmpty()) {
                validateProjectFacilityMappingFromDb(validEntities, errorDetailsMap);
            }
        }

        return errorDetailsMap;
    }

    private void validateProjectFacilityMappingFromDb(List<ProjectFacility> validEntities,
                                                      Map<ProjectFacility, List<Error>> errorDetailsMap) {
        log.info("validating mapping from db");
        log.info("validating {} valid entities", validEntities.size());
        List<String> projectIds = validEntities.stream().map(ProjectFacility::getProjectId)
                .collect(Collectors.toList());

        List<ProjectFacility> existingProjectFacilities = projectFacilityRepository.findById(projectIds,
                false, PROJECT_ID);

        Map<String, ProjectFacility> existingIdMap = getMap(existingProjectFacilities);
        validEntities.stream().filter(entity -> {
            String combinationId = entity.getFacilityId() + PIPE + entity.getProjectId();
            return existingIdMap.containsKey(combinationId)
                    && (entity.getId() == null || !entity.getId().equals(existingIdMap.get(combinationId).getId()));
        }).forEach(entity -> {
            Error error = getErrorForDuplicateMapping(entity.getProjectId(),
                    entity.getFacilityId());
            populateErrorDetails(entity, error, errorDetailsMap);
        });

    }

    private void validateProjectFacilityMappingFromRequest(List<ProjectFacility> validEntities,
                                                           Map<ProjectFacility, List<Error>> errorDetailsMap) {
        log.info("validating mapping from request");
        log.info("validating {} valid entities", validEntities.size());
        Map<String, ProjectFacility> map = getMap(validEntities);
        if (map.keySet().size() != validEntities.size()) {
            List<String> duplicates = map.keySet().stream().filter(id ->
                    validEntities.stream().filter(entity -> {
                                String combinationId = entity.getFacilityId() + PIPE + entity.getProjectId();
                                return combinationId.equals(id);
                    }).count() > 1).collect(Collectors.toList());
            for (String key : duplicates) {
                ProjectFacility projectFacility = map.get(key);
                Error error = getErrorForDuplicateMapping(projectFacility.getProjectId(),
                        projectFacility.getFacilityId());
                populateErrorDetails(projectFacility, error, errorDetailsMap);
            }
        }
    }

    private Map<String, ProjectFacility> getMap(List<ProjectFacility> validEntities) {
        Map<String, ProjectFacility> map = new HashMap<>();
        validEntities.forEach(entity -> map.put(entity.getFacilityId() + PIPE + entity.getProjectId(),entity));
        return map;
    }
}
