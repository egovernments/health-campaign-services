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

import java.time.Duration;
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
    private final PropertiesManager propertiesManager;

    public RedissonIDService(RedissonClient redissonClient, PropertiesManager propertiesManager) {
        this.redissonClient = redissonClient;
        this.propertiesManager = propertiesManager;
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
            throw new CustomException("USER_DEVICE_LIMIT_EXCEEDED", "Total limit for user: " + userId + " and device: " + deviceId + " exceeded. Remaining ids: " + (propertiesManager.getDispatchLimitUserDeviceTotal() - totalCount) + ". Please try again later.");
        }
        if(propertiesManager.isDispatchLimitUserDevicePerDayEnabled()) {
            long todayCount = getUserDeviceDispatchedIDCountToday(tenantId, userId, deviceId);
            if(validateCount && (todayCount + count > propertiesManager.getDispatchLimitUserDevicePerDay())) {
                throw new CustomException("USER_DEVICE_LIMIT_EXCEEDED", "Daily limit for user: " + userId + " and device: " + deviceId + " exceeded. Remaining ids: " + (propertiesManager.getDispatchLimitUserDevicePerDay() - todayCount) + ". Please try again later.");
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
        String dailyKey = getUserDispatchedCountKey(tenantId, userId, deviceId, LocalDate.now());
        String totalKey = getUserDispatchedTotalCountKey(tenantId, userId, deviceId);
        RAtomicLong dailyCounter = redissonClient.getAtomicLong(dailyKey);
        RAtomicLong totalCounter = redissonClient.getAtomicLong(totalKey);

        long newDailyValue = increment ? dailyCounter.get() + delta : delta;
        long newTotalValue = totalCounter.get() + (increment ? delta : dailyCounter.get() - delta);

        if(propertiesManager.isDispatchLimitUserDevicePerDayEnabled() && newDailyValue > propertiesManager.getDispatchLimitUserDevicePerDay()) {
            log.error("USER_DEVICE_DAILY_LIMIT_EXCEEDED: Daily limit for user: {} and device: {}", userId, deviceId);
            throw new CustomException("USER_DEVICE_DAILY_LIMIT_EXCEEDED", "Daily limit for user: " + userId + " and device: " + deviceId + " exceeded. Current value: " + newDailyValue + ". Please try again later.");
        }

        if (newTotalValue > propertiesManager.getDispatchLimitUserDeviceTotal()) {
            log.error("USER_DEVICE_LIMIT_EXCEEDED: Total limit for user: {} and device: {}", userId, deviceId);
            throw new CustomException("USER_DEVICE_LIMIT_EXCEEDED", "Total limit for user: " + userId + " and device: " + deviceId + " exceeded. Current value: " + newTotalValue + ". Please try again later.");
        }

        if (increment) {
            dailyCounter.addAndGet(delta);
            totalCounter.addAndGet(delta);
            log.debug("Incremented daily and total count by {} for  user: {} and device: {}", delta, userId, deviceId);
        } else {
            dailyCounter.set(delta);
            totalCounter.set(newTotalValue);
            log.debug("Updated daily to {} and total count to {} for user: {} and device: {}", delta, newTotalValue, userId, deviceId);
        }
        dailyCounter.expire(Duration.ofDays(propertiesManager.getDispatchUsageUserDevicePerDayExpireDays()));
        totalCounter.expire(Duration.ofDays(propertiesManager.getDispatchUsageUserDeviceTotalExpireDays()));

    }

}
