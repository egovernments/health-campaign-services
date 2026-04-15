package org.egov.web.notification.push;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.egov.tracer.config.TracerConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.kafka.annotation.EnableKafka;

@SpringBootApplication
@Import(TracerConfiguration.class)
@EnableKafka
public class EgovNotificationPushApplication {

    public static void main(String[] args) {
        SpringApplication.run(EgovNotificationPushApplication.class, args);
    }

    @Primary
    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper()
                .configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

}
