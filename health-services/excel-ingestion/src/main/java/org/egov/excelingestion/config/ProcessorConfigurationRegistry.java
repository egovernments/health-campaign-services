package org.egov.excelingestion.config;

import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Registry for processor configurations (processing/validation only)
 * Contains only sheetName and schemaName for validation purposes
 */
@Component
public class ProcessorConfigurationRegistry {
    
    private final Map<String, List<ProcessorSheetConfig>> configs;
    
    public ProcessorConfigurationRegistry() {
        this.configs = new HashMap<>();
        initializeConfigurations();
    }
    
    private void initializeConfigurations() {
        // Microplan processor configuration for processing/validation
        configs.put("microplan-ingestion", Arrays.asList(
                new ProcessorSheetConfig("HCM_CAMP_CONF_SHEETNAME", null), // No schema validation needed
                new ProcessorSheetConfig("HCM_ADMIN_CONSOLE_FACILITIES_LIST", "facility-microplan-ingestion"),
                new ProcessorSheetConfig("HCM_ADMIN_CONSOLE_USERS_LIST", "user-microplan-ingestion"),
                new ProcessorSheetConfig("HCM_CONSOLE_BOUNDARY_HIERARCHY", null) // No schema validation needed
        ));
        
        // Add more processor configurations here as needed
    }
    
    /**
     * Get processor configuration by type
     */
    public List<ProcessorSheetConfig> getConfigByType(String processorType) {
        return configs.get(processorType);
    }
    
    /**
     * Check if processor type is supported
     */
    public boolean isProcessorTypeSupported(String processorType) {
        return configs.containsKey(processorType);
    }
    
    /**
     * Get all supported processor types
     */
    public String[] getSupportedProcessorTypes() {
        return configs.keySet().toArray(new String[0]);
    }
    
    /**
     * Simple config class for processing - only sheetName and schemaName
     */
    public static class ProcessorSheetConfig {
        private final String sheetNameKey;
        private final String schemaName;
        
        public ProcessorSheetConfig(String sheetNameKey, String schemaName) {
            this.sheetNameKey = sheetNameKey;
            this.schemaName = schemaName;
        }
        
        public String getSheetNameKey() {
            return sheetNameKey;
        }
        
        public String getSchemaName() {
            return schemaName;
        }
    }
}