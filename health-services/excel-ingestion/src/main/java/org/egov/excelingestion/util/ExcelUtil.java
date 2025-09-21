package org.egov.excelingestion.util;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;

/**
 * Centralized utility for Excel operations
 */
@Slf4j
public class ExcelUtil {

    private ExcelUtil() {
        // Private constructor to prevent instantiation
    }

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
                    return cell.getNumericCellValue();
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
                            return cellValue.getNumberValue();
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
}