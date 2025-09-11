package org.egov.excelingestion.util;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.XSSFConditionalFormattingRule;
import org.apache.poi.xssf.usermodel.XSSFSheetConditionalFormatting;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.egov.excelingestion.config.ExcelIngestionConfig;
import org.egov.excelingestion.web.models.excel.ColumnDef;
import org.egov.excelingestion.web.models.excel.MultiSelectDetails;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Utility class for populating Excel sheets with data
 * Follows patterns from ExcelSchemaSheetCreator and reuses existing utilities
 */
@Component
@Slf4j
public class ExcelDataPopulator {

    private final ExcelIngestionConfig config;
    private final ExcelStyleHelper excelStyleHelper;
    private final CellProtectionManager cellProtectionManager;

    public ExcelDataPopulator(ExcelIngestionConfig config, ExcelStyleHelper excelStyleHelper,
                            CellProtectionManager cellProtectionManager) {
        this.config = config;
        this.excelStyleHelper = excelStyleHelper;
        this.cellProtectionManager = cellProtectionManager;
    }

    /**
     * Creates a new workbook with a sheet populated with data
     *
     * @param sheetName Sheet name to create
     * @param columnProperties Column definitions with formatting rules
     * @param dataRows Data as Map<unlocalized_code, value> (can be null or empty for headers-only sheets)
     * @return Workbook with populated sheet
     */
    public Workbook populateSheetWithData(String sheetName, List<ColumnDef> columnProperties, 
                                        List<Map<String, Object>> dataRows) {
        return populateSheetWithData(sheetName, columnProperties, dataRows, null);
    }

    public Workbook populateSheetWithData(String sheetName, List<ColumnDef> columnProperties, 
                                        List<Map<String, Object>> dataRows, Map<String, String> localizationMap) {
        // Create new workbook
        Workbook workbook = new XSSFWorkbook();
        populateSheetWithData(workbook, sheetName, columnProperties, dataRows, localizationMap);
        return workbook;
    }


    /**
     * Adds a sheet populated with data to an existing workbook with localization
     *
     * @param workbook Existing workbook to add sheet to
     * @param sheetName Sheet name to create
     * @param columnProperties Column definitions with formatting rules
     * @param dataRows Data as Map<unlocalized_code, value> (can be null or empty for headers-only sheets)
     * @param localizationMap Map for localizing column headers
     * @return The same workbook with the new sheet added
     */
    public Workbook populateSheetWithData(Workbook workbook, String sheetName, List<ColumnDef> columnProperties, 
                                        List<Map<String, Object>> dataRows, Map<String, String> localizationMap) {
        log.info("Adding sheet: {} with {} data rows to workbook", sheetName, 
                dataRows != null ? dataRows.size() : 0);

        // 2. Create/Replace Sheet - Remove if exists, create new
        if (workbook.getSheetIndex(sheetName) >= 0) {
            workbook.removeSheetAt(workbook.getSheetIndex(sheetName));
        }
        Sheet sheet = workbook.createSheet(sheetName);

        // 3. Expand multi-select columns like ExcelSchemaSheetCreator does
        List<ColumnDef> expandedColumns = expandMultiSelectColumns(columnProperties);
        
        // 4. Create Headers - reuse existing pattern from ExcelSchemaSheetCreator
        createHeaderRows(workbook, sheet, expandedColumns, localizationMap);

        // 5. Fill Data (if not empty/null)
        if (dataRows != null && !dataRows.isEmpty()) {
            fillDataRows(workbook, sheet, expandedColumns, dataRows);
        }

        // 6. Apply Formatting - reuse existing methods
        applyFormatting(workbook, sheet, expandedColumns);

        // 7. Apply Protection - reuse existing protection logic
        applyProtection(workbook, sheet, expandedColumns);

        // 8. Apply Validation - reuse existing dropdown creation logic
        applyValidations(workbook, sheet, expandedColumns, localizationMap);
        
        // 9. Apply multiselect formulas if any
        applyMultiSelectFormulas(sheet, expandedColumns);

        log.info("Successfully added sheet: {} to workbook", sheetName);
        return workbook;
    }

    /**
     * Create header rows following ExcelSchemaSheetCreator pattern
     * Row 0: technical names (hidden), Row 1: localized names
     */
    private void createHeaderRows(Workbook workbook, Sheet sheet, List<ColumnDef> columnProperties, Map<String, String> localizationMap) {
        // Create Row 0 (hidden technical names) and Row 1 (localized visible headers)
        Row hiddenRow = sheet.createRow(0);
        Row visibleRow = sheet.createRow(1);

        for (int col = 0; col < columnProperties.size(); col++) {
            ColumnDef def = columnProperties.get(col);

            // Row 0: technical name
            Cell techCell = hiddenRow.createCell(col);
            techCell.setCellValue(def.getName());

            // Row 1: localized display name
            Cell headerCell = visibleRow.createCell(col);
            String displayName = localizationMap != null && localizationMap.containsKey(def.getName()) 
                ? localizationMap.get(def.getName()) 
                : def.getName();
            headerCell.setCellValue(displayName);
            
            // Apply enhanced header styling - always enable wrap text for headers so long text wraps to next line
            headerCell.setCellStyle(excelStyleHelper.createCustomHeaderStyle(workbook, def.getColorHex(), true));

            // Set column width if specified - follow existing pattern with validation
            if (def.getWidth() != null && def.getWidth() > 0) {
                // Excel has a maximum column width of 255 characters
                int columnWidth = Math.min(def.getWidth(), 255);
                sheet.setColumnWidth(col, columnWidth * 256); // POI uses 1/256th of character width
            } else {
                // Set a reasonable default width instead of autosize to avoid very wide columns
                sheet.setColumnWidth(col, 40 * 256); // Default 40 characters width
            }

            // Hide column if specified
            if (def.isHideColumn()) {
                sheet.setColumnHidden(col, true);
            }
        }

        // Hide first row and freeze panes - follow existing pattern
        sheet.createFreezePane(0, 2);
        hiddenRow.setZeroHeight(true);
    }

