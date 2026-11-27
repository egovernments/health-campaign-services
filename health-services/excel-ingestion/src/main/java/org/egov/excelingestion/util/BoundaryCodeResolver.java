package org.egov.excelingestion.util;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.egov.excelingestion.config.ProcessingConstants;
import org.egov.excelingestion.web.models.ProcessResource;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Resolves the hidden boundary-code column for user-entered rows on upload.
 *
 * <p>Scaffold-less templates ({@link HierarchicalBoundaryUtil}) carry no per-row VLOOKUP formulas:
 * only prefilled rows have their code written as a value at generation. For rows the user filled,
 * the code is resolved here from the workbook's OWN lookup sheet (Section 2: display-path key ->
 * code), i.e. the exact mapping the template was generated with - so localization changes between
 * generate and upload cannot skew the resolution.
 *
 * <p>Semantics mirror the old per-row formula: the deepest non-empty level determines the lookup
 * path (levels 0..deepest joined with '#'); a path that is not in the mapping (out-of-dropdown
 * name, broken parent chain) leaves the code blank, which the boundary-selection validation then
 * flags. Cells that already carry a code (prefilled rows; legacy files whose formulas were
 * evaluated at parse time) are left untouched, so pre-redesign files behave exactly as before.
 *
 * <p>Mutates the SHARED cached row maps (same instances used by validation/processing/persistence)
 * and writes the resolved code back onto the workbook cell so the processed output file shows it.
 */
@Component
@Slf4j
public class BoundaryCodeResolver {

    private static final String MULTISELECT_MARKER = "_MULTISELECT_";

    private final ExcelUtil excelUtil;

    public BoundaryCodeResolver(ExcelUtil excelUtil) {
        this.excelUtil = excelUtil;
    }

