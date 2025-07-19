package org.egov.id.service;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.idgen.IdRecord;
import org.egov.id.config.PropertiesManager;
import org.egov.id.repository.IdRepository;
import org.egov.tracer.model.CustomException;
import org.redisson.api.RBucket;
import org.redisson.api.RLock;
import org.redisson.api.RMapCache;
import org.redisson.api.RQueue;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * A service class that manages ID generation and dispatch operations using Redis as the distributed storage system.
 * This class handles operations like fetching unassigned IDs, managing ID dispatch limits per user/device,
 * and maintaining distributed locks to ensure thread-safe operations across multiple instances.
 *
 * @author holashchand
 */
@Slf4j
@Service
public class RedissonIDService {

    private final RedissonClient redisson;
    private final IdRepository idRepository;
    private final PropertiesManager propertiesManager;

    public RedissonIDService(RedissonClient redisson, IdRepository idRepository, PropertiesManager propertiesManager) {
        this.redisson = redisson;
        this.idRepository = idRepository;
        this.propertiesManager = propertiesManager;
    }

    /**
     * Generates a queue key string for a specific tenant.
     *
     * @param tenantId the unique identifier of the tenant
     * @return the generated queue key string in the format "tenant:{tenantId}:DISPATCH_QUEUE:"
     */
    private String queueKey(String tenantId) {
        return "tenant:" + tenantId + ":DISPATCH_QUEUE:";
    }

    /**
     * Retrieves a distributed map cache associated with a specific tenant for dispatch processing.
     *
     * @param tenantId the unique identifier of the tenant for which the cache will be accessed
     * @return a distributed map cache instance associated with the specified tenant's dispatch processing
     */
    private RMapCache<String, String> processingCache(String tenantId) {
        return redisson.getMapCache("tenant:" + tenantId + ":DISPATCH_PROCESSING");
    }

    /**
     * Fetches a list of unassigned IDs for a specific tenant with a lock mechanism to prevent race conditions.
     * The method ensures that only one process can fetch unassigned IDs at a time per tenant by utilizing a distributed lock.
     * Retries are performed if the lock is not acquired initially.
     *
     * @param tenantId the unique identifier of the tenant for which unassigned IDs need to be fetched
     * @param count the number of unassigned IDs to fetch
     * @return a list of unassigned IDs wrapped in {@code IdRecord} objects for the specified tenant
     * @throws Exception if the lock cannot be acquired after retries or any other unexpected errors occur
     */
    public List<IdRecord> fetchUnassignedIdsWithLock(String tenantId, int count) throws Exception {
        String lockKey = "tenant:" + tenantId + ":select-unassigned-ids-lock:";
        return executeWithLock(
                lockKey,
                propertiesManager.getRedissonLockWaitTime(),
                propertiesManager.getRedissonLockLeaseTime(),
                TimeUnit.SECONDS,
                () -> fetchUnassignedIdsWithRefill(tenantId, count)
        );
    }


    /**
     * Fetches a list of unassigned ID records and ensures that the required number
     * of IDs are retrieved, even if a shortage occurs. If there is a shortage,
     * it refills the unassigned IDs by fetching missing IDs from the database
     * and adding them to the unassigned ID pool.
     *
     * @param tenantId the identifier for the tenant, used to scope ID record operations
     * @param count the number of unassigned ID records required
     * @return a list of {@code IdRecord} instances representing unassigned IDs,
     *         ensuring the specified count is fulfilled
     */
    private List<IdRecord> fetchUnassignedIdsWithRefill(String tenantId, int count) {
        List<IdRecord> idRecords = fetchUnassingedIds(tenantId, count);
        List<IdRecord> allIdRecords = new ArrayList<>(idRecords);

        // Check if fetched id records from redis are less then the required
        int shortage = count - idRecords.size();
        if(shortage > 0) {
            // List all the id records which are in process
            Set<String> idRecordsInProcess = fetchIdRecordsInProcess(tenantId);

            // Fetch the id records from db and refill id records in redis excluding ids which are in process
            fetchFromDatabase(tenantId, idRecordsInProcess);

            // List remaining id records and append in all ids to dispatch
            List<IdRecord> remainingIdRecords = fetchUnassingedIds(tenantId, shortage);
            if (remainingIdRecords.isEmpty()) {
                throw new CustomException("NO_IDS_REFILLED", "No IDs are available in redis for tenant: " + tenantId + " to process. Please try again later.");
            }
            allIdRecords.addAll(remainingIdRecords);
        }
        return allIdRecords;
    }

