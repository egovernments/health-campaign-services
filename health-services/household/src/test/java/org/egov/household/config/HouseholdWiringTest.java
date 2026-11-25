package org.egov.household.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.minidev.json.JSONArray;
import org.egov.common.http.client.ServiceRequestClient;
import org.egov.common.pii.BulkEnvelopeService;
import org.egov.common.pii.EncCircuitBreaker;
import org.egov.common.pii.EncryptionDecryptionUtil;
import org.egov.common.pii.EnvelopeEncClient;
import org.egov.common.pii.PiiEncryptionProperties;
import org.egov.common.pii.PiiHasher;
import org.egov.common.producer.Producer;
import org.egov.encryption.EncryptionService;
import org.egov.encryption.audit.AuditService;
import org.egov.encryption.util.MdmsFetcher;
import org.egov.household.consumer.HouseholdConsumer;
import org.egov.household.service.HouseholdPiiCryptoService;
import org.egov.household.service.HouseholdService;
import org.egov.household.validators.household.HAddressValueValidator;
import org.egov.tracer.model.CustomException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.autoconfigure.web.client.RestTemplateAutoConfiguration;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.util.AopTestUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.lang.reflect.Field;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Starts the real {@link MainConfiguration} (the same scan, imports and exclusions as the running service) with the
 * real application.properties. Only what needs a network or a database is replaced: the JDBC template, and the MDMS
 * fetch enc-client makes when it starts. It proves that the enc-client beans, the shared PII classes and the household
 * beans that use them all wire, and that the bean clashes the new dependency causes are handled.
 */
class HouseholdWiringTest {

    private WebApplicationContextRunner runner(Map<String, Object> env) {
        return new WebApplicationContextRunner()
                .withInitializer(ctx -> ctx.getEnvironment().getPropertySources()
                        .addFirst(new SystemEnvironmentPropertySource("fakeSystemEnvironment", env)))
                .withInitializer(new ConfigDataApplicationContextInitializer())
                // a real service has RestTemplateAutoConfiguration; the library's HttpClientTimeoutConfig builds on it
                .withConfiguration(AutoConfigurations.of(PropertyPlaceholderAutoConfiguration.class, RestTemplateAutoConfiguration.class))
                // The scan also picks up the test helper TestConfiguration, which has a RestTemplateBuilder bean of the same
                // name as the library's. The library's is registered later (the jar comes after test-classes), so it wins;
                // httpClientTimeoutsReachEncClientAndServiceRequestClient asserts that it did.
                .withAllowBeanDefinitionOverriding(true)
                .withUserConfiguration(MainConfiguration.class)
                .withPropertyValues(
                        // no database, Kafka, Redis or Flyway in a unit test
                        "spring.autoconfigure.exclude="
                                + "org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration,"
                                + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                                + "org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration,"
                                + "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration,"
                                + "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,"
                                + "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration",
                        "spring.cache.type=none")
                .withBean(NamedParameterJdbcTemplate.class, () -> mock(NamedParameterJdbcTemplate.class))
                // enc-client reads the security policies from MDMS when it starts; there is no MDMS here
                .withBean("mdmsFetcher", MdmsFetcher.class, () -> {
                    MdmsFetcher fetcher = mock(MdmsFetcher.class);
                    when(fetcher.getSecurityMdmsForFilter(any())).thenReturn(new JSONArray());
                    when(fetcher.getMaskingMdmsForFilter(any())).thenReturn(new JSONArray());
                    return fetcher;
                });
    }

    @Test
    @DisplayName("the service context starts with enc-client and the shared PII classes, and every new bean is there")
    void contextStartsAndNewBeansWire() {
        runner(Map.of()).run(context -> {
            assertNull(context.getStartupFailure(), String.valueOf(context.getStartupFailure()));
            assertNotNull(context.getBean(EncryptionService.class));
            assertNotNull(context.getBean(AuditService.class));
            assertNotNull(context.getBean(EncCircuitBreaker.class));
            assertNotNull(context.getBean(EnvelopeEncClient.class));
            assertNotNull(context.getBean(EncryptionDecryptionUtil.class));
            assertNotNull(context.getBean(BulkEnvelopeService.class));
            assertNotNull(context.getBean(HouseholdPiiCryptoService.class));
            assertNotNull(context.getBean(HouseholdService.class));
            assertNotNull(context.getBean(HouseholdConsumer.class));
            assertEquals(1, context.getBeansOfType(HAddressValueValidator.class).size());
            // household has no searched PII: egov.pii.hash.enabled=false, so there is no hasher
            assertTrue(context.getBeansOfType(PiiHasher.class).isEmpty());
        });
    }

