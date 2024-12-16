package org.egov.project.service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
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
import org.egov.project.repository.LocationCaptureRepository;
import org.egov.project.service.enrichment.UserActionEnrichmentService;
import org.egov.project.validator.useraction.UaBoundaryValidator;
import org.egov.project.validator.useraction.UaExistentEntityValidator;
import org.egov.project.validator.useraction.UaProjectIdValidator;
import org.egov.tracer.model.CustomException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.ReflectionUtils;

import static org.egov.common.utils.CommonUtils.getIdFieldName;
import static org.egov.common.utils.CommonUtils.getIdMethod;
import static org.egov.common.utils.CommonUtils.handleErrors;
import static org.egov.common.utils.CommonUtils.havingTenantId;
import static org.egov.common.utils.CommonUtils.isSearchByIdOnly;
import static org.egov.common.utils.CommonUtils.lastChangedSince;
import static org.egov.common.utils.CommonUtils.notHavingErrors;
import static org.egov.common.utils.CommonUtils.populateErrorDetails;
import static org.egov.project.Constants.SET_USER_ACTION;
import static org.egov.project.Constants.VALIDATION_ERROR;

/**
 * Service class for handling location capture tasks related to user actions.
 * Provides methods for creating, validating, searching, and caching location capture tasks.
 */
@Service
@Slf4j
public class LocationCaptureService {

    private final IdGenService idGenService;
    private final LocationCaptureRepository locationCaptureRepository;
    private final ServiceRequestClient serviceRequestClient;
    private final ProjectConfiguration projectConfiguration;
    private final UserActionEnrichmentService userActionEnrichmentService;
    private final List<Validator<UserActionBulkRequest, UserAction>> validators;

    /**
     * Predicate to determine if a validator is applicable for creation.
     * Filters validators based on specific classes.
     */
    private final Predicate<Validator<UserActionBulkRequest, UserAction>> isApplicableForCreate = validator ->
            validator.getClass().equals(UaProjectIdValidator.class)
                    || validator.getClass().equals(UaExistentEntityValidator.class)
                    || validator.getClass().equals(UaBoundaryValidator.class);

    /**
     * Constructor for injecting dependencies into the LocationCaptureService.
     *
     * @param idGenService                 The service for generating unique IDs.
     * @param locationCaptureRepository    Repository for location capture tasks.
     * @param serviceRequestClient         Client for making service requests.
     * @param projectConfiguration         Configuration properties related to the project.
     * @param userActionEnrichmentService Service for enriching location capture user action tasks.
     * @param validators                   List of validators for user actions.
     */
    @Autowired
    public LocationCaptureService(
            IdGenService idGenService,
            LocationCaptureRepository locationCaptureRepository,
            ServiceRequestClient serviceRequestClient,
            ProjectConfiguration projectConfiguration,
            UserActionEnrichmentService userActionEnrichmentService,
            List<Validator<UserActionBulkRequest, UserAction>> validators
    ) {
        this.idGenService = idGenService;
        this.locationCaptureRepository = locationCaptureRepository;
        this.serviceRequestClient = serviceRequestClient;
        this.projectConfiguration = projectConfiguration;
        this.userActionEnrichmentService = userActionEnrichmentService;
        this.validators = validators;
    }

    /**
     * Creates location capture tasks in bulk.
     * Validates the request, enriches valid tasks, saves them, and handles errors.
     *
     * @param request The bulk request containing location capture tasks.
     * @param isBulk  Flag indicating if the request is a bulk operation.
     * @return A list of valid location capture tasks.
     */
    public List<UserAction> create(UserActionBulkRequest request, boolean isBulk) {
        log.info("Received request to create bulk location capture tasks");

        // Validate the request and separate valid tasks from error details.
        Tuple<List<UserAction>, Map<UserAction, ErrorDetails>> tuple = validate(validators, isApplicableForCreate, request, isBulk);
        Map<UserAction, ErrorDetails> errorDetailsMap = tuple.getY();
        List<UserAction> validLocationCaptures = tuple.getX();

        try {
            if (!validLocationCaptures.isEmpty()) {
                log.info("Processing {} valid entities", validLocationCaptures.size());

                // Enrich valid location capture tasks.
                userActionEnrichmentService.create(validLocationCaptures, request);

                // Save valid location capture tasks and send them to the Kafka topic.
                locationCaptureRepository.save(validLocationCaptures, projectConfiguration.getCreateLocationCaptureTopic());
                log.info("Successfully created location capture tasks");
            }
        } catch (Exception exception) {
            // Log and handle exceptions that occur during task creation.
            log.error("Error occurred while creating location capture tasks: {}", ExceptionUtils.getStackTrace(exception));
            populateErrorDetails(request, errorDetailsMap, validLocationCaptures, exception, SET_USER_ACTION);
        }

        // Handle errors based on the validation results.
        handleErrors(errorDetailsMap, isBulk, VALIDATION_ERROR);

        return validLocationCaptures;
    }