    /**
     * Fill data rows using simple loop as specified in design
     */
    private void fillDataRows(Workbook workbook, Sheet sheet, List<ColumnDef> columnProperties, 
                             List<Map<String, Object>> dataRows) {
        log.debug("Filling {} data rows", dataRows.size());

        // Simple loop: for each dataRow → create Excel row → fill cells
        for (int rowIdx = 0; rowIdx < dataRows.size(); rowIdx++) {
            Map<String, Object> dataRow = dataRows.get(rowIdx);
            Row excelRow = sheet.createRow(rowIdx + 2); // Start from row 2 (0-indexed), after headers

            for (int colIdx = 0; colIdx < columnProperties.size(); colIdx++) {
                ColumnDef column = columnProperties.get(colIdx);
                Cell cell = excelRow.createCell(colIdx);

                // Get value from data map using column name
                Object value = dataRow.get(column.getName());
                if (value != null) {
                    setCellValue(cell, value, column);
                }
            }
        }
    }

    /**
     * Set cell value based on data type and column properties
     */
    private void setCellValue(Cell cell, Object value, ColumnDef column) {
        if (value == null) {
            return;
        }

        // Handle different data types
        if (value instanceof String) {
            String stringValue = (String) value;
            // Apply prefix if specified
            if (column.getPrefix() != null && !column.getPrefix().isEmpty()) {
                stringValue = column.getPrefix() + stringValue;
            }
            cell.setCellValue(stringValue);
        } else if (value instanceof Number) {
            cell.setCellValue(((Number) value).doubleValue());
        } else if (value instanceof Boolean) {
            cell.setCellValue((Boolean) value);
        } else {
            // Default to string representation
            cell.setCellValue(value.toString());
        }
    }

    /**
     * Apply formatting using existing utilities
     */
    private void applyFormatting(Workbook workbook, Sheet sheet, List<ColumnDef> columnProperties) {
        // Apply data cell styling - reuse existing method
        applyDataCellStyling(workbook, sheet, columnProperties);
    }

    /**
     * Apply data cell styling including text wrapping - copied from ExcelSchemaSheetCreator
     */
    private void applyDataCellStyling(Workbook workbook, Sheet sheet, List<ColumnDef> columns) {
        // Create styles for different requirements - reuse existing methods
        CellStyle wrapTextStyle = excelStyleHelper.createDataCellStyle(workbook, true);
        CellStyle normalStyle = excelStyleHelper.createDataCellStyle(workbook, false);
        
        // Apply styling to data rows
        for (int rowIdx = 2; rowIdx <= sheet.getLastRowNum(); rowIdx++) {
            Row row = sheet.getRow(rowIdx);
            if (row == null) {
                continue;
            }
            
            boolean needsAutoHeight = false;
            
            for (int colIdx = 0; colIdx < columns.size(); colIdx++) {
                ColumnDef column = columns.get(colIdx);
                Cell cell = row.getCell(colIdx);
                if (cell == null) {
                    continue;
                }
                
                // Apply appropriate cell style
                if (column.isWrapText()) {
                    cell.setCellStyle(wrapTextStyle);
                    needsAutoHeight = true;
                } else {
                    cell.setCellStyle(normalStyle);
                }
            }
            
            // Auto-adjust row height if any column in this row requires it
            if (needsAutoHeight) {
                for (int colIdx = 0; colIdx < columns.size(); colIdx++) {
                    if (columns.get(colIdx).isAdjustHeight()) {
                        row.setHeight((short) -1); // Auto-size height
                        break;
                    }
                }
            }
        }
    }

    /**
     * Apply protection using existing CellProtectionManager
     */
    private void applyProtection(Workbook workbook, Sheet sheet, List<ColumnDef> columnProperties) {
        // Use existing cell protection manager
        cellProtectionManager.applyCellProtection(workbook, sheet, columnProperties);
        
        // Protect sheet with password if configured
        if (config.getExcelSheetPassword() != null && !config.getExcelSheetPassword().isEmpty()) {
            sheet.protectSheet(config.getExcelSheetPassword());
        }
    }

    /**
     * Apply validations - Excel in-sheet validation for different field types
     */
    private void applyValidations(Workbook workbook, Sheet sheet, List<ColumnDef> columns, Map<String, String> localizationMap) {
        DataValidationHelper dvHelper = sheet.getDataValidationHelper();
        
        for (int colIndex = 0; colIndex < columns.size(); colIndex++) {
            ColumnDef column = columns.get(colIndex);
            
            // Skip multi-select hidden columns from validation
            if ("multiselect_hidden".equals(column.getType())) {
                continue;
            }
            
            // Apply enum dropdown validation - keep as is (no changes for dropdowns)
            if (column.getEnumValues() != null && !column.getEnumValues().isEmpty()) {
                String[] enumArray = column.getEnumValues().toArray(new String[0]);
                DataValidationConstraint constraint = dvHelper.createExplicitListConstraint(enumArray);
                CellRangeAddressList addressList = new CellRangeAddressList(2, config.getExcelRowLimit(), colIndex, colIndex);
                DataValidation validation = dvHelper.createValidation(constraint, addressList);
                validation.setErrorStyle(DataValidation.ErrorStyle.STOP);
                validation.setShowErrorBox(true);
                validation.createErrorBox(
                    LocalizationUtil.getLocalizedMessage(localizationMap, "HCM_VALIDATION_INVALID_DROPDOWN_SELECTION", "Invalid Selection"),
                    LocalizationUtil.getLocalizedMessage(localizationMap, "HCM_VALIDATION_INVALID_DROPDOWN_SELECTION_MESSAGE", "Please select a value from the dropdown list.")
                );
                validation.setShowPromptBox(false); // No input prompt, only error on invalid data
                sheet.addValidationData(validation);
            }
            
            // Apply pure visual validation for string and number fields: conditional formatting + cell comments only
            else if ("string".equals(column.getType()) && hasStringValidation(column)) {
                // Apply only visual formatting - no data validation to avoid vanishing entries
                applyPureVisualValidation(workbook, sheet, column, colIndex, localizationMap);
            }
            
            // Apply pure visual validation for number fields: conditional formatting + cell comments only  
            else if ("number".equals(column.getType()) && hasNumberValidation(column)) {
                // Apply only visual formatting - no data validation to avoid vanishing entries
                applyPureVisualValidation(workbook, sheet, column, colIndex, localizationMap);
            }
        }
    }
    
