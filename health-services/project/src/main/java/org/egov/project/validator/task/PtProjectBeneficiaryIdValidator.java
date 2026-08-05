package org.egov.project.validator.task;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.egov.common.exception.InvalidTenantIdException;
import org.egov.common.models.Error;
import org.egov.common.models.project.Task;
import org.egov.common.models.project.TaskBulkRequest;
import org.egov.common.validator.Validator;
import org.egov.project.config.ProjectConfiguration;
import org.egov.project.repository.ProjectBeneficiaryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.egov.common.utils.CommonUtils.getTenantId;
import static org.egov.common.utils.CommonUtils.notHavingErrors;
import static org.egov.common.utils.CommonUtils.populateErrorDetails;
import static org.egov.common.utils.ValidatorUtils.getErrorForInvalidRelatedEntityID;
import static org.egov.common.utils.ValidatorUtils.getErrorForInvalidTenantId;
import static org.egov.common.utils.ValidatorUtils.getErrorForNonExistentRelatedEntity;

@Component
@Order(value = 7)
@Slf4j
public class PtProjectBeneficiaryIdValidator implements Validator<TaskBulkRequest, Task> {

    private final ProjectBeneficiaryRepository projectBeneficiaryRepository;

    private final ProjectConfiguration projectConfiguration;

    @Autowired
    public PtProjectBeneficiaryIdValidator(ProjectBeneficiaryRepository projectBeneficiaryRepository,
                                           ProjectConfiguration projectConfiguration) {
        this.projectBeneficiaryRepository = projectBeneficiaryRepository;
        this.projectConfiguration = projectConfiguration;
    }


    @Override
    public Map<Task, List<Error>> validate(TaskBulkRequest request) {
        log.info("validating for project beneficiary id");
        Map<Task, List<Error>> errorDetailsMap = new HashMap<>();
        List<Task> entities = request.getTasks();
        // A task legitimately carries only ONE of the two parent keys - accepting a delivery whose
        // beneficiary server id is not known yet is the whole point of the unbundle. The previous
        // shape compared the size of a batch-sampled id-to-object map against entities.size() and then
        // flagged every task whose SAMPLED accessor returned null, so a batch mixing the two key shapes
        // had its clientReferenceId-only tasks rejected NON_RECOVERABLE and the device never retried them.
        // Flag a task only when it carries NEITHER key, which is the check that was intended.
        entities.stream()
                .filter(task -> StringUtils.isBlank(task.getProjectBeneficiaryId())
                        && StringUtils.isBlank(task.getProjectBeneficiaryClientReferenceId()))
                .forEach(task -> {
                    Error error = getErrorForInvalidRelatedEntityID();
                    populateErrorDetails(task, error, errorDetailsMap);
                });

        // Existence lookup gated behind the flag; the null-related-id INVALID_RELATED_ENTITY_ID check above always runs.
        List<Task> tasksToValidate = entities.stream().filter(notHavingErrors()).collect(Collectors.toList());
        // The emptiness guard is load bearing: getTenantId does findAny().get() and would throw
        // NoSuchElementException out of validate() for a batch in which every task was flagged above,
        // and the bulk consumer's catch-all would then discard the batch instead of reporting it.
        if (!tasksToValidate.isEmpty() && Boolean.TRUE.equals(projectConfiguration.getIsRelationshipValidationEnabled())) {
            // The parent key is chosen PER TASK, not sampled once for the whole batch. getIdMethod samples
            // one arbitrary element, so in a batch mixing the two parent-key shapes a sibling carrying only
            // the OTHER key contributed a null lookup key, was queried against the sampled column, came back
            // absent from the result and was flagged NON_EXISTENT_RELATED_ENTITY / NON_RECOVERABLE - the very
            // mixed-key rejection the blank check above exists to prevent, reached through this branch.
            // Partition and run one lookup per column, then merge; this is the shape HmHouseholdHeadValidator
            // was reshaped into for the same defect on the household-member side.
            Map<String, List<Task>> tasksByBeneficiaryId = new LinkedHashMap<>();
            Map<String, List<Task>> tasksByBeneficiaryClientReferenceId = new LinkedHashMap<>();
            for (Task task : tasksToValidate) {
                if (StringUtils.isNotBlank(task.getProjectBeneficiaryId())) {
                    tasksByBeneficiaryId
                            .computeIfAbsent(task.getProjectBeneficiaryId(), k -> new ArrayList<>())
                            .add(task);
                } else if (StringUtils.isNotBlank(task.getProjectBeneficiaryClientReferenceId())) {
                    tasksByBeneficiaryClientReferenceId
                            .computeIfAbsent(task.getProjectBeneficiaryClientReferenceId(), k -> new ArrayList<>())
                            .add(task);
                }
                // A task carrying NEITHER key was already flagged INVALID_RELATED_ENTITY_ID above, so
                // notHavingErrors() has already removed it and it cannot contribute a null lookup key.
            }
            String tenantId = getTenantId(tasksToValidate);
            try {
                validateExistence(tenantId, tasksByBeneficiaryId, "id", errorDetailsMap);
                validateExistence(tenantId, tasksByBeneficiaryClientReferenceId, "clientReferenceId",
                        errorDetailsMap);
            } catch (InvalidTenantIdException exception) {
                // Populating InvalidTenantIdException for all entities
                tasksToValidate.forEach(task -> {
                    Error error = getErrorForInvalidTenantId(tenantId, exception);
                    populateErrorDetails(task, error, errorDetailsMap);
                });
            }
        }

        return errorDetailsMap;
    }

    /**
     * Looks the batch's keys of ONE shape up against ONE column and flags every task whose key is absent.
     * Skips the lookup entirely when no task in the batch carries that shape, so a batch of a single shape
     * still costs a single query.
     *
     * @param tenantId       the tenant to look up in
     * @param tasksByKey     the tasks of this shape, grouped by the key value they carry
     * @param columnName     the project_beneficiary column that key value lives in
     * @param errorDetailsMap the map errors are accumulated into
     * @throws InvalidTenantIdException if the schema name cannot be resolved for the tenant
     */
    private void validateExistence(String tenantId, Map<String, List<Task>> tasksByKey, String columnName,
                                   Map<Task, List<Error>> errorDetailsMap) throws InvalidTenantIdException {
        if (tasksByKey.isEmpty()) {
            return;
        }
        List<String> existingProjectBeneficiaryIds = projectBeneficiaryRepository
                .validateIds(tenantId, new ArrayList<>(tasksByKey.keySet()), columnName);
        tasksByKey.forEach((key, tasksForKey) -> {
            if (!existingProjectBeneficiaryIds.contains(key)) {
                // Every task sharing the missing key is flagged, not just one of them. The sampled shape
                // iterated an id-to-object map, which silently kept a single task per key and let its
                // siblings through unflagged.
                tasksForKey.forEach(task -> {
                    Error error = getErrorForNonExistentRelatedEntity(key);
                    populateErrorDetails(task, error, errorDetailsMap);
                });
            }
        });
    }
}
