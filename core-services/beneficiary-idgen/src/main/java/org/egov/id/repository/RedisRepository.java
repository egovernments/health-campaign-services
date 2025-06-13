package org.egov.id.repository;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.idgen.IdRecord;
import org.egov.common.models.idgen.IdStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.ObjectUtils;

import java.time.Duration;
import java.util.*;

import static org.egov.id.utils.Constants.ID_STATUS;

/**
 * RedisRepository handles caching and status tracking of ID records using Redis.
 *
 * Responsibilities:
 * - Track number of dispatched IDs per user-device
 * - Manage pool of unassigned IDs in a sorted set
 * - Track ID status (e.g., DISPATCHED) in a Redis hash
 * - Perform lightweight operations for concurrent access and high performance
 */
@Repository
@Slf4j
public class RedisRepository {

    private final RedisTemplate<String, Object> redisTemplate;

    public RedisRepository(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Generates a Redis key to uniquely track dispatched ID count per user-device pair.
     */
    private String getKey(String userId, String deviceId) {
        return "user:" + userId + ":device:" + deviceId + ":count";
    }

    /**
     * Retrieves the number of IDs already dispatched to a specific user-device.
     * Returns 0 if no record is found.
     */
    public int getDispatchedCount(String userId, String deviceId) {
        String key = getKey(userId, deviceId);
        Object value = redisTemplate.opsForValue().get(key); // Fetch from Redis string key
        log.debug("Fetched dispatched count from Redis for key {}: {}", key, value);
        return value != null ? (Integer) value : 0; // Return 0 if key is not set
    }

    /**
     * Selects a specified number of unassigned IDs from Redis.
     * Filters out any IDs that are already marked as DISPATCHED in the status hash.
     */
    public List<IdRecord> selectUnassignedIds(int count) {
        Set<Object> ids = redisTemplate.opsForZSet().range(IdStatus.UNASSIGNED.name(), 0L, count - 1L); // Fetch all from sorted set
        List<IdRecord> unassigned = new ArrayList<>();
        log.debug("Scanning Redis sorted set 'unassigned_ids' for up to {} unassigned IDs", count);

        if(ObjectUtils.isEmpty(ids)) {
            log.debug("No unassigned IDs found in Redis. Returning empty list.");
            return unassigned;
        }

        for (Object obj : ids) {
            if (unassigned.size() >= count) break; // Stop if enough IDs are collected

            IdRecord record = (IdRecord) obj;

            // Check if ID is already marked as dispatched in hash
            Object status = redisTemplate.opsForHash().get(ID_STATUS, record.getId());
            if (status == null || !IdStatus.DISPATCHED.name().equalsIgnoreCase(status.toString())) {
                unassigned.add(record); // ID is safe to dispatch
            } else {
                log.debug("Skipping ID {} as it is already marked dispatched", record.getId());
            }
        }

        log.debug("Selected {} unassigned IDs from Redis", unassigned.size());
        return unassigned;
    }

    /**
     * Adds a list of ID records to the Redis unassigned sorted set.
     * Score is set to current timestamp to preserve insertion order.
     */
    public void addToRedisCache(List<IdRecord> records) {
        records.forEach(record -> {
            // Use current time as score in sorted set
            redisTemplate.opsForZSet().add(IdStatus.UNASSIGNED.name(), record, System.currentTimeMillis());
            log.debug("Added ID {} to Redis unassigned cache", record.getId());
        });
    }

    /**
     * Updates the status of given ID records to DISPATCHED in the Redis status hash.
     */
    public void updateStatusToDispatched(List<IdRecord> records) {
        records.forEach(record -> {
            // Update Redis hash with DISPATCHED status
            redisTemplate.opsForHash().put(ID_STATUS, record.getId(), IdStatus.DISPATCHED.name());
            log.debug("Updated status of ID {} to 'dispatched' in Redis", record.getId());
        });
    }

    /**
     * Removes a list of ID records from the Redis unassigned sorted set.
     * Called after IDs are dispatched to prevent re-dispatching.
     */
    public void removeFromUnassigned(List<IdRecord> records) {
        records.forEach(record -> {
            redisTemplate.opsForZSet().remove(IdStatus.UNASSIGNED.name(), record); // Remove from sorted set
            log.debug("Removed ID {} from Redis unassigned_ids cache", record.getId());
        });
    }

    /**
     * Increments the count of dispatched IDs for a user-device pair and sets a TTL of 1 day.
     * This enforces per-device daily dispatch limits.
     */
    public void incrementDispatchedCount(String userId, String deviceId , int count) {
        String key = getKey(userId, deviceId);
        redisTemplate.opsForValue().increment(key, count); // Increment string key
        redisTemplate.expire(key, Duration.ofDays(1));     // Set TTL to 24 hours
        log.debug("Incremented dispatch count in Redis for key {} by {}. TTL set to 1 day.", key, count);
    }
}
