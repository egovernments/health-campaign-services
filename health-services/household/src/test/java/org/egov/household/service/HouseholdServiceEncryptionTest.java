package org.egov.household.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.exception.InvalidTenantIdException;
import org.egov.common.helper.RequestInfoTestBuilder;
import org.egov.common.models.core.SearchResponse;
import org.egov.common.models.household.Address;
import org.egov.common.models.household.Household;
import org.egov.common.models.household.HouseholdBulkRequest;
import org.egov.common.models.household.HouseholdRequest;
import org.egov.common.models.household.HouseholdSearch;
import org.egov.common.pii.CipherText;
import org.egov.common.pii.EncryptionDecryptionUtil;
import org.egov.common.service.IdGenService;
import org.egov.household.config.HouseholdConfiguration;
import org.egov.household.helper.FakeEncService;
import org.egov.household.repository.HouseholdRepository;
import org.egov.tracer.model.CustomException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The service saves the stored (encrypted) form, answers plain values, and lets the queue path rethrow enc failures. */
class HouseholdServiceEncryptionTest {

    private static final String CREATE_TOPIC = "save-household-topic";
    private static final String UPDATE_TOPIC = "update-household-topic";
    private static final String DELETE_TOPIC = "delete-household-topic";

    private final ObjectMapper mapper = new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .setTimeZone(TimeZone.getTimeZone("UTC"));
    private HouseholdRepository repository;
    private EncryptionDecryptionUtil encUtil;
    private HouseholdPiiCryptoService crypto;
    private HouseholdService service;
    private final RequestInfo requestInfo = RequestInfoTestBuilder.builder().withCompleteRequestInfo().build();

    @BeforeEach
    void setUp() {
        repository = mock(HouseholdRepository.class);
        HouseholdConfiguration configuration = new HouseholdConfiguration();
        configuration.setCreateTopic(CREATE_TOPIC);
        configuration.setUpdateTopic(UPDATE_TOPIC);
        configuration.setDeleteTopic(DELETE_TOPIC);
        configuration.setPiiEncryptModel("HouseholdPiiEncrypt");
        configuration.setPiiDecryptModel("HouseholdPiiDecrypt");
        encUtil = FakeEncService.encUtil(mapper);
        crypto = new HouseholdPiiCryptoService(encUtil, mapper, configuration);
        service = new HouseholdService(repository, mock(IdGenService.class), configuration, Collections.emptyList(),
                mock(HouseholdEnrichmentService.class), crypto);
    }

    private static Household plainHousehold(String id) {
        return Household.builder().id(id).clientReferenceId("cr-" + id).tenantId("default").memberCount(3).rowVersion(1)
                .isDeleted(false)
                .address(Address.builder().id("a-" + id).tenantId("default").doorNo("12B").addressLine1("line one")
                        .city("Maputo").pincode("1100").street("Main Road").latitude(1.5).longitude(2.5).build())
                .build();
    }

    private HouseholdBulkRequest bulkRequest(Household... households) {
        return HouseholdBulkRequest.builder().requestInfo(requestInfo).households(new ArrayList<>(List.of(households))).build();
    }

    private List<Household> savedTo(String topic) {
        ArgumentCaptor<List<Household>> saved = ArgumentCaptor.forClass(List.class);
        verify(repository).save(saved.capture(), eq(topic));
        return saved.getValue();
    }

    private static void assertStoredForm(Household stored) {
        Address a = stored.getAddress();
        assertTrue(CipherText.isCipher(a.getDoorNo()), "doorNo is not cipher: " + a.getDoorNo());
        assertTrue(CipherText.isCipher(a.getAddressLine1()));
        assertTrue(CipherText.isCipher(a.getCity()));
        assertTrue(CipherText.isCipher(a.getPincode()));
        assertTrue(CipherText.isCipher(a.getStreet()));
        assertEquals(1.5, a.getLatitude());
        assertEquals(2.5, a.getLongitude());
    }

    private static void assertPlain(Household household) {
        Address a = household.getAddress();
        assertEquals("12B", a.getDoorNo());
        assertEquals("line one", a.getAddressLine1());
        assertEquals("Maputo", a.getCity());
        assertEquals("1100", a.getPincode());
        assertEquals("Main Road", a.getStreet());
    }

    // ------------------------------------------------------------------ create / update / delete

    @Test
    @DisplayName("create: the stored copies go to the save topic (and so to Redis); the answer stays plain")
    void createSavesStoredCopiesAndAnswersPlain() {
        Household h = plainHousehold("h-1");

        List<Household> answer = service.create(bulkRequest(h), false);

        assertSame(h, answer.get(0));
        assertPlain(answer.get(0));
        List<Household> saved = savedTo(CREATE_TOPIC);
        assertEquals(1, saved.size());
        assertNotSame(h, saved.get(0));
        assertStoredForm(saved.get(0));
        assertEquals("h-1", saved.get(0).getId());
    }

