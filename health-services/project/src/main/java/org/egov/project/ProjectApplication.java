package org.egov.project;

import org.egov.tracer.config.TracerConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;

@Import({ TracerConfiguration.class })
@SpringBootApplication
@EnableCaching
@ComponentScan(basePackages = { "org.egov.project", "org.egov.project.web.controllers" , "org.egov.project.config"})
public class ProjectApplication {
    public static void main(String[] args) throws Exception {
        SpringApplication.run(ProjectApplication.class, args);
    }
}
