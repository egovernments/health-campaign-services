package org.egov.excelingestion.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.egov.excelingestion.config.ErrorConstants;
import org.egov.excelingestion.config.ProcessorConfigurationRegistry;
import org.egov.excelingestion.exception.CustomExceptionHandler;
import org.egov.excelingestion.web.models.ProcessResource;
import org.egov.excelingestion.web.models.ProcessorGenerationConfig;
import org.egov.excelingestion.web.models.RequestInfo;
import org.egov.excelingestion.web.models.SheetGenerationConfig;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Service for processing validation using ProcessorConfigurationRegistry
 */
@Service
@Slf4j
public class ConfigBasedProcessingService {

    private final ProcessorConfigurationRegistry configRegistry;
    private final CustomExceptionHandler exceptionHandler;
    private final MDMSService mdmsService;

    public ConfigBasedProcessingService(ProcessorConfigurationRegistry configRegistry,
                                      CustomExceptionHandler exceptionHandler,
                                      MDMSService mdmsService) {
        this.configRegistry = configRegistry;
        this.exceptionHandler = exceptionHandler;
        this.mdmsService = mdmsService;
    }

    /**
     * Pre-validates schema configuration and fetches all required schemas
     */
    public Map<String, Map<String, Object>> preValidateAndFetchSchemas(Workbook workbook, 
                                                                      ProcessResource resource,
                                                                      RequestInfo requestInfo, 
                                                                      Map<String, String> localizationMap) {
        log.info("Starting config-based processing validation for type: {}", resource.getType());
        
        Map<String, Map<String, Object>> schemas = new HashMap<>();

        // Step 1: Get processor configuration by type
        ProcessorGenerationConfig config = getConfigByType(resource.getType());

        // Step 2: Check if all required sheets are present in the workbook
        checkRequiredSheetsPresent(workbook, config, localizationMap);

        // Step 3: Pre-validate all sheets and fetch their schemas
        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
            Sheet sheet = workbook.getSheetAt(i);
            String sheetName = sheet.getSheetName();

            // Skip hidden sheets
            if (isHiddenSheet(sheetName)) {
                log.info("Skipping hidden sheet: {}", sheetName);
                continue;
            }

            log.info("Pre-validating schema for sheet: {}", sheetName);

            // Get schema name for this sheet from config
            String schemaName = getSchemaNameForSheet(sheetName, config, localizationMap);

            if (schemaName == null) {
                // Check if it's an unknown sheet or just no validation configured
                if (!isSheetConfigured(sheetName, config, localizationMap)) {
                    Set<String> configuredSheets = getConfiguredSheetNames(config, localizationMap);
                    exceptionHandler.throwCustomException(ErrorConstants.SHEET_NOT_CONFIGURED,
                            ErrorConstants.SHEET_NOT_CONFIGURED_MESSAGE
                                    .replace("{0}", sheetName)
                                    .replace("{1}", resource.getType())
                                    .replace("{2}", String.valueOf(configuredSheets)));
                }
                // Sheet is configured but has no schema (no validation required)
                log.info("Sheet '{}' is configured but has no validation schema", sheetName);
                continue;
            }

            // Fetch schema from MDMS if not already fetched
            if (!schemas.containsKey(schemaName)) {
                Map<String, Object> schema = fetchSchemaFromMDMS(resource.getTenantId(), schemaName, requestInfo);

                if (schema == null) {
                    exceptionHandler.throwCustomException(ErrorConstants.SCHEMA_NOT_FOUND_IN_MDMS,
                            ErrorConstants.SCHEMA_NOT_FOUND_IN_MDMS_MESSAGE
                                    .replace("{0}", schemaName)
                                    .replace("{1}", resource.getTenantId()),
                            new RuntimeException("Schema '" + schemaName + "' not found in MDMS"));
                }

                schemas.put(schemaName, schema);
                log.info("Successfully fetched and cached schema: {}", schemaName);
            }
        }

        log.info("Config-based pre-validation completed successfully. Fetched {} schemas", schemas.size());
        return schemas;
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

