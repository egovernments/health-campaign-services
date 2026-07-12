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
 * Startup-validation contract for {@link AzureBlobStorageBackend}. Parallels
 * {@link AwsS3StorageBackendStartupValidationTest} — the pod must refuse to
 * boot on Azure when any required property is missing.
 *
 * <p>Explicit non-goal: we do NOT validate {@code azure.blob.endpoint} because
 * that property is optional (resolved from the account name).
 */
class AzureBlobStorageBackendStartupValidationTest {

    private AzureBlobStorageBackend backend;
    private ReferralManagementConfiguration config;

    @BeforeEach
    void setup() throws Exception {
        config = new ReferralManagementConfiguration();
        setField(config, "azureAccountName",   "egovprodstorage");
        setField(config, "azureAccountKey",    "very-long-base64-key-value=");
        setField(config, "azureContainerName", "downsync");
        // Leave azureBlobEndpoint blank — it's optional; the backend derives it.

        backend = new AzureBlobStorageBackend();
        setField(backend, "config", config);
        // blobServiceClient is not touched by validateStartupConfig(); stays null.
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    // ── Happy path ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("All required Azure properties present → validateStartupConfig does not throw")
    void allPropertiesPresent_doesNotThrow() {
        assertDoesNotThrow(() -> backend.validateStartupConfig());
    }

    @Test
    @DisplayName("Backend name is 'azure'")
    void backendNameIsAzure() {
        assertTrue("azure".equals(backend.backendName()));
    }

    @Test
    @DisplayName("Optional azure.blob.endpoint left blank → still passes (derived from account name)")
    void endpointIsOptional() {
        assertDoesNotThrow(() -> backend.validateStartupConfig());
    }

    // ── Each required property missing → fail-fast on startup ────────────────

    @Test
    @DisplayName("Missing azure.blob.account.name throws IllegalStateException naming the property")
    void missingAccountName() throws Exception {
        setField(config, "azureAccountName", "");
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> backend.validateStartupConfig());
        assertTrue(ex.getMessage().contains("azure.blob.account.name"),
                () -> "Message should reference 'azure.blob.account.name'; got: " + ex.getMessage());
    }

    @Test
    @DisplayName("Missing azure.blob.account.key throws IllegalStateException naming the property")
    void missingAccountKey() throws Exception {
        setField(config, "azureAccountKey", "");
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> backend.validateStartupConfig());
        assertTrue(ex.getMessage().contains("azure.blob.account.key"));
    }

    @Test
    @DisplayName("Missing azure.blob.container.name throws IllegalStateException naming the property")
    void missingContainerName() throws Exception {
        setField(config, "azureContainerName", "");
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> backend.validateStartupConfig());
        assertTrue(ex.getMessage().contains("azure.blob.container.name"));
    }

    // ── Whitespace / null are treated as missing ─────────────────────────────

    @Test
    @DisplayName("Whitespace-only account name is treated as missing")
    void whitespaceRejected() throws Exception {
        setField(config, "azureAccountName", "  \t");
        assertThrows(IllegalStateException.class, () -> backend.validateStartupConfig());
    }

    @Test
    @DisplayName("Null account key is treated as missing")
    void nullRejected() throws Exception {
        setField(config, "azureAccountKey", null);
        assertThrows(IllegalStateException.class, () -> backend.validateStartupConfig());
    }

    // ── Error message quality ────────────────────────────────────────────────

    @Test
    @DisplayName("Error message names the backend ('azure') and the specific missing property")
    void errorMessageIsActionable() throws Exception {
        setField(config, "azureContainerName", "");
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> backend.validateStartupConfig());
        String msg = ex.getMessage();
        assertTrue(msg.contains("'azure'"),                  () -> "Message should mention backend 'azure'; got: " + msg);
        assertTrue(msg.contains("azure.blob.container.name"),() -> "Message should name the property; got: " + msg);
    }
}
