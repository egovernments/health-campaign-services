package org.egov.household.consumer;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.egov.common.helper.RequestInfoTestBuilder;
import org.egov.common.models.household.Address;
import org.egov.common.models.household.Household;
import org.egov.common.models.household.HouseholdBulkRequest;
import org.egov.common.pii.BulkEnvelopeService;
import org.egov.common.pii.EncCircuitBreaker;
import org.egov.common.pii.EnvelopeEncClient;
import org.egov.common.pii.PiiEncryptionProperties;
import org.egov.common.producer.Producer;
import org.egov.household.helper.FakeEncService;
import org.egov.household.service.HouseholdService;
import org.egov.tracer.model.CustomException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.annotation.KafkaListener;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The consumer opens the encrypted envelope (old plain messages still work), calls the queue variants of the service,
 * and parks the original message in the DLQ when encryption fails. The library's BulkEnvelopeService is the real class;
 * enc-service and Kafka are mocks.
 */
class HouseholdConsumerTest {

    private static final String CREATE_TOPIC = "create-household-bulk-topic";
    private static final String UPDATE_TOPIC = "update-household-bulk-topic";
    private static final String DELETE_TOPIC = "delete-household-bulk-topic";
    private static final String DLQ_TOPIC = "household-bulk-dlq-topic";

    private final ObjectMapper mapper = new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .setTimeZone(TimeZone.getTimeZone("UTC"));
    private HouseholdService householdService;
    private EnvelopeEncClient encClient;
    private Producer producer;
    private BulkEnvelopeService envelopeService;
    private HouseholdConsumer consumer;

    @BeforeEach
    void setUp() {
        householdService = mock(HouseholdService.class);
        encClient = mock(EnvelopeEncClient.class);
        when(encClient.encrypt(anyString())).thenAnswer(call -> FakeEncService.fakeCipher(call.getArgument(0)));
        when(encClient.decrypt(anyString())).thenAnswer(call -> FakeEncService.fakePlain(call.getArgument(0)));
        producer = mock(Producer.class);
        PiiEncryptionProperties properties = new PiiEncryptionProperties();
        properties.setBulkDlqTopic(DLQ_TOPIC);
        properties.setBulkEnvelopeDlqMode("ENCRYPTED_ONLY");
        properties.setBulkDlqRetryBackoffMs(5000L);
        properties.setBulkDlqRetryMaxBackoffMs(3600000L);
        properties.setEncCircuitFailureThreshold(5);
        properties.setEncCircuitOpenMs(30000L);
        envelopeService = new BulkEnvelopeService(encClient, mapper, producer, properties, new EncCircuitBreaker(properties));
        consumer = new HouseholdConsumer(householdService, envelopeService);
    }

