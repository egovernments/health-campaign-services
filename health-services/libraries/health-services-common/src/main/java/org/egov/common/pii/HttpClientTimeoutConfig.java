package org.egov.common.pii;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.web.client.RestTemplateBuilderConfigurer;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Timeouts for the service's shared HTTP client.
 *
 * <p>The tracer library builds the shared RestTemplate ({@code logAwareRestTemplate}) from the
 * RestTemplateBuilder bean, and enc-client, MDMS, user and idgen calls all use it. Without timeouts a
 * stuck enc-service holds each request thread with no end, and the circuit breaker never opens because
 * it counts only calls that end in an error. This replaces Spring Boot's default builder with the same
 * builder (same message converters and customizers, through the configurer) plus the two timeouts.
 * enc-client, enc-service and the tracer library are not changed.</p>
 *
 * <p>Opt-in like the rest of this package: the bean exists only when {@code egov.pii.encryption.enabled=true}.
 * Settings: {@code egov.http.connect.timeout.ms} (default 2000) and {@code egov.http.read.timeout.ms}
 * (default 60000).</p>
 */
@Configuration
@ConditionalOnProperty(name = "egov.pii.encryption.enabled", havingValue = "true")
public class HttpClientTimeoutConfig {

    @Bean
    public RestTemplateBuilder restTemplateBuilder(RestTemplateBuilderConfigurer configurer,
                                                   @Value("${egov.http.connect.timeout.ms:2000}") long connectTimeoutMs,
                                                   @Value("${egov.http.read.timeout.ms:60000}") long readTimeoutMs) {
        return configurer.configure(new RestTemplateBuilder())
                .setConnectTimeout(Duration.ofMillis(connectTimeoutMs))
                .setReadTimeout(Duration.ofMillis(readTimeoutMs));
    }
}
