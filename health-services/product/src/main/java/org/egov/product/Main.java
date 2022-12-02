package org.egov.product;

import org.egov.tracer.config.TracerConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
@Import({ TracerConfiguration.class })
@SpringBootApplication
@EnableCaching
@ComponentScan(basePackages = { "org.egov.product", "org.egov.product.web.controllers" , "org.egov.product.config"})
public class Main {
    public static void main(String[] args) throws Exception {
        SpringApplication.run(Main.class, args);
    }
}
