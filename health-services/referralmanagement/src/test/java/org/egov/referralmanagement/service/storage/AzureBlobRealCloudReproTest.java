package org.egov.referralmanagement.service.storage;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.common.StorageSharedKeyCredential;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.egov.referralmanagement.config.ReferralManagementConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.UUID;

/**
 * Reproduce the prod "Trying to output second root, &lt;ArrayNode&gt;" error
 * against the REAL Azure Blob storage account used by ng-central-dev.
 *
 * <p>Gated on {@code -Dazure.real.repro=true} so it never runs by accident.
 * Requires: {@code -Dazure.account}, {@code -Dazure.key},
 * {@code -Dazure.container} (defaults to "egov-rainmaker").
 *
 * <p>If this test throws with the exact prod message, we've isolated the
 * trigger to the Azure Blob write pipeline (not the writer code itself).
 * The stack trace will point at the specific line inside the Azure SDK or
 * Jackson that fires the second-root check.
 */
@EnabledIfSystemProperty(named = "azure.real.repro", matches = "true")
class AzureBlobRealCloudReproTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private AzureBlobStorageBackend backend;
    private BlobServiceClient blobServiceClient;
    private String container;

    @BeforeEach
    void setup() throws Exception {
        String account   = required("azure.account");
        String key       = required("azure.key");
        container        = System.getProperty("azure.container", "egov-rainmaker");
        String endpoint  = "https://" + account + ".blob.core.windows.net";

        ReferralManagementConfiguration cfg = new ReferralManagementConfiguration();
        setField(cfg, "azureAccountName",       account);
        setField(cfg, "azureAccountKey",        key);
        setField(cfg, "azureContainerName",     container);
        setField(cfg, "azureBlobEndpoint",      endpoint);
        setField(cfg, "presignedUrlExpirySecs", 300);

        blobServiceClient = new BlobServiceClientBuilder()
                .endpoint(endpoint)
                .credential(new StorageSharedKeyCredential(account, key))
                .buildClient();

        backend = new AzureBlobStorageBackend();
        setField(backend, "config",             cfg);
        setField(backend, "blobServiceClient",  blobServiceClient);
    }

    private static String required(String prop) {
        String v = System.getProperty(prop);
        if (v == null || v.isBlank()) {
            throw new IllegalStateException("Missing required system property: " + prop);
        }
        return v;
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    @Test
    @DisplayName("16 parallel writers × 1000 individuals each — mirrors prod ward-pool concurrency")
    void reproConcurrent() throws Exception {
        final int threads = 16;
        final int rowsPerThread = 1000;
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(threads);
        java.util.List<java.util.concurrent.Future<?>> futures = new java.util.ArrayList<>();
        for (int t = 0; t < threads; t++) {
            final int threadIdx = t;
            futures.add(pool.submit(() -> {
                String key = "repro-concurrent/" + UUID.randomUUID() + "/t" + threadIdx + ".ndjson.gz";
                DownsyncStorageBackend.StreamResult r = backend.stream(key, gzip -> {
                    JsonGenerator gen = objectMapper.getFactory().createGenerator(gzip);
                    gen.disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET);
                    long count = 0;
                    try {
                        for (int i = 0; i < rowsPerThread; i++) {
                            try { writeOneIndividual(gen, threadIdx * 1000 + i); }
                            catch (Exception e) { throw new IOException(e); }
                            count++;
                        }
                    } finally { gen.flush(); gen.close(); }
                    return count;
                });
                try {
                    BlobClient bc = blobServiceClient.getBlobContainerClient(container).getBlobClient(key);
                    bc.deleteIfExists();
                } catch (Exception ignored) {}
                return r.rowCount();
            }));
        }
        pool.shutdown();
        int okCount = 0, failCount = 0;
        for (int t = 0; t < threads; t++) {
            try {
                Object v = futures.get(t).get(3, java.util.concurrent.TimeUnit.MINUTES);
                System.out.println("Thread " + t + " OK rows=" + v);
                okCount++;
            } catch (Exception e) {
                System.err.println("Thread " + t + " FAILED — " + rootCauseMessage(e));
                e.printStackTrace(System.err);
                failCount++;
            }
        }
        if (failCount > 0) {
            org.junit.jupiter.api.Assertions.fail(failCount + " of " + threads + " parallel writers failed");
        }
        System.out.println("All " + okCount + "/" + threads + " parallel writers OK");
    }

    private String rootCauseMessage(Throwable t) {
        while (t.getCause() != null) t = t.getCause();
        return t.getClass().getSimpleName() + ": " + t.getMessage();
    }

    @Test
    @DisplayName("Write 1000 individuals against REAL Azure — full stack trace on failure")
    void repro() {
        String key = "repro/" + UUID.randomUUID() + "/individuals.ndjson.gz";
        try {
            DownsyncStorageBackend.StreamResult r = backend.stream(key, gzip -> {
                JsonGenerator gen = objectMapper.getFactory().createGenerator(gzip);
                gen.disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET);
                long count = 0;
                try {
                    for (int i = 0; i < 1000; i++) {
                        try { writeOneIndividual(gen, i); }
                        catch (Exception e) { throw new IOException(e); }
                        count++;
                    }
                } finally {
                    gen.flush();
                    gen.close();
                }
                return count;
            });
            System.out.println("Result: rowCount=" + r.rowCount() + " fileSize=" + r.fileSize());
            // Best-effort cleanup — don't fail if it's slow
            try {
                BlobClient bc = blobServiceClient.getBlobContainerClient(container).getBlobClient(key);
                bc.deleteIfExists();
            } catch (Exception ignored) {}
        } catch (Throwable e) {
            System.err.println("=== REPRO CAUGHT — Full stack trace ===");
            e.printStackTrace(System.err);
            Throwable t = e;
            int depth = 0;
            while (t != null) {
                System.err.println("\n[depth " + depth++ + "] " + t.getClass().getName() + " → " + t.getMessage());
                t = t.getCause();
            }
            org.junit.jupiter.api.Assertions.fail("Reproduced upload failure — see stderr for full trace: " + e.getMessage());
        }
    }

    private void writeOneIndividual(JsonGenerator gen, int i) throws Exception {
        gen.writeStartObject();
        gen.writeStringField("_t", "INDIVIDUAL");
        gen.writeStringField("id",                "id-" + i);
        gen.writeStringField("clientReferenceId", "cri-" + i);
        gen.writeStringField("tenantId",          "ba");

        // additionalFields — jsonb ObjectNode
        gen.writeFieldName("additionalFields");
        gen.writeRawValue(objectMapper.createObjectNode().put("k", "v").toString());

        // address — jsonb ArrayNode
        gen.writeFieldName("address");
        gen.writeRawValue(objectMapper.createArrayNode().toString());

        // identifiers — decrypted String path
        ArrayNode arr = objectMapper.createArrayNode();
        ObjectNode idNode = objectMapper.createObjectNode();
        idNode.put("identifierId",   "value-" + i);
        idNode.put("identifierType", "AADHAAR");
        arr.add(idNode);
        gen.writeFieldName("identifiers");
        gen.writeRawValue(arr.toString());

        // skills — empty ArrayNode
        gen.writeFieldName("skills");
        gen.writeRawValue("[]");

        gen.writeEndObject();
        gen.writeRaw('\n');
    }
}
