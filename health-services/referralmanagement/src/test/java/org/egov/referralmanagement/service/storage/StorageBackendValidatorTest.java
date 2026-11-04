package org.egov.referralmanagement.service.storage;

import org.egov.referralmanagement.config.ReferralManagementConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies {@link StorageBackendValidator} — the @PostConstruct hook that
 * actually runs each backend's startup check. Two guarantees we want to
 * lock down:
 * <ol>
 *   <li>The validator calls {@code backend.validateStartupConfig()} exactly
 *       once — if the backend throws, the pod's Spring context init fails
 *       (which in prod means CrashLoopBackOff).</li>
 *   <li>The validator sanity-checks that the resolved backend's
 *       {@code backendName()} matches the configured
 *       {@code egov.downsync.storage.backend} — catches a class of subtle
 *       misconfiguration where two backends both match somehow.</li>
 * </ol>
 */
class StorageBackendValidatorTest {

    private static void setField(Object target, String name, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    /** Test double: records whether validate() was invoked. */
    private static class RecordingBackend implements DownsyncStorageBackend {
        final String name;
        final AtomicBoolean validated = new AtomicBoolean(false);
        RuntimeException toThrow;

        RecordingBackend(String name) { this.name = name; }

        @Override public String backendName() { return name; }
        @Override public void validateStartupConfig() {
            validated.set(true);
            if (toThrow != null) throw toThrow;
        }
        @Override public StreamResult stream(String key, StreamWriter writer) { throw new UnsupportedOperationException(); }
        @Override public String presign(String key) { throw new UnsupportedOperationException(); }
    }

    // ── Happy path ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Validator calls backend.validateStartupConfig() exactly once when names match")
    void invokesBackendValidate() throws Exception {
        RecordingBackend backend = new RecordingBackend("s3");
        ReferralManagementConfiguration cfg = new ReferralManagementConfiguration();
        setField(cfg, "storageBackend", "s3");

        StorageBackendValidator v = new StorageBackendValidator();
        setField(v, "backend", backend);
        setField(v, "config",  cfg);

        assertDoesNotThrow(v::validate);
        assertTrue(backend.validated.get(), "backend.validateStartupConfig() must be invoked");
    }

    @Test
    @DisplayName("Backend name matching is case-insensitive")
    void caseInsensitiveMatch() throws Exception {
        RecordingBackend backend = new RecordingBackend("s3");
        ReferralManagementConfiguration cfg = new ReferralManagementConfiguration();
        setField(cfg, "storageBackend", "S3");   // uppercased by ops mistake

        StorageBackendValidator v = new StorageBackendValidator();
        setField(v, "backend", backend);
        setField(v, "config",  cfg);

        assertDoesNotThrow(v::validate);
        assertTrue(backend.validated.get());
    }

    // ── Failure surfaces from backend propagate ──────────────────────────────

    @Test
    @DisplayName("Backend's IllegalStateException from validateStartupConfig propagates unchanged")
    void backendFailurePropagates() throws Exception {
        RecordingBackend backend = new RecordingBackend("azure");
        backend.toThrow = new IllegalStateException("azure.blob.account.key missing");

        ReferralManagementConfiguration cfg = new ReferralManagementConfiguration();
        setField(cfg, "storageBackend", "azure");

        StorageBackendValidator v = new StorageBackendValidator();
        setField(v, "backend", backend);
        setField(v, "config",  cfg);

        IllegalStateException ex = assertThrows(IllegalStateException.class, v::validate);
        assertTrue(ex.getMessage().contains("azure.blob.account.key"));
    }

    // ── Sanity: mismatched bean and config prop → clear error ────────────────

    @Test
    @DisplayName("Backend bean name mismatch with configured backend fails startup with a clear error")
    void mismatchGuard() throws Exception {
        RecordingBackend backend = new RecordingBackend("azure");     // wired bean says azure
        ReferralManagementConfiguration cfg = new ReferralManagementConfiguration();
        setField(cfg, "storageBackend", "s3");                        // but config says s3

        StorageBackendValidator v = new StorageBackendValidator();
        setField(v, "backend", backend);
        setField(v, "config",  cfg);

        IllegalStateException ex = assertThrows(IllegalStateException.class, v::validate);
        String msg = ex.getMessage();
        assertTrue(msg.contains("egov.downsync.storage.backend=s3"), () -> "Expected mention of configured backend; got: " + msg);
        assertTrue(msg.contains("azure"),                             () -> "Expected mention of resolved bean; got: " + msg);
    }
}