        return configRegistry.getConfigByType(processorType);
    }

    /**
     * Check if all required sheets are present in the workbook
     */
    private void checkRequiredSheetsPresent(Workbook workbook, ProcessorGenerationConfig config, Map<String, String> localizationMap) {
        // Get all non-hidden sheet names from workbook
        Set<String> workbookSheets = new HashSet<>();
        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
            String name = workbook.getSheetAt(i).getSheetName();
            if (!isHiddenSheet(name)) {
                workbookSheets.add(name);
            }
        }
        
        // Check for all configured visible sheets
        for (SheetGenerationConfig sheetConfig : config.getSheets()) {
            if (sheetConfig.isVisible()) {
                String localizedName = getLocalizedSheetName(sheetConfig.getSheetNameKey(), localizationMap);
                if (!workbookSheets.contains(localizedName)) {
                    Set<String> expectedSheets = getConfiguredSheetNames(config, localizationMap);
                    
                    exceptionHandler.throwCustomException(ErrorConstants.REQUIRED_SHEET_MISSING,
                            ErrorConstants.REQUIRED_SHEET_MISSING_MESSAGE
                                    .replace("{0}", localizedName)
                                    .replace("{1}", expectedSheets.toString()),
                            new RuntimeException("Required sheet '" + localizedName + "' missing"));
                }
            }
        }
    }

    /**
     * Get schema name for a sheet from configuration
     */
    private String getSchemaNameForSheet(String actualSheetName, ProcessorGenerationConfig config, Map<String, String> localizationMap) {
        for (SheetGenerationConfig sheetConfig : config.getSheets()) {
            String localizedName = getLocalizedSheetName(sheetConfig.getSheetNameKey(), localizationMap);
            if (localizedName.equals(actualSheetName)) {
                return sheetConfig.getSchemaName();
            }
        }
        return null;
    }

    /**
     * Check if a sheet is configured
     */
    private boolean isSheetConfigured(String actualSheetName, ProcessorGenerationConfig config, Map<String, String> localizationMap) {
        for (SheetGenerationConfig sheetConfig : config.getSheets()) {
            String localizedName = getLocalizedSheetName(sheetConfig.getSheetNameKey(), localizationMap);
            if (localizedName.equals(actualSheetName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Get all configured sheet names
     */
    private Set<String> getConfiguredSheetNames(ProcessorGenerationConfig config, Map<String, String> localizationMap) {
        Set<String> sheetNames = new HashSet<>();
        for (SheetGenerationConfig sheetConfig : config.getSheets()) {
            if (sheetConfig.isVisible()) {
                String localizedName = getLocalizedSheetName(sheetConfig.getSheetNameKey(), localizationMap);
                sheetNames.add(localizedName);
            }
        }
        return sheetNames;
    }

    /**
     * Check if a sheet is a hidden sheet
     */
    private boolean isHiddenSheet(String sheetName) {
        return sheetName != null && sheetName.startsWith("_h_") && sheetName.endsWith("_h_");
    }

    /**
     * Get localized sheet name with 31-char limit handling
     */
    private String getLocalizedSheetName(String sheetKey, Map<String, String> localizationMap) {
        String localizedName = sheetKey;
        
        if (localizationMap != null && localizationMap.containsKey(sheetKey)) {
            localizedName = localizationMap.get(sheetKey);
        }
        
        // Handle Excel's 31 character limit
        if (localizedName.length() > 31) {
            localizedName = localizedName.substring(0, 31);
        }
        
        return localizedName;
    }

    /**
     * Fetch schema from MDMS
     */
    private Map<String, Object> fetchSchemaFromMDMS(String tenantId, String schemaName, RequestInfo requestInfo) {
        try {
            Map<String, Object> filters = new HashMap<>();
            filters.put("title", schemaName);
            
            java.util.List<Map<String, Object>> mdmsList = mdmsService.searchMDMS(
                    requestInfo, tenantId, "HCM-ADMIN-CONSOLE.schemas", filters, 1, 0);
            
            if (!mdmsList.isEmpty()) {
                Map<String, Object> mdmsData = mdmsList.get(0);
                Map<String, Object> data = (Map<String, Object>) mdmsData.get("data");
                
                if (data != null) {
                    Map<String, Object> properties = (Map<String, Object>) data.get("properties");
                    if (properties != null) {
                        log.info("Successfully fetched MDMS schema for: {}", schemaName);
                        return properties;
                    }
                }
            }
            
            log.warn("No MDMS data found for schema: {}", schemaName);
            return null;
            
        } catch (Exception e) {
            log.error("Error fetching MDMS schema {}: {}", schemaName, e.getMessage(), e);
            return null;
        }
    }
}