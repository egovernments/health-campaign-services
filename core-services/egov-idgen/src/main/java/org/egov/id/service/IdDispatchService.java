package org.egov.id.service;

import org.egov.id.config.PropertiesManager;
import org.egov.id.config.RedissonLockManager;
import org.egov.id.model.*;
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

        int alreadyDispatched = redisRepo.getDispatchedCount(userUuid, deviceUuid);
        if (alreadyDispatched + count > configuredLimit) {
            throw new CustomException("ID LIMIT EXCEPTION",
                    "ID generation limit exceeded for user: " + userUuid + " with the deviceId: " + deviceUuid);
        }

        List<IdRecord> selected = fetchOrRefillIds(count);

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
            updateStatusesAndLogs(selected, userUuid, deviceUuid, request.getUserInfo().getDeviceInfo());
            redisRepo.incrementDispatchedCount(userUuid, deviceUuid, count);
        } finally {
            lockManager.releaseLocks(lockedIds);
        }

        return buildResponse(requestInfo, userUuid, deviceUuid, request.getUserInfo().getDeviceInfo(), lockedIds);
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
    private List<IdRecord> fetchOrRefillIds(int count) {
        List<IdRecord> selected = redisRepo.selectUnassignedIds(count);
        if (selected.size() < count) {
            List<IdRecord> fromDb = idRepo.fetchUnassigned(dbFetchCount);
            redisRepo.addToRedisCache(fromDb);
            selected = redisRepo.selectUnassignedIds(count);
        }
        return selected;
    }

    /**
     * Updates Redis cache, sends Kafka messages for status and logs.
     */
    private void updateStatusesAndLogs(List<IdRecord> selected, String userUuid, String deviceUuid, Object deviceInfo) {
        redisRepo.updateStatusToDispatched(selected);
        redisRepo.removeFromUnassigned(selected);

        idGenProducer.push(propertiesManager.getUpdateIdPoolStatusTopic(),
                buildIdPoolStatusPayload(selected));

        idGenProducer.push(propertiesManager.getSaveIdDispatchLogTopic(),
                buildDispatchLogPayload(selected, userUuid, deviceUuid, deviceInfo));
    }

    /**
     * Builds the Kafka message payload to update status of dispatched IDs.
     */
    private Map<String, Object> buildIdPoolStatusPayload(List<IdRecord> selected) {
        List<DispatchedId> idsToUpdate = selected.stream().map(record -> {
            DispatchedId dispatched = new DispatchedId();
            dispatched.setId(record.getId());
            dispatched.setStatus(IdStatus.DISPATCHED.name());
            return dispatched;
        }).collect(Collectors.toList());

        Map<String, Object> payload = new HashMap<>();
        payload.put("idPool", idsToUpdate);
        return payload;
    }

    /**
     * Builds the Kafka message payload for dispatch logs.
     */
    private Map<String, Object> buildDispatchLogPayload(List<IdRecord> selected, String userUuid,
                                                        String deviceUuid, Object deviceInfo) {
        List<DispatchedId> logs = selected.stream().map(record -> {
            DispatchedId dispatched = new DispatchedId();
            dispatched.setId(record.getId());
            dispatched.setUserUuid(userUuid);
            dispatched.setDeviceUuid(deviceUuid);
            dispatched.setDeviceInfo(deviceInfo);
            return dispatched;
        }).collect(Collectors.toList());

        Map<String, Object> payload = new HashMap<>();
        payload.put("dispatchedIdLogs", logs);
        return payload;
    }

    /**
     * Builds the final response object for the dispatch request.
     */
    private IdDispatchResponse buildResponse(RequestInfo requestInfo, String userUuid, String deviceUuid,
                                             Object deviceInfo, List<String> lockedIds) {
        IdDispatchResponse response = new IdDispatchResponse();
        response.setResponseInfo(responseInfoFactory.createResponseInfoFromRequestInfo(requestInfo, true));

        List<DispatchedId> dispatchedIds = lockedIds.stream().map(id -> {
            DispatchedId detail = new DispatchedId();
            detail.setId(id);
            detail.setUserUuid(userUuid);
            detail.setDeviceUuid(deviceUuid);
            detail.setDeviceInfo(deviceInfo);
            detail.setStatus(IdStatus.DISPATCHED.name());
            return detail;
        }).collect(Collectors.toList());

        response.setIdResponses(dispatchedIds);
        return response;
    }
}
