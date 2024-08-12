package org.egov.project.validator.task;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.Error;
import org.egov.common.models.project.Task;
import org.egov.common.models.project.TaskBulkRequest;
import org.egov.common.models.project.TaskSearch;
import org.egov.common.validator.Validator;
import org.egov.project.repository.ProjectTaskRepository;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import static org.egov.common.utils.CommonUtils.notHavingErrors;
import static org.egov.common.utils.CommonUtils.populateErrorDetails;
import static org.egov.common.utils.ValidatorUtils.getErrorForUniqueEntity;

/**
 * Validator class for checking the existence of ProjectTask entities with the given client reference IDs.
 * This validator ensures that the Task entities provided in the bulk request do not have duplicate client reference IDs in the database.
 *
 * The validation checks if each Task entity's client reference ID is unique across the database,
 * and if a duplicate ID is found, it adds an error to the map with the entity.
 *
 * @author: kanishq-egov
 */
@Component
@Order(value = 1)
@Slf4j
public class PtExistentEntityValidator implements Validator<TaskBulkRequest, Task> {

    private final ProjectTaskRepository projectTaskRepository;

    /**
     * Constructor to initialize the ProjectTaskRepository dependency.
     *
     * @param projectTaskRepository The repository for ProjectTask entities.
     *                              This repository is used to query the database and check if entities with the given client reference IDs exist.
     */
    public PtExistentEntityValidator(ProjectTaskRepository projectTaskRepository) {
        this.projectTaskRepository = projectTaskRepository;
    }

    /**
     * Validates the existence of Task entities with the given client reference IDs.
     *
     * This method checks if any of the Task entities in the provided bulk request already exist in the database based on their client reference IDs.
     * If an entity with a client reference ID already exists, an error is added to the map for that entity.
     *
     * @param request The bulk request containing Task entities to be validated.
     * @return A map where the key is a Task entity and the value is a list of associated Error details.
     *         The map contains entries only for entities with errors (i.e., those whose client reference IDs already exist in the database).
     */
    @Override
    public Map<Task, List<Error>> validate(TaskBulkRequest request) {
        // Initialize a map to store Task entities and their associated error details.
        Map<Task, List<Error>> errorDetailsMap = new HashMap<>();

        // Extract the list of Task entities from the request.
        List<Task> entities = request.getTasks();

        // Extract client reference IDs from Task entities that do not have existing errors.
        List<String> clientReferenceIdList = entities.stream()
                .filter(notHavingErrors()) // Filter out entities that already have errors.
                .map(Task::getClientReferenceId) // Map to extract client reference IDs.
                .collect(Collectors.toList()); // Collect the IDs into a list.

        // Create a map for quick lookup of Task entities by client reference ID.
        Map<String, Task> map = entities.stream()
                .filter(entity -> StringUtils.hasText(entity.getClientReferenceId())) // Ensure client reference ID is not empty.
                .collect(Collectors.toMap(entity -> entity.getClientReferenceId(), entity -> entity)); // Collect to a map.

        // Create a search object to query for existing entities based on client reference IDs.
        TaskSearch taskSearch = TaskSearch.builder()
                .clientReferenceId(clientReferenceIdList) // Set the client reference IDs for the search.
                .build();

        // Check if the client reference ID list is not empty before querying the database.
        if (!CollectionUtils.isEmpty(clientReferenceIdList)) {
            // Query the repository to find existing entities with the given client reference IDs.
            List<String> existingClientReferenceIds = projectTaskRepository.validateClientReferenceIdsFromDB(clientReferenceIdList, Boolean.TRUE);

            // For each existing client reference ID, add an error to the map for the corresponding Task entity.
            existingClientReferenceIds.forEach(clientReferenceId -> {
                // Get a predefined error object for unique entity validation.
                Error error = getErrorForUniqueEntity();
                // Populate error details for the individual Task entity associated with the client reference ID.
                populateErrorDetails(map.get(clientReferenceId), error, errorDetailsMap);
            });
        }

        // Return the map containing Task entities and their associated error details.
        return errorDetailsMap;
    }
}
