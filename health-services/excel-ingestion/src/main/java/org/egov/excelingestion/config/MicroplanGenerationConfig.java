package org.egov.excelingestion.config;

import org.egov.excelingestion.web.models.ProcessorGenerationConfig;
import org.egov.excelingestion.web.models.SheetGenerationConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;

/**
 * Configuration for microplan processor generation
 */
@Configuration
public class MicroplanGenerationConfig {
    
    private final ExcelIngestionConfig config;
    
    public MicroplanGenerationConfig(ExcelIngestionConfig config) {
        this.config = config;
    }
    
    @Bean("microplanProcessorConfig")
    public ProcessorGenerationConfig getMicroplanProcessorConfig() {
        return ProcessorGenerationConfig.builder()
                .processorType("microplan-ingestion")
                .applyWorkbookProtection(true)
                .protectionPassword(config.getExcelSheetPassword())
                .zoomLevel(config.getExcelSheetZoom())
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
                        
                        // 2. Facility Sheet (schema-based with boundary columns)
                        SheetGenerationConfig.builder()
                                .sheetNameKey("HCM_ADMIN_CONSOLE_FACILITIES_LIST")
                                .schemaName("facility-microplan-ingestion")
                                .addLevelAndBoundaryColumns(true)
                                .generationClass("org.egov.excelingestion.generator.SchemaBasedSheetGenerator")
                                .isGenerationClassViaExcelPopulator(true) // Use ExcelPopulator
                                .order(2)
                                .visible(true)
                                .build(),
                        
                        // 3. User Sheet (schema-based with boundary columns)
                        SheetGenerationConfig.builder()
                                .sheetNameKey("HCM_ADMIN_CONSOLE_USERS_LIST")
                                .schemaName("user-microplan-ingestion")
                                .addLevelAndBoundaryColumns(true)
                                .generationClass("org.egov.excelingestion.generator.SchemaBasedSheetGenerator")
                                .isGenerationClassViaExcelPopulator(true) // Use ExcelPopulator
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
                .build();
    }
}