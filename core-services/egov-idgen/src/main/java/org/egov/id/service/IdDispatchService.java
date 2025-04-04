package org.egov.id.service;

import org.egov.common.contract.models.AuditDetails;
import org.egov.common.models.idgen.*;
import org.egov.id.config.PropertiesManager;
import org.egov.id.config.RedissonLockManager;
import org.egov.id.producer.IdGenProducer;
import org.egov.id.repository.IdRepository;
import org.egov.id.repository.RedisRepository;
import org.egov.tracer.model.CustomException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class IdDispatchService {

    private final ResponseInfoFactory responseInfoFactory;
    private final RedisRepository redisRepo;
    private final IdRepository idRepo;
    private final RedissonLockManager lockManager;
    private final IdGenProducer idGenProducer;
    private final PropertiesManager propertiesManager;

    private final int configuredLimit;
    private final int dbFetchCount;

    @Autowired
    public IdDispatchService(ResponseInfoFactory responseInfoFactory,
                             RedisRepository redisRepo,
                             IdRepository idRepo,
                             RedissonLockManager lockManager,
                             IdGenProducer idGenProducer,
                             PropertiesManager propertiesManager) {
        this.responseInfoFactory = responseInfoFactory;
        this.redisRepo = redisRepo;
        this.idRepo = idRepo;
        this.lockManager = lockManager;
        this.idGenProducer = idGenProducer;
        this.propertiesManager = propertiesManager;
        this.configuredLimit = propertiesManager.getDispatchLimitPerUser();
        this.dbFetchCount = propertiesManager.getDbFetchLimit();
    }

    /**
     * Dispatches a given count of IDs to the user after checking limits and locking.
     */
    public IdDispatchResponse dispatchIds(IdDispatchRequest request) throws Exception {

        validateDispatchRequest(request);

        String userUuid = request.getUserInfo().getUserUuid();
        int count = request.getUserInfo().getCount();
        String deviceUuid = request.getUserInfo().getDeviceUuid();
        RequestInfo requestInfo = request.getRequestInfo();
        String tenantId = request.getUserInfo().getTenantId();

        int alreadyDispatched = redisRepo.getDispatchedCount(userUuid, deviceUuid);
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
            updateStatusesAndLogs(selected, userUuid, deviceUuid, request.getUserInfo().getDeviceInfo(), tenantId);
            redisRepo.incrementDispatchedCount(userUuid, deviceUuid, count);
        } finally {
            lockManager.releaseLocks(lockedIds);
        }

        return IdDispatchResponse.builder()
                .responseInfo(responseInfoFactory.createResponseInfoFromRequestInfo(requestInfo, true))
                .idResponses(selected)
                .build();
    }

    public IdDispatchResponse searchIds(RequestInfo requestInfo, IdPoolSearch idPoolSearch) {
        List<IdRecord> records = idRepo.findByIDsAndStatus(idPoolSearch.getIdList(),
                idPoolSearch.getStatus(),
                idPoolSearch.getTenantId());
        return IdDispatchResponse.builder()
                .idResponses(records)
                .responseInfo(responseInfoFactory.createResponseInfoFromRequestInfo(requestInfo, true))
                .build();
    }

    private void validateDispatchRequest(IdDispatchRequest request) {
        if (request == null) {
            throw new CustomException("INVALID REQUEST", "Request cannot be null");
        }

        DispatchUserInfo userInfo = request.getUserInfo();
        RequestInfo requestInfo = request.getRequestInfo();

        if (userInfo == null) {
            throw new CustomException("INVALID USER", "UserInfo is missing in the request");
        }

        if (requestInfo == null) {
            throw new CustomException("INVALID REQUEST INFO", "RequestInfo is missing");
        }

        String userUuid = userInfo.getUserUuid();
        String deviceUuid = userInfo.getDeviceUuid();
        Integer count = userInfo.getCount();

        if (userUuid == null || userUuid.trim().isEmpty()) {
            throw new CustomException("INVALID USER UUID", "User UUID must not be null or empty");
        }

        if (deviceUuid == null || deviceUuid.trim().isEmpty()) {
            throw new CustomException("INVALID DEVICE UUID", "Device UUID must not be null or empty");
        }


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
    private void updateStatusesAndLogs(List<IdRecord> selected, String userUuid, String deviceUuid, Object deviceInfo, String tenantId) {
        redisRepo.updateStatusToDispatched(selected);
        redisRepo.removeFromUnassigned(selected);

        idGenProducer.push(propertiesManager.getUpdateIdPoolStatusTopic(),
                buildIdPoolStatusPayload(userUuid, selected));

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

}
