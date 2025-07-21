package org.egov.id.service;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.idgen.IdRecord;
import org.egov.id.config.PropertiesManager;
import org.egov.id.repository.IdRepository;
import org.egov.tracer.model.CustomException;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RLock;
import org.redisson.api.RMapCache;
import org.redisson.api.RQueue;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
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

    private final RedissonClient redissonClient;
    private final IdRepository idRepository;
    private final PropertiesManager propertiesManager;

    public RedissonIDService(RedissonClient redissonClient, IdRepository idRepository, PropertiesManager propertiesManager) {
        this.redissonClient = redissonClient;
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
        return redissonClient.getMapCache("tenant:" + tenantId + ":DISPATCH_PROCESSING");
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
        RQueue<IdRecord> queue = redissonClient.getQueue(queueKey(tenantId));
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
        RQueue<IdRecord> queue = redissonClient.getQueue(queueKey(tenantId));
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
            RQueue<IdRecord> queue = redissonClient.getQueue(queueKey(tenantId));
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
     * Constructs a key string to represent the dispatched count for a specific user and device
     * within a given tenant context.
     *
     * @param tenantId the unique identifier of the tenant
     * @param userId the unique identifier of the user
     * @param deviceId the unique identifier of the device
     * @return a string representing the combined key for the dispatched count
     */
    private String getUserDispatchedCountKey(String tenantId, String userId, String deviceId) {
        return "tenant:" + tenantId + ":user:" + userId + ":device:" + deviceId + ":count";
    }

    /**
     * Generates a unique key string to represent the dispatched count for a user based on
     * the provided tenant ID, user ID, device ID, and date.
     *
     * @param tenantId the identifier for the tenant
     * @param userId the identifier for the user
     * @param deviceId the identifier for the device
     * @param date the date associated with the dispatched count
     * @return a unique key string combining the tenant ID, user ID, device ID, and date
     */
    private String getUserDispatchedCountKey(String tenantId, String userId, String deviceId, LocalDate date) {
        return "tenant:" + tenantId + ":user:" + userId + ":device:" + deviceId + ":count:" + date;
    }

    /**
     * Generates a unique key for tracking the total dispatched count for a specific user and device within a tenant.
     *
     * @param tenantId the identifier for the tenant
     * @param userId the identifier for the user
     * @param deviceId the identifier for the device
     * @return a formatted string key representing the total dispatched count for the specified user and device
     */
    private String getUserDispatchedTotalCountKey(String tenantId, String userId, String deviceId) {
        return "tenant:" + tenantId + ":user:" + userId + ":device:" + deviceId + ":total:count";
    }

    /**
     * Calculates the remaining dispatch limit for a specific user and device.
     * This method evaluates both total and daily dispatch limits based on the given parameters and configurations.
     *
     * @param tenantId        The identifier of the tenant.
     * @param userId          The identifier of the user.
     * @param deviceId        The identifier of the device.
     * @param count           The number of IDs intended for dispatch.
     * @param validateCount   A flag to indicate whether validation on the count should be enforced.
     * @return The remaining dispatch limit, either the total or the daily limit, depending on system configuration.
     * @throws CustomException If validation is enabled and the count is invalid or exceeds configured limits.
     */
    public long getUserDeviceDispatchedIDRemaining(String tenantId, String userId, String deviceId, long count, boolean validateCount) {
        long totalCount = getUserDeviceDispatchedIDCountTotal(tenantId, userId, deviceId);
        if(validateCount && count <= 0) {
            throw new CustomException("INVALID_DISPATCH_COUNT", "Dispatch count must be greater than 0.");
        }
        if(validateCount && (totalCount + count > propertiesManager.getDispatchLimitUserDeviceTotal())) {
            throw new CustomException("USER_DEVICE_LIMIT_EXCEEDED", "Total limit for user: " + userId + " and device: " + deviceId + " exceeded. Current value: " + (totalCount + count) + ". Please try again later.");
        }
        if(propertiesManager.isDispatchLimitUserDevicePerDayEnabled()) {
            long todayCount = getUserDeviceDispatchedIDCountToday(tenantId, userId, deviceId);
            if(validateCount && (todayCount + count > propertiesManager.getDispatchLimitUserDevicePerDay())) {
                throw new CustomException("USER_DEVICE_LIMIT_EXCEEDED", "Daily limit for user: " + userId + " and device: " + deviceId + " exceeded. Current value: " + (todayCount + count) + ". Please try again later.");
            }
            return propertiesManager.getDispatchLimitUserDevicePerDay() - todayCount;
        }
        return propertiesManager.getDispatchLimitUserDeviceTotal() - totalCount;
    }

    /**
     * Retrieves the total count of dispatched IDs associated with a specific user and device
     * for a given tenant from the underlying data store.
     *
     * @param tenantId the identifier of the tenant
     * @param userId the identifier of the user
     * @param deviceId the identifier of the device
     * @return the total count of dispatched IDs for the specified tenant, user, and device
     */
    public long getUserDeviceDispatchedIDCountTotal(String tenantId, String userId, String deviceId) {
        String key = getUserDispatchedTotalCountKey(tenantId, userId, deviceId);
        RAtomicLong counter = redissonClient.getAtomicLong(key);
        return counter.get();
    }

    /**
     * Retrieves the count of dispatched IDs for a specific user and device
     * under a particular tenant for the current day.
     *
     * @param tenantId the unique identifier of the tenant
     * @param userId the unique identifier of the user
     * @param deviceId the unique identifier of the device
     * @return the count of dispatched IDs for the user and device on the current day
     */
    public long getUserDeviceDispatchedIDCountToday(String tenantId, String userId, String deviceId) {
        String key = getUserDispatchedCountKey(tenantId, userId, deviceId, LocalDate.now());
        RAtomicLong counter = redissonClient.getAtomicLong(key);
        return counter.get();
    }

    /**
     * Updates the count of dispatched IDs for a user's device for the current day and total overall.
     * This method adjusts the dispatch counts based on the specified increment or reset operation.
     * It also checks for daily and total dispatch limits and throws an exception if the limit is exceeded.
     *
     * @param tenantId   The identifier for the tenant.
     * @param userId     The identifier for the user.
     * @param deviceId   The identifier for the device.
     * @param delta      The value to increment or set the dispatched ID count by.
     * @param increment  If true, increments the count by the delta value; if false, resets the count to the delta value.
     *
     * @throws CustomException if the daily or total dispatch limit for the user's device is exceeded.
     */
    public void updateUserDeviceDispatchedIDCountForToday(String tenantId, String userId, String deviceId, long delta, boolean increment) {
        String dailyKey = getUserDispatchedCountKey(tenantId, userId, deviceId);
        String totalKey = getUserDispatchedTotalCountKey(tenantId, userId, deviceId);
        RAtomicLong dailyCounter = redissonClient.getAtomicLong(dailyKey);
        RAtomicLong totalCounter = redissonClient.getAtomicLong(totalKey);

        long newDailyValue = increment ? dailyCounter.get() + delta : delta;
        long newTotalValue = totalCounter.get() + (increment ? delta : dailyCounter.get() - delta);

        if(propertiesManager.isDispatchLimitUserDevicePerDayEnabled() && newDailyValue > propertiesManager.getDispatchLimitUserDevicePerDay()) {
            throw new CustomException("USER_DEVICE_LIMIT_EXCEEDED", "Daily limit for user: " + userId + " and device: " + deviceId + " exceeded. Current value: " + newDailyValue + ". Please try again later.");
        }

        if (newTotalValue > propertiesManager.getDispatchLimitUserDeviceTotal()) {
            throw new CustomException("USER_DEVICE_LIMIT_EXCEEDED", "Total limit for user: " + userId + " and device: " + deviceId + " exceeded. Current value: " + newTotalValue + ". Please try again later.");
        }

        if (increment) {
            dailyCounter.addAndGet(delta);
            totalCounter.addAndGet(delta);
        } else {
            dailyCounter.set(delta);
            totalCounter.set(newTotalValue);
        }

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
        RLock lock = redissonClient.getLock(lockKey);
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
