package org.egov.facility.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.egov.common.ds.Tuple;
import org.egov.common.models.ErrorDetails;
import org.egov.common.models.facility.Facility;
import org.egov.common.models.facility.FacilityBulkRequest;
import org.egov.common.models.facility.FacilityRequest;
import org.egov.common.models.facility.FacilitySearchRequest;
import org.egov.common.validator.Validator;
import org.egov.facility.config.FacilityConfiguration;
import org.egov.facility.repository.FacilityRepository;
import org.egov.facility.service.enrichment.FacilityEnrichmentService;
import org.egov.facility.validator.FBoundaryValidator;
import org.egov.facility.validator.FIsDeletedValidator;
import org.egov.facility.validator.FNonExistentValidator;
import org.egov.facility.validator.FNullIdValidator;
import org.egov.facility.validator.FRowVersionValidator;
import org.egov.facility.validator.FUniqueEntityValidator;
import org.springframework.stereotype.Service;
import org.springframework.util.ReflectionUtils;

import java.util.Collections;
import java.util.HashMap;
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
import static org.egov.common.utils.CommonUtils.populateErrorDetails;
import static org.egov.common.utils.CommonUtils.validate;
import static org.egov.facility.Constants.GET_FACILITIES;
import static org.egov.facility.Constants.SET_FACILITIES;
import static org.egov.facility.Constants.VALIDATION_ERROR;


@Service
@Slf4j
public class FacilityService {

    private final FacilityRepository facilityRepository;

    private final List<Validator<FacilityBulkRequest, Facility>> validators;

    private final FacilityConfiguration configuration;

    private final FacilityEnrichmentService enrichmentService;

    private final Predicate<Validator<FacilityBulkRequest, Facility>> isApplicableForCreate =
            validator -> validator.getClass().equals(FBoundaryValidator.class);

    private final Predicate<Validator<FacilityBulkRequest, Facility>> isApplicableForUpdate =
            validator -> validator.getClass().equals(FIsDeletedValidator.class)
                    || validator.getClass().equals(FBoundaryValidator.class)
                    || validator.getClass().equals(FNonExistentValidator.class)
                    || validator.getClass().equals(FNullIdValidator.class)
                    || validator.getClass().equals(FRowVersionValidator.class)
                    || validator.getClass().equals(FUniqueEntityValidator.class);

    private final Predicate<Validator<FacilityBulkRequest, Facility>> isApplicableForDelete =
            validator -> validator.getClass().equals(FNonExistentValidator.class)
            || validator.getClass().equals(FNullIdValidator.class);

    public FacilityService(FacilityRepository facilityRepository, 
                           List<Validator<FacilityBulkRequest, Facility>> validators, 
                           FacilityConfiguration configuration, 
                           FacilityEnrichmentService enrichmentService) {
        this.facilityRepository = facilityRepository;
        this.validators = validators;
        this.configuration = configuration;
        this.enrichmentService = enrichmentService;
    }

    public Facility create(FacilityRequest request) {
        FacilityBulkRequest bulkRequest = FacilityBulkRequest.builder()
                .facilities(Collections.singletonList(request.getFacility()))
                .requestInfo(request.getRequestInfo()).build();

        return create(bulkRequest, false).get(0);
    }

    public List<Facility> create(FacilityBulkRequest request, boolean isBulk) {
        log.info("starting create method for facility");

        Tuple<List<Facility>, Map<Facility, ErrorDetails>> tuple = validate(validators,
                isApplicableForCreate, request, SET_FACILITIES, GET_FACILITIES, VALIDATION_ERROR,
                isBulk);
        Map<Facility, ErrorDetails> errorDetailsMap = tuple.getY();
        List<Facility> validEntities = tuple.getX();
        try {
            if (!validEntities.isEmpty()) {
                log.info("processing {} valid entities", validEntities.size());
                enrichmentService.create(validEntities, request);
                facilityRepository.save(validEntities, configuration.getCreateFacilityTopic());
            }
        } catch (Exception exception) {
            log.error("error occurred: {}", ExceptionUtils.getStackTrace(exception));
            populateErrorDetails(request, errorDetailsMap, validEntities, exception, SET_FACILITIES);
        }

        handleErrors(errorDetailsMap, isBulk, VALIDATION_ERROR);
        log.info("completed create method for facility");
        return validEntities;
    }