    /**
     * Check if column has string LENGTH validation rules (only minLength/maxLength)
     */
    private boolean hasStringValidation(ColumnDef column) {
        return column.getMinLength() != null || column.getMaxLength() != null;
        // Removed pattern check - regex validation should not have in-sheet validation
    }
    
    /**
     * Check if column has number validation rules (range constraints OR just numeric type validation)
     */
    private boolean hasNumberValidation(ColumnDef column) {
        // Always validate numeric fields (to catch non-numeric entries)
        if ("number".equals(column.getType())) {
            return true;
        }
        return false;
    }
    
    /**
     * Apply string validation rules from MDMS schema (minLength/maxLength/pattern)
     */
    private void applyStringValidation(DataValidationHelper dvHelper, Sheet sheet, ColumnDef column, int colIndex, Map<String, String> localizationMap) {
        try {
            // For string validations that Excel doesn't support directly (like regex pattern),
            // we can use TEXT_LENGTH constraint for minLength/maxLength
            DataValidationConstraint constraint = null;
            String errorMessage = "";
            
            // Apply text length validation if minLength or maxLength is specified
            if (column.getMinLength() != null || column.getMaxLength() != null) {
                if (column.getMinLength() != null && column.getMaxLength() != null) {
                    // Both minLength and maxLength defined
                    constraint = dvHelper.createTextLengthConstraint(
                        DataValidationConstraint.OperatorType.BETWEEN,
                        String.valueOf(column.getMinLength()),
                        String.valueOf(column.getMaxLength())
                    );
                    // Use custom error message from MDMS if available, otherwise use dynamic message
                    if (column.getErrorMessage() != null && !column.getErrorMessage().isEmpty()) {
                        errorMessage = LocalizationUtil.getLocalizedMessage(localizationMap, column.getErrorMessage(), column.getErrorMessage());
                    } else {
                        String template = LocalizationUtil.getLocalizedMessage(localizationMap, "HCM_VALIDATION_TEXT_LENGTH_BETWEEN", "Text length must be between %d and %d characters");
                        errorMessage = String.format(template, column.getMinLength(), column.getMaxLength());
                    }
                    
                } else if (column.getMinLength() != null) {
                    // Only minLength defined
                    constraint = dvHelper.createTextLengthConstraint(
                        DataValidationConstraint.OperatorType.GREATER_OR_EQUAL,
                        String.valueOf(column.getMinLength()),
                        null
                    );
                    if (column.getErrorMessage() != null && !column.getErrorMessage().isEmpty()) {
                        errorMessage = LocalizationUtil.getLocalizedMessage(localizationMap, column.getErrorMessage(), column.getErrorMessage());
                    } else {
                        String template = LocalizationUtil.getLocalizedMessage(localizationMap, "HCM_VALIDATION_TEXT_MIN_LENGTH", "Text must be at least %d characters long");
                        errorMessage = String.format(template, column.getMinLength());
                    }
                    
                } else if (column.getMaxLength() != null) {
                    // Only maxLength defined
                    constraint = dvHelper.createTextLengthConstraint(
                        DataValidationConstraint.OperatorType.LESS_OR_EQUAL,
                        String.valueOf(column.getMaxLength()),
                        null
                    );
                    if (column.getErrorMessage() != null && !column.getErrorMessage().isEmpty()) {
                        errorMessage = LocalizationUtil.getLocalizedMessage(localizationMap, column.getErrorMessage(), column.getErrorMessage());
                    } else {
                        String template = LocalizationUtil.getLocalizedMessage(localizationMap, "HCM_VALIDATION_TEXT_MAX_LENGTH", "Text must not exceed %d characters");
                        errorMessage = String.format(template, column.getMaxLength());
                    }
                }
                
                if (constraint != null) {
                    CellRangeAddressList addressList = new CellRangeAddressList(2, config.getExcelRowLimit(), colIndex, colIndex);
                    DataValidation validation = dvHelper.createValidation(constraint, addressList);
                    
                    // Use WARNING style to allow invalid input but highlight it
                    validation.setErrorStyle(DataValidation.ErrorStyle.WARNING);
                    validation.setShowErrorBox(true); // Show error on hover
                    validation.createErrorBox(
                        LocalizationUtil.getLocalizedMessage(localizationMap, "HCM_VALIDATION_INVALID_TEXT_LENGTH", "Invalid Text Length"),
                        errorMessage
                    );
                    validation.setShowPromptBox(false);
                    
                    sheet.addValidationData(validation);
                    log.info("Applied MDMS string length validation for column '{}': {}", column.getName(), errorMessage);
                }
            }
            
            // Note: Excel doesn't support regex pattern validation directly in data validation
            // Pattern validation is handled during processing, not in-sheet validation
            if (column.getPattern() != null && !column.getPattern().trim().isEmpty()) {
                log.info("Pattern validation '{}' for column '{}' will be applied during processing (not supported in Excel in-sheet validation)", 
                    column.getPattern(), column.getName());
            }
            
        } catch (Exception e) {
            log.warn("Failed to apply MDMS string validation for column '{}': {}", column.getName(), e.getMessage());
        }
    }
    
