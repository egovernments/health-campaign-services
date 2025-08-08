package org.egov.id.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.egov.common.contract.models.AuditDetails;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.ds.Tuple;
import org.egov.common.models.ErrorDetails;
import org.egov.common.models.core.AdditionalFields;
import org.egov.common.models.idgen.*;
import org.egov.common.models.Error;
import org.egov.common.utils.CommonUtils;
import org.egov.common.utils.ResponseInfoUtil;
import org.egov.id.config.PropertiesManager;
import org.egov.id.config.RedissonLockManager;
import org.egov.id.producer.IdGenProducer;
import org.egov.id.repository.IdRepository;
import org.egov.id.repository.RedisRepository;
import org.egov.id.validators.IdPoolValidatorForUpdate;
import org.egov.tracer.model.CustomException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.egov.common.validator.Validator;
import org.springframework.util.ObjectUtils;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.egov.common.utils.CommonUtils.*;
import static org.egov.id.utils.Constants.*;


/**
 * Service class responsible for dispatching IDs to users based on their requests.
 * It handles validation, locking, updating statuses, and publishing events to Kafka.
 * Also provides methods for searching and updating ID records.
 */
@Service
@Slf4j
public class IdDispatchService {

    private final RedisRepository redisRepo;
    private final IdRepository idRepo;
    private final RedissonLockManager lockManager;
    private final IdGenProducer idGenProducer;
    private final PropertiesManager propertiesManager;

    private final int configuredLimit;
    private final int dbFetchCount;


    private final List<Validator<IdRecordBulkRequest, IdRecord>> validators;

    private EnrichmentService enrichmentService;

    /*
        * Constructor for IdDispatchService.
        * Initializes repositories, lock manager, producer, properties manager, and validators.
        *
        * @param redisRepo Redis repository for caching and dispatch count management.
        * @param idRepo Database repository for ID records.
        * @param lockManager Lock manager for distributed locking.
        * @param idGenProducer Kafka producer for ID generation events.
        * @param propertiesManager Properties manager for configuration values.
        * @param validators List of validators to apply on ID records.
        * @param enrichmentservice Service for enriching ID records with additional metadata.
        */
    @Autowired
    public IdDispatchService(RedisRepository redisRepo,
                             IdRepository idRepo,
                             RedissonLockManager lockManager,
                             IdGenProducer idGenProducer,
                             PropertiesManager propertiesManager,
                             List<Validator<IdRecordBulkRequest, IdRecord>> validators,
                             EnrichmentService enrichmentservice) {
        this.redisRepo = redisRepo;
        this.idRepo = idRepo;
        this.lockManager = lockManager;
        this.idGenProducer = idGenProducer;
        this.propertiesManager = propertiesManager;
        this.configuredLimit = propertiesManager.getDispatchLimitPerUser();
        this.dbFetchCount = propertiesManager.getDbFetchLimit();
        this.validators = validators;
        this.enrichmentService  = enrichmentservice;
    }


    private final Predicate<Validator<IdRecordBulkRequest, IdRecord>> isApplicableForUpdate = validator ->
            validator.getClass().equals(IdPoolValidatorForUpdate.class);


