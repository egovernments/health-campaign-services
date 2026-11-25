package org.egov.individual.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.egov.common.models.core.AdditionalFields;
import org.egov.common.models.core.Field;
import org.egov.common.models.individual.Address;
import org.egov.common.models.individual.Gender;
import org.egov.common.models.individual.Identifier;
import org.egov.common.models.individual.Individual;
import org.egov.common.models.individual.Name;
import org.egov.common.models.individual.UserDetails;
import org.egov.individual.config.IndividualProperties;
import org.egov.common.pii.CipherText;
import org.egov.common.pii.EncryptionDecryptionUtil;
import org.egov.common.pii.PiiEncryptionProperties;
import org.egov.common.pii.PiiHasher;
import org.egov.individual.web.models.StoredIdentifier;
import org.egov.individual.web.models.StoredIndividual;
import org.egov.tracer.model.CustomException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IndividualPiiCryptoServiceTest {

    private static final String KEY_ID = "228075";
    private final EncryptionDecryptionUtil encUtil = mock(EncryptionDecryptionUtil.class);
    private final ObjectMapper mapper = new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .setTimeZone(TimeZone.getTimeZone("UTC"))
            .registerModule(new JavaTimeModule());
    private PiiHasher hasher;
    private IndividualPiiCryptoService service;

    /** Fake enc-service: a cipher-shaped text that can be turned back into the plain value. */
    private static String fakeCipher(String plain) {
        return KEY_ID + "|" + Base64.getEncoder().encodeToString(("################" + plain).getBytes(StandardCharsets.UTF_8));
    }

    private static String fakePlain(String cipher) {
        String text = new String(Base64.getDecoder().decode(cipher.substring(cipher.indexOf('|') + 1)), StandardCharsets.UTF_8);
        return text.substring(16);
    }

    @BeforeEach
    void setUp() {
        IndividualProperties properties = new IndividualProperties();
        properties.setPiiEncryptModel("IndividualPiiEncrypt");
        properties.setPiiDecryptModel("IndividualPiiDecrypt");
        properties.setPiiAdditionalFieldKeys(List.of("weight", " height "));
        PiiEncryptionProperties hashProperties = new PiiEncryptionProperties();
        hashProperties.setHashSecret("test-secret");
        hashProperties.setHashLowercaseFields(List.of("givenName"));
        hasher = new PiiHasher(hashProperties);
        service = new IndividualPiiCryptoService(encUtil, hasher, mapper, properties);
        when(encUtil.encryptValues(anyList(), anyString())).thenAnswer(call -> ((List<String>) call.getArgument(0)).stream()
                .map(IndividualPiiCryptoServiceTest::fakeCipher).collect(Collectors.toList()));
        when(encUtil.decryptValueNodes(any(), anyString(), any(), anyString())).thenAnswer(call -> {
            ArrayNode in = call.getArgument(0);
            ArrayNode out = mapper.createArrayNode();
            in.forEach(n -> out.addObject().put("value", fakePlain(n.get("value").asText())));
            return out;
        });
    }

    private static Individual person() {
        Identifier aadhaar = Identifier.builder().identifierType("AADHAAR").identifierId("1234-5678-9012").build();
        return Individual.builder().id("ind-1").clientReferenceId("cref-1").tenantId("dev")
                .name(Name.builder().givenName(" Asha ").familyName("Kumari").build())
                .mobileNumber("9876543210")
                .dateOfBirth(new Date(631152000000L))            // 1990-01-01 UTC
                .gender(Gender.FEMALE)
                .userDetails(UserDetails.builder().username("user01").password("secret-pass").build())
                .identifiers(new ArrayList<>(List.of(aadhaar)))
                .address(new ArrayList<>(List.of(Address.builder().doorNo("12").city("Town").latitude(12.5).build())))
                .additionalFields(AdditionalFields.builder().fields(new ArrayList<>(List.of(
                        Field.builder().key("weight").value("54").build(),
                        Field.builder().key("height").value("150").build(),
                        Field.builder().key("tribe").value("none").build()))).build())
                .build();
    }

    @Test
    @DisplayName("encrypt: one enc-service call for the batch; hashes from plain values; no password; input unchanged")
    @SuppressWarnings("unchecked")
    void encryptStoredForm() {
        Individual source = person();
        Individual source2 = person();
        source2.setId("ind-2");

        List<Individual> out = service.encrypt(List.of(source, source2));

        verify(encUtil, times(1)).encryptValues(anyList(), anyString());
        StoredIndividual s = (StoredIndividual) out.get(0);
        assertEquals(fakeCipher(" Asha "), s.getName().getGivenName());
        assertEquals(fakeCipher("9876543210"), s.getMobileNumber());
        assertEquals(hasher.hash(PiiHasher.GIVEN_NAME, "asha"), s.getGivenNameHash());
        assertEquals(hasher.hash(PiiHasher.MOBILE, "9876543210"), s.getMobileNumberHash());
        assertEquals(hasher.hash(PiiHasher.USERNAME, "user01"), s.getUsernameHash());
        assertEquals(fakeCipher("user01"), s.getUserDetails().getUsername());
        assertNull(s.getUserDetails().getPassword());
        assertNull(s.getDateOfBirth());
        assertEquals(fakeCipher("1990-01-01"), s.getDateOfBirthCipher());
        assertNull(s.getGender());
        assertEquals(fakeCipher("FEMALE"), s.getGenderCipher());
        StoredIdentifier id = (StoredIdentifier) s.getIdentifiers().get(0);
        assertEquals(hasher.hash(PiiHasher.IDENTIFIER_ID, "1234-5678-9012"), id.getIdentifierIdHash());
        assertEquals(fakeCipher("1234-5678-9012"), id.getIdentifierId());
        assertEquals(fakeCipher("12"), s.getAddress().get(0).getDoorNo());
        assertEquals(12.5, s.getAddress().get(0).getLatitude());     // stays a plain number
        assertEquals(fakeCipher("54"), s.getAdditionalFields().getFields().get(0).getValue());
        assertEquals(fakeCipher("150"), s.getAdditionalFields().getFields().get(1).getValue());
        assertEquals("none", s.getAdditionalFields().getFields().get(2).getValue()); // key not listed
        // the request objects are not changed
        assertEquals("9876543210", source.getMobileNumber());
        assertEquals("secret-pass", source.getUserDetails().getPassword());
        assertEquals(Gender.FEMALE, source.getGender());
    }

    @Test
    @DisplayName("encrypt: values that are already cipher text are not encrypted again and their hash is not recomputed")
    @SuppressWarnings("unchecked")
    void encryptSkipsCipher() {
        Individual source = Individual.builder().id("ind-1").tenantId("dev")
                .mobileNumber(fakeCipher("9876543210")).email("  ").build();

        Individual out = service.encrypt(List.of(source)).get(0);

        ArgumentCaptor<List<String>> sent = ArgumentCaptor.forClass(List.class);
        verify(encUtil).encryptValues(sent.capture(), org.mockito.ArgumentMatchers.eq("IndividualPiiEncrypt"));
        assertTrue(sent.getValue().isEmpty());
        assertEquals(fakeCipher("9876543210"), out.getMobileNumber());
        assertNull(((StoredIndividual) out).getMobileNumberHash());
        assertEquals("  ", out.getEmail());
    }

    @Test
    @DisplayName("decrypt: round trip gives a plain Individual; plain values from not-yet-backfilled rows are not sent")
    void decryptRoundTrip() {
        List<Individual> stored = service.encrypt(List.of(person()));
        stored.get(0).setAltContactNumber("9000000000");             // a plain value in an old row

        Individual plain = service.decrypt(stored, null).get(0);

        ArgumentCaptor<ArrayNode> sent = ArgumentCaptor.forClass(ArrayNode.class);
        verify(encUtil).decryptValueNodes(sent.capture(), org.mockito.ArgumentMatchers.eq("IndividualPiiDecrypt"), any(),
                org.mockito.ArgumentMatchers.eq("searchIndividual"));
        sent.getValue().forEach(n -> {
            assertTrue(CipherText.isCipher(n.get("value").asText()));
            assertEquals("ind-1", n.get("ownerId").asText());
        });
        assertSame(Individual.class, plain.getClass());
        assertEquals(" Asha ", plain.getName().getGivenName());
        assertEquals("9876543210", plain.getMobileNumber());
        assertEquals("9000000000", plain.getAltContactNumber());
        assertEquals(new Date(631152000000L), plain.getDateOfBirth());
        assertEquals(Gender.FEMALE, plain.getGender());
        assertEquals("user01", plain.getUserDetails().getUsername());
        assertEquals("1234-5678-9012", plain.getIdentifiers().get(0).getIdentifierId());
        assertEquals("12", plain.getAddress().get(0).getDoorNo());
        assertEquals("54", plain.getAdditionalFields().getFields().get(0).getValue());
        assertFalse(mapper.valueToTree(plain).has("mobileNumberHash"));
    }

    @Test
    @DisplayName("decrypt: nothing cipher -> enc-service is not called")
    void decryptNothingToDo() {
        Individual old = Individual.builder().id("ind-1").mobileNumber("9876543210").build();
        assertEquals("9876543210", service.decrypt(List.of(old), null).get(0).getMobileNumber());
        verify(encUtil, never()).decryptValueNodes(any(), anyString(), any(), anyString());
    }

    @Test
    @DisplayName("date of birth text: yyyy-MM-dd or a timestamp text starting with it; anything else is rejected")
    void dobText() {
        TimeZone utc = TimeZone.getTimeZone("UTC");
        assertEquals(new Date(631152000000L), IndividualPiiCryptoService.parseDobText("1990-01-01", utc));
        assertEquals(new Date(631152000000L), IndividualPiiCryptoService.parseDobText(" 1990-01-01 00:00:00 ", utc));
        assertThrows(CustomException.class, () -> IndividualPiiCryptoService.parseDobText("01/01/1990", utc));
        assertThrows(CustomException.class, () -> IndividualPiiCryptoService.parseDobText("1990-13-01", utc));
        assertThrows(CustomException.class, () -> IndividualPiiCryptoService.parseDobText(null, utc));
        // a year of more than 4 digits (the database turns the date 10000-01-01 into that text)
        java.util.Calendar c = java.util.Calendar.getInstance(utc);
        c.setTime(IndividualPiiCryptoService.parseDobText("10000-01-01", utc));
        assertEquals(10000, c.get(java.util.Calendar.YEAR));
        assertNull(IndividualPiiCryptoService.readDob("01/01/1990", utc, "ind-1"));
    }

    @Test
    @DisplayName("decrypt: an unreadable date of birth comes back empty; the record and the rest of the page are fine")
    void unreadableDobDoesNotFailThePage() {
        List<Individual> stored = service.encrypt(List.of(person(), person()));
        ((StoredIndividual) stored.get(0)).setDateOfBirthCipher(fakeCipher("01/01/1990"));

        List<Individual> plain = service.decrypt(stored, null);

        assertNull(plain.get(0).getDateOfBirth());
        assertEquals("9876543210", plain.get(0).getMobileNumber());
        assertEquals(new Date(631152000000L), plain.get(1).getDateOfBirth());
    }

    @Test
    @DisplayName("empty or null list: no enc-service call")
    void emptyLists() {
        assertTrue(service.encrypt(null).isEmpty());
        assertTrue(service.encrypt(new ArrayList<>()).isEmpty());
        assertTrue(service.decrypt(null, null).isEmpty());
        verify(encUtil, never()).encryptValues(anyList(), anyString());
    }
}
