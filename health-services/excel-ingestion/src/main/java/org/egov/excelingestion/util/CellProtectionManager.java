package org.egov.excelingestion.util;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.egov.excelingestion.config.ExcelIngestionConfig;
import org.egov.excelingestion.web.models.excel.ColumnDef;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Comprehensive cell protection management utility for Excel sheets.
 * Handles dynamic cell locking/unlocking features based on data state.
 */
@Component
@Slf4j
public class CellProtectionManager {

    private final ExcelIngestionConfig config;
    private final ExcelStyleHelper excelStyleHelper;

    public CellProtectionManager(ExcelIngestionConfig config, ExcelStyleHelper excelStyleHelper) {
        this.config = config;
        this.excelStyleHelper = excelStyleHelper;
    }

    /**
     * Apply comprehensive cell protection to a sheet based on column definitions
     *
     * @param workbook The workbook containing the sheet
     * @param sheet The sheet to apply protection to
     * @param columns List of column definitions with protection settings
     * @return The modified workbook with protection applied
     */
    public Workbook applyCellProtection(Workbook workbook, Sheet sheet, List<ColumnDef> columns) {
        log.info("Applying cell protection to sheet: {}", sheet.getSheetName());
        
        // Create styles for locked and unlocked cells
        CellStyle lockedStyle = excelStyleHelper.createLockedCellStyle(workbook);
        CellStyle unlockedStyle = excelStyleHelper.createUnlockedCellStyle(workbook);
        
        // Find the last row with data for data-aware protection features
        int lastDataRow = ExcelUtil.findActualLastRowWithData(sheet);
        log.info("Last data row found at: {}", lastDataRow);
        
        // Find starting column index for data columns (after boundary columns)
        int startCol = findDataColumnStartIndex(sheet, columns);
        log.info("Data columns start at index: {}", startCol);
        
        // Apply protection logic to all data rows
        int protectedCells = 0;
        int unprotectedCells = 0;
        
        // Important: We need to apply styles to a reasonable number of rows
        // to ensure protection works properly even for empty rows
        // For sheets with lots of data (like target sheets), protect all data rows
        int maxRowsToProtect = Math.max(lastDataRow, config.getExcelRowLimit());
        
        for (int rowIdx = 2; rowIdx <= maxRowsToProtect; rowIdx++) {
            Row row = sheet.getRow(rowIdx);
            if (row == null) {
                row = sheet.createRow(rowIdx);
            }
            
            for (int i = 0; i < columns.size(); i++) {
                int colIdx = startCol + i; // Use correct column index
                ColumnDef column = columns.get(i);
                Cell cell = row.getCell(colIdx);
                if (cell == null) {
                    cell = row.createCell(colIdx);
                }
                
                // Use comprehensive protection logic
                boolean shouldLock = determineCellLockState(column, cell, rowIdx, lastDataRow);
                
                CellStyle styleToApply = shouldLock ? lockedStyle : unlockedStyle;
                cell.setCellStyle(styleToApply);
                
                if (shouldLock) {
                    protectedCells++;
                } else {
                    unprotectedCells++;
                }
            }
        }
        
        log.info("Cell protection applied - Protected: {}, Unprotected: {}, LastDataRow: {}", 
                protectedCells, unprotectedCells, lastDataRow);
        return workbook;
    }

    /**
     * Determine if a cell should be locked based on column protection settings
     *
     * @param column The column definition with protection settings
     * @param cell The cell to evaluate
     * @param rowIdx The current row index
     * @param lastDataRow The last row containing data
     * @return true if the cell should be locked, false otherwise
     */
    private boolean determineCellLockState(ColumnDef column, Cell cell, int rowIdx, int lastDataRow) {
        // Priority order for protection rules:
        
        // 1. unFreezeColumnTillData - Highest priority
        // Unlock cells till data exists, lock cells after last data row
        if (column.isUnFreezeColumnTillData()) {
            if (rowIdx <= lastDataRow) {
                log.trace("Cell UNLOCKED by unFreezeColumnTillData at row {} for column {} (row <= lastDataRow {})", 
                        rowIdx, column.getName(), lastDataRow);
                return false; // Unlock where data exists
            } else {
                log.trace("Cell LOCKED by unFreezeColumnTillData at row {} for column {} (row > lastDataRow {})", 
                        rowIdx, column.getName(), lastDataRow);
                return true; // Lock empty rows after data
            }
        }
        
        // 2. freezeColumn - Permanent column locking (second highest priority)
        if (column.isFreezeColumn()) {
            log.debug("Cell locked by freezeColumn for column {}", column.getName());
            return true;
        }
        
        // 3. freezeTillData - Lock cells until last data row
        if (column.isFreezeTillData() && rowIdx <= lastDataRow) {
            log.debug("Cell locked by freezeTillData at row {} for column {}", rowIdx, column.getName());
            return true;
        }
        
        // 4. freezeColumnIfFilled - Conditional locking based on cell content
        if (column.isFreezeColumnIfFilled() && cellHasValue(cell)) {
            log.debug("Cell locked by freezeColumnIfFilled (has value) for column {}", column.getName());
            return true;
        }
        
        // 5. Default - Unlocked for editing
        return false;
    }

