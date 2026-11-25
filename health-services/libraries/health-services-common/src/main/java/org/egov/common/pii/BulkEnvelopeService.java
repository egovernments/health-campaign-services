package org.egov.common.pii;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.producer.Producer;
import org.egov.tracer.model.CustomException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Encrypted envelope for the bulk Kafka topics.
 *
 * <p>The bulk APIs answer 202 and hand the whole request to Kafka. The request is serialised, encrypted as one
 * value (enc-service, type Normal, state-level key) and sent as
 * {@code {"envelopeVersion":1,"encryptedPayload":"<cipher>"}}.</p>
 *
 * <p>When enc-service is down the behaviour depends on {@code egov.bulk.envelope.dlq.mode}:
 * ENCRYPTED_ONLY (default) - the API answers 503 and the app retries; nothing plain is written.
 * ALLOW_PLAIN - the plain request goes to the restricted DLQ topic, the API answers 202, and the
 * DLQ is replayed through encryption when enc-service is back.</p>
 *
 * <p>There is one DLQ topic ({@code egov.bulk.dlq.topic}). Every message on it is
 * {@code {"dlqVersion":1,"payloadType":"ENCRYPTED"|"PLAIN","targetTopic":"<bulk topic>","attempts":<n>,
 * "payload":<sealed envelope | plain request>}}. ENCRYPTED: the consumer could not open an envelope
 * because enc-service was down. PLAIN: ALLOW_PLAIN mode only, the API accepted a request while
 * enc-service was down. The type never changes when a message is parked again.</p>
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "egov.pii.encryption.enabled", havingValue = "true")
public class BulkEnvelopeService {

    public static final String PAYLOAD = "encryptedPayload";
    public static final String VERSION = "envelopeVersion";
    static final String DLQ_VERSION = "dlqVersion";
    static final String PAYLOAD_TYPE = "payloadType";
    static final String TARGET_TOPIC = "targetTopic";
    static final String ATTEMPTS = "attempts";
    static final String DLQ_PAYLOAD = "payload";
    /** DLQ message: do not try before this time (epoch ms); set when a try has just failed. Absent = at once. */
    static final String NOT_BEFORE = "notBefore";
    /** Envelope sent on by the replay: how many tries it has had, so a failure after the open is counted on. */
    public static final String REPLAY_ATTEMPTS = "replayAttempts";
    static final String TYPE_ENCRYPTED = "ENCRYPTED";
    static final String TYPE_PLAIN = "PLAIN";
    static final int CURRENT_DLQ_VERSION = 1;
    static final String ALLOW_PLAIN = "ALLOW_PLAIN";

    /** Longest wait for the circuit's open period before one replay try; well below max.poll.interval.ms. */
    static final long MAX_TRIAL_WAIT_MS = 60_000L;
    /** {@link #MAX_TRIAL_WAIT_MS}; tests make it shorter. */
    long maxWaitMs = MAX_TRIAL_WAIT_MS;

    public enum Outcome { ENCRYPTED, PLAIN_DLQ }

    private final EnvelopeEncClient encClient;
    private final ObjectMapper objectMapper;
    private final Producer producer;
    private final PiiEncryptionProperties properties;
    private final EncCircuitBreaker circuitBreaker;

    public BulkEnvelopeService(EnvelopeEncClient encClient,
                               @Qualifier("objectMapper") ObjectMapper objectMapper,
                               Producer producer,
                               PiiEncryptionProperties properties,
                               EncCircuitBreaker circuitBreaker) {
        this.encClient = encClient;
        this.objectMapper = objectMapper;
        this.producer = producer;
        this.properties = properties;
        this.circuitBreaker = circuitBreaker;
        if (properties.getBulkDlqTopic() == null || properties.getBulkDlqTopic().trim().isEmpty()) {
            // Fail at start-up: a parked message would otherwise have no topic to go to.
            throw new IllegalStateException("egov.bulk.dlq.topic is not set; bulk messages cannot be parked");
        }
    }

    /**
     * Seals the request and pushes it to {@code topic}. In ALLOW_PLAIN mode an enc-service
     * outage parks the plain request in the DLQ (payloadType PLAIN) instead; otherwise the
     * {@link EncCircuitBreaker#ENC_UNAVAILABLE} error is thrown for the API to answer 503.
     */
    public Outcome publish(String topic, Object request) {
        try {
            producer.push(topic, seal(request));
            return Outcome.ENCRYPTED;
        } catch (CustomException e) {
            if (!EncCircuitBreaker.ENC_UNAVAILABLE.equals(e.getCode())
                    || !ALLOW_PLAIN.equalsIgnoreCase(properties.getBulkEnvelopeDlqMode())) {
                throw e;
            }
            park(TYPE_PLAIN, topic, request, 0);
            return Outcome.PLAIN_DLQ;
        }
    }

