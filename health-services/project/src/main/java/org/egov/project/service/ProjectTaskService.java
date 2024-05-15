package org.egov.project.service;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.data.query.exception.QueryBuilderException;
import org.egov.common.ds.Tuple;
import org.egov.common.http.client.ServiceRequestClient;
import org.egov.common.models.ErrorDetails;
import org.egov.common.models.project.Task;
import org.egov.common.models.project.TaskBulkRequest;
import org.egov.common.models.project.TaskRequest;
import org.egov.common.models.project.TaskSearch;
import org.egov.common.service.IdGenService;
import org.egov.common.utils.CommonUtils;
import org.egov.common.validator.Validator;
import org.egov.project.config.ProjectConfiguration;
import org.egov.project.repository.ProjectBeneficiaryRepository;
import org.egov.project.repository.ProjectRepository;
import org.egov.project.repository.ProjectTaskRepository;
import org.egov.project.service.enrichment.ProjectTaskEnrichmentService;
import org.egov.project.validator.task.PtIsDeletedSubEntityValidator;
import org.egov.project.validator.task.PtIsDeletedValidator;
import org.egov.project.validator.task.PtNonExistentEntityValidator;
import org.egov.project.validator.task.PtNullIdValidator;
import org.egov.project.validator.task.PtProductVariantIdValidator;
import org.egov.project.validator.task.PtProjectBeneficiaryIdValidator;
import org.egov.project.validator.task.PtProjectIdValidator;
import org.egov.project.validator.task.PtIsResouceEmptyValidator;
import org.egov.project.validator.task.PtRowVersionValidator;
import org.egov.project.validator.task.PtUniqueEntityValidator;
import org.egov.project.validator.task.PtUniqueSubEntityValidator;
import org.egov.tracer.model.CustomException;
import org.springframework.stereotype.Service;
import org.springframework.util.ReflectionUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.egov.common.utils.CommonUtils.getIdFieldName;
import static org.egov.common.utils.CommonUtils.getIdMethod;
import static org.egov.common.utils.CommonUtils.handleErrors;
import static org.egov.common.utils.CommonUtils.havingTenantId;
import static org.egov.common.utils.CommonUtils.includeDeleted;
import static org.egov.common.utils.CommonUtils.isSearchByIdOnly;
import static org.egov.common.utils.CommonUtils.lastChangedSince;
import static org.egov.common.utils.CommonUtils.notHavingErrors;
import static org.egov.common.utils.CommonUtils.populateErrorDetails;
import static org.egov.project.Constants.SET_TASKS;
import static org.egov.project.Constants.VALIDATION_ERROR;

@Service
@Slf4j
public class ProjectTaskService {

    private final IdGenService idGenService;

    private final ProjectRepository projectRepository;

    private final ServiceRequestClient serviceRequestClient;

    private final ProjectTaskRepository projectTaskRepository;

    private final ProjectBeneficiaryRepository projectBeneficiaryRepository;

    private final ProjectConfiguration projectConfiguration;

    private final ProjectTaskEnrichmentService enrichmentService;

    private final Predicate<Validator<TaskBulkRequest, Task>> isApplicableForCreate = validator ->
            validator.getClass().equals(PtProjectIdValidator.class)
                    || validator.getClass().equals(PtIsResouceEmptyValidator.class)
                    || validator.getClass().equals(PtProjectBeneficiaryIdValidator.class)
                    || validator.getClass().equals(PtProductVariantIdValidator.class);

    private final Predicate<Validator<TaskBulkRequest, Task>> isApplicableForUpdate = validator ->
            validator.getClass().equals(PtProjectIdValidator.class)
                    || validator.getClass().equals(PtIsResouceEmptyValidator.class)
                    || validator.getClass().equals(PtProjectBeneficiaryIdValidator.class)
                    || validator.getClass().equals(PtProductVariantIdValidator.class)
                    || validator.getClass().equals(PtNullIdValidator.class)
                    || validator.getClass().equals(PtIsDeletedValidator.class)
                    || validator.getClass().equals(PtIsDeletedSubEntityValidator.class)
                    || validator.getClass().equals(PtNonExistentEntityValidator.class)
                    || validator.getClass().equals(PtRowVersionValidator.class)
                    || validator.getClass().equals(PtUniqueEntityValidator.class)
                    || validator.getClass().equals(PtUniqueSubEntityValidator.class);

