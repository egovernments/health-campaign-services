package org.egov.excelingestion.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.egov.excelingestion.web.models.ProcessResource;
import org.egov.excelingestion.web.models.excel.ColumnDef;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Maps localized enum dropdown values in an uploaded workbook back to their canonical MDMS values.
 *
 * <p>Templates are generated with their dropdowns in the generation locale (see
 * {@link EnumLocalizationUtil}), but everything downstream of the cell read treats the canonical
 * English value as structural: the sheet processors compare it case-sensitively, it is persisted
 * verbatim into {@code eg_cm_sheet_data_temp.rowJson}, and project-factory maps it by exact key. This
 * normalizer is the single seam where the localized label becomes canonical again, so no downstream
 * consumer needs to know localization happened.
 *
 * <p><b>Ordering.</b> Must run AFTER the immutable-baseline join (which compares the uploaded cells
 * against the generated baseline - both still localized, so it must see them in the same form) and
 * BEFORE validation, processors and persistence. It mutates the workbook cells in place; the cached
 * row maps used by validation are built afterwards, so they pick up the canonical values.
 *
 * <p>Values that match no known enum value are left untouched on purpose - the existing server-side
 * validators must still see them to raise the proper invalid-value error.
 */
@Component
@Slf4j
public class EnumValueNormalizer {

    private final SchemaColumnDefUtil schemaColumnDefUtil;
    private final ObjectMapper objectMapper;
    private final ExcelUtil excelUtil;

    public EnumValueNormalizer(SchemaColumnDefUtil schemaColumnDefUtil, ObjectMapper objectMapper,
                               ExcelUtil excelUtil) {
        this.schemaColumnDefUtil = schemaColumnDefUtil;
        this.objectMapper = objectMapper;
        this.excelUtil = excelUtil;
    }

    /**
     * Rewrites localized enum cells to canonical values across every data sheet of the workbook.
     *
     * @param sheetNameToSchema per-sheet MDMS schema (as already resolved by the processing flow)
     * @param localizationMap   the resolved generation locale's messages; the same map used to build
     *                          the dropdown at generation, so the mapping is exactly invertible
     */
    public void normalizeToCanonical(Workbook workbook, ProcessResource resource,
                                     Map<String, Map<String, Object>> sheetNameToSchema,
                                     Map<String, String> localizationMap) {
        if (workbook == null || sheetNameToSchema == null || localizationMap == null || localizationMap.isEmpty()) {
            return;
        }

        int totalNormalized = 0;
        for (Map.Entry<String, Map<String, Object>> entry : sheetNameToSchema.entrySet()) {
            String sheetName = entry.getKey();
            Sheet sheet = workbook.getSheet(sheetName);
            if (sheet == null || entry.getValue() == null || entry.getValue().isEmpty()) {
                continue;
            }

            // localized label -> canonical value, per enum column. Empty when the column has no
            // usable translation, in which case the column is skipped entirely.
            Map<String, Map<String, String>> reverseMapsByColumn =
                    buildReverseMaps(entry.getValue(), localizationMap);
            if (reverseMapsByColumn.isEmpty()) {
                continue;
            }

            totalNormalized += normalizeSheet(sheet, reverseMapsByColumn, resource, sheetName);
        }

        if (totalNormalized > 0) {
            log.info("Normalized {} localized enum cell(s) back to canonical MDMS values", totalNormalized);
        }
    }

    /** Builds the per-column reverse maps for every translated enum column in the schema. */
    private Map<String, Map<String, String>> buildReverseMaps(Map<String, Object> schemaMap,
                                                              Map<String, String> localizationMap) {
        Map<String, Map<String, String>> reverseMapsByColumn = new HashMap<>();
        String schemaJson;
        try {
            schemaJson = objectMapper.writeValueAsString(schemaMap);
        } catch (Exception e) {
            log.warn("Could not serialize schema for enum normalization: {}", e.getMessage());
            return reverseMapsByColumn; // empty -> sheet skipped, cells left as-is
        }

        for (ColumnDef column : schemaColumnDefUtil.convertSchemaToColumnDefs(schemaJson)) {
            if (!EnumLocalizationUtil.isEnumColumn(column) || column.getName() == null) {
                continue;
            }
            Map<String, String> reverseMap = EnumLocalizationUtil.buildReverseMap(
                    column.getName(), column.getEnumValues(), localizationMap);
            if (!reverseMap.isEmpty()) {
                reverseMapsByColumn.put(column.getName(), reverseMap);
            }
        }
        return reverseMapsByColumn;
    }

