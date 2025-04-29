package org.egov.id.repository;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.idgen.IdRecord;
import org.egov.common.models.idgen.IdStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.*;

import static org.egov.id.utils.Constants.ID_STATUS;

@Repository
@Slf4j
public class RedisRepository {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private String getKey(String userId, String deviceId) {
        return "user:" + userId + ":device:" + deviceId + ":count";
    }

    public int getDispatchedCount(String userId, String deviceId) {
        String key = getKey(userId, deviceId);
        Object value = redisTemplate.opsForValue().get(key);
        log.debug("Fetched dispatched count from Redis for key {}: {}", key, value);
        return value != null ? (Integer) value : 0;
    }

    public List<IdRecord> selectUnassignedIds(int count) {
        Set<Object> ids = redisTemplate.opsForZSet().range(IdStatus.UNASSIGNED.name(), 0, -1);
        List<IdRecord> unassigned = new ArrayList<>();
        log.debug("Scanning Redis sorted set 'unassigned_ids' for up to {} unassigned IDs", count);

        for (Object obj : ids) {
            if (unassigned.size() >= count) break;
            IdRecord record = (IdRecord) obj;

            Object status = redisTemplate.opsForHash().get(ID_STATUS, record.getId());
            if (status == null || !"dispatched".equalsIgnoreCase(status.toString())) {
                unassigned.add(record);
            } else {
                log.debug("Skipping ID {} as it is already marked dispatched", record.getId());
            }
        }
        log.debug("Selected {} unassigned IDs from Redis", unassigned.size());
        return unassigned;
    }

    public void addToRedisCache(List<IdRecord> records) {
        records.forEach(record ->{
                redisTemplate.opsForZSet().add(IdStatus.UNASSIGNED.name(), record, System.currentTimeMillis());
                log.debug("Updated status of ID {} to 'dispatched' in Redis", record.getId());
            }

        );
    }

    public void updateStatusToDispatched(List<IdRecord> records) {
        records.forEach(record -> {
                    redisTemplate.opsForHash().put(ID_STATUS, record.getId(), IdStatus.DISPATCHED.name());
                    log.debug("Updated status of ID {} to 'dispatched' in Redis", record.getId());
            }
        );

        for (IdRecord record : records) {
            redisTemplate.opsForHash().put(ID_STATUS, record.getId(), IdStatus.DISPATCHED.name());
            log.debug("Updated status of ID {} to 'dispatched' in Redis", record.getId());
        }
    }

    public void removeFromUnassigned(List<IdRecord> records) {
        records.forEach(record -> {
            redisTemplate.opsForZSet().remove(IdStatus.UNASSIGNED.name(), record);
            log.debug("Removed ID {} from Redis unassigned_ids cache", record.getId());
            }
        );

    }

    public void incrementDispatchedCount(String userId, String deviceId , int count) {
        String key = getKey(userId, deviceId);
        redisTemplate.opsForValue().increment(key, count);
        redisTemplate.expire(key, Duration.ofDays(1));
        log.debug("Incremented dispatch count in Redis for key {} by {}. TTL set to 1 day.", key, count);
    }
}

