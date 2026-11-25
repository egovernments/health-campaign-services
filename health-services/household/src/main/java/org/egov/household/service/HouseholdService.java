package org.egov.household.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.egov.common.data.query.exception.QueryBuilderException;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.ds.Tuple;
import org.egov.common.exception.InvalidTenantIdException;
import org.egov.common.models.ErrorDetails;
import org.egov.common.models.core.SearchResponse;
import org.egov.common.models.household.Household;
import org.egov.common.models.household.HouseholdBulkRequest;
import org.egov.common.models.household.HouseholdRequest;
import org.egov.common.pii.EncCircuitBreaker;
import org.egov.common.service.IdGenService;
import org.egov.common.utils.CommonUtils;
import org.egov.common.validator.Validator;
import org.egov.household.config.HouseholdConfiguration;
import org.egov.household.repository.HouseholdRepository;
import org.egov.household.validators.household.*;
import org.egov.common.models.household.HouseholdSearch;
import org.egov.tracer.model.CustomException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.ReflectionUtils;

import java.util.Collections;
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
import static org.egov.household.Constants.*;

@Service
@Slf4j
public class HouseholdService {

    private final HouseholdRepository householdRepository;

    private final IdGenService idGenService;

    private final HouseholdConfiguration householdConfiguration;

    private final List<Validator<HouseholdBulkRequest, Household>> validators;

    private final HouseholdEnrichmentService enrichmentService;

    private final HouseholdPiiCryptoService piiCryptoService;

    private final Predicate<Validator<HouseholdBulkRequest, Household>> isApplicableForCreate = validator ->
            validator.getClass().equals(HBoundaryValidator.class)
                    || validator.getClass().equals(HExistentEntityValidator.class)
                    || validator.getClass().equals(HCommunityValidator.class)
                    || validator.getClass().equals(HCommunityTypeValidator.class)
                    || validator.getClass().equals(HAddressValueValidator.class);

    private final Predicate<Validator<HouseholdBulkRequest, Household>> isApplicableForUpdate = validator ->
            validator.getClass().equals(HNullIdValidator.class)
                    || validator.getClass().equals(HBoundaryValidator.class)
                    || validator.getClass().equals(HIsDeletedValidator.class)
                    || validator.getClass().equals(HUniqueEntityValidator.class)
                    || validator.getClass().equals(HNonExistentEntityValidator.class)
                    || validator.getClass().equals(HRowVersionValidator.class)
                    || validator.getClass().equals(HCommunityValidator.class)
                    || validator.getClass().equals(HCommunityTypeValidator.class)
                    || validator.getClass().equals(HHouseholdTypeChangeValidator.class)
                    || validator.getClass().equals(HAddressValueValidator.class);

    private final Predicate<Validator<HouseholdBulkRequest, Household>> isApplicableForDelete = validator ->
            validator.getClass().equals(HNullIdValidator.class)
                    || validator.getClass().equals(HNonExistentEntityValidator.class)
                    || validator.getClass().equals(HRowVersionValidator.class)
                    // delete also carries the client's address: a cipher-shaped value must not be stored or cached
                    || validator.getClass().equals(HAddressValueValidator.class);

    @Autowired
    public HouseholdService(HouseholdRepository householdRepository, IdGenService idGenService,
                            HouseholdConfiguration householdConfiguration, List<Validator<HouseholdBulkRequest, Household>> validators, HouseholdEnrichmentService enrichmentService,
                            HouseholdPiiCryptoService piiCryptoService) {
        this.householdRepository = householdRepository;
        this.idGenService = idGenService;
        this.householdConfiguration = householdConfiguration;
        this.validators = validators;
        this.enrichmentService = enrichmentService;
        this.piiCryptoService = piiCryptoService;
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
        return create(request, isBulk, false);
    }

    /**
     * Bulk create for the Kafka consumer. An encryption failure is thrown, not reported, so the consumer
     * parks the original encrypted message for replay: the records are not lost and no plain copy goes
     * to the error topic.
     */
    public List<Household> createFromQueue(HouseholdBulkRequest request) {
        return create(request, true, true);
    }

