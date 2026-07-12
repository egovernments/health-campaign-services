package org.egov.referralmanagement.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

/**
 * S3 client + presigner beans. Only registered when
 * {@code egov.downsync.storage.backend=s3} (the default when the property is
 * absent, for backward compatibility). When backend=azure, these beans are
 * NOT created and no AWS credential validation is triggered on startup —
 * lets the pod run without egov-s3 secret being present.
 */
@Configuration
@ConditionalOnProperty(name = "egov.downsync.storage.backend", havingValue = "s3", matchIfMissing = true)
public class S3Config {

    @Bean
    public S3Client s3Client(ReferralManagementConfiguration config) {
        S3ClientBuilder builder = S3Client.builder()
                .region(Region.of(config.getS3Region()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(config.getS3AccessKey(), config.getS3SecretKey())));
        if (StringUtils.hasText(config.getS3Endpoint())) {
            builder.endpointOverride(URI.create(config.getS3Endpoint()))
                   .forcePathStyle(true);
        }
        return builder.build();
    }

    @Bean
    public S3Presigner s3Presigner(ReferralManagementConfiguration config) {
        S3Presigner.Builder builder = S3Presigner.builder()
                .region(Region.of(config.getS3Region()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(config.getS3AccessKey(), config.getS3SecretKey())));
        if (StringUtils.hasText(config.getS3PresignEndpoint())) {
            builder.endpointOverride(URI.create(config.getS3PresignEndpoint()))
                   .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build());
        }
        return builder.build();
    }
}
