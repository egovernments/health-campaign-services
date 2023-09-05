package org.egov.project.validator.task;

import com.fasterxml.jackson.databind.util.JSONPObject;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.data.query.exception.QueryBuilderException;
import org.egov.common.models.Error;
import org.egov.common.models.project.Project;
import org.egov.common.models.project.ProjectType;
import org.egov.common.models.project.Task;
import org.egov.common.models.project.TaskBulkRequest;
import org.egov.common.models.project.TaskSearch;
import org.egov.common.validator.Validator;
import org.egov.project.repository.ProjectRepository;
import org.egov.project.repository.ProjectTaskRepository;
import org.egov.project.validator.project.MultiRoundProjectValidator;
import org.egov.project.validator.project.ProjectValidator;
import org.egov.project.web.models.MultiRoundStatus;
import org.egov.tracer.model.CustomException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.egov.common.utils.CommonUtils.getMethod;
import static org.egov.common.utils.CommonUtils.getObjClass;
import static org.egov.common.utils.CommonUtils.populateErrorDetails;


@Component
@Order(value = 1)
@Slf4j
public class PtIsPastTaskAllowedValidator implements Validator<TaskBulkRequest, Task> {

    private final ProjectRepository projectRepository;

    private final ProjectValidator projectValidator;

    private final ProjectTaskRepository projectTaskRepository;
    private final MultiRoundProjectValidator multiRoundProjectValidator;

    @Autowired
    public PtIsPastTaskAllowedValidator(ProjectRepository projectRepository, ProjectValidator projectValidator, ProjectTaskRepository projectTaskRepository, MultiRoundProjectValidator multiRoundProjectValidator) {
        this.projectRepository = projectRepository;
        this.projectValidator = projectValidator;
        this.projectTaskRepository = projectTaskRepository;
        this.multiRoundProjectValidator = multiRoundProjectValidator;
    }

    @Override
    public Map<Task, List<Error>> validate(TaskBulkRequest request) {
        log.info("validating for past task");
        Map<Task, List<Error>> errorDetailsMap = new HashMap<>();
        List<Task> entities = request.getTasks();
        Class<?> objClass = getObjClass(entities);
        Method idMethod = getMethod("getProjectId", objClass);
        Map<String, List<Task>> projectIdTasksMap = entities.stream().collect(Collectors.groupingBy(e -> e.getProjectId()));
        if (!projectIdTasksMap.isEmpty()) {
            List<String> projectIds = new ArrayList<>(projectIdTasksMap.keySet());
            List<Project> existingProjects = projectRepository.getProjectsBasedOnProjectIds(projectIds, Collections.emptyList());
            Map<String, Project> projectMap = existingProjects.stream().collect(Collectors.toMap(p -> p.getId(), p-> p));
            Map<String, List<Project>> tenantIdProjectsMap = existingProjects.stream().collect(Collectors.groupingBy(e -> e.getTenantId()));
            Map<String, List<Project>> projectTypeCodeProjectsMap = existingProjects.stream().collect(Collectors.groupingBy(e -> e.getProjectTypeId()));
            Map<String, ProjectType> projectIdProjectTypeMap = multiRoundProjectValidator.populateProjectIdProjectTypeMap(tenantIdProjectsMap, request.getRequestInfo(), projectTypeCodeProjectsMap);
            for(Map.Entry<String, ProjectType> entry : projectIdProjectTypeMap.entrySet()) {
                Project project = projectMap.get(entry.getKey());
                if(projectIdTasksMap.containsKey(entry.getKey()) && project.getProjectType().equalsIgnoreCase("multi-round")) {
                    ProjectType projectType = entry.getValue();
                    projectIdTasksMap.get(entry.getKey()).forEach(task -> {
                        //For each task verify that it is not a past task
                        String status_str = task.getAdditionalFields().getFields().stream()
                                .filter(f -> f.getKey().equalsIgnoreCase("status"))
                                .findFirst().get().getValue();
                        MultiRoundStatus status = MultiRoundStatus.valueOf(status_str);
                        if(status == MultiRoundStatus.DELIVERED && project.getProjectSubType().equalsIgnoreCase("DOTN")) {
                            Error error = Error.builder().errorMessage(String.format("Past Task %s is not allowed in Strategy :  %s", task.getId(), project.getProjectSubType()))
                                    .errorCode("PAST_TASK_NOT_ALLOWED").type(Error.ErrorType.NON_RECOVERABLE)
                                    .exception(new CustomException("PAST_TASK_NOT_ALLOWED", "Past Task is not allowed"))
                                    .build();
                            populateErrorDetails(task, error, errorDetailsMap);
                        }
                    });
                    compareWithPastTask(project, entry.getValue(), projectIdTasksMap.get(project.getId()), errorDetailsMap);
                }
            }
        }
        return errorDetailsMap;
    }

    private void compareWithPastTask(Project project, ProjectType projectType, List<Task> taskYetToBePersisted, Map<Task, List<Error>> errorDetailsMap) {
        List<Task> pastTasks = new ArrayList<>();
        try {
            pastTasks = projectTaskRepository.find(TaskSearch.builder().projectId(project.getId()).build(), null, 0, project.getTenantId(), null, Boolean.FALSE);
        } catch (QueryBuilderException e) {
            Error error = Error.builder().errorMessage(String.format("Failed to retrieve past task for validation : "+e.getMessage()))
                    .errorCode("FAILED_TO_FETCH_TASK_FROM_DB").type(Error.ErrorType.NON_RECOVERABLE)
                    .exception(new CustomException("FAILED_TO_FETCH_TASK_FROM_DB", e.toString()))
                    .build();
            populateErrorDetails(Task.builder().projectId(project.getId()).tenantId(project.getTenantId()).build(), error, errorDetailsMap);
        }
        pastTasks.sort(Comparator.comparing(Task::getCreatedDate).reversed());
        taskYetToBePersisted.sort(Comparator.comparing(Task::getCreatedDate));
        // compare cycles and past deliveries from mdms data,
        // first identify which cycle and delivery it is based on the number of past task.
        Task pastTask = pastTasks.get(0);
        for(Task task : taskYetToBePersisted) {
            // compare pasttask created
            String pastDeliveryDate = pastTask.getAdditionalFields().getFields().stream()
                    .filter(f -> f.getKey().equalsIgnoreCase("dateOfVerification"))
                    .findFirst().get().getValue();
            // add the selected delivery start date difference and check with new task created time.
//            if(pastTask.getAdditionalFields(). + projecttype.)


        }
    }


}

