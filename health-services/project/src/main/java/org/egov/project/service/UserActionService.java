package org.egov.project.service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.egov.common.data.query.exception.QueryBuilderException;
import org.egov.common.ds.Tuple;
import org.egov.common.http.client.ServiceRequestClient;
import org.egov.common.models.ErrorDetails;
import org.egov.common.models.core.SearchResponse;
import org.egov.common.models.core.URLParams;
import org.egov.common.models.project.Task;
import org.egov.common.models.project.TaskSearch;
import org.egov.common.models.project.TaskSearchRequest;
import org.egov.common.models.project.irs.UserAction;
import org.egov.common.models.project.irs.UserActionBulkRequest;
import org.egov.common.models.project.irs.UserActionSearch;
import org.egov.common.models.project.irs.UserActionSearchRequest;
import org.egov.common.service.IdGenService;
import org.egov.common.utils.CommonUtils;
import org.egov.common.validator.Validator;
import org.egov.project.config.ProjectConfiguration;
import org.egov.project.repository.UserActionRepository;
import org.egov.project.service.enrichment.ProjectTaskEnrichmentService;
import org.egov.project.validator.closedhousehold.ChStatusValidator;
import org.egov.project.validator.task.PtExistentEntityValidator;
import org.egov.project.validator.task.PtIsDeletedValidator;
import org.egov.project.validator.task.PtNonExistentEntityValidator;
import org.egov.project.validator.task.PtNullIdValidator;
import org.egov.project.validator.task.PtProjectIdValidator;
import org.egov.project.validator.task.PtRowVersionValidator;
import org.egov.tracer.model.CustomException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.ReflectionUtils;

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
public class UserActionService {
    private final IdGenService idGenService;

    private final UserActionRepository userActionTaskRepository;

    private final ServiceRequestClient serviceRequestClient;

    private final ProjectConfiguration projectConfiguration;

    private final ProjectTaskEnrichmentService enrichmentService;

    private final List<Validator<UserActionBulkRequest, UserAction>> validators;

    private final Predicate<Validator<UserActionBulkRequest, UserAction>> isApplicableForCreate = validator ->
            validator.getClass().equals(PtProjectIdValidator.class)
                    || validator.getClass().equals(PtExistentEntityValidator.class);

    private final Predicate<Validator<UserActionBulkRequest, UserAction>> isApplicableForUpdate = validator ->
            validator.getClass().equals(PtProjectIdValidator.class)
                    || validator.getClass().equals(PtNullIdValidator.class)
                    || validator.getClass().equals(PtIsDeletedValidator.class)
                    || validator.getClass().equals(PtNonExistentEntityValidator.class)
                    || validator.getClass().equals(PtRowVersionValidator.class)
                    || validator.getClass().equals(ChStatusValidator.class);

    @Autowired
    public UserActionService(
            IdGenService idGenService,
            UserActionRepository userActionTaskRepository,
            ServiceRequestClient serviceRequestClient,
            ProjectConfiguration projectConfiguration,
            ProjectTaskEnrichmentService enrichmentService,
            List<Validator<UserActionBulkRequest, UserAction>> validators
    ) {
        this.idGenService = idGenService;
        this.userActionTaskRepository = userActionTaskRepository;
        this.serviceRequestClient = serviceRequestClient;
        this.projectConfiguration = projectConfiguration;
        this.enrichmentService = enrichmentService;
        this.validators = validators;
    }

    public List<UserAction> create(UserActionBulkRequest request, boolean isBulk) {
        log.info("received request to create bulk closed household tasks");
        Tuple<List<UserAction>, Map<UserAction, ErrorDetails>> tuple = validate(validators, isApplicableForCreate, request, isBulk);
        Map<UserAction, ErrorDetails> errorDetailsMap = tuple.getY();
        List<UserAction> validTasks = tuple.getX();
        try {
            if (!validTasks.isEmpty()) {
                log.info("processing {} valid entities", validTasks.size());
//                enrichmentService.create(validTasks, request);
                userActionTaskRepository.save(validTasks, projectConfiguration.getCreateUserActionTaskTopic());
                log.info("successfully created closed household tasks");
            }
        } catch (Exception exception) {
            log.error("error occurred while creating closed household tasks: {}", ExceptionUtils.getStackTrace(exception));
            populateErrorDetails(request, errorDetailsMap, validTasks, exception, SET_TASKS);
        }

        handleErrors(errorDetailsMap, isBulk, VALIDATION_ERROR);

        return validTasks;
    }