    private final Predicate<Validator<TaskBulkRequest, Task>> isApplicableForDelete = validator ->
    validator.getClass().equals(PtNullIdValidator.class)
                    || validator.getClass().equals(PtNonExistentEntityValidator.class);

    private final List<Validator<TaskBulkRequest, Task>> validators;

    public ProjectTaskService(IdGenService idGenService, ProjectRepository projectRepository,
                              ServiceRequestClient serviceRequestClient,
                              ProjectTaskRepository projectTaskRepository,
                              ProjectBeneficiaryRepository projectBeneficiaryRepository, ProjectConfiguration projectConfiguration, ProjectTaskEnrichmentService enrichmentService, List<Validator<TaskBulkRequest, Task>> validators) {
        this.idGenService = idGenService;
        this.projectRepository = projectRepository;
        this.serviceRequestClient = serviceRequestClient;
        this.projectTaskRepository = projectTaskRepository;
        this.projectBeneficiaryRepository = projectBeneficiaryRepository;
        this.projectConfiguration = projectConfiguration;
        this.enrichmentService = enrichmentService;
        this.validators = validators;
    }

    public Task create(TaskRequest request) {
        log.info("received request to create tasks");
        TaskBulkRequest bulkRequest = TaskBulkRequest.builder().requestInfo(request.getRequestInfo())
                .tasks(Collections.singletonList(request.getTask())).build();
        log.info("creating bulk request");
        List<Task> tasks = create(bulkRequest, false);
        return tasks.get(0);
    }

    public List<Task> create(TaskBulkRequest request, boolean isBulk) {
        log.info("received request to create bulk project tasks");
        Tuple<List<Task>, Map<Task, ErrorDetails>> tuple = validate(validators,
                isApplicableForCreate, request,
                isBulk);
        Map<Task, ErrorDetails> errorDetailsMap = tuple.getY();
        List<Task> validTasks = tuple.getX();
        try {
            if (!validTasks.isEmpty()) {
                log.info("processing {} valid entities", validTasks.size());
                enrichmentService.create(validTasks, request);
                projectTaskRepository.save(validTasks, projectConfiguration.getCreateProjectTaskTopic());
                log.info("successfully created project tasks");
            }
         } catch (Exception exception) {
            log.error("error occurred while creating project tasks: {}", exception.getMessage());
            populateErrorDetails(request, errorDetailsMap, validTasks, exception, SET_TASKS);
        }

        handleErrors(errorDetailsMap, isBulk, VALIDATION_ERROR);

        return validTasks;
    }

    public Task update(TaskRequest request) {
        log.info("received request to update project tasks");
        TaskBulkRequest bulkRequest = TaskBulkRequest.builder().requestInfo(request.getRequestInfo())
                .tasks(Collections.singletonList(request.getTask())).build();
        log.info("creating bulk request");
        return update(bulkRequest, false).get(0);
    }

    public List<Task> update(TaskBulkRequest request, boolean isBulk) {
        log.info("received request to update bulk project tasks");
        Tuple<List<Task>, Map<Task, ErrorDetails>> tuple = validate(validators,
                isApplicableForUpdate, request,
                isBulk);
        Map<Task, ErrorDetails> errorDetailsMap = tuple.getY();
        List<Task> validTasks = tuple.getX();
        try {
            if (!validTasks.isEmpty()) {
                log.info("processing {} valid entities", validTasks.size());
                enrichmentService.update(validTasks, request);
                projectTaskRepository.save(validTasks, projectConfiguration.getUpdateProjectTaskTopic());
                log.info("successfully updated bulk project tasks");
            }
        } catch (Exception exception) {
            log.error("error occurred while updating project tasks", exception);
            populateErrorDetails(request, errorDetailsMap, validTasks, exception, SET_TASKS);
        }

        handleErrors(errorDetailsMap, isBulk, VALIDATION_ERROR);

        return validTasks;
    }

