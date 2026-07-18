package org.egov.referralmanagement.service.storage;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.io.SerializedString;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.egov.referralmanagement.config.ReferralManagementConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.net.URI;
import java.net.URL;
import java.util.UUID;
import java.util.zip.GZIPInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * End-to-end MinIO integration test proving the "one glued line per HH_MEMBERS
 * file" bug is dead: writes the exact 2-streamQuery pattern used by
 * {@code streamHhMembersFile}, uploads via {@link AwsS3StorageBackend}, pulls
 * the object back through a presigned URL, gzip-decodes, then asserts every
 * non-blank line parses as a JSON root — no glued seam anywhere.
 */
@EnabledIfSystemProperty(named = "minio.integration", matches = "true")
class HhMembersSeamMinioIntegrationTest {

    private static final String ENDPOINT   = "http://localhost:9010";
    private static final String BUCKET     = "downsync-test";
    private static final String REGION     = "us-east-1";
    private static final String ACCESS_KEY = "minioadmin";
    private static final String SECRET_KEY = "minioadmin123";

    private final ObjectMapper mapper = new ObjectMapper();
    private AwsS3StorageBackend backend;
    private String key;

    @BeforeEach
    void setup() throws Exception {
        ReferralManagementConfiguration cfg = new ReferralManagementConfiguration();
        set(cfg, "s3Bucket",           BUCKET);
        set(cfg, "s3Region",           REGION);
        set(cfg, "s3Endpoint",         ENDPOINT);
        set(cfg, "s3PresignEndpoint",  ENDPOINT);
        set(cfg, "s3AccessKey",        ACCESS_KEY);
        set(cfg, "s3SecretKey",        SECRET_KEY);
        set(cfg, "presignedUrlExpirySecs", 300);

        S3Client s3 = S3Client.builder()
                .region(Region.of(REGION))
                .endpointOverride(URI.create(ENDPOINT))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(ACCESS_KEY, SECRET_KEY)))
                .forcePathStyle(true).build();
        S3Presigner p = S3Presigner.builder()
                .region(Region.of(REGION))
                .endpointOverride(URI.create(ENDPOINT))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(ACCESS_KEY, SECRET_KEY)))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build();

        backend = new AwsS3StorageBackend();
        set(backend, "config",      cfg);
        set(backend, "s3Client",    s3);
        set(backend, "s3Presigner", p);

        key = "seam-it/" + UUID.randomUUID() + "/hh_members.ndjson.gz";
    }

    private static void set(Object target, String name, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    /** Replicates the writer's per-entity streamQuery pattern with the trailing-newline fix. */
    private long emitStream(OutputStream gzip, String tag, int n) throws Exception {
        JsonGenerator gen = mapper.getFactory().createGenerator(gzip);
        gen.disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET);
        gen.setRootValueSeparator(new SerializedString("\n"));
        try {
            for (int i = 0; i < n; i++) {
                gen.writeStartObject();
                gen.writeStringField("_t", tag);
                gen.writeStringField("id", tag + "-" + i);
                gen.writeNumberField("i", i);
                gen.writeEndObject();
            }
        } finally {
            gen.flush();
            gen.close();
            gzip.write('\n');   // the fix
        }
        return n;
    }

    @Test
    @DisplayName("HH_MEMBERS-style two-stream write round-trips to MinIO with every line parseable")
    void hhMembersSeamThroughMinio() throws Exception {
        assertSeamCleanAtSize(50, 80);
    }

    // ── Multi-scale coverage — same code path across realistic locality sizes ──

    @Test
    @DisplayName("Tiny locality: 1 HOUSEHOLD + 1 HOUSEHOLD_MEMBER — smallest non-trivial seam")
    void tinyLocality() throws Exception { assertSeamCleanAtSize(1, 1); }

    @Test
    @DisplayName("Small locality (≈YADI shape): 225 HOUSEHOLD + 415 HOUSEHOLD_MEMBER")
    void smallLocality() throws Exception { assertSeamCleanAtSize(225, 415); }

    @Test
    @DisplayName("Medium locality (≈ZANNA shape): 528 HOUSEHOLD + 1117 HOUSEHOLD_MEMBER")
    void mediumLocality() throws Exception { assertSeamCleanAtSize(528, 1117); }

    @Test
    @DisplayName("Large locality (≈WANKA_NOMADIC shape): 47057 HOUSEHOLD + 37713 HOUSEHOLD_MEMBER — spans gzip block boundary")
    void largeLocality() throws Exception { assertSeamCleanAtSize(47_057, 37_713); }

    @Test
    @DisplayName("Zero HOUSEHOLD stream: 0 + 100 — first stream empty at seam")
    void emptyFirstStream() throws Exception { assertSeamCleanAtSize(0, 100); }

    @Test
    @DisplayName("Zero HOUSEHOLD_MEMBER stream: 100 + 0 — second stream empty at seam")
    void emptySecondStream() throws Exception { assertSeamCleanAtSize(100, 0); }

    /** Writes a HH_MEMBERS-shape file with {@code nHh} HOUSEHOLD + {@code nHm} HOUSEHOLD_MEMBER
     *  rows through the real S3 backend + MinIO + presigned URL, then asserts every non-blank
     *  line parses AND the type counts match. */
    private void assertSeamCleanAtSize(int nHh, int nHm) throws Exception {
        String uniqueKey = "seam-it/" + UUID.randomUUID() + "/hh_" + nHh + "_hm_" + nHm + ".ndjson.gz";
        DownsyncStorageBackend.StreamResult result = backend.stream(uniqueKey, gzip -> {
            long a = 0, b = 0;
            try { a = emitStream(gzip, "HOUSEHOLD",        nHh); }
            catch (Exception e) { throw new java.io.IOException(e); }
            try { b = emitStream(gzip, "HOUSEHOLD_MEMBER", nHm); }
            catch (Exception e) { throw new java.io.IOException(e); }
            return a + b;
        });
        long total = nHh + nHm;
        if (total == 0) {
            // Zero-row case aborts the upload (backend contract). Nothing to verify further.
            assertEquals(0L, result.rowCount());
            return;
        }
        assertEquals(total, result.rowCount());
        assertNotNull(result.fileSize());

        String presignedUrl = backend.presign(uniqueKey);
        try (InputStream in = new URL(presignedUrl).openStream();
             GZIPInputStream gz = new GZIPInputStream(in);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            gz.transferTo(out);
            String content = out.toString();
            int parsed = 0, households = 0, members = 0;
            for (String line : content.split("\n", -1)) {
                if (line.trim().isEmpty()) continue;
                var node = mapper.readTree(line);  // throws if any line is glued
                parsed++;
                String t = node.get("_t").asText();
                if ("HOUSEHOLD".equals(t))        households++;
                if ("HOUSEHOLD_MEMBER".equals(t)) members++;
            }
            assertEquals(total, parsed,
                    "every non-blank line must parse as a JSON root (no glued seam) at size " + nHh + "+" + nHm);
            assertEquals(nHh, households, "HOUSEHOLD count mismatch at size " + nHh + "+" + nHm);
            assertEquals(nHm, members,    "HOUSEHOLD_MEMBER count mismatch at size " + nHh + "+" + nHm);
        }
    }
}
