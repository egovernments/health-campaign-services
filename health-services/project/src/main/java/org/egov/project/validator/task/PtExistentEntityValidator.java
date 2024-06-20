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

import static org.egov.common.utils.CommonUtils.getIdFieldName;
import static org.egov.common.utils.CommonUtils.notHavingErrors;
import static org.egov.common.utils.CommonUtils.populateErrorDetails;
import static org.egov.common.utils.ValidatorUtils.getErrorForUniqueEntity;

/**
 * Validator class for checking the existence of entities with the given client reference IDs.
 * This validator checks if the provided Task entities already exist in the database based on their client reference IDs.
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
     * @param projectTaskRepository The repository for project task entities.
     */
    public PtExistentEntityValidator(ProjectTaskRepository projectTaskRepository) {
        this.projectTaskRepository = projectTaskRepository;
    }

    /**
     * Validates the existence of entities with the given client reference IDs.
     *
     * @param request The bulk request containing Task entities.
     * @return A map containing Task entities and their associated error details.
     */
    @Override
    public Map<Task, List<Error>> validate(TaskBulkRequest request) {
        // Map to hold Task entities and their error details
        Map<Task, List<Error>> errorDetailsMap = new HashMap<>();
        // Get the list of Task entities from the request
        List<Task> entities = request.getTasks();
        // Extract client reference IDs from Task entities without errors
        List<String> clientReferenceIdList = entities.stream()
                .filter(notHavingErrors())
                .map(Task::getClientReferenceId)
                .collect(Collectors.toList());
        // Create a search object for querying entities by client reference IDs
        TaskSearch taskSearch = TaskSearch.builder()
                .clientReferenceId(clientReferenceIdList)
                .build();
        // Check if the client reference ID list is not empty
        if (!CollectionUtils.isEmpty(clientReferenceIdList)) {
            // Query the repository to find existing entities by client reference IDs
            List<Task> existentEntities = projectTaskRepository.findById(
                    clientReferenceIdList,
                    getIdFieldName(taskSearch),
                    Boolean.FALSE).getResponse();
            // For each existing entity, populate error details for uniqueness
            existentEntities.forEach(entity -> {
                Error error = getErrorForUniqueEntity();
                populateErrorDetails(entity, error, errorDetailsMap);
            });
        }
        return errorDetailsMap;
    }
}