    /**
     * Rewrites the enum cells of one sheet. Single pass over the data rows, O(1) per cell.
     *
     * @return the number of cells rewritten
     */
    private int normalizeSheet(Sheet sheet, Map<String, Map<String, String>> reverseMapsByColumn,
                               ProcessResource resource, String sheetName) {
        Row technicalRow = sheet.getRow(0); // row 0 holds the hidden technical column names
        if (technicalRow == null) {
            return 0;
        }

        // Only the enum columns actually present on this sheet, by column index.
        Map<Integer, Map<String, String>> reverseMapsByColIndex = new HashMap<>();
        for (int col = 0; col < technicalRow.getLastCellNum(); col++) {
            Cell headerCell = technicalRow.getCell(col);
            if (headerCell == null || headerCell.getCellType() != CellType.STRING) {
                continue;
            }
            Map<String, String> reverseMap = reverseMapsByColumn.get(headerCell.getStringCellValue().trim());
            if (reverseMap != null) {
                reverseMapsByColIndex.put(col, reverseMap);
            }
        }
        if (reverseMapsByColIndex.isEmpty()) {
            return 0;
        }

        int normalized = 0;

        // Rewrite the workbook cells so the processed file returned to the user, and anything that
        // re-reads the sheet, carry canonical values.
        int lastRow = ExcelUtil.findActualLastRowWithData(sheet);
        for (int rowIdx = 2; rowIdx <= lastRow; rowIdx++) { // data starts at row 2 (0=technical, 1=headers)
            Row row = sheet.getRow(rowIdx);
            if (row == null) {
                continue;
            }
            for (Map.Entry<Integer, Map<String, String>> col : reverseMapsByColIndex.entrySet()) {
                Cell cell = row.getCell(col.getKey());
                if (cell == null || cell.getCellType() != CellType.STRING) {
                    continue;
                }
                String cellValue = cell.getStringCellValue();
                String canonical = EnumLocalizationUtil.toCanonical(cellValue, col.getValue());
                // Null = unrecognized value: leave it for the existing validators to reject.
                if (canonical != null && !canonical.equals(cellValue)) {
                    cell.setCellValue(canonical);
                    normalized++;
                }
            }
        }

        // Also rewrite the SHARED @Cacheable row maps. The immutable-baseline join already populated
        // this cache (keyed on the uploaded fileStoreId) before this pass runs, and validation,
        // processors and persistence all read that cached copy rather than re-reading the cells - so
        // rewriting only the cells above would leave every downstream consumer on localized values.
        // Same shared-instance mutation pattern as BoundaryCodeResolver.
        normalizeCachedRows(sheet, reverseMapsByColumn, resource, sheetName);

        return normalized;
    }

    /**
     * Applies the same canonical mapping to the cached row maps for this sheet, keyed by technical
     * column name. Mutating these shared instances propagates to validation/processing/persistence.
     */
    private void normalizeCachedRows(Sheet sheet, Map<String, Map<String, String>> reverseMapsByColumn,
                                     ProcessResource resource, String sheetName) {
        List<Map<String, Object>> rows = excelUtil.convertSheetToMapListCached(
                resource.getFileStoreId(), sheetName, sheet);
        if (rows == null || rows.isEmpty()) {
            return;
        }
        for (Map<String, Object> row : rows) {
            for (Map.Entry<String, Map<String, String>> col : reverseMapsByColumn.entrySet()) {
                String value = ExcelUtil.getValueAsString(row.get(col.getKey()));
                String canonical = EnumLocalizationUtil.toCanonical(value, col.getValue());
                if (canonical != null && !canonical.equals(value)) {
                    row.put(col.getKey(), canonical);
                }
            }
        }
    }
}
