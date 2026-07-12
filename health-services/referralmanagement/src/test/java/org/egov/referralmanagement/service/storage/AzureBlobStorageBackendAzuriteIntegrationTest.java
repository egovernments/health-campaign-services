package org.egov.referralmanagement.service.storage;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.common.StorageSharedKeyCredential;
import org.egov.referralmanagement.config.ReferralManagementConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.util.UUID;
import java.util.zip.GZIPInputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end integration test: {@link AzureBlobStorageBackend} talking to
 * Azurite (Microsoft's local Azure Blob emulator). Gated on the system
 * property {@code azurite.integration=true}.
 *
 * <p>Setup expected before running:
 * <pre>
 * docker run -d --name azurite-local -p 10000:10000 \
 *   mcr.microsoft.com/azure-storage/azurite:latest \
 *   azurite-blob --blobHost 0.0.0.0
 * </pre>
 *
 * <p>Azurite's well-known dev credentials (published in Microsoft docs; safe
 * to hard-code — they cannot be used against the real Azure Storage):
 * <ul>
 *   <li>Account name: {@code devstoreaccount1}</li>
 *   <li>Account key:  {@code Eby8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVErCz4I6tq/K1SZFPTOtr/KBHBeksoGMGw==}</li>
 *   <li>Endpoint:     {@code http://localhost:10000/devstoreaccount1}</li>
 * </ul>
 *
 * <p><b>Presign verification note.</b> The backend hard-codes SAS protocol to
 * {@code HTTPS_ONLY} — this is the correct prod behavior (we never want to
 * leak presigned URLs over cleartext). Azurite is HTTP-only, so a raw GET of
 * {@code backend.presign(...)} returns 403 locally. To still exercise the
 * upload contract end-to-end we read the committed blob back through the
 * SDK's {@link BlobClient#downloadStream}, and separately assert that
 * {@code presign()} produces a well-formed SAS URL.
 *
 * <p>What we prove:
 * <ol>
 *   <li>Non-empty upload lands as a committed block-blob and reports its size.</li>
 *   <li>The blob round-trips through gzip and equals the source bytes.</li>
 *   <li>Zero-row upload deletes the blob — no orphan remains.</li>
 *   <li>{@code presign()} returns a SAS URL with the container/key + query string.</li>
 * </ol>
 */
@EnabledIfSystemProperty(named = "azurite.integration", matches = "true")
class AzureBlobStorageBackendAzuriteIntegrationTest {

    private static final String ACCOUNT_NAME = "devstoreaccount1";
    private static final String ACCOUNT_KEY  = "Eby8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVErCz4I6tq/K1SZFPTOtr/KBHBeksoGMGw==";
    private static final String ENDPOINT     = "http://localhost:10000/devstoreaccount1";
    private static final String CONTAINER    = "downsync-test";

    private AzureBlobStorageBackend backend;
    private String key;
    private BlobServiceClient blobServiceClient;

    @BeforeEach
    void setup() throws Exception {
        ReferralManagementConfiguration cfg = new ReferralManagementConfiguration();
        setField(cfg, "azureAccountName",       ACCOUNT_NAME);
        setField(cfg, "azureAccountKey",        ACCOUNT_KEY);
        setField(cfg, "azureContainerName",     CONTAINER);
        setField(cfg, "azureBlobEndpoint",      ENDPOINT);
        setField(cfg, "presignedUrlExpirySecs", 300);

        blobServiceClient = new BlobServiceClientBuilder()
                .endpoint(ENDPOINT)
                .credential(new StorageSharedKeyCredential(ACCOUNT_NAME, ACCOUNT_KEY))
                .buildClient();
        // Ensure container exists — Azurite doesn't auto-create.
        try { blobServiceClient.createBlobContainer(CONTAINER); } catch (Exception ignored) {}

        backend = new AzureBlobStorageBackend();
        setField(backend, "config",             cfg);
        setField(backend, "blobServiceClient",  blobServiceClient);

        key = "it/" + UUID.randomUUID() + "/sample.ndjson.gz";
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    private BlobClient blobRef() {
        return blobServiceClient.getBlobContainerClient(CONTAINER).getBlobClient(key);
    }

    @Test
    @DisplayName("End-to-end: 3-row NDJSON upload lands in Azurite and round-trips through gzip")
    void uploadRoundTrip() throws Exception {
        String[] rows = {
                "{\"_t\":\"INDIVIDUAL\",\"id\":\"a\",\"name\":\"Alice\"}",
                "{\"_t\":\"INDIVIDUAL\",\"id\":\"b\",\"name\":\"Bob\"}",
                "{\"_t\":\"INDIVIDUAL\",\"id\":\"c\",\"name\":\"Carol\"}"
        };
        DownsyncStorageBackend.StreamResult result = backend.stream(key, gzip -> {
            long count = 0;
            for (String row : rows) {
                gzip.write((row + "\n").getBytes());
                count++;
            }
            return count;
        });

        assertEquals(3L, result.rowCount());
        assertNotNull(result.fileSize());
        assertTrue(result.fileSize() > 0, "fileSize must be non-zero for a committed blob");

        // Download the committed blob directly via the SDK (bypasses the
        // HTTPS-only SAS restriction that would 403 against Azurite over HTTP).
        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        blobRef().downloadStream(raw);
        assertEquals(result.fileSize().longValue(), raw.size(),
                "Downloaded byte-count must match reported fileSize");

        try (GZIPInputStream gz = new GZIPInputStream(new ByteArrayInputStream(raw.toByteArray()));
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            gz.transferTo(out);
            String decompressed = out.toString();
            for (String row : rows) {
                assertTrue(decompressed.contains(row),
                        () -> "Round-tripped payload missing row: " + row);
            }
        }
    }

    @Test
    @DisplayName("Zero-row upload deletes the blob — no committed orphan remains")
    void zeroRowUploadDeleted() {
        DownsyncStorageBackend.StreamResult result = backend.stream(key, gzip -> 0L);
        assertEquals(0L, result.rowCount());
        assertNull(result.fileSize());

        assertFalse(blobRef().exists(),
                "Blob must not exist after a zero-row write");
    }

    @Test
    @DisplayName("backendName is 'azure' when wired against Azurite")
    void backendName() {
        assertEquals("azure", backend.backendName());
    }

    @Test
    @DisplayName("Single-byte payload round-trips exactly through Azurite")
    void oneBytePayload() throws Exception {
        byte[] payload = "x".getBytes();
        backend.stream(key, gzip -> { gzip.write(payload); return 1L; });

        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        blobRef().downloadStream(raw);

        try (GZIPInputStream gz = new GZIPInputStream(new ByteArrayInputStream(raw.toByteArray()));
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            gz.transferTo(out);
            assertArrayEquals(payload, out.toByteArray());
        }
    }

    @Test
    @DisplayName("presign() returns a well-formed SAS URL (contract check — 403 over HTTP against Azurite is expected)")
    void presignShapeIsCorrect() {
        // Need a committed blob for presign to succeed.
        backend.stream(key, gzip -> { gzip.write("y".getBytes()); return 1L; });

        String url = backend.presign(key);
        assertNotNull(url,                                             "presign must return a URL");
        assertTrue (url.startsWith(ENDPOINT + "/" + CONTAINER + "/"), () -> "URL should target the container: " + url);
        assertTrue (url.contains("?"),                                 "URL must carry a SAS query string");
        assertTrue (url.contains("sv="),                              "SAS query must include the service version");
        assertTrue (url.contains("sig="),                             "SAS query must include the signature");
        assertTrue (url.contains("spr=https"),
                "Prod SAS must be HTTPS-only — see backend AzureBlobStorageBackend.presign");
    }
}