    /**
     * Dispatches a specified count of IDs to a user after validating limits, locking IDs, and updating status.
     * Handles fetching previously allocated IDs if requested.
     * Ensures concurrency safety using locks and updates Redis counters accordingly.
     *
     * @param request the ID dispatch request containing user info, device info, tenant, and count
     * @return IdDispatchResponse containing the dispatched IDs and relevant metadata
     * @throws Exception if any validation or locking operation fails
     */
    public IdDispatchResponse dispatchIds(IdDispatchRequest request) throws Exception {

        // Validate that User UUID is provided in the request, else throw exception
        if (StringUtils.isEmpty(request.getRequestInfo().getUserInfo().getUuid())) {
            throw new CustomException("VALIDATION EXCEPTION",
                    "Missing User Uuid");
        }

        // Extract necessary info from request for processing
        String userUuid = request.getRequestInfo().getUserInfo().getUuid();
        int count = request.getClientInfo().getCount();
        String deviceUuid = request.getClientInfo().getDeviceUuid();
        RequestInfo requestInfo = request.getRequestInfo();
        String tenantId = request.getClientInfo().getTenantId();

        // Log incoming dispatch request details
        log.info("Dispatch request received: userUuid={}, deviceUuid={}, tenantId={}, count={}", userUuid, deviceUuid, tenantId, count);

        Long fetchLimit;

        // Get count of IDs already dispatched to this user and device from Redis cache
        int alreadyDispatched = redisRepo.getDispatchedCount(userUuid, deviceUuid);
        log.info("Already dispatched count from Redis for user={} and device={} is {}", userUuid, deviceUuid, alreadyDispatched);

        // If requested count is <= 0, calculate default count based on configured limit and already dispatched
        if (count <= 0 ) {
            count = Math.min(configuredLimit - alreadyDispatched, configuredLimit);
        }

        // Handle special case: if client requests fetching previously allocated IDs instead of new dispatch
        if (!ObjectUtils.isEmpty(request.getClientInfo().getFetchAllocatedIds())
                && request.getClientInfo().getFetchAllocatedIds()) {
            log.debug("FetchAllocatedIds flag is true, fetching previously allocated IDs.");

            // Fetch all IDs already dispatched to this user/device
            IdDispatchResponse idDispatchResponse = fetchAllDispatchedIds(request);

            // Update already dispatched count and calculate remaining fetch limit
            alreadyDispatched = (int) Math.max(alreadyDispatched, idDispatchResponse.getTotalCount());
            fetchLimit = Math.max(0L, configuredLimit - alreadyDispatched);
            log.debug("Total previously allocated: {}, updated fetchLimit: {}", alreadyDispatched, fetchLimit);

            // Set fetch limits in the response and return
            idDispatchResponse.setFetchLimit(fetchLimit);
            idDispatchResponse.setTotalLimit((long) configuredLimit);
            return idDispatchResponse;
        }

        // Validate the dispatch request against configured limits
        validateDispatchRequest(request);
        log.debug("Dispatch request validation passed.");

        // Calculate fetch limit remaining after this request
        fetchLimit = Math.max(0L, configuredLimit - (alreadyDispatched + count));
        log.info("Calculated fetch limit for new IDs: {}", fetchLimit);

        // Check if total requested exceeds configured limit, if so throw exception
        if (alreadyDispatched + count > configuredLimit ) {
            log.warn("User {} with device {} exceeded dispatch limit. Allowed={}, Requested={}, Already={}",
                    userUuid, deviceUuid, configuredLimit, count, alreadyDispatched);
            throw new CustomException("ID LIMIT EXCEPTION",
                    "ID generation limit exceeded for user: " + userUuid + " with the deviceId: " + deviceUuid);
        }

        // Fetch IDs either from Redis or DB to fulfill the count requested
        List<IdRecord> selected = fetchOrRefillIds(tenantId, count);
        log.debug("Fetched {} IDs for dispatch.", selected.size());

        // Throw exception if no IDs were fetched
        if (selected == null || selected.isEmpty()) {
            log.error("No IDs available from Redis or DB for tenantId: {}", tenantId);
            throw new CustomException("NO IDS AVAILABLE", "Unable to fetch IDs from Redis or DB");
        }

        // Collect the list of ID strings to attempt locking
        List<String> lockedIds = selected.stream().map(IdRecord::getId).collect(Collectors.toList());

        // Validate the locked ID list is not empty
        if (lockedIds.isEmpty()) {
            log.error("Fetched ID list is empty after filtering.");
            throw new CustomException("INVALID ID LIST", "Selected ID list is empty or invalid");
        }

        // Attempt to acquire locks on the selected IDs before updating status
        log.info("Attempting to lock {} IDs: {}", lockedIds.size(), lockedIds);
        if (!lockManager.lockRecords(lockedIds)) {
            log.error("Failed to acquire lock for IDs: {}", lockedIds);
            throw new CustomException("LOCKING ERROR", "Unable to lock IDs.");
        }

        try {
            // Once locked, update statuses and logs, then increment dispatched count in Redis
            log.debug("Successfully locked IDs. Proceeding with update and logging.");
            updateStatusesAndLogs(selected, userUuid, deviceUuid, request.getClientInfo().getDeviceInfo(), tenantId, requestInfo);
            redisRepo.incrementDispatchedCount(userUuid, deviceUuid, count);
            log.debug("Dispatch count updated in Redis for user: {} and device: {}", userUuid, deviceUuid);
        } finally {
            // Always release locks regardless of success/failure to avoid deadlocks
            lockManager.releaseLocks(lockedIds);
            log.debug("Released locks for IDs: {}", lockedIds);
        }

        // Normalize additional fields for all dispatched IDs before returning
        selected.stream().forEach(idRecord -> { normalizeAdditionalFields(idRecord); });

        // Build and return the successful dispatch response including dispatched IDs and limits
        log.debug("Returning dispatch response with {} IDs", selected.size());
        return IdDispatchResponse.builder()
                .responseInfo(ResponseInfoUtil.createResponseInfoFromRequestInfo(requestInfo, true))
                .idResponses(selected)
                .fetchLimit(fetchLimit)
                .totalLimit((long) configuredLimit)
                .build();
    }