    private HouseholdBulkRequest request() {
        Household h = Household.builder().id("h-1").clientReferenceId("cr-1").tenantId("default").memberCount(2).rowVersion(1)
                .isDeleted(false)
                .address(Address.builder().id("a-1").tenantId("default").doorNo("12B").street("Secret Street").build())
                .build();
        return HouseholdBulkRequest.builder()
                .requestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build())
                .households(new ArrayList<>(List.of(h))).build();
    }

    private Map<String, Object> envelopeOf(HouseholdBulkRequest request) {
        return envelopeService.seal(request);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> plainMessageOf(HouseholdBulkRequest request) {
        return mapper.convertValue(request, Map.class);
    }

    private static CustomException encFailure() {
        return new CustomException("INDIVIDUAL_ENC_SERVICE_UNAVAILABLE", "circuit open");
    }

    private HouseholdBulkRequest passedTo(java.util.function.Consumer<ArgumentCaptor<HouseholdBulkRequest>> verifier) {
        ArgumentCaptor<HouseholdBulkRequest> captor = ArgumentCaptor.forClass(HouseholdBulkRequest.class);
        verifier.accept(captor);
        return captor.getValue();
    }

    // ------------------------------------------------------------------ open the envelope

    @Test
    @DisplayName("create/update/delete: the envelope is opened and the plain request reaches the queue variant of the service")
    void envelopeIsOpenedForAllThreeOperations() {
        HouseholdBulkRequest original = request();

        consumer.bulkCreate(envelopeOf(original), CREATE_TOPIC);
        consumer.bulkUpdate(envelopeOf(original), UPDATE_TOPIC);
        consumer.bulkDelete(envelopeOf(original), DELETE_TOPIC);

        for (HouseholdBulkRequest opened : List.of(
                passedTo(c -> verify(householdService).createFromQueue(c.capture())),
                passedTo(c -> verify(householdService).updateFromQueue(c.capture())),
                passedTo(c -> verify(householdService).deleteFromQueue(c.capture())))) {
            assertEquals("h-1", opened.getHouseholds().get(0).getId());
            assertEquals("Secret Street", opened.getHouseholds().get(0).getAddress().getStreet());
            assertEquals("12B", opened.getHouseholds().get(0).getAddress().getDoorNo());
        }
        // the old (non-queue) methods are not used by the consumer any more
        verify(householdService, never()).create(any(HouseholdBulkRequest.class), anyBoolean());
        verify(householdService, never()).update(any(HouseholdBulkRequest.class), anyBoolean());
        verify(householdService, never()).delete(any(HouseholdBulkRequest.class), anyBoolean());
    }

    @Test
    @DisplayName("an old plain message (no envelope, sent before the rollout) is still accepted, without an enc call")
    void plainOldMessageIsStillAccepted() {
        consumer.bulkCreate(plainMessageOf(request()), CREATE_TOPIC);
        consumer.bulkUpdate(plainMessageOf(request()), UPDATE_TOPIC);
        consumer.bulkDelete(plainMessageOf(request()), DELETE_TOPIC);

        HouseholdBulkRequest created = passedTo(c -> verify(householdService).createFromQueue(c.capture()));
        assertEquals("Secret Street", created.getHouseholds().get(0).getAddress().getStreet());
        verify(householdService).updateFromQueue(any());
        verify(householdService).deleteFromQueue(any());
        verify(encClient, never()).decrypt(anyString());
    }

    // ------------------------------------------------------------------ enc failure: park

    @Test
    @DisplayName("an enc failure while saving a sealed message parks the ORIGINAL envelope in the DLQ (still encrypted)")
    void encFailureParksTheEnvelope() {
        Map<String, Object> envelope = envelopeOf(request());
        when(householdService.createFromQueue(any())).thenThrow(encFailure());

        List<Household> result = consumer.bulkCreate(envelope, CREATE_TOPIC);

        assertTrue(result.isEmpty());
        ArgumentCaptor<Object> parked = ArgumentCaptor.forClass(Object.class);
        verify(producer).push(eq(DLQ_TOPIC), parked.capture());
        Map<String, Object> message = (Map<String, Object>) parked.getValue();
        assertEquals("ENCRYPTED", message.get("payloadType"));
        assertEquals(CREATE_TOPIC, message.get("targetTopic"));
        assertEquals(0, message.get("attempts"));
        assertEquals(1, message.get("dlqVersion"));
        assertSame(envelope, message.get("payload"));
        assertTrue(mapperText(message).contains((String) envelope.get(BulkEnvelopeService.PAYLOAD)));
        assertFalse(mapperText(message).contains("Secret Street"));
    }

    @Test
    @DisplayName("update and delete park on an enc failure too, to their own target topic")
    void updateAndDeleteParkToo() {
        when(householdService.updateFromQueue(any())).thenThrow(encFailure());
        when(householdService.deleteFromQueue(any())).thenThrow(encFailure());

        consumer.bulkUpdate(envelopeOf(request()), UPDATE_TOPIC);
        consumer.bulkDelete(envelopeOf(request()), DELETE_TOPIC);

        ArgumentCaptor<Object> parked = ArgumentCaptor.forClass(Object.class);
        verify(producer, times(2)).push(eq(DLQ_TOPIC), parked.capture());
        assertEquals(UPDATE_TOPIC, ((Map<String, Object>) parked.getAllValues().get(0)).get("targetTopic"));
        assertEquals(DELETE_TOPIC, ((Map<String, Object>) parked.getAllValues().get(1)).get("targetTopic"));
    }

    @Test
    @DisplayName("enc-service down while OPENING the envelope: the message is parked and the service is not called")
    void encFailureWhileOpeningParksTheEnvelope() {
        Map<String, Object> envelope = envelopeOf(request());
        doThrow(new CustomException(EncCircuitBreaker.ENC_UNAVAILABLE, "down")).when(encClient).decrypt(anyString());

        consumer.bulkCreate(envelope, CREATE_TOPIC);

        verifyNoInteractions(householdService);
        ArgumentCaptor<Object> parked = ArgumentCaptor.forClass(Object.class);
        verify(producer).push(eq(DLQ_TOPIC), parked.capture());
        assertSame(envelope, ((Map<String, Object>) parked.getValue()).get("payload"));
    }

    @Test
    @DisplayName("an enc failure on an old plain message is NOT parked (an ENCRYPTED DLQ message must never hold plain data)")
    void plainMessageIsNeverParked() {
        when(householdService.createFromQueue(any())).thenThrow(encFailure());

        List<Household> result = consumer.bulkCreate(plainMessageOf(request()), CREATE_TOPIC);

        assertTrue(result.isEmpty());
        verify(producer, never()).push(anyString(), any());
    }

    @Test
    @DisplayName("an error that is not an enc failure is logged, not parked")
    void otherErrorsAreNotParked() {
        when(householdService.createFromQueue(any())).thenThrow(new IllegalStateException("boom"));
        when(householdService.updateFromQueue(any())).thenThrow(new CustomException("SOMETHING_ELSE", "bad"));

        assertEquals(Collections.emptyList(), consumer.bulkCreate(envelopeOf(request()), CREATE_TOPIC));
        assertEquals(Collections.emptyList(), consumer.bulkUpdate(envelopeOf(request()), UPDATE_TOPIC));

        verify(producer, never()).push(anyString(), any());
    }

    // ------------------------------------------------------------------ DLQ replay listener

    @Test
    @DisplayName("DLQ listener: a parked envelope goes back to its bulk topic, still sealed")
    void dlqListenerReplaysToTheBulkTopic() {
        Map<String, Object> envelope = envelopeOf(request());
        Map<String, Object> parked = new LinkedHashMap<>();
        parked.put("dlqVersion", 1);
        parked.put("payloadType", "ENCRYPTED");
        parked.put("targetTopic", CREATE_TOPIC);
        parked.put("attempts", 0);
        parked.put("payload", envelope);

        consumer.replayBulkDlq(parked);

        ArgumentCaptor<Object> sent = ArgumentCaptor.forClass(Object.class);
        verify(producer).push(eq(CREATE_TOPIC), sent.capture());
        Map<String, Object> onward = (Map<String, Object>) sent.getValue();
        assertEquals(envelope.get(BulkEnvelopeService.PAYLOAD), onward.get(BulkEnvelopeService.PAYLOAD));
        assertEquals(1, onward.get(BulkEnvelopeService.REPLAY_ATTEMPTS));
        verifyNoInteractions(householdService);
    }

    @Test
    @DisplayName("DLQ listener: topic key is egov.bulk.dlq.topic (mapped from household.bulk.dlq.topic), one message per poll")
    void dlqListenerTopicKey() throws Exception {
        Method listener = HouseholdConsumer.class.getMethod("replayBulkDlq", Map.class);
        KafkaListener annotation = listener.getAnnotation(KafkaListener.class);

        assertArrayEquals(new String[]{"${egov.bulk.dlq.topic}"}, annotation.topics());
        assertArrayEquals(new String[]{"max.poll.records=1"}, annotation.properties());
    }

    @Test
    @DisplayName("the bulk listeners still read the household.consumer.bulk.* topics")
    void bulkListenerTopicKeys() throws Exception {
        assertArrayEquals(new String[]{"${household.consumer.bulk.create.topic}"},
                listenerTopics("bulkCreate"));
        assertArrayEquals(new String[]{"${household.consumer.bulk.update.topic}"},
                listenerTopics("bulkUpdate"));
        assertArrayEquals(new String[]{"${household.consumer.bulk.delete.topic}"},
                listenerTopics("bulkDelete"));
    }

    private String[] listenerTopics(String method) throws Exception {
        return HouseholdConsumer.class.getMethod(method, Map.class, String.class)
                .getAnnotation(KafkaListener.class).topics();
    }

    private String mapperText(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
