package org.egov.excelingestion.util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * HMAC-SHA256 helper for the "exact file" guard.
 *
 * <p>The generated join-mode template embeds {@code sign(generationId)} in its hidden meta sheet. On
 * upload the server recomputes the signature with the same server-side secret and requires a match. The
 * generationId itself is not confidential (it appears in API responses / URLs / logs), but the signature
 * is written ONLY into the genuine downloaded file and never exposed elsewhere - so a valid signature
 * proves the upload is that exact file, not a hand-built or differently-sourced look-alike.</p>
 */
public final class SignatureUtil {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private SignatureUtil() {
        // utility class
    }

    /**
     * Computes a Base64 HMAC-SHA256 of {@code payload} keyed by {@code secret}. Caller must ensure the
     * secret is non-empty (an empty key is a configuration error).
     */
    public static String sign(String payload, String secret) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return Base64.getEncoder().encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC signature computation failed", e);
        }
    }

    /** Constant-time comparison so verification does not leak timing information. */
    public static boolean matches(String expected, String provided) {
        if (expected == null || provided == null) {
            return false;
        }
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                provided.getBytes(StandardCharsets.UTF_8));
    }
}
