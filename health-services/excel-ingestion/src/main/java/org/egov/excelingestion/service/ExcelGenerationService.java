package org.egov.excelingestion.service;

import lombok.extern.slf4j.Slf4j;
import org.egov.excelingestion.config.ErrorConstants;
import org.egov.excelingestion.web.models.mdms.ExcelIngestionGenerateData;
import org.egov.excelingestion.exception.CustomExceptionHandler;
import org.egov.excelingestion.util.RequestInfoConverter;
import org.egov.excelingestion.web.models.GenerateResource;
import org.egov.excelingestion.web.models.ProcessorGenerationConfig;
import org.egov.excelingestion.web.models.RequestInfo;
import org.egov.excelingestion.web.models.SheetGenerationConfig;
import org.egov.excelingestion.config.ExcelIngestionConfig;

import java.util.ArrayList;
import java.util.List;
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

    private final MDMSConfigService mdmsConfigService;
    private final ExcelIngestionConfig excelConfig;
    private final ConfigBasedGenerationService generationService;
    private final LocalizationService localizationService;
    private final RequestInfoConverter requestInfoConverter;
    private final CustomExceptionHandler exceptionHandler;

    public ExcelGenerationService(MDMSConfigService mdmsConfigService,
                                ExcelIngestionConfig excelConfig,
                                ConfigBasedGenerationService generationService,
                                LocalizationService localizationService,
                                RequestInfoConverter requestInfoConverter,
                                CustomExceptionHandler exceptionHandler) {
        this.mdmsConfigService = mdmsConfigService;
        this.excelConfig = excelConfig;
        this.generationService = generationService;
        this.localizationService = localizationService;
        this.requestInfoConverter = requestInfoConverter;
        this.exceptionHandler = exceptionHandler;
    }

    /**
     * Generate Excel based on processor type
     * Note: Validations are already done in GenerationService before async process starts
     */
    public byte[] generateExcel(GenerateResource generateResource, RequestInfo requestInfo) throws IOException {
        String processorType = generateResource.getType();
        log.info("Starting Excel generation for processor type: {}", processorType);

        // Step 1: Get configuration by type (already validated)
        ProcessorGenerationConfig config = getConfigByType(processorType, requestInfo, generateResource.getTenantId());

        // Step 2: Prepare localization maps
        Map<String, String> localizationMap = prepareLocalizationMap(generateResource, requestInfo);

        // Step 3: Generate Excel
        return generationService.generateExcelWithConfig(config, generateResource, requestInfo, localizationMap);
    }

    /**
     * Get processor configuration by type with validation
     */
    private ProcessorGenerationConfig getConfigByType(String processorType, RequestInfo requestInfo, String tenantId) {
        ExcelIngestionGenerateData generateData = mdmsConfigService.getExcelIngestionGenerateConfig(requestInfo, tenantId, processorType);
        if (generateData == null || generateData.getSheets() == null || generateData.getSheets().isEmpty()) {
            log.error("Processor type '{}' is not supported for tenant: {}", processorType, tenantId);
            
            exceptionHandler.throwCustomException(
                    ErrorConstants.PROCESSING_TYPE_NOT_SUPPORTED,
                    ErrorConstants.PROCESSING_TYPE_NOT_SUPPORTED_MESSAGE
                            .replace("{0}", processorType)
                            .replace("{1}", "Check MDMS configuration"),
                    new IllegalArgumentException("Unsupported processor type: " + processorType)
            );
        }

        return convertToProcessorGenerationConfig(generateData);
    }
    
    private ProcessorGenerationConfig convertToProcessorGenerationConfig(ExcelIngestionGenerateData generateData) {
        List<SheetGenerationConfig> sheets = new ArrayList<>();
        for (SheetGenerationConfig sheetGenerationConfig : generateData.getSheets()) {
            SheetGenerationConfig.SheetGenerationConfigBuilder builder = SheetGenerationConfig.builder()
                    .sheetName(sheetGenerationConfig.getSheetName())
                    .schemaName(sheetGenerationConfig.getSchemaName())
                    .generationClass(sheetGenerationConfig.getGenerationClass())
                    .isGenerationClassViaExcelPopulator(sheetGenerationConfig.getIsGenerationClassViaExcelPopulator() != null ? sheetGenerationConfig.getIsGenerationClassViaExcelPopulator() : false)
                    .order(sheetGenerationConfig.getOrder() != null ? sheetGenerationConfig.getOrder() : 1)
                    .visible(sheetGenerationConfig.getVisible() != null ? sheetGenerationConfig.getVisible() : true);
            
            sheets.add(builder.build());
        }
        
        return ProcessorGenerationConfig.builder()
                .applyWorkbookProtection(generateData.getApplyWorkbookProtection() != null ? generateData.getApplyWorkbookProtection() : true)
                .protectionPassword(excelConfig.getExcelSheetPassword())
                .zoomLevel(excelConfig.getExcelSheetZoom())
                .sheets(sheets)
                .build();
    }

    /**
     * Prepare localization maps for the generation
     */
    private Map<String, String> prepareLocalizationMap(GenerateResource generateResource, RequestInfo requestInfo) {
        String tenantId = generateResource.getTenantId();
        String hierarchyType = generateResource.getHierarchyType();
        String locale = requestInfoConverter.extractLocale(requestInfo);

        Map<String, String> mergedLocalizationMap = new HashMap<>();

        // Boundary localization - now critical, any failure will stop processing
        String boundaryLocalizationModule = "hcm-boundary-" + hierarchyType.toLowerCase();
        Map<String, String> boundaryLocalizationMap = localizationService.getLocalizedMessages(
                tenantId, boundaryLocalizationModule, locale, requestInfo);
        mergedLocalizationMap.putAll(boundaryLocalizationMap);

        // Schema localization - now critical, any failure will stop processing
        String schemaLocalizationModule = "hcm-admin-schemas";
        Map<String, String> schemaLocalizationMap = localizationService.getLocalizedMessages(
                tenantId, schemaLocalizationModule, locale, requestInfo);
        mergedLocalizationMap.putAll(schemaLocalizationMap);

        log.info("Prepared localization map with {} entries", mergedLocalizationMap.size());

        return mergedLocalizationMap;
    }
}
