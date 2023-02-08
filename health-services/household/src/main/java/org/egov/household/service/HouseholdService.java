package org.egov.household.service;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.data.query.exception.QueryBuilderException;
import org.egov.common.ds.Tuple;
import org.egov.common.models.ErrorDetails;
import org.egov.common.service.IdGenService;
import org.egov.common.utils.CommonUtils;
import org.egov.common.validator.Validator;
import org.egov.household.config.HouseholdConfiguration;
import org.egov.household.repository.HouseholdRepository;
import org.egov.household.validators.household.HIsDeletedValidator;
import org.egov.household.validators.household.HNonExsistentEntityValidator;
import org.egov.household.validators.household.HNullIdValidator;
import org.egov.household.validators.household.HRowVersionValidator;
import org.egov.household.validators.household.HUniqueEntityValidator;
import org.egov.household.web.models.Household;
import org.egov.household.web.models.HouseholdBulkRequest;
import org.egov.household.web.models.HouseholdRequest;
import org.egov.household.web.models.HouseholdSearch;
import org.egov.tracer.model.CustomException;
import org.springframework.beans.factory.annotation.Autowired;
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
import static org.egov.common.utils.CommonUtils.havingTenantId;
import static org.egov.common.utils.CommonUtils.includeDeleted;
import static org.egov.common.utils.CommonUtils.isSearchByIdOnly;
import static org.egov.common.utils.CommonUtils.lastChangedSince;
import static org.egov.common.utils.CommonUtils.notHavingErrors;
import static org.egov.common.utils.CommonUtils.populateErrorDetails;
import static org.egov.household.Constants.SET_HOUSEHOLDS;
import static org.egov.household.Constants.VALIDATION_ERROR;

@Service
@Slf4j
public class HouseholdService {

    private final HouseholdRepository householdRepository;

    private final IdGenService idGenService;

    private final HouseholdConfiguration householdConfiguration;

    private final List<Validator<HouseholdBulkRequest, Household>> validators;

    private final HouseholdEnrichmentService enrichmentService;

    private final Predicate<Validator<HouseholdBulkRequest, Household>> isApplicableForUpdate = validator ->
            validator.getClass().equals(HNullIdValidator.class)
                    || validator.getClass().equals(HIsDeletedValidator.class)
                    || validator.getClass().equals(HUniqueEntityValidator.class)
                    || validator.getClass().equals(HNonExsistentEntityValidator.class)
                    || validator.getClass().equals(HRowVersionValidator.class);

    private final Predicate<Validator<HouseholdBulkRequest, Household>> isApplicableForDelete = validator ->
            validator.getClass().equals(HNullIdValidator.class)
                    || validator.getClass().equals(HNonExsistentEntityValidator.class)
                    || validator.getClass().equals(HRowVersionValidator.class);

    @Autowired
    public HouseholdService(HouseholdRepository householdRepository, IdGenService idGenService,
                            HouseholdConfiguration householdConfiguration, List<Validator<HouseholdBulkRequest, Household>> validators, HouseholdEnrichmentService enrichmentService) {
        this.householdRepository = householdRepository;
        this.idGenService = idGenService;
        this.householdConfiguration = householdConfiguration;
        this.validators = validators;
        this.enrichmentService = enrichmentService;
    }

    public Household create(HouseholdRequest request) throws Exception {
        HouseholdBulkRequest bulkRequest = HouseholdBulkRequest.builder()
                .households(Collections.singletonList(request.getHousehold()))
                .requestInfo(request.getRequestInfo()).build();
        return create(bulkRequest, false).get(0);
    }


    public List<Household> create(HouseholdBulkRequest request, boolean isBulk) throws Exception {

        Map<Household, ErrorDetails> errorDetailsMap = new HashMap<>();
        List<Household> validEntities = request.getHouseholds();
        try {
            if (!validEntities.isEmpty()) {
                enrichmentService.create(validEntities, request);
                householdRepository.save(validEntities, householdConfiguration.getCreateTopic());
            }
        } catch (Exception exception) {
            log.error("error occurred", exception);
            populateErrorDetails(request, errorDetailsMap, validEntities, exception, SET_HOUSEHOLDS);
        }

        handleErrors(isBulk, errorDetailsMap);
        return request.getHouseholds();
    }

