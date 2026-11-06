package org.egov.excelingestion.service;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Copies a source campaign's rows (users, boundary targets, facilities) into a clone's newly generated template, so a clone
 * that adds or drops Outside Household keeps its source's data; only the Outside Household sheet is the clone's own.
 * Rows are matched by phone number, facility code (or name + boundary) and boundary code; the template's own row ids stay,
 * so the upload's pre-filled-value check still matches its rows to this file.
 */
final class CloneSourceDataMerger {

    static final String ROW_ID = "HCM_ADMIN_CONSOLE____ROW_ID";
    private static final Set<String> NEVER_COPIED = Set.of(ROW_ID, "Password");
    private static final String OUTSIDE_HOUSEHOLD_PREFIX = "HCM_ADMIN_CONSOLE_OUTSIDE_HOUSEHOLD_";
    private static final String PHONE = "HCM_ADMIN_CONSOLE_USER_PHONE_NUMBER";
    private static final String FACILITY_CODE = "HCM_ADMIN_CONSOLE_FACILITY_CODE";
    private static final String FACILITY_NAME = "HCM_ADMIN_CONSOLE_FACILITY_NAME";
    private static final String BOUNDARY_CODE = "HCM_ADMIN_CONSOLE_BOUNDARY_CODE";
    private static final String OUTSIDE_HOUSEHOLD_SITE_ID = OUTSIDE_HOUSEHOLD_PREFIX + "CLIENT_REFERENCE_ID";
    private static final String OUTSIDE_HOUSEHOLD_NAME = OUTSIDE_HOUSEHOLD_PREFIX + "NAME";
    private static final String OUTSIDE_HOUSEHOLD_TYPE = OUTSIDE_HOUSEHOLD_PREFIX + "TYPE";
    // The columns a pre-filled site row may change: Site Usage and the frontline worker
    private static final List<String> OUTSIDE_HOUSEHOLD_EDITABLE = List.of(OUTSIDE_HOUSEHOLD_PREFIX + "USAGE",
            OUTSIDE_HOUSEHOLD_PREFIX + "WORKER_NAME", OUTSIDE_HOUSEHOLD_PREFIX + "WORKER_PHONE");
    private static final int FIRST_DATA_ROW = 2;   // row 0 = column keys, row 1 = localized headers

    private CloneSourceDataMerger() {
    }

    /** Copies the source's data rows into target's sheets of the same name; returns the number of rows written. */
    static int copyRows(Workbook source, Workbook target) {
        int written = 0;
        for (Sheet targetSheet : target) {
            String name = targetSheet.getSheetName();
            if (name.startsWith("_h_") || target.isSheetHidden(target.getSheetIndex(targetSheet))) {
                continue;
            }
            Map<String, Integer> targetColumns = columns(targetSheet);
            Sheet sourceSheet = source.getSheet(name);
            if (sourceSheet == null || targetColumns.keySet().stream().anyMatch(k -> k.startsWith(OUTSIDE_HOUSEHOLD_PREFIX))) {
                continue;   // the Outside Household sheet is generated for the clone
            }
            Map<String, Integer> sourceColumns = columns(sourceSheet);
            List<String> keyColumns = keyColumns(targetColumns);
            if (keyColumns == null || !sourceColumns.containsKey(keyColumns.get(0))) {
                continue;
            }
            Map<String, Integer> targetRowByKey = new HashMap<>();
            int nextFreeRow = FIRST_DATA_ROW;
            for (int r = FIRST_DATA_ROW; r <= targetSheet.getLastRowNum(); r++) {
                List<String> keys = keysOf(targetSheet.getRow(r), targetColumns, keyColumns);
                if (!keys.isEmpty()) {
                    for (String k : keys) {
                        targetRowByKey.putIfAbsent(k, r);
                    }
                    nextFreeRow = r + 1;
                }
            }
            for (int r = FIRST_DATA_ROW; r <= sourceSheet.getLastRowNum(); r++) {
                Row sourceRow = sourceSheet.getRow(r);
                List<String> keys = keysOf(sourceRow, sourceColumns, keyColumns);
                if (keys.isEmpty()) {
                    continue;
                }
                Integer targetRowIndex = keys.stream().map(targetRowByKey::get).filter(Objects::nonNull).findFirst().orElse(null);
                if (targetRowIndex == null) {
                    targetRowIndex = nextFreeRow++;
                    for (String k : keys) {
                        targetRowByKey.put(k, targetRowIndex);
                    }
                }
                Row targetRow = targetSheet.getRow(targetRowIndex) != null ? targetSheet.getRow(targetRowIndex) : targetSheet.createRow(targetRowIndex);
                for (Map.Entry<String, Integer> col : sourceColumns.entrySet()) {
                    Integer targetCol = targetColumns.get(col.getKey());
                    if (targetCol != null && !NEVER_COPIED.contains(col.getKey())) {
                        copyValue(sourceRow.getCell(col.getValue()), targetRow, targetCol);
                    }
                }
                written++;
            }
        }
        return written;
    }

