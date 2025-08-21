package org.egov.excelingestion.web.processor;

import lombok.extern.slf4j.Slf4j;
import org.egov.excelingestion.service.ConfigBasedGenerationService;
import org.egov.excelingestion.service.LocalizationService;
import org.egov.excelingestion.util.RequestInfoConverter;
import org.egov.excelingestion.web.models.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Component("microplanProcessor")
@Slf4j
public class MicroplanProcessor implements IGenerateProcessor {

    private final ConfigBasedGenerationService configBasedGenerationService;
    private final LocalizationService localizationService;
    private final RequestInfoConverter requestInfoConverter;
    private final ProcessorGenerationConfig microplanProcessorConfig;

    @Autowired
    public MicroplanProcessor(ConfigBasedGenerationService configBasedGenerationService,
                            LocalizationService localizationService,
                            RequestInfoConverter requestInfoConverter,
                            @Qualifier("microplanProcessorConfig") ProcessorGenerationConfig microplanProcessorConfig) {
        this.configBasedGenerationService = configBasedGenerationService;
        this.localizationService = localizationService;
        this.requestInfoConverter = requestInfoConverter;
        this.microplanProcessorConfig = microplanProcessorConfig;
    }

    @Override
    public GenerateResource process(GenerateResourceRequest request) {
        log.info("Processing hierarchy excel generation for type: {}", request.getGenerateResource().getType());
        return request.getGenerateResource();
    }

    public byte[] generateExcel(GenerateResource generateResource, RequestInfo requestInfo) throws IOException {
        log.info("Starting config-based Excel generation for hierarchyType: {}", generateResource.getHierarchyType());

        String tenantId = generateResource.getTenantId();
        String hierarchyType = generateResource.getHierarchyType();
        String locale = requestInfoConverter.extractLocale(requestInfo);

        // Fetch localization maps
        String localizationModule = "hcm-boundary-" + hierarchyType.toLowerCase();
        Map<String, String> localizationMap = localizationService.getLocalizedMessages(
                tenantId, localizationModule, locale, requestInfo);
        
        String schemaLocalizationModule = "hcm-admin-schemas";
        Map<String, String> schemaLocalizationMap = localizationService.getLocalizedMessages(
                tenantId, schemaLocalizationModule, locale, requestInfo);
        
        // Merge both localization maps
        Map<String, String> mergedLocalizationMap = new HashMap<>();
        mergedLocalizationMap.putAll(localizationMap);
        mergedLocalizationMap.putAll(schemaLocalizationMap);

        // Use config-based generation service
        return configBasedGenerationService.generateExcelWithConfig(
                microplanProcessorConfig, generateResource, requestInfo, mergedLocalizationMap);
    }



    @Override
    public String getType() {
        return "microplan-ingestion";
    }
}
