package org.egov.excelingestion.security;

import org.egov.common.exception.InvalidTenantIdFormatException;
import org.egov.common.utils.MultiStateInstanceUtil;
import org.egov.excelingestion.config.ErrorConstants;
import org.egov.excelingestion.repository.GeneratedFileRepository;
import org.egov.excelingestion.repository.ProcessingRepository;
import org.egov.tracer.model.CustomException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Security tests for the two Java SQLi/schema hardenings exercised in excel-ingestion:
 *
 *  R9 — services-common 2.9.1 MultiStateInstanceUtil.replaceSchemaPlaceholder validates each
 *       tenantId dot-segment (^[a-zA-Z_][a-zA-Z0-9_]{0,62}$) in central-instance mode before it is
 *       interpolated as the SQL schema identifier. (Running this on Spring Boot 3.2.2 also smoke-tests
 *       that the 2.9.1 jar — built on Boot 3.4.5 — loads/executes fine here.)
 *
 *  R4 — ProcessingRepository.validateAdditionalDetailsKey rejects any additionalDetails map KEY that
 *       is not ^[A-Za-z0-9_.-]+$ before it is concatenated into the JSONB ->>'key' SQL path.
 */
class SchemaAndKeyValidationSecurityTest {

    // ──────────────────────────────────────────────────────────────────────
    // R9 · MultiStateInstanceUtil tenantId→schema validation (central instance)
    // ──────────────────────────────────────────────────────────────────────
    private MultiStateInstanceUtil centralUtil() {
        // (stateLevelTenantIdLength, isEnvironmentCentralInstance, stateSchemaIndexPositionInTenantId)
        return new MultiStateInstanceUtil(1, Boolean.TRUE, 1);
    }

    private static final String Q =
            "SELECT * FROM " + MultiStateInstanceUtil.SCHEMA_REPLACE_STRING + ".eg_ex_in_processing WHERE tenantId = :tenantId";

    @Test
    @DisplayName("R9 POSITIVE: valid tenantIds are not rejected and the {schema} placeholder is substituted")
    void r9_acceptsValidTenants() {
        MultiStateInstanceUtil util = centralUtil();
        for (String tenant : new String[]{"ng.kaduna", "mz.maputo", "pb.amritsar"}) {
            String out = assertDoesNotThrow(() -> util.replaceSchemaPlaceholder(Q, tenant),
                    "valid tenantId must not be rejected: " + tenant);
            assertFalse(out.contains(MultiStateInstanceUtil.SCHEMA_REPLACE_STRING),
                    "placeholder should be substituted for " + tenant);
        }
    }

    @Test
    @DisplayName("R9 NEGATIVE: malicious / malformed tenantId segments are rejected (InvalidTenantIdFormatException)")
    void r9_rejectsInjection() {
        MultiStateInstanceUtil util = centralUtil();
        String[] bad = {
                "t WHERE false UNION SELECT 1 --",
                "x) z WHERE false UNION SELECT 1 --",
                "ng; DROP TABLE eg_ex_in_processing",
                "ng'",
                "ng\"",
                "ng--",
                "ng/*c*/",
                "ng kaduna",          // space
                "ng-kaduna",          // hyphen not allowed in a schema segment
                "ng.ka duna",         // bad SECOND segment
                "1ng",                // starts with digit
                "ng\n",               // newline
                "ng\t",               // tab
                "pg_catalog$x",       // dollar
                "ngа",                // unicode (Armenian)
        };
        for (String t : bad) {
            assertThrows(InvalidTenantIdFormatException.class,
                    () -> util.replaceSchemaPlaceholder(Q, t),
                    "should reject malicious tenantId: " + t);
        }
    }

    @Test
    @DisplayName("R9: 2.9.1 validates tenantId in NON-central mode too (defense-in-depth) — malicious rejected, valid accepted")
    void r9_nonCentralAlsoValidates() {
        // Discovered via this test: 2.9.1 runs validateTenantId as the FIRST instruction of
        // replaceSchemaPlaceholder, BEFORE the central/non-central branch — so a malformed tenantId
        // is now rejected even in non-central mode (where tenantId would not have reached SQL anyway).
        // Legitimate tenantIds still pass, so existing non-central flows are unaffected.
        MultiStateInstanceUtil nonCentral = new MultiStateInstanceUtil(1, Boolean.FALSE, 1);
        assertThrows(InvalidTenantIdFormatException.class,
                () -> nonCentral.replaceSchemaPlaceholder(Q, "t WHERE false UNION SELECT 1 --"));
        assertDoesNotThrow(() -> nonCentral.replaceSchemaPlaceholder(Q, "ng.kaduna"));
    }