    /**
     * Apply number validation rules from MDMS schema (minimum/maximum and additional properties)
     */
    private void applyNumberValidation(DataValidationHelper dvHelper, Sheet sheet, ColumnDef column, int colIndex, Map<String, String> localizationMap) {
        try {
            DataValidationConstraint constraint = null;
            String errorMessage = "";
            
            // Priority order: exclusiveMinimum/exclusiveMaximum > minimum/maximum
            // Handle exclusive minimum/maximum validation first (higher priority)
            if (column.getExclusiveMinimum() != null && column.getExclusiveMaximum() != null) {
                // Both exclusive minimum and maximum defined from MDMS schema
                constraint = dvHelper.createDecimalConstraint(
                    DataValidationConstraint.OperatorType.BETWEEN,
                    String.valueOf(column.getExclusiveMinimum().doubleValue() + 0.000001), // Add small epsilon for "greater than"
                    String.valueOf(column.getExclusiveMaximum().doubleValue() - 0.000001)  // Subtract small epsilon for "less than"
                );
                if (column.getErrorMessage() != null && !column.getErrorMessage().isEmpty()) {
                    errorMessage = LocalizationUtil.getLocalizedMessage(localizationMap, column.getErrorMessage(), column.getErrorMessage());
                } else {
                    String template = LocalizationUtil.getLocalizedMessage(localizationMap, "HCM_VALIDATION_NUMBER_BETWEEN_EXCLUSIVE", "Value must be greater than %.0f and less than %.0f");
                    errorMessage = String.format(template, column.getExclusiveMinimum().doubleValue(), column.getExclusiveMaximum().doubleValue());
                }
                
            } else if (column.getExclusiveMinimum() != null) {
                // Only exclusive minimum defined from MDMS schema
                constraint = dvHelper.createDecimalConstraint(
                    DataValidationConstraint.OperatorType.GREATER_THAN,
                    String.valueOf(column.getExclusiveMinimum().doubleValue()),
                    null
                );
                if (column.getErrorMessage() != null && !column.getErrorMessage().isEmpty()) {
                    errorMessage = LocalizationUtil.getLocalizedMessage(localizationMap, column.getErrorMessage(), column.getErrorMessage());
                } else {
                    String template = LocalizationUtil.getLocalizedMessage(localizationMap, "HCM_VALIDATION_NUMBER_GREATER_THAN", "Value must be greater than %.0f");
                    errorMessage = String.format(template, column.getExclusiveMinimum().doubleValue());
                }
                
            } else if (column.getExclusiveMaximum() != null) {
                // Only exclusive maximum defined from MDMS schema
                constraint = dvHelper.createDecimalConstraint(
                    DataValidationConstraint.OperatorType.LESS_THAN,
                    String.valueOf(column.getExclusiveMaximum().doubleValue()),
                    null
                );
                if (column.getErrorMessage() != null && !column.getErrorMessage().isEmpty()) {
                    errorMessage = LocalizationUtil.getLocalizedMessage(localizationMap, column.getErrorMessage(), column.getErrorMessage());
                } else {
                    String template = LocalizationUtil.getLocalizedMessage(localizationMap, "HCM_VALIDATION_NUMBER_LESS_THAN", "Value must be less than %.0f");
                    errorMessage = String.format(template, column.getExclusiveMaximum().doubleValue());
                }
                
            } else if (column.getMinimum() != null && column.getMaximum() != null) {
                // Both minimum and maximum defined from MDMS schema
                constraint = dvHelper.createDecimalConstraint(
                    DataValidationConstraint.OperatorType.BETWEEN,
                    String.valueOf(column.getMinimum().doubleValue()),
                    String.valueOf(column.getMaximum().doubleValue())
                );
                // Use custom error message from MDMS if available, otherwise use dynamic message
                if (column.getErrorMessage() != null && !column.getErrorMessage().isEmpty()) {
                    // Try to localize the custom error message
                    errorMessage = LocalizationUtil.getLocalizedMessage(localizationMap, column.getErrorMessage(), column.getErrorMessage());
                } else {
                    // Use dynamic message
                    String template = LocalizationUtil.getLocalizedMessage(localizationMap, "HCM_VALIDATION_NUMBER_BETWEEN", "Value must be between %.0f and %.0f");
                    errorMessage = String.format(template, column.getMinimum().doubleValue(), column.getMaximum().doubleValue());
                }
                    
            } else if (column.getMinimum() != null) {
                // Only minimum defined from MDMS schema
                constraint = dvHelper.createDecimalConstraint(
                    DataValidationConstraint.OperatorType.GREATER_OR_EQUAL,
                    String.valueOf(column.getMinimum().doubleValue()),
                    null
                );
                // Use custom error message from MDMS if available, otherwise use dynamic message
                if (column.getErrorMessage() != null && !column.getErrorMessage().isEmpty()) {
                    // Try to localize the custom error message
                    errorMessage = LocalizationUtil.getLocalizedMessage(localizationMap, column.getErrorMessage(), column.getErrorMessage());
                } else {
                    // Use dynamic message
                    String template = LocalizationUtil.getLocalizedMessage(localizationMap, "HCM_VALIDATION_NUMBER_MIN", "Value must be at least %.0f");
                    errorMessage = String.format(template, column.getMinimum().doubleValue());
                }
                
            } else if (column.getMaximum() != null) {
                // Only maximum defined from MDMS schema
                constraint = dvHelper.createDecimalConstraint(
                    DataValidationConstraint.OperatorType.LESS_OR_EQUAL,
                    String.valueOf(column.getMaximum().doubleValue()),
                    null
                );
                // Use custom error message from MDMS if available, otherwise use dynamic message
                if (column.getErrorMessage() != null && !column.getErrorMessage().isEmpty()) {
                    // Try to localize the custom error message
                    errorMessage = LocalizationUtil.getLocalizedMessage(localizationMap, column.getErrorMessage(), column.getErrorMessage());
                } else {
                    // Use dynamic message
                    String template = LocalizationUtil.getLocalizedMessage(localizationMap, "HCM_VALIDATION_NUMBER_MAX", "Value must be at most %.0f");
                    errorMessage = String.format(template, column.getMaximum().doubleValue());
                }
            }
            
            if (constraint != null) {
                CellRangeAddressList addressList = new CellRangeAddressList(2, config.getExcelRowLimit(), colIndex, colIndex);
                DataValidation validation = dvHelper.createValidation(constraint, addressList);
                
                // Use WARNING style to allow invalid input but highlight it
                validation.setErrorStyle(DataValidation.ErrorStyle.WARNING);
                validation.setShowErrorBox(true); // Show error on hover
                validation.createErrorBox(
                    LocalizationUtil.getLocalizedMessage(localizationMap, "HCM_VALIDATION_INVALID_NUMBER", "Invalid Number"),
                    errorMessage
                );
                validation.setShowPromptBox(false); // No input prompt as requested
                
                sheet.addValidationData(validation);
                log.info("Applied MDMS number validation for column '{}': {}", column.getName(), errorMessage);
            }
            
            // Note: Excel doesn't support "multipleOf" validation directly in data validation
            // MultipleOf validation is handled during processing, not in-sheet validation
            if (column.getMultipleOf() != null) {
                log.info("MultipleOf validation '{}' for column '{}' will be applied during processing (not supported in Excel in-sheet validation)", 
                    column.getMultipleOf(), column.getName());
            }
            
        } catch (Exception e) {
            log.warn("Failed to apply MDMS number validation for column '{}': {}", column.getName(), e.getMessage());
        }
    }
    

