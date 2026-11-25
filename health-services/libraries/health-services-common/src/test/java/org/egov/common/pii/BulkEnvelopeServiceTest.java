package org.egov.common.pii;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.egov.common.producer.Producer;
import org.egov.tracer.model.CustomException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class BulkEnvelopeServiceTest {

    private static final String BULK_TOPIC = "individual-consumer-bulk-create-topic";
    private static final String DLQ_TOPIC = "individual-bulk-dlq-topic";
    private static final String CIPHER = "key-1|Y2lwaGVyLXRleHQ";

    private EnvelopeEncClient encClient;
    private Producer producer;
    private PiiEncryptionProperties properties;
    private BulkEnvelopeService service;
    private EncCircuitBreaker circuitBreaker;
    private ListAppender<ILoggingEvent> logs;

    @BeforeEach
    void setUp() {
        encClient = mock(EnvelopeEncClient.class);
        producer = mock(Producer.class);
        properties = new PiiEncryptionProperties();
        properties.setBulkDlqTopic(DLQ_TOPIC);
        properties.setBulkDlqRetryBackoffMs(0L);
        properties.setBulkEnvelopeDlqMode("ENCRYPTED_ONLY");
        properties.setEncCircuitFailureThreshold(5);
        properties.setEncCircuitOpenMs(300L);
        circuitBreaker = new EncCircuitBreaker(properties);
        service = new BulkEnvelopeService(encClient, new ObjectMapper(), producer, properties, circuitBreaker);
        logs = new ListAppender<>();
        logs.start();
        ((Logger) LoggerFactory.getLogger(BulkEnvelopeService.class)).addAppender(logs);
    }

    @AfterEach
    void tearDown() {
        ((Logger) LoggerFactory.getLogger(BulkEnvelopeService.class)).detachAppender(logs);
    }

    private static CustomException encDown() {
        return new CustomException(EncCircuitBreaker.ENC_UNAVAILABLE, "enc-service is unavailable");
    }

    private static Map<String, Object> plainRequest() {
        Map<String, Object> name = new LinkedHashMap<>();
        name.put("givenName", "GivenA001");
        name.put("familyName", "FamilyA001");
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("individuals", List.of(name));
        return request;
    }

    private static Map<String, Object> envelope() {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put(BulkEnvelopeService.VERSION, 1);
        envelope.put(BulkEnvelopeService.PAYLOAD, CIPHER);
        return envelope;
    }

    private static Map<String, Object> dlqMessage(Object version, Object type, Object target, Object attempts, Object payload) {
        Map<String, Object> message = new LinkedHashMap<>();
        if (version != null) message.put("dlqVersion", version);
        if (type != null) message.put("payloadType", type);
        if (target != null) message.put("targetTopic", target);
        if (attempts != null) message.put("attempts", attempts);
        if (payload != null) message.put("payload", payload);
        return message;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parkedMessage() {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(producer).push(eq(DLQ_TOPIC), captor.capture());
        return (Map<String, Object>) captor.getValue();
    }

    private boolean errorLogged(String... words) {
        return logs.list.stream().filter(e -> e.getLevel() == Level.ERROR)
                .anyMatch(e -> {
                    String m = e.getFormattedMessage();
                    for (String w : words) {
                        if (!m.contains(w)) return false;
                    }
                    return true;
                });
    }

    // ---------------------------------------------------------------- park: message format

    @Test
    @DisplayName("ALLOW_PLAIN, enc-service down at the API: parked as PLAIN with version, target, attempts 0 and the plain request")
    void apiParksPlainWithMarker() {
        properties.setBulkEnvelopeDlqMode("ALLOW_PLAIN");
        when(encClient.encrypt(anyString())).thenThrow(encDown());
        Map<String, Object> request = plainRequest();

        BulkEnvelopeService.Outcome outcome = service.publish(BULK_TOPIC, request);

        assertEquals(BulkEnvelopeService.Outcome.PLAIN_DLQ, outcome);
        Map<String, Object> parked = parkedMessage();
        assertEquals(List.of("dlqVersion", "payloadType", "targetTopic", "attempts", "payload"),
                List.copyOf(parked.keySet()));
        assertEquals(1, parked.get("dlqVersion"));
        assertEquals("PLAIN", parked.get("payloadType"));
        assertEquals(BULK_TOPIC, parked.get("targetTopic"));
        assertEquals(0, parked.get("attempts"));
        assertSame(request, parked.get("payload"));
        verify(producer, never()).push(eq(BULK_TOPIC), any());
    }

    @Test
    @DisplayName("ENCRYPTED_ONLY (default), enc-service down at the API: error is thrown, nothing is written anywhere")
    void apiRefusesInEncryptedOnlyMode() {
        when(encClient.encrypt(anyString())).thenThrow(encDown());

        CustomException e = assertThrows(CustomException.class, () -> service.publish(BULK_TOPIC, plainRequest()));

        assertEquals(EncCircuitBreaker.ENC_UNAVAILABLE, e.getCode());
        verifyNoInteractions(producer);
    }

    @Test
    @DisplayName("enc-service up at the API: the sealed envelope goes to the bulk topic, DLQ not used")
    void apiPublishesEnvelope() {
        when(encClient.encrypt(anyString())).thenReturn(CIPHER);

        assertEquals(BulkEnvelopeService.Outcome.ENCRYPTED, service.publish(BULK_TOPIC, plainRequest()));

        verify(producer).push(eq(BULK_TOPIC), eq(envelope()));
        verify(producer, never()).push(eq(DLQ_TOPIC), any());
    }

    @Test
    @DisplayName("consumer could not open an envelope: parked as ENCRYPTED, payload is the envelope, no plain field anywhere")
    void consumerParksEncryptedWithMarker() throws Exception {
        when(encClient.encrypt(anyString())).thenReturn(CIPHER);
        Map<String, Object> sealed = service.seal(plainRequest());

        service.parkEncrypted(BULK_TOPIC, sealed);

        Map<String, Object> parked = parkedMessage();
        assertEquals(List.of("dlqVersion", "payloadType", "targetTopic", "attempts", "payload"),
                List.copyOf(parked.keySet()));
        assertEquals(1, parked.get("dlqVersion"));
        assertEquals("ENCRYPTED", parked.get("payloadType"));
        assertEquals(BULK_TOPIC, parked.get("targetTopic"));
        assertEquals(0, parked.get("attempts"));
        assertSame(sealed, parked.get("payload"));
        assertEquals(Set.of("envelopeVersion", "encryptedPayload"), ((Map<?, ?>) parked.get("payload")).keySet());
        String whole = new ObjectMapper().writeValueAsString(parked);
        assertFalse(whole.contains("GivenA001") || whole.contains("FamilyA001"), "plain data found in ENCRYPTED message: " + whole);
    }

    @Test
    @DisplayName("a plain request can never be parked as ENCRYPTED")
    void plainCannotBeParkedAsEncrypted() {
        assertThrows(IllegalArgumentException.class, () -> service.parkEncrypted(BULK_TOPIC, plainRequest()));
        assertThrows(IllegalArgumentException.class, () -> service.parkEncrypted(BULK_TOPIC, null));
        verifyNoInteractions(producer);
    }

    // ---------------------------------------------------------------- replay: ENCRYPTED

    @Test
    @DisplayName("replay ENCRYPTED, enc-service back: the same sealed payload goes to targetTopic with the try count, never sealed again")
    void replayEncryptedSendsUnchangedEnvelope() {
        when(encClient.decrypt(CIPHER)).thenReturn("{\"individuals\":[]}");
        Map<String, Object> envelope = envelope();

        service.replayDlq(dlqMessage(1, "ENCRYPTED", BULK_TOPIC, 2, envelope));

        ArgumentCaptor<Object> sent = ArgumentCaptor.forClass(Object.class);
        verify(producer).push(eq(BULK_TOPIC), sent.capture());
        Map<?, ?> onward = (Map<?, ?>) sent.getValue();
        assertEquals(CIPHER, onward.get("encryptedPayload"));
        assertEquals(1, onward.get("envelopeVersion"));
        assertEquals(3, onward.get(BulkEnvelopeService.REPLAY_ATTEMPTS));
        verify(producer, never()).push(eq(DLQ_TOPIC), any());
        verify(encClient, never()).encrypt(anyString());
    }

    @Test
    @DisplayName("replay ENCRYPTED, enc-service still down: parked again as ENCRYPTED, attempts + 1, same envelope, nothing sent to targetTopic")
    void replayEncryptedStillDownParksAgain() {
        when(encClient.decrypt(CIPHER)).thenThrow(encDown());
        Map<String, Object> envelope = envelope();

        service.replayDlq(dlqMessage(1, "ENCRYPTED", BULK_TOPIC, 3, envelope));

        Map<String, Object> parked = parkedMessage();
        assertEquals("ENCRYPTED", parked.get("payloadType"));
        assertEquals(4, parked.get("attempts"));
        assertEquals(BULK_TOPIC, parked.get("targetTopic"));
        assertSame(envelope, parked.get("payload"));
        verify(producer, never()).push(eq(BULK_TOPIC), any());
    }

    @Test
    @DisplayName("attempts keeps rising on the encrypted path: 0 -> 1 -> 2")
    void encryptedAttemptsIncreaseEveryRound() {
        when(encClient.decrypt(CIPHER)).thenThrow(encDown());
        Map<String, Object> message = dlqMessage(1, "ENCRYPTED", BULK_TOPIC, 0, envelope());

        for (int expected = 1; expected <= 2; expected++) {
            org.mockito.Mockito.clearInvocations(producer);
            service.replayDlq(message);
            message = parkedMessage();
            assertEquals(expected, message.get("attempts"));
            assertEquals("ENCRYPTED", message.get("payloadType"));
        }
    }

    @Test
    @DisplayName("replay ENCRYPTED whose payload has no encryptedPayload (a plain request): error logged, nothing sent anywhere")
    void replayEncryptedRefusesPlainPayload() {
        service.replayDlq(dlqMessage(1, "ENCRYPTED", BULK_TOPIC, 0, plainRequest()));

        verifyNoInteractions(producer);
        verifyNoInteractions(encClient);
        assertTrue(errorLogged("ENCRYPTED", "encryptedPayload"), "expected an error naming the problem");
    }

    @Test
    @DisplayName("replay ENCRYPTED with an envelope that can never be opened (not enc-service down): error logged, not sent on")
    void replayEncryptedBrokenEnvelopeIsNotSentOn() {
        when(encClient.decrypt(CIPHER)).thenThrow(new CustomException("INDIVIDUAL_BULK_ENVELOPE_INVALID", "not cipher"));

        service.replayDlq(dlqMessage(1, "ENCRYPTED", BULK_TOPIC, 0, envelope()));

        verifyNoInteractions(producer);
        assertTrue(errorLogged("INDIVIDUAL_BULK_ENVELOPE_INVALID"));
    }

    // ---------------------------------------------------------------- replay: PLAIN

    @Test
    @DisplayName("replay PLAIN, enc-service back: the plain request is sealed and the envelope goes to targetTopic")
    void replayPlainSealsAndSends() {
        when(encClient.encrypt(anyString())).thenReturn(CIPHER);

        service.replayDlq(dlqMessage(1, "PLAIN", BULK_TOPIC, 0, plainRequest()));

        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(encClient).encrypt(json.capture());
        assertTrue(json.getValue().contains("GivenA001"), "the request must be what is sealed");
        verify(producer).push(eq(BULK_TOPIC), eq(envelope()));
        verify(producer, never()).push(eq(DLQ_TOPIC), any());
    }

    @Test
    @DisplayName("replay PLAIN, enc-service still down: parked again as PLAIN (never ENCRYPTED), attempts + 1, same plain request")
    void replayPlainStillDownParksAgainAsPlain() {
        when(encClient.encrypt(anyString())).thenThrow(encDown());
        Map<String, Object> request = plainRequest();

        service.replayDlq(dlqMessage(1, "PLAIN", BULK_TOPIC, 1, request));

        Map<String, Object> parked = parkedMessage();
        assertEquals("PLAIN", parked.get("payloadType"));
        assertEquals(2, parked.get("attempts"));
        assertEquals(BULK_TOPIC, parked.get("targetTopic"));
        assertSame(request, parked.get("payload"));
        verify(producer, never()).push(eq(BULK_TOPIC), any());
    }

    @Test
    @DisplayName("replay that works does not wait: once enc-service is back the parked messages go on at full speed")
    void replayThatWorksDoesNotWait() {
        properties.setBulkDlqRetryBackoffMs(2_000L);
        when(encClient.encrypt(anyString())).thenReturn(CIPHER);

        long start = System.nanoTime();
        service.replayDlq(dlqMessage(1, "PLAIN", BULK_TOPIC, 0, plainRequest()));
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertTrue(elapsedMs < 1_000, "waited " + elapsedMs + " ms");
        verify(producer).push(eq(BULK_TOPIC), eq(envelope()));
    }

    @Test
    @DisplayName("replay that fails parks the message again at once, marked not to be tried before the backoff has passed")
    void failedReplaySetsNotBefore() {
        properties.setBulkDlqRetryBackoffMs(5_000L);
        when(encClient.encrypt(anyString())).thenThrow(encDown());

        long before = System.currentTimeMillis();
        service.replayDlq(dlqMessage(1, "PLAIN", BULK_TOPIC, 0, plainRequest()));

        Map<String, Object> parked = parkedMessage();
        assertEquals(1, parked.get("attempts"));
        long notBefore = ((Number) parked.get("notBefore")).longValue();
        assertTrue(notBefore >= before + 5_000 && notBefore <= System.currentTimeMillis() + 5_000, "notBefore " + (notBefore - before));
    }

    @Test
    @DisplayName("a message whose notBefore is in the future waits until then; one parked long ago goes at once")
    void notBeforeIsRespected() {
        when(encClient.decrypt(CIPHER)).thenReturn("{\"individuals\":[]}");
        Map<String, Object> soon = dlqMessage(1, "ENCRYPTED", BULK_TOPIC, 1, envelope());
        soon.put("notBefore", System.currentTimeMillis() + 300);
        Map<String, Object> old = dlqMessage(1, "ENCRYPTED", BULK_TOPIC, 4, envelope());
        old.put("notBefore", System.currentTimeMillis() - 3_600_000L);

        long start = System.nanoTime();
        service.replayDlq(soon);
        long waited = (System.nanoTime() - start) / 1_000_000;
        start = System.nanoTime();
        service.replayDlq(old);
        long oldWaited = (System.nanoTime() - start) / 1_000_000;

        assertTrue(waited >= 250, "waited only " + waited + " ms");
        assertTrue(oldWaited < 200, "an old message waited " + oldWaited + " ms");
    }

    @Test
    @DisplayName("envelope enc-service refuses to open: kept, parked again with a doubling wait; the circuit stays closed")
    void rejectedEnvelopeBacksOffAndDoesNotOpenCircuit() {
        properties.setBulkDlqRetryBackoffMs(5_000L);
        properties.setBulkDlqRetryMaxBackoffMs(3_600_000L);
        when(encClient.decrypt(CIPHER)).thenAnswer(call -> circuitBreaker.call("open a bulk envelope", () -> {
            throw new CustomException(EncCircuitBreaker.ENC_REJECTED, "HTTP 500");
        }));

        long before = System.currentTimeMillis();
        service.replayDlq(dlqMessage(1, "ENCRYPTED", BULK_TOPIC, 3, envelope()));

        Map<String, Object> parked = parkedMessage();
        assertEquals(4, parked.get("attempts"));
        long wait = ((Number) parked.get("notBefore")).longValue() - before;
        assertTrue(wait >= 40_000 && wait <= 41_000, "wait " + wait);   // 5 s x 2^3
        verify(producer, never()).push(eq(BULK_TOPIC), any());
        for (int i = 0; i < 10; i++) {
            service.replayDlq(dlqMessage(1, "ENCRYPTED", BULK_TOPIC, 0, envelope()));
        }
        assertTrue(!circuitBreaker.isOpen(), "the circuit opened on refusals");
        assertTrue(errorLogged("cannot be opened by enc-service"));
    }

    @Test
    @DisplayName("the doubling wait of a refused envelope stops at the max backoff")
    void rejectedBackoffIsCapped() {
        properties.setBulkDlqRetryBackoffMs(5_000L);
        properties.setBulkDlqRetryMaxBackoffMs(3_600_000L);
        long now = System.currentTimeMillis();
        long wait = service.rejectedRetryTime(40) - now;
        assertTrue(wait >= 3_599_000 && wait <= 3_601_000, "wait " + wait);
        assertTrue(service.rejectedRetryTime(0) - now <= 5_100);
    }

    @Test
    @DisplayName("a message not due after the longest wait goes back to the DLQ unchanged, without calling enc-service")
    void notDueMessageGoesBackUnchanged() {
        service.maxWaitMs = 50;
        Map<String, Object> message = dlqMessage(1, "ENCRYPTED", BULK_TOPIC, 6, envelope());
        message.put("notBefore", System.currentTimeMillis() + 600_000L);

        service.replayDlq(message);

        assertSame(message, parkedMessage());
        verify(encClient, never()).decrypt(anyString());
        verify(producer, never()).push(eq(BULK_TOPIC), any());
    }

    @Test
    @DisplayName("an envelope that came back from a replay and fails again keeps its count and gets a notBefore (no fast loop)")
    void replayedEnvelopeFailingAgainIsSlowedDown() {
        properties.setBulkDlqRetryBackoffMs(5_000L);
        Map<String, Object> envelope = envelope();
        envelope.put(BulkEnvelopeService.REPLAY_ATTEMPTS, 2);

        service.parkEncrypted(BULK_TOPIC, envelope);

        Map<String, Object> parked = parkedMessage();
        assertEquals(2, parked.get("attempts"));
        assertTrue(((Number) parked.get("notBefore")).longValue() > System.currentTimeMillis() + 4_000);
    }

    @Test
    @DisplayName("circuit open: the replay first waits for the open period to end, then tries once")
    void openCircuitWaitsForTrialWindow() {
        for (int i = 0; i < 5; i++) { // five failures in a row open the circuit for 300 ms
            assertThrows(RuntimeException.class, () -> circuitBreaker.call("test", () -> { throw new IllegalStateException("down"); }));
        }
        assertTrue(circuitBreaker.isOpen());
        when(encClient.decrypt(CIPHER)).thenReturn("{\"individuals\":[]}");

        long start = System.nanoTime();
        service.replayDlq(dlqMessage(1, "ENCRYPTED", BULK_TOPIC, 0, envelope()));
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertTrue(elapsedMs >= 200, "waited only " + elapsedMs + " ms");
        verify(producer).push(eq(BULK_TOPIC), any());
    }

    @Test
    @DisplayName("replay ENCRYPTED, Kafka send to targetTopic fails: parked again as ENCRYPTED with attempts + 1, not dropped")
    void encryptedSendFailureParksAgain() {
        when(encClient.decrypt(CIPHER)).thenReturn("{\"individuals\":[]}");
        Map<String, Object> envelope = envelope();
        org.mockito.Mockito.doThrow(new CustomException("EVENT_BUS_FAILURE", "send failed"))
                .when(producer).push(eq(BULK_TOPIC), any());

        service.replayDlq(dlqMessage(1, "ENCRYPTED", BULK_TOPIC, 2, envelope));

        Map<String, Object> parked = parkedMessage();
        assertEquals("ENCRYPTED", parked.get("payloadType"));
        assertEquals(3, parked.get("attempts"));
        assertSame(envelope, parked.get("payload"));
    }

    @Test
    @DisplayName("replay PLAIN, Kafka send to targetTopic fails: parked again as PLAIN with attempts + 1, not dropped")
    void plainSendFailureParksAgain() {
        when(encClient.encrypt(anyString())).thenReturn(CIPHER);
        Map<String, Object> request = plainRequest();
        org.mockito.Mockito.doThrow(new CustomException("EVENT_BUS_FAILURE", "send failed"))
                .when(producer).push(eq(BULK_TOPIC), any());

        service.replayDlq(dlqMessage(1, "PLAIN", BULK_TOPIC, 0, request));

        Map<String, Object> parked = parkedMessage();
        assertEquals("PLAIN", parked.get("payloadType"));
        assertEquals(1, parked.get("attempts"));
        assertSame(request, parked.get("payload"));
    }

    @Test
    @DisplayName("the message cannot be parked again either (Kafka down): the error is logged as LOST and thrown")
    void parkAgainFailureIsLoggedAndThrown() {
        when(encClient.decrypt(CIPHER)).thenThrow(encDown());
        org.mockito.Mockito.doThrow(new CustomException("EVENT_BUS_FAILURE", "send failed"))
                .when(producer).push(eq(DLQ_TOPIC), any());

        assertThrows(CustomException.class,
                () -> service.replayDlq(dlqMessage(1, "ENCRYPTED", BULK_TOPIC, 0, envelope())));

        assertTrue(errorLogged("LOST", BULK_TOPIC));
    }

    // ---------------------------------------------------------------- replay: bad messages (no guessing)

    @Test
    @DisplayName("unknown payloadType is rejected: error names payloadType, nothing is sent, enc-service not called")
    void unknownPayloadTypeRejected() {
        service.replayDlq(dlqMessage(1, "SECRET", BULK_TOPIC, 0, envelope()));

        verifyNoInteractions(producer);
        verifyNoInteractions(encClient);
        assertTrue(errorLogged("payloadType", "SECRET"));
    }

    @Test
    @DisplayName("missing payloadType is rejected, not guessed from the payload shape")
    void missingPayloadTypeRejected() {
        service.replayDlq(dlqMessage(1, null, BULK_TOPIC, 0, envelope()));
        service.replayDlq(dlqMessage(1, "encrypted", BULK_TOPIC, 0, envelope())); // wrong case is also not guessed

        verifyNoInteractions(producer);
        verifyNoInteractions(encClient);
        assertTrue(errorLogged("payloadType", "null"));
    }

    @Test
    @DisplayName("missing or unknown dlqVersion is rejected")
    void badVersionRejected() {
        service.replayDlq(dlqMessage(null, "ENCRYPTED", BULK_TOPIC, 0, envelope()));
        service.replayDlq(dlqMessage(2, "ENCRYPTED", BULK_TOPIC, 0, envelope()));
        service.replayDlq(dlqMessage("1", "ENCRYPTED", BULK_TOPIC, 0, envelope()));

        verifyNoInteractions(producer);
        verifyNoInteractions(encClient);
        assertEquals(3, logs.list.stream().filter(e -> e.getLevel() == Level.ERROR && e.getFormattedMessage().contains("dlqVersion")).count());
    }

    @Test
    @DisplayName("an old-format message (targetTopic + envelope/request, no marker) is rejected, not guessed")
    void oldFormatRejected() {
        Map<String, Object> old = new LinkedHashMap<>();
        old.put("targetTopic", BULK_TOPIC);
        old.put("attempts", 0);
        old.put("envelope", envelope());

        service.replayDlq(old);

        verifyNoInteractions(producer);
        verifyNoInteractions(encClient);
    }

    @Test
    @DisplayName("missing targetTopic or payload is rejected, nothing is sent to a topic called \"null\"")
    void missingTargetOrPayloadRejected() {
        service.replayDlq(dlqMessage(1, "PLAIN", null, 0, plainRequest()));
        service.replayDlq(dlqMessage(1, "PLAIN", "  ", 0, plainRequest()));
        service.replayDlq(dlqMessage(1, "PLAIN", BULK_TOPIC, 0, null));
        service.replayDlq(dlqMessage(1, "ENCRYPTED", BULK_TOPIC, 0, "not-an-object"));

        verifyNoInteractions(producer);
        verifyNoInteractions(encClient);
        assertTrue(errorLogged(BulkEnvelopeService.TARGET_TOPIC));
        assertTrue(errorLogged(BulkEnvelopeService.DLQ_PAYLOAD));
    }

    @Test
    @DisplayName("missing attempts counts as 0, so the first re-park writes attempts 1")
    void missingAttemptsIsZero() {
        when(encClient.encrypt(anyString())).thenThrow(encDown());

        service.replayDlq(dlqMessage(1, "PLAIN", BULK_TOPIC, null, plainRequest()));

        assertEquals(1, parkedMessage().get("attempts"));
    }

    // ---------------------------------------------------------------- open(): old messages still read as plain

    @Test
    @DisplayName("a bulk record without encryptedPayload (old app / pre-release) is read as plain, enc-service not called")
    void recordWithoutEnvelopeIsReadAsPlain() {
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("name", "GivenA001");

        Map<?, ?> opened = service.open(record, Map.class);

        assertEquals("GivenA001", opened.get("name"));
        verifyNoInteractions(encClient);
    }

    @Test
    @DisplayName("a bulk record with encryptedPayload is opened through enc-service")
    void recordWithEnvelopeIsDecrypted() {
        when(encClient.decrypt(CIPHER)).thenReturn("{\"name\":\"GivenA001\"}");

        Map<?, ?> opened = service.open(envelope(), Map.class);

        assertEquals("GivenA001", opened.get("name"));
        assertEquals(List.of("GivenA001"), opened.values().stream().map(String::valueOf).collect(Collectors.toList()));
    }
    // ---------------------------------------------------------------- parkIfEncFailed: the consumer's catch block

    @Test
    @DisplayName("parkIfEncFailed: an encryption failure on an envelope parks the ORIGINAL record ENCRYPTED and returns true")
    void parkIfEncFailedParksEnvelope() {
        Map<String, Object> record = envelope();

        assertTrue(service.parkIfEncFailed(BULK_TOPIC, record, encDown()));

        Map<String, Object> parked = parkedMessage();
        assertEquals("ENCRYPTED", parked.get("payloadType"));
        assertEquals(BULK_TOPIC, parked.get("targetTopic"));
        assertEquals(0, parked.get("attempts"));
        assertSame(record, parked.get("payload"));
    }

    @Test
    @DisplayName("parkIfEncFailed: every encryption failure code parks")
    void parkIfEncFailedEveryCode() {
        for (String code : List.of(EncCircuitBreaker.ENC_UNAVAILABLE, EncCircuitBreaker.ENC_REJECTED, "ENCRYPTION_SERVICE_ERROR",
                "INDIVIDUAL_ENCRYPTION_ERROR", "INDIVIDUAL_ENCRYPTION_TRANSPORT_ERROR", "INDIVIDUAL_ENCRYPTION_NULL_RESULT")) {
            org.mockito.Mockito.clearInvocations(producer);

            assertTrue(service.parkIfEncFailed(BULK_TOPIC, envelope(), new CustomException(code, "failed")), code);

            verify(producer).push(eq(DLQ_TOPIC), any());
        }
    }

    @Test
    @DisplayName("parkIfEncFailed: a failure that is not an encryption failure is not parked (returns false)")
    void parkIfEncFailedIgnoresOtherFailures() {
        assertFalse(service.parkIfEncFailed(BULK_TOPIC, envelope(), new CustomException("EVENT_BUS_FAILURE", "send failed")));
        assertFalse(service.parkIfEncFailed(BULK_TOPIC, envelope(), new RuntimeException("ENCRYPTION_SERVICE_ERROR")));

        verifyNoInteractions(producer);
    }

    /** An old plain bulk request with person data: only "id" and "clientReferenceId" of the listed objects may reach a log. */
    private static Map<String, Object> plainHouseholdRequest() {
        Map<String, Object> address = new LinkedHashMap<>();
        address.put("doorNo", "SECRET-DOOR-NO");
        Map<String, Object> nestedField = new LinkedHashMap<>();
        nestedField.put("id", "NESTED-ID-NOT-LOGGED");
        nestedField.put("value", "SECRET-NESTED-VALUE");
        Map<String, Object> first = new LinkedHashMap<>();
        first.put("id", "hh-id-1");
        first.put("clientReferenceId", "client-ref-1");
        first.put("address", address);
        first.put("additionalFields", Map.of("fields", List.of(nestedField)));
        Map<String, Object> second = new LinkedHashMap<>();
        second.put("clientReferenceId", "client-ref-2");   // no server id yet on a create
        second.put("memberCount", "SECRET-COUNT");
        Map<String, Object> third = new LinkedHashMap<>();
        third.put("id", Map.of("unexpected", "SECRET-NESTED-ID"));   // not a scalar: never logged
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("RequestInfo", Map.of("userInfo", Map.of("userName", "SECRET-USER")));
        request.put("Households", List.of(first, second, third));
        request.put("Others", List.of("not-an-object", Map.of("id", "other-id-1")));
        request.put("apiOperation", "SECRET-OPERATION");
        return request;
    }

    @Test
    @DisplayName("parkIfEncFailed, ALLOW_PLAIN: an old plain record (no envelope) is parked as PLAIN, attempts 0, and returns true")
    void parkIfEncFailedParksPlainRecordInAllowPlainMode() {
        properties.setBulkEnvelopeDlqMode("ALLOW_PLAIN");
        Map<String, Object> record = plainHouseholdRequest();

        assertTrue(service.parkIfEncFailed(BULK_TOPIC, record, encDown()));

        Map<String, Object> parked = parkedMessage();
        assertEquals(1, parked.get("dlqVersion"));
        assertEquals("PLAIN", parked.get("payloadType"));
        assertEquals(BULK_TOPIC, parked.get("targetTopic"));
        assertEquals(0, parked.get("attempts"));
        assertFalse(parked.containsKey("notBefore"));
        assertSame(record, parked.get("payload"));
    }

    @Test
    @DisplayName("parkIfEncFailed, ALLOW_PLAIN: every encryption failure code parks a plain record; a failure that is not one does not")
    void parkIfEncFailedPlainRecordInAllowPlainModeEveryCode() {
        properties.setBulkEnvelopeDlqMode("ALLOW_PLAIN");
        for (String code : List.of(EncCircuitBreaker.ENC_UNAVAILABLE, EncCircuitBreaker.ENC_REJECTED, "ENCRYPTION_SERVICE_ERROR",
                "INDIVIDUAL_ENCRYPTION_ERROR", "INDIVIDUAL_ENCRYPTION_TRANSPORT_ERROR", "INDIVIDUAL_ENCRYPTION_NULL_RESULT")) {
            org.mockito.Mockito.clearInvocations(producer);

            assertTrue(service.parkIfEncFailed(BULK_TOPIC, plainRequest(), new CustomException(code, "failed")), code);

            verify(producer).push(eq(DLQ_TOPIC), any());
        }
        org.mockito.Mockito.clearInvocations(producer);

        assertFalse(service.parkIfEncFailed(BULK_TOPIC, plainRequest(), new CustomException("EVENT_BUS_FAILURE", "send failed")));
        verifyNoInteractions(producer);
    }

    @Test
    @DisplayName("parkIfEncFailed, ENCRYPTED_ONLY: an old plain record is never parked, returns false and logs the ids at ERROR")
    void parkIfEncFailedNeverParksPlainRecordInEncryptedOnlyMode() {
        assertFalse(service.parkIfEncFailed(BULK_TOPIC, plainHouseholdRequest(), encDown()));

        verifyNoInteractions(producer);
        assertTrue(errorLogged(BULK_TOPIC, "hh-id-1", "client-ref-1", "client-ref-2", "other-id-1"));
    }

    @Test
    @DisplayName("parkIfEncFailed, ENCRYPTED_ONLY: the ERROR log holds only id and clientReferenceId, no other field")
    void parkIfEncFailedLogsOnlyIds() {
        service.parkIfEncFailed(BULK_TOPIC, plainHouseholdRequest(), new CustomException(EncCircuitBreaker.ENC_UNAVAILABLE, "SECRET-MESSAGE"));

        String logged = logs.list.stream().map(ILoggingEvent::getFormattedMessage).collect(Collectors.joining("\n"));
        assertTrue(logged.contains("hh-id-1"), logged);
        for (String secret : List.of("SECRET-DOOR-NO", "SECRET-NESTED-VALUE", "NESTED-ID-NOT-LOGGED", "SECRET-COUNT",
                "SECRET-NESTED-ID", "SECRET-USER", "SECRET-OPERATION", "SECRET-MESSAGE", "not-an-object", "memberCount")) {
            assertFalse(logged.contains(secret), secret + " reached the log: " + logged);
        }
    }

    @Test
    @DisplayName("parkIfEncFailed, ENCRYPTED_ONLY: a failure that is not an encryption failure logs nothing here and returns false")
    void parkIfEncFailedPlainRecordOtherFailureIsNotLoggedHere() {
        assertFalse(service.parkIfEncFailed(BULK_TOPIC, plainHouseholdRequest(), new CustomException("EVENT_BUS_FAILURE", "send failed")));

        verifyNoInteractions(producer);
        assertTrue(logs.list.isEmpty());
    }

    @Test
    @DisplayName("no DLQ topic configured -> the service does not start (a parked message would have nowhere to go)")
    void blankDlqTopicFailsAtStart() {
        properties.setBulkDlqTopic("  ");
        assertThrows(IllegalStateException.class,
                () -> new BulkEnvelopeService(encClient, new ObjectMapper(), producer, properties, circuitBreaker));
        properties.setBulkDlqTopic(null);
        assertThrows(IllegalStateException.class,
                () -> new BulkEnvelopeService(encClient, new ObjectMapper(), producer, properties, circuitBreaker));
    }
}
