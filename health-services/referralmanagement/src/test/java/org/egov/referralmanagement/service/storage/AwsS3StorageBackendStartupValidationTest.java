package org.egov.referralmanagement.service.storage;

import org.egov.referralmanagement.config.ReferralManagementConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Startup-validation contract for {@link AwsS3StorageBackend}. When the
 * S3 backend is selected, the pod must refuse to boot unless every required
 * property is set. These tests exercise each mandatory field independently.
 *
 * <p>We avoid a full Spring context here — {@code AwsS3StorageBackend#validate-
 * StartupConfig} only reads getters on {@link ReferralManagementConfiguration},
 * so a plain instance with fields set via reflection is a much faster + tighter
 * test than {@code @SpringBootTest}.
 */
class AwsS3StorageBackendStartupValidationTest {

    private AwsS3StorageBackend backend;
    private ReferralManagementConfiguration config;

    @BeforeEach
    void setup() throws Exception {
        config = new ReferralManagementConfiguration();
        // Seed all required fields with valid values; individual tests then
        // blank one at a time to prove that field is checked.
        setField(config, "s3Bucket",    "downsync-test-bucket");
        setField(config, "s3Region",    "af-south-1");
        setField(config, "s3Endpoint",  "https://s3.af-south-1.amazonaws.com");
        setField(config, "s3AccessKey", "AKIA_EXAMPLE_ACCESS_KEY");
        setField(config, "s3SecretKey", "example-secret-key-value");

        backend = new AwsS3StorageBackend();
        setField(backend, "config", config);
        // s3Client + s3Presigner are not touched by validateStartupConfig(),
        // so they can stay null in these tests.
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    // ── Happy path ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("All required properties present → validateStartupConfig does not throw")
    void allPropertiesPresent_doesNotThrow() {
        assertDoesNotThrow(() -> backend.validateStartupConfig());
    }

    @Test
    @DisplayName("Backend name is 's3'")
    void backendNameIsS3() {
        assertTrue("s3".equals(backend.backendName()));
    }

    // ── Each required property missing → fail fast with a specific message ────

    @Test
    @DisplayName("Missing egov.s3.bucket throws IllegalStateException naming the property")
    void missingBucket() throws Exception {
        setField(config, "s3Bucket", "");
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> backend.validateStartupConfig());
        assertTrue(ex.getMessage().contains("egov.s3.bucket"),
                () -> "Message should reference 'egov.s3.bucket'; got: " + ex.getMessage());
    }

    @Test
    @DisplayName("Missing egov.s3.region throws IllegalStateException naming the property")
    void missingRegion() throws Exception {
        setField(config, "s3Region", "");
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> backend.validateStartupConfig());
        assertTrue(ex.getMessage().contains("egov.s3.region"));
    }

    @Test
    @DisplayName("Missing egov.s3.endpoint throws IllegalStateException naming the property")
    void missingEndpoint() throws Exception {
        setField(config, "s3Endpoint", "");
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> backend.validateStartupConfig());
        assertTrue(ex.getMessage().contains("egov.s3.endpoint"));
    }

    @Test
    @DisplayName("Missing egov.s3.access-key throws IllegalStateException naming the property")
    void missingAccessKey() throws Exception {
        setField(config, "s3AccessKey", "");
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> backend.validateStartupConfig());
        assertTrue(ex.getMessage().contains("egov.s3.access-key"));
    }

    @Test
    @DisplayName("Missing egov.s3.secret-key throws IllegalStateException naming the property")
    void missingSecretKey() throws Exception {
        setField(config, "s3SecretKey", "");
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> backend.validateStartupConfig());
        assertTrue(ex.getMessage().contains("egov.s3.secret-key"));
    }

    // ── Whitespace-only values are treated as missing ─────────────────────────

    @Test
    @DisplayName("Blank (whitespace-only) property is treated as missing")
    void whitespaceOnlyRejected() throws Exception {
        setField(config, "s3Bucket", "   \t  ");
        assertThrows(IllegalStateException.class, () -> backend.validateStartupConfig());
    }

    @Test
    @DisplayName("Null property is treated as missing")
    void nullRejected() throws Exception {
        setField(config, "s3Bucket", null);
        assertThrows(IllegalStateException.class, () -> backend.validateStartupConfig());
    }

    // ── Error message quality — helps operators fix the deployment fast ──────

    @Test
    @DisplayName("Error message includes both the backend name and the missing property")
    void errorMessageIsActionable() throws Exception {
        setField(config, "s3AccessKey", "");
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> backend.validateStartupConfig());
        String msg = ex.getMessage();
        assertTrue(msg.contains("'s3'"),           () -> "Message should mention backend 's3'; got: " + msg);
        assertTrue(msg.contains("egov.s3.access-key"), () -> "Message should name the property; got: " + msg);
    }
}
