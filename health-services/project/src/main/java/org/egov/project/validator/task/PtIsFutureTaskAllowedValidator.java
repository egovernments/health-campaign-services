package org.egov.project.validator.task;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.data.query.exception.QueryBuilderException;
import org.egov.common.models.Error;
import org.egov.common.models.project.AdditionalFields;
import org.egov.common.models.project.Field;
import org.egov.common.models.project.Project;
import org.egov.common.models.project.Task;
import org.egov.common.models.project.TaskBulkRequest;
import org.egov.common.models.project.TaskSearch;
import org.egov.common.validator.Validator;
import org.egov.project.repository.ProjectRepository;
import org.egov.project.repository.ProjectTaskRepository;
import org.egov.project.validator.project.MultiRoundProjectValidator;
import org.egov.project.validator.project.ProjectValidator;
import org.egov.project.web.models.MultiRoundConstants;
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
import java.util.Optional;
import java.util.stream.Collectors;

import static org.egov.common.utils.CommonUtils.getMethod;
import static org.egov.common.utils.CommonUtils.getObjClass;
import static org.egov.common.utils.CommonUtils.populateErrorDetails;
import static org.egov.project.web.models.MultiRoundConstants.DeliveryType;


@Component
@Order(value = 1)
@Slf4j
public class PtIsFutureTaskAllowedValidator implements Validator<TaskBulkRequest, Task> {

    private final ProjectRepository projectRepository;

    private final ProjectValidator projectValidator;

    private final ProjectTaskRepository projectTaskRepository;
    private final MultiRoundProjectValidator multiRoundProjectValidator;

