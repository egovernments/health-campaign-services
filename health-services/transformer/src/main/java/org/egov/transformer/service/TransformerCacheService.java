package org.egov.transformer.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.egov.transformer.Constants.REDIS_CACHE_PREFIX;

@Slf4j
@Service
public class TransformerCacheService {

    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${spring.cache.redis.time-to-live:60}")
    private Long ttl;

    public TransformerCacheService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void put(String key, String tenantId, Object value) {
        if (value == null) {
            log.warn("Skipping cache put for key {} as value is null", REDIS_CACHE_PREFIX + key);
            return;
        }
        String finalKey = REDIS_CACHE_PREFIX + tenantId + key;
        log.debug("Adding key {} and value {} in redis cache", finalKey, value);
        redisTemplate.opsForValue().set(finalKey, value, ttl, TimeUnit.MINUTES);
    }

    public void put(String key, String tenantId, Object value, long timeToLive, TimeUnit timeUnit) {
        if (value == null) {
            log.warn("Skipping cache put for key {} as value is null", REDIS_CACHE_PREFIX + key);
            return;
        }
        String finalKey = REDIS_CACHE_PREFIX + tenantId + key;
        try {
            redisTemplate.opsForValue().set(finalKey, value, timeToLive, timeUnit);
        } catch (Exception e) {
            // A cache write failure must never fail a record: the caller already has the value.
            log.error("Failed to cache key {}: {}", finalKey, ExceptionUtils.getStackTrace(e));
        }
    }

    /**
     * Fetches many keys in a single round trip, returned by their unprefixed key. Keys with no cached
     * value are simply absent from the result, as are all of them if Redis is unreachable - callers are
     * expected to fall back to the source for whatever is missing.
     */
    public <T> Map<String, T> multiGet(Collection<String> keys, String tenantId, String keyPrefix, Class<T> clazz) {
        Map<String, T> cachedValues = new HashMap<>();
        if (CollectionUtils.isEmpty(keys)) {
            return cachedValues;
        }
        List<String> orderedKeys = new ArrayList<>(new LinkedHashSet<>(keys));
        List<String> finalKeys = orderedKeys.stream()
                .map(key -> REDIS_CACHE_PREFIX + tenantId + keyPrefix + key)
                .collect(Collectors.toList());
        try {
            List<Object> values = redisTemplate.opsForValue().multiGet(finalKeys);
            if (values == null) {
                return cachedValues;
            }
            for (int index = 0; index < orderedKeys.size() && index < values.size(); index++) {
                Object value = values.get(index);
                if (value == null) {
                    continue;
                }
                try {
                    cachedValues.put(orderedKeys.get(index), clazz.cast(value));
                } catch (ClassCastException e) {
                    // Stale entry written by an older class shape; treat it as a miss.
                    log.error("Failed to cast cached value for key {} to {}", finalKeys.get(index), clazz.getName());
                }
            }
            log.debug("Cache returned {} of {} requested keys", cachedValues.size(), orderedKeys.size());
        } catch (Exception e) {
            log.error("Redis multiGet failed for {} keys, falling back to source: {}",
                    finalKeys.size(), ExceptionUtils.getStackTrace(e));
        }
        return cachedValues;
    }

    public <T> T get(String key, String tenantId, Class<T> clazz) {
        String finalKey = REDIS_CACHE_PREFIX + tenantId + key;
        Object value = redisTemplate.opsForValue().get(finalKey);
        if (ObjectUtils.isEmpty(value)) {
            log.info("Cache miss for key {}", finalKey);
        }
        try {
            return clazz.cast(value);
        } catch (ClassCastException e) {
            log.error("Failed to cast cached value for key {} to class {}", finalKey, clazz.getName(), e);
            return null;
        }
    }

    public Object getRaw(String key) {
        return redisTemplate.opsForValue().get(REDIS_CACHE_PREFIX + key);
    }


}
