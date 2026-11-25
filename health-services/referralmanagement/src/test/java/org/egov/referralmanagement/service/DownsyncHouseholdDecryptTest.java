package org.egov.referralmanagement.service;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.ds.Tuple;
import org.egov.common.http.client.ServiceRequestClient;
import org.egov.common.models.household.Address;
import org.egov.common.models.household.Household;
import org.egov.common.models.referralmanagement.beneficiarydownsync.Downsync;
import org.egov.common.models.referralmanagement.beneficiarydownsync.DownsyncCriteria;
import org.egov.common.models.referralmanagement.beneficiarydownsync.DownsyncRequest;
import org.egov.common.utils.MultiStateInstanceUtil;
import org.egov.referralmanagement.config.ReferralManagementConfiguration;
import org.egov.referralmanagement.repository.HouseholdRepository;
import org.egov.tracer.model.CustomException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.postgresql.util.PGobject;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Downsync of households after address encryption at rest: the downsync file and the matview search answer decrypt
 * the address text values with one enc-service call per page, and only real cipher text is sent. enc-service
 * (RestTemplate), the JDBC cursor and the database are mocks.
 */
class DownsyncHouseholdDecryptTest {

    private static final String ENC_URL = "http://enc.test";
    private static final String DECRYPT_ENDPOINT = "/egov-enc-service/crypto/v1/_decrypt";
    private static final String DECRYPT_URL = ENC_URL + DECRYPT_ENDPOINT;

    private final ObjectMapper om = new ObjectMapper();
    private RestTemplate restTemplate;
    private DownsyncFileGenService service;

    /** Cipher-shaped text (keyId|base64, at least 16 bytes) that the fake enc-service turns back into the plain value. */
    private static String cipher(String plain) {
        return "228075|" + Base64.getEncoder().encodeToString(("################" + plain).getBytes(StandardCharsets.UTF_8));
    }

    private static String plainOf(String cipher) {
        String text = new String(Base64.getDecoder().decode(cipher.substring(cipher.indexOf('|') + 1)), StandardCharsets.UTF_8);
        return text.substring(16);
    }

    @BeforeEach
    void setUp() {
        restTemplate = mock(RestTemplate.class);
        // fake enc-service: array of cipher strings in, array of plain strings out
        when(restTemplate.postForObject(eq(DECRYPT_URL), any(), eq(JsonNode.class))).thenAnswer(call -> {
            ArrayNode in = call.getArgument(1);
            ArrayNode out = om.createArrayNode();
            in.forEach(n -> out.add(plainOf(n.asText())));
            return out;
        });
        ReferralManagementConfiguration config = mock(ReferralManagementConfiguration.class);
        when(config.getEncHost()).thenReturn(ENC_URL);
        when(config.getEncDecryptEndpoint()).thenReturn(DECRYPT_ENDPOINT);
        service = new DownsyncFileGenService();
        ReflectionTestUtils.setField(service, "config", config);
        ReflectionTestUtils.setField(service, "objectMapper", om);
        ReflectionTestUtils.setField(service, "restTemplate", restTemplate);
    }

    private ArrayNode sentPayload() {
        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(restTemplate).postForObject(eq(DECRYPT_URL), payload.capture(), eq(JsonNode.class));
        return (ArrayNode) payload.getValue();
    }

    // ------------------------------------------------------------------ matview search (Household objects)

    private static Household storedHousehold(String id) {
        return Household.builder().id(id).clientReferenceId("cr-" + id).tenantId("default")
                .address(Address.builder().id("a-" + id).tenantId("default")
                        .doorNo(cipher("12B")).addressLine1(cipher("line one")).addressLine2(cipher("line two"))
                        .landmark(cipher("near the well")).city(cipher("Maputo")).pincode(cipher("1100"))
                        .buildingName(cipher("Block C")).street(cipher("Main Road"))
                        .latitude(12.31).longitude(77.5).build())
                .build();
    }