    /**
     * Expand multi-select columns - copied from ExcelSchemaSheetCreator
     */
    private List<ColumnDef> expandMultiSelectColumns(List<ColumnDef> columns) {
        List<ColumnDef> expandedColumns = new ArrayList<>();
        
        for (ColumnDef column : columns) {
            if (column.getMultiSelectDetails() != null) {
                MultiSelectDetails details = column.getMultiSelectDetails();
                int maxSelections = details.getMaxSelections();
                
                // Create individual columns for each selection
                for (int i = 1; i <= maxSelections; i++) {
                    ColumnDef multiCol = ColumnDef.builder()
                            .name(column.getName() + "_MULTISELECT_" + i)
                            .technicalName(column.getName())
                            .type("multiselect_item")
                            .colorHex(column.getColorHex())
                            .orderNumber(column.getOrderNumber())
                            .enumValues(details.getEnumValues())
                            .parentColumn(column.getName())
                            .multiSelectIndex(i)
                            .freezeColumnIfFilled(column.isFreezeColumnIfFilled())
                            .width(column.getWidth())
                            .wrapText(column.isWrapText())
                            .build();
                    expandedColumns.add(multiCol);
                }
                
                // Add the hidden concatenated column
                ColumnDef hiddenCol = ColumnDef.builder()
                        .name(column.getName())
                        .technicalName(column.getName())
                        .type("multiselect_hidden")
                        .colorHex(column.getColorHex())
                        .orderNumber(column.getOrderNumber())
                        .hideColumn(column.isHideColumn())
                        .parentColumn(column.getName())
                        .multiSelectMaxSelections(maxSelections)
                        .freezeColumnIfFilled(column.isFreezeColumnIfFilled())
                        .width(column.getWidth())
                        .build();
                expandedColumns.add(hiddenCol);
            } else {
                // Regular column (string, number, enum)
                ColumnDef regularCol = ColumnDef.builder()
                        .name(column.getName())
                        .technicalName(column.getName())
                        .type(column.getType())
                        .description(column.getDescription())
                        .colorHex(column.getColorHex())
                        .orderNumber(column.getOrderNumber())
                        .freezeColumnIfFilled(column.isFreezeColumnIfFilled())
                        .hideColumn(column.isHideColumn())
                        .required(column.isRequired())
                        .enumValues(column.getEnumValues())
                        .width(column.getWidth())
                        .wrapText(column.isWrapText())
                        .prefix(column.getPrefix())
                        .adjustHeight(column.isAdjustHeight())
                        .showInProcessed(column.isShowInProcessed())
                        .freezeColumn(column.isFreezeColumn())
                        .freezeTillData(column.isFreezeTillData())
                        .unFreezeColumnTillData(column.isUnFreezeColumnTillData())
                        // Copy MDMS validation properties
                        .minimum(column.getMinimum())
                        .maximum(column.getMaximum())
                        .errorMessage(column.getErrorMessage())
                        // Copy string validation properties
                        .minLength(column.getMinLength())
                        .maxLength(column.getMaxLength())
                        .pattern(column.getPattern())
                        // Copy additional number validation properties
                        .multipleOf(column.getMultipleOf())
                        .exclusiveMinimum(column.getExclusiveMinimum())
                        .exclusiveMaximum(column.getExclusiveMaximum())
                        .build();
                expandedColumns.add(regularCol);
            }
        }
        
        return expandedColumns;
    }

    /**
     * Apply multiselect formulas - copied from ExcelSchemaSheetCreator
     */
    private void applyMultiSelectFormulas(Sheet sheet, List<ColumnDef> columns) {
        Map<String, List<Integer>> multiSelectGroups = new HashMap<>();
        Map<String, Integer> hiddenColumnIndexes = new HashMap<>();
        
        // Group columns by parent and find hidden column indexes
        for (int i = 0; i < columns.size(); i++) {
            ColumnDef column = columns.get(i);
            if ("multiselect_item".equals(column.getType())) {
                multiSelectGroups.computeIfAbsent(column.getParentColumn(), k -> new ArrayList<>()).add(i);
            } else if ("multiselect_hidden".equals(column.getType())) {
                hiddenColumnIndexes.put(column.getParentColumn(), i);
            }
        }
        
        // Apply CONCATENATE formula to hidden columns
        for (Map.Entry<String, List<Integer>> entry : multiSelectGroups.entrySet()) {
            String parentColumn = entry.getKey();
            List<Integer> itemColumns = entry.getValue();
            Integer hiddenColumnIndex = hiddenColumnIndexes.get(parentColumn);
            
            if (hiddenColumnIndex != null && !itemColumns.isEmpty()) {
                // Build CONCATENATE formula similar to project-factory
                List<String> colLetters = new ArrayList<>();
                for (Integer colIndex : itemColumns) {
                    colLetters.add(getColumnLetter(colIndex + 1)); // +1 because Excel is 1-indexed
                }
                
                // Create formula for concatenating non-blank values with commas
                // Start from row 3 to match validation expectations (row 3 = first data row)
                StringBuilder formulaBuilder = new StringBuilder("=IF(AND(");
                for (String colLetter : colLetters) {
                    formulaBuilder.append("ISBLANK(").append(colLetter).append("3),");
                }
                // Remove last comma
                if (colLetters.size() > 0) {
                    formulaBuilder.setLength(formulaBuilder.length() - 1);
                }
                formulaBuilder.append("),\"\",TRIM(CONCATENATE(");
                
                for (String colLetter : colLetters) {
                    formulaBuilder.append("IF(ISBLANK(").append(colLetter).append("3),\"\",")
                                  .append(colLetter).append("3&\",\"),");
                }
                // Remove last comma
                if (colLetters.size() > 0) {
                    formulaBuilder.setLength(formulaBuilder.length() - 1);
                }
                formulaBuilder.append(")))");
                
                String formula = formulaBuilder.toString();
                
                // Apply formula starting from row 3 (first data row) to match validation expectations
                for (int row = 3; row <= 101; row++) { // Apply to first 100 data rows (3-102)
                    String rowFormula = formula.replace("3", String.valueOf(row));
                    Row excelRow = sheet.getRow(row - 1); // POI rows are 0-indexed, so row 3 = index 2
                    if (excelRow == null) {
                        excelRow = sheet.createRow(row - 1);
                    }
                    Cell cell = excelRow.getCell(hiddenColumnIndex);
                    if (cell == null) {
                        cell = excelRow.createCell(hiddenColumnIndex);
                    }
                    cell.setCellFormula(rowFormula.substring(1)); // Remove the leading '='
                }
            }
        }
    }

