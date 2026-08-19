package org.egov.id.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.egov.common.contract.request.RequestInfo;
import org.egov.id.config.PropertiesManager;
import org.egov.id.model.DispatchLimitConfig;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class DispatchLimitCacheService {

    private final PropertiesManager propertiesManager;
    private final MdmsService mdmsService;
    private final Cache<String, DispatchLimitConfig> cache;

    /**
     * Last successfully resolved config per tenant. Unlike {@link #cache}, this is never
     * evicted on TTL expiry, so it can serve a stale-but-usable value when MDMS is unavailable
     * on a refresh, keeping ID dispatch off MDMS's failure path.
     */
    private final Map<String, DispatchLimitConfig> lastKnownConfig = new ConcurrentHashMap<>();

    public DispatchLimitCacheService(PropertiesManager propertiesManager, MdmsService mdmsService, Ticker ticker) {
        this.propertiesManager = propertiesManager;
        this.mdmsService = mdmsService;
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(propertiesManager.getDispatchLimitCacheTtlMinutes(), TimeUnit.MINUTES)
                .ticker(ticker)
                .build();
    }

    public DispatchLimitConfig getEffectiveLimitConfig(String tenantId, RequestInfo requestInfo) {
        if (StringUtils.isBlank(tenantId)) {
            return propertiesManager.getDefaultDispatchLimitConfig();
        }
        DispatchLimitConfig config = cache.get(tenantId, key -> loadConfig(key, requestInfo));
        if (config != null) {
            return config;
        }
        // loadConfig returned null: the MDMS lookup failed and Caffeine recorded no mapping (see below),
        // so this failure is NOT pinned in the cache and the next request will retry MDMS. Resolve a
        // usable value here without caching it - last-known if this tenant ever loaded successfully,
        // otherwise the service default.
        DispatchLimitConfig stale = lastKnownConfig.get(tenantId);
        return stale != null ? stale : propertiesManager.getDefaultDispatchLimitConfig();
    }

    private DispatchLimitConfig loadConfig(String tenantId, RequestInfo requestInfo) {
        try {
            Optional<DispatchLimitConfig> mdmsConfig = mdmsService.getDispatchLimitConfig(requestInfo, tenantId);
            DispatchLimitConfig resolved;
            if (mdmsConfig.isPresent()) {
                resolved = mdmsConfig.get();
            } else {
                log.debug("Using default dispatch limit config for tenantId={}", tenantId);
                resolved = propertiesManager.getDefaultDispatchLimitConfig();
            }
            lastKnownConfig.put(tenantId, resolved);
            return resolved;
        } catch (Exception e) {
            // Return null so Caffeine records NO mapping for this key. Caching the fallback here would
            // pin the tenant to it for the full TTL even after MDMS recovers seconds later; returning
            // null lets the caller degrade gracefully while the next request retries MDMS.
            log.error("Failed to fetch dispatch limit config from MDMS for tenantId={}; degrading and will retry on next request", tenantId, e);
            return null;
        }
    }
}