    @Test
    @DisplayName("BulkEnvelopeService gets exactly one Producer; enc-client's own Producer is a separate bean")
    void producerBeansDoNotClash() {
        runner(Map.of()).run(context -> {
            assertNull(context.getStartupFailure(), String.valueOf(context.getStartupFailure()));
            // org.egov.common.producer.Producer: the one the library (BulkEnvelopeService) and the repositories use
            assertEquals(1, context.getBeansOfType(Producer.class).size());
            // org.egov.encryption.producer.Producer: the audit producer enc-client's AuditService needs
            assertEquals(1, context.getBeansOfType(org.egov.encryption.producer.Producer.class).size());
            assertTrue(context.containsBean("encClientProducer"));
            assertSame(context.getBean("producer"), context.getBean(Producer.class));
        });
    }

    @Test
    @DisplayName("the library and enc-client use the household objectMapper, not enc-client's own default one")
    void objectMapperIsTheHouseholdOne() {
        runner(Map.of()).run(context -> {
            assertNull(context.getStartupFailure(), String.valueOf(context.getStartupFailure()));
            ObjectMapper mapper = context.getBean("objectMapper", ObjectMapper.class);
            // enc-client's EncryptionConfiguration.objectMapper() is a plain ObjectMapper that fails on unknown properties;
            // old field apps send fields this service does not know, so the household one must win
            assertFalse(mapper.isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES));
        });
    }

    @Test
    @DisplayName("without the scan exclusions the two Producer classes clash (why MainConfiguration excludes one)")
    void producerClashIsReal() {
        // ConflictingBeanDefinitionException (an IllegalStateException) is not a public class
        IllegalStateException clash = assertThrows(IllegalStateException.class, () -> {
            try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
                context.scan("org.egov.common.producer", "org.egov.encryption.producer");
            }
        });
        assertTrue(clash.getMessage().contains("bean name 'producer'"), clash.getMessage());
    }

    @Test
    @DisplayName("application.properties maps household.* into the library's egov.* names, with the individual defaults")
    void propertiesMapToTheLibrary() {
        runner(Map.of()).run(context -> {
            assertNull(context.getStartupFailure(), String.valueOf(context.getStartupFailure()));
            PiiEncryptionProperties p = context.getBean(PiiEncryptionProperties.class);
            assertEquals("ENCRYPTED_ONLY", p.getBulkEnvelopeDlqMode());
            assertEquals("household-bulk-dlq-topic", p.getBulkDlqTopic());
            assertEquals(5000L, p.getBulkDlqRetryBackoffMs());
            assertEquals(3600000L, p.getBulkDlqRetryMaxBackoffMs());
            assertEquals(2000, p.getEncConnectTimeoutMs());
            assertEquals(10000, p.getEncReadTimeoutMs());
            assertEquals(5, p.getEncCircuitFailureThreshold());
            assertEquals(30000L, p.getEncCircuitOpenMs());
            assertEquals("default", p.getStateLevelTenantId());
            // shared HTTP client timeouts (library HttpClientTimeoutConfig)
            assertEquals("2000", context.getEnvironment().getProperty("egov.http.connect.timeout.ms"));
            assertEquals("60000", context.getEnvironment().getProperty("egov.http.read.timeout.ms"));
            HouseholdConfiguration configuration = context.getBean(HouseholdConfiguration.class);
            assertEquals("HouseholdPiiEncrypt", configuration.getPiiEncryptModel());
            assertEquals("HouseholdPiiDecrypt", configuration.getPiiDecryptModel());
            assertEquals(30000L, configuration.getEncCircuitOpenMs());
            // the DLQ listener's topic key resolves to the household DLQ topic
            assertEquals("household-bulk-dlq-topic", context.getEnvironment().resolvePlaceholders("${egov.bulk.dlq.topic}"));
        });
    }

    @Test
    @DisplayName("household.* env vars reach the library (dlq mode, dlq topic, circuit open period)")
    void envVarsReachTheLibrary() {
        runner(Map.of("HOUSEHOLD_BULK_ENVELOPE_DLQ_MODE", "ALLOW_PLAIN",
                "HOUSEHOLD_BULK_DLQ_TOPIC", "other-dlq",
                "HOUSEHOLD_ENC_CIRCUIT_OPEN_MS", "60000")).run(context -> {
            assertNull(context.getStartupFailure(), String.valueOf(context.getStartupFailure()));
            PiiEncryptionProperties p = context.getBean(PiiEncryptionProperties.class);
            assertEquals("ALLOW_PLAIN", p.getBulkEnvelopeDlqMode());
            assertEquals("other-dlq", p.getBulkDlqTopic());
            assertEquals(60000L, p.getEncCircuitOpenMs());
            assertEquals(60000L, context.getBean(HouseholdConfiguration.class).getEncCircuitOpenMs());
        });
    }
    /** Server that accepts the connection and never sends a byte: a stuck enc-service. */
    private static final class StuckServer implements AutoCloseable {
        final ServerSocket socket = new ServerSocket(0);
        final List<Socket> accepted = new CopyOnWriteArrayList<>();

        StuckServer() throws Exception {
            Thread acceptor = new Thread(() -> {
                try {
                    while (true) {
                        accepted.add(socket.accept());
                    }
                } catch (Exception ignored) {
                    // closed at the end of the test
                }
            });
            acceptor.setDaemon(true);
            acceptor.start();
        }

        String url() {
            return "http://127.0.0.1:" + socket.getLocalPort() + "/egov-enc-service/crypto/v1/_encrypt";
        }

        @Override
        public void close() throws Exception {
            socket.close();
            for (Socket s : accepted) {
                s.close();
            }
        }
    }

    /** The RestTemplate a bean holds (the bean may be a Spring proxy, whose own fields are empty, so the target is read). */
    private static RestTemplate restTemplateOf(Object bean) {
        Object target = AopTestUtils.getUltimateTargetObject(bean);
        Field field = ReflectionUtils.findField(target.getClass(), null, RestTemplate.class);
        assertNotNull(field, target.getClass() + " has no RestTemplate field");
        ReflectionUtils.makeAccessible(field);
        return (RestTemplate) ReflectionUtils.getField(field, target);
    }

    @Test
    @DisplayName("HTTP timeouts: the one RestTemplate enc-client and ServiceRequestClient use is built from the library's timeout builder, so a stuck server times out")
    void httpClientTimeoutsReachEncClientAndServiceRequestClient() throws Exception {
        // the read timeout comes in through an env var, the way a deployment sets it (HOUSEHOLD_HTTP_READ_TIMEOUT_MS)
        runner(Map.of("HOUSEHOLD_HTTP_READ_TIMEOUT_MS", "300")).run(context -> {
            assertNull(context.getStartupFailure(), String.valueOf(context.getStartupFailure()));
            // the builder tracer's logAwareRestTemplate is made from is the library's, not Spring Boot's or the test helper's
            assertEquals("httpClientTimeoutConfig", context.getBeanFactory().getBeanDefinition("restTemplateBuilder").getFactoryBeanName());
            assertEquals(1, context.getBeansOfType(RestTemplate.class).size());
            RestTemplate shared = context.getBean("logAwareRestTemplate", RestTemplate.class);
            // enc-client's connection and household's ServiceRequestClient (boundary, individual and other calls) hold that same object
            assertSame(shared, restTemplateOf(context.getBean("encryptionServiceRestConnection")));
            assertSame(shared, restTemplateOf(context.getBean(ServiceRequestClient.class)));

            try (StuckServer stuck = new StuckServer()) {
                // the template itself
                long start = System.nanoTime();
                ResourceAccessException e = null;
                try {
                    shared.postForObject(stuck.url(), "{}", String.class);
                } catch (ResourceAccessException thrown) {
                    e = thrown;
                }
                long elapsedMs = (System.nanoTime() - start) / 1_000_000;
                assertNotNull(e, "the call to a stuck server returned");
                assertInstanceOf(SocketTimeoutException.class, e.getCause());
                assertTrue(elapsedMs >= 250 && elapsedMs < 5_000, "gave up after " + elapsedMs + " ms");

                // household's own client class
                start = System.nanoTime();
                CustomException failed = assertThrows(CustomException.class, () ->
                        context.getBean(ServiceRequestClient.class).fetchResult(new StringBuilder(stuck.url()), Map.of(), Map.class));
                elapsedMs = (System.nanoTime() - start) / 1_000_000;
                assertEquals("SERVICE_REQUEST_CLIENT_ERROR", failed.getCode());
                assertTrue(elapsedMs >= 250 && elapsedMs < 5_000, "gave up after " + elapsedMs + " ms");
            }
        });
    }
}
