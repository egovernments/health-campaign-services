package org.egov.id.service;

import org.egov.common.contract.request.RequestInfo;
import org.egov.id.config.PropertiesManager;
import org.egov.id.model.DispatchLimitConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DispatchLimitCacheServiceTest {

    private static final String TENANT_ID = "pb.amritsar";
    private static final DispatchLimitConfig DEFAULT_CONFIG = DispatchLimitConfig.builder()
            .perDayEnabled(true)
            .totalLimit(10000)
            .perDayLimit(100)
            .build();
    private static final DispatchLimitConfig TENANT_CONFIG = DispatchLimitConfig.builder()
            .perDayEnabled(true)
            .totalLimit(500)
            .perDayLimit(50)
            .build();

    @Mock
    private PropertiesManager propertiesManager;

    @Mock
    private MdmsService mdmsService;

    private DispatchLimitCacheService dispatchLimitCacheService;

    @BeforeEach
    void setUp() {
        when(propertiesManager.getDispatchLimitCacheTtlMinutes()).thenReturn(30);
        dispatchLimitCacheService = new DispatchLimitCacheService(propertiesManager, mdmsService);
    }

    @Test
    void returnsDefaultConfigWhenTenantIdAbsent() {
        when(propertiesManager.getDefaultDispatchLimitConfig()).thenReturn(DEFAULT_CONFIG);
        DispatchLimitConfig result = dispatchLimitCacheService.getEffectiveLimitConfig(null, new RequestInfo());

        assertEquals(DEFAULT_CONFIG, result);
        verify(mdmsService, times(0)).getDispatchLimitConfig(any(), any());
    }

    @Test
    void returnsDefaultConfigWhenTenantIdBlank() {
        when(propertiesManager.getDefaultDispatchLimitConfig()).thenReturn(DEFAULT_CONFIG);
        DispatchLimitConfig result = dispatchLimitCacheService.getEffectiveLimitConfig("  ", new RequestInfo());

        assertEquals(DEFAULT_CONFIG, result);
        verify(mdmsService, times(0)).getDispatchLimitConfig(any(), any());
    }

    @Test
    void loadsFromMdmsOnFirstRequestForTenant() {
        when(mdmsService.getDispatchLimitConfig(any(), eq(TENANT_ID))).thenReturn(Optional.of(TENANT_CONFIG));

        DispatchLimitConfig result = dispatchLimitCacheService.getEffectiveLimitConfig(TENANT_ID, new RequestInfo());

        assertEquals(TENANT_CONFIG, result);
        verify(mdmsService, times(1)).getDispatchLimitConfig(any(), eq(TENANT_ID));
    }

    @Test
    void usesCacheOnSubsequentRequestsWithinTtl() {
        when(mdmsService.getDispatchLimitConfig(any(), eq(TENANT_ID))).thenReturn(Optional.of(TENANT_CONFIG));

        dispatchLimitCacheService.getEffectiveLimitConfig(TENANT_ID, new RequestInfo());
        DispatchLimitConfig result = dispatchLimitCacheService.getEffectiveLimitConfig(TENANT_ID, new RequestInfo());

        assertEquals(TENANT_CONFIG, result);
        verify(mdmsService, times(1)).getDispatchLimitConfig(any(), eq(TENANT_ID));
    }

    @Test
    void refreshesFromMdmsAfterCacheExpiry() throws Exception {
        when(mdmsService.getDispatchLimitConfig(any(), eq(TENANT_ID)))
                .thenReturn(Optional.of(TENANT_CONFIG))
                .thenReturn(Optional.of(DispatchLimitConfig.builder()
                        .perDayEnabled(false)
                        .totalLimit(800)
                        .perDayLimit(80)
                        .build()));

        dispatchLimitCacheService.getEffectiveLimitConfig(TENANT_ID, new RequestInfo());
        invalidateCache(TENANT_ID);

        DispatchLimitConfig result = dispatchLimitCacheService.getEffectiveLimitConfig(TENANT_ID, new RequestInfo());

        assertFalse(result.isPerDayEnabled());
        assertEquals(800, result.getTotalLimit());
        verify(mdmsService, times(2)).getDispatchLimitConfig(any(), eq(TENANT_ID));
    }

    @Test
    void fallsBackToDefaultWhenMdmsHasNoConfig() {
        when(propertiesManager.getDefaultDispatchLimitConfig()).thenReturn(DEFAULT_CONFIG);
        when(mdmsService.getDispatchLimitConfig(any(), eq(TENANT_ID))).thenReturn(Optional.empty());

        DispatchLimitConfig result = dispatchLimitCacheService.getEffectiveLimitConfig(TENANT_ID, new RequestInfo());

        assertEquals(DEFAULT_CONFIG, result);
    }

    @Test
    void usesStaleConfigWhenMdmsFailsAfterPreviousSuccessfulLoad() throws Exception {
        when(mdmsService.getDispatchLimitConfig(any(), eq(TENANT_ID)))
                .thenReturn(Optional.of(TENANT_CONFIG))
                .thenThrow(new RuntimeException("MDMS unavailable"));

        dispatchLimitCacheService.getEffectiveLimitConfig(TENANT_ID, new RequestInfo());
        invalidateCache(TENANT_ID);

        DispatchLimitConfig result = dispatchLimitCacheService.getEffectiveLimitConfig(TENANT_ID, new RequestInfo());

        assertEquals(TENANT_CONFIG, result);
        verify(mdmsService, times(2)).getDispatchLimitConfig(any(), eq(TENANT_ID));
    }

    @Test
    void usesDefaultWhenMdmsFailsAndNoStaleConfigExists() {
        when(propertiesManager.getDefaultDispatchLimitConfig()).thenReturn(DEFAULT_CONFIG);
        when(mdmsService.getDispatchLimitConfig(any(), eq(TENANT_ID)))
                .thenThrow(new RuntimeException("MDMS unavailable"));

        DispatchLimitConfig result = dispatchLimitCacheService.getEffectiveLimitConfig(TENANT_ID, new RequestInfo());

        assertEquals(DEFAULT_CONFIG, result);
    }

    @Test
    void normalizesTenantIdToLowercase() {
        when(mdmsService.getDispatchLimitConfig(any(), eq(TENANT_ID))).thenReturn(Optional.of(TENANT_CONFIG));

        DispatchLimitConfig result = dispatchLimitCacheService.getEffectiveLimitConfig("PB.Amritsar", new RequestInfo());

        assertEquals(TENANT_CONFIG, result);
        assertTrue(result.isPerDayEnabled());
        assertEquals(50, result.getPerDayLimit());
    }

    private void invalidateCache(String tenantId) throws Exception {
        Field cacheField = DispatchLimitCacheService.class.getDeclaredField("cache");
        cacheField.setAccessible(true);
        com.github.benmanes.caffeine.cache.Cache<String, DispatchLimitConfig> cache =
                (com.github.benmanes.caffeine.cache.Cache<String, DispatchLimitConfig>) cacheField.get(dispatchLimitCacheService);
        cache.invalidate(tenantId.toLowerCase());
    }
}
