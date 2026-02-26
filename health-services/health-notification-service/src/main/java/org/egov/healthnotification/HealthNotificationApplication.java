package org.egov.healthnotification;

import org.egov.tracer.config.TracerConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@Import({ TracerConfiguration.class })
@SpringBootApplication
public class HealthNotificationApplication {
    public static void main(String[] args) throws Exception {
        SpringApplication.run(HealthNotificationApplication.class, args);
    }
}
