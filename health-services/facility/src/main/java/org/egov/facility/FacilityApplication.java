package org.egov.facility;

import org.egov.tracer.config.TracerConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Import;
@Import({ TracerConfiguration.class })
@SpringBootApplication
@EnableCaching
public class FacilityApplication {
    public static void main(String[] args) throws Exception {
        SpringApplication.run(FacilityApplication.class, args);
    }
}
