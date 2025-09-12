package org.egov.excelingestion.config;

import org.egov.excelingestion.web.models.ProcessorGenerationConfig;
import org.egov.excelingestion.web.models.SheetGenerationConfig;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Registry for all generator configurations
 */
@Component
public class GeneratorConfigurationRegistry {
    
    private final Map<String, ProcessorGenerationConfig> configs;
    private final ExcelIngestionConfig excelConfig;
    
    public GeneratorConfigurationRegistry(ExcelIngestionConfig excelConfig) {
        this.excelConfig = excelConfig;
        this.configs = new HashMap<>();
        initializeConfigurations();
    }
    
    private void initializeConfigurations() {
        // Unified console processor configuration
        configs.put("unified-console", ProcessorGenerationConfig.builder()
                .applyWorkbookProtection(true)
                .protectionPassword(excelConfig.getExcelSheetPassword())
                .zoomLevel(excelConfig.getExcelSheetZoom())
                .sheets(Arrays.asList(
                        // 1. Facility Sheet (automatic schema-based with hierarchical boundary columns)
                        SheetGenerationConfig.builder()
                                .sheetNameKey("HCM_ADMIN_CONSOLE_FACILITIES_LIST")
                                .schemaName("facility-microplan-ingestion")
                                .boundaryColumnsClass("HierarchicalBoundaryUtil")
                                .order(1)
                                .visible(true)
                                .build(),
                        
                        // 2. User Sheet (automatic schema-based with traditional boundary columns)
                        SheetGenerationConfig.builder()
                                .sheetNameKey("HCM_ADMIN_CONSOLE_USERS_LIST")
                                .schemaName("user-microplan-ingestion")
                                .boundaryColumnsClass("HierarchicalBoundaryUtil")
                                .order(2)
                                .visible(true)
                                .build(),
                        
                        // 3. Boundary Hierarchy Sheet (custom data generation)
                        SheetGenerationConfig.builder()
                                .sheetNameKey("HCM_CONSOLE_BOUNDARY_HIERARCHY")
                                .schemaName(null) // No schema needed
                                .boundaryColumnsClass(null) // No boundary columns needed
                                .generationClass("BoundaryHierarchySheetGenerator")
                                .isGenerationClassViaExcelPopulator(true) // Use ExcelPopulator
                                .order(3)
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