    /**
     * Fetches unassigned IDs from the queue for the given tenant and marks them as processing.
     * In case of an exception, the method requeues the IDs before propagating the error.
     *
     * @param tenantId the identifier of the tenant for which unassigned IDs are to be fetched
     * @param count the number of unassigned IDs to fetch from the queue
     * @return a list of unassigned ID records fetched from the queue
     */
    private List<IdRecord> fetchUnassingedIds(String tenantId, int count) {
        List<IdRecord> allIdRecords = new ArrayList<>();
        RQueue<IdRecord> queue = redisson.getQueue(queueKey(tenantId));
        try {
            List<IdRecord> idRecords = queue.poll(count);
            allIdRecords.addAll(idRecords);
            markProcessing(tenantId, idRecords);
            log.debug("Fetched {} records from queue for tenant {}", idRecords.size(), tenantId);
        } catch (Exception e) {
            requeue(tenantId, allIdRecords);
            throw e;
        }
        return allIdRecords;
    }

    /**
     * Fetches a set of processed IDs for the given tenant.
     *
     * @param tenantId the identifier of the tenant whose processed IDs are to be fetched
     * @return a set of processed IDs associated with the given tenant
     */
    private Set<String> fetchIdRecordsInProcess(String tenantId) {
        return processingCache(tenantId).readAllKeySet();
    }

    /**
     * Marks the given list of IDs as processing for the specified tenant.
     *
     * @param tenantId the identifier for the tenant
     * @param idRecords the list of {@code IdRecord} objects containing the IDs to mark as processing
     */
    public void markProcessing(String tenantId, List<IdRecord> idRecords) {
        if(CollectionUtils.isEmpty(idRecords)) return;
        processingCache(tenantId).putAll(
                idRecords.stream().map(IdRecord::getId).collect(Collectors.toMap(id -> id, id -> "processing")),
                propertiesManager.getProcessedIDCacheTime(),
                TimeUnit.SECONDS
        );
        log.debug("Marked {} IDs as processing for tenant {}", idRecords.size(), tenantId);
    }

    /**
     * Removes the specified ID records from the processing cache for the given tenant.
     *
     * @param tenantId The identifier of the tenant whose processing cache is to be updated.
     * @param ids A list of ID records to be removed from the processing cache.
     */
    private void removeProcessing(String tenantId, String[] ids) {
        processingCache(tenantId).fastRemove(ids);
        log.debug("Removed {} IDs from processing cache for tenant {}", ids.length, tenantId);
    }

    /**
     * Re-queues a list of ID records to the designated queue of the specified tenant.
     * If the provided list of ID records is not empty, the method will add all the records to the
     * appropriate queue, remove them from processing, and log the operation.
     *
     * @param tenantId the identifier for the tenant whose queue will be updated
     * @param idRecords a list of {@code IdRecord} objects to be re-queued, cannot be null or empty
     */
    private void requeue(String tenantId, List<IdRecord> idRecords) {
        RQueue<IdRecord> queue = redisson.getQueue(queueKey(tenantId));
        if(!CollectionUtils.isEmpty(idRecords)) {
            queue.addAll(idRecords);
            String[] ids = idRecords.stream().map(IdRecord::getId).toArray(String[]::new);
            removeProcessing(tenantId, ids);
            log.debug("Re-queued {} IDs for tenant {}", idRecords.size(), tenantId);
        }
    }

