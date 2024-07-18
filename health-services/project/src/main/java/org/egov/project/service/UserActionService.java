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
import org.egov.common.models.project.irs.UserAction;
import org.egov.common.models.project.irs.UserActionBulkRequest;
import org.egov.common.models.project.irs.UserActionSearch;
import org.egov.common.models.project.irs.UserActionSearchRequest;
import org.egov.common.service.IdGenService;
import org.egov.common.utils.CommonUtils;
import org.egov.common.validator.Validator;
import org.egov.project.config.ProjectConfiguration;
import org.egov.project.repository.UserActionRepository;
import org.egov.project.service.enrichment.UserActionEnrichmentService;
import org.egov.project.validator.irs.UaExistentEntityValidator;
import org.egov.project.validator.irs.UaNonExistentEntityValidator;
import org.egov.project.validator.irs.UaNullIdValidator;
import org.egov.project.validator.irs.UaProjectIdValidator;
import org.egov.project.validator.irs.UaRowVersionValidator;
import org.egov.project.validator.irs.UaStatusValidator;
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
import static org.egov.project.Constants.SET_USER_ACTION;
import static org.egov.project.Constants.VALIDATION_ERROR;

@Service
@Slf4j
public class UserActionService {
    private final IdGenService idGenService;

    private final UserActionRepository userActionTaskRepository;

    private final ServiceRequestClient serviceRequestClient;

    private final ProjectConfiguration projectConfiguration;

    private final UserActionEnrichmentService userActionEnrichmentService;

    private final List<Validator<UserActionBulkRequest, UserAction>> validators;

    private final Predicate<Validator<UserActionBulkRequest, UserAction>> isApplicableForCreate = validator ->
            validator.getClass().equals(UaProjectIdValidator.class)
                    || validator.getClass().equals(UaExistentEntityValidator.class);

    private final Predicate<Validator<UserActionBulkRequest, UserAction>> isApplicableForUpdate = validator ->
            validator.getClass().equals(UaProjectIdValidator.class)
                    || validator.getClass().equals(UaNullIdValidator.class)
                    || validator.getClass().equals(UaNonExistentEntityValidator.class)
                    || validator.getClass().equals(UaRowVersionValidator.class)
                    || validator.getClass().equals(UaStatusValidator.class);

    @Autowired
    public UserActionService(
            IdGenService idGenService,
            UserActionRepository userActionTaskRepository,
            ServiceRequestClient serviceRequestClient,
            ProjectConfiguration projectConfiguration,
            UserActionEnrichmentService userActionEnrichmentService,
            List<Validator<UserActionBulkRequest, UserAction>> validators
    ) {
        this.idGenService = idGenService;
        this.userActionTaskRepository = userActionTaskRepository;
        this.serviceRequestClient = serviceRequestClient;
        this.projectConfiguration = projectConfiguration;
        this.userActionEnrichmentService = userActionEnrichmentService;
        this.validators = validators;
    }

    public List<UserAction> create(UserActionBulkRequest request, boolean isBulk) {
        log.info("received request to create bulk closed household userActions");
        Tuple<List<UserAction>, Map<UserAction, ErrorDetails>> tuple = validate(validators, isApplicableForCreate, request, isBulk);
        Map<UserAction, ErrorDetails> errorDetailsMap = tuple.getY();
        List<UserAction> validUserActions = tuple.getX();
        try {
            if (!validUserActions.isEmpty()) {
                log.info("processing {} valid entities", validUserActions.size());
                userActionEnrichmentService.create(validUserActions, request);
                userActionTaskRepository.save(validUserActions, projectConfiguration.getCreateUserActionTaskTopic());
                log.info("successfully created closed household userActions");
            }
        } catch (Exception exception) {
            log.error("error occurred while creating closed household userActions: {}", ExceptionUtils.getStackTrace(exception));
            populateErrorDetails(request, errorDetailsMap, validUserActions, exception, SET_USER_ACTION);
        }

        handleErrors(errorDetailsMap, isBulk, VALIDATION_ERROR);

        return validUserActions;
    }

    public List<UserAction> update(UserActionBulkRequest request, boolean isBulk) {
        log.info("received request to update bulk closed household userActions");
        Tuple<List<UserAction>, Map<UserAction, ErrorDetails>> tuple = validate(validators, isApplicableForUpdate, request, isBulk);
        Map<UserAction, ErrorDetails> errorDetailsMap = tuple.getY();
        List<UserAction> validUserActions = tuple.getX();
        try {
            if (!validUserActions.isEmpty()) {
                log.info("processing {} valid entities", validUserActions.size());
                userActionEnrichmentService.update(validUserActions, request);
                userActionTaskRepository.save(validUserActions, projectConfiguration.getUpdateUserActionTaskTopic());
                log.info("successfully updated bulk closed household userActions");
            }
        } catch (Exception exception) {
            log.error("error occurred while updating closed household userActions", ExceptionUtils.getStackTrace(exception));
            populateErrorDetails(request, errorDetailsMap, validUserActions, exception, SET_USER_ACTION);
        }

        handleErrors(errorDetailsMap, isBulk, VALIDATION_ERROR);

        return validUserActions;
    }

    private Tuple<List<UserAction>, Map<UserAction, ErrorDetails>> validate(List<Validator<UserActionBulkRequest, UserAction>> validators,
                                                                Predicate<Validator<UserActionBulkRequest, UserAction>> applicableValidators,
                                                                UserActionBulkRequest request, boolean isBulk) {
        log.info("validating request");
        Map<UserAction, ErrorDetails> errorDetailsMap = CommonUtils.validate(validators,
                applicableValidators, request,
                SET_USER_ACTION);
        if (!errorDetailsMap.isEmpty() && !isBulk) {
            throw new CustomException(VALIDATION_ERROR, errorDetailsMap.values().toString());
        }
        List<UserAction> validUserActions = request.getUserActions().stream()
                .filter(notHavingErrors()).collect(Collectors.toList());
        return new Tuple<>(validUserActions, errorDetailsMap);
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
            log.info("fetching closed household userActions with ids: {}", ids);
            SearchResponse<UserAction> searchResponse = userActionTaskRepository.findById(ids, idFieldName);
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

    public void putInCache(List<UserAction> userActions) {
        log.info("putting {} closed household userActions in cache", userActions.size());
        userActionTaskRepository.putInCache(userActions);
        log.info("successfully put closed household userActions in cache");
    }
}
