package org.egov.project.service;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.data.query.exception.QueryBuilderException;
import org.egov.common.ds.Tuple;
import org.egov.common.http.client.ServiceRequestClient;
import org.egov.common.models.ErrorDetails;
import org.egov.common.service.IdGenService;
import org.egov.common.utils.CommonUtils;
import org.egov.common.validator.Validator;
import org.egov.project.config.ProjectConfiguration;
import org.egov.project.repository.ProjectBeneficiaryRepository;
import org.egov.project.repository.ProjectRepository;
import org.egov.project.repository.ProjectTaskRepository;
import org.egov.project.service.enrichment.ProjectTaskEnrichmentService;
import org.egov.project.task.validators.IsDeletedSubEntityValidator;
import org.egov.project.task.validators.IsDeletedValidator;
import org.egov.project.task.validators.NonExistentEntityValidator;
import org.egov.project.task.validators.NullIdValidator;
import org.egov.project.task.validators.ProductVariantIdValidator;
import org.egov.project.task.validators.ProjectBeneficiaryIdValidator;
import org.egov.project.task.validators.ProjectIdValidator;
import org.egov.project.task.validators.RowVersionValidator;
import org.egov.project.task.validators.UniqueEntityValidator;
import org.egov.project.task.validators.UniqueSubEntityValidator;
import org.egov.project.web.models.Task;
import org.egov.project.web.models.TaskBulkRequest;
import org.egov.project.web.models.TaskRequest;
import org.egov.project.web.models.TaskSearch;
import org.egov.tracer.model.CustomException;
import org.springframework.stereotype.Service;
import org.springframework.util.ReflectionUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.egov.common.utils.CommonUtils.getIdFieldName;
import static org.egov.common.utils.CommonUtils.getIdMethod;
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
            validator.getClass().equals(ProjectIdValidator.class)
                    || validator.getClass().equals(ProjectBeneficiaryIdValidator.class)
                    || validator.getClass().equals(ProductVariantIdValidator.class);

    private final Predicate<Validator<TaskBulkRequest, Task>> isApplicableForUpdate = validator ->
            validator.getClass().equals(ProjectIdValidator.class)
                    || validator.getClass().equals(ProjectBeneficiaryIdValidator.class)
                    || validator.getClass().equals(ProductVariantIdValidator.class)
                    || validator.getClass().equals(NullIdValidator.class)
                    || validator.getClass().equals(IsDeletedValidator.class)
                    || validator.getClass().equals(IsDeletedSubEntityValidator.class)
                    || validator.getClass().equals(NonExistentEntityValidator.class)
                    || validator.getClass().equals(RowVersionValidator.class)
                    || validator.getClass().equals(UniqueEntityValidator.class)
                    || validator.getClass().equals(UniqueSubEntityValidator.class);

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

    public Task create(TaskRequest request) throws Exception {
        TaskBulkRequest bulkRequest = TaskBulkRequest.builder().requestInfo(request.getRequestInfo())
                .tasks(Collections.singletonList(request.getTask())).build();
        List<Task> tasks = create(bulkRequest, false);
        return create(bulkRequest, false).get(0);
    }

    public List<Task> create(TaskBulkRequest request, boolean isBulk) throws Exception {
        Tuple<List<Task>, Map<Task, ErrorDetails>> tuple = validate(validators,
                isApplicableForCreate, request,
                isBulk);
        Map<Task, ErrorDetails> errorDetailsMap = tuple.getY();
        List<Task> validTasks = tuple.getX();
        try {
            if (!validTasks.isEmpty()) {
                enrichmentService.create(validTasks, request);
                projectTaskRepository.save(request.getTasks(), projectConfiguration.getCreateProjectTaskTopic());
            }
         } catch (Exception exception) {
            log.error("error occurred", exception);
            populateErrorDetails(request, errorDetailsMap, validTasks, exception, SET_TASKS);
        }

        handleErrors(isBulk, errorDetailsMap);

        return validTasks;
    }

    public Task update(TaskRequest request) throws Exception {
        TaskBulkRequest bulkRequest = TaskBulkRequest.builder().requestInfo(request.getRequestInfo())
                .tasks(Collections.singletonList(request.getTask())).build();
        return update(bulkRequest, false).get(0);
    }

    public List<Task> update(TaskBulkRequest request, boolean isBulk) {
        Tuple<List<Task>, Map<Task, ErrorDetails>> tuple = validate(validators,
                isApplicableForUpdate, request,
                isBulk);
        Map<Task, ErrorDetails> errorDetailsMap = tuple.getY();
        List<Task> validTasks = tuple.getX();
        try {
            if (!validTasks.isEmpty()) {
                enrichmentService.update(validTasks, request);
                projectTaskRepository.save(request.getTasks(), projectConfiguration.getUpdateProjectTaskTopic());
            }
        } catch (Exception exception) {
            log.error("error occurred", exception);
            populateErrorDetails(request, errorDetailsMap, validTasks, exception, SET_TASKS);
        }

        handleErrors(isBulk, errorDetailsMap);

        return validTasks;

    }

    public List<Task> delete(TaskBulkRequest request, boolean isBulk) {
        return null;
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
        String idFieldName = getIdFieldName(taskSearch);
        if (isSearchByIdOnly(taskSearch, idFieldName)) {
            List<String> ids = new ArrayList<>();
            ids.add((String) ReflectionUtils.invokeMethod(getIdMethod(Collections
                            .singletonList(taskSearch)),
                    taskSearch));
            return projectTaskRepository.findById(ids,
                            idFieldName, includeDeleted).stream()
                    .filter(lastChangedSince(lastChangedSince))
                    .filter(havingTenantId(tenantId))
                    .filter(includeDeleted(includeDeleted))
                    .collect(Collectors.toList());
        }

        try {
            return projectTaskRepository.find(taskSearch, limit, offset,
                    tenantId, lastChangedSince, includeDeleted);
        } catch (QueryBuilderException e) {
            throw new CustomException("ERROR_IN_QUERY", e.getMessage());
        }
    }

    private static void handleErrors(boolean isBulk, Map<Task, ErrorDetails> errorDetailsMap) {
        if (!errorDetailsMap.isEmpty()) {
            log.error("{} errors collected", errorDetailsMap.size());
            if (isBulk) {
                log.info("call tracer.handleErrors(), {}", errorDetailsMap.values());
            } else {
                throw new CustomException(VALIDATION_ERROR, errorDetailsMap.values().toString());
            }
        }
    }
}
