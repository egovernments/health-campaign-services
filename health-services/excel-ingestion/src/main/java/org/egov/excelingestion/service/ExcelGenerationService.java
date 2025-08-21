package org.egov.excelingestion.service;

import lombok.extern.slf4j.Slf4j;
import org.egov.excelingestion.config.ErrorConstants;
import org.egov.excelingestion.config.ProcessorConfigurationRegistry;
import org.egov.excelingestion.exception.CustomExceptionHandler;
import org.egov.excelingestion.util.RequestInfoConverter;
import org.egov.excelingestion.web.models.GenerateResource;
import org.egov.excelingestion.web.models.ProcessorGenerationConfig;
import org.egov.excelingestion.web.models.RequestInfo;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Main service for Excel generation - handles type to config to generation flow
 */
@Service
@Slf4j
public class ExcelGenerationService {

    private final ProcessorConfigurationRegistry configRegistry;
    private final GenerationConfigValidationService validationService;
    private final ConfigBasedGenerationService generationService;
    private final LocalizationService localizationService;
    private final RequestInfoConverter requestInfoConverter;
    private final CustomExceptionHandler exceptionHandler;

    public ExcelGenerationService(ProcessorConfigurationRegistry configRegistry,
                                GenerationConfigValidationService validationService,
                                ConfigBasedGenerationService generationService,
                                LocalizationService localizationService,
                                RequestInfoConverter requestInfoConverter,
                                CustomExceptionHandler exceptionHandler) {
        this.configRegistry = configRegistry;
        this.validationService = validationService;
        this.generationService = generationService;
        this.localizationService = localizationService;
        this.requestInfoConverter = requestInfoConverter;
        this.exceptionHandler = exceptionHandler;
    }

    /**
     * Generate Excel based on processor type
     */
    public byte[] generateExcel(GenerateResource generateResource, RequestInfo requestInfo) throws IOException {
        String processorType = generateResource.getType();
        log.info("Starting Excel generation for processor type: {}", processorType);

        // Step 1: Get configuration by type
        ProcessorGenerationConfig config = getConfigByType(processorType);

        // Step 2: Validate configuration (classes, schemas, etc.)
        validationService.validateProcessorConfig(config);

        // Step 3: Prepare localization maps
        Map<String, String> localizationMap = prepareLocalizationMap(generateResource, requestInfo);

        // Step 4: Generate Excel
        return generationService.generateExcelWithConfig(config, generateResource, requestInfo, localizationMap);
    }

    /**
     * Get processor configuration by type with validation
     */
    private ProcessorGenerationConfig getConfigByType(String processorType) {
        if (!configRegistry.isProcessorTypeSupported(processorType)) {
            log.error("Processor type '{}' is not supported. Supported types: {}", 
                    processorType, String.join(", ", configRegistry.getSupportedProcessorTypes()));
            
            exceptionHandler.throwCustomException(
                    ErrorConstants.PROCESSING_TYPE_NOT_SUPPORTED,
                    ErrorConstants.PROCESSING_TYPE_NOT_SUPPORTED_MESSAGE
                            .replace("{0}", processorType)
                            .replace("{1}", String.join(", ", configRegistry.getSupportedProcessorTypes())),
                    new IllegalArgumentException("Unsupported processor type: " + processorType)
            );
        }

        ProcessorGenerationConfig config = configRegistry.getConfigByType(processorType);
        if (config == null) {
            log.error("Configuration not found for processor type: {}", processorType);
            exceptionHandler.throwCustomException(
                    ErrorConstants.INVALID_CONFIGURATION,
                    ErrorConstants.INVALID_CONFIGURATION_MESSAGE.replace("{0}", "Configuration not found for type: " + processorType),
                    new IllegalArgumentException("Configuration not found for processor type: " + processorType)
            );
        }

        return config;
    }

    /**
     * Prepare localization maps for the generation
     */
    private Map<String, String> prepareLocalizationMap(GenerateResource generateResource, RequestInfo requestInfo) {
        String tenantId = generateResource.getTenantId();
        String hierarchyType = generateResource.getHierarchyType();
        String locale = requestInfoConverter.extractLocale(requestInfo);

        Map<String, String> mergedLocalizationMap = new HashMap<>();

        try {
            // Boundary localization
            String boundaryLocalizationModule = "hcm-boundary-" + hierarchyType.toLowerCase();
            Map<String, String> boundaryLocalizationMap = localizationService.getLocalizedMessages(
                    tenantId, boundaryLocalizationModule, locale, requestInfo);
            mergedLocalizationMap.putAll(boundaryLocalizationMap);

            // Schema localization
            String schemaLocalizationModule = "hcm-admin-schemas";
            Map<String, String> schemaLocalizationMap = localizationService.getLocalizedMessages(
                    tenantId, schemaLocalizationModule, locale, requestInfo);
            mergedLocalizationMap.putAll(schemaLocalizationMap);

            log.info("Prepared localization map with {} entries", mergedLocalizationMap.size());
            
        } catch (Exception e) {
            log.warn("Error preparing localization map: {}", e.getMessage());
            // Continue with empty map - localization is not critical for generation
        }

        return mergedLocalizationMap;
    }
}
