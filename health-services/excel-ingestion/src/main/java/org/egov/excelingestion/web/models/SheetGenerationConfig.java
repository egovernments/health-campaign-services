package org.egov.excelingestion.web.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Configuration for generating a single sheet in the workbook
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SheetGenerationConfig {
    
    /**
     * Unlocalised sheet name key (e.g., "HCM_ADMIN_CONSOLE_FACILITIES_LIST")
     */
    private String sheetNameKey;
    
    /**
     * Schema name to fetch from MDMS (e.g., "facility-microplan-ingestion")
     */
    private String schemaName;
    
    /**
     * Boundary column handler class name (e.g., "BoundaryColumnUtil", "HierarchicalBoundaryUtil", "SecondLevelBoundaryDropdownUtil")
     * If null or empty, no boundary columns will be added to this sheet
     */
    private String boundaryColumnsClass;
    
    /**
     * Fully qualified class name for sheet generation (e.g., "org.egov.excelingestion.generator.FacilitySheetGenerator")
     */
    private String generationClass;
    
    /**
     * If true: class returns ExcelPopulator input (columnDefs, data)
     * If false: class generates workbook directly for the sheet
     */
    private boolean isGenerationClassViaExcelPopulator;
    
    /**
     * Order in which this sheet should be created (lower numbers first)
     */
    private int order;
    
    /**
     * Whether this sheet should be visible or hidden
     */
    private boolean visible;
}