    /**
     * Fetches all IDs previously dispatched to a given user/device from transaction logs and maps them to IdRecords.
     * Throws an exception if no IDs found.
     *
     * @param request the dispatch request containing user and device info
     * @return IdDispatchResponse containing previously dispatched IDs and response metadata
     */
    private IdDispatchResponse fetchAllDispatchedIds(IdDispatchRequest request) {
        // Extract user and device info
        String userUuid = request.getRequestInfo().getUserInfo().getUuid();
        String deviceUuid = request.getClientInfo().getDeviceUuid();
        String tenantId = request.getClientInfo().getTenantId();

        // Log the fetch operation
        log.info("Fetching dispatched IDs for userUuid={}, deviceUuid={}, tenantId={}", userUuid, deviceUuid, tenantId);

        // Query transaction logs for all IDs dispatched to the user/device
        List<IdTransactionLog> idTransactionLogs = idRepo.selectClientDispatchedIds(
                tenantId,
                deviceUuid,
                userUuid,
                null
        );
        log.info("Fetched {} transaction logs for userUuid={}, deviceUuid={}", idTransactionLogs.size(), userUuid, deviceUuid);

        // Extract unique ID strings from the transaction logs
        List<String> ids = idTransactionLogs.stream()
                .map(IdTransactionLog::getId)
                .collect(Collectors.toList());
        log.info("Extracted {} unique ID(s) from transaction logs: {}", ids.size(), ids);

        // Throw exception if no dispatched IDs found
        if (ids.isEmpty()) {
            throw new CustomException("NO IDS Dispatched", "NO IDS Dispatched: No IDs found for the given user and device.");
        }

        // Map the extracted IDs back to IdRecord objects from DB
        List<IdRecord> records = idRepo.findByIDsAndStatus(ids, null, tenantId);
        log.info("Mapped {} ID(s) to IdRecord(s) from DB", records.size());

        // Normalize additional fields on the fetched records before returning
        records.stream().forEach(idRecord -> { normalizeAdditionalFields(idRecord); });

        // Build and return the response with previously dispatched IDs
        return IdDispatchResponse.builder()
                .responseInfo(ResponseInfoUtil.createResponseInfoFromRequestInfo(request.getRequestInfo(), true))
                .idResponses(records)
                .build();
    }


    /**
     * Searches for ID records based on given criteria: tenant ID, status, and optional list of IDs.
     * Logs the search parameters and number of records found.
     * Constructs and returns an IdDispatchResponse containing the matched records and response metadata.
     */
    public IdDispatchResponse searchIds(RequestInfo requestInfo, IdPoolSearch idPoolSearch) {
        // Log the search parameters including tenant ID, status, and the size of the ID list if present
        log.debug("Searching ID records with params - tenantId: {}, status: {}, idList size: {}",
                idPoolSearch.getTenantId(),
                idPoolSearch.getStatus(),
                idPoolSearch.getIdList() != null ? idPoolSearch.getIdList().size() : 0);

        // Query the database repository for ID records matching the provided ID list, status, and tenant ID
        List<IdRecord> records = idRepo.findByIDsAndStatus(
                idPoolSearch.getIdList(),
                idPoolSearch.getStatus(),
                idPoolSearch.getTenantId()
        );

        // Log the number of records found matching the search criteria
        log.debug("Found {} ID records matching the criteria.", records.size());

        // Build and return the response including the found ID records and response info derived from the request
        return IdDispatchResponse.builder()
                .idResponses(records)
                .responseInfo(ResponseInfoUtil.createResponseInfoFromRequestInfo(requestInfo, true))
                .build();
    }

    /**
     * Normalizes the AdditionalFields of an IdRecord by clearing it
     * if all subfields (schema, version, fields) are null.
     * This helps avoid storing empty AdditionalFields objects.
     */
    public static void normalizeAdditionalFields(IdRecord record) {
        // Check if AdditionalFields is not null
        if (record.getAdditionalFields() != null) {
            AdditionalFields af = record.getAdditionalFields();

            // If all subfields are null, set AdditionalFields to null
            if (af.getSchema() == null && af.getVersion() == null && af.getFields() == null) {
                record.setAdditionalFields(null);
            }
        }
    }


