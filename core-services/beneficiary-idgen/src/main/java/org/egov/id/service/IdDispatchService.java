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
import org.egov.id.producer.IdGenProducer;
import org.egov.id.repository.IdRepository;
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

    private final IdRepository idRepo;
    private final IdGenProducer idGenProducer;
    private final PropertiesManager propertiesManager;

    @Autowired
    private RedissonIDService redissonIDService;


    private final List<Validator<IdRecordBulkRequest, IdRecord>> validators;

    private EnrichmentService enrichmentService;

    /*
        * Constructor for IdDispatchService.
        * Initializes repositories, lock manager, producer, properties manager, and validators.
        *
        * @param idRepo Database repository for ID records.
        * @param lockManager Lock manager for distributed locking.
        * @param idGenProducer Kafka producer for ID generation events.
        * @param propertiesManager Properties manager for configuration values.
        * @param validators List of validators to apply on ID records.
        * @param enrichmentservice Service for enriching ID records with additional metadata.
        */
    @Autowired
    public IdDispatchService(IdRepository idRepo,
                             IdGenProducer idGenProducer,
                             PropertiesManager propertiesManager,
                             List<Validator<IdRecordBulkRequest, IdRecord>> validators,
                             EnrichmentService enrichmentservice) {
        this.idRepo = idRepo;
        this.idGenProducer = idGenProducer;
        this.propertiesManager = propertiesManager;
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
    public IdDispatchResponse dispatchIds(IdDispatchRequest request, Integer limit, Integer offset) throws Exception {

        // Validate that User UUID is provided in the request, else throw exception
        if (StringUtils.isEmpty(request.getRequestInfo().getUserInfo().getUuid())) {
            throw new CustomException("VALIDATION EXCEPTION", "Missing User Uuid");
        }

        // Extract necessary info from request for processing
        String userUuid = request.getRequestInfo().getUserInfo().getUuid();
        int count = request.getClientInfo().getCount();
        String deviceUuid = request.getClientInfo().getDeviceUuid();
        RequestInfo requestInfo = request.getRequestInfo();
        String tenantId = request.getClientInfo().getTenantId();

        // Log incoming dispatch request details
        log.debug("Dispatch request received: userUuid={}, deviceUuid={}, tenantId={}, count={}", userUuid, deviceUuid, tenantId, count);

        long totalLimit = propertiesManager.getDispatchLimitUserDeviceTotal();
        if (propertiesManager.isDispatchLimitUserDevicePerDayEnabled()) {
            totalLimit = propertiesManager.getDispatchLimitUserDevicePerDay();
        }

        // Handle special case: if client requests fetching previously allocated IDs instead of new dispatch
        if (!ObjectUtils.isEmpty(request.getClientInfo().getFetchAllocatedIds())
                && request.getClientInfo().getFetchAllocatedIds()) {
            // Get count of IDs already dispatched to this user and device from Redis cache
            long remainingCount = redissonIDService.getUserDeviceDispatchedIDRemaining(tenantId, userUuid, deviceUuid, false, propertiesManager.isIdDispatchRetrievalRestrictToTodayEnabled());
            log.debug("FetchAllocatedIds flag is true, fetching previously allocated IDs.");

            // Fetch all IDs already dispatched to this user/device
            Tuple<IdDispatchResponse, Long> idDispatchLogs = fetchAllUserDeviceIds(request, limit, offset, propertiesManager.isIdDispatchRetrievalRestrictToTodayEnabled());
            IdDispatchResponse idDispatchResponse = idDispatchLogs.getX();
            Long totalCount = idDispatchLogs.getY();
            long actualRemainingCount = totalLimit - totalCount;
            if(remainingCount != actualRemainingCount) {
                redissonIDService.updateUserDeviceDispatchedIDCount(tenantId, userUuid, deviceUuid, totalCount, false, propertiesManager.isIdDispatchRetrievalRestrictToTodayEnabled());
            }
            // Set fetch limits in the response and return
            idDispatchResponse.setFetchLimit(totalCount - (offset + idDispatchResponse.getIdResponses().size()));
            idDispatchResponse.setTotalLimit(totalLimit);
            idDispatchResponse.setTotalCount(totalCount);
            return idDispatchResponse;
        }

        // Get count of IDs already dispatched to this user and device from Redis cache
        long remainingCount = redissonIDService.getUserDeviceDispatchedIDRemaining(tenantId, userUuid, deviceUuid, true, true);
        long fetchCount = Math.min(remainingCount, count);

        List<IdRecord> idRecordsToDispatch = idRepo.fetchUnassigned(tenantId, userUuid, (int) fetchCount);

        if (idRecordsToDispatch.isEmpty()) {
            log.error("No IDs available in the database for tenantId: {}", tenantId);
            throw new CustomException("NO IDS AVAILABLE", "Unable to fetch IDs from the database");
        }

        updateStatusesAndLogs(idRecordsToDispatch, userUuid, deviceUuid,
                request.getClientInfo().getDeviceInfo(), tenantId, requestInfo);

        redissonIDService.updateUserDeviceDispatchedIDCount(tenantId, userUuid, deviceUuid, idRecordsToDispatch.size(), true, true);

        idRecordsToDispatch.forEach(IdDispatchService::normalizeAdditionalFields);

        return IdDispatchResponse.builder()
                .responseInfo(ResponseInfoUtil.createResponseInfoFromRequestInfo(requestInfo, true))
                .idResponses(idRecordsToDispatch)
                .fetchLimit(remainingCount - idRecordsToDispatch.size())
                .totalLimit(totalLimit)
                .build();
    }

    /**
     * Fetches all IDs previously dispatched to a given user/device from transaction logs and maps them to IdRecords.
     * Throws an exception if no IDs found.
     *
     * @param request the dispatch request containing user and device info
     * @return IdDispatchResponse containing previously dispatched IDs and response metadata
     */
    private Tuple<IdDispatchResponse, Long> fetchAllUserDeviceIds(IdDispatchRequest request, Integer limit, Integer offset, boolean restrictToday) {
        // Extract user and device info
        String userUuid = request.getRequestInfo().getUserInfo().getUuid();
        String deviceUuid = request.getClientInfo().getDeviceUuid();
        String tenantId = request.getClientInfo().getTenantId();

        // Log the fetch operation
        log.trace("Fetching dispatched IDs for userUuid={}, deviceUuid={}, tenantId={}", userUuid, deviceUuid, tenantId);

        // Query transaction logs for all IDs dispatched to the user/device today or total
        Tuple<List<IdTransactionLog>, Long> idTransactionLogs = idRepo.selectIDsForUserDevice(
                tenantId,
                deviceUuid,
                userUuid,
                null,
                limit,
                offset,
                restrictToday
        );
        log.debug("Fetched {} transaction logs for userUuid={}, deviceUuid={}", idTransactionLogs.getX().size(), userUuid, deviceUuid);

        // Extract unique ID strings from the transaction logs
        List<String> ids = idTransactionLogs.getX().stream()
                .map(IdTransactionLog::getId)
                .collect(Collectors.toList());
        log.debug("Extracted {} unique ID(s) from transaction logs: {}", ids.size(), ids);

        // Throw exception if no dispatched IDs found
        if (ids.isEmpty()) {
            throw new CustomException("NO IDS Dispatched", "NO IDS Dispatched: No IDs found for the given user and device.");
        }

        // Map the extracted IDs back to IdRecord objects from DB dispatched or assigned
        List<IdRecord> records = idRepo.findByIDsAndStatus(ids, null, tenantId);
        log.debug("Mapped {} ID(s) to IdRecord(s) from DB", records.size());

        // Normalize additional fields on the fetched records before returning
        records.stream().forEach(idRecord -> { normalizeAdditionalFields(idRecord); });

        // Build and return the response with previously dispatched IDs
        return new Tuple<>(IdDispatchResponse.builder()
                .responseInfo(ResponseInfoUtil.createResponseInfoFromRequestInfo(request.getRequestInfo(), true))
                .idResponses(records)
                .build(), idTransactionLogs.getY());
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
        // Enrich records with audit and status metadata before publishing to Kafka
        log.trace("Enriching status for update before sending Kafka update");
        enrichmentService.enrichStatusForUpdate(
                selected,
                IdRecordBulkRequest.builder()
                        .idRecords(selected)
                        .requestInfo(requestInfo)
                        .build(),
                IdStatus.DISPATCHED.name()
        );

        // Prepare and push transaction logs to Kafka for tracking
        Map<String, Object> payloadToUpdateTransactionLog = buildTransactionLogPayload(
                selected, userUuid, deviceUuid, deviceInfo, tenantId, IdStatus.DISPATCHED.name());
        log.debug("Pushing dispatch logs for {} IDs to Kafka topic: {}", selected.size(), propertiesManager.getSaveIdDispatchLogTopic());
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