    /**
     * Fetches unassigned IDs from the database for a specific tenant and adds them to a queue for processing.
     * If the IDs cannot be added to the queue or no IDs are available, appropriate exceptions are thrown.
     *
     * @param tenantId the unique identifier for the tenant whose IDs need to be fetched
     * @param idRecordsInProcess a set of IDs that are currently being processed and should be excluded from the fetch
     */
    private void fetchFromDatabase(String tenantId, Set<String> idRecordsInProcess) {
        List<IdRecord> dbResults = idRepository.fetchUnassigned(tenantId, propertiesManager.getDbFetchLimit(), idRecordsInProcess);
        if (!dbResults.isEmpty()) {
            log.debug("Fetched {} IDs from DB for tenant {}", dbResults.size(), tenantId);
            RQueue<IdRecord> queue = redisson.getQueue(queueKey(tenantId));
            boolean result = queue.addAll(dbResults);
            if(!result) {
                log.error("Failed to add IDs to queue for tenant: {}", tenantId);
                throw new CustomException("QUEUE_ADD_FAILED", "Failed to add IDs to queue for tenant: " + tenantId + ". Please try again later.");
            }
            log.debug("Excluded {} IDs from queue for tenant {}", idRecordsInProcess.size(), tenantId);
            log.debug("Cached {} ID Records from DB for tenant {}", dbResults.size(), tenantId);
        } else {
            throw new CustomException("NO_IDS_AVAILABLE", "No IDs are available for tenant: " + tenantId + " to process. Please try again later.");
        }
    }

    /**
     * Generates a Redis key to uniquely track dispatched ID count per tenant-user-device pair.
     */
    private String getUserDispatchedCountKey(String tenantId, String userId, String deviceId) {
        return "tenant:" + tenantId + ":user:" + userId + ":device:" + deviceId + ":count";
    }

    /**
     * Retrieves a map containing the count of dispatched IDs grouped by date for a specific user,
     * device, and tenant from a Redis data store.
     *
     * @param tenantId the identifier for the tenant
     * @param userId the identifier for the user
     * @param deviceId the identifier for the device
     * @return a map where each key represents a date and the associated value is the count of
     *         dispatched IDs on that date. If no data is found, an empty map is returned.
     */
    public Map<LocalDate, Integer> getUserDispatchedIDCount(String tenantId, String userId, String deviceId) {
        String key = getUserDispatchedCountKey(tenantId, userId, deviceId);

        RBucket<Map<LocalDate, Integer>> bucket = redisson.getBucket(key);
        Map<LocalDate, Integer> dayDispatchedCountMap = bucket.get();
        if(dayDispatchedCountMap == null) {
            dayDispatchedCountMap = new HashMap<>();
        }
        log.trace("Fetched day dispatched count map from Redis for key {}: {}", key, dayDispatchedCountMap);
        return dayDispatchedCountMap;
    }

    /**
     * Retrieves the count of dispatched IDs for a specific user and device for the current day.
     *
     * @param tenantId The identifier for the tenant context.
     * @param userId The identifier for the user whose dispatched ID count is being retrieved.
     * @param deviceId The identifier for the device associated with the dispatched IDs.
     * @return The number of dispatched IDs for the specified user and device for today. Returns 0 if no IDs have been dispatched.
     */
    public int getUserDispatchedIDCountForToday(String tenantId, String userId, String deviceId) {
        Map<LocalDate, Integer> dayDispatchedCountMap = getUserDispatchedIDCount(tenantId, userId, deviceId);
        Integer todayDispatchedCount = dayDispatchedCountMap.getOrDefault(LocalDate.now(), 0);
        log.debug("Dispatched count for user {} for device {} for today: {}", userId, deviceId, todayDispatchedCount);
        return todayDispatchedCount;
    }

