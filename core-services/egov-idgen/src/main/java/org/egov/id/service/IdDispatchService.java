package org.egov.id.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.egov.common.contract.models.AuditDetails;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.ds.Tuple;
import org.egov.common.models.ErrorDetails;
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
import static org.egov.id.utils.Constants.SET_ID_RECORDS;
import static org.egov.id.utils.Constants.VALIDATION_ERROR;

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


        Long fetchLimit;

        int alreadyDispatched = redisRepo.getDispatchedCount(userUuid, deviceUuid);
        fetchLimit = Long.valueOf(Math.max( 0 , configuredLimit - (alreadyDispatched + count)));

        validateDispatchRequest(request);

        // return dispatched Ids if fetAllDispatched flag is true
        if(!ObjectUtils.isEmpty(request.getUserInfo().getFetchAllDispatched())
                && request.getUserInfo().getFetchAllDispatched()) {
            IdDispatchResponse idDispatchResponse = fetchAllDispatchedIds(request);
            idDispatchResponse.setFetchLimit(fetchLimit);
             return idDispatchResponse;
        }

        if (alreadyDispatched + count > configuredLimit) {
            throw new CustomException("ID LIMIT EXCEPTION",
                    "ID generation limit exceeded for user: " + userUuid + " with the deviceId: " + deviceUuid);
        }

        List<IdRecord> selected = fetchOrRefillIds(tenantId, count);

        if (selected == null || selected.isEmpty()) {
            throw new CustomException("NO IDS AVAILABLE", "Unable to fetch IDs from Redis or DB");
        }


        List<String> lockedIds = selected.stream().map(IdRecord::getId).collect(Collectors.toList());

        if (lockedIds.isEmpty()) {
            throw new CustomException("INVALID ID LIST", "Selected ID list is empty or invalid");
        }

        if (!lockManager.lockRecords(lockedIds)) {
            throw new CustomException("LOCKING ERROR", "Unable to lock IDs.");
        }

        try {
            updateStatusesAndLogs(selected, userUuid, deviceUuid, request.getUserInfo().getDeviceInfo(), tenantId , requestInfo);
            redisRepo.incrementDispatchedCount(userUuid, deviceUuid, count);
        } finally {
            lockManager.releaseLocks(lockedIds);
        }
        return IdDispatchResponse.builder()
                .responseInfo(ResponseInfoUtil.createResponseInfoFromRequestInfo(requestInfo, true))
                .idResponses(selected)
                .fetchLimit(fetchLimit)
                .build();
    }

    private IdDispatchResponse fetchAllDispatchedIds(IdDispatchRequest request) {

        List<IdTransactionLog> idTransactionLogs = idRepo.selectClientDispatchedIds(
                request.getUserInfo().getTenantId(),
                request.getUserInfo().getDeviceUuid(),
                request.getUserInfo().getUserUuid(),
                null
        );

        List<IdRecord> records = convertToIdRecords(idTransactionLogs);

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
        List<IdRecord> records = idRepo.findByIDsAndStatus(idPoolSearch.getIdList(),
                idPoolSearch.getStatus(),
                idPoolSearch.getTenantId());
        return IdDispatchResponse.builder()
                .idResponses(records)
                .responseInfo(ResponseInfoUtil.createResponseInfoFromRequestInfo(requestInfo, true))
                .build();
    }

    private void validateDispatchRequest(IdDispatchRequest request) {

        DispatchUserInfo userInfo = request.getUserInfo();
        RequestInfo requestInfo = request.getRequestInfo();

        String userUuid = userInfo.getUserUuid();
        String deviceUuid = userInfo.getDeviceUuid();
        Integer count = userInfo.getCount();


        if (count > configuredLimit) {
            throw new CustomException("COUNT EXCEEDS LIMIT",
                    "Requested count exceeds maximum allowed limit per user: " + configuredLimit);
        }

        int alreadyDispatched = redisRepo.getDispatchedCount(userUuid, deviceUuid);
        if (alreadyDispatched + count > configuredLimit) {
            throw new CustomException("ID LIMIT EXCEPTION",
                    "ID generation limit exceeded for user: " + userUuid + " with the deviceId: " + deviceUuid);
        }
    }


    /**
     * Fetches IDs from Redis or DB if needed.
     */
    private List<IdRecord> fetchOrRefillIds(String tenantId, int count) {
        List<IdRecord> selected = redisRepo.selectUnassignedIds(count);
        if (selected.size() < count) {
            List<IdRecord> fromDb = idRepo.fetchUnassigned(tenantId, dbFetchCount);
            redisRepo.addToRedisCache(fromDb);
            selected = redisRepo.selectUnassignedIds(count);
        }
        return selected;
    }

    /**
     * Updates Redis cache, sends Kafka messages for status and logs.
     */
    private void updateStatusesAndLogs(List<IdRecord> selected, String userUuid, String deviceUuid, Object deviceInfo, String tenantId, RequestInfo requestInfo) {
        redisRepo.updateStatusToDispatched(selected);
        redisRepo.removeFromUnassigned(selected);

        enrichmentService.enrichStatusForUpdate(selected,
                IdRecordBulkRequest.builder()
                        .idRecords(selected)
                        .requestInfo(requestInfo)
                        .build() , IdStatus.DISPATCHED.name());
        Map<String, Object> payloadToUpdate = new HashMap<>();
        payloadToUpdate.put("idPool", selected);
        idGenProducer.push(propertiesManager.getUpdateIdPoolStatusTopic(),
                payloadToUpdate);

        idGenProducer.push(propertiesManager.getSaveIdDispatchLogTopic(),
                buildDispatchLogPayload(selected, userUuid, deviceUuid, deviceInfo , tenantId));
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
    private Map<String, Object> buildDispatchLogPayload(List<IdRecord> selected, String userUuid,
                                                        String deviceUuid, Object deviceInfo, String tenantId) {
        List<IdTransactionLog> logs = selected.stream().map(record -> {
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
                    .status(IdStatus.DISPATCHED.name())
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
        Tuple<List<IdRecord>, Map<IdRecord, ErrorDetails>> tuple =  validate(validators,
                isApplicableForUpdate, request,
                isBulk);
        Map<IdRecord, ErrorDetails> errorDetailsMap = tuple.getY();
        List<IdRecord> validIdRecords = tuple.getX();

        try {
            if (!validIdRecords.isEmpty()) {
                log.info("processing {} valid entities", validIdRecords.size());
                enrichmentService.update(validIdRecords, request);
                // save
                Map<String, Object> payload = new HashMap<>();
                payload.put("idPool", validIdRecords);
                idGenProducer.push(
                        propertiesManager.getUpdateIdPoolStatusTopic(),
                        payload
                        );

            }
        } catch (Exception exception) {
            log.error("error occurred", ExceptionUtils.getStackTrace(exception));
            populateErrorDetails(request, errorDetailsMap, validIdRecords, exception, SET_ID_RECORDS);
        }
        handleErrors(errorDetailsMap, isBulk,VALIDATION_ERROR );
        return validIdRecords;
    }

}
