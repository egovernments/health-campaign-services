package org.egov.excelingestion.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
@Slf4j
public class SheetSchemaConfig {
    
    private static final Map<String, Map<String, String>> TYPE_TO_SHEET_SCHEMA_MAP = new HashMap<>();
    
    static {
        initializeMicroplanIngestionConfig();
        // Add other processing type configurations here as needed
    }
    
    /**
     * Initialize configuration for microplan-ingestion type
     */
    private static void initializeMicroplanIngestionConfig() {
        Map<String, String> microplanConfig = new HashMap<>();
    
        microplanConfig.put(ProcessingConstants.FACILITIES_SHEET_KEY, ProcessingConstants.FACILITY_SCHEMA);
        microplanConfig.put(ProcessingConstants.USER_LIST_SHEET_KEY, ProcessingConstants.USER_SCHEMA);
        microplanConfig.put(ProcessingConstants.CAMP_CONF_SHEET_KEY, null); // No validation
        microplanConfig.put(ProcessingConstants.BOUNDARY_HIERARCHY_SHEET_KEY, null); // No validation
        
        TYPE_TO_SHEET_SCHEMA_MAP.put(ProcessingConstants.MICROPLAN_INGESTION, microplanConfig);
    }
    
    /**
     * Gets schema name for a specific sheet and processing type
     * 
     * @param processingType The processing type (e.g., "microplan-ingestion")
     * @param sheetKey The localization key for the sheet name
     * @return Schema name or null if no validation required
     */
    public String getSchemaNameForSheet(String processingType, String sheetKey) {
        Map<String, String> typeConfig = TYPE_TO_SHEET_SCHEMA_MAP.get(processingType);
        if (typeConfig == null) {
            log.warn("No configuration found for processing type: {}", processingType);
            return null;
        }
        
        String schemaName = typeConfig.get(sheetKey);
        log.debug("Schema mapping for type: {}, sheet: {} -> schema: {}", 
                processingType, sheetKey, schemaName);
        
        return schemaName;
    }
    
    /**
     * Gets all sheet-schema mappings for a processing type
     * 
     * @param processingType The processing type
     * @return Map of sheet keys to schema names, or null if type not found
     */
    public Map<String, String> getConfigForType(String processingType) {
        return TYPE_TO_SHEET_SCHEMA_MAP.get(processingType);
    }
    
    /**
     * Checks if a processing type is supported
     * 
     * @param processingType The processing type to check
     * @return true if supported, false otherwise
     */
    public boolean isProcessingTypeSupported(String processingType) {
        return TYPE_TO_SHEET_SCHEMA_MAP.containsKey(processingType);
    }
    
    /**
     * Gets all supported processing types
     * 
     * @return Set of supported processing type names
     */
    public java.util.Set<String> getSupportedProcessingTypes() {
        return TYPE_TO_SHEET_SCHEMA_MAP.keySet();
    }
}