package org.egov.excelingestion.util;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

/**
 * Centralized utility for Excel operations
 */
@Slf4j
@Component
public class ExcelUtil {

    // Thread-safe, immutable — safe to share as static constant
    private static final DateTimeFormatter CELL_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /**
     * Get cell value as string with proper formula evaluation
     * 
     * @param cell The Excel cell
     * @return String value of the cell (empty string if null or error)
     */
    public static String getCellValueAsString(Cell cell) {
        return getCellValueAsString(cell, (FormulaEvaluator) null);
    }

    /**
     * Backward-compatible overload. zoneId is not used (dates use the JVM default zone to preserve
     * the calendar date); retained so existing callers keep compiling.
     */
    public static String getCellValueAsString(Cell cell, ZoneId zoneId) {
        return getCellValueAsString(cell, (FormulaEvaluator) null);
    }

    /**
     * Get cell value as string, reusing the supplied FormulaEvaluator for formula cells. Pass a
     * single evaluator when reading many cells of the same workbook to avoid re-creating an
     * evaluator per formula cell; a null evaluator falls back to a lazily created one.
     */
    public static String getCellValueAsString(Cell cell, FormulaEvaluator evaluator) {
        if (cell == null) return "";

        switch (cell.getCellType()) {
            case STRING: {
                String raw = cell.getStringCellValue();
                return raw == null ? "" : raw.trim();
            }
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    // POI creates Date in JVM default timezone — use JVM TZ to preserve calendar date
                    return cell.getDateCellValue().toInstant()
                            .atZone(ZoneId.systemDefault()).toLocalDate()
                            .format(CELL_DATE_FORMATTER);
                } else {
                    return String.valueOf((long) cell.getNumericCellValue());
                }
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                // Evaluate formula and get the result value, not the formula itself
                try {
                    FormulaEvaluator eval = (evaluator != null) ? evaluator
                            : cell.getSheet().getWorkbook().getCreationHelper().createFormulaEvaluator();
                    CellValue cellValue = eval.evaluate(cell);

                    switch (cellValue.getCellType()) {
                        case STRING: {
                            String raw = cellValue.getStringValue();
                            return raw == null ? "" : raw.trim();
                        }
                        case NUMERIC:
                            if (DateUtil.isCellDateFormatted(cell)) {
                                // POI creates Date in JVM default timezone — use JVM TZ to preserve calendar date
                                return DateUtil.getJavaDate(cellValue.getNumberValue())
                                        .toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
                                        .format(CELL_DATE_FORMATTER);
                            }
                            return String.valueOf((long) cellValue.getNumberValue());
                        case BOOLEAN:
                            return String.valueOf(cellValue.getBooleanValue());
                        default:
                            return "";
                    }
                } catch (Exception e) {
                    log.warn("Error evaluating formula in cell: {}", e.getMessage());
                    return "";
                }
            default:
                return "";
        }
    }

    /**
     * Get cell value as object (preserves type)
     * 
     * @param cell The Excel cell
     * @return Object value of the cell (null if empty or error)
     */
    public static Object getCellValue(Cell cell) {
        return getCellValue(cell, null);
    }

    /**
     * Get cell value as a typed object, reusing the supplied FormulaEvaluator for formula cells.
     * A null evaluator falls back to a lazily created one.
     */
    public static Object getCellValue(Cell cell, FormulaEvaluator evaluator) {
        if (cell == null) return null;

        switch (cell.getCellType()) {
            case STRING: {
                String raw = cell.getStringCellValue();
                return raw == null ? null : raw.trim();
            }
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getDateCellValue();
                } else {
                    return (long) cell.getNumericCellValue();
                }
            case BOOLEAN:
                return cell.getBooleanCellValue();
            case FORMULA:
                // Evaluate formula and get the result value
                try {
                    FormulaEvaluator eval = (evaluator != null) ? evaluator
                            : cell.getSheet().getWorkbook().getCreationHelper().createFormulaEvaluator();
                    CellValue cellValue = eval.evaluate(cell);

                    switch (cellValue.getCellType()) {
                        case STRING: {
                            String raw = cellValue.getStringValue();
                            return raw == null ? null : raw.trim();
                        }
                        case NUMERIC:
                            return (long) cellValue.getNumberValue();
                        case BOOLEAN:
                            return cellValue.getBooleanValue();
                        default:
                            return null;
                    }
                } catch (Exception e) {
                    log.warn("Error evaluating formula in cell: {}", e.getMessage());
                    return null;
                }
            default:
                return null;
        }
    }

    /**
     * Get localized sheet name with configurable character limit
     */
    public static String getLocalizedSheetName(String sheetKey, Map<String, String> localizationMap, int maxLength) {
        String localizedName = (localizationMap != null && localizationMap.containsKey(sheetKey))
                ? localizationMap.get(sheetKey) : sheetKey;
        if (localizedName.length() > maxLength) {
            localizedName = localizedName.substring(0, maxLength);
        }
        return localizedName;
    }

    /**
     * CACHED version of convertSheetToMapList - ULTRA-FAST OPTIMIZED VERSION
     * Cache key: fileStoreId + sheetName
     * Performance optimizations:
     * - No streams (overhead removed)
     * - Minimal method calls 
     * - Direct cell access patterns
     * - Pre-allocated memory
     * - Early exit strategies
     */
    @Cacheable(value = "excelSheetData", key = "#fileStoreId + '_' + #sheetName")
    public List<Map<String, Object>> convertSheetToMapListCached(String fileStoreId, String sheetName, Sheet sheet) {
        log.info("Cache MISS - Converting sheet to map list: {} for file: {}", sheetName, fileStoreId);
        System.out.println("Converting sheet to map list: " + sheet.getSheetName());
        Row headerRow = sheet.getRow(0);
        if (headerRow == null) {
            return Collections.emptyList();
        }

        // Fast header extraction - direct cell access
        final int headerCount = headerRow.getLastCellNum();
        if (headerCount <= 0) {
            return Collections.emptyList();
        }
        
        final String[] headers = new String[headerCount];
        for (int i = 0; i < headerCount; i++) {
            Cell cell = headerRow.getCell(i);
            headers[i] = cell != null ? getCellValueAsString(cell) : "";
        }

        // Find actual last row with data (ignore formula-only rows)
        int actualLastRow = findActualLastRowWithData(sheet);
        if (actualLastRow < 2) {
            return Collections.emptyList();
        }
        
        // Pre-allocate with actual capacity - no resizing overhead
        final List<Map<String, Object>> data = new ArrayList<>(actualLastRow - 1);

        // One evaluator reused for every formula cell in this sheet (POI caches compiled exprs);
        // avoids creating a new evaluator per formula cell.
        final FormulaEvaluator evaluator = sheet.getWorkbook().getCreationHelper().createFormulaEvaluator();

        // Ultra-fast loop - only process rows with actual data
        for (int rowNum = 2; rowNum <= actualLastRow; rowNum++) {
            Row row = sheet.getRow(rowNum);
            if (row == null) continue;

            // Pre-sized map - avoid rehashing
            Map<String, Object> rowData = new HashMap<>(headerCount + 1);
            boolean hasData = false;

            // Direct cell iteration - fastest approach
            for (int colNum = 0; colNum < headerCount; colNum++) {
                Cell cell = row.getCell(colNum);
                Object value = null;
                
                if (cell != null) {
                    // Fast empty check - avoid double conversion
                    CellType cellType = cell.getCellType();
                    if (cellType == CellType.BLANK) {
                        value = null;
                    } else if (cellType == CellType.STRING) {
                        String rawVal = cell.getStringCellValue();
                        String trimmedVal = rawVal == null ? "" : rawVal.trim();
                        value = trimmedVal.isEmpty() ? null : trimmedVal;
                        if (value != null) hasData = true;
                    } else if (cellType == CellType.NUMERIC) {
                        if (DateUtil.isCellDateFormatted(cell)) {
                            value = cell.getDateCellValue();
                            hasData = true;
                        } else {
                            long numVal = (long) cell.getNumericCellValue();
                            value = numVal;
                            hasData = true;
                        }
                    } else if (cellType == CellType.BOOLEAN) {
                        value = cell.getBooleanCellValue();
                        hasData = true;
                    } else {
                        // Handle other types (formulas etc) - fallback to string
                        String stringValue = getCellValueAsString(cell, evaluator);
                        value = stringValue.trim().isEmpty() ? null : getCellValue(cell, evaluator);
                        if (value != null) hasData = true;
                    }
                }
                
                rowData.put(headers[colNum], value);
            }

            if (hasData) {
                rowData.put("__actualRowNumber__", rowNum + 1);
                data.add(rowData);
            }
        }
        
        reconstructMultiSelectValues(data);
        return data;
    }

    /**
     * Reconstructs hidden multiselect parent column values from individual _MULTISELECT_* columns.
     * Handles backward compatibility with sheets where the CONCATENATE formula was not applied
     * beyond a certain row limit.
     */
    public static void reconstructMultiSelectValues(List<Map<String, Object>> data) {
        for (Map<String, Object> row : data) {
            // TreeMap with suffix index as key preserves column order (_MULTISELECT_1 before _MULTISELECT_2).
            // Allocated lazily — rows with no _MULTISELECT_ columns never allocate a map.
            Map<String, TreeMap<Integer, String>> parentToValues = null;

            for (Map.Entry<String, Object> entry : row.entrySet()) {
                String key = entry.getKey();
                int idx = key.indexOf("_MULTISELECT_");
                if (idx > 0) {
                    String parent = key.substring(0, idx);
                    String suffix = key.substring(idx + "_MULTISELECT_".length());
                    int suffixIndex;
                    try {
                        suffixIndex = Integer.parseInt(suffix);
                    } catch (NumberFormatException e) {
                        continue;
                    }
                    Object val = entry.getValue();
                    if (val != null && !val.toString().trim().isEmpty()) {
                        if (parentToValues == null) {
                            parentToValues = new HashMap<>();
                        }
                        parentToValues.computeIfAbsent(parent, k -> new TreeMap<>())
                                .put(suffixIndex, val.toString().trim());
                    }
                }
            }

            if (parentToValues == null) {
                continue;
            }

            for (Map.Entry<String, TreeMap<Integer, String>> entry : parentToValues.entrySet()) {
                String parent = entry.getKey();
                List<String> values = new ArrayList<>(entry.getValue().values());
                Object existing = row.get(parent);
                if ((existing == null || existing.toString().trim().isEmpty()) && !values.isEmpty()) {
                    row.put(parent, String.join(",", values));
                }
            }
        }
    }

    /**
     * Find actual last row with meaningful data (not just formulas)
     * This prevents processing thousands of empty formula rows
     */
    public static int findActualLastRowWithData(Sheet sheet) {
        log.info("Finding actual last row with data for sheet: {}", sheet.getSheetName());
        int maxRowNum = sheet.getLastRowNum();
        if (maxRowNum < 0)
            return -1;

        for (int rowNum = maxRowNum; rowNum >= 2; rowNum--) {
            Row row = sheet.getRow(rowNum);
            if (row != null && row.getPhysicalNumberOfCells() > 0 && rowHasData(row)) {
                return rowNum;
            }
        }

        return 1;
    }

    private static boolean rowHasData(Row row) {
        int lastCell = row.getLastCellNum();
        for (int col = 0; col < lastCell; col++) {
            Cell cell = row.getCell(col);
            if (cell == null)
                continue;
            CellType type = cell.getCellType();

            if (type == CellType.FORMULA) {
                if (cell.getCachedFormulaResultType() == CellType.STRING
                        && !cell.getStringCellValue().trim().isEmpty())
                    return true;
                continue;
            }

            switch (type) {
                case STRING:
                    if (!cell.getStringCellValue().trim().isEmpty())
                        return true;
                    break;
                case NUMERIC:
                case BOOLEAN:
                    return true;
                default:
                    break;
            }
        }
        return false;
    }
    /**
     * Safely convert any object value to string
     * @param value Object value from sheet data
     * @return String representation or empty string if null
     */
    public static String getValueAsString(Object value) {
        return getValueAsString(value, ZoneId.systemDefault());
    }

    /**
     * Safely convert any object value to string using the specified timezone for Date values.
     * Note: For Date objects from Apache POI (getDateCellValue), we use the JVM default timezone
     * because POI creates Date objects representing midnight in the JVM timezone. Using a different
     * timezone here would shift the date by ±1 day when JVM TZ ≠ server TZ.
     */
    public static String getValueAsString(Object value, ZoneId zoneId) {
        if (value == null) {
            return "";
        }
        if (value instanceof Date) {
            // POI creates Date in JVM default timezone — extract calendar date using JVM TZ to preserve it
            return ((Date) value).toInstant()
                    .atZone(ZoneId.systemDefault()).toLocalDate()
                    .format(CELL_DATE_FORMATTER);
        }
        return value.toString();
    }
}