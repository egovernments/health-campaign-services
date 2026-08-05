package org.egov.common.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the behaviour of the extracted masker directly, so the CommonUtils choke-point usage has its
 * own regression gate.
 */
class SensitiveDataMaskerTest {

    private static final String TOKEN = "1f7a0e3c-live-session-token";

    /** The masked error body is parsed downstream, so validity is asserted with a real parser. */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static JsonNode parseOrFail(String json) {
        try {
            return MAPPER.readTree(json);
        } catch (JsonProcessingException e) {
            throw new AssertionError("masked output is not valid JSON: " + json, e);
        }
    }

    @Test
    @DisplayName("masks an authToken inside serialized JSON while keeping the JSON shape valid")
    void masksAuthTokenInJsonKeepingValidShape() {
        String json = "{\"RequestInfo\":{\"apiId\":\"some-api-id\",\"authToken\":\"" + TOKEN
                + "\",\"msgId\":\"some-msg-id\"},\"Households\":[{\"clientReferenceId\":\"CRI-0\"}]}";

        String masked = SensitiveDataMasker.maskText(json);

        assertFalse(masked.contains(TOKEN));
        // The replacement slots into the same quoted position, so the result is still valid JSON.
        assertTrue(masked.contains("\"authToken\":\"***MASKED***\""));
        // Everything that is not the credential value is byte-identical.
        assertTrue(masked.contains("\"apiId\":\"some-api-id\""));
        assertTrue(masked.contains("\"msgId\":\"some-msg-id\""));
        assertTrue(masked.contains("\"clientReferenceId\":\"CRI-0\""));
    }

    @Test
    @DisplayName("masks authToken=... in free text, e.g. a RequestInfo.toString() echoed by an exception")
    void masksAuthTokenInFreeText() {
        String masked = SensitiveDataMasker.maskText(
                "call failed for RequestInfo(apiId=x, authToken=" + TOKEN + ", ts=1)");

        assertFalse(masked.contains(TOKEN));
        assertTrue(masked.contains("authToken=***MASKED***"));
        assertTrue(masked.contains("call failed for RequestInfo(apiId=x"));
    }

    @Test
    @DisplayName("masks auth_token and password variants case-insensitively")
    void masksVariantsCaseInsensitively() {
        assertEquals("auth_token=***MASKED***", SensitiveDataMasker.maskText("auth_token=" + TOKEN));
        assertEquals("AUTHTOKEN=***MASKED***", SensitiveDataMasker.maskText("AUTHTOKEN=" + TOKEN));
        assertEquals("\"Password\":\"***MASKED***\"",
                SensitiveDataMasker.maskText("\"Password\":\"hunter2\""));
        assertEquals("password: ***MASKED***", SensitiveDataMasker.maskText("password: hunter2"));
    }

    @Test
    @DisplayName("leaves null, empty and token-free text untouched - no invented mask")
    void leavesEmptyOrAbsentTokenAlone() {
        assertNull(SensitiveDataMasker.maskText(null));
        assertEquals("", SensitiveDataMasker.maskText(""));
        String noToken = "plain diagnostic without credentials, code=SOMECODE";
        // Byte-identical when there is nothing to mask.
        assertEquals(noToken, SensitiveDataMasker.maskText(noToken));
        // An empty value after the key does not match the value group, so nothing is invented.
        String emptyToken = "{\"authToken\":\"\"}";
        assertEquals(emptyToken, SensitiveDataMasker.maskText(emptyToken));
    }

    @Test
    @DisplayName("does not touch non-sensitive keys or values")
    void doesNotMaskNonSensitiveContent() {
        // RequestInfo.key is deliberately NOT credential-shaped for the masker: generic key/value
        // additionalFields would collide.
        String text = "{\"key\":\"some-key\",\"tokenizer\":\"whitespace\",\"name\":\"A B\"}";
        assertEquals(text, SensitiveDataMasker.maskText(text));
        assertFalse(SensitiveDataMasker.isSensitiveKey("key"));
        assertFalse(SensitiveDataMasker.isSensitiveKey("name"));
        assertFalse(SensitiveDataMasker.isSensitiveKey(null));
        assertTrue(SensitiveDataMasker.isSensitiveKey("authToken"));
        assertTrue(SensitiveDataMasker.isSensitiveKey("AUTHTOKEN"));
        assertTrue(SensitiveDataMasker.isSensitiveKey("password"));
    }