    public Map<String, Object> seal(Object request) {
        String json;
        try {
            json = objectMapper.writeValueAsString(request);
        } catch (JsonProcessingException e) {
            throw new CustomException("INDIVIDUAL_BULK_ENVELOPE_INVALID", "bulk request could not be serialised");
        }
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put(VERSION, 1);
        envelope.put(PAYLOAD, encClient.encrypt(json));
        return envelope;
    }

    /** Opens an envelope. A record without a payload is a plain message from before the rollout. */
    public <T> T open(Map<String, Object> record, Class<T> type) {
        Object payload = record.get(PAYLOAD);
        if (payload == null) {
            return objectMapper.convertValue(record, type);
        }
        String json = encClient.decrypt(String.valueOf(payload));
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new CustomException("INDIVIDUAL_BULK_ENVELOPE_INVALID", "decrypted bulk payload is not valid JSON");
        }
    }

    public static boolean isEncUnavailable(Exception e) {
        return e instanceof CustomException && EncCircuitBreaker.ENC_UNAVAILABLE.equals(((CustomException) e).getCode());
    }

    /**
     * The consumer could not open the envelope or encrypt its records: park it, still encrypted. An envelope that
     * already came back from a replay keeps its count and waits the backoff before the next try, so a failure that
     * is not enc-service being down (for example a missing MDMS policy) cannot go round bulk topic and DLQ at full speed.
     */
    public void parkEncrypted(String topic, Map<String, Object> envelope) {
        if (envelope == null || envelope.get(PAYLOAD) == null) {
            // a message marked ENCRYPTED must never hold a plain request
            throw new IllegalArgumentException("only a sealed envelope can be parked as ENCRYPTED");
        }
        int attempts = envelope.get(REPLAY_ATTEMPTS) instanceof Number ? ((Number) envelope.get(REPLAY_ATTEMPTS)).intValue() : 0;
        park(TYPE_ENCRYPTED, topic, envelope, attempts, attempts > 0 ? retryTime() : 0L);
    }

    /**
     * For a bulk consumer's catch block. Encryption failed, while opening the envelope or while encrypting the records
     * after it was opened: park the ORIGINAL message so it is replayed instead of lost. An envelope is parked as it is,
     * still encrypted. A message from before the rollout has no envelope, so it is plain data:
     * <ul>
     * <li>ALLOW_PLAIN: parked as PLAIN (attempts 0), the same as a plain request the API parks. Plain data waiting in the
     * restricted DLQ is what this mode allows.</li>
     * <li>ENCRYPTED_ONLY: never parked (nothing plain is written), so the caller must handle the failure. The ids of its
     * records are logged at ERROR so the loss can be traced.</li>
     * </ul>
     *
     * @return true when the message was parked (the consumer is done with it), false when the caller must handle the failure
     */
    public boolean parkIfEncFailed(String topic, Map<String, Object> record, Exception exception) {
        if (!EncCircuitBreaker.isEncFailure(exception)) {
            return false;
        }
        if (record.containsKey(PAYLOAD)) {
            parkEncrypted(topic, record);
            return true;
        }
        if (ALLOW_PLAIN.equalsIgnoreCase(properties.getBulkEnvelopeDlqMode())) {
            park(TYPE_PLAIN, topic, record, 0);
            return true;
        }
        log.error("plain bulk message on {} hit an encryption failure ({}) and is NOT parked in {} mode; ids of its records: {}",
                topic, ((CustomException) exception).getCode(), properties.getBulkEnvelopeDlqMode(), recordIds(record));
        return false;
    }

    /**
     * For every list of objects in the record, the id and clientReferenceId of each object, and nothing else:
     * the message is plain person data, so no other field may reach the log.
     */
    private static Map<String, List<Map<String, Object>>> recordIds(Map<String, Object> record) {
        Map<String, List<Map<String, Object>>> ids = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : record.entrySet()) {
            if (!(entry.getValue() instanceof List)) {
                continue;
            }
            List<Map<String, Object>> entries = new ArrayList<>();
            for (Object element : (List<?>) entry.getValue()) {
                if (!(element instanceof Map)) {
                    continue;
                }
                Map<?, ?> object = (Map<?, ?>) element;
                Map<String, Object> idsOfObject = new LinkedHashMap<>();
                for (String key : new String[]{"id", "clientReferenceId"}) {
                    Object value = object.get(key);
                    if (value instanceof String || value instanceof Number) {  // a scalar only, never a nested object
                        idsOfObject.put(key, value);
                    }
                }
                entries.add(idsOfObject);
            }
            if (!entries.isEmpty()) {
                ids.put(entry.getKey(), entries);
            }
        }
        return ids;
    }

    /**
     * Reads one DLQ message and sends it on: ENCRYPTED goes back to its bulk topic as it is, PLAIN is
     * sealed first. A message whose last try failed just now waits until its notBefore time (the retry backoff
     * after that failure; at most 60 s here, a message still not due then goes back to the DLQ unchanged); while the enc-service circuit is open it also waits for the open period to end, so an
     * outage costs one try per open period, not one per message. Messages parked long ago go on at once, so the
     * DLQ drains at full speed once enc-service is back. If the try fails (enc-service still down, or the Kafka
     * send fails) the message is parked again with attempts + 1, the SAME payloadType and a new notBefore.
     * A message with a missing or unknown dlqVersion, payloadType, targetTopic or payload is logged as
     * an error and not touched (no guessing).
     */
    @SuppressWarnings("unchecked")
    public void replayDlq(Map<String, Object> parked) {
        Object version = parked.get(DLQ_VERSION);
        if (!(version instanceof Number) || ((Number) version).intValue() != CURRENT_DLQ_VERSION) {
            log.error("bulk DLQ message ignored: {} is {}, expected {}", DLQ_VERSION, version, CURRENT_DLQ_VERSION);
            return;
        }
        Object type = parked.get(PAYLOAD_TYPE);
        if (!TYPE_ENCRYPTED.equals(type) && !TYPE_PLAIN.equals(type)) {
            log.error("bulk DLQ message ignored: {} is {}, expected {} or {}", PAYLOAD_TYPE, type, TYPE_ENCRYPTED, TYPE_PLAIN);
            return;
        }
        Object target = parked.get(TARGET_TOPIC);
        if (!(target instanceof String) || ((String) target).trim().isEmpty()) {
            log.error("bulk DLQ message ignored: {} is missing", TARGET_TOPIC);
            return;
        }
        Object payload = parked.get(DLQ_PAYLOAD);
        if (!(payload instanceof Map)) {
            log.error("bulk DLQ message ignored: {} is missing or not an object", DLQ_PAYLOAD);
            return;
        }
        int attempts = parked.get(ATTEMPTS) instanceof Number ? ((Number) parked.get(ATTEMPTS)).intValue() : 0;
        long notBefore = parked.get(NOT_BEFORE) instanceof Number ? ((Number) parked.get(NOT_BEFORE)).longValue() : 0L;
        waitUntil(notBefore);
        if (notBefore > System.currentTimeMillis()) {
            // still not due (a long wait after enc-service refused it): back to the DLQ as it is, no enc-service call
            try {
                producer.push(properties.getBulkDlqTopic(), parked);
            } catch (RuntimeException e) {
                log.error("bulk DLQ message for {} could not be put back (attempt {}); it is LOST", target, attempts);
                throw e;
            }
            return;
        }
        circuitBreaker.awaitTrialWindow(maxWaitMs);
        if (TYPE_ENCRYPTED.equals(type)) {
            replayEncrypted((String) target, (Map<String, Object>) payload, attempts);
        } else {
            replayPlain((String) target, payload, attempts);
        }
    }

    /**
     * The envelope goes back to its bulk topic unchanged and stays encrypted. A trial open first checks that
     * enc-service is back, so a message that cannot be opened yet is parked again (attempts + 1) instead of
     * going round the bulk topic with its count lost. The opened text is dropped at once, never stored or sent.
     */
    private void replayEncrypted(String target, Map<String, Object> envelope, int attempts) {
        if (envelope.get(PAYLOAD) == null) {
            log.error("bulk DLQ message ignored: payloadType {} but the payload has no {} (a plain request is never sent on as encrypted)",
                    TYPE_ENCRYPTED, PAYLOAD);
            return;
        }
        try {
            encClient.decrypt(String.valueOf(envelope.get(PAYLOAD)));
        } catch (CustomException e) {
            if (EncCircuitBreaker.ENC_UNAVAILABLE.equals(e.getCode())) {
                parkAgain(TYPE_ENCRYPTED, target, envelope, attempts, e);
                return;
            }
            if (EncCircuitBreaker.ENC_REJECTED.equals(e.getCode())) {
                // enc-service is up but cannot open it: kept (it may become readable, e.g. a key put back), tried
                // again later and later so it neither floods the DLQ nor holds the circuit open
                log.error("bulk DLQ envelope for {} cannot be opened by enc-service (attempt {}); tried again later",
                        target, attempts);
                parkAgain(TYPE_ENCRYPTED, target, envelope, attempts, e, rejectedRetryTime(attempts));
                return;
            }
            log.error("bulk DLQ message ignored: the envelope cannot be opened ({}); it is not sent to {}", e.getCode(), target);
            return;
        }
        Map<String, Object> onward = new LinkedHashMap<>(envelope);  // same sealed payload, try count carried on
        onward.put(REPLAY_ATTEMPTS, attempts + 1);
        try {
            producer.push(target, onward);
        } catch (RuntimeException e) {
            parkAgain(TYPE_ENCRYPTED, target, envelope, attempts, e);
            return;
        }
        log.info("replayed encrypted bulk envelope to {} (attempt {})", target, attempts);
    }

    /** ALLOW_PLAIN only: encrypt, then send on. Still down: park again as PLAIN, nothing is lost. */
    private void replayPlain(String target, Object request, int attempts) {
        try {
            producer.push(target, seal(request));
            log.info("plain DLQ bulk request encrypted and sent to {} (attempt {})", target, attempts);
        } catch (RuntimeException e) {
            parkAgain(TYPE_PLAIN, target, request, attempts, e);
        }
    }

    /**
     * The replay try failed: park the message again, not to be tried before the retry backoff has passed (so a
     * failure that the circuit breaker does not count, such as a Kafka send error, cannot turn into a fast loop).
     */
    private void parkAgain(String payloadType, String target, Object payload, int attempts, RuntimeException cause) {
        parkAgain(payloadType, target, payload, attempts, cause, retryTime());
    }

    private void parkAgain(String payloadType, String target, Object payload, int attempts, RuntimeException cause,
                           long notBefore) {
        log.warn("bulk DLQ replay to {} failed (attempt {}): {}", target, attempts,
                cause instanceof CustomException ? ((CustomException) cause).getCode() : cause.getClass().getSimpleName());
        try {
            park(payloadType, target, payload, attempts + 1, notBefore);
        } catch (RuntimeException e) {
            log.error("bulk DLQ message for {} could not be parked again (attempt {}); it is LOST", target, attempts + 1);
            throw e;
        }
    }

    private void park(String payloadType, String targetTopic, Object payload, int attempts) {
        park(payloadType, targetTopic, payload, attempts, 0L);
    }

    private void park(String payloadType, String targetTopic, Object payload, int attempts, long notBefore) {
        Map<String, Object> parked = new LinkedHashMap<>();
        parked.put(DLQ_VERSION, CURRENT_DLQ_VERSION);
        parked.put(PAYLOAD_TYPE, payloadType);
        parked.put(TARGET_TOPIC, targetTopic);
        parked.put(ATTEMPTS, attempts);
        if (notBefore > 0) {
            parked.put(NOT_BEFORE, notBefore);
        }
        parked.put(DLQ_PAYLOAD, payload);
        producer.push(properties.getBulkDlqTopic(), parked);
        log.warn("enc-service unavailable; bulk {} message parked in {} for {} (attempt {})",
                payloadType, properties.getBulkDlqTopic(), targetTopic, attempts);
    }

    private long retryTime() {
        return System.currentTimeMillis() + properties.getBulkDlqRetryBackoffMs();
    }

    /** backoff x 2^attempts, at most the max backoff: 5 s, 10 s, 20 s ... 1 h with the defaults. */
    long rejectedRetryTime(int attempts) {
        long backoff = properties.getBulkDlqRetryBackoffMs();
        long max = properties.getBulkDlqRetryMaxBackoffMs() == null ? backoff : properties.getBulkDlqRetryMaxBackoffMs();
        long wait = backoff;
        for (int i = 0; i < attempts && wait < max; i++) {
            wait *= 2;
        }
        return System.currentTimeMillis() + Math.min(wait, max);
    }

    /** Waits until notBefore (at most maxWaitMs, well below max.poll.interval.ms). */
    private void waitUntil(long notBefore) {
        long waitMs = Math.min(notBefore - System.currentTimeMillis(), maxWaitMs);
        if (waitMs <= 0) {
            return;
        }
        try {
            Thread.sleep(waitMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
