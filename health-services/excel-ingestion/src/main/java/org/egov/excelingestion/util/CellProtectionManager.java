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

        // The empty paste area (rows lastDataRow+1 .. excelRowLimit) used to be MATERIALIZED here as
        // ~excelRowLimit real Row/Cell objects each carrying a locked/unlocked style. For a template with
        // only a handful of pre-filled rows that meant tens/hundreds of thousands of empty styled cells,
        // which is the dominant driver of the multi-MB file size and the GB-scale POI heap on generation.
        //
        // Instead we apply the empty-region lock intent ONCE per data column via the sheet's DEFAULT
        // COLUMN STYLE (an O(columns) XML attribute, materializes zero cells). Empty cells the user pastes
        // into inherit that column default, so sheet protection still keeps the paste area editable exactly
        // as before. We then explicitly style ONLY the rows that actually carry data (2 .. lastDataRow),
        // which is where per-cell decisions (freezeColumnIfFilled) genuinely need a real cell.
        for (int i = 0; i < columns.size(); i++) {
            int colIdx = startCol + i;
            ColumnDef column = columns.get(i);
            boolean emptyRegionLocked = isEmptyRegionLocked(column, lastDataRow);
            sheet.setDefaultColumnStyle(colIdx, emptyRegionLocked ? lockedStyle : unlockedStyle);
        }

        // Style only the materialized data rows. When the sheet is truly empty (lastDataRow <= 1) this
        // loop runs zero times and the column default styles above carry all the protection.
        for (int rowIdx = 2; rowIdx <= lastDataRow; rowIdx++) {
            Row row = sheet.getRow(rowIdx);
            if (row == null) {
                // A gap row inside the data region: its lock state matches the column default already
                // applied above, so there is no need to materialize it.
                continue;
            }

            for (int i = 0; i < columns.size(); i++) {
                int colIdx = startCol + i; // Use correct column index
                ColumnDef column = columns.get(i);
                Cell cell = row.getCell(colIdx);
                if (cell == null) {
                    // Empty cell within a data row inherits the column default style; only materialize +
                    // style it when its intended lock state differs from that default.
                    boolean shouldLockEmpty = determineCellLockState(column, null, rowIdx, lastDataRow);
                    boolean colDefaultLocked = isEmptyRegionLocked(column, lastDataRow);
                    if (shouldLockEmpty == colDefaultLocked) {
                        if (shouldLockEmpty) protectedCells++; else unprotectedCells++;
                        continue;
                    }
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

        log.info("Cell protection applied - Protected: {}, Unprotected: {}, LastDataRow: {} (empty paste area covered by default column styles, not materialized rows)",
                protectedCells, unprotectedCells, lastDataRow);
        return workbook;
    }

    /**
     * Lock state for the EMPTY paste region of a column (rows after {@code lastDataRow}, no value).
     * This mirrors {@link #determineCellLockState} for an empty cell located past the last data row, so
     * the single default column style we set is identical to what per-cell styling would have produced
     * for every empty row — just without materializing those rows.
     *
     * @return true if empty cells in this column should be LOCKED by default, false if UNLOCKED
     */
    private boolean isEmptyRegionLocked(ColumnDef column, int lastDataRow) {
        // Matches determineCellLockState(column, emptyCell, rowIdx > lastDataRow, lastDataRow):
        // 1. unFreezeColumnTillData: empty template (lastDataRow<=1) -> unlocked; otherwise rows past
        //    the data are LOCKED.
        if (column.isUnFreezeColumnTillData()) {
            return lastDataRow > 1;
        }
        // 2. freezeColumn: always locked.
        if (column.isFreezeColumn()) {
            return true;
        }
        // 3. freezeTillData: locks only rows <= lastDataRow; the empty region is beyond that -> unlocked.
        // 4. freezeColumnIfFilled: locks only cells WITH a value; empty cells -> unlocked.
        // 5. default: unlocked.
        return false;
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
            // lastDataRow <= 1 means only header rows exist (empty template).
            // Boundary code formulas evaluate to "" so findActualLastRowWithData returns 1.
            // In this case unlock all rows to allow data entry on a fresh template.
            if (lastDataRow <= 1) {
                log.trace("Cell UNLOCKED by unFreezeColumnTillData (empty template) at row {} for column {}",
                        rowIdx, column.getName());
                return false;
            }
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
        String name = sheet.getSheetName();
        // Never protect hidden helper sheets (_h_...._h_): they carry lookup/meta data the pipeline reads
        // and must stay freely writable.
        if (name != null && name.startsWith("_h_") && name.endsWith("_h_")) {
            return workbook;
        }
        log.info("Applying sheet protection to: {}", name);

        // Protect sheet with password - POI leaves permissive defaults so users can still edit/paste
        // into UNLOCKED cells while locked cells stay read-only.
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