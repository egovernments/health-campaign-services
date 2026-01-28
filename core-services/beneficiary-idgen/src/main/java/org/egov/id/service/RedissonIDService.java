package org.egov.id.service;

import lombok.extern.slf4j.Slf4j;
import org.egov.id.config.PropertiesManager;
import org.egov.tracer.model.CustomException;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

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
     * @param validateCount   A flag to indicate whether validation on the count should be enforced.
     * @return The remaining dispatch limit, either the total or the daily limit, depending on system configuration.
     * @throws CustomException If validation is enabled and the count is invalid or exceeds configured limits.
     */
    public long getUserDeviceDispatchedIDRemaining(String tenantId, String userId, String deviceId, boolean validateCount, boolean allowToday) throws CustomException {
        long totalCount = getUserDeviceDispatchedIDCountTotal(tenantId, userId, deviceId);
        long remainingLimit = propertiesManager.getDispatchLimitUserDeviceTotal() - totalCount;
        if(validateCount && remainingLimit <= 0) {
            throw new CustomException("USER_DEVICE_LIMIT_EXCEEDED", "ID generation limit exceeded: Total limit for user: " + userId + " and device: " + deviceId + " exceeded. Remaining ids: " + remainingLimit + ". Please try again later.");
        }
        if(allowToday && propertiesManager.isDispatchLimitUserDevicePerDayEnabled()) {
            long todayCount = getUserDeviceDispatchedIDCountToday(tenantId, userId, deviceId);
            remainingLimit = Math.min(remainingLimit, propertiesManager.getDispatchLimitUserDevicePerDay() - todayCount);
            if(validateCount && remainingLimit <= 0) {
                throw new CustomException("USER_DEVICE_LIMIT_EXCEEDED", "ID generation limit exceeded: Daily limit for user: " + userId + " and device: " + deviceId + " exceeded. Remaining ids: " + (propertiesManager.getDispatchLimitUserDevicePerDay() - todayCount) + ". Please try again later.");
            }
            return remainingLimit;
        }
        return remainingLimit;
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
        String key = getUserDispatchedCountKey(tenantId, userId, deviceId, ZonedDateTime.now(ZoneId.of(propertiesManager.getUserTimeZone())).toLocalDate());
        RAtomicLong counter = redissonClient.getAtomicLong(key);
        return counter.get();
    }

    /**
     * Updates the dispatched ID count for a specific user and device under a given tenant.
     * This method can handle both total count updates and daily updates based on the provided flag.
     *
     * @param tenantId the identifier of the tenant
     * @param userId the identifier of the user
     * @param deviceId the identifier of the device
     * @param delta the amount by which to adjust the dispatched ID count
     * @param increment a flag indicating whether to increment the count (true for increment, false for set/overwrite)
     * @param isToday a flag indicating whether the update is for today's count specifically
     */
    public void updateUserDeviceDispatchedIDCount(String tenantId, String userId, String deviceId, long delta, boolean increment, boolean isToday) {
        long totalDelta = delta;
        if (isToday) {
            totalDelta = updateUserDeviceDispatchedIDCountForToday(tenantId,userId, deviceId, delta, increment);
        }
        updateUserDeviceDispatchedIDCountForTotal(tenantId, userId,deviceId, totalDelta, increment);
    }

    /**
     * Updates the total dispatched ID count for a specific user and device within a tenant, either
     * incrementing the count by a specified delta or setting it to a specific value. The updated
     * count is stored with a defined expiration period.
     *
     * @param tenantId the unique identifier of the tenant
     * @param userId the unique identifier of the user
     * @param deviceId the unique identifier of the device
     * @param delta the value to increment or set the total count
     * @param increment if true, the count will be incremented by delta; if false, the count will be set to delta
     */
    private void updateUserDeviceDispatchedIDCountForTotal(String tenantId, String userId, String deviceId, long delta, boolean increment) {
        String totalKey = getUserDispatchedTotalCountKey(tenantId, userId, deviceId);
        RAtomicLong totalCounter = redissonClient.getAtomicLong(totalKey);
        if (increment) {
            totalCounter.addAndGet(delta);
            log.debug("Incremented total count by {} for  user: {} and device: {}", delta, userId, deviceId);
        } else {
            totalCounter.compareAndSet(totalCounter.get(), delta);
            log.debug("Updated total count to {} for user: {} and device: {}", delta, userId, deviceId);
        }
        totalCounter.expire(Duration.ofDays(propertiesManager.getDispatchUsageUserDeviceTotalExpireDays()));
    }

    /**
     * Updates the dispatched ID count for the specified user and device on the given day.
     * Depending on the `increment` flag, the method either increments the existing count or updates it to the specified value.
     *
     * @param tenantId the unique identifier of the tenant
     * @param userId the unique identifier of the user
     * @param deviceId the unique identifier of the device
     * @param delta the value to increment or set the dispatched count to
     * @param increment boolean flag indicating whether to increment (true) or overwrite (false) the count
     * @return the difference between the new value and the previous value of the dispatched count
     */
    private long updateUserDeviceDispatchedIDCountForToday(String tenantId, String userId, String deviceId, long delta, boolean increment) {
        String dailyKey = getUserDispatchedCountKey(tenantId, userId, deviceId, ZonedDateTime.now(ZoneId.of(propertiesManager.getUserTimeZone())).toLocalDate());
        RAtomicLong dailyCounter = redissonClient.getAtomicLong(dailyKey);
        long previousValue = dailyCounter.get();
        long difference = delta;
        if (increment) {
            dailyCounter.addAndGet(delta);
            log.debug("Incremented daily count by {} for  user: {} and device: {}", delta, userId, deviceId);
        } else {
            difference -= previousValue;
            dailyCounter.compareAndSet(previousValue, delta);
            log.debug("Updated daily count to {} for user: {} and device: {}", delta, userId, deviceId);
        }
        dailyCounter.expire(Duration.ofDays(propertiesManager.getDispatchUsageUserDevicePerDayExpireDays()));
        return difference;
    }

}
