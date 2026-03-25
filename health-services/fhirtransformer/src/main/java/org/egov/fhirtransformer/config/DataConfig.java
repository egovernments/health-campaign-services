package org.egov.fhirtransformer.config;

import ca.uhn.fhir.context.FhirContext;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
@EnableCaching
public class DataConfig {

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    @Bean
    public FhirContext fhirContext() {
        return FhirContext.forR5();
    }

}
