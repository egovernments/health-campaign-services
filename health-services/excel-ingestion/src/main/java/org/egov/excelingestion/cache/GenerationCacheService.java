package org.egov.excelingestion.cache;

import lombok.extern.slf4j.Slf4j;
import org.egov.excelingestion.constants.GenerationConstants;
import org.egov.excelingestion.web.models.GenerateResource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Redis-backed cache for generation records keyed by (tenantId, referenceId).
 *
 * Invariant: every cached list is sorted by lastModifiedTime DESC. Callers that
 * mutate a record's state should call {@link #invalidate(String, String)} so the
 * next search rehydrates from DB.
 */
@Component
@Slf4j
public class GenerationCacheService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final long ttlSeconds;

    public GenerationCacheService(@Qualifier("generationRedisTemplate") RedisTemplate<String, Object> redisTemplate,
                                  @Value("${excel.ingestion.cache.generation.ttl.seconds:21600}") long ttlSeconds) {
        this.redisTemplate = redisTemplate;
        this.ttlSeconds = ttlSeconds;
    }

    private String key(String tenantId, String referenceId) {
        return GenerationConstants.CACHE_KEY_GENERATION_BY_REF + tenantId + ":" + referenceId;
    }

    @SuppressWarnings("unchecked")
    public List<GenerateResource> getByReference(String tenantId, String referenceId) {
        if (tenantId == null || referenceId == null) {
            return null;
        }
        try {
            Object cached = redisTemplate.opsForValue().get(key(tenantId, referenceId));
            if (cached == null) {
                return null;
            }
            if (cached instanceof List) {
                List<GenerateResource> list = (List<GenerateResource>) cached;
                // Defensive sort - cached writes already sort, but cheap to enforce.
                list.sort(byLastModifiedDesc());
                return list;
            }
            log.warn("Unexpected cache value type for key {}: {}", key(tenantId, referenceId), cached.getClass());
            return null;
        } catch (Exception e) {
            log.warn("Redis read failed for tenantId={} referenceId={} - {}", tenantId, referenceId, e.getMessage());
            return null;
        }
    }

    public void putByReference(String tenantId, String referenceId, List<GenerateResource> records) {
        if (tenantId == null || referenceId == null || records == null) {
            return;
        }
        try {
            List<GenerateResource> sorted = new ArrayList<>(records);
            sorted.sort(byLastModifiedDesc());
            redisTemplate.opsForValue().set(key(tenantId, referenceId), sorted, ttlSeconds, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("Redis write failed for tenantId={} referenceId={} - {}", tenantId, referenceId, e.getMessage());
        }
    }

    public void invalidate(String tenantId, String referenceId) {
        if (tenantId == null || referenceId == null) {
            return;
        }
        try {
            redisTemplate.delete(key(tenantId, referenceId));
        } catch (Exception e) {
            log.warn("Redis invalidate failed for tenantId={} referenceId={} - {}", tenantId, referenceId, e.getMessage());
        }
    }

    private static Comparator<GenerateResource> byLastModifiedDesc() {
        return Comparator.comparing(
                (GenerateResource r) -> {
                    Long ts = r.getLastModifiedTime();
                    if (ts != null) return ts;
                    if (r.getAuditDetails() != null && r.getAuditDetails().getLastModifiedTime() != null) {
                        return r.getAuditDetails().getLastModifiedTime();
                    }
                    return 0L;
                },
                Comparator.reverseOrder());
    }

    public static List<GenerateResource> emptyList() {
        return Collections.emptyList();
    }
}
