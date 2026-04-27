package org.egov.referralmanagement.service;

import lombok.extern.slf4j.Slf4j;
import org.egov.referralmanagement.config.ReferralManagementConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPOutputStream;

/**
 * Central S3 facade for downsync file generation.
 * Owns multipart upload, file-size lookup, and presigned URL generation.
 * Nothing else should import S3Client or S3Presigner directly.
 */
@Service
@Slf4j
public class DownsyncS3Service {

    private static final int PART_SIZE_BYTES = 5 * 1024 * 1024;

    @Autowired private S3Client s3Client;
    @Autowired private S3Presigner s3Presigner;
    @Autowired private ReferralManagementConfiguration config;

    // ── Types ─────────────────────────────────────────────────────────────────

    @FunctionalInterface
    public interface StreamWriter {
        long write(GZIPOutputStream gzip) throws IOException;
    }

    public record S3Result(long rowCount, Long fileSize) {}

    // ── Upload ────────────────────────────────────────────────────────────────

    /**
     * Streams data produced by {@code writer} into a multipart upload at {@code s3Key}.
     * If the writer produces 0 rows, aborts the upload, deletes any stale object, and
     * returns S3Result(0, null). Otherwise returns the row count and compressed file size.
     */
    public S3Result streamToS3(String s3Key, StreamWriter writer) {
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
                return new S3Result(0, null);
            }

            partOut.uploadFinalPart();
            s3Client.completeMultipartUpload(
                    CompleteMultipartUploadRequest.builder()
                            .bucket(config.getS3Bucket()).key(s3Key).uploadId(uploadId)
                            .multipartUpload(CompletedMultipartUpload.builder().parts(parts).build())
                            .build());

            Long fileSize = headObjectSize(s3Key);
            log.debug("S3 upload complete: {} ({} rows, {} bytes)", s3Key, rowCount, fileSize);
            return new S3Result(rowCount, fileSize);

        } catch (Exception e) {
            if (uploadId != null) {
                try {
                    s3Client.abortMultipartUpload(AbortMultipartUploadRequest.builder()
                            .bucket(config.getS3Bucket()).key(s3Key).uploadId(uploadId).build());
                } catch (Exception ignored) {}
            }
            throw new RuntimeException("S3 upload failed: " + s3Key, e);
        }
    }

    // ── Presigned URL ─────────────────────────────────────────────────────────

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

    // ── Private ───────────────────────────────────────────────────────────────

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

        S3PartOutputStream(S3Client s3, String bucket, String key,
                           String uploadId, List<CompletedPart> parts) {
            this.s3 = s3; this.bucket = bucket; this.key = key;
            this.uploadId = uploadId; this.parts = parts;
        }

        @Override public void write(int b)                      { buffer.write(b);         flushIfFull(); }
        @Override public void write(byte[] b, int off, int len) { buffer.write(b, off, len); flushIfFull(); }

        private void flushIfFull() { if (buffer.size() >= PART_SIZE_BYTES) flushPart(); }

        void uploadFinalPart() { if (buffer.size() > 0) flushPart(); }

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