    /**
     * Resolves blank boundary-code cells on every data sheet of the uploaded workbook.
     * No-op when the file has no lookup sheet (non-boundary templates), the resource is not a
     * join-mode type, or no hierarchy type is set.
     */
    public void resolveBlankBoundaryCodes(Workbook workbook, ProcessResource resource) {
        if (!ProcessingConstants.isJoinModeType(resource.getType())) {
            return;
        }
        String hierarchyType = resource.getHierarchyType();
        if (hierarchyType == null || hierarchyType.isEmpty()) {
            return;
        }
        Sheet lookupSheet = workbook.getSheet(HierarchicalBoundaryUtil.LOOKUP_SHEET_NAME);
        if (lookupSheet == null) {
            return; // no boundary scaffold in this file
        }
        Map<String, String> pathToCode = readPathToCodeMapping(lookupSheet);
        if (pathToCode.isEmpty()) {
            return;
        }

        String hierarchyPrefix = hierarchyType.toUpperCase() + "_";
        int resolved = 0;
        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
            Sheet sheet = workbook.getSheetAt(i);
            String sheetName = sheet.getSheetName();
            if (sheetName == null || (sheetName.startsWith("_h_") && sheetName.endsWith("_h_"))) {
                continue;
            }
            resolved += resolveSheet(sheet, sheetName, hierarchyPrefix, pathToCode, resource);
        }
        log.info("Boundary-code resolver: resolved {} user-entered rows from the workbook's lookup mapping", resolved);
    }

    private int resolveSheet(Sheet sheet, String sheetName, String hierarchyPrefix,
                             Map<String, String> pathToCode, ProcessResource resource) {
        Row header = sheet.getRow(0);
        if (header == null) {
            return 0;
        }
        // Ordered visible hierarchy-level column keys + the boundary-code column, from the hidden header row
        List<String> levelKeys = new ArrayList<>();
        Map<String, Integer> colIndex = new HashMap<>();
        Integer codeColIdx = null;
        for (int c = 0; c < header.getLastCellNum(); c++) {
            Cell cell = header.getCell(c);
            String key = cell == null ? null : ExcelUtil.getCellValueAsString(cell);
            if (key == null || key.isEmpty()) {
                continue;
            }
            colIndex.putIfAbsent(key, c);
            if (ProcessingConstants.BOUNDARY_CODE_COLUMN_KEY.equals(key)) {
                codeColIdx = c;
            } else if (key.toUpperCase().startsWith(hierarchyPrefix)
                    && !key.endsWith(ProcessingConstants.HELPER_COLUMN_SUFFIX)
                    && !key.contains(MULTISELECT_MARKER)) {
                levelKeys.add(key);
            }
        }
        if (codeColIdx == null || levelKeys.isEmpty()) {
            return 0;
        }

        // Shared @Cacheable instance - mutations propagate to validation/processing/persistence
        List<Map<String, Object>> rows = excelUtil.convertSheetToMapListCached(
                resource.getFileStoreId(), sheetName, sheet);

        int resolved = 0;
        for (Map<String, Object> row : rows) {
            String existing = ExcelUtil.getValueAsString(row.get(ProcessingConstants.BOUNDARY_CODE_COLUMN_KEY));
            if (existing != null && !existing.trim().isEmpty()) {
                continue; // prefilled or legacy-formula code - authoritative, leave untouched
            }
            String path = buildDeepestPath(row, levelKeys);
            if (path == null) {
                continue; // no boundary selection on this row
            }
            String code = pathToCode.get(path);
            if (code == null) {
                continue; // not a valid dropdown path - stays blank for the validators to flag
            }
            row.put(ProcessingConstants.BOUNDARY_CODE_COLUMN_KEY, code);
            writeCodeCell(sheet, row, codeColIdx, code);
            resolved++;
        }
        if (resolved > 0) {
            log.info("Boundary-code resolver: sheet '{}' resolved {} rows", sheetName, resolved);
        }
        return resolved;
    }

    /**
     * The lookup path exactly as the old per-row formula built it: values of levels 0..deepest
     * non-empty, joined with '#'. A blank intermediate level yields a key that is absent from the
     * mapping, so the code correctly stays blank.
     */
    private String buildDeepestPath(Map<String, Object> row, List<String> levelKeys) {
        int deepest = -1;
        List<String> values = new ArrayList<>(levelKeys.size());
        for (int i = 0; i < levelKeys.size(); i++) {
            String v = ExcelUtil.getValueAsString(row.get(levelKeys.get(i)));
            v = v == null ? "" : v.trim();
            values.add(v);
            if (!v.isEmpty()) {
                deepest = i;
            }
        }
        if (deepest < 0) {
            return null;
        }
        return String.join(HierarchicalBoundaryUtil.BOUNDARY_SEPARATOR, values.subList(0, deepest + 1));
    }

    /** Writes the resolved code onto the workbook cell so the processed output file shows it. */
    private void writeCodeCell(Sheet sheet, Map<String, Object> row, int codeColIdx, String code) {
        Object rowNum = row.get(ProcessingConstants.ACTUAL_ROW_NUMBER_KEY);
        if (!(rowNum instanceof Number)) {
            return; // map-only fallback: downstream consumers read the row map anyway
        }
        Row poiRow = sheet.getRow(((Number) rowNum).intValue() - 1);
        if (poiRow == null) {
            return;
        }
        poiRow.getCell(codeColIdx, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK).setCellValue(code);
    }

    /** Reads Section 2 of the lookup sheet: column D = display-path key, column E = code. */
    private Map<String, String> readPathToCodeMapping(Sheet lookupSheet) {
        Map<String, String> pathToCode = new HashMap<>();
        for (int r = 0; r <= lookupSheet.getLastRowNum(); r++) {
            Row row = lookupSheet.getRow(r);
            if (row == null) {
                continue;
            }
            Cell keyCell = row.getCell(HierarchicalBoundaryUtil.CODE_MAPPING_KEY_COLUMN);
            Cell codeCell = row.getCell(HierarchicalBoundaryUtil.CODE_MAPPING_CODE_COLUMN);
            String key = keyCell == null ? null : ExcelUtil.getCellValueAsString(keyCell);
            String code = codeCell == null ? null : ExcelUtil.getCellValueAsString(codeCell);
            if (key != null && !key.isEmpty() && code != null && !code.isEmpty()) {
                pathToCode.put(key, code);
            }
        }
        return pathToCode;
    }
}