    /**
     * Validates the incoming dispatch request to ensure the requested ID count
     * does not exceed the configured per-user dispatch limit.
     * Also checks the cumulative dispatched count for the user-device combination.
     */
    private void validateDispatchRequest(IdDispatchRequest request) {
        // Extract client and request information
        ClientInfo userInfo = request.getClientInfo();
        RequestInfo requestInfo = request.getRequestInfo();
        String userUuid = requestInfo.getUserInfo().getUuid();
        String deviceUuid = userInfo.getDeviceUuid();
        Integer count = userInfo.getCount();

        // Log details about the incoming request
        log.debug("Validating dispatch request for userUuid: {}, deviceUuid: {}, requested count: {}", userUuid, deviceUuid, count);
        log.debug("Configured dispatch limit per user: {}", configuredLimit);

        // Check if requested count exceeds the configured limit per request
        if (count > configuredLimit) {
            log.warn("Dispatch request count {} exceeds configured limit {}", count, configuredLimit);
            // Throw exception if request count exceeds allowed limit
            throw new CustomException("COUNT EXCEEDS LIMIT",
                    "Requested count exceeds maximum allowed limit per user: " + configuredLimit);
        }

        // Fetch how many IDs have already been dispatched for this user-device pair
        int alreadyDispatched = redisRepo.getDispatchedCount(userUuid, deviceUuid);
        log.debug("Already dispatched count for userUuid: {}, deviceUuid: {} is {}", userUuid, deviceUuid, alreadyDispatched);

        // Check if cumulative count (already dispatched + new request) exceeds limit
        if (alreadyDispatched + count > configuredLimit) {
            log.warn("Total dispatched + requested count ({}) exceeds configured limit for userUuid: {}, deviceUuid: {}", alreadyDispatched + count, userUuid, deviceUuid);
            // Throw exception if total exceeds allowed limit
            throw new CustomException("ID LIMIT EXCEPTION",
                    "ID generation limit exceeded for user: " + userUuid + " with the deviceId: " + deviceUuid);
        }

        // Log successful validation
        log.debug("Dispatch request validation passed for userUuid: {}, deviceUuid: {}", userUuid, deviceUuid);
    }


    /**
     * Fetches the required number of unassigned IDs from Redis cache.
     * If Redis does not contain enough IDs, fetches additional unassigned IDs from the database,
     * refills the Redis cache, and then retries the fetch from Redis.
     */
    private List<IdRecord> fetchOrRefillIds(String tenantId, int count) {
        // Attempt to fetch 'count' unassigned IDs from Redis
        log.debug("Attempting to fetch {} unassigned IDs for tenant: {}", count, tenantId);
        List<IdRecord> selected = redisRepo.selectUnassignedIds(count);
        log.debug("Fetched {} unassigned IDs from Redis cache", selected.size());

        // If Redis does not have enough IDs, fetch more from the DB and refill Redis
        if (selected.size() < count) {
            int remaining = count - selected.size();
            log.debug("Redis cache insufficient by {} IDs. Fetching {} IDs from DB for tenant: {}", remaining, dbFetchCount, tenantId);

            // Fetch a batch of unassigned IDs from the database
            List<IdRecord> fromDb = idRepo.fetchUnassigned(tenantId, dbFetchCount);
            log.debug("Fetched {} unassigned IDs from DB for tenant: {}", fromDb.size(), tenantId);

            // Add the fetched DB IDs to Redis cache
            redisRepo.addToRedisCache(fromDb);
            log.debug("Added {} IDs from DB to Redis cache", fromDb.size());

            // Retry fetching the required number of IDs from Redis after refill
            selected = redisRepo.selectUnassignedIds(count);
            log.debug("Re-fetched {} unassigned IDs from Redis cache after refill", selected.size());
        }

        // Return the list of selected IDs (from Redis)
        return selected;
    }


