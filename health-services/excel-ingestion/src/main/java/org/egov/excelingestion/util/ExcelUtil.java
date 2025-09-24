package org.egov.excelingestion.util;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;

import java.util.*;
import java.util.concurrent.TimeUnit;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

/**
 * Centralized utility for Excel operations
 */
@Slf4j
@Component
public class ExcelUtil {

    /**
     * Get cell value as string with proper formula evaluation
     * 
     * @param cell The Excel cell
     * @return String value of the cell (empty string if null or error)
     */
    public static String getCellValueAsString(Cell cell) {
        if (cell == null) return "";
        
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getDateCellValue().toString();
                } else {
                    return String.valueOf((long) cell.getNumericCellValue());
                }
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                // Evaluate formula and get the result value, not the formula itself
                try {
                    FormulaEvaluator evaluator = cell.getSheet().getWorkbook().getCreationHelper().createFormulaEvaluator();
                    CellValue cellValue = evaluator.evaluate(cell);
                    
                    switch (cellValue.getCellType()) {
                        case STRING:
                            return cellValue.getStringValue();
                        case NUMERIC:
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
        if (cell == null) return null;
        
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
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
                    FormulaEvaluator evaluator = cell.getSheet().getWorkbook().getCreationHelper().createFormulaEvaluator();
                    CellValue cellValue = evaluator.evaluate(cell);
                    
                    switch (cellValue.getCellType()) {
                        case STRING:
                            return cellValue.getStringValue();
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
     * Get localized sheet name with 31-char limit handling
     */
    public static String getLocalizedSheetName(String sheetKey, Map<String, String> localizationMap) {
        String localizedName = sheetKey;
        
        if (localizationMap != null && localizationMap.containsKey(sheetKey)) {
            localizedName = localizationMap.get(sheetKey);
        }
        
        // Handle Excel's 31 character limit
        if (localizedName.length() > 31) {
            localizedName = localizedName.substring(0, 31);
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

        final int lastRowNum = ExcelUtil.findActualLastRowWithData(sheet);
        if (lastRowNum < 2) { // No data rows
            return Collections.emptyList();
        }
        
        // Find actual last row with data (ignore formula-only rows) - SMART OPTIMIZATION
        int actualLastRow = findActualLastRowWithData(sheet);
        if (actualLastRow < 2) {
            return Collections.emptyList();
        }
        
        // Pre-allocate with actual capacity - no resizing overhead
        final List<Map<String, Object>> data = new ArrayList<>(actualLastRow - 1);

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
                        String strVal = cell.getStringCellValue();
                        value = strVal.trim().isEmpty() ? null : strVal;
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
                        String stringValue = getCellValueAsString(cell);
                        value = stringValue.trim().isEmpty() ? null : getCellValue(cell);
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
        
        return data;
    }

    private static final Cache<String, Integer> lastRowCache = Caffeine.newBuilder()
            .expireAfterWrite(5, TimeUnit.MINUTES) // entry 5 min baad expire ho jayegi
            .maximumSize(1000) // max 1000 entries rakhega
            .build();

    public static String buildCacheKey(Sheet sheet) {
        int workbookId = System.identityHashCode(sheet.getWorkbook());
        String sheetName = sheet.getSheetName();
        int sheetHash = sheetHash(sheet); // simple hash of content
        return workbookId + "::" + sheetName + "::" + sheetHash;
    }

    private static int sheetHash(Sheet sheet) {
        int hash = 1;
        for (Row row : sheet) {
            if (row == null)
                continue;
            for (Cell cell : row) {
                if (cell == null)
                    continue;
                switch (cell.getCellType()) {
                    case STRING:
                        hash = 31 * hash + cell.getStringCellValue().hashCode();
                        break;
                    case NUMERIC:
                        long bits = Double.doubleToLongBits(cell.getNumericCellValue());
                        hash = 31 * hash + (int) (bits ^ (bits >>> 32));
                        break;
                    case BOOLEAN:
                        hash = 31 * hash + Boolean.hashCode(cell.getBooleanCellValue());
                        break;
                    case FORMULA:
                        // Use formula string directly, avoid evaluating every formula
                        hash = 31 * hash + cell.getCellFormula().hashCode();
                        break;
                    default:
                        break;
                }
            }
        }
        return hash;
    }

    /**
     * Find actual last row with meaningful data (not just formulas)
     * This prevents processing thousands of empty formula rows
     */
    public static int findActualLastRowWithData(Sheet sheet) {
        log.info("Finding actual last row with data for sheet: {}", sheet.getSheetName());
        String key = buildCacheKey(sheet);

        return lastRowCache.get(key, k -> {
            log.info("Cache MISS - Finding actual last row with data for sheet: {}", sheet.getSheetName());
            int maxRowNum = sheet.getLastRowNum();
            if (maxRowNum < 0)
                return -1;

            FormulaEvaluator evaluator = sheet.getWorkbook().getCreationHelper().createFormulaEvaluator();

            int start = 0;
            int end = maxRowNum;
            int actualLast = 0;

            while (start <= end) {
                int curr = start + (end - start) / 2;
                log.info("Checking row {}", curr);

                boolean foundData = false;
                int offset = 0;

                // Exponential jump from curr
                for (int jump = 1; jump <= 512; jump *= 4) {
                    int rowNum = curr + offset;
                    if (rowNum > maxRowNum)
                        break;

                    Row row = sheet.getRow(rowNum);
                    if (row != null && row.getPhysicalNumberOfCells() > 0 && rowHasData(row, evaluator)) {
                        actualLast = rowNum; // data found
                        foundData = true;
                    }

                    offset = jump; // next jump
                }

                if (foundData) {
                    start = curr + 1; // check higher half
                } else {
                    end = curr - 1; // check lower half
                }
            }

            return actualLast > 0 ? actualLast : 1; // at least header
        });
    }

    private static boolean rowHasData(Row row, FormulaEvaluator evaluator) {
        int lastCell = row.getLastCellNum();
        for (int col = 0; col < lastCell; col++) {
            Cell cell = row.getCell(col);
            if (cell == null)
                continue;
            CellType type = cell.getCellType();

            switch (type) {
                case STRING:
                    if (!cell.getStringCellValue().trim().isEmpty())
                        return true;
                    break;
                case NUMERIC:
                case BOOLEAN:
                    return true;
                case FORMULA:
                    try {
                        CellValue value = evaluator.evaluate(cell);
                        if (value != null) {
                            switch (value.getCellType()) {
                                case STRING:
                                    if (!value.getStringValue().trim().isEmpty())
                                        return true;
                                    break;
                                case NUMERIC:
                                case BOOLEAN:
                                    return true;
                                default:
                                    break;
                            }
                        }
                    } catch (Exception ignore) {
                    }
                    break;
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
        if (value == null) {
            return "";
        }
        return value.toString();
    }
}