    /**
     * Apply sheet-level protection with password
     *
     * @param workbook The workbook to protect
     * @param sheet The sheet to protect
     * @param password The protection password
     * @return The protected workbook
     */
    public Workbook applySheetProtection(Workbook workbook, Sheet sheet, String password) {
        log.info("Applying sheet protection to: {}", sheet.getSheetName());
        
        // Protect sheet with password - allows users to edit unlocked cells
        sheet.protectSheet(password);
        
        return workbook;
    }

    /**
     * Apply workbook-level protection with password
     *
     * @param workbook The workbook to protect
     * @param password The protection password
     * @return The protected workbook
     */
    public Workbook applyWorkbookProtection(Workbook workbook, String password) {
        log.info("Applying workbook structure protection");
        
        // Lock workbook structure to prevent sheet modifications
        // Note: lockStructure() is specific to XSSFWorkbook
        if (workbook instanceof XSSFWorkbook) {
            ((XSSFWorkbook) workbook).lockStructure();
        }
        
        return workbook;
    }

    /**
     * Apply comprehensive protection (cells + sheet + workbook structure)
     *
     * @param workbook The workbook to protect
     * @param sheet The sheet to protect
     * @param columns List of column definitions with protection settings
     * @param password The protection password
     * @return The fully protected workbook
     */
    public Workbook applyComprehensiveProtection(Workbook workbook, Sheet sheet, 
                                               List<ColumnDef> columns, String password) {
        log.info("Applying comprehensive protection to workbook and sheet: {}", sheet.getSheetName());
        
        // Step 1: Apply cell-level protection
        applyCellProtection(workbook, sheet, columns);
        
        // Step 2: Apply sheet protection
        applySheetProtection(workbook, sheet, password);
        
        // Step 3: Apply workbook structure protection
        applyWorkbookProtection(workbook, password);
        
        log.info("Comprehensive protection applied successfully");
        return workbook;
    }

    /**
     * Re-evaluate and update cell protection after data changes
     *
     * @param workbook The workbook containing the sheet
     * @param sheet The sheet to re-evaluate
     * @param columns List of column definitions with protection settings
     * @return The workbook with updated protection
     */
    public Workbook updateCellProtection(Workbook workbook, Sheet sheet, List<ColumnDef> columns) {
        log.info("Re-evaluating cell protection for sheet: {}", sheet.getSheetName());
        
        // Remove existing protection temporarily if needed
        // Note: In practice, this would require unprotecting the sheet first
        
        // Re-apply protection with current data state
        return applyCellProtection(workbook, sheet, columns);
    }

    /**
     * Find the starting column index for data columns (after boundary columns)
     * This handles the case where boundary columns are added first, then data columns
     */
    private int findDataColumnStartIndex(Sheet sheet, List<ColumnDef> columns) {
        Row visibleRow = sheet.getRow(1);
        if (visibleRow == null) {
            return 0; // No headers, start from beginning
        }
        
        int totalExistingCols = visibleRow.getLastCellNum();
        if (totalExistingCols <= 0) {
            return 0; // No existing columns
        }
        
        // Data columns start after boundary columns
        // If we have N total columns and M data column definitions,
        // then data columns start at position (N - M)
        int startCol = Math.max(0, totalExistingCols - columns.size());
        return startCol;
    }
    
    
    /**
     * Check if a cell has a value (not blank and not empty string)
     */
    private boolean cellHasValue(Cell cell) {
        if (cell == null) return false;
        
        switch (cell.getCellType()) {
            case STRING:
                return !cell.getStringCellValue().trim().isEmpty();
            case NUMERIC:
                return true;
            case BOOLEAN:
                return true;
            case FORMULA:
                return true;
            default:
                return false;
        }
    }

    /**
     * Get protection statistics for a sheet
     *
     * @param sheet The sheet to analyze
     * @param columns List of column definitions
     * @return Protection statistics as a formatted string
     */
    public String getProtectionStatistics(Sheet sheet, List<ColumnDef> columns) {
        int totalCells = 0;
        int protectedCells = 0;
        int lastDataRow = ExcelUtil.findActualLastRowWithData(sheet);
        int startCol = findDataColumnStartIndex(sheet, columns);
        int actualLastRow = Math.max(ExcelUtil.findActualLastRowWithData(sheet), config.getExcelRowLimit());
        for (int rowIdx = 2; rowIdx <= actualLastRow; rowIdx++) {
            Row row = sheet.getRow(rowIdx);
            if (row != null) {
                for (int i = 0; i < columns.size(); i++) {
                    int colIdx = startCol + i; // Use correct column index
                    totalCells++;
                    ColumnDef column = columns.get(i);
                    Cell cell = row.getCell(colIdx);
                    if (cell != null && determineCellLockState(column, cell, rowIdx, lastDataRow)) {
                        protectedCells++;
                    }
                }
            }
        }
        
        return String.format("Protection Stats - Total: %d, Protected: %d, Editable: %d, Last Data Row: %d", 
                           totalCells, protectedCells, totalCells - protectedCells, lastDataRow);
    }
}