    /**
     * Get column letter from index - copied from ExcelSchemaSheetCreator
     */
    private String getColumnLetter(int columnIndex) {
        StringBuilder columnName = new StringBuilder();
        while (columnIndex > 0) {
            columnIndex--; // Make it 0-indexed
            columnName.insert(0, (char) ('A' + columnIndex % 26));
            columnIndex /= 26;
        }
        return columnName.toString();
    }
    
    /**
     * Apply visual validation only - no data validation, just conditional formatting
     * for error coloring when invalid data is entered
     */
    private void applyVisualValidationOnly(Workbook workbook, Sheet sheet, ColumnDef column, int colIndex, Map<String, String> localizationMap) {
        try {
            // Build error message for tooltip
            String errorMessage = buildErrorMessage(column, localizationMap);
            
            // Get column letter for formula
            String columnLetter = getColumnLetter(colIndex + 1);
            
            // Build validation formula based on column type and constraints
            String formula = buildConditionalFormula(column, columnLetter);
            
            if (formula != null) {
                // Apply conditional formatting for invalid data (rose/red background)
                XSSFSheetConditionalFormatting conditionalFormatting = (XSSFSheetConditionalFormatting) sheet.getSheetConditionalFormatting();
                
                // Define cell range for the column (data rows only, starting from row 3)
                CellRangeAddress[] regions = {new CellRangeAddress(2, config.getExcelRowLimit(), colIndex, colIndex)};
                
                // Create conditional formatting rule
                XSSFConditionalFormattingRule rule = conditionalFormatting.createConditionalFormattingRule(formula);
                
                // Set validation error color for invalid cells
                PatternFormatting fill = rule.createPatternFormatting();
                setValidationErrorColor(fill);
                fill.setFillPattern(PatternFormatting.SOLID_FOREGROUND);
                
                // Apply the rule
                conditionalFormatting.addConditionalFormatting(regions, rule);
                
                // Add cell comments for error messages on hover
                addCellComments(sheet, colIndex, errorMessage, localizationMap);
                
                log.info("Applied visual validation for column '{}': {}", column.getName(), errorMessage);
            }
        } catch (Exception e) {
            log.warn("Failed to apply visual validation for column '{}': {}", column.getName(), e.getMessage());
        }
    }
    
    /**
     * Apply hybrid validation: WARNING style data validation for hover tooltips + conditional formatting for error coloring
     */
    private void applyHybridValidation(Workbook workbook, Sheet sheet, ColumnDef column, int colIndex, Map<String, String> localizationMap) {
        try {
            // First apply conditional formatting for error color
            applyVisualValidationOnly(workbook, sheet, column, colIndex, localizationMap);
            
            // Then add WARNING style data validation for hover tooltips
            DataValidationHelper validationHelper = sheet.getDataValidationHelper();
            String errorMessage = buildErrorMessage(column, localizationMap);
            
            CellRangeAddressList addressList = new CellRangeAddressList(2, config.getExcelRowLimit(), colIndex, colIndex);
            DataValidationConstraint constraint = null;
            
            if ("string".equals(column.getType())) {
                // Text length constraint
                if (column.getMinLength() != null || column.getMaxLength() != null) {
                    int minLength = column.getMinLength() != null ? column.getMinLength() : 0;
                    int maxLength = column.getMaxLength() != null ? column.getMaxLength() : 255;
                    constraint = validationHelper.createTextLengthConstraint(
                        DataValidationConstraint.OperatorType.BETWEEN, 
                        String.valueOf(minLength), 
                        String.valueOf(maxLength)
                    );
                }
            } else if ("number".equals(column.getType())) {
                // Number range constraint
                if (column.getMinimum() != null || column.getMaximum() != null) {
                    Double minValue = column.getMinimum() != null ? column.getMinimum().doubleValue() : null;
                    Double maxValue = column.getMaximum() != null ? column.getMaximum().doubleValue() : null;
                    
                    if (minValue != null && maxValue != null) {
                        constraint = validationHelper.createDecimalConstraint(
                            DataValidationConstraint.OperatorType.BETWEEN,
                            String.valueOf(minValue),
                            String.valueOf(maxValue)
                        );
                    } else if (minValue != null) {
                        constraint = validationHelper.createDecimalConstraint(
                            DataValidationConstraint.OperatorType.GREATER_OR_EQUAL,
                            String.valueOf(minValue),
                            null
                        );
                    } else if (maxValue != null) {
                        constraint = validationHelper.createDecimalConstraint(
                            DataValidationConstraint.OperatorType.LESS_OR_EQUAL,
                            String.valueOf(maxValue),
                            null
                        );
                    }
                }
            }
            
            if (constraint != null) {
                DataValidation validation = validationHelper.createValidation(constraint, addressList);
                
                // Set to WARNING style - allows entry but shows tooltip on hover
                validation.setErrorStyle(DataValidation.ErrorStyle.WARNING);
                
                // Configure error display - no popup during entry, but tooltip on hover
                validation.setShowErrorBox(false); // No popup during data entry
                validation.setShowPromptBox(false); // No input prompt
                
                // Set error message for tooltip
                validation.createErrorBox("Validation Error", errorMessage);
                
                sheet.addValidationData(validation);
                
                log.info("Applied hybrid validation (WARNING + conditional formatting) for column '{}': {}", column.getName(), errorMessage);
            }
            
        } catch (Exception e) {
            log.warn("Failed to apply hybrid validation for column '{}': {}", column.getName(), e.getMessage());
        }
    }
    
