package org.egov.common.pii;

import org.egov.tracer.model.CustomException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EncCircuitBreakerTest {

    private static EncCircuitBreaker breaker(int threshold, long openMs) {
        PiiEncryptionProperties properties = new PiiEncryptionProperties();
        properties.setEncCircuitFailureThreshold(threshold);
        properties.setEncCircuitOpenMs(openMs);
        return new EncCircuitBreaker(properties);
    }

    private static String fail() {
        throw new CustomException("ENCRYPTION_SERVICE_ERROR", "down");
    }

    @Test
    @DisplayName("isEncFailure: only the enc failure codes")
    void encFailureCodes() {
        assertTrue(EncCircuitBreaker.isEncFailure(new CustomException("ENCRYPTION_SERVICE_ERROR", "x")));
        assertTrue(EncCircuitBreaker.isEncFailure(new CustomException(EncCircuitBreaker.ENC_UNAVAILABLE, "x")));
        assertTrue(EncCircuitBreaker.isEncFailure(new CustomException("INDIVIDUAL_ENCRYPTION_NULL_RESULT", "x")));
        assertFalse(EncCircuitBreaker.isEncFailure(new CustomException("DUPLICATE_AADHAAR", "x")));
        assertFalse(EncCircuitBreaker.isEncFailure(new RuntimeException("ENCRYPTION_SERVICE_ERROR")));
        assertFalse(EncCircuitBreaker.isEncFailure(null));
    }

    @Test
    @DisplayName("after N failures in a row calls fail at once without calling enc-service")
    void opensAfterThreshold() {
        EncCircuitBreaker b = breaker(2, 60_000);
        AtomicInteger calls = new AtomicInteger();
        assertThrows(CustomException.class, () -> b.call("encrypt", () -> { calls.incrementAndGet(); return fail(); }));
        assertFalse(b.isOpen());
        assertThrows(CustomException.class, () -> b.call("encrypt", () -> { calls.incrementAndGet(); return fail(); }));
        assertTrue(b.isOpen());
        CustomException e = assertThrows(CustomException.class,
                () -> b.call("encrypt", () -> { calls.incrementAndGet(); return "ok"; }));
        assertEquals(EncCircuitBreaker.ENC_UNAVAILABLE, e.getCode());
        assertEquals(2, calls.get());
    }

    @Test
    @DisplayName("enc-service refusing a value (it answered) is not a failure: it resets the count and closes a trial")
    void rejectedIsNotAFailure() throws Exception {
        EncCircuitBreaker b = breaker(2, 50);
        for (int i = 0; i < 5; i++) {
            assertThrows(CustomException.class, () -> b.call("open", () -> {
                throw new CustomException(EncCircuitBreaker.ENC_REJECTED, "HTTP 500");
            }));
        }
        assertFalse(b.isOpen());
        assertThrows(CustomException.class, () -> b.call("encrypt", EncCircuitBreakerTest::fail));
        assertThrows(CustomException.class, () -> b.call("encrypt", EncCircuitBreakerTest::fail));
        assertTrue(b.isOpen());
        Thread.sleep(80);
        assertThrows(CustomException.class, () -> b.call("open", () -> {   // the trial is answered
            throw new CustomException(EncCircuitBreaker.ENC_REJECTED, "HTTP 500");
        }));
        assertFalse(b.isOpen());
        assertTrue(EncCircuitBreaker.isEncFailure(new CustomException(EncCircuitBreaker.ENC_REJECTED, "x")));
    }

    @Test
    @DisplayName("a success resets the failure count")
    void successResets() {
        EncCircuitBreaker b = breaker(2, 60_000);
        assertThrows(CustomException.class, () -> b.call("encrypt", EncCircuitBreakerTest::fail));
        assertEquals("ok", b.call("encrypt", () -> "ok"));
        assertThrows(CustomException.class, () -> b.call("encrypt", EncCircuitBreakerTest::fail));
        assertFalse(b.isOpen());
    }

    @Test
    @DisplayName("after the open period one trial call goes through: success closes, failure opens again")
    void trialCall() throws Exception {
        EncCircuitBreaker b = breaker(1, 50);
        assertThrows(CustomException.class, () -> b.call("encrypt", EncCircuitBreakerTest::fail));
        Thread.sleep(80);
        assertThrows(CustomException.class, () -> b.call("encrypt", EncCircuitBreakerTest::fail)); // trial failed
        assertTrue(b.isOpen());
        Thread.sleep(80);
        assertEquals("ok", b.call("encrypt", () -> "ok"));                                    // trial worked
        assertFalse(b.isOpen());
        assertEquals("ok", b.call("encrypt", () -> "ok"));
    }

    @Test
    @DisplayName("awaitTrialWindow: returns at once when closed; waits for the open period (capped) when open")
    void awaitTrialWindow() {
        EncCircuitBreaker closed = breaker(1, 60_000);
        long t0 = System.nanoTime();
        closed.awaitTrialWindow(5_000);
        assertTrue((System.nanoTime() - t0) / 1_000_000 < 50);

        EncCircuitBreaker open = breaker(1, 60_000);
        assertThrows(CustomException.class, () -> open.call("encrypt", EncCircuitBreakerTest::fail));
        long t1 = System.nanoTime();
        open.awaitTrialWindow(200);
        long waited = (System.nanoTime() - t1) / 1_000_000;
        assertTrue(waited >= 190 && waited < 2_000, "waited " + waited + " ms");
    }
}