    public List<UserAction> update(UserActionBulkRequest request, boolean isBulk) {
        log.info("received request to update bulk closed household tasks");
        Tuple<List<UserAction>, Map<UserAction, ErrorDetails>> tuple = validate(validators, isApplicableForUpdate, request, isBulk);
        Map<UserAction, ErrorDetails> errorDetailsMap = tuple.getY();
        List<UserAction> validTasks = tuple.getX();
        try {
            if (!validTasks.isEmpty()) {
                log.info("processing {} valid entities", validTasks.size());
//                enrichmentService.update(validTasks, request);
                userActionTaskRepository.save(validTasks, projectConfiguration.getUpdateUserActionTaskTopic());
                log.info("successfully updated bulk closed household tasks");
            }
        } catch (Exception exception) {
            log.error("error occurred while updating closed household tasks", ExceptionUtils.getStackTrace(exception));
            populateErrorDetails(request, errorDetailsMap, validTasks, exception, SET_TASKS);
        }

        handleErrors(errorDetailsMap, isBulk, VALIDATION_ERROR);

        return validTasks;
    }

    private Tuple<List<UserAction>, Map<UserAction, ErrorDetails>> validate(List<Validator<UserActionBulkRequest, UserAction>> validators,
                                                                Predicate<Validator<UserActionBulkRequest, UserAction>> applicableValidators,
                                                                UserActionBulkRequest request, boolean isBulk) {
        log.info("validating request");
        Map<UserAction, ErrorDetails> errorDetailsMap = CommonUtils.validate(validators,
                applicableValidators, request,
                SET_TASKS);
        if (!errorDetailsMap.isEmpty() && !isBulk) {
            throw new CustomException(VALIDATION_ERROR, errorDetailsMap.values().toString());
        }
        List<UserAction> validTasks = request.getUserActions().stream()
                .filter(notHavingErrors()).collect(Collectors.toList());
        return new Tuple<>(validTasks, errorDetailsMap);
    }

    public SearchResponse<UserAction> search(UserActionSearchRequest request, URLParams urlParams) {

        log.info("received request to search project UserAction");

        UserActionSearch userActionSearch = request.getUserAction();

        String idFieldName = getIdFieldName(userActionSearch);
        if (isSearchByIdOnly(userActionSearch, idFieldName)) {
            log.info("searching project UserAction by id");
            List<String> ids = (List<String>) ReflectionUtils.invokeMethod(getIdMethod(Collections
                            .singletonList(userActionSearch)),
                    userActionSearch);
            log.info("fetching closed household tasks with ids: {}", ids);
            SearchResponse<UserAction> searchResponse = userActionTaskRepository.findById(ids,
                    idFieldName, urlParams.getIncludeDeleted());
            return SearchResponse.<UserAction>builder().response(searchResponse.getResponse().stream()
                    .filter(lastChangedSince(urlParams.getLastChangedSince()))
                    .filter(havingTenantId(urlParams.getTenantId()))
                    .filter(includeDeleted(urlParams.getIncludeDeleted()))
                    .collect(Collectors.toList())).totalCount(searchResponse.getTotalCount()).build();
        }

        try {
            log.info("searching project beneficiaries using criteria");
            return userActionTaskRepository.find(userActionSearch, urlParams);
        } catch (QueryBuilderException e) {
            log.error("error in building query", ExceptionUtils.getStackTrace(e));
            throw new CustomException("ERROR_IN_QUERY", e.getMessage());
        }
    }

    public void putInCache(List<UserAction> tasks) {
        log.info("putting {} closed household tasks in cache", tasks.size());
        userActionTaskRepository.putInCache(tasks);
        log.info("successfully put closed household tasks in cache");
    }
}
