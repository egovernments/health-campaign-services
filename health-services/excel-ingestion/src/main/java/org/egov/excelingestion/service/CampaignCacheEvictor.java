package org.egov.excelingestion.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

/**
 * Evicts the cached copies of one campaign at the start of a generation/upload run, so the run
 * reads fresh campaign data instead of a previous run's snapshot (a run is only triggered when the
 * campaign changed). Region names must match the @Cacheable values in {@link CampaignService};
 * guarded by CampaignCacheBehaviourTest.
 */
@Component
@Slf4j
public class CampaignCacheEvictor {

    private static final String[] CAMPAIGN_CACHES = {"campaignDetail", "campaignBoundaries", "campaignProjectType"};

    private final CacheManager cacheManager;

    public CampaignCacheEvictor(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    public void evictCampaign(String campaignId, String tenantId) {
        // Campaign-less uploads are legal; nothing to evict for them.
        if (campaignId == null || tenantId == null) {
            return;
        }
        String key = campaignId + "_" + tenantId;
        for (String cacheName : CAMPAIGN_CACHES) {
            // A failed evict must never fail the run (worst case: stale for one TTL, as before the fix).
            try {
                Cache cache = cacheManager.getCache(cacheName);
                if (cache != null) {
                    cache.evict(key);
                }
            } catch (RuntimeException e) {
                log.warn("Evict failed for region '{}' key '{}': {}", cacheName, key, e.getMessage(), e);
            }
        }
        log.info("Evicted campaign caches for campaign: {} in tenant: {}", campaignId, tenantId);
    }
}
