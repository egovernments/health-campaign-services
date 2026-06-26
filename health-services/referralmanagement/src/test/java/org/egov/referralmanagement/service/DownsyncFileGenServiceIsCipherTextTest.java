package org.egov.referralmanagement.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the {@code DownsyncFileGenService#isCipherText} predicate against the
 * actual eGov ciphertext format produced by the encryption service:
 *   {@code <keyId>|<base64>}
 * where {@code keyId} is a short numeric key-version identifier (e.g. {@code 104227})
 * for current deployments, OR a 36-char UUID for legacy data.
 *
 * <p>Background: an earlier version of the predicate required a 36-char UUID prefix
 * and rejected the short-numeric format that production enc-service actually emits,
 * causing encrypted identifierId / mobileNumber to leak un-decrypted into the
 * downsync NDJSON files.
 */
class DownsyncFileGenServiceIsCipherTextTest {

    private static boolean call(String text) throws Exception {
        Method m = DownsyncFileGenService.class.getDeclaredMethod("isCipherText", String.class);
        m.setAccessible(true);
        return (boolean) m.invoke(null, text);
    }

    // ── Positive cases — must be recognized as ciphertext ──────────────────────

    @Test @DisplayName("Production format with 6-digit numeric keyId")
    void prodNumericKeyId() throws Exception {
        assertTrue(call("104227|CsGQCcaHqjJ7uJJVC9nObS3sosm550+OME/n9z9T4kknAp84Dxm7/onvIRPv9535Lvl/SQ=="));
        assertTrue(call("104227|D8zEXMaA/zd7v8YBDdnLaX7ropLssRuON0PmrW8O7Ek="));
    }

    @Test @DisplayName("Locally-encrypted ciphertext from enc-service")
    void localEncServiceOutput() throws Exception {
        assertTrue(call("153660|urvP+23L8GEjS0WYL4nmZszn+l5vFF27ooU="));
        assertTrue(call("153660|yeyQo3i7rDaJuJp46WzIddQ05IGBCWFZ"));
    }

    @Test @DisplayName("Legacy UUID-prefixed ciphertext still accepted (backward compat)")
    void legacyUuidPrefix() throws Exception {
        assertTrue(call("12345678-1234-1234-1234-123456789012|7+afrDuG7iZnEmuyl8acNFmVuP7k4+nfoBIzQOjaLLs="));
    }

    @Test @DisplayName("Minimal 1-char prefix accepted")
    void minimalPrefix() throws Exception {
        assertTrue(call("1|YWJj"));
    }

    // ── Negative cases — must NOT be sent to enc-service ───────────────────────

    @Test @DisplayName("null and empty string are not ciphertext")
    void nullAndEmpty() throws Exception {
        assertFalse(call(null));
        assertFalse(call(""));
    }

    @Test @DisplayName("Plaintext (no pipe) rejected")
    void plaintextNoPipe() throws Exception {
        assertFalse(call("plain-text-id"));
        assertFalse(call("9876543210"));
        assertFalse(call("d57bb260-1dd1-11f1-bac1-457c3d6b2ee7")); // bare UUID, no pipe
    }

    @Test @DisplayName("Leading pipe (empty keyId) rejected")
    void leadingPipe() throws Exception {
        assertFalse(call("|YWJj"));
    }

    @Test @DisplayName("Trailing pipe (empty base64) rejected")
    void trailingPipe() throws Exception {
        assertFalse(call("104227|"));
    }

    @Test @DisplayName("Invalid base64 characters rejected")
    void invalidBase64Chars() throws Exception {
        assertFalse(call("104227|***not-b64!!!*"));
    }

    @Test @DisplayName("Base64 with length not multiple of 4 rejected")
    void invalidBase64Length() throws Exception {
        assertFalse(call("104227|abc"));   // length 3
        assertFalse(call("ABC|123"));      // length 3
    }
}
