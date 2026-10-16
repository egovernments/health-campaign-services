package org.egov.id.service;

import org.egov.common.contract.request.RequestInfo;
import org.egov.common.contract.request.User;
import org.egov.common.models.idgen.*;
import org.egov.id.config.PropertiesManager;
import org.egov.id.model.IdPoolConfig;
import org.egov.id.producer.IdGenProducer;
import org.egov.tracer.model.CustomException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdGenerationServiceTest {

    @InjectMocks
    private IdGenerationService idGenerationService;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private MdmsService mdmsService;

    @Mock
    private IdGenProducer idGenProducer;

    @Mock
    private PropertiesManager propertiesManager;

    @ParameterizedTest
    @MethodSource("invalidRequests")
    void testGenerateIdResponseWithInvalidInput(IdGenerationRequest request) {
        assertThrows(CustomException.class, () -> idGenerationService.generateIdResponse(request));
    }

    static Stream<IdGenerationRequest> invalidRequests() {
        return Stream.of(
                // Case 1: Null RequestInfo, empty IdRequest
                request(Collections.singletonList(new IdRequest()), null),

                // Case 2: Valid fields, but throws due to format or internal logic
                request(Collections.singletonList(buildRequest("Id Name", "42", "\\[(.*?)\\]", 3)), new RequestInfo()),

                // Case 3: Null tenant ID
                request(Collections.singletonList(buildRequest("Id Name", null, "\\[(.*?)\\]", 3)), new RequestInfo()),

                // Case 4: Null count
                request(Collections.singletonList(buildRequest("Id Name", "42", "\\[(.*?)\\]", null)), new RequestInfo()),

                // Case 5: Null ID name
                request(Collections.singletonList(buildRequest(null, "42", "\\[(.*?)\\]", 3)), new RequestInfo())
        );
    }

    private static IdRequest buildRequest(String idName, String tenantId, String format, Integer count) {
        IdRequest idRequest = new IdRequest(idName, tenantId, format, count);
        idRequest.setFormat(format);
        return idRequest;
    }

    private static IdGenerationRequest request(List<IdRequest> idRequests, RequestInfo requestInfo) {
        IdGenerationRequest req = new IdGenerationRequest();
        req.setIdRequests(idRequests);
        req.setRequestInfo(requestInfo);
        return req;
    }

    @Test
    void zeroPadsSequenceToConfiguredDefaultWidth() {
        ReflectionTestUtils.setField(idGenerationService, "defaultPaddingLength", 12);
        // NEXTVAL result the [seq...] token resolves to; format is preset so no MDMS/DB format lookup runs.
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class), eq(String.class)))
                .thenReturn(Collections.singletonList("42"));

        IdGenerationRequest request = request(
                Collections.singletonList(buildRequest(null, "ch", "[SEQ_EG]", 1)), new RequestInfo());

        IdGenerationResponse response = assertDoesNotThrow(() -> idGenerationService.generateIdResponse(request));

        assertEquals("000000000042", response.getIdResponses().get(0).getId());
    }

    @Test
    void emitsUnpaddedSequenceWhenPaddingLengthNonPositive() {
        // A non-positive width must not produce the invalid "%00d" spec that throws at runtime; it
        // simply yields the raw sequence number.
        ReflectionTestUtils.setField(idGenerationService, "defaultPaddingLength", 0);
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class), eq(String.class)))
                .thenReturn(Collections.singletonList("42"));

        IdGenerationRequest request = request(
                Collections.singletonList(buildRequest(null, "ch", "[SEQ_EG]", 1)), new RequestInfo());

        IdGenerationResponse response = assertDoesNotThrow(() -> idGenerationService.generateIdResponse(request));

        assertEquals("42", response.getIdResponses().get(0).getId());
    }

    @Test
    @SuppressWarnings("unchecked")
    void asyncPoolPathUsesTenantPaddingWidthNotSingletonDefault() {
        // Core guarantee of the Issue #1 fix: the async pool path must pad with the tenant's own
        // IdPoolConfig width, never with the shared-singleton default. Set the singleton default to a
        // DIFFERENT width (12) so that observing width 6 in the output proves the per-tenant value was
        // threaded through rather than read from shared state.
        ReflectionTestUtils.setField(idGenerationService, "defaultPaddingLength", 12);
        ReflectionTestUtils.setField(idGenerationService, "MAX_RECORDS_PER_PERSIST_BATCH", 1000);
        ReflectionTestUtils.setField(idGenerationService, "idFormatFromMDMS", false);

        String tenantId = "ch";
        IdPoolConfig tenantPoolConfig = IdPoolConfig.builder().seqCode("SEQ_TEST").paddingLength(6).build();
        when(mdmsService.getIdPoolConfig(any(), eq(tenantId))).thenReturn(Optional.of(tenantPoolConfig));
        // idFormatFromMDMS=false, so the format is resolved from the DB.
        when(jdbcTemplate.queryForObject(anyString(), eq(String.class), any(), any())).thenReturn("[SEQ_TEST]");
        // NEXTVAL block the [SEQ_TEST] token resolves to.
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class), eq(String.class)))
                .thenReturn(Collections.singletonList("42"));
        when(propertiesManager.getSaveIdPoolTopic()).thenReturn("save-in-id-pool");

        IDPoolGenerationKafkaRequest request = IDPoolGenerationKafkaRequest.builder()
                .tenantId(tenantId)
                .chunkSize(1)
                .requestInfo(requestInfoWithUser())
                .build();

        idGenerationService.handleAsyncIdPoolRequest(request);

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(idGenProducer).push(eq("save-in-id-pool"), payloadCaptor.capture());

        Map<String, Object> payload = (Map<String, Object>) payloadCaptor.getValue();
        List<IdRecord> records = (List<IdRecord>) payload.get("idPool");
        assertEquals(1, records.size());
        // Width 6 (from the tenant IdPoolConfig), NOT 12 (the singleton default).
        assertEquals("000042", records.get(0).getId());
    }

    @Test
    @SuppressWarnings("unchecked")
    void asyncPoolFallsBackToDefaultConfigWhenMdmsHasNoPoolConfig() {
        // When MDMS returns no IdPoolConfig for the tenant, handleAsyncIdPoolRequest must fall back to
        // propertiesManager.getDefaultIdPoolConfig() (see the orElseGet in the production path) and generate
        // using that configuration. Set the singleton default to a DIFFERENT width (12) so observing width 8
        // in the output proves the fallback IdPoolConfig's own width was threaded through.
        ReflectionTestUtils.setField(idGenerationService, "defaultPaddingLength", 12);
        ReflectionTestUtils.setField(idGenerationService, "MAX_RECORDS_PER_PERSIST_BATCH", 1000);
        ReflectionTestUtils.setField(idGenerationService, "idFormatFromMDMS", false);

        String tenantId = "ch";
        when(mdmsService.getIdPoolConfig(any(), eq(tenantId))).thenReturn(Optional.empty());
        IdPoolConfig fallbackConfig = IdPoolConfig.builder().seqCode("SEQ_DEFAULT").paddingLength(8).build();
        when(propertiesManager.getDefaultIdPoolConfig()).thenReturn(fallbackConfig);
        // idFormatFromMDMS=false, so the format is resolved from the DB using the fallback seqCode.
        when(jdbcTemplate.queryForObject(anyString(), eq(String.class), any(), any())).thenReturn("[SEQ_DEFAULT]");
        // NEXTVAL block the [SEQ_DEFAULT] token resolves to.
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class), eq(String.class)))
                .thenReturn(Collections.singletonList("7"));
        when(propertiesManager.getSaveIdPoolTopic()).thenReturn("save-in-id-pool");

        IDPoolGenerationKafkaRequest request = IDPoolGenerationKafkaRequest.builder()
                .tenantId(tenantId)
                .chunkSize(1)
                .requestInfo(requestInfoWithUser())
                .build();

        idGenerationService.handleAsyncIdPoolRequest(request);

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(idGenProducer).push(eq("save-in-id-pool"), payloadCaptor.capture());

        Map<String, Object> payload = (Map<String, Object>) payloadCaptor.getValue();
        List<IdRecord> records = (List<IdRecord>) payload.get("idPool");
        assertEquals(1, records.size());
        // Width 8 (from the fallback default IdPoolConfig), NOT 12 (the singleton default padding).
        assertEquals("00000007", records.get(0).getId());
    }

    @Test
    void asyncPoolAbortsWithoutPublishingWhenSeqCodeMissing() {
        // Issue #5: a blank seqCode must be detected as a configuration error and abort generation, rather
        // than silently producing IDs from an unintended sequence. fetchIdFormat throws CONFIGURATION_ERROR;
        // the async handler swallows that non-retryable error, so the observable guarantee is that NO
        // sequence is queried and NOTHING is published.
        ReflectionTestUtils.setField(idGenerationService, "MAX_RECORDS_PER_PERSIST_BATCH", 1000);

        String tenantId = "ch";
        IdPoolConfig misconfigured = IdPoolConfig.builder().seqCode("").paddingLength(12).build();
        when(mdmsService.getIdPoolConfig(any(), eq(tenantId))).thenReturn(Optional.of(misconfigured));

        IDPoolGenerationKafkaRequest request = IDPoolGenerationKafkaRequest.builder()
                .tenantId(tenantId)
                .chunkSize(1)
                .requestInfo(requestInfoWithUser())
                .build();

        assertDoesNotThrow(() -> idGenerationService.handleAsyncIdPoolRequest(request));

        // seqCode was blank, so generation aborts before any DB lookup or Kafka publish.
        verifyNoInteractions(jdbcTemplate);
        verifyNoInteractions(idGenProducer);
    }

    private static RequestInfo requestInfoWithUser() {
        RequestInfo requestInfo = new RequestInfo();
        requestInfo.setUserInfo(User.builder().uuid("test-uuid").build());
        return requestInfo;
    }

}

