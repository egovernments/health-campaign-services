package org.egov.project.validator.task;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.Error;
import org.egov.common.models.project.Project;
import org.egov.common.models.project.ProjectType;
import org.egov.common.models.project.Task;
import org.egov.common.models.project.TaskBulkRequest;
import org.egov.common.validator.Validator;
import org.egov.project.repository.ProjectRepository;
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

    private final MultiRoundProjectValidator multiRoundProjectValidator;

    @Autowired
    public PtIsPastTaskAllowedValidator(ProjectRepository projectRepository, ProjectValidator projectValidator, MultiRoundProjectValidator multiRoundProjectValidator) {
        this.projectRepository = projectRepository;
        this.projectValidator = projectValidator;
        this.multiRoundProjectValidator = multiRoundProjectValidator;
    }

    // project task before today and after project start date.
    @Override
    public Map<Task, List<Error>> validate(TaskBulkRequest request) {
        log.info("validating for past task");
        Map<Task, List<Error>> errorDetailsMap = new HashMap<>();
        List<Task> entities = request.getTasks();
        Class<?> objClass = getObjClass(entities);
        Method idMethod = getMethod("getProjectId", objClass);
        Map<String, List<Task>> eMap = entities.stream().collect(Collectors.groupingBy(e -> e.getProjectId()));
        if (!eMap.isEmpty()) {
            List<String> projectIds = new ArrayList<>(eMap.keySet());
            List<Project> existingProjects = projectRepository.getProjectsBasedOnProjectIds(projectIds, Collections.emptyList());
            Map<String, List<Project>> tenantIdProjectsMap = existingProjects.stream().collect(Collectors.groupingBy(e -> e.getTenantId()));
            Map<String, List<Project>> projectTypeCodeProjectsMap = existingProjects.stream().collect(Collectors.groupingBy(e -> e.getProjectTypeId()));
            for(Map.Entry<String, List<Project>> entry : tenantIdProjectsMap.entrySet()) {
                List<ProjectType> projectTypes = multiRoundProjectValidator.getProjectTypes(entry.getKey(), request.getRequestInfo());
                //if project type list is empty throw error that for tenant id there is no project type.
                projectTypes.forEach(projectTypeObj -> {
                    if(projectTypeCodeProjectsMap.containsKey(projectTypeObj.getCode())) {
                        if(projectTypeObj.getName().contains("multi-round")) {
                            projectTypeCodeProjectsMap.get(projectTypeObj.getCode()).forEach(projectObj -> {
                                // iterate over all the project task grouped by project id
                                if(projectObj.getProjectSubType().contains("DOTN")) {
                                    // past task not allowed
                                    if(eMap.containsKey(projectObj.getId())) {
                                        eMap.get(projectObj).forEach(task -> {
                                            String status_str = task.getAdditionalFields().getFields().stream()
                                                    .filter(f -> f.getKey().equalsIgnoreCase("status"))
                                                    .findFirst().get().getValue();
                                            MultiRoundStatus status = MultiRoundStatus.valueOf(status_str);
                                            if(status == MultiRoundStatus.DELIVERED) {
                                                Error error = Error.builder().errorMessage(String.format("Past Task %s is not allowed in Strategy :  %s", task.getId(), projectObj.getProjectSubType()))
                                                .errorCode("PAST_TASK_NOT_ALLOWED").type(Error.ErrorType.NON_RECOVERABLE)
                                                .exception(new CustomException("PAST_TASK_NOT_ALLOWED", "Past Task is not allowed"))
                                                .build();
                                                populateErrorDetails(task, error, errorDetailsMap);
                                            }
                                        });
                                    }
                                } else {
                                }
                            });
                        }
                    }
                });

            }

//                        Error error = Error.builder().errorMessage(String.format("Project is not present for project :  %s", project))
//                        .errorCode("ONLY_PAST_TASK_ALLOWED").type(Error.ErrorType.NON_RECOVERABLE)
//                        .exception(new CustomException("ONLY_PAST_TASK_ALLOWED", "Only Past Task is allowed"))
//                        .build();
//            Method projectTypeIdMethod = getMethod("getProjectTypeId", objClass);
//            List<Project> projectsThatAllowPastTask = existingProjects.stream().filter(p -> p.getProjectSubType().contains("DOT1")).collect(Collectors.toList());
//            List<Task> invalidEntities = entities.stream().filter(notHavingErrors()).filter(entity ->
//                    projectsThatAllowPastTask.contains(entity.getProjectId()) ?
//                            !(entity.getActualEndDate() < (Instant.now(Clock.systemUTC()).toEpochMilli() - 10*60*1000) )
//                            :
//                            (existingProjects.contains(entity.getProjectId()) && (entity.getActualEndDate() != null && entity.getActualEndDate() < (Instant.now(Clock.systemUTC()).toEpochMilli() - 10*60*1000)) )
//                    ).collect(Collectors.toList());
//            invalidEntities.forEach(task -> {
//                Error error = projectsThatAllowPastTask.contains(task.getProjectId()) ? Error.builder()
//                        .errorMessage(String.format("Only Past Task is allowed :  %s", task.getId()))
//                        .errorCode("ONLY_PAST_TASK_ALLOWED").type(Error.ErrorType.NON_RECOVERABLE)
//                        .exception(new CustomException("ONLY_PAST_TASK_ALLOWED", "Only Past Task is allowed"))
//                        .build()
//                        : Error.builder()
//                        .errorMessage(String.format("Past Task is not allowed :  %s"
//                        , task.getId()))
//                        .errorCode("PAST_TASK_NOT_ALLOWED").type(Error.ErrorType.NON_RECOVERABLE)
//                        .exception(new CustomException("PAST_TASK_NOT_ALLOWED", "Past Task is not allowed"))
//                        .build();
//
//
//                populateErrorDetails(task, error, errorDetailsMap);
//            });
        }

        return errorDetailsMap;
    }
}

