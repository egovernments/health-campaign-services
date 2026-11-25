package org.egov.household.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.models.core.Boundary;
import org.egov.common.models.household.Address;
import org.egov.common.models.household.AddressType;
import org.egov.common.models.household.Household;
import org.egov.common.pii.CipherText;
import org.egov.common.pii.EncryptionDecryptionUtil;
import org.egov.household.config.HouseholdConfiguration;
import org.egov.household.helper.FakeEncService;
import org.egov.tracer.model.CustomException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Arrays;
import java.util.List;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HouseholdPiiCryptoServiceTest {

    private final ObjectMapper mapper = new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .setTimeZone(TimeZone.getTimeZone("UTC"));
    private EncryptionDecryptionUtil encUtil;
    private HouseholdPiiCryptoService service;

    @BeforeEach
    void setUp() {
        HouseholdConfiguration configuration = new HouseholdConfiguration();
        configuration.setPiiEncryptModel("HouseholdPiiEncrypt");
        configuration.setPiiDecryptModel("HouseholdPiiDecrypt");
        encUtil = FakeEncService.encUtil(mapper);
        service = new HouseholdPiiCryptoService(encUtil, mapper, configuration);
    }

    private static Address fullAddress() {
        return Address.builder().id("a-1").clientReferenceId("a-cr-1").tenantId("default")
                .doorNo("12B").addressLine1("line one").addressLine2("line two").landmark("near the well")
                .city("Maputo").pincode("1100").buildingName("Block C").street("Main Road")
                .latitude(12.31).longitude(77.5).locationAccuracy(5.0).type(AddressType.OTHER)
                .locality(Boundary.builder().code("LOC_1").build()).build();
    }

    private static Household household(String id, Address address) {
        return Household.builder().id(id).clientReferenceId("cr-" + id).tenantId("default").memberCount(4)
                .rowVersion(1).address(address).build();
    }

    // ------------------------------------------------------------------ toStoredForm

    @Test
    @DisplayName("stored form: the 8 address text fields are cipher text, everything else is as it was")
    void storedFormEncryptsTheEightTextFields() {
        Household source = household("h-1", fullAddress());

        Household stored = service.toStoredForm(List.of(source)).get(0);

        Address a = stored.getAddress();
        for (String value : Arrays.asList(a.getDoorNo(), a.getAddressLine1(), a.getAddressLine2(), a.getLandmark(),
                a.getCity(), a.getPincode(), a.getBuildingName(), a.getStreet())) {
            assertTrue(CipherText.isCipher(value), "not cipher text: " + value);
        }
        assertEquals("12B", FakeEncService.fakePlain(a.getDoorNo()));
        assertEquals("Main Road", FakeEncService.fakePlain(a.getStreet()));
        // not personal text: unchanged
        assertEquals(12.31, a.getLatitude());
        assertEquals(77.5, a.getLongitude());
        assertEquals(5.0, a.getLocationAccuracy());
        assertEquals(AddressType.OTHER, a.getType());
        assertEquals("a-1", a.getId());
        assertEquals("a-cr-1", a.getClientReferenceId());
        assertEquals("LOC_1", a.getLocality().getCode());
        assertEquals("h-1", stored.getId());
        assertEquals(4, stored.getMemberCount());
    }

    @Test
    @DisplayName("stored form is a copy: the request objects keep their plain values")
    void requestObjectsAreNotChanged() {
        Household source = household("h-1", fullAddress());

        Household stored = service.toStoredForm(List.of(source)).get(0);

        assertNotSame(source, stored);
        assertNotSame(source.getAddress(), stored.getAddress());
        assertEquals("12B", source.getAddress().getDoorNo());
        assertEquals("line one", source.getAddress().getAddressLine1());
        assertEquals("Main Road", source.getAddress().getStreet());
    }

    @Test
    @DisplayName("one enc call for the whole batch, with the encrypt model")
    void oneEncCallPerBatch() {
        List<Household> batch = List.of(household("h-1", fullAddress()), household("h-2", fullAddress()),
                household("h-3", fullAddress()));

        service.toStoredForm(batch);

        ArgumentCaptor<List<String>> plains = ArgumentCaptor.forClass(List.class);
        verify(encUtil, times(1)).encryptValues(plains.capture(), eq("HouseholdPiiEncrypt"));
        assertEquals(3 * 8, plains.getValue().size());
    }

    @Test
    @DisplayName("blank values are not sent and stay as they are; a household without address is fine")
    void blankValuesAndMissingAddressAreSkipped() {
        Address a = Address.builder().tenantId("default").doorNo("").addressLine1("   ").addressLine2(null)
                .city("Maputo").build();
        Household blankFields = household("h-1", a);
        Household noAddress = household("h-2", null);

        List<Household> stored = service.toStoredForm(List.of(blankFields, noAddress));

        ArgumentCaptor<List<String>> plains = ArgumentCaptor.forClass(List.class);
        verify(encUtil).encryptValues(plains.capture(), anyString());
        assertEquals(List.of("Maputo"), plains.getValue());
        Address s = stored.get(0).getAddress();
        assertEquals("", s.getDoorNo());
        assertEquals("   ", s.getAddressLine1());
        assertNull(s.getAddressLine2());
        assertTrue(CipherText.isCipher(s.getCity()));
        assertNull(stored.get(1).getAddress());
    }

    @Test
    @DisplayName("a value that is already cipher text is never encrypted again")
    void cipherValuesAreNotEncryptedTwice() {
        Household once = service.toStoredForm(List.of(household("h-1", fullAddress()))).get(0);
        org.mockito.Mockito.clearInvocations(encUtil);

        Household twice = service.toStoredForm(List.of(once)).get(0);

        verify(encUtil, never()).encryptValues(anyList(), anyString());
        assertEquals(once.getAddress().getDoorNo(), twice.getAddress().getDoorNo());
        assertEquals(once.getAddress().getStreet(), twice.getAddress().getStreet());
    }

    @Test
    @DisplayName("a mix of cipher and plain values: only the plain ones are encrypted")
    void mixedValuesEncryptOnlyThePlainOnes() {
        Address a = fullAddress();
        a.setCity(FakeEncService.fakeCipher("Beira"));

        Household stored = service.toStoredForm(List.of(household("h-1", a))).get(0);

        ArgumentCaptor<List<String>> plains = ArgumentCaptor.forClass(List.class);
        verify(encUtil).encryptValues(plains.capture(), anyString());
        assertEquals(7, plains.getValue().size());
        assertEquals(FakeEncService.fakeCipher("Beira"), stored.getAddress().getCity());
    }

    @Test
    @DisplayName("nothing to encrypt: enc-service is not called")
    void noEncCallWhenNothingToEncrypt() {
        service.toStoredForm(List.of(household("h-1", Address.builder().tenantId("default").build()), household("h-2", null)));
        service.toStoredForm(List.of());
        service.toStoredForm(null);

        verify(encUtil, never()).encryptValues(anyList(), anyString());
    }

    @Test
    @DisplayName("an enc failure is thrown, never swallowed (no plain value is returned as stored)")
    void encFailureIsThrown() {
        when(encUtil.encryptValues(anyList(), anyString()))
                .thenThrow(new CustomException("INDIVIDUAL_ENC_SERVICE_UNAVAILABLE", "down"));

        CustomException e = assertThrows(CustomException.class,
                () -> service.toStoredForm(List.of(household("h-1", fullAddress()))));

        assertEquals("INDIVIDUAL_ENC_SERVICE_UNAVAILABLE", e.getCode());
    }

    // ------------------------------------------------------------------ decrypt

    @Test
    @DisplayName("decrypt: cipher values come back plain, in place; the audit purpose and decrypt model are used")
    void decryptRestoresPlainValues() {
        Household stored = service.toStoredForm(List.of(household("h-1", fullAddress()))).get(0);
        RequestInfo requestInfo = RequestInfo.builder().build();

        List<Household> plain = service.decrypt(List.of(stored), requestInfo);

        Address a = plain.get(0).getAddress();
        assertEquals("12B", a.getDoorNo());
        assertEquals("line one", a.getAddressLine1());
        assertEquals("line two", a.getAddressLine2());
        assertEquals("near the well", a.getLandmark());
        assertEquals("Maputo", a.getCity());
        assertEquals("1100", a.getPincode());
        assertEquals("Block C", a.getBuildingName());
        assertEquals("Main Road", a.getStreet());
        assertEquals(12.31, a.getLatitude());
        verify(encUtil).decryptValueNodes(any(ArrayNode.class), eq("HouseholdPiiDecrypt"), eq(requestInfo), eq("searchHousehold"));
    }

    @Test
    @DisplayName("decrypt: only cipher values are sent; the owner is the household id, or the clientReferenceId when there is no id")
    void onlyCipherValuesAreSentWithTheOwnerId() {
        Address withId = Address.builder().tenantId("default").doorNo(FakeEncService.fakeCipher("1A"))
                .city("plain city").street(FakeEncService.fakeCipher("Elm St")).build();
        Address withoutId = Address.builder().tenantId("default").pincode(FakeEncService.fakeCipher("9999")).build();
        Household h1 = household("h-1", withId);
        Household h2 = Household.builder().clientReferenceId("cr-only").tenantId("default").address(withoutId).build();

        service.decrypt(List.of(h1, h2), RequestInfo.builder().build());

        ArgumentCaptor<ArrayNode> nodes = ArgumentCaptor.forClass(ArrayNode.class);
        verify(encUtil).decryptValueNodes(nodes.capture(), anyString(), any(), anyString());
        ArrayNode sent = nodes.getValue();
        assertEquals(3, sent.size());
        assertEquals("h-1", sent.get(0).get("ownerId").asText());
        assertEquals("h-1", sent.get(1).get("ownerId").asText());
        assertEquals("cr-only", sent.get(2).get("ownerId").asText());
        sent.forEach(n -> assertTrue(CipherText.isCipher(n.get("value").asText())));
        assertEquals("1A", h1.getAddress().getDoorNo());
        assertEquals("plain city", h1.getAddress().getCity());
        assertEquals("Elm St", h1.getAddress().getStreet());
        assertEquals("9999", h2.getAddress().getPincode());
    }

    @Test
    @DisplayName("decrypt: rows not yet backfilled (plain values) pass through and enc-service is not called")
    void plainValuesPassThrough() {
        Household plainRow = household("h-1", fullAddress());
        Household noAddress = household("h-2", null);

        List<Household> result = service.decrypt(List.of(plainRow, noAddress), RequestInfo.builder().build());

        verify(encUtil, never()).decryptValueNodes(any(), anyString(), any(), anyString());
        assertSame(plainRow, result.get(0));
        assertEquals("12B", result.get(0).getAddress().getDoorNo());
        assertEquals("Main Road", result.get(0).getAddress().getStreet());
        assertNull(result.get(1).getAddress());
    }

    @Test
    @DisplayName("decrypt: empty and null lists are returned without a call")
    void decryptEmptyList() {
        assertTrue(service.decrypt(List.of(), RequestInfo.builder().build()).isEmpty());
        assertTrue(service.decrypt(null, RequestInfo.builder().build()).isEmpty());
        verify(encUtil, never()).decryptValueNodes(any(), anyString(), any(), anyString());
    }

    @Test
    @DisplayName("decrypt: an enc failure is thrown to the caller")
    void decryptFailureIsThrown() {
        Household stored = service.toStoredForm(List.of(household("h-1", fullAddress()))).get(0);
        doThrow(new CustomException("INDIVIDUAL_DECRYPTION_ERROR", "failed"))
                .when(encUtil).decryptValueNodes(any(), anyString(), any(), anyString());

        assertThrows(CustomException.class, () -> service.decrypt(List.of(stored), RequestInfo.builder().build()));
    }
}
