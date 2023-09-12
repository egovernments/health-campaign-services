package org.egov.adrm;


import org.egov.tracer.config.TracerConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@EnableCaching
@Import({ TracerConfiguration.class })
public class AdrmApplication
{
    public static void main(String[] args) throws Exception {
        SpringApplication.run(AdrmApplication.class, args);
    }
}
