package org.egov.excelingestion.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the HMAC-SHA256 helper behind the "exact file" guard.
 */
class SignatureUtilTest {

    @Test
    void sign_matchesKnownAnswerVector() {
        // Known-answer test: HMAC-SHA256("gen-123", key="secret") in Base64. Guards against any accidental
        // change of algorithm / encoding that would silently invalidate every previously-issued file.
        assertEquals("bGIGjwtuQrDcrKK9aZAFqFeyT7td73DVA306UraQFT4=",
                SignatureUtil.sign("gen-123", "secret"));
    }

    @Test
    void sign_isDeterministic() {
        assertEquals(SignatureUtil.sign("gen-123", "secret"), SignatureUtil.sign("gen-123", "secret"));
    }

    @Test
    void sign_differsForDifferentSecret() {
        assertNotEquals(SignatureUtil.sign("gen-123", "secret-A"), SignatureUtil.sign("gen-123", "secret-B"));
    }

    @Test
    void sign_differsForDifferentPayload() {
        assertNotEquals(SignatureUtil.sign("gen-1", "secret"), SignatureUtil.sign("gen-2", "secret"));
    }

    @Test
    void matches_trueForEqual_falseOtherwise() {
        String sig = SignatureUtil.sign("gen-123", "secret");
        assertTrue(SignatureUtil.matches(sig, sig));
        assertFalse(SignatureUtil.matches(sig, sig + "x"));
        assertFalse(SignatureUtil.matches(sig, null));
        assertFalse(SignatureUtil.matches(null, sig));
        assertFalse(SignatureUtil.matches(null, null));
    }
}
