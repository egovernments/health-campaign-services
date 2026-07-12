package org.egov.referralmanagement.service.storage;

import lombok.extern.slf4j.Slf4j;
import org.egov.referralmanagement.config.ReferralManagementConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPOutputStream;

/**
 * AWS S3 (and any S3-compatible endpoint like MinIO) backend for downsync
 * uploads. Active when {@code egov.downsync.storage.backend=s3}.
 *
 * <p>Uses the AWS SDK v2 multipart upload primitives. Parts are 5 MiB
 * (S3's minimum), buffered in memory and flushed on threshold or on
 * finalisation. Zero-row writes are aborted so no empty stub object is
 * left behind.
 */
@Component
@ConditionalOnProperty(name = "egov.downsync.storage.backend", havingValue = "s3", matchIfMissing = true)
@Slf4j
public class AwsS3StorageBackend implements DownsyncStorageBackend {

    private static final int PART_SIZE_BYTES = 5 * 1024 * 1024;

    @Autowired private S3Client s3Client;
    @Autowired private S3Presigner s3Presigner;
    @Autowired private ReferralManagementConfiguration config;

    @Override
    public String backendName() { return "s3"; }

    @Override
    public void validateStartupConfig() {
        require("egov.s3.bucket",      config.getS3Bucket());
        require("egov.s3.region",      config.getS3Region());
        require("egov.s3.endpoint",    config.getS3Endpoint());
        require("egov.s3.access-key",  config.getS3AccessKey());
        require("egov.s3.secret-key",  config.getS3SecretKey());
        log.info("AWS S3 backend ready — bucket={} region={} endpoint={}",
                config.getS3Bucket(), config.getS3Region(), config.getS3Endpoint());
    }

    private void require(String propName, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                "Storage backend 's3' selected but required property '" + propName +
                "' is missing or blank. Set it in egov-config / secret and restart.");
        }
    }

    @Override
    public StreamResult stream(String s3Key, StreamWriter writer) {
        abortOrphanedUploads(s3Key);
        String uploadId = null;
        try {
            uploadId = s3Client.createMultipartUpload(
                    CreateMultipartUploadRequest.builder()
                            .bucket(config.getS3Bucket()).key(s3Key)
                            .contentEncoding("gzip").contentType("application/x-ndjson")
                            .build()
            ).uploadId();

            List<CompletedPart> parts = new ArrayList<>();
            S3PartOutputStream partOut = new S3PartOutputStream(
                    s3Client, config.getS3Bucket(), s3Key, uploadId, parts);

            GZIPOutputStream gzip = new GZIPOutputStream(partOut);
            long rowCount = writer.write(gzip);
            gzip.finish();

            if (rowCount == 0) {
                s3Client.abortMultipartUpload(AbortMultipartUploadRequest.builder()
                        .bucket(config.getS3Bucket()).key(s3Key).uploadId(uploadId).build());
                s3Client.deleteObject(DeleteObjectRequest.builder()
                        .bucket(config.getS3Bucket()).key(s3Key).build());
                log.debug("No data for {}, aborted upload", s3Key);
                return new StreamResult(0, null);
            }

            partOut.uploadFinalPart();
            s3Client.completeMultipartUpload(
                    CompleteMultipartUploadRequest.builder()
                            .bucket(config.getS3Bucket()).key(s3Key).uploadId(uploadId)
                            .multipartUpload(CompletedMultipartUpload.builder().parts(parts).build())
                            .build());

            Long fileSize = headObjectSize(s3Key);
            log.debug("S3 upload complete: {} ({} rows, {} bytes)", s3Key, rowCount, fileSize);
            return new StreamResult(rowCount, fileSize);

        } catch (Exception e) {
            if (uploadId != null) {
                try {
                    s3Client.abortMultipartUpload(AbortMultipartUploadRequest.builder()
                            .bucket(config.getS3Bucket()).key(s3Key).uploadId(uploadId).build());
                } catch (Exception ignored) {}
            }
            String cause = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
            throw new RuntimeException("S3 upload failed: " + s3Key + ". Cause: " + cause, e);
        }
    }

    @Override
    public String presign(String s3Key) {
        try {
            PresignedGetObjectRequest req = s3Presigner.presignGetObject(p -> p
                    .signatureDuration(Duration.ofSeconds(config.getPresignedUrlExpirySecs()))
                    .getObjectRequest(r -> r.bucket(config.getS3Bucket()).key(s3Key)));
            return req.url().toString();
        } catch (Exception e) {
            log.error("Failed to presign URL for key {}: {}", s3Key, e.getMessage());
            return null;
        }
    }

    private void abortOrphanedUploads(String s3Key) {
        try {
            ListMultipartUploadsResponse list = s3Client.listMultipartUploads(
                    ListMultipartUploadsRequest.builder()
                            .bucket(config.getS3Bucket()).prefix(s3Key).build());
            for (MultipartUpload u : list.uploads()) {
                if (!u.key().equals(s3Key)) continue;
                s3Client.abortMultipartUpload(AbortMultipartUploadRequest.builder()
                        .bucket(config.getS3Bucket()).key(s3Key).uploadId(u.uploadId()).build());
                log.info("Aborted orphaned multipart upload — key={} uploadId={}", s3Key, u.uploadId());
            }
        } catch (Exception e) {
            log.warn("Could not list/abort orphaned uploads for key={}: {}", s3Key, e.getMessage());
        }
    }

    private Long headObjectSize(String s3Key) {
        try {
            return s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(config.getS3Bucket()).key(s3Key).build()).contentLength();
        } catch (Exception e) {
            log.warn("Could not fetch file size for {}: {}", s3Key, e.getMessage());
            return null;
        }
    }

    // ── S3PartOutputStream ────────────────────────────────────────────────────

    private static class S3PartOutputStream extends OutputStream {
        private final S3Client s3;
        private final String bucket;
        private final String key;
        private final String uploadId;
        private final List<CompletedPart> parts;
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream(PART_SIZE_BYTES + 65536);
        private int partNum = 1;

        S3PartOutputStream(S3Client s3, String bucket, String key, String uploadId, List<CompletedPart> parts) {
            this.s3 = s3; this.bucket = bucket; this.key = key;
            this.uploadId = uploadId; this.parts = parts;
        }

        @Override public void write(int b)                      { buffer.write(b);           flushIfFull(); }
        @Override public void write(byte[] b, int off, int len) { buffer.write(b, off, len); flushIfFull(); }

        private void flushIfFull() { if (buffer.size() >= PART_SIZE_BYTES) flushPart(); }
        void uploadFinalPart()    { if (buffer.size() > 0) flushPart(); }

        private void flushPart() {
            byte[] data = buffer.toByteArray();
            UploadPartResponse resp = s3.uploadPart(
                    UploadPartRequest.builder()
                            .bucket(bucket).key(key).uploadId(uploadId).partNumber(partNum).build(),
                    RequestBody.fromBytes(data));
            parts.add(CompletedPart.builder().partNumber(partNum++).eTag(resp.eTag()).build());
            buffer.reset();
        }

        @Override public void close() {}
    }
}
