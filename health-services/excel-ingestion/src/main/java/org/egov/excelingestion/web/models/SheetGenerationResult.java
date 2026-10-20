package org.egov.excelingestion.web.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.egov.excelingestion.web.models.excel.ColumnDef;

import java.util.List;
import java.util.Map;

/**
 * Result from sheet generation class when using ExcelPopulator approach
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SheetGenerationResult {
    
    /**
     * Column definitions for the sheet
     */
    private List<ColumnDef> columnDefs;
    
    /**
     * Data rows for the sheet (null for headers-only sheets)
     */
    private List<Map<String, Object>> data;
    
    /**
     * Additional properties for sheet customization
     */
    private Map<String, Object> additionalProperties;
}