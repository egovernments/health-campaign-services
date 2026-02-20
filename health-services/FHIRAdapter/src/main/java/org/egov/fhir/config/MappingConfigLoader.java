package org.egov.fhir.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class MappingConfigLoader {

    @Value("${fhir.mapping.path:classpath:mappings/*.json}")
    private String mappingPath;

    @Getter
    private final Map<String, MappingConfig> configs = new HashMap<>();

    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostConstruct
    public void loadConfigs() throws Exception {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resolver.getResources(mappingPath);

        for (Resource resource : resources) {
            MappingConfig config = objectMapper.readValue(resource.getInputStream(), MappingConfig.class);
            configs.put(config.getFhirResource().toLowerCase(), config);
            log.info("Loaded mapping for FHIR resource: {}", config.getFhirResource());
        }
    }

    public MappingConfig getConfig(String fhirResource) {
        return configs.get(fhirResource.toLowerCase());
    }
}