    private List<Household> create(HouseholdBulkRequest request, boolean isBulk, boolean fromQueue) {
        log.info("received request to create households");
        Tuple<List<Household>, Map<Household, ErrorDetails>> tuple = validate(validators,
                isApplicableForCreate, request,
                isBulk);
        Map<Household, ErrorDetails> errorDetailsMap = tuple.getY();
        List<Household> validEntities = tuple.getX();
        try {
            if (!validEntities.isEmpty()) {
                enrichmentService.create(validEntities, request);
                // Kafka (persister, indexer) and Redis get the stored form; the API answer stays plain
                householdRepository.save(piiCryptoService.toStoredForm(validEntities), householdConfiguration.getCreateTopic());
                log.info("successfully created {} households", validEntities.size());
            }
        } catch (Exception exception) {
            rethrowEncFailureFromQueue(exception, fromQueue);
            log.error("error occurred while creating households: {}", ExceptionUtils.getStackTrace(exception));
            populateErrorDetails(request, errorDetailsMap, validEntities, exception, SET_HOUSEHOLDS);
        }

        handleErrors(errorDetailsMap, isBulk, VALIDATION_ERROR);
        return request.getHouseholds();
    }

    public SearchResponse<Household> search(HouseholdSearch householdSearch, Integer limit, Integer offset, String tenantId,
                                            Long lastChangedSince, Boolean includeDeleted, RequestInfo requestInfo) {

        String idFieldName = getIdFieldName(householdSearch);
        if (isSearchByIdOnly(householdSearch, idFieldName)) {
            List<String> ids = (List<String>) ReflectionUtils.invokeMethod(getIdMethod(Collections
                            .singletonList(householdSearch)),
                    householdSearch);
            SearchResponse<Household> householdsTuple = null;
            try {
                householdsTuple = householdRepository.findById( tenantId,ids,
                        idFieldName, includeDeleted);
            } catch (InvalidTenantIdException e) {
                log.error("error occurred while searching households: {}", ExceptionUtils.getStackTrace(e));
                throw new CustomException(TENANT_ID_EXCEPTION, e.getMessage());
            }
            List<Household> households = householdsTuple.getResponse().stream()
                    .filter(lastChangedSince(lastChangedSince))
                    .filter(havingTenantId(tenantId))
                    .filter(includeDeleted(includeDeleted))
                    .collect(Collectors.toList());
            log.info("households found for search by id, size: {}", households.size());
            // the stored address values are cipher text: the API answers plain values
            households = piiCryptoService.decrypt(households, requestInfo);
            return SearchResponse.<Household>builder().totalCount(Long.valueOf(households.size())).response(households).build();
        }
        try {
            SearchResponse<Household> searchResponse;
            if(Boolean.TRUE.equals(isProximityBasedSearch(householdSearch))) {
                searchResponse = householdRepository.findByRadius(householdSearch, limit, offset, tenantId, includeDeleted);
            } else {
                searchResponse = householdRepository.find(householdSearch, limit, offset, tenantId, lastChangedSince, includeDeleted);
            }
            log.info("households found for search, size: {}", searchResponse.getResponse().size());
            searchResponse.setResponse(piiCryptoService.decrypt(searchResponse.getResponse(), requestInfo));
            return searchResponse;
        } catch (QueryBuilderException e) {
            log.error("error occurred while searching households: {}", ExceptionUtils.getStackTrace(e));
            throw new CustomException("ERROR_IN_QUERY", e.getMessage());
        } catch (InvalidTenantIdException e ) {
            log.error("error occurred while searching households: {}", ExceptionUtils.getStackTrace(e));
            throw new CustomException(TENANT_ID_EXCEPTION, e.getMessage());
        }
    }

    public Household update(HouseholdRequest request) {
        HouseholdBulkRequest bulkRequest = HouseholdBulkRequest.builder()
                .households(Collections.singletonList(request.getHousehold()))
                .requestInfo(request.getRequestInfo()).build();
        return update(bulkRequest, false).get(0);
    }

