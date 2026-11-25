package org.egov.household.web.controllers;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.egov.common.helper.RequestInfoTestBuilder;
import org.egov.common.models.core.SearchResponse;
import org.egov.common.models.core.URLParams;
import org.egov.common.models.household.Address;
import org.egov.common.models.household.Household;
import org.egov.common.models.household.HouseholdBulkRequest;
import org.egov.common.models.household.HouseholdSearch;
import org.egov.common.models.household.HouseholdSearchRequest;
import org.egov.common.pii.BulkEnvelopeService;
import org.egov.common.pii.CipherText;
import org.egov.common.pii.EncCircuitBreaker;
import org.egov.common.pii.EncryptionDecryptionUtil;
import org.egov.common.pii.EnvelopeEncClient;
import org.egov.common.pii.PiiEncryptionProperties;
import org.egov.common.producer.Producer;
import org.egov.household.config.HouseholdConfiguration;
import org.egov.household.config.HouseholdMemberConfiguration;
import org.egov.household.helper.FakeEncService;
import org.egov.household.repository.HouseholdRepository;
import org.egov.household.service.HouseholdEnrichmentService;
import org.egov.household.service.HouseholdMemberService;
import org.egov.household.service.HouseholdPiiCryptoService;
import org.egov.household.service.HouseholdService;
import org.egov.tracer.model.CustomException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Bulk create/update/delete: the request is sealed into an encrypted envelope before it reaches Kafka, the cache gets
 * the stored form, and 503 + Retry-After is the answer while enc-service is unavailable. The controller, the service,
 * the crypto service and the library's BulkEnvelopeService are the real classes; enc-service, Kafka and the database
 * are mocks.
 */
class HouseholdApiControllerBulkTest {

    private static final String CREATE_TOPIC = "create-household-bulk-topic";
    private static final String UPDATE_TOPIC = "update-household-bulk-topic";
    private static final String DELETE_TOPIC = "delete-household-bulk-topic";
    private static final String DLQ_TOPIC = "household-bulk-dlq-topic";

    private final ObjectMapper mapper = new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .setTimeZone(TimeZone.getTimeZone("UTC"));
    private HouseholdRepository repository;
    private EncryptionDecryptionUtil encUtil;
    private EnvelopeEncClient envelopeEncClient;
    private Producer producer;
    private Producer envelopeProducer;
    private HouseholdApiController controller;

    @BeforeEach
    void setUp() {
        HouseholdConfiguration configuration = new HouseholdConfiguration();
        configuration.setConsumerCreateTopic(CREATE_TOPIC);
        configuration.setConsumerUpdateTopic(UPDATE_TOPIC);
        configuration.setConsumerDeleteTopic(DELETE_TOPIC);
        configuration.setPiiEncryptModel("HouseholdPiiEncrypt");
        configuration.setPiiDecryptModel("HouseholdPiiDecrypt");
        configuration.setEncCircuitOpenMs(30000L);

        repository = mock(HouseholdRepository.class);
        encUtil = FakeEncService.encUtil(mapper);
        HouseholdPiiCryptoService crypto = new HouseholdPiiCryptoService(encUtil, mapper, configuration);
        HouseholdService householdService = new HouseholdService(repository, null, configuration, Collections.emptyList(),
                mock(HouseholdEnrichmentService.class), crypto);

        PiiEncryptionProperties properties = new PiiEncryptionProperties();
        properties.setBulkDlqTopic(DLQ_TOPIC);
        properties.setBulkEnvelopeDlqMode("ENCRYPTED_ONLY");
        properties.setEncCircuitFailureThreshold(5);
        properties.setEncCircuitOpenMs(30000L);
        envelopeEncClient = mock(EnvelopeEncClient.class);
        when(envelopeEncClient.encrypt(anyString())).thenAnswer(call -> FakeEncService.fakeCipher(call.getArgument(0)));
        envelopeProducer = mock(Producer.class);
        BulkEnvelopeService envelopeService = new BulkEnvelopeService(envelopeEncClient, mapper, envelopeProducer,
                properties, new EncCircuitBreaker(properties));

        HttpServletRequest servletRequest = mock(HttpServletRequest.class);
        when(servletRequest.getRequestURI()).thenReturn("/household/v1/bulk/_create");
        producer = mock(Producer.class);
        controller = new HouseholdApiController(mapper, servletRequest, householdService, mock(HouseholdMemberService.class),
                producer, mock(HouseholdMemberConfiguration.class), configuration, envelopeService);
    }

