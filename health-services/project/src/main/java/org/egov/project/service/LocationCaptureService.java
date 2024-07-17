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
import org.egov.common.models.project.irs.LocationCapture;
import org.egov.common.models.project.irs.LocationCaptureBulkRequest;
import org.egov.common.models.project.irs.LocationCaptureSearch;
import org.egov.common.models.project.irs.LocationCaptureSearchRequest;
import org.egov.common.service.IdGenService;
import org.egov.common.utils.CommonUtils;
import org.egov.common.validator.Validator;
import org.egov.project.config.ProjectConfiguration;
import org.egov.project.repository.LocationCaptureRepository;
import org.egov.project.service.enrichment.ProjectTaskEnrichmentService;
import org.egov.project.validator.irs.LcProjectIdValidator;
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
import static org.egov.project.Constants.SET_TASKS;
import static org.egov.project.Constants.VALIDATION_ERROR;

@Service
@Slf4j
public class LocationCaptureService {

    private final IdGenService idGenService;

    private final LocationCaptureRepository locationCaptureRepository;

    private final ServiceRequestClient serviceRequestClient;

    private final ProjectConfiguration projectConfiguration;

    private final ProjectTaskEnrichmentService enrichmentService;

    private final List<Validator<LocationCaptureBulkRequest, LocationCapture>> validators;

    private final Predicate<Validator<LocationCaptureBulkRequest, LocationCapture>> isApplicableForCreate = validator ->
            validator.getClass().equals(LcProjectIdValidator.class)
                    || validator.getClass().equals(LcProjectIdValidator.class);

    @Autowired
    public LocationCaptureService(
            IdGenService idGenService,
            LocationCaptureRepository locationCaptureRepository,
            ServiceRequestClient serviceRequestClient,
            ProjectConfiguration projectConfiguration,
            ProjectTaskEnrichmentService enrichmentService,
            List<Validator<LocationCaptureBulkRequest, LocationCapture>> validators
    ) {
        this.idGenService = idGenService;
        this.locationCaptureRepository = locationCaptureRepository;
        this.serviceRequestClient = serviceRequestClient;
        this.projectConfiguration = projectConfiguration;
        this.enrichmentService = enrichmentService;
        this.validators = validators;
    }

    public List<LocationCapture> create(LocationCaptureBulkRequest request, boolean isBulk) {
        log.info("received request to create bulk location capture tasks");
        Tuple<List<LocationCapture>, Map<LocationCapture, ErrorDetails>> tuple = validate(validators, isApplicableForCreate, request, isBulk);
        Map<LocationCapture, ErrorDetails> errorDetailsMap = tuple.getY();
        List<LocationCapture> validLocationCaptures = tuple.getX();
        try {
            if (!validLocationCaptures.isEmpty()) {
                log.info("processing {} valid entities", validLocationCaptures.size());
//                enrichmentService.create(validLocationCaptures, request); TODO
                locationCaptureRepository.save(validLocationCaptures, projectConfiguration.getCreateLocationCaptureTaskTopic());
                log.info("successfully created location capture tasks");
            }
        } catch (Exception exception) {
            log.error("error occurred while creating location capture tasks: {}", ExceptionUtils.getStackTrace(exception));
            populateErrorDetails(request, errorDetailsMap, validLocationCaptures, exception, SET_TASKS);
        }

        handleErrors(errorDetailsMap, isBulk, VALIDATION_ERROR);

        return validLocationCaptures;
    }

    private Tuple<List<LocationCapture>, Map<LocationCapture, ErrorDetails>> validate(List<Validator<LocationCaptureBulkRequest, LocationCapture>> validators,
                                                                Predicate<Validator<LocationCaptureBulkRequest, LocationCapture>> applicableValidators,
                                                                LocationCaptureBulkRequest request, boolean isBulk) {
        log.info("validating request");
        Map<LocationCapture, ErrorDetails> errorDetailsMap = CommonUtils.validate(validators,
                applicableValidators, request,
                SET_TASKS);
        if (!errorDetailsMap.isEmpty() && !isBulk) {
            throw new CustomException(VALIDATION_ERROR, errorDetailsMap.values().toString());
        }
        List<LocationCapture> validLocationCaptures = request.getLocationCaptures().stream()
                .filter(notHavingErrors()).collect(Collectors.toList());
        return new Tuple<>(validLocationCaptures, errorDetailsMap);
    }

    public SearchResponse<LocationCapture> search(LocationCaptureSearchRequest locationCaptureSearchRequest, URLParams urlParams) {

        log.info("received request to search project task");
        LocationCaptureSearch locationCaptureSearch = locationCaptureSearchRequest.getLocationCapture();
        String idFieldName = getIdFieldName(locationCaptureSearch);
        if (isSearchByIdOnly(locationCaptureSearch, idFieldName)) {
            log.info("searching location capture by id");
            List<String> ids = (List<String>) ReflectionUtils.invokeMethod(
                    getIdMethod(Collections.singletonList(locationCaptureSearch)),
                    locationCaptureSearch
            );
            log.info("fetching location capture tasks with ids: {}", ids);
            SearchResponse<LocationCapture> searchResponse = locationCaptureRepository.findById(ids, idFieldName);
            return SearchResponse.<LocationCapture>builder()
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

    public void putInCache(List<LocationCapture> locationCaptures) {
        log.info("putting {} location tracking tasks in cache", locationCaptures.size());
        locationCaptureRepository.putInCache(locationCaptures);
        log.info("successfully put location tracking tasks in cache");
    }

}
