package org.egov.common.pii;

import java.util.Base64;
import java.util.regex.Pattern;

/**
 * Tells enc-service cipher text apart from plain text.
 *
 * <p>enc-service returns {@code <keyId>|<base64>}. AES-GCM always adds a 16-byte tag, so a
 * real cipher decodes to at least 16 bytes. Checking this (and not only "contains a pipe")
 * keeps plain values such as "A|B" from being sent to decrypt, which enc-service rejects
 * with an error.</p>
 */
public final class CipherText {

    private static final Pattern SHAPE = Pattern.compile("^[A-Za-z0-9-]{1,64}\\|[A-Za-z0-9+/]+={0,2}$");
    private static final int GCM_TAG_BYTES = 16;

    private CipherText() {
    }

    public static boolean isCipher(String value) {
        if (value == null || !SHAPE.matcher(value).matches()) {
            return false;
        }
        String b64 = value.substring(value.indexOf('|') + 1);
        if (b64.length() % 4 != 0) {
            return false;
        }
        try {
            return Base64.getDecoder().decode(b64).length >= GCM_TAG_BYTES;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
