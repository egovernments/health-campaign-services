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
     * Schema name to fetch from MDMS (e.g., "facility-microplan-ingestion").
     *
     * <p>README sheet only: carries the comma-separated ReadMeConfig types instead
     * (e.g. "unified-sheet" or "user,facility,boundary"). Safe to reuse because a sheet with a
     * generationClass never goes through schema-based generation - see
     * ConfigBasedGenerationService#shouldUseSchemaBasedGeneration, which requires generationClass to be
     * empty - and the upload path reads its schema from the separate excelIngestionProcess master.
     * Reused rather than adding a field because the MDMS excelIngestionGenerate schema sets
     * "additionalProperties": false on each sheet, so an unknown key is rejected on save.
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

    /**
     * If true, skip fetching schema columns for this sheet (e.g., boundary data sheet without target columns)
     */
    private Boolean skipSchemaColumns;

    /**
     * Boundary filter configuration for this sheet.
     * Controls which users are included based on their boundary relative to the register locality.
     * If null, defaults to self-only (register locality exact match).
     */
    private BoundaryFilterConfig boundaryFilter;

    /**
     * README sheet only: the resource types whose MDMS ReadMeConfig instruction blocks are merged into
     * this sheet, in render order (e.g. ["user", "facility", "boundary"]). Lets one README cover a
     * combined workbook.
     *
     * <p>Optional override, and NOT settable from MDMS today: the excelIngestionGenerate schema
     * forbids unknown sheet keys, so authors put the list in {@link #schemaName} as a comma-separated
     * string. Kept for programmatic callers and for the day the schema gains the field; when it is
     * null the generator falls back to schemaName, then to the generation type.
     */
    private List<String> readMeTypes;
}