    /**
     * Copies an earlier file's Outside Household rows into target's Outside Household sheet, for a campaign that drops Outside
     * Household and adds it back: a site the template already lists (same site id) takes the earlier Site Usage and
     * frontline worker, the only columns a pre-filled row may change; a row typed without a site id is added after the
     * template's rows, the earlier value replacing the template's Site Status default. The template's row ids stay, and a
     * site id the template does not list is left out. Returns the number of rows written.
     */
    static int copyOutsideHouseholdRows(Workbook source, Workbook target) {
        Sheet targetSheet = outsideHouseholdSheet(target);
        Sheet sourceSheet = outsideHouseholdSheet(source);
        if (targetSheet == null || sourceSheet == null) {
            return 0;
        }
        Map<String, Integer> targetColumns = columns(targetSheet);
        Map<String, Integer> sourceColumns = columns(sourceSheet);
        Map<String, Integer> targetRowBySite = new HashMap<>();
        int nextFreeRow = FIRST_DATA_ROW;
        for (int r = FIRST_DATA_ROW; r <= targetSheet.getLastRowNum(); r++) {
            Row row = targetSheet.getRow(r);
            String site = valueAt(row, targetColumns, OUTSIDE_HOUSEHOLD_SITE_ID);
            if (site != null) {
                targetRowBySite.putIfAbsent(site, r);
            }
            if (site != null || valueAt(row, targetColumns, OUTSIDE_HOUSEHOLD_NAME) != null) {
                nextFreeRow = r + 1;
            }
        }
        int written = 0;
        for (int r = FIRST_DATA_ROW; r <= sourceSheet.getLastRowNum(); r++) {
            Row sourceRow = sourceSheet.getRow(r);
            if (sourceRow == null) {
                continue;
            }
            String site = valueAt(sourceRow, sourceColumns, OUTSIDE_HOUSEHOLD_SITE_ID);
            if (site != null) {
                Integer targetRow = targetRowBySite.get(site);
                if (targetRow != null) {
                    for (String key : OUTSIDE_HOUSEHOLD_EDITABLE) {
                        copyOver(sourceRow, sourceColumns, targetSheet.getRow(targetRow), targetColumns, key);
                    }
                    written++;
                }
                continue;
            }
            if (valueAt(sourceRow, sourceColumns, OUTSIDE_HOUSEHOLD_NAME) == null
                    && valueAt(sourceRow, sourceColumns, OUTSIDE_HOUSEHOLD_TYPE) == null) {
                continue;
            }
            Row targetRow = targetSheet.getRow(nextFreeRow) != null ? targetSheet.getRow(nextFreeRow) : targetSheet.createRow(nextFreeRow);
            nextFreeRow++;
            for (String key : sourceColumns.keySet()) {
                if (!NEVER_COPIED.contains(key) && !OUTSIDE_HOUSEHOLD_SITE_ID.equals(key)) {
                    copyOver(sourceRow, sourceColumns, targetRow, targetColumns, key);
                }
            }
            written++;
        }
        return written;
    }

    /** Whether the workbook has an Outside Household sheet. */
    static boolean hasOutsideHouseholdSheet(Workbook workbook) {
        return outsideHouseholdSheet(workbook) != null;
    }

    /** Whether the workbook has an Outside Household sheet with at least one site row. */
    static boolean hasOutsideHouseholdRows(Workbook workbook) {
        Sheet sheet = outsideHouseholdSheet(workbook);
        if (sheet == null) {
            return false;
        }
        Map<String, Integer> columns = columns(sheet);
        for (int r = FIRST_DATA_ROW; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (valueAt(row, columns, OUTSIDE_HOUSEHOLD_SITE_ID) != null || valueAt(row, columns, OUTSIDE_HOUSEHOLD_NAME) != null) {
                return true;
            }
        }
        return false;
    }

