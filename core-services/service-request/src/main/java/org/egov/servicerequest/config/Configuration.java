package org.egov.servicerequest.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.egov.tracer.config.TracerConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;

import javax.annotation.PostConstruct;
import java.util.TimeZone;

@org.springframework.context.annotation.Configuration
@Data
@Import({TracerConfiguration.class})
@NoArgsConstructor
@AllArgsConstructor
@Setter
@Getter
public class Configuration {
    @Value("${app.timezone}")
    private String timeZone;

    @PostConstruct
    public void initialize() {
        TimeZone.setDefault(TimeZone.getTimeZone(timeZone));
    }

    @Bean
    public ObjectMapper objectMapper(){
        return new ObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).setTimeZone(TimeZone.getTimeZone(timeZone));
    }

    @Bean
    @Autowired
    public MappingJackson2HttpMessageConverter jacksonConverter(ObjectMapper objectMapper) {
        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter();
        converter.setObjectMapper(objectMapper);
        return converter;
    }

    // Pagination
    @Value("${egov.service.request.default.offset}")
    private Integer defaultOffset;

    @Value("${egov.service.request.default.limit}")
    private Integer defaultLimit;

    @Value("${egov.service.request.max.limit}")
    private Integer maxLimit;

    // Kafka topics
    @Value("${egov.service.definition.create.topic}")
    private String serviceDefinitionCreateTopic;

    @Value("${egov.service.definition.update.topic}")
    private String serviceDefinitionUpdateTopic;

    @Value("${egov.service.create.topic}")
    private String serviceCreateTopic;

    @Value("${egov.service.update.topic}")
    private String serviceUpdateTopic;

    @Value("${egov.max.string.input.size}")
    private Integer maxStringInputSize;

}
