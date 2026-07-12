package org.egov.referralmanagement.service;

import lombok.extern.slf4j.Slf4j;
import org.egov.referralmanagement.service.storage.DownsyncStorageBackend;
import org.egov.referralmanagement.service.storage.DownsyncStorageBackend.StreamResult;
import org.egov.referralmanagement.service.storage.DownsyncStorageBackend.StreamWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Public facade for downsync uploads + presigned URLs. Delegates to whichever
 * {@link DownsyncStorageBackend} was picked at startup via
 * {@code egov.downsync.storage.backend}. The class is kept under its
 * historical name (DownsyncS3Service) so existing callers don't need to move,
 * but internally it's storage-backend-agnostic — the concrete SDK (AWS S3
 * or Azure Blob) is chosen at bean-wire time.
 *
 * <p>Nothing else in the codebase should import S3Client, S3Presigner,
 * BlobServiceClient, or any other backend-specific type. Route all IO
 * through this class.
 */
@Service
@Slf4j
public class DownsyncS3Service {

    @Autowired private DownsyncStorageBackend backend;

    /**
     * Compat alias for callers that hold a reference to this class's original
     * result record. Keeps the return type stable across the refactor.
     */
    public record S3Result(long rowCount, Long fileSize) {}

    /** Compat alias — {@link DownsyncStorageBackend.StreamWriter} is the source of truth. */
    @FunctionalInterface
    public interface Writer extends StreamWriter {}

    /**
     * Streams gzipped NDJSON to the configured backend at {@code key}. Empty
     * writes (rowCount == 0) leave no artefact behind on the backend and
     * return S3Result(0, null). See {@link DownsyncStorageBackend#stream}.
     *
     * <p>Named {@code streamToS3} for backward compatibility with existing
     * call sites; despite the name, the actual target may be Azure Blob.
     */
    public S3Result streamToS3(String key, StreamWriter writer) {
        StreamResult r = backend.stream(key, writer);
        return new S3Result(r.rowCount(), r.fileSize());
    }

    /**
     * Generates a temporary URL a mobile client can GET the object with,
     * expiring per {@code egov.downsync.presigned.url.expiry.secs}. Returns
     * null on failure (callers already handle null).
     */
    public String presign(String key) {
        return backend.presign(key);
    }

    /** Backend identifier ({@code s3} or {@code azure}) — for logging/metrics. */
    public String backendName() {
        return backend.backendName();
    }
}