    @Autowired
    public PtIsFutureTaskAllowedValidator(ProjectRepository projectRepository, ProjectValidator projectValidator, ProjectTaskRepository projectTaskRepository, MultiRoundProjectValidator multiRoundProjectValidator) {
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
            List<Project> projects = projectRepository.getProjectsBasedOnProjectIds(projectIds, new ArrayList<>());
            Map<String, Project> projectMap = new HashMap<>();
            Map<String, String> projectIdProjectTypeIdMap = new HashMap<>();
            Map<String, List<Project>> tenantIdProjectsMap = new HashMap<>();
            Map<String, List<Project>> projectTypeCodeProjectsMap = new HashMap<>();
            projects.forEach(project -> {
                projectMap.put(project.getId(), project);
                projectIdProjectTypeIdMap.put(project.getId(), project.getProjectTypeId());
                if (tenantIdProjectsMap.containsKey(project.getTenantId())) {
                    tenantIdProjectsMap.get(project.getTenantId()).add(project);
                } else {
                    tenantIdProjectsMap.put(project.getTenantId(), Collections.singletonList(project));
                }
                if (projectTypeCodeProjectsMap.containsKey(project.getProjectTypeId())) {
                    projectTypeCodeProjectsMap.get(project.getProjectTypeId()).add(project);
                } else {
                    projectTypeCodeProjectsMap.put(project.getProjectTypeId(), Collections.singletonList(project));
                }
            });
            Map<String, JsonNode> projectTypeJsonMap = multiRoundProjectValidator.populateProjectIdProjectTypeMap(tenantIdProjectsMap.keySet(), request.getRequestInfo(), projectTypeCodeProjectsMap);
            Map<String, List<Task>> projectBeneficiaryClientReferenceIdTaskMap = entities.stream().collect(Collectors.groupingBy(Task::getProjectBeneficiaryClientReferenceId));
            projectBeneficiaryClientReferenceIdTaskMap.forEach((projectBeneficiaryClientReferenceId, tasks) -> {
                JsonNode projectTypeJson = projectTypeJsonMap.get(projectIdProjectTypeIdMap.get(tasks.get(0).getProjectId()));
                if(projectTypeJson.get("code").textValue().equalsIgnoreCase("MR-DN")) verifyPastTask(projectBeneficiaryClientReferenceId, projectTypeJson, tasks.get(0).getTenantId(), tasks, errorDetailsMap);
            });
        }
        return errorDetailsMap;
    }

    private void verifyPastTask(String projectBeneficiaryClientReferenceId, JsonNode projectTypeJson, String tenantId, List<Task> taskYetToBePersisted, Map<Task, List<Error>> errorDetailsMap) {
        List<Task> pastTasks = new ArrayList<>();
        try {
            pastTasks = projectTaskRepository.find(TaskSearch.builder().projectBeneficiaryClientReferenceId(projectBeneficiaryClientReferenceId).build(), null, 0, tenantId, null, Boolean.FALSE);
        } catch (QueryBuilderException e) {
            Error error = Error.builder().errorMessage(String.format("Failed to retrieve past task for validation : "+e.getMessage()))
                    .errorCode("FAILED_TO_FETCH_TASK_FROM_DB").type(Error.ErrorType.NON_RECOVERABLE)
                    .exception(new CustomException("FAILED_TO_FETCH_TASK_FROM_DB", e.toString()))
                    .build();
            populateErrorDetails(Task.builder().projectBeneficiaryClientReferenceId(projectBeneficiaryClientReferenceId).tenantId(tenantId).build(), error, errorDetailsMap);
        }
        taskYetToBePersisted.sort(Comparator.comparing(Task::getCreatedDate));
        List<Task> tasks = new ArrayList<>(pastTasks);
        tasks.addAll(taskYetToBePersisted);
        Integer index = 0;
        JsonNode cycles = projectTypeJson.withArray("cycles");
        String mandatoryWaitTimeForCycleInDays = "";
        Long previousCycleEndDate = null;
        for(JsonNode cycle : cycles) {
            if(previousCycleEndDate != null) {
                mandatoryWaitTimeForCycleInDays = cycle.get("mandatoryWaitSinceLastCycleInDays").textValue();
                Long currentCycleStartDate = tasks.get(index).getCreatedDate();
                Long differenceInDays = ((currentCycleStartDate - previousCycleEndDate)/(1000*60*60*24));
                if(differenceInDays < Long.valueOf(mandatoryWaitTimeForCycleInDays)) {
                    Error error = Error.builder()
                            .errorMessage("Task not allowed as Mandatory wait time for cycle is not over.")
                            .errorCode("TASK_NOT_ALLOWED")
                            .type(Error.ErrorType.NON_RECOVERABLE)
                            .exception(new CustomException("TASK_NOT_ALLOWED", "Task not allowed as Mandatory wait time for cycle is not over.")).build();
                    populateErrorDetails(tasks.get(index), error, errorDetailsMap);
                }
            }
            previousCycleEndDate = isTasksForCycleAllowed(tasks, index, cycle, errorDetailsMap);
            if(previousCycleEndDate == null || index >= tasks.size()) break;
        }
    }

    public Long isTasksForCycleAllowed(List<Task> tasks, Integer index, JsonNode cycle, Map<Task, List<Error>> errorDetailsMap) {
        String mandatoryWaitTimeInDays = "";
        Boolean taskAllowed = Boolean.TRUE;
        Long previousTaskEndDate = null;
        List<Task> futureTasks = null;
        Long lastTaskEndDate = null;
        Task task = null;
        loop : for(JsonNode delivery : cycle.withArray("deliveries")) {
            if(index < tasks.size()) break;
            mandatoryWaitTimeInDays = delivery.get("mandatoryWaitSinceLastDeliveryInDays").textValue();
            String deliveryStrategy = delivery.get("deliveryStrategy").textValue();
            futureTasks = new ArrayList<>();
            task = tasks.get(index);
            if(getDateFromAdditionalFields(task.getAdditionalFields(), MultiRoundConstants.DateType.DATE_OF_DELIVERY) == null) {
                Error error = Error.builder()
                        .errorMessage("Task not allowed as "+MultiRoundConstants.DateType.DATE_OF_DELIVERY+" is required.")
                        .errorCode("TASK_NOT_ALLOWED")
                        .type(Error.ErrorType.NON_RECOVERABLE)
                        .exception(new CustomException("TASK_NOT_ALLOWED", "Task not allowed as "+MultiRoundConstants.DateType.DATE_OF_DELIVERY+" is required.")).build();
                populateErrorDetails(task, error, errorDetailsMap);
                taskAllowed = Boolean.FALSE;
                break;
            }
            switch (DeliveryType.valueOf(deliveryStrategy)) {
                case DIRECT:
                    if(!futureTasks.isEmpty()) {
                        Error error = Error.builder()
                                .errorMessage("Direct task not allowed as un-finished/un-verified task exists")
                                .errorCode("DIRECT_TASK_NOT_ALLOWED")
                                .type(Error.ErrorType.NON_RECOVERABLE)
                                .exception(new CustomException("DIRECT_TASK_NOT_ALLOWED", "Direct task not allowed as un-finished/un-verified task exists")).build();
                        populateErrorDetails(task, error, errorDetailsMap);
                        taskAllowed = Boolean.FALSE;
                        break loop;
                    } else {
                        String dateOfVerification = getDateFromAdditionalFields(task.getAdditionalFields(), MultiRoundConstants.DateType.DATE_OF_VERIFICATION);
                        if(dateOfVerification == null || dateOfVerification.trim().isEmpty()) {
                            Error error = Error.builder()
                                    .errorMessage("Future task not allowed")
                                    .errorCode("FUTURE_TASK_NOT_ALLOWED")
                                    .type(Error.ErrorType.NON_RECOVERABLE)
                                    .exception(new CustomException("FUTURE_TASK_NOT_ALLOWED", "Future task not allowed")).build();
                            populateErrorDetails(task, error, errorDetailsMap);
                            taskAllowed = Boolean.FALSE;
                            break loop;
                        }
                        if((previousTaskEndDate != null && mandatoryWaitTimeInDays != null && ((task.getCreatedDate() - previousTaskEndDate)/(1000*60*60*24)) < Long.valueOf(mandatoryWaitTimeInDays))) {
                            Error error = Error.builder()
                                    .errorMessage("Task not allowed as Mandatory wait time for delivery is not over.")
                                    .errorCode("TASK_NOT_ALLOWED")
                                    .type(Error.ErrorType.NON_RECOVERABLE)
                                    .exception(new CustomException("TASK_NOT_ALLOWED", "Task not allowed as Mandatory wait time for delivery is not over.")).build();
                            populateErrorDetails(tasks.get(index), error, errorDetailsMap);
                        }
                        previousTaskEndDate = Long.valueOf(dateOfVerification);
                    }
                    break;
                case INDIRECT:
                    futureTasks.add(task);
            }
            index++;
        }
        if(!taskAllowed) {
            index++;
            failedTasksFrom(tasks, index, errorDetailsMap);
        } else {
            lastTaskEndDate = Long.valueOf(getDateFromAdditionalFields(tasks.get(index - 1).getAdditionalFields(), MultiRoundConstants.DateType.DATE_OF_VERIFICATION));
        }
        return lastTaskEndDate;
    }

    public String getDateFromAdditionalFields(AdditionalFields fields, MultiRoundConstants.DateType dateType) {
        Optional<Field> dateField = fields.getFields().stream().filter(field -> field.getKey().equalsIgnoreCase(dateType.toString())).findFirst();
        return dateField.map(Field::getValue).orElse(null);
    }
    public void failedTasksFrom(List<Task> tasks, Integer fromIndex, Map<Task, List<Error>> errorDetailsMap) {
        while(fromIndex < tasks.size()) {
            Error error = Error.builder()
                    .errorMessage("Task not allowed")
                    .errorCode("TASK_NOT_ALLOWED")
                    .type(Error.ErrorType.NON_RECOVERABLE)
                    .exception(new CustomException("TASK_NOT_ALLOWED", "Task not allowed")).build();
            populateErrorDetails(tasks.get(fromIndex), error, errorDetailsMap);
            fromIndex++;
        }
    }
}