    @Test
    @DisplayName("create (single API): same, the household is returned plain")
    void singleCreateAnswersPlain() {
        Household h = plainHousehold("h-1");

        Household answer = service.create(HouseholdRequest.builder().requestInfo(requestInfo).household(h).build());

        assertSame(h, answer);
        assertPlain(answer);
        assertStoredForm(savedTo(CREATE_TOPIC).get(0));
    }

    @Test
    @DisplayName("update: the stored copies go to the update topic; the answer stays plain")
    void updateSavesStoredCopiesAndAnswersPlain() {
        Household h = plainHousehold("h-1");

        List<Household> answer = service.update(bulkRequest(h), false);

        assertPlain(answer.get(0));
        assertStoredForm(savedTo(UPDATE_TOPIC).get(0));
    }

    @Test
    @DisplayName("delete: the stored copies go to the delete topic (the indexer reads it); the answer stays plain")
    void deleteSavesStoredCopiesAndAnswersPlain() {
        Household h = plainHousehold("h-1");
        h.setIsDeleted(true);

        List<Household> answer = service.delete(bulkRequest(h), false);

        assertPlain(answer.get(0));
        assertStoredForm(savedTo(DELETE_TOPIC).get(0));
    }

    @Test
    @DisplayName("update of a record whose address is already cipher text does not encrypt it twice")
    void updateDoesNotDoubleEncrypt() {
        Household h = plainHousehold("h-1");
        Household alreadyStored = crypto.toStoredForm(List.of(h)).get(0);
        org.mockito.Mockito.clearInvocations(encUtil);

        service.update(bulkRequest(alreadyStored), false);

        verify(encUtil, never()).encryptValues(anyList(), anyString());
        assertEquals(alreadyStored.getAddress().getDoorNo(), savedTo(UPDATE_TOPIC).get(0).getAddress().getDoorNo());
    }

    // ------------------------------------------------------------------ queue variants

    private void encServiceDown() {
        doThrow(new CustomException("INDIVIDUAL_ENC_SERVICE_UNAVAILABLE", "circuit open"))
                .when(encUtil).encryptValues(anyList(), anyString());
    }

    @Test
    @DisplayName("createFromQueue: an enc failure is rethrown (so the consumer parks the message) and nothing is saved")
    void createFromQueueRethrowsEncFailure() {
        encServiceDown();

        CustomException e = assertThrows(CustomException.class,
                () -> service.createFromQueue(bulkRequest(plainHousehold("h-1"))));

        assertEquals("INDIVIDUAL_ENC_SERVICE_UNAVAILABLE", e.getCode());
        verify(repository, never()).save(anyList(), anyString());
    }

    @Test
    @DisplayName("updateFromQueue: an enc failure is rethrown and nothing is saved")
    void updateFromQueueRethrowsEncFailure() {
        encServiceDown();

        CustomException e = assertThrows(CustomException.class,
                () -> service.updateFromQueue(bulkRequest(plainHousehold("h-1"))));

        assertEquals("INDIVIDUAL_ENC_SERVICE_UNAVAILABLE", e.getCode());
        verify(repository, never()).save(anyList(), anyString());
    }

    @Test
    @DisplayName("deleteFromQueue: an enc failure is rethrown and nothing is saved")
    void deleteFromQueueRethrowsEncFailure() {
        encServiceDown();
        Household h = plainHousehold("h-1");
        h.setIsDeleted(true);

        CustomException e = assertThrows(CustomException.class, () -> service.deleteFromQueue(bulkRequest(h)));

        assertEquals("INDIVIDUAL_ENC_SERVICE_UNAVAILABLE", e.getCode());
        verify(repository, never()).save(anyList(), anyString());
    }

    @Test
    @DisplayName("the queue variants save the stored copies when enc-service works")
    void queueVariantsSaveStoredCopies() {
        Household created = plainHousehold("h-1");
        Household updated = plainHousehold("h-2");

        service.createFromQueue(bulkRequest(created));
        service.updateFromQueue(bulkRequest(updated));

        assertStoredForm(savedTo(CREATE_TOPIC).get(0));
        assertStoredForm(savedTo(UPDATE_TOPIC).get(0));
        assertPlain(created);
        assertPlain(updated);
    }

    @Test
    @DisplayName("the API path (not from the queue) reports an enc failure as an error to the caller and saves nothing")
    void apiPathReportsEncFailureAsAnError() {
        encServiceDown();

        assertThrows(CustomException.class, () -> service.create(
                HouseholdRequest.builder().requestInfo(requestInfo).household(plainHousehold("h-1")).build()));

        verify(repository, never()).save(anyList(), anyString());
    }

    // ------------------------------------------------------------------ search

