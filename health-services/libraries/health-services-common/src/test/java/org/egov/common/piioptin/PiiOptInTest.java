package org.egov.common.piioptin;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.egov.common.pii.BulkEnvelopeService;
import org.egov.common.pii.EncCircuitBreaker;
import org.egov.common.pii.EncryptionDecryptionUtil;
import org.egov.common.pii.EnvelopeEncClient;
import org.egov.common.pii.HttpClientTimeoutConfig;
import org.egov.common.pii.PiiEncryptionProperties;
import org.egov.common.pii.PiiHasher;
import org.egov.common.producer.Producer;
import org.egov.encryption.EncryptionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.autoconfigure.web.client.RestTemplateAutoConfiguration;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * The classes in org.egov.common.pii are opt-in. Household, project, referralmanagement, stock and facility scan all
 * of org.egov, individual and worker-registry scan org.egov.common; the scan is what this test repeats. The test lives
 * outside org.egov.common.pii on purpose, so the scan does not pick up the test classes.
 */
class PiiOptInTest {

    @Configuration
    @ComponentScan(basePackages = "org.egov.common.pii")
    static class ScanConfig {
    }

    private ApplicationContextRunner runner() {
        return new ApplicationContextRunner()
                // a real service has Spring Boot's RestTemplateAutoConfiguration; HttpClientTimeoutConfig (flag on) builds on it
                .withConfiguration(AutoConfigurations.of(PropertyPlaceholderAutoConfiguration.class, RestTemplateAutoConfiguration.class))
                .withUserConfiguration(ScanConfig.class)
                .withBean("objectMapper", ObjectMapper.class, ObjectMapper::new)
                // individual has a second ObjectMapper too: an unqualified one is picked by its parameter name
                .withBean("redisObjectMapper", ObjectMapper.class, ObjectMapper::new)
                .withBean(Producer.class, () -> mock(Producer.class))
                .withBean(EncryptionService.class, () -> mock(EncryptionService.class));
    }

    /** What a service sets when it turns the flag on (the required ones: no default in the library). */
    private ApplicationContextRunner enabled() {
        return runner().withPropertyValues("egov.pii.encryption.enabled=true",
                "state.level.tenant.id=dev",
                "egov.enc.host=http://localhost:1", "egov.enc.encrypt.endpoint=/e", "egov.enc.decrypt.endpoint=/d",
                "egov.bulk.dlq.topic=some-bulk-dlq-topic");
    }

    @Test
    @DisplayName("flag absent: none of the new beans exists, and no new property is needed")
    void flagAbsentCreatesNothing() {
        runner().run(context -> {
            assertTrue(context.getStartupFailure() == null, String.valueOf(context.getStartupFailure()));
            assertEquals(0, context.getBeansOfType(PiiEncryptionProperties.class).size());
            assertEquals(0, context.getBeansOfType(EncCircuitBreaker.class).size());
            assertEquals(0, context.getBeansOfType(EnvelopeEncClient.class).size());
            assertEquals(0, context.getBeansOfType(BulkEnvelopeService.class).size());
            assertEquals(0, context.getBeansOfType(PiiHasher.class).size());
            assertEquals(0, context.getBeansOfType(EncryptionDecryptionUtil.class).size());
            assertEquals(0, context.getBeansOfType(HttpClientTimeoutConfig.class).size());
        });
    }

    @Test
    @DisplayName("flag false, and hash flag alone: still none of the new beans")
    void flagFalseCreatesNothing() {
        for (String[] values : new String[][]{{"egov.pii.encryption.enabled=false"},
                {"egov.pii.hash.enabled=true", "egov.pii.hash.secret=s"},
                {"egov.pii.encryption.enabled=false", "egov.pii.hash.enabled=true", "egov.pii.hash.secret=s"}}) {
            runner().withPropertyValues(values).run(context -> {
                assertTrue(context.getStartupFailure() == null, String.valueOf(context.getStartupFailure()));
                assertEquals(0, context.getBeansOfType(PiiEncryptionProperties.class).size());
                assertEquals(0, context.getBeansOfType(EncCircuitBreaker.class).size());
                assertEquals(0, context.getBeansOfType(EnvelopeEncClient.class).size());
                assertEquals(0, context.getBeansOfType(BulkEnvelopeService.class).size());
                assertEquals(0, context.getBeansOfType(PiiHasher.class).size());
                assertEquals(0, context.getBeansOfType(EncryptionDecryptionUtil.class).size());
                assertEquals(0, context.getBeansOfType(HttpClientTimeoutConfig.class).size());
            });
        }
    }

