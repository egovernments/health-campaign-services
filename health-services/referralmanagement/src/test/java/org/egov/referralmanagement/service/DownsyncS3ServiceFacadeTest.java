package org.egov.referralmanagement.service;

import org.egov.referralmanagement.service.storage.DownsyncStorageBackend;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link DownsyncS3Service} is preserved under its historical name as a thin
 * facade over {@link DownsyncStorageBackend}. All calls flow through the
 * backend; result records are translated back to the legacy {@code S3Result}
 * shape so callers (DownsyncFileGenService, DownsyncPregenService) don't
 * change.
 *
 * <p>These tests use an inline test-double for the backend so we're purely
 * exercising the delegation logic — no Spring, no real S3 or Azure client.
 */
class DownsyncS3ServiceFacadeTest {

    private static void setField(Object target, String name, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    /** Records inputs and returns configured outputs so we can assert delegation. */
    private static class StubBackend implements DownsyncStorageBackend {
        String lastKey;
        StreamWriter lastWriter;
        StreamResult nextStreamResult;
        String nextPresignResult;
        String name = "stub";

        @Override public String backendName() { return name; }
        @Override public void validateStartupConfig() {}
        @Override public StreamResult stream(String key, StreamWriter writer) {
            this.lastKey = key;
            this.lastWriter = writer;
            return nextStreamResult;
        }
        @Override public String presign(String key) {
            this.lastKey = key;
            return nextPresignResult;
        }
    }

    // ── streamToS3 delegates and translates result shape ─────────────────────

    @Test
    @DisplayName("streamToS3 passes key + writer through and returns S3Result mirroring StreamResult")
    void streamToS3_delegates() throws Exception {
        StubBackend backend = new StubBackend();
        backend.nextStreamResult = new DownsyncStorageBackend.StreamResult(42L, 1024L);

        DownsyncS3Service facade = new DownsyncS3Service();
        setField(facade, "backend", backend);

        DownsyncS3Service.S3Result out =
                facade.streamToS3("kw/x/y.ndjson.gz", gzip -> 42L);

        assertNotNull(out);
        assertEquals(42L,   out.rowCount());
        assertEquals(1024L, out.fileSize());
        assertEquals("kw/x/y.ndjson.gz", backend.lastKey);
        assertNotNull(backend.lastWriter);
    }

    @Test
    @DisplayName("streamToS3 propagates zero-row result (rowCount=0, fileSize=null)")
    void streamToS3_zeroRows() throws Exception {
        StubBackend backend = new StubBackend();
        backend.nextStreamResult = new DownsyncStorageBackend.StreamResult(0L, null);

        DownsyncS3Service facade = new DownsyncS3Service();
        setField(facade, "backend", backend);

        DownsyncS3Service.S3Result out = facade.streamToS3("k", gzip -> 0L);
        assertEquals(0L, out.rowCount());
        assertNull(out.fileSize());
    }

    // ── presign delegates and passes null through unchanged ──────────────────

    @Test
    @DisplayName("presign returns whatever the backend produced")
    void presign_delegates() throws Exception {
        StubBackend backend = new StubBackend();
        backend.nextPresignResult = "https://example.com/presigned?x=y";

        DownsyncS3Service facade = new DownsyncS3Service();
        setField(facade, "backend", backend);

        assertEquals("https://example.com/presigned?x=y", facade.presign("k"));
        assertEquals("k", backend.lastKey);
    }

    @Test
    @DisplayName("presign returns null when backend returns null (offline / error)")
    void presign_nullFromBackendPropagates() throws Exception {
        StubBackend backend = new StubBackend();
        backend.nextPresignResult = null;

        DownsyncS3Service facade = new DownsyncS3Service();
        setField(facade, "backend", backend);

        assertNull(facade.presign("some/key"));
    }

    // ── backendName exposed for logging/metrics ──────────────────────────────

    @Test
    @DisplayName("backendName reflects the wired backend")
    void backendName_exposed() throws Exception {
        StubBackend s3 = new StubBackend(); s3.name = "s3";
        StubBackend az = new StubBackend(); az.name = "azure";

        DownsyncS3Service facade1 = new DownsyncS3Service();
        setField(facade1, "backend", s3);
        assertEquals("s3", facade1.backendName());

        DownsyncS3Service facade2 = new DownsyncS3Service();
        setField(facade2, "backend", az);
        assertEquals("azure", facade2.backendName());
    }

    // ── Writer lambda from caller is forwarded intact ────────────────────────

    @Test
    @DisplayName("Writer lambda supplied by caller is forwarded to the backend, not re-wrapped")
    void writerForwardingIsTransparent() throws Exception {
        StubBackend backend = new StubBackend();
        backend.nextStreamResult = new DownsyncStorageBackend.StreamResult(1L, 10L);

        DownsyncS3Service facade = new DownsyncS3Service();
        setField(facade, "backend", backend);

        DownsyncStorageBackend.StreamWriter myWriter = gzip -> 1L;
        facade.streamToS3("k", myWriter);

        // The backend received the SAME writer instance the caller supplied — no
        // adapter layer that could accidentally alter row-count semantics.
        assertTrue(backend.lastWriter == myWriter, "Writer should be forwarded by reference");
    }
}