    /**
     * Updates Redis cache status to DISPATCHED, removes processed IDs from unassigned Redis cache,
     * enriches them, and publishes both status and transaction logs to Kafka.
     *
     * @param selected     List of ID records that have been successfully dispatched.
     * @param userUuid     UUID of the user who initiated the dispatch.
     * @param deviceUuid   UUID of the user's device.
     * @param deviceInfo   Metadata about the device (optional or object-based).
     * @param tenantId     Tenant identifier for multi-tenant isolation.
     * @param requestInfo  Metadata about the request (timestamp, user info, etc.).
     */
    private void updateStatusesAndLogs(List<IdRecord> selected, String userUuid, String deviceUuid,
                                       Object deviceInfo, String tenantId, RequestInfo requestInfo) {
        // Update the Redis cache marking records as DISPATCHED
        log.info("Updating status to DISPATCHED for {} IDs in Redis", selected.size());
        redisRepo.updateStatusToDispatched(selected);

        // Remove records from the Redis unassigned cache as they are now dispatched
        log.info("Removing {} IDs from Redis unassigned cache", selected.size());
        redisRepo.removeFromUnassigned(selected);

        // Enrich records with audit and status metadata before publishing to Kafka
        log.info("Enriching status for update before sending Kafka update");
        enrichmentService.enrichStatusForUpdate(
                selected,
                IdRecordBulkRequest.builder()
                        .idRecords(selected)
                        .requestInfo(requestInfo)
                        .build(),
                IdStatus.DISPATCHED.name()
        );

        // Prepare payload to update ID status in Kafka
        Map<String, Object> payloadToUpdate = new HashMap<>();
        payloadToUpdate.put("idPool", selected);
        log.info("Pushing {} IDs to Kafka topic: {}", selected.size(), propertiesManager.getUpdateIdPoolStatusTopic());
        idGenProducer.push(propertiesManager.getUpdateIdPoolStatusTopic(), payloadToUpdate);

        // Prepare and push transaction logs to Kafka for tracking
        Map<String, Object> payloadToUpdateTransactionLog = buildTransactionLogPayload(
                selected, userUuid, deviceUuid, deviceInfo, tenantId, IdStatus.DISPATCHED.name());
        log.info("Pushing dispatch logs for {} IDs to Kafka topic: {}", selected.size(), propertiesManager.getSaveIdDispatchLogTopic());
        idGenProducer.push(propertiesManager.getSaveIdDispatchLogTopic(), payloadToUpdateTransactionLog);
    }


    /**
     * Builds Kafka payload containing transaction logs for dispatched ID records.
     *
     * @param selected   List of dispatched ID records.
     * @param userUuid   UUID of the user who triggered the dispatch.
     * @param deviceUuid UUID of the device used during dispatch.
     * @param deviceInfo Additional device information.
     * @param tenantId   Tenant ID for the records.
     * @param status     Optional override status for the transaction logs.
     * @return           Kafka payload map containing list of transaction logs.
     */
    private Map<String, Object> buildTransactionLogPayload(List<IdRecord> selected, String userUuid,
                                                           String deviceUuid, Object deviceInfo,
                                                           String tenantId, String status) {
        // Build a transaction log for each ID record
        List<IdTransactionLog> logs = selected.stream().map(record -> {
            // Determine status: either provided explicitly or fall back to the record's existing status
            String updateStatus = "";
            if (StringUtils.isNotBlank(status)) {
                updateStatus = status;
            } else if (StringUtils.isNotBlank(record.getStatus())) {
                updateStatus = record.getStatus();
            }

            // Construct a transaction log entry with audit and metadata
            return IdTransactionLog.builder()
                    .tenantId(tenantId)
                    .id(record.getId())
                    .auditDetails(AuditDetails.builder()
                            .createdBy(userUuid)
                            .createdTime(System.currentTimeMillis())
                            .build())
                    .rowVersion(1)
                    .status(updateStatus)
                    .userUuid(userUuid)
                    .deviceUuid(deviceUuid)
                    .deviceInfo(deviceInfo)
                    .build();
        }).collect(Collectors.toList());

        // Return the list of logs wrapped in a Kafka payload map
        Map<String, Object> payload = new HashMap<>();
        payload.put("idTransactionLog", logs);
        return payload;
    }

