package org.egov.common.pii;

import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Keyed hash (HMAC-SHA256) for exact-match search on encrypted PII columns.
 *
 * <p>The cipher text from enc-service cannot be searched safely (a key rotation changes it),
 * so every searchable PII column gets a hash column next to it. The hash is keyed with a
 * per-environment secret: without the secret a phone number or ID cannot be found by
 * hashing every possible value.</p>
 *
 * <p>Format: lowercase hex of HMAC-SHA256(secret, field + ":" + normalised value).
 * The field name is part of the input, so the same value in two different fields gives two
 * different hashes. The migration script (individual/scripts/pii_migrate.py) must use the same rule.</p>
 *
 * <p>Opt-in twice: the bean exists only when {@code egov.pii.encryption.enabled=true} and
 * {@code egov.pii.hash.enabled=true}. Fields listed in {@code egov.pii.hash.lowercase.fields} are lower-cased
 * before hashing; every other field is only trimmed.</p>
 */
@Component
@ConditionalOnProperty(name = {"egov.pii.encryption.enabled", "egov.pii.hash.enabled"}, havingValue = "true")
public class PiiHasher {

    public static final String MOBILE = "mobileNumber";
    public static final String GIVEN_NAME = "givenName";
    public static final String USERNAME = "username";
    public static final String IDENTIFIER_ID = "identifierId";

    private final byte[] secret;
    private final Set<String> lowerCaseFields;

    public PiiHasher(PiiEncryptionProperties properties) {
        String configured = properties.getHashSecret();
        if (StringUtils.isBlank(configured)) {
            // Fail at start-up: without the secret every hash column would be wrong.
            throw new IllegalStateException("egov.pii.hash.secret is not set (services map it from the env var PII_HASH_SECRET); "
                    + "PII search hashes cannot be computed");
        }
        this.secret = configured.getBytes(StandardCharsets.UTF_8);
        this.lowerCaseFields = new HashSet<>();
        if (properties.getHashLowercaseFields() != null) {
            properties.getHashLowercaseFields().stream().map(String::trim).filter(StringUtils::isNotBlank)
                    .forEach(lowerCaseFields::add);
        }
    }

    /** Hash of one value, or null when the value is null/blank (nulls stay null). */
    public String hash(String field, String value) {
        if (StringUtils.isBlank(value)) {
            return null;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            byte[] out = mac.doFinal((field + ":" + normalise(field, value)).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(out);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 is not available", e);
        }
    }

    public List<String> hashAll(String field, List<String> values) {
        if (values == null) {
            return null;
        }
        return values.stream().map(v -> hash(field, v)).filter(Objects::nonNull).collect(Collectors.toList());
    }

    /**
     * Fields in {@code egov.pii.hash.lowercase.fields} (the individual service sets givenName: names match
     * case-insensitively, as the old ILIKE name search did, minus the partial match) are trimmed and lower-cased.
     * Everything else - username included - matches exactly after trimming, as before.
     */
    private String normalise(String field, String value) {
        String trimmed = value.trim();
        if (lowerCaseFields.contains(field)) {
            return trimmed.toLowerCase(Locale.ROOT);
        }
        return trimmed;
    }
}
