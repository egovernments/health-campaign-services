package org.egov.project.validator.staff;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.Error;
import org.egov.common.models.project.ProjectStaff;
import org.egov.common.models.project.ProjectStaffBulkRequest;
import org.egov.common.validator.Validator;
import org.egov.project.repository.ProjectStaffRepository;
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
public class PsUniqueCombinationValidator implements Validator<ProjectStaffBulkRequest, ProjectStaff> {

    private ProjectStaffRepository projectStaffRepository;

    public PsUniqueCombinationValidator(ProjectStaffRepository projectStaffRepository) {
        this.projectStaffRepository = projectStaffRepository;
    }

    private Map<String, ProjectStaff> getMap(List<ProjectStaff> validEntities) {
        Map<String, ProjectStaff> map = new HashMap<>();
        validEntities.forEach(entity -> {
            map.put(entity.getUserId() + PIPE + entity.getProjectId(),entity);
        });
        return map;
    }

    private void validateProductVariantMappingFromRequest(List<ProjectStaff> validEntities,
                                                          Map<ProjectStaff, List<Error>> errorDetailsMap) {
        log.info("validating mapping from request");
        log.info("validating {} valid entities", validEntities.size());
        Map<String, ProjectStaff> map = getMap(validEntities);
        if (map.keySet().size() != validEntities.size()) {
            List<String> duplicates = map.keySet().stream().filter(id ->
                    validEntities.stream().filter(entity -> {
                        String combinationId = entity.getUserId() + PIPE + entity.getProjectId();
                        return combinationId.equals(id);
                    }).count() > 1).collect(Collectors.toList());
            log.info("found {} duplicates in request", duplicates.size());
            for (String key : duplicates) {
                ProjectStaff projectStaff = map.get(key);
                Error error = getErrorForDuplicateMapping(projectStaff.getProjectId(),
                        projectStaff.getUserId());
                populateErrorDetails(projectStaff, error, errorDetailsMap);
            }
        }
    }

    private void validateProductVariantMappingFromDb(List<ProjectStaff> validEntities,
                                                     Map<ProjectStaff, List<Error>> errorDetailsMap) {

        log.info("validating mapping from db");
        log.info("validating {} valid entities", validEntities.size());
        List<String> projectIds = validEntities.stream().map(ProjectStaff::getProjectId)
                .collect(Collectors.toList());
        List<ProjectStaff> existingProjectResources = projectStaffRepository.findById(projectIds,
                false,  PROJECT_ID);

        Map<String, ProjectStaff> existingIdMap = getMap(existingProjectResources);
        validEntities.stream().filter(entity -> {
            String combinationId = entity.getUserId() + PIPE + entity.getProjectId();
            return existingIdMap.containsKey(combinationId)
                    && (entity.getId() == null || !entity.getId().equals(existingIdMap.get(combinationId).getId()));
        }).forEach(entity -> {
            Error error = getErrorForDuplicateMapping(entity.getProjectId(),
                    entity.getUserId());
            populateErrorDetails(entity, error, errorDetailsMap);
        });
    }

    @Override
    public Map<ProjectStaff, List<Error>> validate(ProjectStaffBulkRequest request) {
        Map<ProjectStaff, List<Error>> errorDetailsMap = new HashMap<>();
        List<ProjectStaff> validEntities = request.getProjectStaff()
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