    public List<Household> search(HouseholdSearch householdSearch, Integer limit, Integer offset, String tenantId,
                                  Long lastChangedSince, Boolean includeDeleted) {

        String idFieldName = getIdFieldName(householdSearch);
        if (isSearchByIdOnly(householdSearch, idFieldName)) {
            List<String> ids = (List<String>) ReflectionUtils.invokeMethod(getIdMethod(Collections
                            .singletonList(householdSearch)),
                    householdSearch);
            return householdRepository.findById(ids,
                    idFieldName, includeDeleted).stream()
                    .filter(lastChangedSince(lastChangedSince))
                    .filter(havingTenantId(tenantId))
                    .filter(includeDeleted(includeDeleted))
                    .collect(Collectors.toList());
        }
        try {
            return householdRepository.find(householdSearch, limit, offset,
                    tenantId, lastChangedSince, includeDeleted);
        } catch (QueryBuilderException e) {
            throw new CustomException("ERROR_IN_QUERY", e.getMessage());
        }
    }

    public Household update(HouseholdRequest request) {
        HouseholdBulkRequest bulkRequest = HouseholdBulkRequest.builder()
                .households(Collections.singletonList(request.getHousehold()))
                .requestInfo(request.getRequestInfo()).build();
        return update(bulkRequest, false).get(0);
    }

    public List<Household> update(HouseholdBulkRequest request, boolean isBulk) {
        Tuple<List<Household>, Map<Household, ErrorDetails>> tuple = validate(validators,
                isApplicableForUpdate, request,
                isBulk);
        Map<Household, ErrorDetails> errorDetailsMap = tuple.getY();
        List<Household> validEntities = tuple.getX();
        try {
            if (!validEntities.isEmpty()) {
                enrichmentService.update(validEntities, request);
                householdRepository.save(validEntities, householdConfiguration.getUpdateTopic());
            }
        } catch (Exception exception) {
            log.error("error occurred", exception);
            populateErrorDetails(request, errorDetailsMap, validEntities, exception, SET_HOUSEHOLDS);
        }

        handleErrors(isBulk, errorDetailsMap);
        return request.getHouseholds();
    }

    public Household delete(HouseholdRequest request) {
        HouseholdBulkRequest bulkRequest = HouseholdBulkRequest.builder()
                .households(Collections.singletonList(request.getHousehold()))
                .requestInfo(request.getRequestInfo()).build();
        return delete(bulkRequest, false).get(0);
    }

    public List<Household> delete(HouseholdBulkRequest request, boolean isBulk) {
        Tuple<List<Household>, Map<Household, ErrorDetails>> tuple = validate(validators,
                isApplicableForDelete, request,
                isBulk);
        Map<Household, ErrorDetails> errorDetailsMap = tuple.getY();
        List<Household> validEntities = tuple.getX();
        try {
            if (!validEntities.isEmpty()) {
                enrichmentService.delete(validEntities, request);
                householdRepository.save(validEntities, householdConfiguration.getDeleteTopic());
            }
        } catch (Exception exception) {
            log.error("error occurred", exception);
            populateErrorDetails(request, errorDetailsMap, validEntities, exception, SET_HOUSEHOLDS);
        }

        handleErrors(isBulk, errorDetailsMap);
        return request.getHouseholds();
    }

    public List<Household> findById(List<String> houseHoldIds, String columnName, boolean includeDeleted){
       return householdRepository.findById(houseHoldIds, columnName, includeDeleted);
    }

    private Tuple<List<Household>, Map<Household, ErrorDetails>> validate(List<Validator<HouseholdBulkRequest, Household>> validators,
                                                                Predicate<Validator<HouseholdBulkRequest, Household>> applicableValidators,
                                                                          HouseholdBulkRequest request, boolean isBulk) {
        log.info("validating request");
        Map<Household, ErrorDetails> errorDetailsMap = CommonUtils.validate(validators,
                applicableValidators, request,
                SET_HOUSEHOLDS);
        if (!errorDetailsMap.isEmpty() && !isBulk) {
            throw new CustomException(VALIDATION_ERROR, errorDetailsMap.values().toString());
        }
        List<Household> validTasks = request.getHouseholds().stream()
                .filter(notHavingErrors()).collect(Collectors.toList());
        return new Tuple<>(validTasks, errorDetailsMap);
    }

    private static void handleErrors(boolean isBulk, Map<Household, ErrorDetails> errorDetailsMap) {
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
