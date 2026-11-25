package org.egov.common.pii;

import lombok.extern.slf4j.Slf4j;
import org.egov.tracer.model.CustomException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.function.Supplier;

/**
 * Small circuit breaker for calls to enc-service.
 *
 * <p>After N failures in a row the circuit opens and calls fail at once (no waiting on a
 * dead service). After the open period one trial call is let through; if it works the
 * circuit closes again. enc-client itself has no retry, timeout or breaker, and it must not
 * be changed, so this sits in front of it.</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "egov.pii.encryption.enabled", havingValue = "true")
public class EncCircuitBreaker {

    public static final String ENC_UNAVAILABLE = "INDIVIDUAL_ENC_SERVICE_UNAVAILABLE";

    /**
     * enc-service answered but refused the value (for example a bulk envelope it cannot open: unknown key id or
     * damaged cipher text). The service is up, so this is not counted as a failure: one bad message must not
     * open the circuit for every caller.
     */
    public static final String ENC_REJECTED = "INDIVIDUAL_ENC_SERVICE_REJECTED";

    /**
     * Error codes that mean "the values could not be encrypted right now": enc-service down or failing
     * (enc-client wraps every enc-service failure as ENCRYPTION_SERVICE_ERROR), the circuit open, or the
     * MDMS policy missing or wrong. A queued bulk message that hits one of these is parked, not dropped.
     */
    private static final Set<String> ENC_FAILURE_CODES = Set.of(ENC_UNAVAILABLE, ENC_REJECTED, "ENCRYPTION_SERVICE_ERROR",
            "INDIVIDUAL_ENCRYPTION_ERROR", "INDIVIDUAL_ENCRYPTION_TRANSPORT_ERROR", "INDIVIDUAL_ENCRYPTION_NULL_RESULT");

    public static boolean isEncFailure(Throwable e) {
        return e instanceof CustomException && ENC_FAILURE_CODES.contains(((CustomException) e).getCode());
    }

    private final int failureThreshold;
    private final long openMillis;

    private int consecutiveFailures = 0;
    private long openedAt = 0L;
    private boolean trialInFlight = false;

    public EncCircuitBreaker(PiiEncryptionProperties properties) {
        this.failureThreshold = properties.getEncCircuitFailureThreshold();
        this.openMillis = properties.getEncCircuitOpenMs();
    }

    public <T> T call(String what, Supplier<T> action) {
        beforeCall(what);
        try {
            T result = action.get();
            onSuccess();
            return result;
        } catch (RuntimeException e) {
            if (e instanceof CustomException && ENC_REJECTED.equals(((CustomException) e).getCode())) {
                onSuccess();  // enc-service answered: it is up
            } else {
                onFailure(what, e);
            }
            throw e;
        }
    }

    public synchronized boolean isOpen() {
        return consecutiveFailures >= failureThreshold
                && System.currentTimeMillis() - openedAt < openMillis;
    }

    /**
     * While the circuit is open, waits until the open period is over (at most {@code maxWaitMs}), so a caller
     * that retries in a loop (the DLQ replay) makes one trial call per open period instead of one per message.
     * Returns at once when the circuit is closed.
     */
    public void awaitTrialWindow(long maxWaitMs) {
        long waitMs;
        synchronized (this) {
            if (consecutiveFailures < failureThreshold) {
                return;
            }
            waitMs = Math.min(maxWaitMs, openMillis - (System.currentTimeMillis() - openedAt));
        }
        if (waitMs <= 0) {
            return;
        }
        try {
            Thread.sleep(waitMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private synchronized void beforeCall(String what) {
        if (consecutiveFailures < failureThreshold) {
            return;
        }
        boolean openPeriodOver = System.currentTimeMillis() - openedAt >= openMillis;
        if (openPeriodOver && !trialInFlight) {
            trialInFlight = true; // half-open: let exactly one call try
            return;
        }
        throw new CustomException(ENC_UNAVAILABLE,
                "enc-service is unavailable (circuit open after " + consecutiveFailures
                        + " failures); refusing to " + what + " without encryption");
    }

    private synchronized void onSuccess() {
        if (consecutiveFailures > 0) {
            log.info("enc-service reachable again; circuit closed");
        }
        consecutiveFailures = 0;
        trialInFlight = false;
    }

    private synchronized void onFailure(String what, RuntimeException e) {
        consecutiveFailures++;
        trialInFlight = false;
        if (consecutiveFailures >= failureThreshold) {
            openedAt = System.currentTimeMillis();
            log.error("enc-service call failed {} times in a row (last: {} - {}); circuit open for {} ms",
                    consecutiveFailures, what, e.getClass().getSimpleName(), openMillis);
        }
    }
}
