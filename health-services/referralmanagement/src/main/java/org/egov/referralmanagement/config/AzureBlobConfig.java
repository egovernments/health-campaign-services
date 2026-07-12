package org.egov.referralmanagement.config;

import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.common.StorageSharedKeyCredential;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Azure Blob Storage client bean. Only registered when
 * {@code egov.downsync.storage.backend=azure}. When backend=s3, this
 * @Configuration is skipped entirely — no Azure SDK code path is loaded.
 *
 * <p>Endpoint resolves to {@code https://<accountName>.blob.core.windows.net}
 * unless overridden by {@code azure.blob.endpoint} (needed for Azure Gov,
 * Azure China, or Azurite dev emulator).
 */
@Configuration
@ConditionalOnProperty(name = "egov.downsync.storage.backend", havingValue = "azure")
public class AzureBlobConfig {

    @Bean
    public BlobServiceClient blobServiceClient(ReferralManagementConfiguration config) {
        String endpoint = config.getAzureBlobEndpoint();
        if (endpoint == null || endpoint.isBlank()) {
            endpoint = "https://" + config.getAzureAccountName() + ".blob.core.windows.net";
        }
        return new BlobServiceClientBuilder()
                .endpoint(endpoint)
                .credential(new StorageSharedKeyCredential(
                        config.getAzureAccountName(),
                        config.getAzureAccountKey()))
                .buildClient();
    }
}
