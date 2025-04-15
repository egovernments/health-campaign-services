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
     * Dispatches a given count of IDs to the user after checking limits and locking.
     */
    public IdDispatchResponse dispatchIds(IdDispatchRequest request) throws Exception {
        String userUuid = request.getUserInfo().getUserUuid();
        int count = request.getUserInfo().getCount();
        String deviceUuid = request.getUserInfo().getDeviceUuid();
        RequestInfo requestInfo = request.getRequestInfo();
        String tenantId = request.getUserInfo().getTenantId();

        log.info("Dispatch request received: userUuid={}, deviceUuid={}, tenantId={}, count={}", userUuid, deviceUuid, tenantId, count);

        Long fetchLimit;

        int alreadyDispatched = redisRepo.getDispatchedCount(userUuid, deviceUuid);
        log.info("Already dispatched count from Redis for user={} and device={} is {}", userUuid, deviceUuid, alreadyDispatched);


        // Handle fetch of already allocated IDs
        if (!ObjectUtils.isEmpty(request.getUserInfo().getFetchAllocatedIds())
                && request.getUserInfo().getFetchAllocatedIds()) {
            log.info("FetchAllocatedIds flag is true, fetching previously allocated IDs.");
            IdDispatchResponse idDispatchResponse = fetchAllDispatchedIds(request);
            alreadyDispatched = (int) Math.max(alreadyDispatched, idDispatchResponse.getTotalCount());
            fetchLimit = Math.max(0L, configuredLimit - alreadyDispatched);
            log.info("Total previously allocated: {}, updated fetchLimit: {}", alreadyDispatched, fetchLimit);
            idDispatchResponse.setFetchLimit(fetchLimit);
            return idDispatchResponse;
        }

        validateDispatchRequest(request);
        log.info("Dispatch request validation passed.");

        fetchLimit = Math.max(0L, configuredLimit - (alreadyDispatched + count));
        log.info("Calculated fetch limit for new IDs: {}", fetchLimit);

        if (alreadyDispatched + count > configuredLimit) {
            log.warn("User {} with device {} exceeded dispatch limit. Allowed={}, Requested={}, Already={}",
                    userUuid, deviceUuid, configuredLimit, count, alreadyDispatched);
            throw new CustomException("ID LIMIT EXCEPTION",
                    "ID generation limit exceeded for user: " + userUuid + " with the deviceId: " + deviceUuid);
        }

        List<IdRecord> selected = fetchOrRefillIds(tenantId, count);
        log.info("Fetched {} IDs for dispatch.", selected.size());

        if (selected == null || selected.isEmpty()) {
            log.error("No IDs available from Redis or DB for tenantId: {}", tenantId);
            throw new CustomException("NO IDS AVAILABLE", "Unable to fetch IDs from Redis or DB");
        }

        List<String> lockedIds = selected.stream().map(IdRecord::getId).collect(Collectors.toList());

        if (lockedIds.isEmpty()) {
            log.error("Fetched ID list is empty after filtering.");
            throw new CustomException("INVALID ID LIST", "Selected ID list is empty or invalid");
        }

        log.info("Attempting to lock {} IDs: {}", lockedIds.size(), lockedIds);

        if (!lockManager.lockRecords(lockedIds)) {
            log.error("Failed to acquire lock for IDs: {}", lockedIds);
            throw new CustomException("LOCKING ERROR", "Unable to lock IDs.");
        }

        try {
            log.info("Successfully locked IDs. Proceeding with update and logging.");
            updateStatusesAndLogs(selected, userUuid, deviceUuid, request.getUserInfo().getDeviceInfo(), tenantId, requestInfo);
            redisRepo.incrementDispatchedCount(userUuid, deviceUuid, count);
            log.info("Dispatch count updated in Redis for user: {} and device: {}", userUuid, deviceUuid);
        } finally {
            lockManager.releaseLocks(lockedIds);
            log.info("Released locks for IDs: {}", lockedIds);
        }
        selected.stream().forEach(idRecord -> {normalizeAdditionalFields(idRecord);});
        log.info("Returning dispatch response with {} IDs", selected.size());
        return IdDispatchResponse.builder()
                .responseInfo(ResponseInfoUtil.createResponseInfoFromRequestInfo(requestInfo, true))
                .idResponses(selected)
                .fetchLimit(fetchLimit)
                .build();
    }

    private IdDispatchResponse fetchAllDispatchedIds(IdDispatchRequest request) {
        String userUuid = request.getUserInfo().getUserUuid();
        String deviceUuid = request.getUserInfo().getDeviceUuid();
        String tenantId = request.getUserInfo().getTenantId();
        log.info("Fetching dispatched IDs for userUuid={}, deviceUuid={}, tenantId={}", userUuid, deviceUuid, tenantId);
        List<IdTransactionLog> idTransactionLogs = idRepo.selectClientDispatchedIds(
                tenantId,
                deviceUuid,
                userUuid,
                null
        );
        log.info("Fetched {} transaction logs for userUuid={}, deviceUuid={}", idTransactionLogs.size(), userUuid, deviceUuid);
        List<String> ids = idTransactionLogs.stream()
                .map(IdTransactionLog::getId)
                .collect(Collectors.toList());
        log.info("Extracted {} unique ID(s) from transaction logs: {}", ids.size(), ids);
        List<IdRecord> records = idRepo.findByIDsAndStatus(ids, null, tenantId);
        log.info("Mapped {} ID(s) to IdRecord(s) from DB", records.size());

        records.stream().forEach(idRecord -> {normalizeAdditionalFields(idRecord);});



        return IdDispatchResponse.builder()
                .responseInfo(ResponseInfoUtil.createResponseInfoFromRequestInfo(request.getRequestInfo(), true))
                .idResponses(records)
                .build();
    }


    private List<IdRecord> convertToIdRecords(List<IdTransactionLog> idTransactionLogs) {
        return idTransactionLogs.stream()
                .map(log -> IdRecord.builder()
                        .id(log.getId())
                        .tenantId(log.getTenantId())
                        .source(log.getSource())
                        .rowVersion(log.getRowVersion())
                        .applicationId(log.getApplicationId())
                        .hasErrors(log.getHasErrors())
                        .additionalFields(null)
                        .auditDetails(log.getAuditDetails())
                        .status(log.getStatus()) // specific to IdRecord
                        .build())
                .collect(Collectors.toList());
    }


    public IdDispatchResponse searchIds(RequestInfo requestInfo, IdPoolSearch idPoolSearch) {
        log.info("Searching ID records with params - tenantId: {}, status: {}, idList size: {}",
                idPoolSearch.getTenantId(),
                idPoolSearch.getStatus(),
                idPoolSearch.getIdList() != null ? idPoolSearch.getIdList().size() : 0);

        List<IdRecord> records = idRepo.findByIDsAndStatus(
                idPoolSearch.getIdList(),
                idPoolSearch.getStatus(),
                idPoolSearch.getTenantId()
        );
        log.info("Found {} ID records matching the criteria.", records.size());

        return IdDispatchResponse.builder()
                .idResponses(records)
                .responseInfo(ResponseInfoUtil.createResponseInfoFromRequestInfo(requestInfo, true))
                .build();
    }

    public static void normalizeAdditionalFields(IdRecord record) {
        if (record.getAdditionalFields() != null) {
            AdditionalFields af = record.getAdditionalFields();
            if (af.getSchema() == null && af.getVersion() == null && af.getFields() == null) {
                record.setAdditionalFields(null);
            }
        }
    }

    private void validateDispatchRequest(IdDispatchRequest request) {
        DispatchUserInfo userInfo = request.getUserInfo();
        RequestInfo requestInfo = request.getRequestInfo();
        String userUuid = userInfo.getUserUuid();
        String deviceUuid = userInfo.getDeviceUuid();
        Integer count = userInfo.getCount();
        log.info("Validating dispatch request for userUuid: {}, deviceUuid: {}, requested count: {}", userUuid, deviceUuid, count);
        log.info("Configured dispatch limit per user: {}", configuredLimit);
        if (count > configuredLimit) {
            log.warn("Dispatch request count {} exceeds configured limit {}", count, configuredLimit);
            throw new CustomException("COUNT EXCEEDS LIMIT",
                    "Requested count exceeds maximum allowed limit per user: " + configuredLimit);
        }
        int alreadyDispatched = redisRepo.getDispatchedCount(userUuid, deviceUuid);
        log.info("Already dispatched count for userUuid: {}, deviceUuid: {} is {}", userUuid, deviceUuid, alreadyDispatched);

        if (alreadyDispatched + count > configuredLimit) {
            log.warn("Total dispatched + requested count ({}) exceeds configured limit for userUuid: {}, deviceUuid: {}", alreadyDispatched + count, userUuid, deviceUuid);
            throw new CustomException("ID LIMIT EXCEPTION",
                    "ID generation limit exceeded for user: " + userUuid + " with the deviceId: " + deviceUuid);
        }
        log.info("Dispatch request validation passed for userUuid: {}, deviceUuid: {}", userUuid, deviceUuid);
    }



    /**
     * Fetches IDs from Redis or DB if needed.
     */
    private List<IdRecord> fetchOrRefillIds(String tenantId, int count) {
        log.info("Attempting to fetch {} unassigned IDs for tenant: {}", count, tenantId);

        List<IdRecord> selected = redisRepo.selectUnassignedIds(count);
        log.info("Fetched {} unassigned IDs from Redis cache", selected.size());
        if (selected.size() < count) {
            int remaining = count - selected.size();
            log.info("Redis cache insufficient by {} IDs. Fetching {} IDs from DB for tenant: {}", remaining, dbFetchCount, tenantId);
            List<IdRecord> fromDb = idRepo.fetchUnassigned(tenantId, dbFetchCount);
            log.info("Fetched {} unassigned IDs from DB for tenant: {}", fromDb.size(), tenantId);
            redisRepo.addToRedisCache(fromDb);
            log.info("Added {} IDs from DB to Redis cache", fromDb.size());
            selected = redisRepo.selectUnassignedIds(count);
            log.info("Re-fetched {} unassigned IDs from Redis cache after refill", selected.size());
        } else {
            log.info("Sufficient unassigned IDs fetched from Redis, DB refill not needed");
        }

        return selected;
    }


    /**
     * Updates Redis cache, sends Kafka messages for status and logs.
     */
    private void updateStatusesAndLogs(List<IdRecord> selected, String userUuid, String deviceUuid, Object deviceInfo, String tenantId, RequestInfo requestInfo) {
        log.info("Updating status to DISPATCHED for {} IDs in Redis", selected.size());
        redisRepo.updateStatusToDispatched(selected);

        log.info("Removing {} IDs from Redis unassigned cache", selected.size());
        redisRepo.removeFromUnassigned(selected);

        log.info("Enriching status for update before sending Kafka update");
        enrichmentService.enrichStatusForUpdate(
                selected,
                IdRecordBulkRequest.builder()
                        .idRecords(selected)
                        .requestInfo(requestInfo)
                        .build(),
                IdStatus.DISPATCHED.name()
        );

        Map<String, Object> payloadToUpdate = new HashMap<>();
        payloadToUpdate.put("idPool", selected);
        log.info("Pushing {} IDs to Kafka topic: {}", selected.size(), propertiesManager.getUpdateIdPoolStatusTopic());
        idGenProducer.push(propertiesManager.getUpdateIdPoolStatusTopic(), payloadToUpdate);

        Map<String, Object> payloadToUpdateTransactionLog = buildTransactionLogPayload(selected, userUuid, deviceUuid, deviceInfo, tenantId, IdStatus.DISPATCHED.name());
        log.info("Pushing dispatch logs for {} IDs to Kafka topic: {}", selected.size(), propertiesManager.getSaveIdDispatchLogTopic());
        idGenProducer.push(propertiesManager.getSaveIdDispatchLogTopic(), payloadToUpdateTransactionLog);
    }


    /**
     * Builds the Kafka message payload to update status of dispatched IDs.
     */
    private Map<String, Object> buildIdPoolStatusPayload(String userUuid, List<IdRecord> selected) {

        List<IdRecord> updatedRecords = selected.stream().peek(d -> {
            d.setStatus(IdStatus.DISPATCHED.name());
            AuditDetails auditDetails = d.getAuditDetails();
            auditDetails.setLastModifiedBy(userUuid);
            auditDetails.setLastModifiedTime(System.currentTimeMillis());
            d.setRowVersion(d.getRowVersion() + 1);
        }).toList();

        Map<String, Object> payload = new HashMap<>();
        payload.put("idPool", updatedRecords);
        return payload;
    }

    /**
     * Builds the Kafka message payload for dispatch logs.
     */
    private Map<String, Object> buildTransactionLogPayload(List<IdRecord> selected, String userUuid,
                                                        String deviceUuid, Object deviceInfo, String tenantId, String status) {
        List<IdTransactionLog> logs = selected.stream().map(record -> {
            String updateStatus = "";
            if (StringUtils.isNotBlank(status)) updateStatus = status;
            else {
                if(StringUtils.isNotBlank(record.getStatus())) {
                    updateStatus = record.getStatus();
                }
            }
            IdTransactionLog dispatched = IdTransactionLog.builder()
                    .tenantId(tenantId)
                    .id(record.getId())
                    .auditDetails(
                            AuditDetails.builder()
                                    .createdBy(userUuid)
                                    .createdTime(System.currentTimeMillis())
                                    .build()
                    )
                    .rowVersion(1)
                    .status(updateStatus)
                    .userUuid(userUuid)
                    .deviceUuid(deviceUuid)
                    .deviceInfo(deviceInfo)
                    .build();
            return dispatched;
        }).collect(Collectors.toList());

        Map<String, Object> payload = new HashMap<>();
        payload.put("idTransactionLog", logs);
        return payload;
    }


    private Tuple<List<IdRecord>, Map<IdRecord, ErrorDetails>> validate(List<Validator<IdRecordBulkRequest, IdRecord>> validators,
                                                                            Predicate<Validator<IdRecordBulkRequest, IdRecord>> isApplicableForCreate,
                                                                            IdRecordBulkRequest request, boolean isBulk) {
        log.info("validating request");
        Map<IdRecord, ErrorDetails> errorDetailsMap = CommonUtils.validate(validators,
                isApplicableForCreate, request,
                SET_ID_RECORDS);
        if (!errorDetailsMap.isEmpty() && !isBulk) {
            Set<String> hashset = new HashSet<>();
            for (Map.Entry<IdRecord, ErrorDetails> entry : errorDetailsMap.entrySet()) {
                List<Error> errors = entry.getValue().getErrors();
                hashset.addAll(errors.stream().map(error -> error.getErrorCode()).collect(Collectors.toSet()));
            }
            throw new CustomException(String.join(":",  hashset), errorDetailsMap.values().toString());
        }
        List<IdRecord> validIdRecords = request.getIdRecords().stream()
                .filter(notHavingErrors()).collect(Collectors.toList());
        return new Tuple<>(validIdRecords, errorDetailsMap);
    }



    public List<IdRecord> update(IdRecordBulkRequest request, boolean isBulk) {
        log.info("Starting update process for IdRecordBulkRequest. Bulk mode: {}", isBulk);
        Tuple<List<IdRecord>, Map<IdRecord, ErrorDetails>> tuple = validate(
                validators, isApplicableForUpdate, request, isBulk
        );
        Map<IdRecord, ErrorDetails> errorDetailsMap = tuple.getY();
        List<IdRecord> validIdRecords = tuple.getX();
        log.info("Validation complete. Valid records count: {}, Errors found: {}",
                validIdRecords.size(), errorDetailsMap.size());

        try {
            if (!validIdRecords.isEmpty()) {
                log.info("Processing {} valid ID records for update.", validIdRecords.size());

                log.info("Calling enrichment service for valid ID records.");
                enrichmentService.update(validIdRecords, request);

                Map<String, Object> payload = new HashMap<>();
                payload.put("idPool", validIdRecords);
                log.info("Pushing enriched records to Kafka topic: {}", propertiesManager.getUpdateIdPoolStatusTopic());
                idGenProducer.push(propertiesManager.getUpdateIdPoolStatusTopic(), payload);

                String userUuid;
                if (!ObjectUtils.isEmpty(request.getRequestInfo().getUserInfo()) &&
                        !ObjectUtils.isEmpty(request.getRequestInfo().getUserInfo().getUuid())) {
                    userUuid = request.getRequestInfo().getUserInfo().getUuid();
                } else {
                    userUuid = request.getRequestInfo().getUserInfo().toString();
                }

                Map<String, Object> payloadToUpdateTransactionLog = buildTransactionLogPayload(
                        validIdRecords, userUuid, SYSTEM_UPDATED, "", validIdRecords.get(0).getTenantId(),
                        null
                );
                log.info("Pushing transaction logs to Kafka topic: {}", propertiesManager.getSaveIdDispatchLogTopic());
                idGenProducer.push(propertiesManager.getSaveIdDispatchLogTopic(), payloadToUpdateTransactionLog);
            }
        } catch (Exception exception) {
            log.error("Exception occurred while processing update for ID records: {}", ExceptionUtils.getStackTrace(exception));
            populateErrorDetails(request, errorDetailsMap, validIdRecords, exception, SET_ID_RECORDS);
        }

        handleErrors(errorDetailsMap, isBulk, VALIDATION_ERROR);
        log.info("Update process completed. Returning {} records.", validIdRecords.size());
        return validIdRecords;
    }


}
