package org.egov.projectfactory.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestTemplate;

/**
 * RestTemplate + ObjectMapper beans for downstream integration calls.
 * Kept separate from {@link MainConfiguration} to mirror the excel-ingestion layout
 * (RestTemplate is used for FileStore multipart uploads and downstream REST adapters).
 */
@Configuration
public class ApplicationConfig {

    @Bean
    public RestTemplate restTemplate(ObjectMapper objectMapper) {
        RestTemplate restTemplate = new RestTemplate();
        restTemplate.getMessageConverters().add(0, new MappingJackson2HttpMessageConverter(objectMapper));
        return restTemplate;
    }

    /**
     * A lenient mapper used by RestTemplate for downstream payloads.
     * Note: the primary {@code @Qualifier("objectMapper")} lives in {@link MainConfiguration};
     * this one is the injectable default for integration serialization.
     */
    @Bean
    public ObjectMapper restObjectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        return objectMapper;
    }
}
