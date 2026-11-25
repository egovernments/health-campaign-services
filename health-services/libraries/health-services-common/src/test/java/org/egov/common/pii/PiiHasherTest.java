package org.egov.common.pii;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PiiHasherTest {

    private static PiiHasher hasher(String secret) {
        return hasher(secret, List.of(PiiHasher.GIVEN_NAME));
    }

    private static PiiHasher hasher(String secret, List<String> lowercaseFields) {
        PiiEncryptionProperties properties = new PiiEncryptionProperties();
        properties.setHashSecret(secret);
        properties.setHashLowercaseFields(lowercaseFields);
        return new PiiHasher(properties);
    }

    @Test
    @DisplayName("same hashes as the migration script (scripts/pii_migrate.py Hasher, secret 'test-secret')")
    void matchesMigrationScript() {
        PiiHasher h = hasher("test-secret");
        assertEquals("547f401df372862ffa3cb6ee8283778ab24f599baa0bf60e39e4fbddfbbbbc1c", h.hash(PiiHasher.MOBILE, "  9876543210 "));
        assertEquals("9a5e57c6b645f7117681290078b829af061c6a414d81bfcc174a06e3234f63a6", h.hash(PiiHasher.GIVEN_NAME, " Asha "));
        assertEquals("cde4bc3ef4681828ecb99805e60462c74dcc7d5671bc8cc0612f788bb389dc7a", h.hash(PiiHasher.USERNAME, "User01"));
        assertEquals("7ae0c965c2d0ac08f0c71153f64b388ccc15313487873083adef30ed1040a43f", h.hash(PiiHasher.IDENTIFIER_ID, "1234-5678-9012"));
    }

    @Test
    @DisplayName("given name ignores case; username and other fields do not; field name is part of the hash")
    void normalising() {
        PiiHasher h = hasher("test-secret");
        assertEquals(h.hash(PiiHasher.GIVEN_NAME, "ASHA"), h.hash(PiiHasher.GIVEN_NAME, "asha"));
        assertNotEquals(h.hash(PiiHasher.USERNAME, "USER01"), h.hash(PiiHasher.USERNAME, "user01"));
        assertNotEquals(h.hash(PiiHasher.MOBILE, "123"), h.hash(PiiHasher.IDENTIFIER_ID, "123"));
        assertNotEquals(h.hash(PiiHasher.MOBILE, "123"), hasher("other-secret").hash(PiiHasher.MOBILE, "123"));
    }

    @Test
    @DisplayName("lower-case fields come from the property: blank entries are ignored, a field not listed keeps its case")
    void lowercaseFieldsFromProperty() {
        PiiHasher none = hasher("test-secret", List.of(""));
        assertNotEquals(none.hash(PiiHasher.GIVEN_NAME, "ASHA"), none.hash(PiiHasher.GIVEN_NAME, "asha"));
        PiiHasher nullList = hasher("test-secret", null);
        assertNotEquals(nullList.hash(PiiHasher.GIVEN_NAME, "ASHA"), nullList.hash(PiiHasher.GIVEN_NAME, "asha"));

        PiiHasher listed = hasher("test-secret", Arrays.asList(" givenName ", "", "familyName"));
        assertEquals(listed.hash(PiiHasher.GIVEN_NAME, "ASHA"), listed.hash(PiiHasher.GIVEN_NAME, "asha"));
        assertEquals(listed.hash("familyName", "RAO"), listed.hash("familyName", "rao"));
        assertNotEquals(listed.hash(PiiHasher.USERNAME, "USER01"), listed.hash(PiiHasher.USERNAME, "user01"));
    }

    @Test
    @DisplayName("null and blank values give null; hashAll drops them")
    void blanks() {
        PiiHasher h = hasher("test-secret");
        assertNull(h.hash(PiiHasher.MOBILE, null));
        assertNull(h.hash(PiiHasher.MOBILE, "   "));
        assertNull(h.hashAll(PiiHasher.MOBILE, null));
        assertEquals(List.of(h.hash(PiiHasher.MOBILE, "1")), h.hashAll(PiiHasher.MOBILE, Arrays.asList("1", null, " ")));
    }

    @Test
    @DisplayName("no secret -> the service does not start")
    void noSecret() {
        assertThrows(IllegalStateException.class, () -> hasher(""));
        assertThrows(IllegalStateException.class, () -> hasher(null));
    }
}
