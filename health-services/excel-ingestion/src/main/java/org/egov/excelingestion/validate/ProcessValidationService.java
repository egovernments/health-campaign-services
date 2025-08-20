package org.egov.excelingestion.validate;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.egov.excelingestion.config.ErrorConstants;
import org.egov.excelingestion.config.SheetSchemaConfig;
import org.egov.excelingestion.exception.CustomExceptionHandler;
import org.egov.excelingestion.service.MDMSService;
import org.egov.excelingestion.web.models.ProcessResource;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Service
@Slf4j
public class ProcessValidationService {

    private final SheetSchemaConfig sheetSchemaConfig;
    private final CustomExceptionHandler exceptionHandler;
    private final MDMSService mdmsService;

    public ProcessValidationService(SheetSchemaConfig sheetSchemaConfig, CustomExceptionHandler exceptionHandler, 
                                    MDMSService mdmsService) {
        this.sheetSchemaConfig = sheetSchemaConfig;
        this.exceptionHandler = exceptionHandler;
        this.mdmsService = mdmsService;
    }

    /**
     * Pre-validates schema configuration and fetches all required schemas
     * Throws custom exceptions immediately if any configuration issues are found
     */
    public Map<String, Map<String, Object>> preValidateAndFetchSchemas(Workbook workbook, ProcessResource resource,
                                                                        org.egov.excelingestion.web.models.RequestInfo requestInfo, 
                                                                        Map<String, String> localizationMap) {

        Map<String, Map<String, Object>> schemas = new HashMap<>();

        // Check if processing type is supported
        if (!sheetSchemaConfig.isProcessingTypeSupported(resource.getType())) {
            exceptionHandler.throwCustomException(ErrorConstants.PROCESSING_TYPE_NOT_SUPPORTED,
                    ErrorConstants.PROCESSING_TYPE_NOT_SUPPORTED_MESSAGE
                            .replace("{0}", resource.getType())
                            .replace("{1}", String.valueOf(sheetSchemaConfig.getSupportedProcessingTypes())));
        }
        
        // First, check if all required sheets are present in the workbook
        checkRequiredSheetsPresent(workbook, resource.getType(), localizationMap);

        // Pre-validate all sheets and fetch their schemas
        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
            Sheet sheet = workbook.getSheetAt(i);
            String sheetName = sheet.getSheetName();

            // Skip hidden sheets (wrapped in _h_ prefix and suffix)
            if (isHiddenSheet(sheetName)) {
                log.info("Skipping hidden sheet: {}", sheetName);
                continue;
            }

            log.info("Pre-validating schema for sheet: {}", sheetName);

            // Get schema name for this sheet
            String schemaName = getSchemaNameForSheet(sheetName, resource.getType(), localizationMap);

            if (schemaName == null) {
                // Check if it's an unknown sheet or just no validation configured
                if (!isSheetConfigured(sheetName, resource.getType(), localizationMap)) {
                    Set<String> configuredSheets = getConfiguredSheetNames(resource.getType());
                    exceptionHandler.throwCustomException(ErrorConstants.SHEET_NOT_CONFIGURED,
                            ErrorConstants.SHEET_NOT_CONFIGURED_MESSAGE
                                    .replace("{0}", sheetName)
                                    .replace("{1}", resource.getType())
                                    .replace("{2}", String.valueOf(configuredSheets)));
                }
                // Sheet is configured but has null schema (no validation required) - this is OK
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
                                    .replace("{1}", resource.getTenantId()));
                }