    @Test
    @DisplayName("flag on (enc-client on the class path): properties, breaker, envelope client, bulk service and crypto util exist; hasher does not")
    void flagOnCreatesBeansButNotHasher() {
        enabled().run(context -> {
            assertTrue(context.getStartupFailure() == null, String.valueOf(context.getStartupFailure()));
            assertNotNull(context.getBean(PiiEncryptionProperties.class));
            assertNotNull(context.getBean(EncCircuitBreaker.class));
            assertNotNull(context.getBean(EnvelopeEncClient.class));
            assertNotNull(context.getBean(BulkEnvelopeService.class));
            assertNotNull(context.getBean(EncryptionDecryptionUtil.class));
            assertNotNull(context.getBean(HttpClientTimeoutConfig.class));
            assertEquals(0, context.getBeansOfType(PiiHasher.class).size(), "hasher needs egov.pii.hash.enabled=true");
        });
    }

    @Test
    @DisplayName("defaults equal the old individual defaults")
    void defaults() {
        enabled().run(context -> {
            PiiEncryptionProperties p = context.getBean(PiiEncryptionProperties.class);
            assertEquals("dev", p.getStateLevelTenantId());
            assertEquals(2000, p.getEncConnectTimeoutMs());
            assertEquals(10000, p.getEncReadTimeoutMs());
            assertEquals(5, p.getEncCircuitFailureThreshold());
            assertEquals(30000L, p.getEncCircuitOpenMs());
            assertEquals("ENCRYPTED_ONLY", p.getBulkEnvelopeDlqMode());
            assertEquals("some-bulk-dlq-topic", p.getBulkDlqTopic());
            assertEquals(5000L, p.getBulkDlqRetryBackoffMs());
            assertEquals(3600000L, p.getBulkDlqRetryMaxBackoffMs());
        });
    }

    @Test
    @DisplayName("flag on and hash flag on with a secret: hasher exists, lower-case fields are read from the property")
    void hasherNeedsSecondFlag() {
        enabled().withPropertyValues("egov.pii.hash.enabled=true", "egov.pii.hash.secret=test-secret",
                "egov.pii.hash.lowercase.fields=givenName").run(context -> {
            assertTrue(context.getStartupFailure() == null, String.valueOf(context.getStartupFailure()));
            PiiHasher hasher = context.getBean(PiiHasher.class);
            assertEquals(hasher.hash("givenName", "ASHA"), hasher.hash("givenName", "asha"));
            // same value as the migration script vector (secret 'test-secret')
            assertEquals("9a5e57c6b645f7117681290078b829af061c6a414d81bfcc174a06e3234f63a6", hasher.hash("givenName", " Asha "));
        });
    }

    @Test
    @DisplayName("hasher on but secret blank: start-up fails (fail-fast kept)")
    void blankSecretFailsStart() {
        enabled().withPropertyValues("egov.pii.hash.enabled=true").run(context -> {
            assertNotNull(context.getStartupFailure());
            Throwable root = context.getStartupFailure();
            while (root.getCause() != null) {
                root = root.getCause();
            }
            assertInstanceOf(IllegalStateException.class, root);
            assertTrue(root.getMessage().contains("egov.pii.hash.secret"), root.getMessage());
        });
    }

    @Test
    @DisplayName("flag on but enc-client not on the class path: the crypto util is skipped, the rest still exists")
    void noEncClientSkipsUtil() {
        enabled().withClassLoader(new FilteredClassLoader("org.egov.encryption")).run(context -> {
            assertTrue(context.getStartupFailure() == null, String.valueOf(context.getStartupFailure()));
            assertEquals(0, context.getBeansOfType(EncryptionDecryptionUtil.class).size());
            assertNotNull(context.getBean(BulkEnvelopeService.class));
        });
    }
}
