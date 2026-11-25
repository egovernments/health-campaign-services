package org.egov.household.validators.household;

import org.egov.common.contract.request.RequestInfo;
import org.egov.common.helper.RequestInfoTestBuilder;
import org.egov.common.models.Error;
import org.egov.common.models.household.Address;
import org.egov.common.models.household.Household;
import org.egov.common.models.household.HouseholdBulkRequest;
import org.egov.common.pii.CipherText;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A household address text value shaped like cipher text must not be accepted from the API. */
class HAddressValueValidatorTest {

    /** keyId|base64 of 24 bytes: the shape enc-service returns (a real cipher is at least 16 bytes, the GCM tag). */
    static final String CIPHER = "123456|" + Base64.getEncoder().encodeToString(new byte[24]);

    private final HAddressValueValidator validator = new HAddressValueValidator();
    private final RequestInfo requestInfo = RequestInfoTestBuilder.builder().withCompleteRequestInfo().build();

    private static Household household(String id, Address address) {
        return Household.builder().id(id).clientReferenceId("cr-" + id).tenantId("default").address(address).build();
    }

    private static Address plainAddress() {
        return Address.builder().id("a-1").tenantId("default").doorNo("12B").addressLine1("line one").addressLine2("line two")
                .landmark("near the school").city("Maputo").pincode("1100").buildingName("Block 4").street("Main Road").build();
    }

    private Map<Household, List<Error>> validate(Household... households) {
        return validator.validate(HouseholdBulkRequest.builder().requestInfo(requestInfo)
                .households(new ArrayList<>(List.of(households))).build());
    }

    @Test
    @DisplayName("the test value really is cipher-shaped (so the tests below test what they say)")
    void testValueIsCipherShaped() {
        assertTrue(CipherText.isCipher(CIPHER), CIPHER);
    }

    @Test
    @DisplayName("each of the 8 address text fields: a cipher-shaped value gives a per-record error, code HOUSEHOLD_ADDRESS_VALUE_NOT_ALLOWED, no value in it")
    void cipherShapedValueInAnyFieldIsRejected() {
        List<Map.Entry<String, BiConsumer<Address, String>>> setters = List.of(
                Map.entry("doorNo", Address::setDoorNo), Map.entry("addressLine1", Address::setAddressLine1),
                Map.entry("addressLine2", Address::setAddressLine2), Map.entry("landmark", Address::setLandmark),
                Map.entry("city", Address::setCity), Map.entry("pincode", Address::setPincode),
                Map.entry("buildingName", Address::setBuildingName), Map.entry("street", Address::setStreet));
        for (Map.Entry<String, BiConsumer<Address, String>> field : setters) {
            Address address = plainAddress();
            field.getValue().accept(address, CIPHER);
            Household bad = household("bad", address);

            Map<Household, List<Error>> errors = validate(household("good", plainAddress()), bad);

            assertEquals(1, errors.size(), field.getKey());
            List<Error> forBad = errors.get(bad);
            assertNotNull(forBad, field.getKey());
            assertEquals(1, forBad.size(), field.getKey());
            Error error = forBad.get(0);
            assertEquals("HOUSEHOLD_ADDRESS_VALUE_NOT_ALLOWED", error.getErrorCode());
            assertEquals(Error.ErrorType.NON_RECOVERABLE, error.getType());
            assertTrue(error.getErrorMessage().contains(field.getKey()), error.getErrorMessage());
            assertFalse(error.getErrorMessage().contains(CIPHER), "the value is in the message");
            assertFalse(error.getErrorMessage().contains("AAAAAAAA"), "part of the value is in the message");
            assertFalse(String.valueOf(error.getException().getMessage()).contains("AAAAAAAA"));
        }
    }

    @Test
    @DisplayName("several cipher-shaped fields in one record: one error naming all of them")
    void severalFieldsOneError() {
        Address address = plainAddress();
        address.setDoorNo(CIPHER);
        address.setCity(CIPHER);
        Household bad = household("bad", address);

        List<Error> errors = validate(bad).get(bad);

        assertEquals(1, errors.size());
        assertTrue(errors.get(0).getErrorMessage().contains("doorNo") && errors.get(0).getErrorMessage().contains("city"),
                errors.get(0).getErrorMessage());
    }

    @Test
    @DisplayName("a plain address is accepted")
    void plainValueIsAccepted() {
        assertTrue(validate(household("h-1", plainAddress())).isEmpty());
    }

    @Test
    @DisplayName("a plain value with a '|' that is not cipher-shaped is accepted")
    void plainValueWithPipeIsAccepted() {
        for (String value : List.of("12|B", "Flat 4|Block B", "A|B", "Block A|Lane 2",
                "5|ABCDEFGHIJKLMNOPQRST1",            // base64 part is not a multiple of 4 characters
                "5|ABCDEFGH",                          // base64 part decodes to fewer than the 16 bytes of a GCM tag
                "|AAAAAAAAAAAAAAAAAAAAAA==",           // no key id
                "123456|AAAAAAAAAAAAAAAAAAAAAA== extra")) {
            assertFalse(CipherText.isCipher(value), value + " is cipher-shaped; the test needs another example");
            Address address = plainAddress();
            address.setDoorNo(value);
            address.setLandmark(value);

            assertTrue(validate(household("h-1", address)).isEmpty(), value);
        }
    }

    @Test
    @DisplayName("no address, and null or blank address values, are accepted")
    void emptyValuesAreAccepted() {
        Address blank = Address.builder().doorNo("").city("  ").build();

        assertTrue(validate(household("h-1", null), household("h-2", new Address()), household("h-3", blank)).isEmpty());
    }

    @Test
    @DisplayName("latitude and longitude are not text fields and are not checked")
    void coordinatesAreNotChecked() {
        Address address = plainAddress();
        address.setLatitude(1.5);
        address.setLongitude(2.5);

        assertTrue(validate(household("h-1", address)).isEmpty());
    }

    @Test
    @DisplayName("hasCipherShapedValue: true only when one of the 8 text fields is cipher-shaped (used to keep such input out of the cache)")
    void hasCipherShapedValue() {
        Address bad = plainAddress();
        bad.setStreet(CIPHER);

        assertTrue(HAddressValueValidator.hasCipherShapedValue(household("h-1", bad)));
        assertFalse(HAddressValueValidator.hasCipherShapedValue(household("h-2", plainAddress())));
        assertFalse(HAddressValueValidator.hasCipherShapedValue(household("h-3", null)));
    }
}