    @Test
    @DisplayName("matview households: the 8 cipher address fields come back plain, with ONE enc-service call for the page")
    void matviewHouseholdsAreDecryptedWithOneCall() {
        List<Household> page = List.of(storedHousehold("h-1"), storedHousehold("h-2"), storedHousehold("h-3"));

        service.decryptHouseholdAddresses(page);

        for (Household h : page) {
            Address a = h.getAddress();
            assertEquals("12B", a.getDoorNo());
            assertEquals("line one", a.getAddressLine1());
            assertEquals("line two", a.getAddressLine2());
            assertEquals("near the well", a.getLandmark());
            assertEquals("Maputo", a.getCity());
            assertEquals("1100", a.getPincode());
            assertEquals("Block C", a.getBuildingName());
            assertEquals("Main Road", a.getStreet());
            assertEquals(12.31, a.getLatitude());
            assertEquals(77.5, a.getLongitude());
        }
        assertEquals(3 * 8, sentPayload().size());
    }

    @Test
    @DisplayName("matview households: only cipher text is sent; plain values and a missing address pass through")
    void onlyCipherTextIsSent() {
        Household mixed = Household.builder().id("h-1").address(Address.builder().id("a-1")
                .doorNo(cipher("12B")).addressLine1("plain line").city("").street(null).pincode("A|B").build()).build();
        Household noAddress = Household.builder().id("h-2").build();

        service.decryptHouseholdAddresses(List.of(mixed, noAddress));

        ArrayNode sent = sentPayload();
        assertEquals(1, sent.size());
        assertEquals(cipher("12B"), sent.get(0).asText());
        assertEquals("12B", mixed.getAddress().getDoorNo());
        assertEquals("plain line", mixed.getAddress().getAddressLine1());
        assertEquals("", mixed.getAddress().getCity());
        assertNull(mixed.getAddress().getStreet());
        assertEquals("A|B", mixed.getAddress().getPincode());
        assertNull(noAddress.getAddress());
    }

    @Test
    @DisplayName("matview households: rows not yet backfilled (all plain), empty or null list: enc-service is not called")
    void noCallWhenNothingIsEncrypted() {
        Household plain = Household.builder().id("h-1").address(Address.builder().id("a-1").doorNo("12B")
                .addressLine1("line one").build()).build();

        service.decryptHouseholdAddresses(List.of(plain));
        service.decryptHouseholdAddresses(List.of());
        service.decryptHouseholdAddresses(null);

        verifyNoInteractions(restTemplate);
        assertEquals("12B", plain.getAddress().getDoorNo());
    }

    @Test
    @DisplayName("an enc-service failure fails the page loudly (never returns cipher text as if it were plain)")
    void encServiceFailureIsAnError() {
        when(restTemplate.postForObject(eq(DECRYPT_URL), any(), eq(JsonNode.class)))
                .thenThrow(new ResourceAccessException("connect timed out"));

        CustomException e = assertThrows(CustomException.class,
                () -> service.decryptHouseholdAddresses(List.of(storedHousehold("h-1"))));

        assertEquals("ENC_SERVICE_DECRYPT_FAILED", e.getCode());
        assertTrue(e.getMessage().contains("household(s)"), e.getMessage());
    }

    @Test
    @DisplayName("an enc-service answer of the wrong size is an error, so values are never mapped to the wrong rows")
    void wrongSizeAnswerIsAnError() {
        when(restTemplate.postForObject(eq(DECRYPT_URL), any(), eq(JsonNode.class)))
                .thenReturn(om.createArrayNode().add("only one"));

        CustomException e = assertThrows(CustomException.class,
                () -> service.decryptHouseholdAddresses(List.of(storedHousehold("h-1"))));

        assertEquals("ENC_SERVICE_RESPONSE_SIZE_MISMATCH", e.getCode());
    }

    // ------------------------------------------------------------------ DownsyncService (egov.enable.matview.search)

