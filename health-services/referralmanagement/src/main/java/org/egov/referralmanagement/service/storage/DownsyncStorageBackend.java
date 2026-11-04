package org.egov.referralmanagement.service.storage;

import java.io.IOException;
import java.util.zip.GZIPOutputStream;

/**
 * Object-storage abstraction for downsync file generation. Two implementations:
 * {@link AwsS3StorageBackend} (AWS S3 or S3-compatible endpoint) and
 * {@link AzureBlobStorageBackend} (Azure Blob Storage). Exactly one bean is
 * created per pod, selected by {@code egov.downsync.storage.backend}.
 *
 * <p>Semantics — every implementation MUST:
 * <ul>
 *   <li>Wrap {@link #stream(String, StreamWriter)} in a multi-part or block
 *       upload appropriate to the backend. Total bytes are gzipped by the
 *       caller.</li>
 *   <li>If the writer produces 0 rows, roll back any in-progress upload and
 *       return {@code StreamResult(0, null)} — leaving no orphaned object on
 *       the backend.</li>
 *   <li>Guarantee no side effects visible to a subsequent read until the
 *       write completes atomically (S3 multipart complete / Azure commitBlockList).</li>
 *   <li>Presign for the configured expiry seconds; return {@code null} on any
 *       presign failure (callers treat null as "signal offline; try later").</li>
 * </ul>
 */
public interface DownsyncStorageBackend {

    /**
     * Backend identifier — matches the {@code egov.downsync.storage.backend}
     * property value that selected this implementation. Used in log lines and
     * in the startup banner so operators can confirm which backend is live.
     */
    String backendName();

    /**
     * Called from {@link StorageBackendValidator} at application startup.
     * Implementations MUST throw a RuntimeException with a specific, human-
     * readable message if any required config value is missing or blank, so
     * the pod fails fast instead of accepting write requests it can't fulfil.
     */
    void validateStartupConfig();

    /**
     * Streams gzipped NDJSON produced by {@code writer} to the given key on
     * the backend. Behaviour:
     * <ul>
     *   <li>writer returns 0 → upload aborted, no object left behind →
     *       result rowCount=0, fileSize=null.</li>
     *   <li>writer returns N &gt; 0 → object committed at {@code key} →
     *       result rowCount=N, fileSize=&lt;bytes after gzip&gt;.</li>
     *   <li>writer or backend throws → RuntimeException propagates. Any
     *       partial upload is aborted before rethrowing.</li>
     * </ul>
     */
    StreamResult stream(String key, StreamWriter writer);

    /**
     * Returns a temporary URL a mobile client can GET the object with,
     * expiring after {@code egov.downsync.presigned.url.expiry.secs}. Returns
     * null on failure (do not throw — callers handle null gracefully).
     */
    String presign(String key);

    // ── Types shared by both backends ────────────────────────────────────────

    @FunctionalInterface
    interface StreamWriter {
        /** Return the number of records written; 0 means "no data — abort". */
        long write(GZIPOutputStream gzip) throws IOException;
    }

    record StreamResult(long rowCount, Long fileSize) {}
}
