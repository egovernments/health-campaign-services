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
import org.egov.project.service.enrichment.LocationCaptureEnrichmentService;
import org.egov.project.validator.useraction.LcBoundaryValidator;
import org.egov.project.validator.useraction.LcExistentEntityValidator;
import org.egov.project.validator.useraction.LcProjectIdValidator;
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
import static org.egov.project.Constants.SET_LOCATION_CAPTURE;
import static org.egov.project.Constants.VALIDATION_ERROR;

@Service
@Slf4j
public class LocationCaptureService {

    private final IdGenService idGenService;

    private final LocationCaptureRepository locationCaptureRepository;

    private final ServiceRequestClient serviceRequestClient;

    private final ProjectConfiguration projectConfiguration;

    private final LocationCaptureEnrichmentService locationCaptureEnrichmentService;

    private final List<Validator<UserActionBulkRequest, UserAction>> validators;

    private final Predicate<Validator<UserActionBulkRequest, UserAction>> isApplicableForCreate = validator ->
            validator.getClass().equals(LcProjectIdValidator.class)
                    || validator.getClass().equals(LcExistentEntityValidator.class)
                    || validator.getClass().equals(LcBoundaryValidator.class);

    @Autowired
    public LocationCaptureService(
            IdGenService idGenService,
            LocationCaptureRepository locationCaptureRepository,
            ServiceRequestClient serviceRequestClient,
            ProjectConfiguration projectConfiguration,
            LocationCaptureEnrichmentService locationCaptureEnrichmentService,
            List<Validator<UserActionBulkRequest, UserAction>> validators
    ) {
        this.idGenService = idGenService;
        this.locationCaptureRepository = locationCaptureRepository;
        this.serviceRequestClient = serviceRequestClient;
        this.projectConfiguration = projectConfiguration;
        this.locationCaptureEnrichmentService = locationCaptureEnrichmentService;
        this.validators = validators;
    }

    public List<UserAction> create(UserActionBulkRequest request, boolean isBulk) {
        log.info("received request to create bulk location capture tasks");
        Tuple<List<UserAction>, Map<UserAction, ErrorDetails>> tuple = validate(validators, isApplicableForCreate, request, isBulk);
        Map<UserAction, ErrorDetails> errorDetailsMap = tuple.getY();
        List<UserAction> validLocationCaptures = tuple.getX();
        try {
            if (!validLocationCaptures.isEmpty()) {
                log.info("processing {} valid entities", validLocationCaptures.size());
                locationCaptureEnrichmentService.create(validLocationCaptures, request);
                locationCaptureRepository.save(validLocationCaptures, projectConfiguration.getCreateLocationCaptureTaskTopic());
                log.info("successfully created location capture tasks");
            }
        } catch (Exception exception) {
            log.error("error occurred while creating location capture tasks: {}", ExceptionUtils.getStackTrace(exception));
            populateErrorDetails(request, errorDetailsMap, validLocationCaptures, exception, SET_LOCATION_CAPTURE);
        }

        handleErrors(errorDetailsMap, isBulk, VALIDATION_ERROR);

        return validLocationCaptures;
    }

    private Tuple<List<UserAction>, Map<UserAction, ErrorDetails>> validate(List<Validator<UserActionBulkRequest, UserAction>> validators,
                                                                Predicate<Validator<UserActionBulkRequest, UserAction>> applicableValidators,
                                                                            UserActionBulkRequest request, boolean isBulk) {
        log.info("validating request");
        Map<UserAction, ErrorDetails> errorDetailsMap = CommonUtils.validate(validators,
                applicableValidators, request,
                SET_LOCATION_CAPTURE);
        if (!errorDetailsMap.isEmpty() && !isBulk) {
            throw new CustomException(VALIDATION_ERROR, errorDetailsMap.values().toString());
        }
        List<UserAction> validLocationCaptures = request.getUserActions().stream()
                .filter(notHavingErrors()).collect(Collectors.toList());
        return new Tuple<>(validLocationCaptures, errorDetailsMap);
    }

    public SearchResponse<UserAction> search(UserActionSearchRequest locationCaptureSearchRequest, URLParams urlParams) {

        log.info("received request to search project task");
        UserActionSearch locationCaptureSearch = locationCaptureSearchRequest.getUserAction();
        String idFieldName = getIdFieldName(locationCaptureSearch);
        if (isSearchByIdOnly(locationCaptureSearch, idFieldName)) {
            log.info("searching location capture by id");
            List<String> ids = (List<String>) ReflectionUtils.invokeMethod(
                    getIdMethod(Collections.singletonList(locationCaptureSearch)),
                    locationCaptureSearch
            );
            log.info("fetching location capture tasks with ids: {}", ids);
            SearchResponse<UserAction> searchResponse = locationCaptureRepository.findById(ids, idFieldName);
            return SearchResponse.<UserAction>builder()
                    .response(searchResponse.getResponse().stream()
                        .filter(lastChangedSince(urlParams.getLastChangedSince()))
                        .filter(havingTenantId(urlParams.getTenantId()))
                        .collect(Collectors.toList())
                    )
                    .totalCount(searchResponse.getTotalCount()).build();
        }

        log.info("searching project beneficiaries using criteria");
        return locationCaptureRepository.find(locationCaptureSearch, urlParams);
    }

    public void putInCache(List<UserAction> locationCaptures) {
        log.info("putting {} location tracking tasks in cache", locationCaptures.size());
        locationCaptureRepository.putInCache(locationCaptures);
        log.info("successfully put location tracking tasks in cache");
    }

}
