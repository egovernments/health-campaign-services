package org.egov.individual.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.egov.common.pii.BulkEnvelopeService;
import org.egov.common.pii.EncCircuitBreaker;
import org.egov.common.pii.EncryptionDecryptionUtil;
import org.egov.common.pii.EnvelopeEncClient;
import org.egov.common.pii.PiiEncryptionProperties;
import org.egov.common.pii.PiiHasher;
import org.egov.common.producer.Producer;
import org.egov.encryption.EncryptionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.autoconfigure.web.client.RestTemplateAutoConfiguration;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.SystemEnvironmentPropertySource;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * The shared PII classes read egov.* property names; individual's application.properties maps its existing
 * individual.* names into them. This loads the real application.properties and the real library beans, so it shows
 * that the defaults and the existing env vars (PII_HASH_SECRET, INDIVIDUAL_BULK_ENVELOPE_DLQ_MODE, ...) still reach them.
 */
class PiiPropertyMappingTest {

    @Configuration
    @ComponentScan(basePackages = "org.egov.common.pii")
    static class ScanLikeMainConfiguration {
    }

    private ApplicationContextRunner runner(Map<String, Object> env) {
        return new ApplicationContextRunner()
                .withInitializer(ctx -> ctx.getEnvironment().getPropertySources()
                        .addFirst(new SystemEnvironmentPropertySource("fakeSystemEnvironment", env)))
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withConfiguration(AutoConfigurations.of(PropertyPlaceholderAutoConfiguration.class, RestTemplateAutoConfiguration.class))
                .withUserConfiguration(ScanLikeMainConfiguration.class)
                .withBean("objectMapper", ObjectMapper.class, ObjectMapper::new)
                // individual has a second ObjectMapper too: an unqualified one is picked by its parameter name
                .withBean("redisObjectMapper", ObjectMapper.class, ObjectMapper::new)
                .withBean(Producer.class, () -> mock(Producer.class))
                .withBean(EncryptionService.class, () -> mock(EncryptionService.class));
    }

    @Test
    @DisplayName("no env vars except the secret: the library gets the same defaults individual had before the move")
    void defaultsAreTheOldIndividualDefaults() {
        runner(Map.of("PII_HASH_SECRET", "some-secret")).run(context -> {
            assertTrue(context.getStartupFailure() == null, String.valueOf(context.getStartupFailure()));
            PiiEncryptionProperties p = context.getBean(PiiEncryptionProperties.class);
            assertEquals("ENCRYPTED_ONLY", p.getBulkEnvelopeDlqMode());
            assertEquals("individual-bulk-dlq-topic", p.getBulkDlqTopic());
            assertEquals(5000L, p.getBulkDlqRetryBackoffMs());
            assertEquals(3600000L, p.getBulkDlqRetryMaxBackoffMs());
            assertEquals(2000, p.getEncConnectTimeoutMs());
            assertEquals(10000, p.getEncReadTimeoutMs());
            assertEquals(5, p.getEncCircuitFailureThreshold());
            assertEquals(30000L, p.getEncCircuitOpenMs());
            assertEquals("some-secret", p.getHashSecret());
            // shared HTTP client timeouts (library HttpClientTimeoutConfig): the old individual defaults
            assertEquals("2000", context.getEnvironment().getProperty("egov.http.connect.timeout.ms"));
            assertEquals("60000", context.getEnvironment().getProperty("egov.http.read.timeout.ms"));
            assertNotNull(context.getBean(RestTemplateBuilder.class));
            assertEquals(java.util.List.of("givenName"), p.getHashLowercaseFields());
            assertEquals("default", p.getStateLevelTenantId());
            // the flags individual sets: every library bean exists, hasher included
            assertNotNull(context.getBean(EncCircuitBreaker.class));
            assertNotNull(context.getBean(EnvelopeEncClient.class));
            assertNotNull(context.getBean(BulkEnvelopeService.class));
            assertNotNull(context.getBean(EncryptionDecryptionUtil.class));
            PiiHasher hasher = context.getBean(PiiHasher.class);
            assertEquals(hasher.hash("givenName", "ASHA"), hasher.hash("givenName", "asha"));
        });
    }

    @Test
    @DisplayName("every existing individual env var still reaches the library setting")
    void existingEnvVarsStillWork() {
        Map<String, Object> env = Map.ofEntries(
                Map.entry("PII_HASH_SECRET", "env-secret"),
                Map.entry("INDIVIDUAL_BULK_ENVELOPE_DLQ_MODE", "ALLOW_PLAIN"),
                Map.entry("INDIVIDUAL_BULK_DLQ_TOPIC", "env-dlq-topic"),
                Map.entry("INDIVIDUAL_BULK_DLQ_RETRY_BACKOFF_MS", "111"),
                Map.entry("INDIVIDUAL_BULK_DLQ_RETRY_MAX_BACKOFF_MS", "222"),
                Map.entry("INDIVIDUAL_HTTP_CONNECT_TIMEOUT_MS", "666"),
                Map.entry("INDIVIDUAL_HTTP_READ_TIMEOUT_MS", "777"),
                Map.entry("INDIVIDUAL_ENC_CONNECT_TIMEOUT_MS", "333"),
                Map.entry("INDIVIDUAL_ENC_READ_TIMEOUT_MS", "444"),
                Map.entry("INDIVIDUAL_ENC_CIRCUIT_FAILURE_THRESHOLD", "7"),
                Map.entry("INDIVIDUAL_ENC_CIRCUIT_OPEN_MS", "555"));
        runner(env).run(context -> {
            assertTrue(context.getStartupFailure() == null, String.valueOf(context.getStartupFailure()));
            PiiEncryptionProperties p = context.getBean(PiiEncryptionProperties.class);
            assertEquals("env-secret", p.getHashSecret());
            assertEquals("ALLOW_PLAIN", p.getBulkEnvelopeDlqMode());
            assertEquals("env-dlq-topic", p.getBulkDlqTopic());
            assertEquals(111L, p.getBulkDlqRetryBackoffMs());
            assertEquals(222L, p.getBulkDlqRetryMaxBackoffMs());
            assertEquals(333, p.getEncConnectTimeoutMs());
            assertEquals(444, p.getEncReadTimeoutMs());
            assertEquals(7, p.getEncCircuitFailureThreshold());
            assertEquals(555L, p.getEncCircuitOpenMs());
            assertEquals("666", context.getEnvironment().getProperty("egov.http.connect.timeout.ms"));
            assertEquals("777", context.getEnvironment().getProperty("egov.http.read.timeout.ms"));
        });
    }

    @Test
    @DisplayName("PII_HASH_SECRET not set: start-up fails, as it did before the move")
    void missingSecretFailsStart() {
        runner(Map.of("PII_HASH_SECRET", "")).run(context -> {  // blank on purpose: a real exported secret must not leak in
            assertNotNull(context.getStartupFailure());
            Throwable root = context.getStartupFailure();
            while (root.getCause() != null) {
                root = root.getCause();
            }
            assertTrue(root.getMessage().contains("egov.pii.hash.secret"), root.getMessage());
        });
    }
}