    private Household storedOf(String id) {
        return crypto.toStoredForm(List.of(plainHousehold(id))).get(0);
    }

    @Test
    @DisplayName("search by id: the stored cipher values in the answer are decrypted")
    void searchByIdDecrypts() throws InvalidTenantIdException {
        Household stored = storedOf("h-1");
        assertStoredForm(stored);
        when(repository.findById(anyString(), anyList(), eq("id"), anyBoolean()))
                .thenReturn(SearchResponse.<Household>builder().totalCount(1L).response(new ArrayList<>(List.of(stored))).build());

        SearchResponse<Household> response = service.search(HouseholdSearch.builder().id(List.of("h-1")).build(),
                10, 0, "default", null, false, requestInfo);

        assertEquals(1, response.getResponse().size());
        assertPlain(response.getResponse().get(0));
        assertEquals(1L, response.getTotalCount());
        verify(encUtil).decryptValueNodes(any(), eq("HouseholdPiiDecrypt"), eq(requestInfo), eq("searchHousehold"));
    }

    @Test
    @DisplayName("search (other criteria): the stored cipher values in the answer are decrypted")
    void searchDecrypts() throws Exception {
        Household stored = storedOf("h-1");
        when(repository.find(any(HouseholdSearch.class), anyInt(), anyInt(), anyString(), anyLong(), anyBoolean()))
                .thenReturn(SearchResponse.<Household>builder().totalCount(1L).response(new ArrayList<>(List.of(stored))).build());

        SearchResponse<Household> response = service.search(
                HouseholdSearch.builder().localityCode("LOC_1").build(), 10, 0, "default", 0L, false, requestInfo);

        assertPlain(response.getResponse().get(0));
    }

    @Test
    @DisplayName("search by radius: the stored cipher values in the answer are decrypted")
    void searchByRadiusDecrypts() throws Exception {
        Household stored = storedOf("h-1");
        when(repository.findByRadius(any(HouseholdSearch.class), anyInt(), anyInt(), anyString(), anyBoolean()))
                .thenReturn(SearchResponse.<Household>builder().totalCount(1L).response(new ArrayList<>(List.of(stored))).build());

        SearchResponse<Household> response = service.search(
                HouseholdSearch.builder().latitude(1.0).longitude(2.0).searchRadius(5.0).build(),
                10, 0, "default", null, false, requestInfo);

        assertPlain(response.getResponse().get(0));
    }

    @Test
    @DisplayName("search: plain rows (not yet backfilled) are returned as they are, without an enc call")
    void searchPassesPlainRowsThrough() throws Exception {
        Household plain = plainHousehold("h-1");
        when(repository.find(any(HouseholdSearch.class), anyInt(), anyInt(), anyString(), anyLong(), anyBoolean()))
                .thenReturn(SearchResponse.<Household>builder().totalCount(1L).response(new ArrayList<>(List.of(plain))).build());

        SearchResponse<Household> response = service.search(
                HouseholdSearch.builder().localityCode("LOC_1").build(), 10, 0, "default", 0L, false, requestInfo);

        assertPlain(response.getResponse().get(0));
        verify(encUtil, never()).decryptValueNodes(any(), anyString(), any(), anyString());
    }

    @Test
    @DisplayName("findById (internal callers: validators, member enrichment) keeps the stored form and does not call enc-service")
    void findByIdKeepsStoredForm() throws InvalidTenantIdException {
        Household stored = storedOf("h-1");
        when(repository.findById(anyString(), anyList(), eq("id"), anyBoolean()))
                .thenReturn(SearchResponse.<Household>builder().totalCount(1L).response(new ArrayList<>(List.of(stored))).build());

        SearchResponse<Household> response = service.findById("default", new ArrayList<>(List.of("h-1")), "id", false);

        assertSame(stored, response.getResponse().get(0));
        assertTrue(CipherText.isCipher(response.getResponse().get(0).getAddress().getDoorNo()));
        verify(encUtil, never()).decryptValueNodes(any(), anyString(), any(), anyString());
    }

    // ------------------------------------------------------------------ cache

    @Test
    @DisplayName("putInCache: the cache gets the stored form, never the plain request")
    void putInCacheGetsStoredForm() {
        Household h = plainHousehold("h-1");

        service.putInCache(List.of(h));

        ArgumentCaptor<List<Household>> cached = ArgumentCaptor.forClass(List.class);
        verify(repository).putInCache(cached.capture());
        assertStoredForm(cached.getValue().get(0));
        assertPlain(h);
    }

    @Test
    @DisplayName("putInCache: when enc-service cannot be reached the cache is skipped, no error, and nothing plain is cached")
    void putInCacheSkippedWhenEncFails() {
        encServiceDown();

        service.putInCache(List.of(plainHousehold("h-1")));

        verify(repository, never()).putInCache(anyList());
    }
}