    public Facility update(FacilityRequest request) {
        FacilityBulkRequest bulkRequest = FacilityBulkRequest.builder()
                .facilities(Collections.singletonList(request.getFacility()))
                .requestInfo(request.getRequestInfo()).build();

        return update(bulkRequest, false).get(0);
    }

    public List<Facility> update(FacilityBulkRequest request, boolean isBulk) {
        log.info("starting update method for facility");
        Tuple<List<Facility>, Map<Facility, ErrorDetails>> tuple = validate(validators,
                isApplicableForUpdate, request, SET_FACILITIES, GET_FACILITIES, VALIDATION_ERROR,
                isBulk);
        Map<Facility, ErrorDetails> errorDetailsMap = tuple.getY();
        List<Facility> validEntities = tuple.getX();
        try {
            if (!validEntities.isEmpty()) {
                log.info("processing {} valid entities", validEntities.size());
                enrichmentService.update(validEntities, request);
                facilityRepository.save(validEntities, configuration.getUpdateFacilityTopic());
            }
        } catch (Exception exception) {
            log.error("error occurred: {}", ExceptionUtils.getStackTrace(exception));
            populateErrorDetails(request, errorDetailsMap, validEntities, exception, SET_FACILITIES);
        }

        handleErrors(errorDetailsMap, isBulk, VALIDATION_ERROR);
        log.info("completed update method for facility");
        return validEntities;
    }

    public Facility delete(FacilityRequest request) {
        FacilityBulkRequest bulkRequest = FacilityBulkRequest.builder()
                .facilities(Collections.singletonList(request.getFacility()))
                .requestInfo(request.getRequestInfo()).build();

        return delete(bulkRequest, false).get(0);
    }

    public List<Facility> delete(FacilityBulkRequest request, boolean isBulk) {
        log.info("starting delete method for facility");
        Tuple<List<Facility>, Map<Facility, ErrorDetails>> tuple = validate(validators,
                isApplicableForDelete, request, SET_FACILITIES, GET_FACILITIES, VALIDATION_ERROR,
                isBulk);
        Map<Facility, ErrorDetails> errorDetailsMap = tuple.getY();
        List<Facility> validEntities = tuple.getX();
        try {
            if (!validEntities.isEmpty()) {
                log.info("processing {} valid entities", validEntities.size());
                enrichmentService.delete(validEntities, request);
                facilityRepository.save(validEntities, configuration.getDeleteFacilityTopic());
            }
        } catch (Exception exception) {
            log.error("error occurred: {}", ExceptionUtils.getStackTrace(exception));
            populateErrorDetails(request, errorDetailsMap, validEntities, exception, SET_FACILITIES);
        }

        handleErrors(errorDetailsMap, isBulk, VALIDATION_ERROR);
        log.info("completed delete method for facility");
        return validEntities;
    }

    public List<Facility> search(FacilitySearchRequest facilitySearchRequest,
                                 Integer limit,
                                 Integer offset,
                                 String tenantId,
                                 Long lastChangedSince,
                                 Boolean includeDeleted) throws Exception  {
        log.info("starting search method for facility");
        String idFieldName = getIdFieldName(facilitySearchRequest.getFacility());
        if (isSearchByIdOnly(facilitySearchRequest.getFacility(), idFieldName)) {
            List<String> ids = (List<String>) ReflectionUtils.invokeMethod(getIdMethod(Collections
                            .singletonList(facilitySearchRequest.getFacility())),
                    facilitySearchRequest.getFacility());
            return facilityRepository.findById(ids, idFieldName, includeDeleted).stream()
                    .filter(lastChangedSince(lastChangedSince))
                    .filter(havingTenantId(tenantId))
                    .filter(includeDeleted(includeDeleted))
                    .collect(Collectors.toList());
        }

        log.info("completed search method for facility");
        return facilityRepository.find(facilitySearchRequest.getFacility(),
                limit, offset, tenantId, lastChangedSince, includeDeleted);
    }
}
