package org.egov.common.pii;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CipherTextTest {

    @Test
    @DisplayName("keyId|base64 of at least 16 bytes is cipher text")
    void cipher() {
        assertTrue(CipherText.isCipher("228075|" + Base64.getEncoder().encodeToString(new byte[16])));
        assertTrue(CipherText.isCipher("859418|" + Base64.getEncoder().encodeToString(new byte[29])));
    }

    @Test
    @DisplayName("plain values, even with a pipe, are not cipher text")
    void plain() {
        assertFalse(CipherText.isCipher(null));
        assertFalse(CipherText.isCipher(""));
        assertFalse(CipherText.isCipher("9876543210"));
        assertFalse(CipherText.isCipher("A|B"));
        assertFalse(CipherText.isCipher("228075|AAAA"));                // decodes to 3 bytes (< GCM tag)
        assertFalse(CipherText.isCipher("228075|" + Base64.getEncoder().encodeToString(new byte[16]).substring(1)));
        assertFalse(CipherText.isCipher("house 12|" + Base64.getEncoder().encodeToString(new byte[16])));
        assertFalse(CipherText.isCipher("228075|" + Base64.getEncoder().encodeToString(new byte[16]) + " "));
    }
}
