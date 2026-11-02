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

    /** Column key (row 0) -> column index. */
    private static Map<String, Integer> columns(Sheet sheet) {
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

    private static String valueAt(Row row, Map<String, Integer> columns, String key) {
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
