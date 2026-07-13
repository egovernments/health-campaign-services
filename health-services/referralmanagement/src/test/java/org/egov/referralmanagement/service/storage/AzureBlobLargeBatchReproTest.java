package org.egov.referralmanagement.service.storage;

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
import org.postgresql.util.PGobject;

import java.io.OutputStream;
import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Reproduce the "Trying to output second root, &lt;ArrayNode&gt;" failure by
 * driving the SAME Jackson writer that prod uses through the SAME
 * {@link AzureBlobStorageBackend#stream(String, DownsyncStorageBackend.StreamWriter)}
 * we ship, targeting a real Azure-Blob endpoint (Azurite locally).
 *
 * <p>Prior unit tests wrote to a {@code ByteArrayOutputStream} → GZIP and
 * saw no error. If this test throws with "second root" while the plain-stream
 * ones don't, we've proven the trigger lives at the Azure-stream boundary.
 */
@EnabledIfSystemProperty(named = "azurite.integration", matches = "true")
class AzureBlobLargeBatchReproTest {

    private static final String ACCOUNT_NAME = "devstoreaccount1";
    private static final String ACCOUNT_KEY  = "Eby8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVErCz4I6tq/K1SZFPTOtr/KBHBeksoGMGw==";
    private static final String ENDPOINT     = "http://localhost:10000/devstoreaccount1";
    private static final String CONTAINER    = "downsync-test";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private AzureBlobStorageBackend backend;

    @BeforeEach
    void setup() throws Exception {
        ReferralManagementConfiguration cfg = new ReferralManagementConfiguration();
        setField(cfg, "azureAccountName",       ACCOUNT_NAME);
        setField(cfg, "azureAccountKey",        ACCOUNT_KEY);
        setField(cfg, "azureContainerName",     CONTAINER);
        setField(cfg, "azureBlobEndpoint",      ENDPOINT);
        setField(cfg, "presignedUrlExpirySecs", 300);

        BlobServiceClient blobServiceClient = new BlobServiceClientBuilder()
                .endpoint(ENDPOINT)
                .credential(new StorageSharedKeyCredential(ACCOUNT_NAME, ACCOUNT_KEY))
                .buildClient();
        try { blobServiceClient.createBlobContainer(CONTAINER); } catch (Exception ignored) {}

        backend = new AzureBlobStorageBackend();
        setField(backend, "config",             cfg);
        setField(backend, "blobServiceClient",  blobServiceClient);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    @Test
    @DisplayName("Write 1000 individuals to a live Azure blob — reproduces prod bug if it exists here")
    void reproSecondRootAgainstAzurite() {
        String key = "repro/" + UUID.randomUUID() + "/individuals.ndjson.gz";
        try {
            DownsyncStorageBackend.StreamResult r = backend.stream(key, gzip -> {
                JsonGenerator gen = objectMapper.getFactory().createGenerator(gzip);
                gen.disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET);
                long count = 0;
                try {
                    for (int i = 0; i < 1000; i++) {
                        try { writeOneIndividual(gen, i); }
                        catch (Exception e) { throw new java.io.IOException(e); }
                        count++;
                    }
                } finally {
                    gen.flush();
                    gen.close();
                }
                return count;
            });
            System.out.println("Result: rowCount=" + r.rowCount() + " fileSize=" + r.fileSize());
        } catch (Exception e) {
            System.out.println("=== REPRO CAUGHT ===");
            System.out.println("Top-level: " + e.getClass().getName() + " → " + e.getMessage());
            Throwable t = e.getCause();
            int depth = 1;
            while (t != null) {
                System.out.println("  cause[" + depth++ + "]: " + t.getClass().getName() + " → " + t.getMessage());
                if (t.getStackTrace().length > 0) {
                    for (int k = 0; k < Math.min(6, t.getStackTrace().length); k++) {
                        System.out.println("      at " + t.getStackTrace()[k]);
                    }
                }
                t = t.getCause();
            }
            fail("Reproduced upload failure: " + e.getMessage());
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

        // address — jsonb ArrayNode as PGobject
        PGobject addr = new PGobject();
        addr.setType("jsonb");
        addr.setValue(objectMapper.createArrayNode().toString());
        gen.writeFieldName("address");
        gen.writeRawValue(addr.getValue());

        // identifiers — decrypted String (this is the ArrayNode path we suspect)
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
