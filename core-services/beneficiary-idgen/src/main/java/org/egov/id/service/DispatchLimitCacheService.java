package org.egov.id.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.egov.common.contract.request.RequestInfo;
import org.egov.id.config.PropertiesManager;
import org.egov.id.model.DispatchLimitConfig;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class DispatchLimitCacheService {

    private final PropertiesManager propertiesManager;
    private final MdmsService mdmsService;
    private final ConcurrentHashMap<String, DispatchLimitConfig> lastKnownConfigs;
    private final Cache<String, DispatchLimitConfig> cache;

    public DispatchLimitCacheService(PropertiesManager propertiesManager, MdmsService mdmsService) {
        this.propertiesManager = propertiesManager;
        this.mdmsService = mdmsService;
        this.lastKnownConfigs = new ConcurrentHashMap<>();
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(propertiesManager.getDispatchLimitCacheTtlMinutes(), TimeUnit.MINUTES)
                .build();
    }

    public DispatchLimitConfig getEffectiveLimitConfig(String tenantId, RequestInfo requestInfo) {
        if (StringUtils.isBlank(tenantId)) {
            return propertiesManager.getDefaultDispatchLimitConfig();
        }
        String normalizedTenantId = normalizeTenantId(tenantId);
        return cache.get(normalizedTenantId, key -> loadConfig(key, requestInfo));
    }

    private DispatchLimitConfig loadConfig(String tenantId, RequestInfo requestInfo) {
        try {
            Optional<DispatchLimitConfig> mdmsConfig = mdmsService.getDispatchLimitConfig(requestInfo, tenantId);
            if (mdmsConfig.isPresent()) {
                lastKnownConfigs.put(tenantId, mdmsConfig.get());
                return mdmsConfig.get();
            }
            log.debug("Using default dispatch limit config for tenantId={}", tenantId);
            return propertiesManager.getDefaultDispatchLimitConfig();
        } catch (Exception e) {
            log.error("Failed to fetch dispatch limit config from MDMS for tenantId={}", tenantId, e);
            DispatchLimitConfig staleConfig = lastKnownConfigs.get(tenantId);
            if (staleConfig != null) {
                log.warn("Using stale dispatch limit config for tenantId={}", tenantId);
                return staleConfig;
            }
            return propertiesManager.getDefaultDispatchLimitConfig();
        }
    }

    private String normalizeTenantId(String tenantId) {
        return tenantId.toLowerCase();
    }
}