    private Downsync matviewSearch(boolean matviewEnabled, List<Household> fromView, List<Household> fromHouseholdService)
            throws Exception {
        ReferralManagementConfiguration configs = mock(ReferralManagementConfiguration.class);
        when(configs.isEnableMatviewSearch()).thenReturn(matviewEnabled);
        when(configs.getHouseholdHost()).thenReturn("http://household.test");
        when(configs.getHouseholdSearchUrl()).thenReturn("/household/v1/_search");
        HouseholdRepository householdRepository = mock(HouseholdRepository.class);
        when(householdRepository.findByView(anyString(), any(), any(), anyString()))
                .thenReturn(new Tuple<>(2L, fromView));
        ServiceRequestClient restClient = mock(ServiceRequestClient.class);
        var response = org.egov.common.models.household.HouseholdBulkResponse.builder()
                .households(fromHouseholdService).totalCount(1L).build();
        when(restClient.fetchResult(any(StringBuilder.class), any(), eq(org.egov.common.models.household.HouseholdBulkResponse.class)))
                .thenReturn(response);
        DownsyncService downsyncService = new DownsyncService(restClient, configs, mock(NamedParameterJdbcTemplate.class),
                null, null, null, null, mock(MultiStateInstanceUtil.class), householdRepository, service);
        DownsyncCriteria criteria = DownsyncCriteria.builder().locality("LOC_1").tenantId("default").limit(50).offset(0).build();
        Downsync downsync = new Downsync();
        downsync.setDownsyncCriteria(criteria);
        DownsyncRequest request = DownsyncRequest.builder().requestInfo(RequestInfo.builder().build())
                .downsyncCriteria(criteria).build();
        ReflectionTestUtils.invokeMethod(downsyncService, "searchHouseholds", request, downsync);
        return downsync;
    }

    @Test
    @DisplayName("matview search: the households in the downsync answer carry plain address values")
    void matviewSearchAnswersPlainAddress() throws Exception {
        Downsync downsync = matviewSearch(true, List.of(storedHousehold("h-1"), storedHousehold("h-2")), null);

        assertEquals(2, downsync.getHouseholds().size());
        for (Household h : downsync.getHouseholds()) {
            assertEquals("12B", h.getAddress().getDoorNo());
            assertEquals("Main Road", h.getAddress().getStreet());
            assertEquals("Maputo", h.getAddress().getCity());
        }
        assertEquals(2L, downsync.getDownsyncCriteria().getTotalCount());
        verify(restTemplate, times(1)).postForObject(eq(DECRYPT_URL), any(), eq(JsonNode.class));
    }

    @Test
    @DisplayName("household-service search (matview off): the answer is already plain, no enc-service call here")
    void householdServiceSearchIsNotDecryptedAgain() throws Exception {
        Household plain = Household.builder().id("h-1").clientReferenceId("cr-1").address(Address.builder().id("a-1")
                .doorNo("12B").street("Main Road").build()).build();

        Downsync downsync = matviewSearch(false, List.of(), new ArrayList<>(List.of(plain)));

        assertEquals("12B", downsync.getHouseholds().get(0).getAddress().getDoorNo());
        verifyNoInteractions(restTemplate);
    }

    // ------------------------------------------------------------------ HH_MEMBERS file (HOUSEHOLD rows)

