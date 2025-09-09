package org.egov.excelingestion.service;

import lombok.extern.slf4j.Slf4j;
import org.egov.excelingestion.config.ErrorConstants;
import org.egov.excelingestion.config.GeneratorConfigurationRegistry;
import org.egov.excelingestion.exception.CustomExceptionHandler;
import org.egov.excelingestion.util.RequestInfoConverter;
import org.egov.excelingestion.web.models.BoundaryHierarchyResponse;
import org.egov.excelingestion.web.models.GenerateResource;
import org.egov.excelingestion.web.models.ProcessorGenerationConfig;
import org.egov.excelingestion.web.models.RequestInfo;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class ExcelGenerationValidationService {

    private final GeneratorConfigurationRegistry configRegistry;
    private final GenerationConfigValidationService validationService;
    private final LocalizationService localizationService;
    private final RequestInfoConverter requestInfoConverter;
    private final CustomExceptionHandler exceptionHandler;
    private final BoundaryService boundaryService;

    public ExcelGenerationValidationService(GeneratorConfigurationRegistry configRegistry,
                                          GenerationConfigValidationService validationService,
                                          LocalizationService localizationService,
                                          RequestInfoConverter requestInfoConverter,
                                          CustomExceptionHandler exceptionHandler,
                                          BoundaryService boundaryService) {
        this.configRegistry = configRegistry;
        this.validationService = validationService;
        this.localizationService = localizationService;
        this.requestInfoConverter = requestInfoConverter;
        this.exceptionHandler = exceptionHandler;
        this.boundaryService = boundaryService;
    }

    public void validate(GenerateResource generateResource, RequestInfo requestInfo) {
        String generateType = generateResource.getType();
        log.info("Starting pre-async validation for generate type: {}", generateType);

        // Step 1: Validate Hierarchy Type
        validateHierarchyType(generateResource, requestInfo);

        // Step 2: Get configuration by type
        ProcessorGenerationConfig config = getConfigByGenerateType(generateType);

        // Step 3: Validate configuration (classes, schemas, etc.)
        validationService.validateProcessorConfig(config);

        // Step 4: Prepare localization maps
        prepareLocalizationMap(generateResource, requestInfo);

        log.info("Pre-async validation successful for generate type: {}", generateType);
    }

    private void validateHierarchyType(GenerateResource generateResource, RequestInfo requestInfo) {
        String hierarchyType = generateResource.getHierarchyType();
        String tenantId = generateResource.getTenantId();

        BoundaryHierarchyResponse response = boundaryService.fetchBoundaryHierarchy(tenantId, hierarchyType, requestInfo);

        if (response == null || CollectionUtils.isEmpty(response.getBoundaryHierarchy())) {
            log.error("Invalid hierarchy type: {}", hierarchyType);
            exceptionHandler.throwCustomException(ErrorConstants.INVALID_HIERARCHY_TYPE,
                    ErrorConstants.INVALID_HIERARCHY_TYPE_MESSAGE.replace("{0}", hierarchyType));
        }
    }

    private ProcessorGenerationConfig getConfigByGenerateType(String generateType) {
        if (!configRegistry.isProcessorTypeSupported(generateType)) {
            log.error("Generate type '{}' is not supported. Supported types: {}",
                    generateType, String.join(", ", configRegistry.getSupportedProcessorTypes()));

            exceptionHandler.throwCustomException(
                    ErrorConstants.GENERATION_TYPE_NOT_SUPPORTED,
                    ErrorConstants.GENERATION_TYPE_NOT_SUPPORTED_MESSAGE
                            .replace("{0}", generateType)
                            .replace("{1}", String.join(", ", configRegistry.getSupportedProcessorTypes())),
                    new IllegalArgumentException("Unsupported generate type: " + generateType)
            );
        }

        ProcessorGenerationConfig config = configRegistry.getConfigByType(generateType);
        if (config == null) {
            log.error("Configuration not found for generate type: {}", generateType);
            exceptionHandler.throwCustomException(
                    ErrorConstants.INVALID_CONFIGURATION,
                    ErrorConstants.INVALID_CONFIGURATION_MESSAGE.replace("{0}", "Configuration not found for type: " + generateType),
                    new IllegalArgumentException("Configuration not found for generate type: " + generateType)
            );
        }

        return config;
    }

    private void prepareLocalizationMap(GenerateResource generateResource, RequestInfo requestInfo) {
        String tenantId = generateResource.getTenantId();
        String hierarchyType = generateResource.getHierarchyType();
        String locale = requestInfoConverter.extractLocale(requestInfo);

        Map<String, String> mergedLocalizationMap = new HashMap<>();

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

        log.info("Prepared localization map with {} entries for validation", mergedLocalizationMap.size());
    }
}