    /**
     * Validates the user action bulk request using the provided validators.
     * Filters out tasks with errors and returns the valid ones.
     *
     * @param validators         List of validators to use for validation.
     * @param applicableValidators Predicate to filter applicable validators.
     * @param request            The bulk request to validate.
     * @param isBulk             Flag indicating if the request is a bulk operation.
     * @return A tuple containing valid location capture tasks and error details.
     */
    private Tuple<List<UserAction>, Map<UserAction, ErrorDetails>> validate(
            List<Validator<UserActionBulkRequest, UserAction>> validators,
            Predicate<Validator<UserActionBulkRequest, UserAction>> applicableValidators,
            UserActionBulkRequest request, boolean isBulk) {

        log.info("Validating request");

        // Perform validation and collect error details.
        Map<UserAction, ErrorDetails> errorDetailsMap = CommonUtils.validate(
                validators,
                applicableValidators,
                request,
                SET_USER_ACTION
        );

        // Throw an exception if there are validation errors and it's not a bulk operation.
        if (!errorDetailsMap.isEmpty() && !isBulk) {
            throw new CustomException(VALIDATION_ERROR, errorDetailsMap.values().toString());
        }

        // Filter out tasks with no errors.
        List<UserAction> validLocationCaptures = request.getUserActions().stream()
                .filter(notHavingErrors())
                .collect(Collectors.toList());

        return new Tuple<>(validLocationCaptures, errorDetailsMap);
    }

    /**
     * Searches for location capture tasks based on the provided search request and URL parameters.
     * Supports searching by ID or by other criteria.
     *
     * @param locationCaptureSearchRequest The search request containing criteria for searching.
     * @param urlParams                    URL parameters for filtering the search results.
     * @return A SearchResponse containing the search results and total count.
     */
    public SearchResponse<UserAction> search(UserActionSearchRequest locationCaptureSearchRequest, URLParams urlParams) {
        log.info("Received request to search project task");

        UserActionSearch locationCaptureSearch = locationCaptureSearchRequest.getUserAction();
        String idFieldName = getIdFieldName(locationCaptureSearch);

        if (isSearchByIdOnly(locationCaptureSearch, idFieldName)) {
            log.info("Searching location capture by id");
            List<String> ids = (List<String>) ReflectionUtils.invokeMethod(
                    getIdMethod(Collections.singletonList(locationCaptureSearch)),
                    locationCaptureSearch
            );
            log.info("Fetching location capture tasks with ids: {}", ids);

            // Perform search by IDs and filter results based on last changed date and tenant ID.
            SearchResponse<UserAction> searchResponse = locationCaptureRepository.findById(ids, idFieldName);
            return SearchResponse.<UserAction>builder()
                    .response(searchResponse.getResponse().stream()
                            .filter(lastChangedSince(urlParams.getLastChangedSince()))
                            .filter(havingTenantId(urlParams.getTenantId()))
                            .collect(Collectors.toList())
                    )
                    .totalCount(searchResponse.getTotalCount())
                    .build();
        }

        log.info("Searching project beneficiaries using criteria");
        // Perform search based on other criteria.
        return locationCaptureRepository.find(locationCaptureSearch, urlParams);
    }

    /**
     * Puts location capture tasks into cache.
     *
     * @param locationCaptures The list of location capture tasks to cache.
     */
    public void putInCache(List<UserAction> locationCaptures) {
        log.info("Putting {} location tracking tasks in cache", locationCaptures.size());
        locationCaptureRepository.putInCache(locationCaptures);
        log.info("Successfully put location tracking tasks in cache");
    }
}