    // ──────────────────────────────────────────────────────────────────────
    // R4 · additionalDetails map-KEY charset allowlist (^[A-Za-z0-9_.-]+$)
    // ──────────────────────────────────────────────────────────────────────
    private Method validateKeyMethod() throws Exception {
        Method m = ProcessingRepository.class.getDeclaredMethod("validateAdditionalDetailsKey", String.class);
        m.setAccessible(true);
        return m;
    }

    private void invokeKey(String key) throws Exception {
        ProcessingRepository repo = new ProcessingRepository(null, null, null);
        try {
            validateKeyMethod().invoke(repo, key);
        } catch (InvocationTargetException e) {
            // unwrap so the test sees the real (CustomException) thrown by the method
            if (e.getCause() instanceof RuntimeException) throw (RuntimeException) e.getCause();
            throw e;
        }
    }

    @Test
    @DisplayName("R4 POSITIVE: legitimate additionalDetails keys (letters/digits/_ . -) are accepted")
    void r4_acceptsValidKeys() {
        String[] ok = {"campaignName", "SOCIAL_CATEGORY", "social.category", "field-1", "a_b.c-d", "ABC123", "_x", "a.b.c"};
        for (String k : ok) {
            assertDoesNotThrow(() -> invokeKey(k), "valid key must be accepted: " + k);
        }
    }

    // R4 TWIN — GeneratedFileRepository has the byte-identical validateAdditionalDetailsKey (the
    // audit flagged BOTH excel repos as the same JSONB map-key sink). Exercise it too.
    private void invokeGenKey(String key) throws Exception {
        GeneratedFileRepository repo = new GeneratedFileRepository(null, null, null);
        Method m = GeneratedFileRepository.class.getDeclaredMethod("validateAdditionalDetailsKey", String.class);
        m.setAccessible(true);
        try {
            m.invoke(repo, key);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException) throw (RuntimeException) e.getCause();
            throw e;
        }
    }

    @Test
    @DisplayName("R4 TWIN (GeneratedFileRepository): valid keys accepted; injection keys rejected")
    void r4_generatedFileRepositoryTwin() {
        for (String ok : new String[]{"campaignName", "social.category", "field-1", "a_b.c-d", "SOCIAL_CATEGORY"}) {
            assertDoesNotThrow(() -> invokeGenKey(ok), "valid key: " + ok);
        }
        for (String bad : new String[]{"a' OR '1'='1", "x'); DROP TABLE eg_ex_in_generated_file; --", "a b", "a;b", "a)b", "a=b", "a\nb", "", null}) {
            CustomException ex = assertThrows(CustomException.class, () -> invokeGenKey(bad), "should reject: " + bad);
            assertEquals(ErrorConstants.INVALID_SEARCH_KEY, ex.getCode());
        }
    }

    @Test
    @DisplayName("R4 NEGATIVE: injection / out-of-charset keys throw CustomException(INVALID_SEARCH_KEY)")
    void r4_rejectsInjectionKeys() {
        String[] bad = {
                "a' = 'a' OR '1'='1",
                "x'); DROP TABLE eg_ex_in_processing; --",
                "key' UNION SELECT password FROM users --",
                "a b",          // space
                "a;b",          // semicolon
                "a)b",          // paren
                "a'b",          // single quote
                "a\"b",         // double quote
                "a=b",          // equals
                "a/*c*/b",      // comment
                "a\nb",         // newline
                "a\tb",         // tab
                "a b OR 1=1",
                "",             // empty
                null,           // null
        };
        for (String k : bad) {
            CustomException ex = assertThrows(CustomException.class, () -> invokeKey(k),
                    "should reject malicious key: " + k);
            assertEquals(ErrorConstants.INVALID_SEARCH_KEY, ex.getCode(), "code for key: " + k);
        }
    }
}