    public List<Household> update(HouseholdBulkRequest request, boolean isBulk) {
        return update(request, isBulk, false);
    }

    /** Bulk update for the Kafka consumer: an encryption failure is thrown so the consumer parks the message (see {@link #createFromQueue}). */
    public List<Household> updateFromQueue(HouseholdBulkRequest request) {
        return update(request, true, true);
    }

    private List<Household> update(HouseholdBulkRequest request, boolean isBulk, boolean fromQueue) {
        Tuple<List<Household>, Map<Household, ErrorDetails>> tuple = validate(validators,
                isApplicableForUpdate, request,
                isBulk);
        Map<Household, ErrorDetails> errorDetailsMap = tuple.getY();
        List<Household> validEntities = tuple.getX();
        try {
            if (!validEntities.isEmpty()) {
                log.info("updating valid entities");
                enrichmentService.update(validEntities, request);
                householdRepository.save(piiCryptoService.toStoredForm(validEntities), householdConfiguration.getUpdateTopic());
                log.info("successfully updated households");
            }
        } catch (Exception exception) {
            rethrowEncFailureFromQueue(exception, fromQueue);
            log.error("error occurred while updating households: {}" , ExceptionUtils.getStackTrace(exception));
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
        return delete(request, isBulk, false);
    }

    /** Bulk delete for the Kafka consumer: an encryption failure is thrown so the consumer parks the message (see {@link #createFromQueue}). */
    public List<Household> deleteFromQueue(HouseholdBulkRequest request) {
        return delete(request, true, true);
    }

    private List<Household> delete(HouseholdBulkRequest request, boolean isBulk, boolean fromQueue) {
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
                // the indexer also reads the delete topic, so the message carries the stored form, never the plain request
                householdRepository.save(piiCryptoService.toStoredForm(validEntities), householdConfiguration.getDeleteTopic());
                log.info("Households saved to delete topic");
            }
        } catch (Exception exception) {
            rethrowEncFailureFromQueue(exception, fromQueue);
            log.error("error occurred while deleting households: {}", ExceptionUtils.getStackTrace(exception));
            populateErrorDetails(request, errorDetailsMap, validEntities, exception, SET_HOUSEHOLDS);
        }

        handleErrors(errorDetailsMap, isBulk, VALIDATION_ERROR);
        return request.getHouseholds();
    }

    public SearchResponse<Household> findById(String tenantId, List<String> houseHoldIds, String columnName, boolean includeDeleted) throws InvalidTenantIdException {
        log.info("finding Households by Ids: {} with columnName: {} and includeDeleted: {}",
                houseHoldIds, columnName, includeDeleted);
        log.info("started finding Households by Ids");
        SearchResponse<Household> searchResponse = householdRepository.findById(tenantId, houseHoldIds, columnName, includeDeleted);
        log.info("finished finding Households by Ids. Found {} Households", searchResponse.getResponse().size());
        return searchResponse;
    }

    /**
     * Caches the stored form (address values encrypted), never the plain request.
     * Best effort: the bulk request is already on its way to Kafka, and the consumer caches the stored form again
     * after saving, so when enc-service cannot be reached here the cache is skipped, not the request.
     * A household with a cipher-shaped address value is not cached: the consumer rejects it (HAddressValueValidator).
     */
    public void putInCache(List<Household> households) {
        List<Household> stored;
        try {
            stored = piiCryptoService.toStoredForm(households == null ? null : households.stream()
                    .filter(household -> !HAddressValueValidator.hasCipherShapedValue(household))
                    .collect(Collectors.toList()));
        } catch (RuntimeException e) {
            log.warn("{} households not cached: could not encrypt them ({})", households.size(), e.getMessage());
            return;
        }
        log.info("putting {} households in cache", stored.size());
        householdRepository.putInCache(stored);
        log.info("successfully put households in cache");
    }

    private static void rethrowEncFailureFromQueue(Exception exception, boolean fromQueue) {
        if (fromQueue && EncCircuitBreaker.isEncFailure(exception)) {
            throw (CustomException) exception;
        }
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
