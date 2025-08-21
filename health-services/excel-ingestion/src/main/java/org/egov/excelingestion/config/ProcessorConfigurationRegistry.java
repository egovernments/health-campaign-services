package org.egov.excelingestion.config;

import org.egov.excelingestion.web.models.ProcessorGenerationConfig;
import org.egov.excelingestion.web.models.SheetGenerationConfig;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Registry for all processor configurations
 */
@Component
public class ProcessorConfigurationRegistry {
    
    private final Map<String, ProcessorGenerationConfig> configs;
    private final ExcelIngestionConfig excelConfig;
    
    public ProcessorConfigurationRegistry(ExcelIngestionConfig excelConfig) {
        this.excelConfig = excelConfig;
        this.configs = new HashMap<>();
        initializeConfigurations();
    }
    
    private void initializeConfigurations() {
        // Microplan processor configuration
        configs.put("microplan-ingestion", ProcessorGenerationConfig.builder()
                .processorType("microplan-ingestion")
                .applyWorkbookProtection(true)
                .protectionPassword(excelConfig.getExcelSheetPassword())
                .zoomLevel(excelConfig.getExcelSheetZoom())
                .sheets(Arrays.asList(
                        // 1. Campaign Configuration Sheet (first sheet, directly generated)
                        SheetGenerationConfig.builder()
                                .sheetNameKey("HCM_CAMP_CONF_SHEETNAME")
                                .schemaName(null) // No schema needed
                                .addLevelAndBoundaryColumns(false)
                                .generationClass("org.egov.excelingestion.generator.CampaignConfigSheetGenerator")
                                .isGenerationClassViaExcelPopulator(false) // Direct workbook generation
                                .order(1)
                                .visible(true)
                                .build(),
                        
                        // 2. Facility Sheet (automatic schema-based with boundary columns)
                        SheetGenerationConfig.builder()
                                .sheetNameKey("HCM_ADMIN_CONSOLE_FACILITIES_LIST")
                                .schemaName("facility-microplan-ingestion")
                                .addLevelAndBoundaryColumns(true)
                                .order(2)
                                .visible(true)
                                .build(),
                        
                        // 3. User Sheet (automatic schema-based with boundary columns)
                        SheetGenerationConfig.builder()
                                .sheetNameKey("HCM_ADMIN_CONSOLE_USERS_LIST")
                                .schemaName("user-microplan-ingestion")
                                .addLevelAndBoundaryColumns(true)
                                .order(3)
                                .visible(true)
                                .build(),
                        
                        // 4. Boundary Hierarchy Sheet (custom data generation)
                        SheetGenerationConfig.builder()
                                .sheetNameKey("HCM_CONSOLE_BOUNDARY_HIERARCHY")
                                .schemaName(null) // No schema needed
                                .addLevelAndBoundaryColumns(false)
                                .generationClass("org.egov.excelingestion.generator.BoundaryHierarchySheetGenerator")
                                .isGenerationClassViaExcelPopulator(true) // Use ExcelPopulator
                                .order(4)
                                .visible(true)
                                .build()
                ))
                .build());
                
        // Add more processor configurations here as needed
    }
    
    /**
     * Get processor configuration by type
     */
    public ProcessorGenerationConfig getConfigByType(String processorType) {
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
}