    /**
     * Apply pure visual validation: conditional formatting + unconditional cell comments
     */
    private void applyPureVisualValidation(Workbook workbook, Sheet sheet, ColumnDef column, int colIndex, Map<String, String> localizationMap) {
        try {
            // Build error message for tooltips
            String errorMessage = buildErrorMessage(column, localizationMap);
            
            // Get column letter for formula
            String columnLetter = getColumnLetter(colIndex + 1);
            
            // Build validation formula based on column type and constraints
            String formula = buildConditionalFormula(column, columnLetter);
            
            if (formula != null) {
                // 1. Apply conditional formatting for error color
                XSSFSheetConditionalFormatting conditionalFormatting = (XSSFSheetConditionalFormatting) sheet.getSheetConditionalFormatting();
                
                // Define cell range for the column (data rows only, starting from row 3)
                CellRangeAddress[] regions = {new CellRangeAddress(2, config.getExcelRowLimit(), colIndex, colIndex)};
                
                // Create conditional formatting rule
                XSSFConditionalFormattingRule rule = conditionalFormatting.createConditionalFormattingRule(formula);
                
                // Set validation error color for invalid cells
                PatternFormatting fill = rule.createPatternFormatting();
                setValidationErrorColor(fill);
                fill.setFillPattern(PatternFormatting.SOLID_FOREGROUND);
                
                // Apply the rule
                conditionalFormatting.addConditionalFormatting(regions, rule);
                
                // 2. Add validation tooltip to column header only (not individual cells)
                addCellComments(sheet, colIndex, errorMessage, localizationMap);
                
                log.info("Applied pure visual validation for column '{}': {}", column.getName(), errorMessage);
            }
        } catch (Exception e) {
            log.warn("Failed to apply pure visual validation for column '{}': {}", column.getName(), e.getMessage());
        }
    }
    
    
    /**
     * Build error message based on column constraints
     */
    private String buildErrorMessage(ColumnDef column, Map<String, String> localizationMap) {
        // Use custom error message if available
        if (column.getErrorMessage() != null && !column.getErrorMessage().isEmpty()) {
            return localizationMap != null && localizationMap.containsKey(column.getErrorMessage()) 
                ? localizationMap.get(column.getErrorMessage()) 
                : column.getErrorMessage();
        }
        
        // Build dynamic message based on constraints
        if ("string".equals(column.getType())) {
            if (column.getMinLength() != null && column.getMaxLength() != null) {
                String template = localizationMap != null && localizationMap.containsKey("HCM_VALIDATION_TEXT_LENGTH_BETWEEN") 
                    ? localizationMap.get("HCM_VALIDATION_TEXT_LENGTH_BETWEEN") 
                    : "Text length must be between %d and %d characters";
                return String.format(template, column.getMinLength(), column.getMaxLength());
            } else if (column.getMinLength() != null) {
                String template = localizationMap != null && localizationMap.containsKey("HCM_VALIDATION_TEXT_MIN_LENGTH") 
                    ? localizationMap.get("HCM_VALIDATION_TEXT_MIN_LENGTH") 
                    : "Text must be at least %d characters long";
                return String.format(template, column.getMinLength());
            } else if (column.getMaxLength() != null) {
                String template = localizationMap != null && localizationMap.containsKey("HCM_VALIDATION_TEXT_MAX_LENGTH") 
                    ? localizationMap.get("HCM_VALIDATION_TEXT_MAX_LENGTH") 
                    : "Text must not exceed %d characters";
                return String.format(template, column.getMaxLength());
            }
        } else if ("number".equals(column.getType())) {
            // Check if there are range constraints
            boolean hasRangeConstraints = column.getMinimum() != null || column.getMaximum() != null || 
                                         column.getExclusiveMinimum() != null || column.getExclusiveMaximum() != null;
            
            if (hasRangeConstraints) {
                // Include both numeric and range validation in message
                if (column.getMinimum() != null && column.getMaximum() != null) {
                    String template = localizationMap != null && localizationMap.containsKey("HCM_VALIDATION_NUMBER_BETWEEN") 
                        ? localizationMap.get("HCM_VALIDATION_NUMBER_BETWEEN") 
                        : "Must be a number between %.0f and %.0f";
                    return String.format(template, column.getMinimum().doubleValue(), column.getMaximum().doubleValue());
                } else if (column.getMinimum() != null) {
                    String template = localizationMap != null && localizationMap.containsKey("HCM_VALIDATION_NUMBER_MIN") 
                        ? localizationMap.get("HCM_VALIDATION_NUMBER_MIN") 
                        : "Must be a number at least %.0f";
                    return String.format(template, column.getMinimum().doubleValue());
                } else if (column.getMaximum() != null) {
                    String template = localizationMap != null && localizationMap.containsKey("HCM_VALIDATION_NUMBER_MAX") 
                        ? localizationMap.get("HCM_VALIDATION_NUMBER_MAX") 
                        : "Must be a number at most %.0f";
                    return String.format(template, column.getMaximum().doubleValue());
                } else if (column.getExclusiveMinimum() != null) {
                    String template = localizationMap != null && localizationMap.containsKey("HCM_VALIDATION_NUMBER_EXCLUSIVE_MIN") 
                        ? localizationMap.get("HCM_VALIDATION_NUMBER_EXCLUSIVE_MIN") 
                        : "Must be a number greater than %.0f";
                    return String.format(template, column.getExclusiveMinimum().doubleValue());
                } else if (column.getExclusiveMaximum() != null) {
                    String template = localizationMap != null && localizationMap.containsKey("HCM_VALIDATION_NUMBER_EXCLUSIVE_MAX") 
                        ? localizationMap.get("HCM_VALIDATION_NUMBER_EXCLUSIVE_MAX") 
                        : "Must be a number less than %.0f";
                    return String.format(template, column.getExclusiveMaximum().doubleValue());
                }
            } else {
                // Pure numeric validation - no range constraints
                String template = localizationMap != null && localizationMap.containsKey("HCM_VALIDATION_NUMBER_ONLY") 
                    ? localizationMap.get("HCM_VALIDATION_NUMBER_ONLY") 
                    : "Must be a number only";
                return template;
            }
        }
        
        return "Invalid value";
    }
    
