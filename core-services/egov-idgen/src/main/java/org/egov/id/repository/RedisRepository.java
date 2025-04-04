package org.egov.id.repository;

import org.egov.common.models.idgen.IdRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

@Repository
public class RedisRepository {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    public int getDispatchedCount(String userId, String deviceId) {
        String key = "user:" + userId + "device:" + deviceId +":count";
        Object value = redisTemplate.opsForValue().get(key);
        return value != null ? (Integer) value : 0;
    }

    public List<IdRecord> selectUnassignedIds(int count) {
        Set<Object> ids = redisTemplate.opsForZSet().range("unassigned_ids", 0, count - 1);
        return ids.stream().map(obj -> (IdRecord) obj).collect(Collectors.toList());
    }

    public void addToRedisCache(List<IdRecord> records) {
        records.forEach(record ->
                redisTemplate.opsForZSet().add("unassigned_ids", record, System.currentTimeMillis())
        );
    }

    public void updateStatusToDispatched(List<IdRecord> records) {
        records.forEach(record ->
                redisTemplate.opsForHash().put("id_status", record.getId(), "dispatched")
        );
    }

    public void removeFromUnassigned(List<IdRecord> records) {
        records.forEach(record -> redisTemplate.opsForZSet().remove("unassigned_ids", record));
    }

    public void incrementDispatchedCount(String userId, String deviceId , int count) {
        String key = "user:" + userId + "device:" + deviceId +":count";
        redisTemplate.opsForValue().increment(key, count);
        redisTemplate.expire(key, Duration.ofHours(1));
    }
}

