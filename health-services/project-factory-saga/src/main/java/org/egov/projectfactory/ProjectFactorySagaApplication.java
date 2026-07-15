package org.egov.projectfactory;

import org.egov.tracer.config.TracerConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Project Factory (Saga) application entry point.
 *
 * <p>Java rewrite of the TypeScript project-factory service that also absorbs the
 * excel-ingestion service. Campaign setup is driven by a durable, DB-backed saga
 * orchestrator (see {@code context/} for the design-doc summary).</p>
 *
 * <p>Scheduling is enabled for the saga reaper; async for streaming
 * generation/validation work.</p>
 */
@SpringBootApplication
@ComponentScan(basePackages = {"org.egov"})
@Import({TracerConfiguration.class})
@EnableCaching
@EnableAsync
@EnableScheduling
public class ProjectFactorySagaApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProjectFactorySagaApplication.class, args);
    }
}
