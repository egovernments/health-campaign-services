package org.egov.common.pii;

import org.egov.tracer.config.TracerConfiguration;
import org.egov.tracer.config.TracerProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.web.client.RestTemplateAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The shared HTTP client is built by the tracer library ({@code logAwareRestTemplate}) from the
 * RestTemplateBuilder in the Spring context; enc-client and every other outgoing call use it.
 */
class HttpClientTimeoutConfigTest {

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

    private static RestTemplate sharedClientFromContext(boolean withOurConfig) {
        AtomicReference<RestTemplate> shared = new AtomicReference<>();
        ApplicationContextRunner runner = new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(RestTemplateAutoConfiguration.class))
                .withPropertyValues("egov.pii.encryption.enabled=true",
                        "egov.http.connect.timeout.ms=2000", "egov.http.read.timeout.ms=300");
        if (withOurConfig) {
            runner = runner.withUserConfiguration(HttpClientTimeoutConfig.class);
        }
        runner.run(context -> shared.set(new TracerConfiguration()
                .logAwareRestTemplate(context.getBean(RestTemplateBuilder.class), new TracerProperties())));
        return shared.get();
    }

    @Test
    @DisplayName("with our config, the tracer's shared client gives up on a stuck server after the read timeout")
    void sharedClientTimesOutOnStuckServer() throws Exception {
        RestTemplate shared = sharedClientFromContext(true);
        try (StuckServer stuck = new StuckServer()) {
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
        }
    }

    @Test
    @DisplayName("opt-in: with egov.pii.encryption.enabled unset the config creates no builder, Spring Boot's own stays")
    void notCreatedWithoutTheFlag() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(RestTemplateAutoConfiguration.class))
                .withUserConfiguration(HttpClientTimeoutConfig.class)
                .run(context -> {
                    assertEquals(0, context.getBeansOfType(HttpClientTimeoutConfig.class).size());
                    BeanDefinition definition = context.getBeanFactory().getBeanDefinition("restTemplateBuilder");
                    assertTrue("restTemplateAutoConfiguration".equals(definition.getFactoryBeanName()),
                            String.valueOf(definition));
                });
    }

    @Test
    @DisplayName("control: without our config the same client is still waiting after 1.5 s (no timeout today)")
    void withoutOurConfigTheClientWaits() throws Exception {
        RestTemplate shared = sharedClientFromContext(false);
        try (StuckServer stuck = new StuckServer()) {
            AtomicReference<Throwable> outcome = new AtomicReference<>();
            Thread call = new Thread(() -> {
                try {
                    shared.postForObject(stuck.url(), "{}", String.class);
                } catch (Throwable t) {
                    outcome.set(t);
                }
            });
            call.setDaemon(true);
            call.start();
            call.join(1_500);

            assertTrue(call.isAlive(), "the call ended early: " + outcome.get());
            assertNull(outcome.get());
        } // closing the server ends the waiting call
    }
}