                schemas.put(schemaName, schema);
                log.info("Successfully fetched and cached schema: {}", schemaName);
            }
        }

        log.info("Pre-validation completed successfully. Fetched {} schemas", schemas.size());
        return schemas;
    }

    /**
     * Check if all required sheets are present in the workbook
     */
    private void checkRequiredSheetsPresent(Workbook workbook, String processingType, Map<String, String> localizationMap) {
        Map<String, String> typeConfig = sheetSchemaConfig.getConfigForType(processingType);
        if (typeConfig == null) return;
        
        // Get all non-hidden sheet names from workbook
        Set<String> workbookSheets = new HashSet<>();
        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
            String name = workbook.getSheetAt(i).getSheetName();
            if (!isHiddenSheet(name)) {
                workbookSheets.add(name);
            }
        }
        
        // Check for all configured sheets (except hidden ones)
        for (Map.Entry<String, String> entry : typeConfig.entrySet()) {
            if (!isHiddenSheet(entry.getKey())) {
                String localizedName = getLocalizedSheetName(entry.getKey(), localizationMap);
                if (!workbookSheets.contains(localizedName)) {
                    // Get localized names for all expected sheets
                    Set<String> expectedSheets = new HashSet<>();
                    for (String key : typeConfig.keySet()) {
                        if (!isHiddenSheet(key)) {
                            expectedSheets.add(getLocalizedSheetName(key, localizationMap));
                        }
                    }
                    
                    exceptionHandler.throwCustomException(ErrorConstants.REQUIRED_SHEET_MISSING,
                            ErrorConstants.REQUIRED_SHEET_MISSING_MESSAGE
                                    .replace("{0}", localizedName)
                                    .replace("{1}", expectedSheets.toString()));
                }
            }
        }
    }

    /**
     * Check if a sheet is a hidden sheet (wrapped in _h_ prefix and suffix)
     */
    private boolean isHiddenSheet(String sheetName) {
        return sheetName != null && sheetName.startsWith("_h_") && sheetName.endsWith("_h_");
    }

    /**
     * Maps sheet names to schema names using configuration (public for ExcelProcessingService)
     */
    public String getSchemaNameForSheet(String sheetName, String type, Map<String, String> localizationMap) {
        Map<String, String> typeConfig = sheetSchemaConfig.getConfigForType(type);
        if (typeConfig == null) {
            return null;
        }

        // Check each configured sheet against the provided sheet name
        for (Map.Entry<String, String> entry : typeConfig.entrySet()) {
            String sheetKey = entry.getKey();
            String schemaName = entry.getValue();

            // Get localized sheet name and trim to 31 characters if needed
            String localizedSheetName = getLocalizedSheetName(sheetKey, localizationMap);

            if (localizedSheetName.equals(sheetName)) {
                log.debug("Matched sheet '{}' to schema '{}'", sheetName, schemaName);
                return schemaName;
            }
        }

        return null;
    }

    /**
     * Check if a sheet is configured (even if schema is null)
     */
    private boolean isSheetConfigured(String sheetName, String type, Map<String, String> localizationMap) {
        Map<String, String> typeConfig = sheetSchemaConfig.getConfigForType(type);
        if (typeConfig == null) {
            return false;
        }

        // Check if any configured sheet matches the provided sheet name
        for (String sheetKey : typeConfig.keySet()) {
            String localizedSheetName = getLocalizedSheetName(sheetKey, localizationMap);
            if (localizedSheetName.equals(sheetName)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Get configured sheet names for a processing type
     */
    private Set<String> getConfiguredSheetNames(String type) {
        Map<String, String> typeConfig = sheetSchemaConfig.getConfigForType(type);
        if (typeConfig == null) {
            return java.util.Collections.emptySet();
        }
        return typeConfig.keySet();
    }

    /**
     * Gets localized sheet name and trims to 31 characters if needed
     */
    private String getLocalizedSheetName(String key, Map<String, String> localizationMap) {
        String localizedName = key;

        // Get localized value if available
        if (localizationMap != null && localizationMap.containsKey(key)) {
            localizedName = localizationMap.get(key);
        }

        // Trim to 31 characters if needed (Excel sheet name limit)
        if (localizedName.length() > 31) {
            localizedName = localizedName.substring(0, 31);
            log.debug("Trimmed sheet name from {} to {} (31 char limit)", 
                    localizationMap != null ? localizationMap.get(key) : key, localizedName);
        }

        return localizedName;
    }

    /**
     * Fetches schema from MDMS
     */
    private Map<String, Object> fetchSchemaFromMDMS(String tenantId, String schemaName,
                                                     org.egov.excelingestion.web.models.RequestInfo requestInfo) {
        try {
            // Create MDMS search criteria with title filter
            Map<String, Object> mdmsCriteria = new HashMap<>();
            mdmsCriteria.put("tenantId", tenantId);
            mdmsCriteria.put("schemaCode", "HCM-ADMIN-CONSOLE.schemas");

            // Add filters for title
            Map<String, Object> filters = new HashMap<>();
            filters.put("title", schemaName);
            mdmsCriteria.put("filters", filters);

            // Call MDMS service
            java.util.List<Map<String, Object>> mdmsResponse = mdmsService.searchMDMSData(requestInfo, mdmsCriteria);

            if (mdmsResponse != null && !mdmsResponse.isEmpty()) {
                return convertToValidationSchema(mdmsResponse.get(0));
            }

            return null;
        } catch (Exception e) {
            log.error("Error fetching schema from MDMS: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Converts MDMS response to validation schema
     */
    private Map<String, Object> convertToValidationSchema(Map<String, Object> mdmsData) {
        Map<String, Object> schema = new HashMap<>();

        // Extract properties from MDMS data structure
        if (mdmsData.containsKey("data")) {
            Object data = mdmsData.get("data");
            if (data instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> dataMap = (Map<String, Object>) data;
                schema.putAll(dataMap);
            }
        }

        return schema;
    }
}