    /**
     * Calculates and returns the remaining count of dispatch IDs a user is allowed to perform
     * based on the provided tenant ID, user ID, and device ID. The calculation takes into account
     * daily limits and overall limits if the dispatch limit per user is enabled in the system.
     *
     * @param tenantId the unique identifier of the tenant to which the user belongs
     * @param userId the unique identifier of the user for whom the dispatch count is calculated
     * @param deviceId the unique identifier of the device used by the user for dispatches
     * @return the number of dispatch IDs the user is allowed to perform based on system limits
     */
    public int getRemainingUserAllowedDispatchIDCount(String tenantId, String userId, String deviceId) {
        Map<LocalDate, Integer> dayDispatchedCountMap = getUserDispatchedIDCount(tenantId, userId, deviceId);
        Integer todayDispatchedCount = dayDispatchedCountMap.getOrDefault(LocalDate.now(), 0);
        Integer AllDispatchedCount = dayDispatchedCountMap.values().stream().reduce(0, Integer::sum);
        if(propertiesManager.isDispatchLimitPerUserPerDayEnabled()) {
            int remainingCount = propertiesManager.getDispatchLimitPerUserPerDay() - todayDispatchedCount;
            log.debug("Allowed dispatch count for user {} for device {} for today: {}", userId, deviceId, remainingCount);
            return remainingCount;
        }
        int remainingCount = propertiesManager.getDispatchLimitPerUser() - AllDispatchedCount;
        log.debug("Allowed dispatch count for user {} for device {}: {}", userId, deviceId, remainingCount);
        return remainingCount;
    }

    /**
     * Updates the count of dispatched items for a specific tenant, user, and device for the current day.
     *
     * @param tenantId the identifier of the tenant
     * @param userId the identifier of the user
     * @param deviceId the identifier of the device
     * @param count the number of items to increment/update
     */
    public void updateDispatchedCountForToday(String tenantId, String userId, String deviceId, int count, boolean increment) {
        String key = getUserDispatchedCountKey(tenantId, userId, deviceId);
        RBucket<Map<LocalDate, Integer>> bucket = redisson.getBucket(key);
        Map<LocalDate, Integer> dayDispatchedCountMap = bucket.get();
        if(dayDispatchedCountMap == null) {
            dayDispatchedCountMap = new HashMap<>();
        }
        int updatedCount = count;
        // if required to be incremented, then add the current count as well
        if(increment) {
            Integer current = dayDispatchedCountMap.getOrDefault(LocalDate.now(), 0);
            updatedCount += current;
        }
        dayDispatchedCountMap.put(LocalDate.now(), updatedCount);
        bucket.setAndKeepTTL(dayDispatchedCountMap);
        log.debug("Updated day dispatched count for key {}, day {}: {}", key, LocalDate.now(), updatedCount);
    }

    /**
     * Executes the provided task with a distributed lock mechanism. The method will attempt
     * to acquire the lock within the configured retry attempts and execute the task if successful.
     *
     * @param lockKey the unique key representing the lock
     * @param waitTime the maximum time to wait for acquiring the lock in the given time unit
     * @param leaseTime the time for which the lock will be held after being acquired
     * @param timeUnit the time unit for the waitTime and leaseTime parameters
     * @param task the executable logic to be executed once the lock is acquired
     * @param <T> the result type of the task execution
     * @return the result of the executed task
     * @throws Exception if the execution of the task or lock operations cause an exception
     */
    public <T> T executeWithLock(String lockKey, long waitTime, long leaseTime, TimeUnit timeUnit, Callable<T> task) throws Exception {
        RLock lock = redisson.getLock(lockKey);
        int retries = propertiesManager.getRedissonLockAcquireRetryCount();
        boolean acquired = false;
        for (int attempt = 1; attempt <= retries; attempt++) {
            try {
                acquired = lock.tryLock(waitTime, leaseTime, timeUnit);
                if (acquired) {
                    log.debug("Acquired lock for key: {}", lockKey);
                    return task.call();
                }
                Thread.sleep(200);
                log.trace("Failed to acquire lock for key: {}. Retry attempt {}/{}", lockKey, attempt, retries);
            } finally {
                // Release lock only if acquired and held by this thread
                if (acquired && lock.isHeldByCurrentThread()) {
                    try {
                        lock.unlock();
                        log.debug("Released lock for key: {}", lockKey);
                    } catch (IllegalMonitorStateException e) {
                        log.warn("Attempted to unlock but lock not held by thread: {}", lockKey, e);
                    }
                }
            }
        }

        log.debug("Failed to acquire lock for key: {} after {} retries", lockKey, retries);
        // In case all retry attempts fail without returning
        throw new CustomException("REDISSON_LOCK_RETRIES_EXHAUSTED",
                "All attempts to acquire redis lock for key " + lockKey + " failed. Please try again later.");
    }

}
