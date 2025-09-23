package org.egov.excelingestion.util;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

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

        final int lastRowNum = sheet.getLastRowNum();
        if (lastRowNum < 2) { // No data rows
            return Collections.emptyList();
        }
        
        // Find actual last row with data (ignore formula-only rows) - SMART OPTIMIZATION
        int actualLastRow = findActualLastRowWithData(sheet, lastRowNum);
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

    /**
     * Find actual last row with meaningful data (not just formulas)
     * This prevents processing thousands of empty formula rows
     */
    public static int findActualLastRowWithData(Sheet sheet, int maxRowNum) {
        // Start from the end and work backwards to find last row with actual data
        for (int rowNum = maxRowNum; rowNum >= 2; rowNum--) {
            Row row = sheet.getRow(rowNum);
            if (row == null) continue;
            
            // Check if row has any meaningful data (not just formulas that return empty)
            for (int colNum = 0; colNum < row.getLastCellNum(); colNum++) {
                Cell cell = row.getCell(colNum);
                if (cell != null) {
                    CellType cellType = cell.getCellType();
                    
                    // Skip blank cells
                    if (cellType == CellType.BLANK) continue;
                    
                    // Check for actual content
                    if (cellType == CellType.STRING) {
                        String strVal = cell.getStringCellValue();
                        if (strVal != null && !strVal.trim().isEmpty()) {
                            return rowNum; // Found data!
                        }
                    } else if (cellType == CellType.NUMERIC) {
                        return rowNum; // Numeric data found
                    } else if (cellType == CellType.BOOLEAN) {
                        return rowNum; // Boolean data found
                    } else if (cellType == CellType.FORMULA) {
                        // Check if formula actually produces meaningful output
                        try {
                            String formulaResult = getCellValueAsString(cell);
                            if (formulaResult != null && !formulaResult.trim().isEmpty()) {
                                return rowNum; // Formula with result found
                            }
                        } catch (Exception e) {
                            // Ignore formula evaluation errors
                        }
                    }
                }
            }
        }
        
        // No meaningful data found, return minimum row
        return 1;
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