package org.egov.individual.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.egov.common.models.individual.IndividualBulkRequest;
import org.egov.common.producer.Producer;
import org.egov.common.pii.BulkEnvelopeService;
import org.egov.individual.service.IndividualService;
import org.egov.common.pii.EncCircuitBreaker;
import org.egov.common.pii.EnvelopeEncClient;
import org.egov.common.pii.PiiEncryptionProperties;
import org.egov.tracer.model.CustomException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.annotation.KafkaListener;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Consumer with the real BulkEnvelopeService; only enc-service, Kafka producer and IndividualService are mocked. */
class IndividualConsumerTest {

    private static final String CREATE_TOPIC = "individual-consumer-bulk-create-topic";
    private static final String UPDATE_TOPIC = "individual-consumer-bulk-update-topic";
    private static final String DELETE_TOPIC = "individual-consumer-bulk-delete-topic";
    private static final String DLQ_TOPIC = "individual-bulk-dlq-topic";
    private static final String CIPHER = "key-1|Y2lwaGVyLXRleHQ";

    private EnvelopeEncClient encClient;
    private Producer producer;
    private IndividualService individualService;
    private IndividualConsumer consumer;

    @BeforeEach
    void setUp() {
        encClient = mock(EnvelopeEncClient.class);
        producer = mock(Producer.class);
        individualService = mock(IndividualService.class);
        PiiEncryptionProperties properties = new PiiEncryptionProperties();
        properties.setBulkDlqTopic(DLQ_TOPIC);
        properties.setBulkDlqRetryBackoffMs(0L);
        properties.setBulkEnvelopeDlqMode("ENCRYPTED_ONLY");
        properties.setEncCircuitFailureThreshold(5);
        properties.setEncCircuitOpenMs(30_000L);
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        BulkEnvelopeService envelopeService = new BulkEnvelopeService(encClient, objectMapper, producer, properties,
                new EncCircuitBreaker(properties));
        consumer = new IndividualConsumer(individualService, objectMapper, envelopeService);
    }

    private static CustomException encDown() {
        return new CustomException(EncCircuitBreaker.ENC_UNAVAILABLE, "enc-service is unavailable");
    }

    private static Map<String, Object> envelope() {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("envelopeVersion", 1);
        envelope.put("encryptedPayload", CIPHER);
        return envelope;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parkedInDlq() {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(producer).push(eq(DLQ_TOPIC), captor.capture());
        return (Map<String, Object>) captor.getValue();
    }

    // ---------------------------------------------------------------- one DLQ listener, one property

    @Test
    @DisplayName("exactly one listener reads the DLQ, on individual.bulk.dlq.topic; no listener uses an old DLQ property")
    void oneDlqListener() {
        List<Method> dlqListeners = Arrays.stream(IndividualConsumer.class.getDeclaredMethods())
                .filter(m -> m.isAnnotationPresent(KafkaListener.class))
                .filter(m -> Arrays.stream(m.getAnnotation(KafkaListener.class).topics()).anyMatch(t -> t.contains("dlq")))
                .collect(Collectors.toList());

        assertEquals(1, dlqListeners.size(), "DLQ listeners: " + dlqListeners);
        assertEquals("replayBulkDlq", dlqListeners.get(0).getName());
        assertEquals("${egov.bulk.dlq.topic}",
                dlqListeners.get(0).getAnnotation(KafkaListener.class).topics()[0]);
    }

    @Test
    @DisplayName("application.properties has only the single individual.bulk.dlq.topic, the two old keys are gone")
    void propertiesHaveOneDlqTopic() throws Exception {
        Properties props = new Properties();
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("application.properties")) {
            assertNotNull(in);
            props.load(in);
        }
        assertEquals("individual-bulk-dlq-topic", props.getProperty("individual.bulk.dlq.topic"));
        assertFalse(props.containsKey("individual.bulk.plain.dlq.topic"));
        assertFalse(props.containsKey("individual.bulk.encrypted.dlq.topic"));
    }

    // ---------------------------------------------------------------- bulk listeners park ENCRYPTED

    @Test
    @DisplayName("bulk create: envelope cannot be opened (enc-service down) -> parked ENCRYPTED for the create topic, nothing processed")
    void createParksEncrypted() {
        when(encClient.decrypt(CIPHER)).thenThrow(encDown());
        Map<String, Object> record = envelope();

        assertTrue(consumer.bulkCreate(record, CREATE_TOPIC).isEmpty());

        Map<String, Object> parked = parkedInDlq();
        assertEquals(1, parked.get("dlqVersion"));
        assertEquals("ENCRYPTED", parked.get("payloadType"));
        assertEquals(CREATE_TOPIC, parked.get("targetTopic"));
        assertEquals(0, parked.get("attempts"));
        assertSame(record, parked.get("payload"));
        verifyNoInteractions(individualService);
    }

