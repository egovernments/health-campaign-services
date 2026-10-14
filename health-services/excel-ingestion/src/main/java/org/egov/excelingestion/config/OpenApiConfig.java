package org.egov.excelingestion.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.tags.Tag;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI excelIngestionOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Excel Ingestion Service API")
                        .version("1.0.0")
                        .description("Dynamic OpenAPI definition for excel-ingestion APIs.")
                        .contact(new Contact()
                                .name("egovernments foundation")
                                .email("info@egovernments.org")))
                .tags(List.of(
                        new Tag().name("Data Management (V1)")
                                .description("Legacy data generation and processing endpoints"),
                        new Tag().name("Localization")
                                .description("Multi-language support and message management (Internal APIs)")
                ));
    }
}
