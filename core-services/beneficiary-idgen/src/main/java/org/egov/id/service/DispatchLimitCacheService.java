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

    public DispatchLimitCacheService(PropertiesManager propertiesManager, MdmsService mdmsService, Ticker ticker) {
        this.propertiesManager = propertiesManager;
        this.mdmsService = mdmsService;
        this.lastKnownConfigs = new ConcurrentHashMap<>();
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(propertiesManager.getDispatchLimitCacheTtlMinutes(), TimeUnit.MINUTES)
                .ticker(ticker)
                .build();
    }

    public DispatchLimitConfig getEffectiveLimitConfig(String tenantId, RequestInfo requestInfo) {
        if (StringUtils.isBlank(tenantId)) {
            return propertiesManager.getDefaultDispatchLimitConfig();
        }
        return cache.get(tenantId, key -> loadConfig(key, requestInfo));
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
            throw e;
        }
    }
}