    public Task delete(TaskRequest request) {
        log.info("received request to delete a project task");
        TaskBulkRequest bulkRequest = TaskBulkRequest.builder().requestInfo(request.getRequestInfo())
                .tasks(Collections.singletonList(request.getTask())).build();
        log.info("creating bulk request");
        return delete(bulkRequest, false).get(0);
    }

    public List<Task> delete(TaskBulkRequest request, boolean isBulk) {
        Tuple<List<Task>, Map<Task, ErrorDetails>> tuple = validate(validators,
                isApplicableForDelete, request,
                isBulk);
        Map<Task, ErrorDetails> errorDetailsMap = tuple.getY();
        List<Task> validTasks = tuple.getX();
        try {
            if (!validTasks.isEmpty()) {
                log.info("processing {} valid entities", validTasks.size());
                enrichmentService.delete(validTasks, request);
                projectTaskRepository.save(validTasks, projectConfiguration.getDeleteProjectTaskTopic());
            }
        } catch (Exception exception) {
            log.error("error occurred while deleting entities: {}", exception);
            populateErrorDetails(request, errorDetailsMap, validTasks, exception, SET_TASKS);
        }

        handleErrors(errorDetailsMap, isBulk, VALIDATION_ERROR);
        return validTasks;
    }

    private Tuple<List<Task>, Map<Task, ErrorDetails>> validate(List<Validator<TaskBulkRequest, Task>> validators,
                                                                Predicate<Validator<TaskBulkRequest, Task>> applicableValidators,
                                                                TaskBulkRequest request, boolean isBulk) {
        log.info("validating request");
        Map<Task, ErrorDetails> errorDetailsMap = CommonUtils.validate(validators,
                applicableValidators, request,
                SET_TASKS);
        if (!errorDetailsMap.isEmpty() && !isBulk) {
            throw new CustomException(VALIDATION_ERROR, errorDetailsMap.values().toString());
        }
        List<Task> validTasks = request.getTasks().stream()
                .filter(notHavingErrors()).collect(Collectors.toList());
        return new Tuple<>(validTasks, errorDetailsMap);
    }

    public List<Task> search(TaskSearch taskSearch, Integer limit, Integer offset, String tenantId,
                             Long lastChangedSince, Boolean includeDeleted) {

        log.info("received request to search project task");

        String idFieldName = getIdFieldName(taskSearch);
        if (isSearchByIdOnly(taskSearch, idFieldName)) {
            log.info("searching project task by id");
            List<String> ids = (List<String>) ReflectionUtils.invokeMethod(getIdMethod(Collections
                            .singletonList(taskSearch)),
                    taskSearch);
            log.info("fetching project tasks with ids: {}", ids);
            return projectTaskRepository.findById(ids,
                            idFieldName, includeDeleted).stream()
                    .filter(lastChangedSince(lastChangedSince))
                    .filter(havingTenantId(tenantId))
                    .filter(includeDeleted(includeDeleted))
                    .collect(Collectors.toList());
        }

        try {
            log.info("searching project beneficiaries using criteria");
            return projectTaskRepository.find(taskSearch, limit, offset,
                    tenantId, lastChangedSince, includeDeleted);
        } catch (QueryBuilderException e) {
            log.error("error in building query", e);
            throw new CustomException("ERROR_IN_QUERY", e.getMessage());
        }
    }

    public void putInCache(List<Task> tasks) {
        log.info("putting {} project tasks in cache", tasks.size());
        projectTaskRepository.putInCache(tasks);
        log.info("successfully put project tasks in cache");
    }
}
