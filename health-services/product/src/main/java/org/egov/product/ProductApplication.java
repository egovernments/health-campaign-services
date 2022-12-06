package org.egov.product;

import org.egov.tracer.config.TracerConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@EnableCaching
@Import({ TracerConfiguration.class })
public class ProductApplication {
    public static void main(String[] args) throws Exception {
        SpringApplication.run(ProductApplication.class, args);
    }
}
