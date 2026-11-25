package org.egov.common.pii;

import lombok.Data;
import lombok.ToString;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Settings of the shared PII encryption classes in this package.
 *
 * <p>Opt-in: this bean, and every other bean in this package, exists only when the service sets
 * {@code egov.pii.encryption.enabled=true}. Services that scan all of {@code org.egov} or {@code org.egov.common}
 * therefore start exactly as before when they take this library version without the flag.</p>
 *
 * <p>A service that wants its own property names maps them here in its application.properties, for example
 * {@code egov.bulk.dlq.topic=${individual.bulk.dlq.topic:individual-bulk-dlq-topic}}.</p>
 */
@Data
@Component
@ConditionalOnProperty(name = "egov.pii.encryption.enabled", havingValue = "true")
public class PiiEncryptionProperties {

    @Value("${state.level.tenant.id}")
    private String stateLevelTenantId;

    // ---- Calls to enc-service ----

    @Value("${egov.enc.connect.timeout.ms:2000}")
    private Integer encConnectTimeoutMs;

    @Value("${egov.enc.read.timeout.ms:10000}")
    private Integer encReadTimeoutMs;

    /** Consecutive enc-service failures that open the circuit. */
    @Value("${egov.enc.circuit.failure.threshold:5}")
    private Integer encCircuitFailureThreshold;

    /** How long the circuit stays open before one trial call is let through. */
    @Value("${egov.enc.circuit.open.ms:30000}")
    private Long encCircuitOpenMs;

    // ---- Encrypted envelope for the bulk Kafka topics ----

    /** ENCRYPTED_ONLY (default): enc-service down -> HTTP 503. ALLOW_PLAIN: plain message to a restricted DLQ, HTTP 202. */
    @Value("${egov.bulk.envelope.dlq.mode:ENCRYPTED_ONLY}")
    private String bulkEnvelopeDlqMode;

    /**
     * Restricted dead-letter topic for bulk messages parked while enc-service was down. No default on purpose: a shared
     * default name would mix the messages of two services in one topic. Only the bulk envelope service needs it; it
     * refuses to start without it.
     */
    @Value("${egov.bulk.dlq.topic:}")
    private String bulkDlqTopic;

    /** Wait after a failed DLQ replay before the message is parked again, so a failure cannot spin. */
    @Value("${egov.bulk.dlq.retry.backoff.ms:5000}")
    private Long bulkDlqRetryBackoffMs;

    /** Longest wait between tries of a DLQ envelope that enc-service refuses to open (the wait doubles each try). */
    @Value("${egov.bulk.dlq.retry.max.backoff.ms:3600000}")
    private Long bulkDlqRetryMaxBackoffMs;

    // ---- Search hashes (only used when egov.pii.hash.enabled=true) ----

    /** Secret for the HMAC-SHA256 search hashes. One per environment, never rotated, never logged. */
    @ToString.Exclude
    @Value("${egov.pii.hash.secret:}")
    private String hashSecret;

    /** Fields whose values are lower-cased before hashing (case-insensitive match), for example givenName. */
    @Value("#{'${egov.pii.hash.lowercase.fields:}'.split(',')}")
    private List<String> hashLowercaseFields;
}
