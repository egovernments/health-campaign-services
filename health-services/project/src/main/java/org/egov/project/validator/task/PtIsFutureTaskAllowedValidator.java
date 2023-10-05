package org.egov.project.validator.task;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.data.query.exception.QueryBuilderException;
import org.egov.common.models.Error;
import org.egov.common.models.project.Project;
import org.egov.common.models.project.Task;
import org.egov.common.models.project.TaskBulkRequest;
import org.egov.common.models.project.TaskSearch;
import org.egov.common.validator.Validator;
import org.egov.project.repository.ProjectRepository;
import org.egov.project.repository.ProjectTaskRepository;
import org.egov.project.validator.project.MultiRoundProjectValidator;
import org.egov.project.web.models.MultiRoundConstants;
import org.egov.tracer.model.CustomException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.egov.common.utils.CommonUtils.populateErrorDetails;
import static org.egov.project.web.models.MultiRoundConstants.TASK_NOT_ALLOWED;


@Component
@Order(value = 1)
@Slf4j
public class PtIsFutureTaskAllowedValidator implements Validator<TaskBulkRequest, Task> {

    private final ProjectRepository projectRepository;
    private final ProjectTaskRepository projectTaskRepository;
    private final MultiRoundProjectValidator multiRoundProjectValidator;

    @Autowired
    public PtIsFutureTaskAllowedValidator(ProjectRepository projectRepository, ProjectTaskRepository projectTaskRepository, MultiRoundProjectValidator multiRoundProjectValidator) {
        this.projectRepository = projectRepository;
        this.projectTaskRepository = projectTaskRepository;
        this.multiRoundProjectValidator = multiRoundProjectValidator;
    }

    @Override
    public Map<Task, List<Error>> validate(TaskBulkRequest request) {
        log.info("validating for past task");
        Map<Task, List<Error>> errorDetailsMap = new HashMap<>();
        List<Task> entities = request.getTasks();
        Map<String, List<Task>> projectIdTasksMap = entities.stream().collect(Collectors.groupingBy(Task::getProjectId));
        if (!projectIdTasksMap.isEmpty()) {
            List<String> projectIds = new ArrayList<>(projectIdTasksMap.keySet());
            List<Project> projects = projectRepository.getProjectsBasedOnProjectIds(projectIds, new ArrayList<>());
            Map<String, Project> projectMap = new HashMap<>();
            Map<String, List<Project>> tenantIdProjectsMap = new HashMap<>();
            Map<String, String> projectIdProjectTypeIdMap = new HashMap<>();
            projects.forEach(project -> {
                projectMap.put(project.getId(), project);
                tenantIdProjectsMap
                        .computeIfAbsent(project.getTenantId(), k -> new ArrayList<>())
                        .add(project);
                projectIdProjectTypeIdMap.put(project.getId(), project.getProjectTypeId());
            });
            
            // Fetch project type from mdms for each project.
            Map<String, JsonNode> projectTypeJsonMap = multiRoundProjectValidator.populateProjectTypeMap(tenantIdProjectsMap.keySet(), request.getRequestInfo());

            // Group tasks by project beneficiary client reference ID
            Map<String, List<Task>> projectBeneficiaryClientReferenceIdTaskMap = entities.stream().collect(Collectors.groupingBy(Task::getProjectBeneficiaryClientReferenceId));
            projectBeneficiaryClientReferenceIdTaskMap.forEach((projectBeneficiaryClientReferenceId, tasks) -> {
                JsonNode projectTypeJson = projectTypeJsonMap.get(projectIdProjectTypeIdMap.get(tasks.get(0).getProjectId()));
                if (isProjectTypeMatching(projectTypeJson, "multiround")) {
                    verifyPastTask(projectBeneficiaryClientReferenceId, tasks.get(0).getTenantId(), tasks, errorDetailsMap);
                }
            });
        }
        return errorDetailsMap;
    }

    private boolean isProjectTypeMatching(JsonNode projectTypeJson, String value) {
        return projectTypeJson != null && projectTypeJson.get("type") != null && projectTypeJson.get("type").textValue().equalsIgnoreCase(value);
    }

    private void verifyPastTask(String projectBeneficiaryClientReferenceId, String tenantId, List<Task> taskYetToBePersisted, Map<Task, List<Error>> errorDetailsMap) {
        List<Task> pastTasks;
        try {
            pastTasks = projectTaskRepository.find(TaskSearch.builder().projectBeneficiaryClientReferenceId(projectBeneficiaryClientReferenceId).build(), null, 0, tenantId, null, Boolean.FALSE);
        } catch (QueryBuilderException e) {
            pastTasks = new ArrayList<>();
        }
        taskYetToBePersisted.sort(Comparator.comparing(Task::getCreatedDate));
        List<Task> tasks = new ArrayList<>(pastTasks);
        tasks.addAll(taskYetToBePersisted);
        tasks.forEach(task -> {
            if(task.getStatus() != null) {
                if(!task.getStatus().equals(MultiRoundConstants.Status.BENEFICIARY_REFUSED) && (task.getResources() == null || task.getResources().isEmpty())) {
                    Error error = Error.builder()
                            .errorMessage("Task not allowed as resources are missing.")
                            .errorCode(TASK_NOT_ALLOWED)
                            .type(Error.ErrorType.NON_RECOVERABLE)
                            .exception(new CustomException(TASK_NOT_ALLOWED, "Task not allowed as resources are missing.")).build();
                    populateErrorDetails(task, error, errorDetailsMap);
                } else if(task.getStatus().equals(MultiRoundConstants.Status.BENEFICIARY_REFUSED) && task.getResources() != null) {
                    Error error = Error.builder()
                            .errorMessage("Task not allowed as resources can not be provided when "+MultiRoundConstants.Status.BENEFICIARY_REFUSED)
                            .errorCode(TASK_NOT_ALLOWED)
                            .type(Error.ErrorType.NON_RECOVERABLE)
                            .exception(new CustomException(TASK_NOT_ALLOWED, "Task not allowed as resources can not be provided when "+MultiRoundConstants.Status.BENEFICIARY_REFUSED)).build();
                    populateErrorDetails(task, error, errorDetailsMap);
                }
            }
        });
    }
}