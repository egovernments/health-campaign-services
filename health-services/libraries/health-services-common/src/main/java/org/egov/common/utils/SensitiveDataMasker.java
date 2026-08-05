package org.egov.common.utils;

import org.springframework.util.ObjectUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Masks credential values (authToken, password) in free text and in map/list structures before they
 * are written anywhere durable - a Kafka topic, an error-details document, an HTTP error echo or a
 * log line.
 *
 * <p>Valid JSON in yields valid JSON out. Trap: do NOT collapse {@link #SENSITIVE_JSON_STRING},
 * {@link #SENSITIVE_JSON_NUMBER} and {@link #SENSITIVE_TEXT} back into one free-text pattern - that
 * shape emits invalid JSON for every non-string value, and the masked body is both returned to the
 * caller and published to the error-details topic, where it is parsed.
 *
 * <p>Masking is deliberately NOT flag-gated: a redaction behind a default-OFF flag ships the leak.
 * Nothing is dropped - every error code, message and payload field stays visible, only the credential
 * value becomes the {@value #MASKED} sentinel.
 */
public final class SensitiveDataMasker {

    /** What a masked value is replaced with, in payloads and in free text alike. */
    public static final String MASKED = "***MASKED***";

    /**
     * Keys whose values are credentials, are never needed to replay or debug a request, and must
     * therefore not be written to a topic, an index or a log. Compared lower-cased so
     * {@code authToken} / {@code AuthToken} both match.
     *
     * <p>{@code RequestInfo.key} is deliberately NOT in this set: it is a generic name that would
     * collide with key/value additionalFields content.
     */
    private static final Set<String> SENSITIVE_KEYS = Set.of("authtoken", "password");

    /**
     * The credential key names recognised in text. Matched without a leading anchor so a compound key
     * ending in a credential word ({@code "userPassword"}) is covered too, while a key that merely
     * starts with one ({@code "passwordPolicyName"}) is not: the closing quote or the separator has to
     * follow the word immediately.
     */
    private static final String CREDENTIAL_KEY = "(?:auth_?token|password)";

    /**
     * JSON form, quoted string value: {@code "authToken":"abc"} becomes
     * {@code "authToken":"***MASKED***"}. Both source quotes are consumed and re-emitted by
     * {@link #JSON_REPLACEMENT}, so the result still parses. Escapes inside the value are honoured, so
     * a value containing {@code \"} is masked whole. An empty value is excluded by the {@code (?!")}
     * guard - there is no credential to hide in {@code "authToken":""}.
     *
     * <p>Trap: the value body must stay the unrolled, possessive
     * {@code [^"\\]*+(?:\\[\s\S][^"\\]*+)*+}. The obvious {@code (?:[^"\\]|\\.)+} is an alternation
     * inside a quantifier, which java.util.regex recurses per character and throws
     * {@link StackOverflowError} on a value of a few KB - inside error handling.
     */
    private static final Pattern SENSITIVE_JSON_STRING = Pattern.compile(
            "(?i)(" + CREDENTIAL_KEY + "\"\\s*:\\s*)\"(?!\")[^\"\\\\]*+(?:\\\\[\\s\\S][^\"\\\\]*+)*+\"");

    /**
     * JSON form, unquoted number value: {@code "password":1234} is masked to the QUOTED sentinel,
     * because an unquoted {@code ***MASKED***} is not JSON - so this one field changes type from
     * number to string. The trailing lookahead ensures the whole number was consumed, so a non-number
     * that merely starts with digits is not half-replaced.
     *
     * <p>Literal {@code null}, {@code true}, {@code false} and object/array values match none of these
     * patterns and are left exactly as they are. A credential nested INSIDE such an object is still
     * masked, by these same patterns, as its own {@code "key":"value"} pair.
     */
    private static final Pattern SENSITIVE_JSON_NUMBER = Pattern.compile(
            "(?i)(" + CREDENTIAL_KEY + "\"\\s*:\\s*)"
                    + "-?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?(?:[eE][+-]?[0-9]+)?(?=\\s*(?:[,}\\]]|$))");

    /**
     * Free-text form: {@code RequestInfo.toString()} renders {@code authToken=...}, and a log line or a
     * truncated echo can carry {@code password: ...} with no JSON around it. Here the sentinel replaces
     * the value bare, because there are no quotes to preserve.
     *
     * <p>A value that is a bare JSON literal is excluded: those shapes are handled by the two JSON
     * patterns above or intentionally left alone.
     *
     * <p>Known gap: a credential inside an ALREADY-escaped nested JSON
     * string ({@code "body":"{\"authToken\":\"...\"}"}) is not matched by any of these patterns. The
     * structure walk masks such a payload before it is serialized, so this only affects text that
     * arrives double-serialized.
     */
    private static final Pattern SENSITIVE_TEXT = Pattern.compile(
            "(?i)(" + CREDENTIAL_KEY + "\"?\\s*[:=]\\s*\"?)"
                    + "(?!(?:null|true|false)\\b)([^\"{\\[,;}\\s\\]][^\",;}\\s\\]]*+)");

    /** Keeps the JSON value quoted, so a masked document still parses. */
    private static final String JSON_REPLACEMENT =
            "$1" + Matcher.quoteReplacement("\"" + MASKED + "\"");

    /** Free text has no quotes to preserve. */
    private static final String TEXT_REPLACEMENT = "$1" + Matcher.quoteReplacement(MASKED);

    /**
     * Depth cap for the structure walk. Bulk payloads are shallow (request -> entity list -> entity ->
     * nested address/additionalFields), so this is only a guard against a pathological structure.
     */
    private static final int MAX_MASK_DEPTH = 8;

    private SensitiveDataMasker() {
        // static utility
    }

    /**
     * Masks {@code authToken=...} / {@code "password":"..."} inside free text. Null/empty-safe: a null
     * or empty input is returned as-is, and text with nothing to mask is returned as the very same
     * instance.
     *
     * <p>Trap: the two JSON passes must run BEFORE the free-text pass, which emits an unquoted
     * sentinel. Already-malformed input is masked best-effort and stays malformed.
     */
    public static String maskText(String text) {
        if (ObjectUtils.isEmpty(text)) {
            return text;
        }
        String masked = SENSITIVE_JSON_STRING.matcher(text).replaceAll(JSON_REPLACEMENT);
        masked = SENSITIVE_JSON_NUMBER.matcher(masked).replaceAll(JSON_REPLACEMENT);
        return SENSITIVE_TEXT.matcher(masked).replaceAll(TEXT_REPLACEMENT);
    }

    /**
     * Copy-on-write masking of {@link #SENSITIVE_KEYS} anywhere in a map/list structure, plus
     * {@link #maskText} inside string values (a token can be embedded in a free-text field such
     * as {@code additionalFields}). Every other value is carried through unchanged so a replay is
     * equivalent to the original, and the caller's own structure is never mutated - a value with
     * nothing to mask is returned as the very same instance.
     */
    public static Object maskStructure(Object value) {
        return maskValue(value, 0);
    }

    /** Whether the given key names a credential whose value must be masked. Null-safe. */
    public static boolean isSensitiveKey(String key) {
        return key != null && SENSITIVE_KEYS.contains(key.toLowerCase(Locale.ROOT));
    }

    @SuppressWarnings("unchecked")
    private static Object maskValue(Object value, int depth) {
        if (depth > MAX_MASK_DEPTH) {
            return value;
        }
        if (value instanceof Map) {
            Map<String, Object> source = (Map<String, Object>) value;
            Map<String, Object> copy = null;
            for (Map.Entry<String, Object> entry : source.entrySet()) {
                Object original = entry.getValue();
                Object replacement = isSensitiveKey(entry.getKey()) && !ObjectUtils.isEmpty(original)
                        ? MASKED
                        : maskValue(original, depth + 1);
                if (replacement != original) {
                    if (copy == null) {
                        copy = new LinkedHashMap<>(source);
                    }
                    copy.put(entry.getKey(), replacement);
                }
            }
            return copy == null ? source : copy;
        }
        if (value instanceof List) {
            List<Object> source = (List<Object>) value;
            List<Object> copy = null;
            for (int i = 0; i < source.size(); i++) {
                Object original = source.get(i);
                Object replacement = maskValue(original, depth + 1);
                if (replacement != original) {
                    if (copy == null) {
                        copy = new ArrayList<>(source);
                    }
                    copy.set(i, replacement);
                }
            }
            return copy == null ? source : copy;
        }
        if (value instanceof String) {
            return maskText((String) value);
        }
        return value;
    }
}
