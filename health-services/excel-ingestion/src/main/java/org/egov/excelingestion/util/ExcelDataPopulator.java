package org.egov.excelingestion.util;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddressList;
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
            
            // Apply enhanced header styling - reuse existing method
            headerCell.setCellStyle(excelStyleHelper.createCustomHeaderStyle(workbook, def.getColorHex(), def.isWrapText()));

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
            
            // Apply enum dropdown validation - reuse existing pattern
            if (column.getEnumValues() != null && !column.getEnumValues().isEmpty()) {
                String[] enumArray = column.getEnumValues().toArray(new String[0]);
                DataValidationConstraint constraint = dvHelper.createExplicitListConstraint(enumArray);
                CellRangeAddressList addressList = new CellRangeAddressList(2, config.getExcelRowLimit(), colIndex, colIndex);
                DataValidation validation = dvHelper.createValidation(constraint, addressList);
                validation.setErrorStyle(DataValidation.ErrorStyle.STOP);
                validation.setShowErrorBox(true);
                validation.createErrorBox("Invalid Selection", "Please select a value from the dropdown list.");
                validation.setShowPromptBox(false); // No input prompt, only error on invalid data
                sheet.addValidationData(validation);
            }
            
            // Apply MDMS number validation based on minimum/maximum properties
            else if ("number".equals(column.getType()) && hasNumberValidation(column)) {
                applyNumberValidation(dvHelper, sheet, column, colIndex, localizationMap);
            }
        }
    }
    
    /**
     * Check if column has number validation rules
     */
    private boolean hasNumberValidation(ColumnDef column) {
        return column.getMinimum() != null || column.getMaximum() != null;
    }
    
    /**
     * Apply number validation rules from MDMS schema (minimum/maximum only)
     */
    private void applyNumberValidation(DataValidationHelper dvHelper, Sheet sheet, ColumnDef column, int colIndex, Map<String, String> localizationMap) {
        try {
            DataValidationConstraint constraint = null;
            String errorMessage = "";
            
            // Handle MDMS minimum/maximum validation
            if (column.getMinimum() != null && column.getMaximum() != null) {
                // Both minimum and maximum defined from MDMS schema
                constraint = dvHelper.createDecimalConstraint(
                    DataValidationConstraint.OperatorType.BETWEEN,
                    String.valueOf(column.getMinimum().doubleValue()),
                    String.valueOf(column.getMaximum().doubleValue())
                );
                // Use custom error message from MDMS if available, otherwise use dynamic message
                if (column.getErrorMessage() != null && !column.getErrorMessage().isEmpty()) {
                    // Try to localize the custom error message
                    errorMessage = getLocalizedMessage(localizationMap, column.getErrorMessage(), column.getErrorMessage());
                } else {
                    // Use dynamic message
                    errorMessage = String.format("Value must be between %.0f and %.0f", 
                        column.getMinimum().doubleValue(), column.getMaximum().doubleValue());
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
                    errorMessage = getLocalizedMessage(localizationMap, column.getErrorMessage(), column.getErrorMessage());
                } else {
                    // Use dynamic message
                    errorMessage = String.format("Value must be at least %.0f", column.getMinimum().doubleValue());
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
                    errorMessage = getLocalizedMessage(localizationMap, column.getErrorMessage(), column.getErrorMessage());
                } else {
                    // Use dynamic message
                    errorMessage = String.format("Value must be at most %.0f", column.getMaximum().doubleValue());
                }
            }
            
            if (constraint != null) {
                CellRangeAddressList addressList = new CellRangeAddressList(2, config.getExcelRowLimit(), colIndex, colIndex);
                DataValidation validation = dvHelper.createValidation(constraint, addressList);
                
                // Configure validation behavior - no prompts, only error on invalid entry
                validation.setErrorStyle(DataValidation.ErrorStyle.STOP);
                validation.setShowErrorBox(true);
                validation.createErrorBox("Invalid Number", errorMessage);
                validation.setShowPromptBox(false); // No input prompt as requested
                
                sheet.addValidationData(validation);
                log.info("Applied MDMS number validation for column '{}': {}", column.getName(), errorMessage);
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
     * Get localized message from localization map, with fallback to default message
     */
    private String getLocalizedMessage(Map<String, String> localizationMap, String key, String defaultMessage) {
        if (localizationMap != null && key != null && localizationMap.containsKey(key)) {
            return localizationMap.get(key);
        }
        return defaultMessage;
    }
}