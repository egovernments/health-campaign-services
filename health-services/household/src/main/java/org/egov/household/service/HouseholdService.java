package org.egov.household.service;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.data.query.exception.QueryBuilderException;
import org.egov.common.ds.Tuple;
import org.egov.common.models.ErrorDetails;
import org.egov.common.models.household.Household;
import org.egov.common.models.household.HouseholdBulkRequest;
import org.egov.common.models.household.HouseholdRequest;
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
import static org.egov.common.utils.CommonUtils.handleErrors;
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

    public Household create(HouseholdRequest request) {
        log.info("received request to create household");
        HouseholdBulkRequest bulkRequest = HouseholdBulkRequest.builder()
                .households(Collections.singletonList(request.getHousehold()))
                .requestInfo(request.getRequestInfo()).build();
        log.info("converted request to bulk request");
        List<Household> createdHouseholds = create(bulkRequest, false);
        log.info("created households");
        return createdHouseholds.get(0);
    }

    public List<Household> create(HouseholdBulkRequest request, boolean isBulk) {
        log.info("received request to create households");
        Map<Household, ErrorDetails> errorDetailsMap = new HashMap<>();
        List<Household> validEntities = request.getHouseholds();
        try {
            if (!validEntities.isEmpty()) {
                enrichmentService.create(validEntities, request);
                householdRepository.save(validEntities, householdConfiguration.getCreateTopic());
                log.info("successfully created {} households", validEntities.size());
            }
        } catch (Exception exception) {
            log.error("error occurred while creating households", exception);
            populateErrorDetails(request, errorDetailsMap, validEntities, exception, SET_HOUSEHOLDS);
        }

        handleErrors(errorDetailsMap, isBulk, VALIDATION_ERROR);
        return request.getHouseholds();
    }

    public Tuple<Long, List<Household>> search(HouseholdSearch householdSearch, Integer limit, Integer offset, String tenantId,
                                  Long lastChangedSince, Boolean includeDeleted) {

        String idFieldName = getIdFieldName(householdSearch);
        if (isSearchByIdOnly(householdSearch, idFieldName)) {
            List<String> ids = (List<String>) ReflectionUtils.invokeMethod(getIdMethod(Collections
                            .singletonList(householdSearch)),
                    householdSearch);
            Tuple<Long, List<Household>> householdsTuple = householdRepository.findById(ids,
                    idFieldName, includeDeleted);
            List<Household> households = householdsTuple.getY().stream()
                    .filter(lastChangedSince(lastChangedSince))
                    .filter(havingTenantId(tenantId))
                    .filter(includeDeleted(includeDeleted))
                    .collect(Collectors.toList());
            log.info("households found for search by id, size: {}", households.size());
            return new Tuple<>(householdsTuple.getX(), households);
        }
        try {
            new Tuple<>(null, Collections.emptyList());
            Tuple<Long, List<Household>> householdsTuple;
            if(Boolean.TRUE.equals(isProximityBasedSearch(householdSearch))) {
                householdsTuple = householdRepository.findByRadius(householdSearch, limit, offset, tenantId, includeDeleted);
            } else {
                householdsTuple = householdRepository.find(householdSearch, limit, offset, tenantId, lastChangedSince, includeDeleted);
            }
            log.info("households found for search, size: {}", householdsTuple.getY().size());
            return householdsTuple;
        } catch (QueryBuilderException e) {
            log.error("error occurred while searching households", e);
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
                log.info("updating valid entities");
                enrichmentService.update(validEntities, request);
                householdRepository.save(validEntities, householdConfiguration.getUpdateTopic());
                log.info("successfully updated households");
            }
        } catch (Exception exception) {
            log.error("error occurred while updating households: " + exception.getMessage());
            populateErrorDetails(request, errorDetailsMap, validEntities, exception, SET_HOUSEHOLDS);
        }

        handleErrors(errorDetailsMap, isBulk, VALIDATION_ERROR);
        log.info("returning updated households");
        return request.getHouseholds();
    }

    public Household delete(HouseholdRequest request) {
        log.info("deleting Household with id: {}", request.getHousehold().getId());
        HouseholdBulkRequest bulkRequest = HouseholdBulkRequest.builder()
                .households(Collections.singletonList(request.getHousehold()))
                .requestInfo(request.getRequestInfo()).build();
        List<Household> deletedHouseholds = delete(bulkRequest, false);
        log.info("successfully deleted Household with id: {}", request.getHousehold().getId());
        return deletedHouseholds.get(0);
    }

    public List<Household> delete(HouseholdBulkRequest request, boolean isBulk) {
        log.info("deleting households, isBulk={}", isBulk);
        Tuple<List<Household>, Map<Household, ErrorDetails>> tuple = validate(validators,
                isApplicableForDelete, request,
                isBulk);
        Map<Household, ErrorDetails> errorDetailsMap = tuple.getY();
        List<Household> validEntities = tuple.getX();
        try {
            if (!validEntities.isEmpty()) {
                enrichmentService.delete(validEntities, request);
                log.info("households deleted successfully");
                householdRepository.save(validEntities, householdConfiguration.getDeleteTopic());
                log.info("Households saved to delete topic");
            }
        } catch (Exception exception) {
            log.error("error occurred while deleting households: ", exception);
            populateErrorDetails(request, errorDetailsMap, validEntities, exception, SET_HOUSEHOLDS);
        }

        handleErrors(errorDetailsMap, isBulk, VALIDATION_ERROR);
        return request.getHouseholds();
    }

    public Tuple<Long, List<Household>> findById(List<String> houseHoldIds, String columnName, boolean includeDeleted){
        log.info("finding Households by Ids: {} with columnName: {} and includeDeleted: {}",
                houseHoldIds, columnName, includeDeleted);
        log.info("started finding Households by Ids");
        Tuple<Long, List<Household>> householdsTuple = householdRepository.findById(houseHoldIds, columnName, includeDeleted);
        log.info("finished finding Households by Ids. Found {} Households", householdsTuple.getY().size());
        return householdsTuple;
    }

    public void putInCache(List<Household> households) {
        log.info("putting {} households in cache", households.size());
        householdRepository.putInCache(households);
        log.info("successfully put households in cache");
    }

    private Tuple<List<Household>, Map<Household, ErrorDetails>> validate(List<Validator<HouseholdBulkRequest, Household>> validators,
                                                                Predicate<Validator<HouseholdBulkRequest, Household>> applicableValidators,
                                                                          HouseholdBulkRequest request, boolean isBulk) {
        log.info("validating the request for households");
        Map<Household, ErrorDetails> errorDetailsMap = CommonUtils.validate(validators,
                applicableValidators, request,
                SET_HOUSEHOLDS);
        if (!errorDetailsMap.isEmpty() && !isBulk) {
            log.error("validation error occurred. Error details: {}", errorDetailsMap.values().toString());
            throw new CustomException(VALIDATION_ERROR, errorDetailsMap.values().toString());
        }
        List<Household> validHouseholds = request.getHouseholds().stream()
                .filter(notHavingErrors()).collect(Collectors.toList());
        log.info("number of valid households after validation: {}", validHouseholds.size());
        return new Tuple<>(validHouseholds, errorDetailsMap);
    }

    private Boolean isProximityBasedSearch(HouseholdSearch householdSearch) {
        return householdSearch.getLatitude() != null && householdSearch.getLongitude() != null && householdSearch.getSearchRadius() != null;
    }
}
