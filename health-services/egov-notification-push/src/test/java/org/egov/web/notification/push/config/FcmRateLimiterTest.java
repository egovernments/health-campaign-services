package org.egov.web.notification.push.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class FcmRateLimiterTest {

    private static FcmRateLimiter limiter(double perSecond, String json) {
        FcmRateLimiter limiter = new FcmRateLimiter();
        ReflectionTestUtils.setField(limiter, "defaultPerSecond", perSecond);
        ReflectionTestUtils.setField(limiter, "byProjectJson", json);
        limiter.init();
        return limiter;
    }

    @Test
    void projectsHaveIndependentBudgets() {
        FcmRateLimiter limiter = limiter(10, "{}");

        assertTrue(limiter.tryAcquire("p1", 10, 0));
        assertFalse(limiter.tryAcquire("p1", 10, 0));
        assertTrue(limiter.tryAcquire("p2", 10, 0));
    }

    @Test
    void perProjectOverrideApplies() {
        FcmRateLimiter limiter = limiter(1, "{\"big\": 1000}");

        assertTrue(limiter.tryAcquire("big", 500, 0));
        assertFalse(limiter.tryAcquire("big", 500, 0));
    }

    @Test
    void backOffBlocksOnlyThatProject() {
        FcmRateLimiter limiter = limiter(1000, "{}");

        limiter.backOff("p1", 60_000);

        assertFalse(limiter.tryAcquire("p1", 1, 1000));
        assertTrue(limiter.tryAcquire("p2", 1, 0));
    }

    @Test
    void invalidOverrideJsonFailsStartup() {
        assertThrows(IllegalStateException.class, () -> limiter(1, "[1]"));
    }
}