    /**
     * Build conditional formatting formula for validation
     */
    private String buildConditionalFormula(ColumnDef column, String columnLetter) {
        StringBuilder formula = new StringBuilder();
        
        if ("string".equals(column.getType())) {
            // Text length validation formula - using relative reference for current row
            formula.append("AND(");
            formula.append(columnLetter).append("3<>\"\","); // Cell is not empty (relative to first data row)
            
            if (column.getMinLength() != null && column.getMaxLength() != null) {
                formula.append("OR(LEN(").append(columnLetter).append("3)<").append(column.getMinLength());
                formula.append(",LEN(").append(columnLetter).append("3)>").append(column.getMaxLength()).append(")");
            } else if (column.getMinLength() != null) {
                formula.append("LEN(").append(columnLetter).append("3)<").append(column.getMinLength());
            } else if (column.getMaxLength() != null) {
                formula.append("LEN(").append(columnLetter).append("3)>").append(column.getMaxLength());
            }
            formula.append(")");
            
        } else if ("number".equals(column.getType())) {
            // Number validation formula - check for non-numeric + range validation
            formula.append("AND(");
            formula.append(columnLetter).append("3<>\"\","); // Cell is not empty
            
            // Check if it's non-numeric OR out of range
            formula.append("OR(");
            formula.append("NOT(ISNUMBER(").append(columnLetter).append("3))"); // Non-numeric entries
            
            // Add range validation if constraints exist
            boolean hasRangeConstraints = column.getMinimum() != null || column.getMaximum() != null || 
                                         column.getExclusiveMinimum() != null || column.getExclusiveMaximum() != null;
            
            if (hasRangeConstraints) {
                formula.append(",AND(ISNUMBER(").append(columnLetter).append("3),"); // Only check range if numeric
                
                if (column.getMinimum() != null && column.getMaximum() != null) {
                    formula.append("OR(").append(columnLetter).append("3<").append(column.getMinimum().doubleValue());
                    formula.append(",").append(columnLetter).append("3>").append(column.getMaximum().doubleValue()).append(")");
                } else if (column.getMinimum() != null) {
                    formula.append(columnLetter).append("3<").append(column.getMinimum().doubleValue());
                } else if (column.getMaximum() != null) {
                    formula.append(columnLetter).append("3>").append(column.getMaximum().doubleValue());
                } else if (column.getExclusiveMinimum() != null) {
                    formula.append(columnLetter).append("3<=").append(column.getExclusiveMinimum().doubleValue());
                } else if (column.getExclusiveMaximum() != null) {
                    formula.append(columnLetter).append("3>=").append(column.getExclusiveMaximum().doubleValue());
                }
                formula.append(")"); // Close AND(ISNUMBER...)
            }
            
            formula.append(")"); // Close OR(NOT(ISNUMBER...)
            formula.append(")"); // Close main AND
        }
        
        return formula.length() > 0 ? formula.toString() : null;
    }
    
    /**
     * Add cell comments for error messages that appear on hover
     */
    private void addCellComments(Sheet sheet, int colIndex, String errorMessage, Map<String, String> localizationMap) {
        try {
            Drawing<?> drawing = sheet.createDrawingPatriarch();
            CreationHelper factory = sheet.getWorkbook().getCreationHelper();
            
            // Add comment to shown header cell (row 2) with validation rules
            Row shownHeaderRow = sheet.getRow(1);
            if (shownHeaderRow != null) {
                Cell shownHeaderCell = shownHeaderRow.getCell(colIndex);
                if (shownHeaderCell != null) {
                    ClientAnchor anchor = factory.createClientAnchor();
                    anchor.setCol1(colIndex);
                    anchor.setCol2(colIndex + 3);
                    anchor.setRow1(1);
                    anchor.setRow2(4);
                    
                    Comment comment = drawing.createCellComment(anchor);
                    
                    // Get localized text for validation message components
                    String validationLabel = getLocalizedText("HCM_VALIDATION_LABEL", "Validation", localizationMap);
                    String highlightText = getLocalizedText("HCM_VALIDATION_HIGHLIGHT_MESSAGE", "Invalid entries will be highlighted in red color", localizationMap);
                    String authorName = getLocalizedText("HCM_CONSOLE_TEAM", "Console Team", localizationMap);
                    
                    // Build localized tooltip message
                    String tooltipMessage = validationLabel + ": " + errorMessage + "\n\n" + highlightText + ".";
                    RichTextString str = factory.createRichTextString(tooltipMessage);
                    comment.setString(str);
                    comment.setAuthor(authorName); // Set localized author name
                    comment.setVisible(false); // Hidden by default, shows on hover
                    
                    shownHeaderCell.setCellComment(comment);
                }
            }
        } catch (Exception e) {
            log.debug("Could not add cell comments: {}", e.getMessage());
        }
    }

    /**
     * Get localized text from map with fallback to default
     */
    private String getLocalizedText(String key, String defaultValue, Map<String, String> localizationMap) {
        if (localizationMap != null && localizationMap.containsKey(key)) {
            return localizationMap.get(key);
        }
        return defaultValue;
    }
    
    /**
     * Set validation error color from configuration
     */
    private void setValidationErrorColor(PatternFormatting fill) {
        try {
            // Use custom color from configuration
            if (fill instanceof org.apache.poi.xssf.usermodel.XSSFPatternFormatting) {
                org.apache.poi.xssf.usermodel.XSSFPatternFormatting xssfFill = (org.apache.poi.xssf.usermodel.XSSFPatternFormatting) fill;
                java.awt.Color color = hexToColor(config.getValidationErrorColor());
                org.apache.poi.xssf.usermodel.XSSFColor validationColor = new org.apache.poi.xssf.usermodel.XSSFColor(color, null);
                xssfFill.setFillBackgroundColor(validationColor);
            } else {
                // Fallback to closest indexed color for non-XLSX formats
                fill.setFillBackgroundColor(IndexedColors.RED.getIndex());
            }
        } catch (Exception e) {
            log.warn("Failed to set custom validation error color, using fallback: {}", e.getMessage());
            fill.setFillBackgroundColor(IndexedColors.RED.getIndex());
        }
    }
    
    /**
     * Convert hex color string to Color object
     */
    private java.awt.Color hexToColor(String hex) {
        if (hex == null || hex.trim().isEmpty()) {
            return new java.awt.Color(196, 4, 4); // Default #ff0000
        }
        
        // Remove # if present
        if (hex.startsWith("#")) {
            hex = hex.substring(1);
        }
        
        try {
            int rgb = Integer.parseInt(hex, 16);
            return new java.awt.Color(rgb);
        } catch (NumberFormatException e) {
            log.warn("Invalid hex color format '{}', using default", hex);
            return new java.awt.Color(196, 4, 4); // Default #ff0000
        }
    }
    
}