    @Test
    @DisplayName("bulk update and delete park ENCRYPTED for their own topic")
    void updateAndDeleteParkEncrypted() {
        when(encClient.decrypt(CIPHER)).thenThrow(encDown());

        consumer.bulkUpdate(envelope(), UPDATE_TOPIC);
        consumer.bulkDelete(envelope(), DELETE_TOPIC);

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(producer, org.mockito.Mockito.times(2)).push(eq(DLQ_TOPIC), captor.capture());
        @SuppressWarnings("unchecked")
        Map<String, Object> first = (Map<String, Object>) captor.getAllValues().get(0);
        @SuppressWarnings("unchecked")
        Map<String, Object> second = (Map<String, Object>) captor.getAllValues().get(1);
        assertEquals(UPDATE_TOPIC, first.get("targetTopic"));
        assertEquals(DELETE_TOPIC, second.get("targetTopic"));
        assertEquals("ENCRYPTED", first.get("payloadType"));
        assertEquals("ENCRYPTED", second.get("payloadType"));
        verifyNoInteractions(individualService);
    }

    @Test
    @DisplayName("envelope opened fine but enc-service goes down inside the service: the ORIGINAL envelope is parked ENCRYPTED")
    void parksOriginalEnvelopeWhenServiceFails() {
        when(encClient.decrypt(CIPHER)).thenReturn("{\"individuals\":[]}");
        when(individualService.createFromQueue(any(IndividualBulkRequest.class))).thenThrow(encDown());
        Map<String, Object> record = envelope();

        consumer.bulkCreate(record, CREATE_TOPIC);

        Map<String, Object> parked = parkedInDlq();
        assertEquals("ENCRYPTED", parked.get("payloadType"));
        assertSame(record, parked.get("payload"));
    }

    @Test
    @DisplayName("every encryption failure code after the open parks the envelope: enc-client's own code, missing or wrong MDMS policy")
    void everyEncFailureCodeParks() {
        when(encClient.decrypt(CIPHER)).thenReturn("{\"individuals\":[]}");
        for (String code : List.of("ENCRYPTION_SERVICE_ERROR", "INDIVIDUAL_ENCRYPTION_ERROR",
                "INDIVIDUAL_ENCRYPTION_TRANSPORT_ERROR", "INDIVIDUAL_ENCRYPTION_NULL_RESULT")) {
            clearInvocations(producer);
            when(individualService.createFromQueue(any(IndividualBulkRequest.class)))
                    .thenThrow(new CustomException(code, "failed"));
            Map<String, Object> record = envelope();

            consumer.bulkCreate(record, CREATE_TOPIC);

            Map<String, Object> parked = parkedInDlq();
            assertEquals("ENCRYPTED", parked.get("payloadType"), code);
            assertSame(record, parked.get("payload"), code);
        }
    }

    @Test
    @DisplayName("update and delete: enc-service fails after the open -> the original envelope is parked for its own topic")
    void updateAndDeleteParkWhenServiceFails() {
        when(encClient.decrypt(CIPHER)).thenReturn("{\"individuals\":[]}");
        when(individualService.updateFromQueue(any(IndividualBulkRequest.class)))
                .thenThrow(new CustomException("ENCRYPTION_SERVICE_ERROR", "failed"));
        when(individualService.deleteFromQueue(any(IndividualBulkRequest.class)))
                .thenThrow(new CustomException("ENCRYPTION_SERVICE_ERROR", "failed"));

        consumer.bulkUpdate(envelope(), UPDATE_TOPIC);
        consumer.bulkDelete(envelope(), DELETE_TOPIC);

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(producer, org.mockito.Mockito.times(2)).push(eq(DLQ_TOPIC), captor.capture());
        assertEquals(UPDATE_TOPIC, ((Map<?, ?>) captor.getAllValues().get(0)).get("targetTopic"));
        assertEquals(DELETE_TOPIC, ((Map<?, ?>) captor.getAllValues().get(1)).get("targetTopic"));
    }

    @Test
    @DisplayName("a failure that is not encryption (for example Kafka) is not parked: same as before")
    void otherFailureIsNotParked() {
        when(encClient.decrypt(CIPHER)).thenReturn("{\"individuals\":[]}");
        when(individualService.createFromQueue(any(IndividualBulkRequest.class)))
                .thenThrow(new CustomException("EVENT_BUS_FAILURE", "send failed"));

        consumer.bulkCreate(envelope(), CREATE_TOPIC);

        verify(producer, never()).push(anyString(), any());
    }

    @Test
    @DisplayName("an old plain message (no envelope) that hits an encryption failure is never parked as ENCRYPTED")
    void oldPlainMessageIsNeverParkedAsEncrypted() {
        when(individualService.createFromQueue(any(IndividualBulkRequest.class))).thenThrow(encDown());
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("individuals", List.of());

        consumer.bulkCreate(record, CREATE_TOPIC);

        verify(producer, never()).push(anyString(), any());
    }

