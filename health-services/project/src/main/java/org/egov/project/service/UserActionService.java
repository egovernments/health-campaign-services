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
import org.egov.common.models.project.useraction.UserAction;
import org.egov.common.models.project.useraction.UserActionBulkRequest;
import org.egov.common.models.project.useraction.UserActionSearch;
import org.egov.common.models.project.useraction.UserActionSearchRequest;
import org.egov.common.service.IdGenService;
import org.egov.common.utils.CommonUtils;
import org.egov.common.validator.Validator;
import org.egov.project.config.ProjectConfiguration;
import org.egov.project.repository.UserActionRepository;
import org.egov.project.service.enrichment.UserActionEnrichmentService;
import org.egov.project.validator.useraction.UaBoundaryValidator;
import org.egov.project.validator.useraction.UaExistentEntityValidator;
import org.egov.project.validator.useraction.UaNonExistentEntityValidator;
import org.egov.project.validator.useraction.UaNullIdValidator;
import org.egov.project.validator.useraction.UaProjectIdValidator;
import org.egov.project.validator.useraction.UaRowVersionValidator;
import org.egov.project.validator.useraction.UaStatusValidator;
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
    private final IdGenService idGenService; // Service for generating unique IDs
    private final UserActionRepository userActionTaskRepository; // Repository for user actions
    private final ServiceRequestClient serviceRequestClient; // Client for external service requests
    private final ProjectConfiguration projectConfiguration; // Configuration properties for the project
    private final UserActionEnrichmentService userActionEnrichmentService; // Service for enriching user actions
    private final List<Validator<UserActionBulkRequest, UserAction>> validators; // List of validators for user actions

    // Predicate to filter validators applicable for creation
    private final Predicate<Validator<UserActionBulkRequest, UserAction>> isApplicableForCreate = validator ->
            validator.getClass().equals(UaProjectIdValidator.class)
                    || validator.getClass().equals(UaExistentEntityValidator.class)
                    || validator.getClass().equals(UaBoundaryValidator.class);

    // Predicate to filter validators applicable for updates
    private final Predicate<Validator<UserActionBulkRequest, UserAction>> isApplicableForUpdate = validator ->
            validator.getClass().equals(UaProjectIdValidator.class)
                    || validator.getClass().equals(UaNullIdValidator.class)
                    || validator.getClass().equals(UaNonExistentEntityValidator.class)
                    || validator.getClass().equals(UaRowVersionValidator.class)
                    || validator.getClass().equals(UaStatusValidator.class)
                    || validator.getClass().equals(UaBoundaryValidator.class);

    // Constructor for dependency injection
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

    // Method to handle the creation of user actions
    public List<UserAction> create(UserActionBulkRequest request, boolean isBulk) {
        log.info("Received request to create bulk closed household userActions");

        // Validate the request and get valid user actions along with error details
        Tuple<List<UserAction>, Map<UserAction, ErrorDetails>> tuple = validate(validators, isApplicableForCreate, request, isBulk);
        Map<UserAction, ErrorDetails> errorDetailsMap = tuple.getY();
        List<UserAction> validUserActions = tuple.getX();

        try {
            // If there are valid user actions, enrich and save them
            if (!validUserActions.isEmpty()) {
                log.info("Processing {} valid entities", validUserActions.size());
                userActionEnrichmentService.create(validUserActions, request);
                userActionTaskRepository.save(validUserActions, projectConfiguration.getCreateUserActionTaskTopic());
                log.info("Successfully created closed household userActions");
            }
        } catch (Exception exception) {
            // Handle and log any exceptions that occur
            log.error("Error occurred while creating closed household userActions: {}", ExceptionUtils.getStackTrace(exception));
            populateErrorDetails(request, errorDetailsMap, validUserActions, exception, SET_USER_ACTION);
        }

        // Handle any validation errors
        handleErrors(errorDetailsMap, isBulk, VALIDATION_ERROR);

        return validUserActions;
    }

    // Method to handle the update of user actions
    public List<UserAction> update(UserActionBulkRequest request, boolean isBulk) {
        log.info("Received request to update bulk closed household userActions");

        // Validate the request and get valid user actions along with error details
        Tuple<List<UserAction>, Map<UserAction, ErrorDetails>> tuple = validate(validators, isApplicableForUpdate, request, isBulk);
        Map<UserAction, ErrorDetails> errorDetailsMap = tuple.getY();
        List<UserAction> validUserActions = tuple.getX();

        try {
            // If there are valid user actions, enrich and update them
            if (!validUserActions.isEmpty()) {
                log.info("Processing {} valid entities", validUserActions.size());
                userActionEnrichmentService.update(validUserActions, request);
                userActionTaskRepository.save(validUserActions, projectConfiguration.getUpdateUserActionTaskTopic());
                log.info("Successfully updated bulk closed household userActions");
            }
        } catch (Exception exception) {
            // Handle and log any exceptions that occur
            log.error("Error occurred while updating closed household userActions: {}", ExceptionUtils.getStackTrace(exception));
            populateErrorDetails(request, errorDetailsMap, validUserActions, exception, SET_USER_ACTION);
        }

        // Handle any validation errors
        handleErrors(errorDetailsMap, isBulk, VALIDATION_ERROR);

        return validUserActions;
    }

    // Method to validate user action requests
    private Tuple<List<UserAction>, Map<UserAction, ErrorDetails>> validate(List<Validator<UserActionBulkRequest, UserAction>> validators,
                                                                            Predicate<Validator<UserActionBulkRequest, UserAction>> applicableValidators,
                                                                            UserActionBulkRequest request, boolean isBulk) {
        log.info("Validating request");

        // Validate the request using the applicable validators
        Map<UserAction, ErrorDetails> errorDetailsMap = CommonUtils.validate(validators,
                applicableValidators, request,
                SET_USER_ACTION);

        // Throw exception if there are validation errors and it's not a bulk request
        if (!errorDetailsMap.isEmpty() && !isBulk) {
            throw new CustomException(VALIDATION_ERROR, errorDetailsMap.values().toString());
        }

        // Filter and return valid user actions
        List<UserAction> validUserActions = request.getUserActions().stream()
                .filter(notHavingErrors()).collect(Collectors.toList());
        return new Tuple<>(validUserActions, errorDetailsMap);
    }

    // Method to search for user actions based on the request and URL parameters
    public SearchResponse<UserAction> search(UserActionSearchRequest request, URLParams urlParams) {
        log.info("Received request to search project UserAction");

        UserActionSearch userActionSearch = request.getUserAction();

        // Determine the ID field name for search
        String idFieldName = getIdFieldName(userActionSearch);
        if (isSearchByIdOnly(userActionSearch, idFieldName)) {
            log.info("Searching project UserAction by id");

            // Retrieve IDs and search for user actions by ID
            List<String> ids = (List<String>) ReflectionUtils.invokeMethod(getIdMethod(Collections
                            .singletonList(userActionSearch)),
                    userActionSearch);
            log.info("Fetching closed household userActions with ids: {}", ids);
            SearchResponse<UserAction> searchResponse = userActionTaskRepository.findById(ids, idFieldName);
            return SearchResponse.<UserAction>builder().response(searchResponse.getResponse().stream()
                    .filter(lastChangedSince(urlParams.getLastChangedSince()))
                    .filter(havingTenantId(urlParams.getTenantId()))
                    .filter(includeDeleted(urlParams.getIncludeDeleted()))
                    .collect(Collectors.toList())).totalCount(searchResponse.getTotalCount()).build();
        }

        try {
            // Search using the criteria specified in the request
            log.info("Searching project user actions using criteria");
            return userActionTaskRepository.find(userActionSearch, urlParams);
        } catch (QueryBuilderException e) {
            // Handle and log query building exceptions
            log.error("Error in building query: {}", ExceptionUtils.getStackTrace(e));
            throw new CustomException("ERROR_IN_QUERY", e.getMessage());
        }
    }

    // Method to put user actions into cache
    public void putInCache(List<UserAction> userActions) {
        log.info("Putting {} closed household userActions in cache", userActions.size());
        userActionTaskRepository.putInCache(userActions);
        log.info("Successfully put closed household userActions in cache");
    }
}
