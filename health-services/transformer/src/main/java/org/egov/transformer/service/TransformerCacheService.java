package org.egov.transformer.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

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

    public <T> T get(String key, String tenantId, Class<T> clazz) {
        String finalKey = REDIS_CACHE_PREFIX + tenantId + key;
        Object value = redisTemplate.opsForValue().get(finalKey);
        if (ObjectUtils.isEmpty(value)) {
            log.info("Cache miss for key {}", finalKey);
        } else {
            log.info("Cache hit for key {}", finalKey);
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
