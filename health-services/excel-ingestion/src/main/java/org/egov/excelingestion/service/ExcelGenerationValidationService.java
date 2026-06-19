package org.egov.excelingestion.service;

import lombok.extern.slf4j.Slf4j;
import org.egov.excelingestion.config.ErrorConstants;
import org.egov.excelingestion.service.MDMSConfigService;
import org.egov.excelingestion.web.models.mdms.ExcelIngestionGenerateData;
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

    private final MDMSConfigService mdmsConfigService;
    private final GenerationConfigValidationService validationService;
    private final LocalizationService localizationService;
    private final RequestInfoConverter requestInfoConverter;
    private final CustomExceptionHandler exceptionHandler;
    private final BoundaryService boundaryService;

    public ExcelGenerationValidationService(MDMSConfigService mdmsConfigService,
                                          GenerationConfigValidationService validationService,
                                          LocalizationService localizationService,
                                          RequestInfoConverter requestInfoConverter,
                                          CustomExceptionHandler exceptionHandler,
                                          BoundaryService boundaryService) {
        this.mdmsConfigService = mdmsConfigService;
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
        ProcessorGenerationConfig config = getConfigByGenerateType(generateType, requestInfo, generateResource.getTenantId());

        // Step 3: Validate configuration (classes, schemas, etc.)
        validationService.validateProcessorConfig(config, generateType);

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

    private ProcessorGenerationConfig getConfigByGenerateType(String generateType, RequestInfo requestInfo, String tenantId) {
        ExcelIngestionGenerateData generateData = mdmsConfigService.getExcelIngestionGenerateConfig(requestInfo, tenantId, generateType);
        if (generateData == null || generateData.getSheets() == null || generateData.getSheets().isEmpty()) {
            log.error("Generate type '{}' is not supported for tenant: {}", generateType, tenantId);

            exceptionHandler.throwCustomException(
                    ErrorConstants.GENERATION_TYPE_NOT_SUPPORTED,
                    ErrorConstants.GENERATION_TYPE_NOT_SUPPORTED_MESSAGE
                            .replace("{0}", generateType)
                            .replace("{1}", "Check MDMS configuration"),
                    new IllegalArgumentException("Unsupported generate type: " + generateType)
            );
        }

        // Convert MDMS data to ProcessorGenerationConfig using existing logic from GeneratorConfigurationRegistry
        return convertToProcessorGenerationConfig(generateData);
    }
    
    private ProcessorGenerationConfig convertToProcessorGenerationConfig(ExcelIngestionGenerateData generateData) {
        // This method should contain the same logic as was in GeneratorConfigurationRegistry.getConfigByType
        // For now, return a basic config - you might need to inject ExcelIngestionConfig here
        return ProcessorGenerationConfig.builder()
                .applyWorkbookProtection(generateData.getApplyWorkbookProtection() != null ? generateData.getApplyWorkbookProtection() : true)
                .sheets(generateData.getSheets()) // Convert sheets as needed
                .build();
    }
    
}