    @Test
    @DisplayName("maskStructure is copy-on-write: caller's maps are never mutated")
    void maskStructureIsCopyOnWrite() {
        Map<String, Object> requestInfo = new LinkedHashMap<>();
        requestInfo.put("apiId", "some-api-id");
        requestInfo.put("authToken", TOKEN);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("RequestInfo", requestInfo);
        List<Object> records = List.of(Map.of("clientReferenceId", "CRI-0"));
        payload.put("Households", records);

        @SuppressWarnings("unchecked")
        Map<String, Object> masked = (Map<String, Object>) SensitiveDataMasker.maskStructure(payload);

        assertNotSame(payload, masked);
        @SuppressWarnings("unchecked")
        Map<String, Object> maskedRequestInfo = (Map<String, Object>) masked.get("RequestInfo");
        assertEquals("***MASKED***", maskedRequestInfo.get("authToken"));
        assertEquals("some-api-id", maskedRequestInfo.get("apiId"));
        // Untouched branches are carried through as the same instances, not deep-copied.
        assertSame(records, masked.get("Households"));
        // The caller's own structure keeps the live token: it may still be needed upstream.
        assertEquals(TOKEN, requestInfo.get("authToken"));
    }

    @Test
    @DisplayName("maskStructure returns the very same instance when there is nothing to mask")
    void maskStructureSameInstanceWhenNothingToMask() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("RequestInfo", new LinkedHashMap<>(Map.of("apiId", "some-api-id")));
        payload.put("Households", List.of(Map.of("clientReferenceId", "CRI-0")));

