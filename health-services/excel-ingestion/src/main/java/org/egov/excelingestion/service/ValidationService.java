package org.egov.excelingestion.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.egov.excelingestion.config.ExcelIngestionConfig;
import org.egov.excelingestion.config.ValidationConstants;
import org.egov.excelingestion.util.CellProtectionManager;
import org.egov.excelingestion.util.LocalizationUtil;
import org.egov.excelingestion.web.models.ValidationError;
import org.egov.excelingestion.web.models.ValidationColumnInfo;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ValidationService {

    private final ExcelIngestionConfig config;
    private final CellProtectionManager cellProtectionManager;

    public ValidationService(ExcelIngestionConfig config, CellProtectionManager cellProtectionManager) {
        this.config = config;
        this.cellProtectionManager = cellProtectionManager;
    }

    /**
     * Finds the last column with data in the header row
     */
    private int findLastDataColumn(Sheet sheet) {
        Row headerRow = sheet.getRow(0);
        if (headerRow == null) return 0;
        
        int lastColumn = 0;
        for (int i = headerRow.getLastCellNum() - 1; i >= 0; i--) {
            Cell cell = headerRow.getCell(i);
            if (cell != null && cell.getCellType() != CellType.BLANK) {
                lastColumn = i;
                break;
            }
        }
        return lastColumn;
    }

    /**
     * Finds or adds status and error columns to the sheet
     */
    public ValidationColumnInfo addValidationColumns(Sheet sheet) {
        return addValidationColumns(sheet, null);
    }
    
    /**
     * Finds or adds status and error columns to the sheet with localization support
     */
    public ValidationColumnInfo addValidationColumns(Sheet sheet, Map<String, String> localizationMap) {
        Row headerRow = sheet.getRow(0);
        if (headerRow == null) {
            headerRow = sheet.createRow(0);
        }
        
        Row visibleRow = sheet.getRow(1);
        if (visibleRow == null) {
            visibleRow = sheet.createRow(1);
        }

        // Check if status and error columns already exist
        int statusColumnIndex = findColumnByName(headerRow, "#status#");
        int errorColumnIndex = findColumnByName(headerRow, "#errorDetails#");

        // If columns don't exist, add them at the end
        if (statusColumnIndex == -1) {
            int lastDataColumn = findLastDataColumn(sheet);
            statusColumnIndex = lastDataColumn + 1;
            
            // Row 0: Technical name
            Cell statusCell = headerRow.createCell(statusColumnIndex);
            statusCell.setCellValue(ValidationConstants.STATUS_COLUMN_NAME);
            applyCellStyle(statusCell, true);
            
            // Row 1: Localized visible header with yellow background
            Cell statusVisibleCell = visibleRow.createCell(statusColumnIndex);
            String statusDisplayName = (localizationMap != null && localizationMap.containsKey(ValidationConstants.STATUS_COLUMN_NAME))
                ? localizationMap.get(ValidationConstants.STATUS_COLUMN_NAME)
                : ValidationConstants.STATUS_COLUMN_NAME;
            statusVisibleCell.setCellValue(statusDisplayName);
            applyYellowHeaderStyle(statusVisibleCell);
            
            sheet.setColumnWidth(statusColumnIndex, 5000); // ~20 characters
        }

        if (errorColumnIndex == -1) {
            int lastDataColumn = findLastDataColumn(sheet);
            errorColumnIndex = statusColumnIndex == lastDataColumn + 1 ? lastDataColumn + 2 : lastDataColumn + 1;
            
            // Row 0: Technical name
            Cell errorCell = headerRow.createCell(errorColumnIndex);
            errorCell.setCellValue(ValidationConstants.ERROR_DETAILS_COLUMN_NAME);
            applyCellStyle(errorCell, true);
            
            // Row 1: Localized visible header with yellow background
            Cell errorVisibleCell = visibleRow.createCell(errorColumnIndex);
            String errorDisplayName = (localizationMap != null && localizationMap.containsKey(ValidationConstants.ERROR_DETAILS_COLUMN_NAME))
                ? localizationMap.get(ValidationConstants.ERROR_DETAILS_COLUMN_NAME)
                : ValidationConstants.ERROR_DETAILS_COLUMN_NAME;
            errorVisibleCell.setCellValue(errorDisplayName);
            applyYellowHeaderStyle(errorVisibleCell);
            
            sheet.setColumnWidth(errorColumnIndex, 10000); // ~40 characters
        }

        return new ValidationColumnInfo(statusColumnIndex, errorColumnIndex);
    }

    /**
     * Finds a column by its header name
     */
    private int findColumnByName(Row headerRow, String columnName) {
        for (int i = 0; i < headerRow.getLastCellNum(); i++) {
            Cell cell = headerRow.getCell(i);
            if (cell != null && cell.getCellType() == CellType.STRING) {
                String headerValue = cell.getStringCellValue().trim();
                if (columnName.equals(headerValue)) {
                    return i;
                }
            }
        }
        return -1; // Column not found
    }

    /**
     * Processes validation errors and adds them to the sheet
     */
    public void processValidationErrors(Sheet sheet, List<ValidationError> errors, ValidationColumnInfo columnInfo, Map<String, String> localizationMap) {
        if (errors == null || errors.isEmpty()) {
            return;
        }

        // Group errors by row number
        Map<Integer, List<ValidationError>> errorsByRow = errors.stream()
                .filter(error -> error.getRowNumber() != null)
                .collect(Collectors.groupingBy(ValidationError::getRowNumber));

        // Process each row with errors
        for (Map.Entry<Integer, List<ValidationError>> entry : errorsByRow.entrySet()) {
            int rowNumber = entry.getKey();
            List<ValidationError> rowErrors = entry.getValue();

            Row row = sheet.getRow(rowNumber - 1); // Convert to 0-based index
            if (row == null) {
                row = sheet.createRow(rowNumber - 1);
            }

            // Merge error messages for the row - robust empty message filtering
            List<String> validMessages = rowErrors.stream()
                    .map(ValidationError::getErrorDetails)
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(msg -> !msg.isEmpty() && !msg.equals("")) // Double check for empty strings
                    .filter(msg -> msg.length() > 0) // Extra safety check
                    .distinct() // Remove duplicate messages
                    .collect(Collectors.toList());
            
            // Join messages safely
            String mergedErrorMessage = "";
            if (!validMessages.isEmpty()) {
                mergedErrorMessage = String.join("; ", validMessages);
                // Additional safety - clean any leading/trailing semicolons
                mergedErrorMessage = mergedErrorMessage.trim();
                if (mergedErrorMessage.startsWith(";")) {
                    mergedErrorMessage = mergedErrorMessage.substring(1).trim();
                }
                if (mergedErrorMessage.endsWith(";")) {
                    mergedErrorMessage = mergedErrorMessage.substring(0, mergedErrorMessage.length() - 1).trim();
                }
            }

            // Set status
            String status = determineRowStatus(rowErrors);
            Cell statusCell = row.getCell(columnInfo.getStatusColumnIndex());
            if (statusCell == null) {
                statusCell = row.createCell(columnInfo.getStatusColumnIndex());
            }
            statusCell.setCellValue(status);

            // Set error details
            Cell errorCell = row.getCell(columnInfo.getErrorColumnIndex());
            if (errorCell == null) {
                errorCell = row.createCell(columnInfo.getErrorColumnIndex());
            }
            
            if (!mergedErrorMessage.isEmpty()) {
                errorCell.setCellValue(mergedErrorMessage);
            } else if (ValidationConstants.STATUS_INVALID.equals(status) || 
                      ValidationConstants.STATUS_ERROR.equals(status)) {
                // If status is invalid but no error details, provide a default message
                String defaultErrorMessage = LocalizationUtil.getLocalizedMessage(localizationMap, 
                    ValidationConstants.HCM_VALIDATION_FAILED_NO_DETAILS, 
                    "Validation failed - no specific error details available");
                errorCell.setCellValue(defaultErrorMessage);
            } else {
                errorCell.setCellValue("");
            }
        }
    }


    /**
     * Determines the overall status for a row based on its errors
     */
    private String determineRowStatus(List<ValidationError> errors) {
        if (errors.isEmpty()) {
            return ValidationConstants.STATUS_VALID;
        }
        
        // Check if any error has CREATED status
        boolean hasCreated = errors.stream()
                .anyMatch(e -> ValidationConstants.STATUS_CREATED.equals(e.getStatus()));
        if (hasCreated) {
            return ValidationConstants.STATUS_CREATED;
        }

        // Check if any error has ERROR status
        boolean hasError = errors.stream()
                .anyMatch(e -> ValidationConstants.STATUS_ERROR.equals(e.getStatus()));
        if (hasError) {
            return ValidationConstants.STATUS_ERROR;
        }

        // Check if any error has INVALID status
        boolean hasInvalid = errors.stream()
                .anyMatch(e -> ValidationConstants.STATUS_INVALID.equals(e.getStatus()));
        if (hasInvalid) {
            return ValidationConstants.STATUS_INVALID;
        }

        // If only VALID errors exist, return VALID
        return ValidationConstants.STATUS_VALID;
    }

    /**
     * Applies cell styling
     */
    private void applyCellStyle(Cell cell, boolean isHeader) {
        Workbook workbook = cell.getSheet().getWorkbook();
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();

        if (isHeader) {
            font.setBold(true);
            style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        }

        style.setFont(font);
        
        cell.setCellStyle(style);
    }
    
    /**
     * Applies yellow header styling for validation column headers
     * Uses project-factory standard color #ffff00 (bright yellow)
     */
    private void applyYellowHeaderStyle(Cell cell) {
        Workbook workbook = cell.getSheet().getWorkbook();
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();

        // Bold font for header
        font.setBold(true);
        
        // Bright yellow background (#ffff00) - matching project-factory standard
        if (workbook instanceof org.apache.poi.xssf.usermodel.XSSFWorkbook) {
            org.apache.poi.xssf.usermodel.XSSFCellStyle xssfStyle = (org.apache.poi.xssf.usermodel.XSSFCellStyle) style;
            org.apache.poi.xssf.usermodel.XSSFColor yellowColor = new org.apache.poi.xssf.usermodel.XSSFColor(
                new java.awt.Color(255, 255, 0), null); // RGB(255, 255, 0) = #ffff00
            xssfStyle.setFillForegroundColor(yellowColor);
            xssfStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        } else {
            // Fallback to indexed color for non-XLSX formats
            style.setFillForegroundColor(IndexedColors.YELLOW.getIndex());
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        }
        
        style.setFont(font);
        cell.setCellStyle(style);
    }

    /**
     * Merges duplicate errors for the same row
     */
    public List<ValidationError> mergeErrors(List<ValidationError> errors) {
        if (errors == null || errors.isEmpty()) {
            return new ArrayList<>();
        }

        Map<String, ValidationError> errorMap = new HashMap<>();

        for (ValidationError error : errors) {
            String key = error.getRowNumber() + "_" + error.getSheetName();
            
            if (errorMap.containsKey(key)) {
                ValidationError existing = errorMap.get(key);
                String mergedDetails = existing.getErrorDetails() + "; " + error.getErrorDetails();
                existing.setErrorDetails(mergedDetails);
            } else {
                errorMap.put(key, error);
            }
        }

        return new ArrayList<>(errorMap.values());
    }

    /**
     * Removes validation formatting (conditional formatting and cell comments) from processed files
     * and locks the sheet for protection since processed files are meant for review only
     * This should be called when adding validation columns to processed files to clean up 
     * the original template's validation formatting since error columns now handle validation feedback
     */
    public void removeValidationFormatting(Sheet sheet) {
        log.info("Cleaning processed file sheet and applying protection: {}", sheet.getSheetName());
        
        // Remove all conditional formatting from the sheet
        if (sheet.getSheetConditionalFormatting() != null) {
            // Clear all conditional formatting rules
            for (int i = sheet.getSheetConditionalFormatting().getNumConditionalFormattings() - 1; i >= 0; i--) {
                sheet.getSheetConditionalFormatting().removeConditionalFormatting(i);
            }
            log.debug("Removed conditional formatting rules from sheet: {}", sheet.getSheetName());
        }
        
        // Remove cell comments from data cells (keeping header comments if any)
        int lastRowNum = sheet.getLastRowNum();
        for (int rowIndex = 2; rowIndex <= lastRowNum; rowIndex++) { // Start from row 2 (skip headers at 0,1)
            Row row = sheet.getRow(rowIndex);
            if (row != null) {
                for (int colIndex = 0; colIndex < row.getLastCellNum(); colIndex++) {
                    Cell cell = row.getCell(colIndex);
                    if (cell != null && cell.getCellComment() != null) {
                        // Remove validation-related cell comments
                        cell.removeCellComment();
                    }
                }
            }
        }
        
        // Lock all cells first, then protect the sheet (processed files are read-only for review)
        lockAllCells(sheet);
        sheet.protectSheet(config.getExcelSheetPassword());
        
        log.info("Successfully cleaned validation formatting and locked processed file sheet: {}", sheet.getSheetName());
    }

    /**
     * Locks all cells in the sheet by setting them as locked
     */
    private void lockAllCells(Sheet sheet) {
        Workbook workbook = sheet.getWorkbook();
        CellStyle lockedStyle = workbook.createCellStyle();
        lockedStyle.setLocked(true);
        
        int lastRowNum = sheet.getLastRowNum();
        for (int rowIndex = 0; rowIndex <= lastRowNum; rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row != null) {
                for (int colIndex = 0; colIndex < row.getLastCellNum(); colIndex++) {
                    Cell cell = row.getCell(colIndex);
                    if (cell != null) {
                        // Create a new style based on existing style but with locked=true
                        CellStyle existingStyle = cell.getCellStyle();
                        CellStyle newStyle = workbook.createCellStyle();
                        newStyle.cloneStyleFrom(existingStyle);
                        newStyle.setLocked(true);
                        cell.setCellStyle(newStyle);
                    }
                }
            }
        }
        log.debug("Locked all cells in sheet: {}", sheet.getSheetName());
    }
}