package org.egov.referralmanagement.service.storage;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.models.BlobHttpHeaders;
import com.azure.storage.blob.models.ParallelTransferOptions;
import com.azure.storage.blob.options.BlockBlobOutputStreamOptions;
import com.azure.storage.blob.sas.BlobSasPermission;
import com.azure.storage.blob.sas.BlobServiceSasSignatureValues;
import com.azure.storage.blob.specialized.BlockBlobClient;
import com.azure.storage.common.sas.SasProtocol;
import lombok.extern.slf4j.Slf4j;
import org.egov.referralmanagement.config.ReferralManagementConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.OutputStream;
import java.time.OffsetDateTime;
import java.util.zip.GZIPOutputStream;

/**
 * Azure Blob Storage backend for downsync uploads. Active when
 * {@code egov.downsync.storage.backend=azure}.
 *
 * <p>Uses Azure's block-blob upload primitives (analogous to S3 multipart —
 * stage blocks, commit block list on close). Zero-row writes delete the
 * blob so no orphan is left behind.
 *
 * <p>Presigned reads are Blob SAS URLs (Shared Access Signatures), signed
 * with the account key. Expiry matches
 * {@code egov.downsync.presigned.url.expiry.secs}.
 */
@Component
@ConditionalOnProperty(name = "egov.downsync.storage.backend", havingValue = "azure")
@Slf4j
public class AzureBlobStorageBackend implements DownsyncStorageBackend {

    private static final int BLOCK_SIZE_BYTES = 5 * 1024 * 1024;

    @Autowired private BlobServiceClient blobServiceClient;
    @Autowired private ReferralManagementConfiguration config;

    @Override
    public String backendName() { return "azure"; }

    @Override
    public void validateStartupConfig() {
        require("azure.blob.account.name",   config.getAzureAccountName());
        require("azure.blob.account.key",    config.getAzureAccountKey());
        require("azure.blob.container.name", config.getAzureContainerName());
        // azure.blob.endpoint is optional — defaulted from account name.
        log.info("Azure Blob backend ready — account={} container={} endpoint={}",
                config.getAzureAccountName(), config.getAzureContainerName(), resolveEndpoint());
    }

    private String resolveEndpoint() {
        String ep = config.getAzureBlobEndpoint();
        if (ep == null || ep.isBlank()) {
            return "https://" + config.getAzureAccountName() + ".blob.core.windows.net";
        }
        return ep;
    }

    private void require(String propName, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                "Storage backend 'azure' selected but required property '" + propName +
                "' is missing or blank. Set it in egov-config / secret and restart.");
        }
    }

    @Override
    public StreamResult stream(String key, StreamWriter writer) {
        BlobContainerClient container = blobServiceClient
                .getBlobContainerClient(config.getAzureContainerName());
        BlockBlobClient blob = container.getBlobClient(key).getBlockBlobClient();

        BlobHttpHeaders headers = new BlobHttpHeaders()
                .setContentEncoding("gzip")
                .setContentType("application/x-ndjson");
        BlockBlobOutputStreamOptions opts = new BlockBlobOutputStreamOptions()
                .setHeaders(headers)
                .setParallelTransferOptions(new ParallelTransferOptions()
                        .setBlockSizeLong((long) BLOCK_SIZE_BYTES));

        long rowCount;
        try (OutputStream blobOut = blob.getBlobOutputStream(opts);
             GZIPOutputStream gzip = new GZIPOutputStream(blobOut)) {

            rowCount = writer.write(gzip);
            gzip.finish();
            // Close of gzip flushes the gzip footer; close of blobOut (LIFO)
            // commits the block list to Azure. Both happen when the try-with-
            // resources unwinds after this block.

        } catch (Exception e) {
            // Roll back any partial blob so no stub is left behind.
            try { blob.deleteIfExists(); } catch (Exception ignored) {}
            String cause = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
            throw new RuntimeException("Azure Blob upload failed: " + key + ". Cause: " + cause, e);
        }

        // At this point close() has run and the blob is committed. Handle empty-writer
        // by deleting the blob and returning (0, null). Otherwise HEAD to get size.
        if (rowCount == 0) {
            try { blob.deleteIfExists(); } catch (Exception ignored) {}
            log.debug("No data for {}, deleted empty Azure blob", key);
            return new StreamResult(0, null);
        }

        Long fileSize = headBlobSize(blob);
        log.debug("Azure Blob upload complete: {} ({} rows, {} bytes)", key, rowCount, fileSize);
        return new StreamResult(rowCount, fileSize);
    }

    private Long headBlobSize(BlockBlobClient blob) {
        try {
            return blob.getProperties().getBlobSize();
        } catch (Exception e) {
            log.warn("Could not fetch Azure blob size for {}: {}", blob.getBlobName(), e.getMessage());
            return null;
        }
    }

    @Override
    public String presign(String key) {
        try {
            BlobContainerClient container = blobServiceClient
                    .getBlobContainerClient(config.getAzureContainerName());
            BlobClient blob = container.getBlobClient(key);

            BlobSasPermission perms = new BlobSasPermission().setReadPermission(true);
            OffsetDateTime expiry = OffsetDateTime.now().plusSeconds(config.getPresignedUrlExpirySecs());

            BlobServiceSasSignatureValues values =
                    new BlobServiceSasSignatureValues(expiry, perms).setProtocol(SasProtocol.HTTPS_ONLY);

            String sas = blob.generateSas(values);
            return blob.getBlobUrl() + "?" + sas;

        } catch (Exception e) {
            log.error("Failed to presign SAS URL for blob {}: {}", key, e.getMessage());
            return null;
        }
    }
}