        assertSame(payload, SensitiveDataMasker.maskStructure(payload));
    }

    @Test
    @DisplayName("stops walking at the depth cap instead of recursing without bound")
    void depthCapGuardsPathologicalNesting() {
        // Build 12 levels of nesting with a credential at the bottom - deeper than the cap of 8.
        Map<String, Object> innermost = new LinkedHashMap<>();
        innermost.put("authToken", TOKEN);
        Map<String, Object> current = innermost;
        for (int i = 0; i < 12; i++) {
            Map<String, Object> wrapper = new LinkedHashMap<>();
            wrapper.put("level" + i, current);
            current = wrapper;
        }

        // Must return (guard, not stack overflow); below the cap nothing changed, so the same
        // instance comes back and the too-deep credential is knowingly left as-is.
        Object result = SensitiveDataMasker.maskStructure(current);
        assertSame(current, result);
        assertEquals(TOKEN, innermost.get("authToken"));
    }

    // ------------------------------------------------------------------ JSON validity (L2-F1)
    // The masked text is the ErrorDetails.requestBody returned to the caller AND published to the
    // error-details topic, so it is PARSED downstream. The original single pattern ended its first
    // group with an optional quote and replaced with a bare sentinel: the closing quote survived only
    // because the source quote was never consumed, so every value that was not a quoted string
    // produced structurally invalid JSON. These tests assert validity with Jackson, not by eye.

    @Test
    @DisplayName("a quoted credential value is masked and the document still parses")
    void quotedCredentialValueKeepsDocumentParseable() {
        String json = "{\"RequestInfo\":{\"apiId\":\"some-api-id\",\"authToken\":\"" + TOKEN
                + "\"},\"Households\":[{\"clientReferenceId\":\"CRI-0\"}]}";

        String masked = SensitiveDataMasker.maskText(json);

        JsonNode node = parseOrFail(masked);
        assertEquals("***MASKED***", node.get("RequestInfo").get("authToken").asText());
        assertEquals("some-api-id", node.get("RequestInfo").get("apiId").asText());
        assertEquals("CRI-0", node.get("Households").get(0).get("clientReferenceId").asText());
        assertFalse(masked.contains(TOKEN));
    }

    @Test
    @DisplayName("a credential whose value is a literal null is left structurally intact")
    void literalNullCredentialValueIsLeftIntact() {
        // The regression shape: the request carried no credential at all, and the old pattern turned
        // this into {"authToken":***MASKED***} - unparseable, and claiming a mask that never happened.
        String json = "{\"authToken\":null,\"apiId\":\"some-api-id\"}";

        String masked = SensitiveDataMasker.maskText(json);

        JsonNode node = parseOrFail(masked);
        assertTrue(node.get("authToken").isNull());
        assertEquals("some-api-id", node.get("apiId").asText());
        // Nothing to hide means nothing is claimed to have been hidden.
        assertFalse(masked.contains("***MASKED***"));
        assertEquals(json, masked);
        // Whitespace around the colon must not change that verdict either.
        String spaced = "{\"password\" : null }";
        assertEquals(spaced, SensitiveDataMasker.maskText(spaced));
    }

    @Test
    @DisplayName("a numeric credential value is masked to a QUOTED sentinel so the document still parses")
    void numericCredentialValueIsMaskedAsQuotedString() {
        // A PIN-shaped password is still a credential, so it must not be left in the clear; the only
        // JSON-valid replacement is a quoted sentinel, which changes that field's type by design.
        String json = "{\"password\":8461532,\"apiId\":\"some-api-id\"}";

        String masked = SensitiveDataMasker.maskText(json);

        JsonNode node = parseOrFail(masked);
        assertEquals("***MASKED***", node.get("password").asText());
        assertTrue(node.get("password").isTextual());
        assertEquals("some-api-id", node.get("apiId").asText());
        assertFalse(masked.contains("8461532"));

        // Negative and exponent forms are numbers too, and are consumed whole.
        JsonNode signed = parseOrFail(SensitiveDataMasker.maskText(
                "{\"password\":-1.5e3,\"apiId\":\"x\"}"));
        assertEquals("***MASKED***", signed.get("password").asText());
        assertEquals("x", signed.get("apiId").asText());
    }

    @Test
    @DisplayName("an object credential value is left alone, but a credential nested inside it is masked")
    void objectCredentialValueIsLeftStructurallyIntact() {
        String json = "{\"authToken\":{\"scheme\":\"Bearer\",\"password\":\"nested-secret-value\"},"
                + "\"apiId\":\"some-api-id\"}";

        String masked = SensitiveDataMasker.maskText(json);

        JsonNode node = parseOrFail(masked);
        // The object survives as an object - the old pattern replaced its opening brace and left the
        // rest of the members dangling.
        assertTrue(node.get("authToken").isObject());
        assertEquals("Bearer", node.get("authToken").get("scheme").asText());
        // The nested credential is still redacted, as its own key/value pair.
        assertEquals("***MASKED***", node.get("authToken").get("password").asText());
        assertFalse(masked.contains("nested-secret-value"));
        assertEquals("some-api-id", node.get("apiId").asText());
    }

    @Test
    @DisplayName("an array credential value is left alone and the document still parses")
    void arrayCredentialValueIsLeftStructurallyIntact() {
        String json = "{\"authToken\":[\"scope-a\",\"scope-b\"],\"apiId\":\"some-api-id\"}";

        String masked = SensitiveDataMasker.maskText(json);

        JsonNode node = parseOrFail(masked);
        assertTrue(node.get("authToken").isArray());
        assertEquals(2, node.get("authToken").size());
        assertEquals("scope-a", node.get("authToken").get(0).asText());
        assertEquals("some-api-id", node.get("apiId").asText());
        // There is no credential string at this key, so nothing is rewritten at all.
        assertEquals(json, masked);
    }

    @Test
    @DisplayName("a credential value containing an escaped quote is masked whole, leaking no fragment")
    void escapedQuoteInsideValueIsMaskedWhole() {
        // The old pattern stopped at the backslash: it emitted "pre\***MASKED***post-fragment", which
        // is both invalid JSON (\* is not an escape) and a partial disclosure of the credential.
        String json = "{\"authToken\":\"pre\\\"post-fragment\",\"apiId\":\"some-api-id\"}";

        String masked = SensitiveDataMasker.maskText(json);

        JsonNode node = parseOrFail(masked);
        assertEquals("***MASKED***", node.get("authToken").asText());
        assertFalse(masked.contains("post-fragment"));
        assertEquals("some-api-id", node.get("apiId").asText());

        // An escaped backslash immediately before the closing quote must not be mistaken for it.
        JsonNode trailing = parseOrFail(SensitiveDataMasker.maskText(
                "{\"password\":\"ends-with-backslash\\\\\",\"apiId\":\"x\"}"));
        assertEquals("***MASKED***", trailing.get("password").asText());
        assertEquals("x", trailing.get("apiId").asText());
    }

    @Test
    @DisplayName("extra whitespace and newlines around the colon do not defeat the mask")
    void whitespaceAroundColonStillMasks() {
        String json = "{\"authToken\"  :  \"" + TOKEN + "\",\n  \"password\"\n:\t\"other-secret\"}";

        String masked = SensitiveDataMasker.maskText(json);

        JsonNode node = parseOrFail(masked);
        assertEquals("***MASKED***", node.get("authToken").asText());
        assertEquals("***MASKED***", node.get("password").asText());
        assertFalse(masked.contains(TOKEN));
        assertFalse(masked.contains("other-secret"));
    }

    @Test
    @DisplayName("a business field whose NAME merely contains a credential word is not corrupted")
    void businessFieldNamedAfterACredentialWordSurvives() {
        // Legitimate configuration/business data: the word is a prefix, not the whole key, so there is
        // no credential here and the values must arrive intact for the error to be diagnosable.
        String json = "{\"passwordPolicyName\":\"NIST-800-63B\",\"authTokenCount\":3,"
                + "\"passwordHint\":\"set at onboarding\",\"tokenizer\":\"whitespace\"}";

        String masked = SensitiveDataMasker.maskText(json);

        assertEquals(json, masked);
        JsonNode node = parseOrFail(masked);
        assertEquals("NIST-800-63B", node.get("passwordPolicyName").asText());
        assertEquals(3, node.get("authTokenCount").asInt());
        assertEquals("set at onboarding", node.get("passwordHint").asText());
        // A key that ENDS with a credential word is a credential, and is still masked.
        JsonNode compound = parseOrFail(SensitiveDataMasker.maskText(
                "{\"userPassword\":\"compound-key-secret\"}"));
        assertEquals("***MASKED***", compound.get("userPassword").asText());
    }

    @Test
    @DisplayName("free-text key=value still masks, and free-text key=null is not reported as masked")
    void freeTextFormsKeepTheirContract() {
        // RequestInfo.toString() shape, inside an exception message: no JSON, no quotes to preserve.
        assertEquals("failed for RequestInfo(apiId=x, authToken=***MASKED***, ts=1)",
                SensitiveDataMasker.maskText(
                        "failed for RequestInfo(apiId=x, authToken=" + TOKEN + ", ts=1)"));
        assertEquals("auth_token=***MASKED*** password=***MASKED***",
                SensitiveDataMasker.maskText("auth_token=" + TOKEN + " password=hunter2"));
        // An absent credential rendered as the literal null must not be reported as a mask - that is
        // misleading in exactly the artefact an investigator reads.
        assertEquals("RequestInfo(apiId=x, authToken=null, ts=1)",
                SensitiveDataMasker.maskText("RequestInfo(apiId=x, authToken=null, ts=1)"));
        assertEquals("authToken=null", SensitiveDataMasker.maskText("authToken=null"));
        // The same free-text shape embedded in a JSON string value keeps the document parseable.
        JsonNode node = parseOrFail(SensitiveDataMasker.maskText(
                "{\"note\":\"retry with authToken=" + TOKEN + " please\"}"));
        assertEquals("retry with authToken=***MASKED*** please", node.get("note").asText());
    }

    @Test
    @DisplayName("masking is idempotent: a second pass neither re-masks nor breaks the document")
    void maskingIsIdempotent() {
        String json = "{\"authToken\":\"" + TOKEN + "\",\"password\":4242,\"n\":null,"
                + "\"apiId\":\"some-api-id\"}";

        String once = SensitiveDataMasker.maskText(json);
        String twice = SensitiveDataMasker.maskText(once);

        assertEquals(once, twice);
        parseOrFail(twice);
    }

    @Test
    @DisplayName("a large credential value is masked without a StackOverflowError")
    void largeCredentialValueDoesNotOverflowTheStack() {
        // Guards the regression the obvious escape-tolerant pattern (?:[^"\\]|\\.)+ would reintroduce:
        // java.util.regex recurses once per character for an alternation inside a quantifier and dies
        // at a value of only a few KB. This masker runs inside error handling, where throwing an Error
        // would be far worse than the leak it prevents.
        StringBuilder longValue = new StringBuilder(64 * 1024);
        for (int i = 0; i < 64 * 1024; i++) {
            longValue.append('a');
        }
        String json = "{\"authToken\":\"" + longValue + "\",\"apiId\":\"some-api-id\"}";

        String masked = SensitiveDataMasker.maskText(json);

        JsonNode node = parseOrFail(masked);
        assertEquals("***MASKED***", node.get("authToken").asText());
        assertEquals("some-api-id", node.get("apiId").asText());
    }

    @Test
    @DisplayName("a structure masked and then serialized is valid JSON, null-valued credential included")
    void maskedStructureSerializesToValidJson() throws JsonProcessingException {
        Map<String, Object> requestInfo = new LinkedHashMap<>();
        requestInfo.put("apiId", "some-api-id");
        requestInfo.put("authToken", TOKEN);
        Map<String, Object> userInfo = new LinkedHashMap<>();
        userInfo.put("userName", "some-username");
        // The end-to-end shape behind the defect: a credential key the caller left null.
        userInfo.put("password", null);
        requestInfo.put("userInfo", userInfo);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("RequestInfo", requestInfo);
        payload.put("note", "retry with authToken=" + TOKEN);

        String serialized = MAPPER.writeValueAsString(SensitiveDataMasker.maskStructure(payload));

        JsonNode node = parseOrFail(serialized);
        assertEquals("***MASKED***", node.get("RequestInfo").get("authToken").asText());
        assertTrue(node.get("RequestInfo").get("userInfo").get("password").isNull());
        assertEquals("some-username", node.get("RequestInfo").get("userInfo").get("userName").asText());
        assertEquals("retry with authToken=***MASKED***", node.get("note").asText());
        assertFalse(serialized.contains(TOKEN));
    }
}