    /**
     * Validates the list of ID records in the given request using the provided validators.
     *
     * @param validators               List of validators to be applied on each IdRecord.
     * @param isApplicableForCreate   Predicate to filter applicable validators for create operation.
     * @param request                 Incoming request containing a list of ID records to validate.
     * @param isBulk                  Flag to indicate whether this is a bulk operation.
     * @return                        A Tuple containing list of valid ID records and map of invalid records with error details.
     */
    private Tuple<List<IdRecord>, Map<IdRecord, ErrorDetails>> validate(
            List<Validator<IdRecordBulkRequest, IdRecord>> validators,
            Predicate<Validator<IdRecordBulkRequest, IdRecord>> isApplicableForCreate,
            IdRecordBulkRequest request, boolean isBulk) {

        log.info("Validating request");

        // Run all applicable validators and collect error details for failed records
        Map<IdRecord, ErrorDetails> errorDetailsMap = CommonUtils.validate(validators,
                isApplicableForCreate, request, SET_ID_RECORDS);

        // If there are validation errors and it is not a bulk request, immediately throw a CustomException
        if (!errorDetailsMap.isEmpty() && !isBulk) {
            Set<String> hashset = new HashSet<>();
            for (Map.Entry<IdRecord, ErrorDetails> entry : errorDetailsMap.entrySet()) {
                List<Error> errors = entry.getValue().getErrors();
                // Collect all distinct error codes
                hashset.addAll(errors.stream().map(Error::getErrorCode).collect(Collectors.toSet()));
            }
            throw new CustomException(String.join(":", hashset), errorDetailsMap.values().toString());
        }

        // Filter out valid records (those not associated with errors)
        List<IdRecord> validIdRecords = request.getIdRecords().stream()
                .filter(notHavingErrors()).collect(Collectors.toList());

        // Return the tuple of valid records and error details map
        return new Tuple<>(validIdRecords, errorDetailsMap);
    }


    /**
     * Updates a list of ID records based on the input request.
     * Performs validation, enrichment, Kafka publication, and transaction logging.
     *
     * @param request The request containing ID records and request metadata.
     * @param isBulk  Flag to indicate if the request is a bulk update.
     * @return        List of successfully updated ID records.
     */
    public List<IdRecord> update(IdRecordBulkRequest request, boolean isBulk) {
        log.info("Starting update process for IdRecordBulkRequest. Bulk mode: {}", isBulk);

        // Step 1: Validate the request and extract valid records and errors
        Tuple<List<IdRecord>, Map<IdRecord, ErrorDetails>> tuple = validate(
                validators, isApplicableForUpdate, request, isBulk
        );
        Map<IdRecord, ErrorDetails> errorDetailsMap = tuple.getY();
        List<IdRecord> validIdRecords = tuple.getX();

        log.info("Validation complete. Valid records count: {}, Errors found: {}",
                validIdRecords.size(), errorDetailsMap.size());

        try {
            // Step 2: Enrich and process only if valid records exist
            if (!validIdRecords.isEmpty()) {
                log.info("Processing {} valid ID records for update.", validIdRecords.size());

                // Enrich valid records with metadata
                log.info("Calling enrichment service for valid ID records.");
                enrichmentService.update(validIdRecords, request);

                // Step 3: Push enriched records to Kafka for further async processing
                Map<String, Object> payload = new HashMap<>();
                payload.put("idPool", validIdRecords);
                log.info("Pushing enriched records to Kafka topic: {}", propertiesManager.getUpdateIdPoolStatusTopic());
                idGenProducer.push(propertiesManager.getUpdateIdPoolStatusTopic(), payload);

                // Step 4: Prepare user UUID for logging
                String userUuid;
                if (!ObjectUtils.isEmpty(request.getRequestInfo().getUserInfo()) &&
                        !ObjectUtils.isEmpty(request.getRequestInfo().getUserInfo().getUuid())) {
                    userUuid = request.getRequestInfo().getUserInfo().getUuid();
                } else {
                    userUuid = request.getRequestInfo().getUserInfo().toString();  // fallback if UUID is null
                }

                // Step 5: Prepare and push transaction log to Kafka
                Map<String, Object> payloadToUpdateTransactionLog = buildTransactionLogPayload(
                        validIdRecords, userUuid, SYSTEM_UPDATED, "", validIdRecords.get(0).getTenantId(),
                        null
                );
                log.info("Pushing transaction logs to Kafka topic: {}", propertiesManager.getSaveIdDispatchLogTopic());
                idGenProducer.push(propertiesManager.getSaveIdDispatchLogTopic(), payloadToUpdateTransactionLog);
            }
        } catch (Exception exception) {
            // Handle unexpected runtime exceptions
            log.error("Exception occurred while processing update for ID records: {}",
                    ExceptionUtils.getStackTrace(exception));

            // Populate error details for failed records
            populateErrorDetails(request, errorDetailsMap, validIdRecords, exception, SET_ID_RECORDS);
        }

        // Step 6: Handle and optionally throw validation errors
        handleErrors(errorDetailsMap, isBulk, VALIDATION_ERROR);

        log.info("Update process completed. Returning {} records.", validIdRecords.size());
        return validIdRecords;
    }


}
