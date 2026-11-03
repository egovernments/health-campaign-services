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
 *
 * <p>Also evicts the parsed rows of an uploaded file ({@link #evictSheetData}) at the start of an upload
 * run. Region name must match the @Cacheable value on ExcelUtil.convertSheetToMapListCached; guarded by
 * SheetDataCacheIsolationTest.
 */
@Component
@Slf4j
public class CampaignCacheEvictor {

    private static final String[] CAMPAIGN_CACHES = {"campaignDetail", "campaignBoundaries", "campaignProjectType"};

    static final String SHEET_DATA_CACHE = "excelSheetData";

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

    /**
     * Drops every cached sheet of one uploaded file, so the run parses the file's own cells.
     *
     * <p>Within a run the cached row maps are shared and mutated in place on purpose (immutable join,
     * enum normalization, boundary-code resolution). But validation and creation process the SAME
     * fileStoreId, so without this a creation run inside the cache TTL would start from the rows the
     * validation run already normalized to canonical values, compare them against the still-localized
     * baseline and reject an unedited file as tampered.
     *
     * <p>Keys are {@code fileStoreId + "_" + sheetName} and the sheet names are not known up front, so
     * this removes by prefix on the backing map.
     */
    @SuppressWarnings("unchecked")
    public void evictSheetData(String fileStoreId) {
        if (fileStoreId == null || fileStoreId.trim().isEmpty()) {
            return;
        }
        String prefix = fileStoreId + "_";
        // A failed evict must never fail the run (worst case: the pre-fix behaviour for one TTL).
        try {
            Cache cache = cacheManager.getCache(SHEET_DATA_CACHE);
            if (cache == null) {
                return;
            }
            Object nativeCache = cache.getNativeCache();
            if (nativeCache instanceof com.github.benmanes.caffeine.cache.Cache) {
                ((com.github.benmanes.caffeine.cache.Cache<Object, Object>) nativeCache).asMap().keySet()
                        .removeIf(k -> k instanceof String && ((String) k).startsWith(prefix));
            } else {
                cache.clear(); // not Caffeine: cannot evict by prefix, drop the whole region
            }
        } catch (RuntimeException e) {
            log.warn("Evict failed for region '{}' file '{}': {}", SHEET_DATA_CACHE, fileStoreId, e.getMessage(), e);
            return;
        }
        log.info("Evicted cached sheet data for file: {}", fileStoreId);
    }
}
