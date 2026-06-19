package org.egov.excelingestion.web.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Configuration for generating a single sheet in the workbook
 */
@Data
@Builder
@NoArgsConstructor
@Getter
@AllArgsConstructor
public class SheetGenerationConfig {
    
    /**
     * Unlocalised sheet name key (e.g., "HCM_ADMIN_CONSOLE_FACILITIES_LIST")
     */
    private String sheetName;
    
    /**
     * Schema name to fetch from MDMS (e.g., "facility-microplan-ingestion")
     */
    private String schemaName;
    
    
    /**
     * Fully qualified class name for sheet generation (e.g., "org.egov.excelingestion.generator.FacilitySheetGenerator")
     */
    private String generationClass;
    
    /**
     * If true: class returns ExcelPopulator input (columnDefs, data)
     * If false: class generates workbook directly for the sheet
     */
    private Boolean isGenerationClassViaExcelPopulator;
    
    /**
     * Order in which this sheet should be created (lower numbers first)
     */
    private Integer order;
    
    /**
     * Whether this sheet should be visible or hidden
     */
    private Boolean visible;
}