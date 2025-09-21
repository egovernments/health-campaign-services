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
    
    private final Map<String, ProcessorTypeConfig> configs;
    private final KafkaTopicConfig kafkaTopicConfig;
    
    public ProcessorConfigurationRegistry(KafkaTopicConfig kafkaTopicConfig) {
        this.kafkaTopicConfig = kafkaTopicConfig;
        this.configs = new HashMap<>();
        initializeConfigurations();
    }
    
    private void initializeConfigurations() {
        // Unified console validation processor configuration for processing/validation
        configs.put("unified-console-validation", new ProcessorTypeConfig(
                null, // No processing result topic for validation-only
                Arrays.asList(
                        new ProcessorSheetConfig("HCM_ADMIN_CONSOLE_FACILITIES_LIST", "facility-microplan-ingestion",
                        "FacilityValidationProcessor"),
                        new ProcessorSheetConfig("HCM_ADMIN_CONSOLE_USERS_LIST", "user-microplan-ingestion", 
                                "UserValidationProcessor"), // Custom processor for user validation
                        new ProcessorSheetConfig("HCM_CONSOLE_BOUNDARY_HIERARCHY", null, 
                                "BoundaryHierarchyTargetProcessor") // Custom processor for target validation
                )
        ));

        // Unified console parse processor configuration for processing/validation
        configs.put("unified-console-parse", new ProcessorTypeConfig(
                kafkaTopicConfig.getProcessingResultTopic(), // Processing result topic for completed processing
                Arrays.asList(
                        new ProcessorSheetConfig("HCM_ADMIN_CONSOLE_FACILITIES_LIST", "facility-microplan-ingestion", null, true),
                        new ProcessorSheetConfig("HCM_ADMIN_CONSOLE_USERS_LIST", "user-microplan-ingestion", 
                                "UserValidationProcessor", true), // Custom processor for user validation
                        new ProcessorSheetConfig("HCM_CONSOLE_BOUNDARY_HIERARCHY", null,
                                "BoundaryHierarchyTargetProcessor", true) // Custom processor for target validation
                )
        ));
        
        // Add more processor configurations here as needed
    }
    
    /**
     * Get processor configuration by type
     */
    public List<ProcessorSheetConfig> getConfigByType(String processorType) {
        ProcessorTypeConfig config = configs.get(processorType);
        return config != null ? config.getSheets() : null;
    }
    
    /**
     * Get processing result topic by type
     */
    public String getProcessingResultTopic(String processorType) {
        ProcessorTypeConfig config = configs.get(processorType);
        return config != null ? config.getProcessingResultTopic() : null;
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
     * Config class for processor type - includes processing result topic and sheet configurations
     */
    public static class ProcessorTypeConfig {
        private final String processingResultTopic;
        private final List<ProcessorSheetConfig> sheets;
        
        public ProcessorTypeConfig(String processingResultTopic, List<ProcessorSheetConfig> sheets) {
            this.processingResultTopic = processingResultTopic;
            this.sheets = sheets;
        }
        
        public String getProcessingResultTopic() {
            return processingResultTopic;
        }
        
        public List<ProcessorSheetConfig> getSheets() {
            return sheets;
        }
    }
    
    /**
     * Config class for processing - includes persistence options
     */
    public static class ProcessorSheetConfig {
        private final String sheetNameKey;
        private final String schemaName;
        private final String processorClass;
        private final boolean persistParsings;
        
        public ProcessorSheetConfig(String sheetNameKey, String schemaName) {
            this(sheetNameKey, schemaName, null, true);
        }
        
        public ProcessorSheetConfig(String sheetNameKey, String schemaName, String processorClass) {
            this(sheetNameKey, schemaName, processorClass, true);
        }
        
        public ProcessorSheetConfig(String sheetNameKey, String schemaName, String processorClass, 
                                  boolean persistParsings) {
            this.sheetNameKey = sheetNameKey;
            this.schemaName = schemaName;
            this.processorClass = processorClass;
            this.persistParsings = persistParsings;
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
    }
}