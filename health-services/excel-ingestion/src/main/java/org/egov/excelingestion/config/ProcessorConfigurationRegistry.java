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
        // Unified console validation processor configuration for processing/validation
        configs.put("unified-console-validation", Arrays.asList(
                new ProcessorSheetConfig("HCM_ADMIN_CONSOLE_FACILITIES_LIST", "facility-microplan-ingestion"),
                new ProcessorSheetConfig("HCM_ADMIN_CONSOLE_USERS_LIST", "user-microplan-ingestion"),
                new ProcessorSheetConfig("HCM_CONSOLE_BOUNDARY_HIERARCHY", null, 
                        "BoundaryHierarchyTargetProcessor") // Custom processor for target validation
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
     * Config class for processing - includes persistence and event publishing options
     */
    public static class ProcessorSheetConfig {
        private final String sheetNameKey;
        private final String schemaName;
        private final String processorClass;
        private final boolean persistParsings;
        private final String triggerParsingCompleteTopic;
        
        public ProcessorSheetConfig(String sheetNameKey, String schemaName) {
            this(sheetNameKey, schemaName, null, true, null);
        }
        
        public ProcessorSheetConfig(String sheetNameKey, String schemaName, String processorClass) {
            this(sheetNameKey, schemaName, processorClass, true, null);
        }
        
        public ProcessorSheetConfig(String sheetNameKey, String schemaName, String processorClass, 
                                  boolean persistParsings, String triggerParsingCompleteTopic) {
            this.sheetNameKey = sheetNameKey;
            this.schemaName = schemaName;
            this.processorClass = processorClass;
            this.persistParsings = persistParsings;
            this.triggerParsingCompleteTopic = triggerParsingCompleteTopic;
        }
        
        public String getSheetNameKey() {
            return sheetNameKey;
        }
        
        public String getSchemaName() {
            return schemaName;
        }
        
        public String getProcessorClass() {
            return processorClass;
        }
        
        public boolean isPersistParsings() {
            return persistParsings;
        }
        
        public String getTriggerParsingCompleteTopic() {
            return triggerParsingCompleteTopic;
        }
    }
}