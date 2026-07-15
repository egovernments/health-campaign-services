package org.egov.id.service;

import com.github.benmanes.caffeine.cache.Ticker;
import org.egov.common.contract.request.RequestInfo;
import org.egov.id.config.PropertiesManager;
import org.egov.id.model.DispatchLimitConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DispatchLimitCacheServiceTest {

    private static final String TENANT_ID = "ch";
    private static final DispatchLimitConfig DEFAULT_CONFIG = DispatchLimitConfig.builder()
            .perDayEnabled(true)
            .totalLimit(10000)
            .perDayLimit(100)
            .perDayExpireDays(30)
            .totalExpireDays(30)
            .restrictToTodayEnabled(true)
            .build();
    private static final DispatchLimitConfig TENANT_CONFIG = DispatchLimitConfig.builder()
            .perDayEnabled(true)
            .totalLimit(500)
            .perDayLimit(50)
            .perDayExpireDays(30)
            .totalExpireDays(30)
            .restrictToTodayEnabled(true)
            .build();

    @Mock
    private PropertiesManager propertiesManager;

    @Mock
    private MdmsService mdmsService;

    private DispatchLimitCacheService dispatchLimitCacheService;
    private ManualTicker ticker;

    @BeforeEach
    void setUp() {
        when(propertiesManager.getDispatchLimitCacheTtlMinutes()).thenReturn(30);
        ticker = new ManualTicker();
        dispatchLimitCacheService = new DispatchLimitCacheService(propertiesManager, mdmsService, ticker);
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
                        .perDayExpireDays(30)
                        .totalExpireDays(30)
                        .restrictToTodayEnabled(true)
                        .build()));

        dispatchLimitCacheService.getEffectiveLimitConfig(TENANT_ID, new RequestInfo());
        advanceBeyondTtl();

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
        advanceBeyondTtl();

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
    void retriesMdmsOnNextRequestWhenColdLoadFailsInsteadOfCachingFallback() {
        when(propertiesManager.getDefaultDispatchLimitConfig()).thenReturn(DEFAULT_CONFIG);
        when(mdmsService.getDispatchLimitConfig(any(), eq(TENANT_ID)))
                .thenThrow(new RuntimeException("MDMS unavailable"))
                .thenReturn(Optional.of(TENANT_CONFIG));

        // Cold failure with no prior config: the default is served but must NOT be pinned in the cache.
        assertEquals(DEFAULT_CONFIG, dispatchLimitCacheService.getEffectiveLimitConfig(TENANT_ID, new RequestInfo()));

        // Still within the TTL window: because the failure was not cached, the next request retries
        // MDMS (now recovered) and serves the real tenant config.
        assertEquals(TENANT_CONFIG, dispatchLimitCacheService.getEffectiveLimitConfig(TENANT_ID, new RequestInfo()));

        // The recovered config is now cached, so a third request within the TTL does not hit MDMS again.
        assertEquals(TENANT_CONFIG, dispatchLimitCacheService.getEffectiveLimitConfig(TENANT_ID, new RequestInfo()));
        verify(mdmsService, times(2)).getDispatchLimitConfig(any(), eq(TENANT_ID));
    }

    @Test
    void usesRawTenantIdAsKeyWithoutNormalization() {
        String tenantId = "PB.Amritsar";
        when(mdmsService.getDispatchLimitConfig(any(), eq(tenantId))).thenReturn(Optional.of(TENANT_CONFIG));

        DispatchLimitConfig result = dispatchLimitCacheService.getEffectiveLimitConfig(tenantId, new RequestInfo());

        assertEquals(TENANT_CONFIG, result);
        // The tenantId is used verbatim as the MDMS lookup key and cache key; no case normalization is applied.
        verify(mdmsService).getDispatchLimitConfig(any(), eq(tenantId));
    }

    private void advanceBeyondTtl() {
        ticker.advance(31, TimeUnit.MINUTES);
    }

    private static final class ManualTicker implements Ticker {
        private final AtomicLong nanos = new AtomicLong();

        @Override
        public long read() {
            return nanos.get();
        }

        void advance(long time, TimeUnit unit) {
            nanos.addAndGet(unit.toNanos(time));
        }
    }
}