    private HouseholdBulkRequest request() {
        Household h = Household.builder().id("h-1").clientReferenceId("cr-1").tenantId("default").memberCount(2).rowVersion(1)
                .isDeleted(false)
                .address(Address.builder().id("a-1").tenantId("default").doorNo("12B").addressLine1("line one")
                        .city("Maputo").street("Secret Street").latitude(1.5).longitude(2.5).build())
                .build();
        return HouseholdBulkRequest.builder()
                .requestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build())
                .households(new ArrayList<>(List.of(h))).build();
    }

    private void encServiceDown() {
        when(envelopeEncClient.encrypt(anyString()))
                .thenThrow(new CustomException(EncCircuitBreaker.ENC_UNAVAILABLE, "circuit open"));
    }

    // ------------------------------------------------------------------ happy path

    @Test
    @DisplayName("bulk create: stored form cached first, then the sealed envelope goes to the bulk topic; answer 202")
    void bulkCreateCachesStoredFormThenPublishesEnvelope() throws Exception {
        HouseholdBulkRequest request = request();

        ResponseEntity<?> response = controller.householdV1CreatePost(request, true);

        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
        // cache: stored form (cipher), and it happens before the publish
        ArgumentCaptor<List<Household>> cached = ArgumentCaptor.forClass(List.class);
        InOrder order = inOrder(repository, envelopeEncClient, envelopeProducer);
        order.verify(repository).putInCache(cached.capture());
        order.verify(envelopeProducer).push(eq(CREATE_TOPIC), any(Object.class));
        Address inCache = cached.getValue().get(0).getAddress();
        assertTrue(CipherText.isCipher(inCache.getDoorNo()));
        assertTrue(CipherText.isCipher(inCache.getStreet()));
        // the request object itself is untouched
        assertEquals("Secret Street", request.getHouseholds().get(0).getAddress().getStreet());
    }

    @Test
    @DisplayName("bulk create: what reaches the bulk topic is the envelope only; no plain household or address is on it")
    void bulkTopicGetsOnlyTheEnvelope() throws Exception {
        controller.householdV1CreatePost(request(), true);

        ArgumentCaptor<Object> pushed = ArgumentCaptor.forClass(Object.class);
        verify(envelopeProducer).push(eq(CREATE_TOPIC), pushed.capture());
        Map<String, Object> envelope = (Map<String, Object>) pushed.getValue();
        assertEquals(1, envelope.get(BulkEnvelopeService.VERSION));
        assertEquals(2, envelope.size());
        String cipher = (String) envelope.get(BulkEnvelopeService.PAYLOAD);
        assertTrue(CipherText.isCipher(cipher));
        assertFalse(mapper.writeValueAsString(envelope).contains("Secret Street"));
        // opened again, it is the full plain request
        JsonNode opened = mapper.readTree(FakeEncService.fakePlain(cipher));
        assertEquals("Secret Street", opened.get("Households").get(0).get("address").get("street").asText());
        // the controller never pushes the household request to Kafka itself
        verify(producer, never()).push(anyString(), any());
    }

    @Test
    @DisplayName("bulk update and bulk delete: envelope to their own topic; nothing plain on any topic")
    void bulkUpdateAndDeletePublishEnvelopes() throws Exception {
        assertEquals(HttpStatus.ACCEPTED, controller.householdV1BulkUpdatePost(request(), true).getStatusCode());
        assertEquals(HttpStatus.ACCEPTED, controller.householdV1DeletePost(request(), true).getStatusCode());

        ArgumentCaptor<Object> updatePushed = ArgumentCaptor.forClass(Object.class);
        ArgumentCaptor<Object> deletePushed = ArgumentCaptor.forClass(Object.class);
        verify(envelopeProducer).push(eq(UPDATE_TOPIC), updatePushed.capture());
        verify(envelopeProducer).push(eq(DELETE_TOPIC), deletePushed.capture());
        assertNotNull(((Map<String, Object>) updatePushed.getValue()).get(BulkEnvelopeService.PAYLOAD));
        assertNotNull(((Map<String, Object>) deletePushed.getValue()).get(BulkEnvelopeService.PAYLOAD));
        verify(producer, never()).push(anyString(), any());
        // only bulk create caches (as before)
        verify(repository, never()).putInCache(anyList());
    }

    // ------------------------------------------------------------------ enc-service unavailable

    private void assertServiceUnavailable(ResponseEntity<?> response) {
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertEquals("30", response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER));
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        List<Map<String, Object>> errors = (List<Map<String, Object>>) body.get("Errors");
        assertEquals(EncCircuitBreaker.ENC_UNAVAILABLE, errors.get(0).get("code"));
        assertNotNull(body.get("ResponseInfo"));
    }

    @Test
    @DisplayName("bulk create while enc-service is down: 503 + Retry-After, nothing on the bulk topic, nothing plain cached")
    void bulkCreateAnswers503WhenEncServiceIsDown() {
        encServiceDown();

        ResponseEntity<?> response = controller.householdV1CreatePost(request(), true);

        assertServiceUnavailable(response);
        verify(envelopeProducer, never()).push(anyString(), any());
        verify(producer, never()).push(anyString(), any());
    }

    @Test
    @DisplayName("bulk create while enc-service is down: the cache is skipped (best effort), the request is not")
    void bulkCreateCacheIsBestEffort() {
        // the crypto service (cache path) cannot reach enc-service either
        doThrow(new CustomException(EncCircuitBreaker.ENC_UNAVAILABLE, "circuit open"))
                .when(encUtil).encryptValues(anyList(), anyString());
        encServiceDown();

        ResponseEntity<?> response = controller.householdV1CreatePost(request(), true);

        assertServiceUnavailable(response);
        verify(repository, never()).putInCache(anyList());
    }

    @Test
    @DisplayName("bulk update and delete while enc-service is down: 503 + Retry-After and nothing on any topic")
    void bulkUpdateAndDeleteAnswer503() {
        encServiceDown();

        assertServiceUnavailable(controller.householdV1BulkUpdatePost(request(), true));
        assertServiceUnavailable(controller.householdV1DeletePost(request(), true));

        verify(envelopeProducer, never()).push(anyString(), any());
        verify(producer, never()).push(anyString(), any());
    }

    @Test
    @DisplayName("an error that is not 'enc-service unavailable' is not turned into a 503")
    void otherErrorsAreRethrown() {
        when(envelopeEncClient.encrypt(anyString())).thenThrow(new CustomException("SOMETHING_ELSE", "bad"));

        CustomException e = assertThrows(CustomException.class, () -> controller.householdV1CreatePost(request(), true));

        assertEquals("SOMETHING_ELSE", e.getCode());
    }

    // ------------------------------------------------------------------ search

    @Test
    @DisplayName("search: the caller's RequestInfo reaches the service (decrypt policy check) and the answer is plain")
    void searchAnswersPlainValues() throws Exception {
        Household stored = new HouseholdPiiCryptoService(encUtil, mapper, configurationWithModels())
                .toStoredForm(List.of(request().getHouseholds().get(0))).get(0);
        when(repository.find(any(HouseholdSearch.class), anyInt(), anyInt(), anyString(), any(), anyBoolean()))
                .thenReturn(SearchResponse.<Household>builder().totalCount(1L).response(new ArrayList<>(List.of(stored))).build());
        HouseholdSearchRequest searchRequest = HouseholdSearchRequest.builder()
                .requestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build())
                .household(HouseholdSearch.builder().localityCode("LOC_1").build()).build();
        URLParams urlParams = URLParams.builder().limit(10).offset(0).tenantId("default").includeDeleted(false).build();

        var response = controller.householdV1SearchPost(urlParams, searchRequest);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Secret Street", response.getBody().getHouseholds().get(0).getAddress().getStreet());
        assertEquals("12B", response.getBody().getHouseholds().get(0).getAddress().getDoorNo());
        verify(encUtil).decryptValueNodes(any(), eq("HouseholdPiiDecrypt"), eq(searchRequest.getRequestInfo()), eq("searchHousehold"));
    }

    private HouseholdConfiguration configurationWithModels() {
        HouseholdConfiguration c = new HouseholdConfiguration();
        c.setPiiEncryptModel("HouseholdPiiEncrypt");
        c.setPiiDecryptModel("HouseholdPiiDecrypt");
        return c;
    }
}