    private static Map<String, Object> mvRow(String id, boolean encrypted) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        row.put("clientReferenceId", "cr-" + id);
        row.put("tenantId", "default");
        row.put("numberOfMembers", 4);
        row.put("householdType", "FAMILY");
        row.put("rowVersion", 2);
        row.put("isDeleted", false);
        row.put("additionalDetails", jsonb("{\"fields\":[{\"key\":\"k\",\"value\":\"v\"}]}"));
        row.put("createdBy", "u-1");
        row.put("createdTime", 1700000000000L);
        row.put("lastModifiedBy", "u-2");
        row.put("lastModifiedTime", 1700000001000L);
        row.put("clientCreatedBy", "u-3");
        row.put("clientCreatedTime", 1700000002000L);
        row.put("clientLastModifiedBy", "u-4");
        row.put("clientLastModifiedTime", 1700000003000L);
        row.put("aid", "a-" + id);
        row.put("aclientreferenceid", "acr-" + id);
        row.put("atenantid", "default");
        row.put("doorNo", encrypted ? cipher("12B") : "12B");
        row.put("latitude", 12.31);
        row.put("longitude", 77.5);
        row.put("locationAccuracy", 5);
        row.put("type", "OTHER");
        row.put("addressLine1", encrypted ? cipher("line one") : "line one");
        row.put("addressLine2", null);
        row.put("landmark", encrypted ? cipher("near the well") : "near the well");
        row.put("city", encrypted ? cipher("Maputo") : "Maputo");
        row.put("pinCode", encrypted ? cipher("1100") : "1100");
        row.put("buildingName", "");
        row.put("street", encrypted ? cipher("Main Road") : "Main Road");
        row.put("localityCode", "LOC_1");
        return row;
    }

    private static PGobject jsonb(String value) {
        try {
            PGobject pg = new PGobject();
            pg.setType("jsonb");
            pg.setValue(value);
            return pg;
        } catch (java.sql.SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    /** A ResultSet that answers like the JDBC driver does for one row (getString / getLong / ... / wasNull). */
    private static ResultSet resultSetOf(Map<String, Object> row) {
        boolean[] lastWasNull = {false};
        return mock(ResultSet.class, invocation -> {
            String method = invocation.getMethod().getName();
            if (method.equals("wasNull")) return lastWasNull[0];
            if (invocation.getArguments().length != 1 || !(invocation.getArgument(0) instanceof String col)) return null;
            Object v = row.get(col);
            lastWasNull[0] = v == null;
            return switch (method) {
                case "getString" -> v == null ? null : v.toString();
                case "getObject" -> v;
                case "getLong" -> v == null ? 0L : ((Number) v).longValue();
                case "getInt" -> v == null ? 0 : ((Number) v).intValue();
                case "getDouble" -> v == null ? 0d : ((Number) v).doubleValue();
                case "getBoolean" -> v != null && (Boolean) v;
                default -> null;
            };
        });
    }

    private JsonNode written(java.util.function.Consumer<JsonGenerator> body) throws Exception {
        StringWriter out = new StringWriter();
        try (JsonGenerator gen = om.getFactory().createGenerator(out)) {
            gen.writeStartObject();
            body.accept(gen);
            gen.writeEndObject();
        }
        return om.readTree(out.toString());
    }

    @Test
    @DisplayName("the buffered household writer gives exactly the same JSON as the row-by-row writer it replaces (plain data)")
    void bufferedWriterMatchesTheResultSetWriter() throws Exception {
        for (Map<String, Object> row : List.of(mvRow("h-1", false), withoutAddress(mvRow("h-2", false)))) {
            JsonNode fromResultSet = written(gen -> ReflectionTestUtils.invokeMethod(service, "writeHousehold", gen, resultSetOf(row)));
            JsonNode fromMap = written(gen -> ReflectionTestUtils.invokeMethod(service, "writeHouseholdFromMap", gen, row));

            assertEquals(fromResultSet, fromMap);
        }
    }

    private static Map<String, Object> withoutAddress(Map<String, Object> row) {
        row.put("aid", null);
        return row;
    }

    @Test
    @DisplayName("HH_MEMBERS file: cipher address values are decrypted in the buffer (one call per page) before they are written")
    void houseHoldRowsAreDecryptedBeforeTheyAreWritten() throws Exception {
        List<Map<String, Object>> page = List.of(mvRow("h-1", true), mvRow("h-2", false), mvRow("h-3", true));

        ReflectionTestUtils.invokeMethod(service, "decryptHouseholdBatch", page);

        assertEquals(2 * 6, sentPayload().size());  // 6 cipher columns in each of the 2 encrypted rows
        JsonNode first = written(gen -> ReflectionTestUtils.invokeMethod(service, "writeHouseholdFromMap", gen, page.get(0)));
        assertEquals("12B", first.at("/address/doorNo").asText());
        assertEquals("line one", first.at("/address/addressLine1").asText());
        assertTrue(first.at("/address/addressLine2").isNull());
        assertEquals("near the well", first.at("/address/landmark").asText());
        assertEquals("Maputo", first.at("/address/city").asText());
        assertEquals("1100", first.at("/address/pincode").asText());
        assertEquals("", first.at("/address/buildingName").asText());
        assertEquals("Main Road", first.at("/address/street").asText());
        assertEquals(12.31, first.at("/address/latitude").asDouble());
        assertEquals(77.5, first.at("/address/longitude").asDouble());
        assertEquals("LOC_1", first.at("/address/locality/code").asText());
        // the plain row in the middle is untouched
        JsonNode second = written(gen -> ReflectionTestUtils.invokeMethod(service, "writeHouseholdFromMap", gen, page.get(1)));
        assertEquals("12B", second.at("/address/doorNo").asText());
        assertFalse(first.toString().contains("228075|"));
    }

    @Test
    @DisplayName("HH_MEMBERS file: a page of plain rows (not yet backfilled) makes no enc-service call")
    void plainHouseholdRowsMakeNoCall() {
        ReflectionTestUtils.invokeMethod(service, "decryptHouseholdBatch", List.of(mvRow("h-1", false)));

        verifyNoInteractions(restTemplate);
    }

    /** The whole streaming method: JDBC cursor (mock) -> buffer -> one decrypt call per page -> gzip NDJSON. */
    @Test
    @DisplayName("HH_MEMBERS file end to end (mock cursor): every HOUSEHOLD line is written with plain address values")
    void streamsDecryptedHouseholdLines() throws Exception {
        List<Map<String, Object>> rows = List.of(mvRow("h-1", true), mvRow("h-2", true), mvRow("h-3", false));
        NamedParameterJdbcTemplate named = mock(NamedParameterJdbcTemplate.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(named.getJdbcTemplate()).thenReturn(jdbc);
        when(jdbc.query(any(PreparedStatementCreator.class), any(ResultSetExtractor.class))).thenAnswer(call -> {
            ResultSetExtractor<?> extractor = call.getArgument(1);
            Iterator<Map<String, Object>> it = rows.iterator();
            Map<String, Object>[] current = new Map[1];
            ResultSet cursor = mock(ResultSet.class, invocation -> {
                if (invocation.getMethod().getName().equals("next")) {
                    if (!it.hasNext()) return false;
                    current[0] = it.next();
                    return true;
                }
                if (invocation.getMethod().getName().equals("getObject")) return current[0].get((String) invocation.getArgument(0));
                return null;
            });
            return extractor.extractData(cursor);
        });
        ReflectionTestUtils.setField(service, "namedJdbcTemplate", named);
        ReflectionTestUtils.setField(service, "readOnlyTx", new TransactionTemplate(mock(PlatformTransactionManager.class)));

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        long count;
        try (GZIPOutputStream gzip = new GZIPOutputStream(bytes)) {
            count = (Long) ReflectionTestUtils.invokeMethod(service, "streamHouseholdQuery", gzip,
                    "select * from household_address_mv where localitycode = :locality", Map.of("locality", "LOC_1"));
        }

        assertEquals(3, count);
        String ndjson = new String(new GZIPInputStream(new ByteArrayInputStream(bytes.toByteArray())).readAllBytes(),
                StandardCharsets.UTF_8);
        String[] lines = ndjson.split("\n");
        assertEquals(3, lines.length);
        for (String line : lines) {
            JsonNode node = om.readTree(line);
            assertEquals("HOUSEHOLD", node.get("_t").asText());
            assertEquals("12B", node.at("/address/doorNo").asText());
            assertEquals("Maputo", node.at("/address/city").asText());
            assertEquals("Main Road", node.at("/address/street").asText());
        }
        assertFalse(ndjson.contains("228075|"), "cipher text leaked into the downsync file");
        // 3 rows < one page of 1000: ONE enc-service call, with only the cipher values of the 2 encrypted rows
        assertEquals(2 * 6, sentPayload().size());
    }
}
