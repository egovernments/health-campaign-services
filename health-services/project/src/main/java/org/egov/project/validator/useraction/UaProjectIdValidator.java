package org.egov.project.validator.useraction;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.exception.InvalidTenantIdException;
import org.egov.common.models.Error;
import org.egov.common.models.project.useraction.UserAction;
import org.egov.common.models.project.useraction.UserActionBulkRequest;
import org.egov.common.validator.Validator;
import org.egov.project.repository.ProjectRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import static org.egov.common.utils.CommonUtils.getIdFieldName;
import static org.egov.common.utils.CommonUtils.getIdToObjMap;
import static org.egov.common.utils.CommonUtils.getMethod;
import static org.egov.common.utils.CommonUtils.getObjClass;
import static org.egov.common.utils.CommonUtils.getTenantId;
import static org.egov.common.utils.CommonUtils.notHavingErrors;
import static org.egov.common.utils.CommonUtils.populateErrorDetails;
import static org.egov.common.utils.ValidatorUtils.getErrorForInvalidTenantId;
import static org.egov.common.utils.ValidatorUtils.getErrorForNonExistentRelatedEntity;

/**
 * UaProjectIdValidator is responsible for validating the Project IDs in UserActionBulkRequest.
 * It checks if the Project IDs present in the UserAction entities exist in the Project repository.
 */
@Component
@Order(value = 6)
@Slf4j
public class UaProjectIdValidator implements Validator<UserActionBulkRequest, UserAction> {

    private ProjectRepository projectRepository;

    @Autowired
    public UaProjectIdValidator(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

    /**
     * Validates the Project IDs in the UserActionBulkRequest.
     * It checks if the Project IDs present in the UserAction entities exist in the Project repository.
     *
     * @param request the UserActionBulkRequest containing UserAction entities to be validated.
     * @return a map of UserAction entities to a list of Errors encountered during validation.
     */
    @Override
    public Map<UserAction, List<Error>> validate(UserActionBulkRequest request) {
        log.info("Starting validation of project IDs in UserActionBulkRequest with {} entities", request.getUserActions().size());

        Map<UserAction, List<Error>> errorDetailsMap = new HashMap<>();
        List<UserAction> entities = request.getUserActions();
        String tenantId = getTenantId(entities);

        // Retrieve the class of the UserAction entities
        Class<?> objClass = getObjClass(entities);
        log.debug("Retrieved UserAction entity class: {}", objClass.getName());

        // Retrieve the method to get ProjectId from UserAction entities
        Method idMethod = getMethod("getProjectId", objClass);
        log.debug("Retrieved getProjectId method from UserAction entity class");

        // Create a map of Project IDs to UserAction entities
        Map<String, UserAction> eMap = getIdToObjMap(entities.stream().filter(notHavingErrors()).collect(Collectors.toList()), idMethod);
        log.info("Created map of Project IDs to UserAction entities with {} entries", eMap.size());

        if (!eMap.isEmpty()) {
            List<String> entityIds = new ArrayList<>(eMap.keySet());
            log.debug("List of Project IDs to validate: {}", entityIds);

            try {
                // Validate the Project IDs by checking their existence in the Project repository
                List<String> existingProjectIds = projectRepository.validateIds(tenantId, entityIds, getIdFieldName(idMethod));
                log.info("Retrieved list of existing Project IDs from Project repository: {}", existingProjectIds);

                // Identify invalid UserAction entities with non-existent Project IDs
                List<UserAction> invalidEntities = entities.stream().filter(notHavingErrors()).filter(entity ->
                        !existingProjectIds.contains(entity.getProjectId())).collect(Collectors.toList());
                log.info("Identified {} invalid UserAction entities with non-existent Project IDs", invalidEntities.size());

                // Populate error details for invalid UserAction entities
                invalidEntities.forEach(userAction -> {
                    Error error = getErrorForNonExistentRelatedEntity(userAction.getProjectId());
                    populateErrorDetails(userAction, error, errorDetailsMap);
                    log.debug("Populated error details for UserAction with invalid Project ID: {}", userAction.getProjectId());
                });
            } catch (InvalidTenantIdException exception) {
                // Populating InvalidTenantIdException for all entities
                entities.forEach(userAction -> {
                    Error error = getErrorForInvalidTenantId(tenantId, exception);
                    populateErrorDetails(userAction, error, errorDetailsMap);
                });
            }
        } else {
            log.info("No Project IDs to validate as the map of Project IDs to UserAction entities is empty");
        }

        log.info("Completed validation of project IDs in UserActionBulkRequest");
        return errorDetailsMap;
    }
}
