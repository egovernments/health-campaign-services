package org.egov.project.validator.useraction;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
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
import static org.egov.common.utils.CommonUtils.notHavingErrors;
import static org.egov.common.utils.CommonUtils.populateErrorDetails;
import static org.egov.common.utils.ValidatorUtils.getErrorForNonExistentRelatedEntity;

/**
 * UaProjectIdValidator is responsible for validating Project IDs within UserAction entities contained
 * in a UserActionBulkRequest. This validator ensures that the Project IDs in UserAction entities exist
 * in the Project repository.
 */
@Component
@Order(value = 6)
@Slf4j
public class UaProjectIdValidator implements Validator<UserActionBulkRequest, UserAction> {

    private final ProjectRepository projectRepository;

    /**
     * Constructs an UaProjectIdValidator with the specified ProjectRepository.
     *
     * @param projectRepository the repository used to validate Project IDs.
     */
    @Autowired
    public UaProjectIdValidator(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

    /**
     * Validates the Project IDs in the UserActionBulkRequest.
     * It checks if the Project IDs present in the UserAction entities exist in the Project repository.
     *
     * @param request the UserActionBulkRequest containing UserAction entities to be validated.
     * @return a map where keys are UserAction entities and values are lists of Errors encountered during validation.
     */
    @Override
    public Map<UserAction, List<Error>> validate(UserActionBulkRequest request) {
        log.info("Starting validation of project IDs in UserActionBulkRequest with {} entities", request.getUserActions().size());

        // Create a map to hold error details for each UserAction entity
        Map<UserAction, List<Error>> errorDetailsMap = new HashMap<>();
        List<UserAction> entities = request.getUserActions();

        // Retrieve the class type of the UserAction entities
        Class<?> objClass = getObjClass(entities);
        log.debug("Retrieved UserAction entity class: {}", objClass.getName());

        // Retrieve the method that gets the Project ID from a UserAction entity
        Method idMethod = getMethod("getProjectId", objClass);
        log.debug("Retrieved getProjectId method from UserAction entity class");

        // Create a map of Project IDs to UserAction entities
        Map<String, UserAction> eMap = getIdToObjMap(
                entities.stream()
                        .filter(notHavingErrors()) // Filter out entities that already have errors
                        .collect(Collectors.toList()), // Collect to a list
                idMethod
        );
        log.info("Created map of Project IDs to UserAction entities with {} entries", eMap.size());

        // Proceed if there are Project IDs to validate
        if (!eMap.isEmpty()) {
            List<String> entityIds = new ArrayList<>(eMap.keySet()); // List of Project IDs
            log.debug("List of Project IDs to validate: {}", entityIds);

            // Validate the Project IDs by checking their existence in the Project repository
            List<String> existingProjectIds = projectRepository.validateIds(entityIds, getIdFieldName(idMethod));
            log.info("Retrieved list of existing Project IDs from Project repository: {}", existingProjectIds);

            // Identify UserAction entities with non-existent Project IDs
            List<UserAction> invalidEntities = entities.stream()
                    .filter(notHavingErrors()) // Filter out entities that already have errors
                    .filter(entity -> !existingProjectIds.contains(entity.getProjectId())) // Find entities with invalid Project IDs
                    .collect(Collectors.toList());
            log.info("Identified {} invalid UserAction entities with non-existent Project IDs", invalidEntities.size());

            // Populate error details for UserAction entities with invalid Project IDs
            invalidEntities.forEach(userAction -> {
                Error error = getErrorForNonExistentRelatedEntity(userAction.getProjectId());
                populateErrorDetails(userAction, error, errorDetailsMap);
                log.debug("Populated error details for UserAction with invalid Project ID: {}", userAction.getProjectId());
            });
        } else {
            log.info("No Project IDs to validate as the map of Project IDs to UserAction entities is empty");
        }

        log.info("Completed validation of project IDs in UserActionBulkRequest");
        return errorDetailsMap;
    }
}
