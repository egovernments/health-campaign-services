package org.egov.referralmanagement.service.storage;

import org.egov.referralmanagement.config.ReferralManagementConfiguration;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.URI;
import java.net.URL;
import java.util.UUID;
import java.util.zip.GZIPInputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end integration test: {@link AwsS3StorageBackend} talking to a real
 * S3-compatible endpoint (MinIO running at localhost:9010, bucket
 * {@code downsync-test}).
 *
 * <p>This test is gated on the system property {@code minio.integration=true}
 * so CI doesn't fail when Docker isn't available. Run locally with:
 * <pre>mvn test -Dminio.integration=true \
 *   -Dtest=AwsS3StorageBackendMinioIntegrationTest</pre>
 *
 * <p>Setup expected before running:
 * <pre>
 * docker run -d --name minio-local -p 9010:9000 -p 9011:9001 \
 *   -e MINIO_ROOT_USER=minioadmin -e MINIO_ROOT_PASSWORD=minioadmin123 \
 *   minio/minio:latest server /data --console-address ":9001"
 * docker run --rm --network host \
 *   -e MC_HOST_minio=http://minioadmin:minioadmin123@localhost:9010 \
 *   minio/mc:latest mb minio/downsync-test
 * </pre>
 *
 * <p>What we prove — the four assertions that map 1:1 to the backend contract:
 * <ol>
 *   <li>A non-empty upload lands in the bucket and reports its byte size.</li>
 *   <li>The uploaded object round-trips through gzip and equals the source bytes.</li>
 *   <li>A zero-row upload is aborted; no orphan object remains at that key.</li>
 *   <li>A presigned URL is generated and can actually be used to GET the object
 *       without any auth header (proves the SAS-like semantics work).</li>
 * </ol>
 */
@EnabledIfSystemProperty(named = "minio.integration", matches = "true")
class AwsS3StorageBackendMinioIntegrationTest {

    private static final String ENDPOINT   = "http://localhost:9010";
    private static final String BUCKET     = "downsync-test";
    private static final String REGION     = "us-east-1";     // MinIO ignores region content
    private static final String ACCESS_KEY = "minioadmin";
    private static final String SECRET_KEY = "minioadmin123";

    private AwsS3StorageBackend backend;
    private String key;

    @BeforeEach
    void setup() throws Exception {
        ReferralManagementConfiguration cfg = new ReferralManagementConfiguration();
        setField(cfg, "s3Bucket",           BUCKET);
        setField(cfg, "s3Region",           REGION);
        setField(cfg, "s3Endpoint",         ENDPOINT);
        setField(cfg, "s3PresignEndpoint",  ENDPOINT);
        setField(cfg, "s3AccessKey",        ACCESS_KEY);
        setField(cfg, "s3SecretKey",        SECRET_KEY);
        setField(cfg, "presignedUrlExpirySecs", 300);

        S3Client s3Client = S3Client.builder()
                .region(Region.of(REGION))
                .endpointOverride(URI.create(ENDPOINT))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(ACCESS_KEY, SECRET_KEY)))
                .forcePathStyle(true)
                .build();
        S3Presigner presigner = S3Presigner.builder()
                .region(Region.of(REGION))
                .endpointOverride(URI.create(ENDPOINT))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(ACCESS_KEY, SECRET_KEY)))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build();

        backend = new AwsS3StorageBackend();
        setField(backend, "config",      cfg);
        setField(backend, "s3Client",    s3Client);
        setField(backend, "s3Presigner", presigner);

        key = "it/" + UUID.randomUUID() + "/sample.ndjson.gz";
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    @Test
    @DisplayName("End-to-end: 3-row NDJSON upload lands in MinIO and round-trips via presigned URL")
    void uploadRoundTrip() throws Exception {
        // ── stream in three JSON lines ──
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

        // ── assert result ──
        assertEquals(3L, result.rowCount());
        assertNotNull(result.fileSize());
        assertTrue(result.fileSize() > 0, "fileSize should reflect gzipped bytes on disk");

        // ── presign + fetch + decompress ──
        String url = backend.presign(key);
        assertNotNull(url, "presign must return a URL");
        assertTrue(url.startsWith(ENDPOINT), () -> "URL should point at the S3 endpoint: " + url);

        try (InputStream in = new URL(url).openStream();
             GZIPInputStream gz = new GZIPInputStream(in);
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
    @DisplayName("Zero-row upload aborts the multipart — no orphan object left in the bucket")
    void zeroRowUploadAborted() throws Exception {
        DownsyncStorageBackend.StreamResult result = backend.stream(key, gzip -> 0L);
        assertEquals(0L, result.rowCount());
        assertNull(result.fileSize());

        // Presign should still succeed (S3 lets you presign any URL), but a GET
        // against it MUST return 404 — the object was never committed.
        String url = backend.presign(key);
        assertNotNull(url);
        int status = probeStatus(url);
        assertEquals(404, status,
                () -> "Expected 404 for aborted upload; got " + status + " (URL: " + url + ")");
    }

    @Test
    @DisplayName("backendName is 's3' when wired against S3-compatible endpoint")
    void backendName() {
        assertEquals("s3", backend.backendName());
    }

    private static int probeStatus(String url) throws Exception {
        java.net.HttpURLConnection c = (java.net.HttpURLConnection) new URL(url).openConnection();
        c.setRequestMethod("GET");
        try {
            return c.getResponseCode();
        } finally {
            c.disconnect();
        }
    }

    // Sanity — smoke-test byte equality end-to-end for a tiny known payload.
    @Test
    @DisplayName("Single-byte upload round-trips exactly")
    void oneBytePayload() throws Exception {
        byte[] payload = "x".getBytes();
        backend.stream(key, gzip -> { gzip.write(payload); return 1L; });

        String url = backend.presign(key);
        try (InputStream in = new URL(url).openStream();
             GZIPInputStream gz = new GZIPInputStream(in);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            gz.transferTo(out);
            assertArrayEquals(payload, out.toByteArray());
        }
    }
}
