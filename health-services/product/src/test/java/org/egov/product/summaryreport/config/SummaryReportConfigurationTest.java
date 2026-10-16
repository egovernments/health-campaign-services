package org.egov.product.summaryreport.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SummaryReportConfigurationTest {

    private SummaryReportConfiguration config;

    @BeforeEach
    void setUp() {
        config = new SummaryReportConfiguration();
        ReflectionTestUtils.setField(config, "reportTimezone", "Africa/Lagos");
        ReflectionTestUtils.setField(config, "tenantTimezoneRaw", "");
    }

    @Test
    @DisplayName("unknown tenant falls back to the app default")
    void fallsBackToDefault() {
        assertEquals("Africa/Lagos", config.getTimezoneForTenant("os"));
        assertEquals("Africa/Lagos", config.getTimezoneForTenant("anything"));
        assertEquals("Africa/Lagos", config.getTimezoneForTenant(null));
    }

    @Test
    @DisplayName("configured tenant uses its override; others still fall back")
    void perTenantOverride() {
        ReflectionTestUtils.setField(config, "tenantTimezoneRaw",
                "bi:Africa/Bujumbura, cd:Africa/Kinshasa");
        assertEquals("Africa/Bujumbura", config.getTimezoneForTenant("bi"));
        assertEquals("Africa/Kinshasa", config.getTimezoneForTenant("cd"));
        // not listed -> default
        assertEquals("Africa/Lagos", config.getTimezoneForTenant("os"));
    }

    @Test
    @DisplayName("map parsing trims spaces and drops malformed / invalid entries")
    void parsingIsRobust() {
        ReflectionTestUtils.setField(config, "tenantTimezoneRaw",
                " bi : Africa/Bujumbura , broken , :Africa/Lagos , cd: , xx:bad;zone , ok:Africa/Accra ");
        Map<String, String> map = config.getTenantTimezones();
        assertEquals("Africa/Bujumbura", map.get("bi"));   // trimmed
        assertEquals("Africa/Accra", map.get("ok"));       // valid
        assertTrue(map.get("broken") == null);              // no colon
        assertTrue(map.get("cd") == null);                  // empty zone
        assertTrue(map.get("xx") == null);                  // invalid zone chars (; ) rejected
        assertEquals(2, map.size());
    }

    @Test
    @DisplayName("invalid per-tenant zone is ignored, tenant falls back to default")
    void invalidOverrideFallsBack() {
        ReflectionTestUtils.setField(config, "tenantTimezoneRaw", "bi:bad;injection");
        assertEquals("Africa/Lagos", config.getTimezoneForTenant("bi"));
    }

    @Test
    @DisplayName("invalid app default throws (would otherwise be inlined into SQL)")
    void invalidDefaultThrows() {
        ReflectionTestUtils.setField(config, "reportTimezone", "bad;zone");
        assertThrows(IllegalStateException.class, () -> config.getTimezoneForTenant("os"));
    }
}