    /** The visible sheet whose column keys include the Outside Household site name; null when there is none. */
    static Sheet outsideHouseholdSheet(Workbook workbook) {
        for (Sheet sheet : workbook) {
            if (!sheet.getSheetName().startsWith("_h_") && columns(sheet).containsKey(OUTSIDE_HOUSEHOLD_NAME)) {
                return sheet;
            }
        }
        return null;
    }

    /**
     * Writes the source row's non-blank typed value of one column into the target row, replacing a formula (the Site Status
     * default). A formula in the source is the template's own default, often saved without a value, so the target keeps its own.
     */
    private static void copyOver(Row sourceRow, Map<String, Integer> sourceColumns, Row targetRow, Map<String, Integer> targetColumns, String key) {
        Integer from = sourceColumns.get(key);
        Integer to = targetColumns.get(key);
        if (from == null || to == null || targetRow == null || text(sourceRow.getCell(from)) == null
                || sourceRow.getCell(from).getCellType() == CellType.FORMULA) {
            return;
        }
        Cell existing = targetRow.getCell(to);
        if (existing != null && existing.getCellType() == CellType.FORMULA) {
            existing.setBlank();
        }
        copyValue(sourceRow.getCell(from), targetRow, to);
    }

    /** Column key (row 0) -> column index. */
    static Map<String, Integer> columns(Sheet sheet) {
        Map<String, Integer> columns = new HashMap<>();
        Row header = sheet.getRow(0);
        if (header != null) {
            for (Cell cell : header) {
                String key = text(cell);
                if (key != null) {
                    columns.putIfAbsent(key, cell.getColumnIndex());
                }
            }
        }
        return columns;
    }

    /** The columns that identify a row: phone (users), facility code then name + boundary (facilities), boundary code. */
    private static List<String> keyColumns(Map<String, Integer> columns) {
        if (columns.containsKey(PHONE)) {
            return List.of(PHONE);
        }
        if (columns.containsKey(FACILITY_NAME)) {
            return List.of(FACILITY_NAME, FACILITY_CODE, BOUNDARY_CODE);
        }
        if (columns.containsKey(BOUNDARY_CODE)) {
            return List.of(BOUNDARY_CODE);
        }
        return null;
    }

    private static List<String> keysOf(Row row, Map<String, Integer> columns, List<String> keyColumns) {
        if (row == null) {
            return List.of();
        }
        if (!FACILITY_NAME.equals(keyColumns.get(0))) {
            String key = valueAt(row, columns, keyColumns.get(0));
            return key == null ? List.of() : List.of(key);
        }
        String name = valueAt(row, columns, FACILITY_NAME);
        String code = valueAt(row, columns, FACILITY_CODE);
        String boundary = valueAt(row, columns, BOUNDARY_CODE);
        if (code != null) {
            return name == null ? List.of("code:" + code) : List.of("code:" + code, "name:" + name + "|" + boundary);
        }
        return name == null ? List.of() : List.of("name:" + name + "|" + boundary);
    }

    static String valueAt(Row row, Map<String, Integer> columns, String key) {
        Integer col = columns.get(key);
        return col == null ? null : text(row.getCell(col));
    }

    /** A cell's value as trimmed text (whole numbers without ".0"); null when blank. Formulas give their cached value. */
    private static String text(Cell cell) {
        if (cell == null) {
            return null;
        }
        CellType type = cell.getCellType() == CellType.FORMULA ? cell.getCachedFormulaResultType() : cell.getCellType();
        String value = switch (type) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> new BigDecimal(String.valueOf(cell.getNumericCellValue())).stripTrailingZeros().toPlainString();
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            default -> null;
        };
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    /** Writes a non-blank source value; a blank source cell leaves the template's own value. */
    private static void copyValue(Cell from, Row toRow, int toColumn) {
        if (from == null) {
            return;
        }
        CellType type = from.getCellType() == CellType.FORMULA ? from.getCachedFormulaResultType() : from.getCellType();
        Cell to = toRow.getCell(toColumn) != null ? toRow.getCell(toColumn) : toRow.createCell(toColumn);
        switch (type) {
            case STRING -> {
                String s = from.getStringCellValue();
                if (s != null && !s.trim().isEmpty()) {
                    to.setCellValue(s);
                }
            }
            case NUMERIC -> to.setCellValue(from.getNumericCellValue());
            case BOOLEAN -> to.setCellValue(from.getBooleanCellValue());
            default -> { }
        }
    }
}