    @Test
    @DisplayName("the DLQ listener takes one message per poll, so a replay that waits cannot run past max.poll.interval.ms")
    void dlqListenerTakesOneMessagePerPoll() throws Exception {
        KafkaListener listener = IndividualConsumer.class.getMethod("replayBulkDlq", Map.class).getAnnotation(KafkaListener.class);
        assertEquals(List.of("max.poll.records=1"), List.of(listener.properties()));
    }

    @Test
    @DisplayName("a bulk record without encryptedPayload (old app / pre-release) is still read as plain and processed")
    void oldPlainRecordStillProcessed() {
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("individuals", List.of());

        consumer.bulkCreate(record, CREATE_TOPIC);

        verify(individualService).createFromQueue(any(IndividualBulkRequest.class));
        verifyNoInteractions(encClient);
        verify(producer, never()).push(anyString(), any());
    }

    // ---------------------------------------------------------------- DLQ listener, full round trip

    @Test
    @DisplayName("round trip: park from the consumer, DLQ listener re-parks while enc-service is down (attempts 1, 2, still ENCRYPTED), then sends the unchanged envelope back")
    void encryptedRoundTrip() {
        when(encClient.decrypt(CIPHER)).thenThrow(encDown());
        Map<String, Object> record = envelope();
        consumer.bulkCreate(record, CREATE_TOPIC);
        Map<String, Object> message = parkedInDlq();

        for (int expected = 1; expected <= 2; expected++) {
            clearInvocations(producer);
            consumer.replayBulkDlq(message);
            message = parkedInDlq();
            assertEquals("ENCRYPTED", message.get("payloadType"));
            assertEquals(expected, message.get("attempts"));
            verify(producer, never()).push(eq(CREATE_TOPIC), any());
        }

        doReturn("{\"individuals\":[]}").when(encClient).decrypt(CIPHER); // enc-service is back
        clearInvocations(producer);
        consumer.replayBulkDlq(message);

        ArgumentCaptor<Object> sent = ArgumentCaptor.forClass(Object.class);
        verify(producer).push(eq(CREATE_TOPIC), sent.capture());
        assertEquals(CIPHER, ((Map<?, ?>) sent.getValue()).get("encryptedPayload"));
        assertEquals(3, ((Map<?, ?>) sent.getValue()).get(BulkEnvelopeService.REPLAY_ATTEMPTS));
        verify(producer, never()).push(eq(DLQ_TOPIC), any());
    }

    @Test
    @DisplayName("policy missing: open works, encrypt fails every time -> each round the count goes up and the next try waits")
    void failureAfterOpenDoesNotLoopFast() {
        when(encClient.decrypt(CIPHER)).thenReturn("{\"individuals\":[]}");
        when(individualService.createFromQueue(any(IndividualBulkRequest.class)))
                .thenThrow(new CustomException("INDIVIDUAL_ENCRYPTION_ERROR", "policy missing"));
        consumer.bulkCreate(envelope(), CREATE_TOPIC);
        Map<String, Object> parked = parkedInDlq();
        assertEquals(0, parked.get("attempts"));
        assertFalse(parked.containsKey("notBefore"), "a first failure is not delayed");

        clearInvocations(producer);
        consumer.replayBulkDlq(parked);  // trial open works, the envelope goes back with replayAttempts 1
        ArgumentCaptor<Object> sent = ArgumentCaptor.forClass(Object.class);
        verify(producer).push(eq(CREATE_TOPIC), sent.capture());
        @SuppressWarnings("unchecked")
        Map<String, Object> onward = (Map<String, Object>) sent.getValue();

        clearInvocations(producer);
        consumer.bulkCreate(onward, CREATE_TOPIC);  // fails again after the open
        Map<String, Object> again = parkedInDlq();
        assertEquals(1, again.get("attempts"));
        assertTrue(again.containsKey("notBefore"), "a repeated failure must wait before the next try");
    }

    @Test
    @DisplayName("DLQ listener with a PLAIN message seals it and sends the envelope to the target topic")
    void plainMessageIsSealedByListener() {
        when(encClient.encrypt(anyString())).thenReturn(CIPHER);
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("dlqVersion", 1);
        message.put("payloadType", "PLAIN");
        message.put("targetTopic", UPDATE_TOPIC);
        message.put("attempts", 0);
        message.put("payload", Map.of("individuals", List.of()));

        consumer.replayBulkDlq(message);

        verify(producer).push(eq(UPDATE_TOPIC), eq(envelope()));
    }

    @Test
    @DisplayName("DLQ listener with an unknown payloadType sends nothing")
    void unknownTypeSendsNothing() {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("dlqVersion", 1);
        message.put("payloadType", "SECRET");
        message.put("targetTopic", CREATE_TOPIC);
        message.put("attempts", 0);
        message.put("payload", envelope());

        consumer.replayBulkDlq(message);

        verifyNoInteractions(producer);
        verifyNoInteractions(encClient);
    }
}
