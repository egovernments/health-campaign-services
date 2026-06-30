package org.egov.excelingestion.util;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.XSSFConditionalFormattingRule;
import org.apache.poi.xssf.usermodel.XSSFSheetConditionalFormatting;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.egov.excelingestion.config.ExcelIngestionConfig;
import org.egov.excelingestion.config.ProcessingConstants;
import org.egov.excelingestion.web.models.excel.ColumnDef;
import org.egov.excelingestion.web.models.excel.ConditionalRequired;
import org.egov.excelingestion.web.models.excel.MultiSelectDetails;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Utility class for populating Excel sheets with data
 * Follows patterns from ExcelSchemaSheetCreator and reuses existing utilities
 */
@Component
@Slf4j
public class ExcelDataPopulator {

    private static final String DROPDOWN_HELPER_SHEET_NAME = "_h_Dropdowns_h_";
    private static final int INLINE_LIST_CHAR_LIMIT = 255;

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

        // 2. Get or Create Sheet - Use existing sheet if present, otherwise create new
        Sheet sheet = workbook.getSheet(sheetName);
        if (sheet == null) {
            sheet = workbook.createSheet(sheetName);
        }

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
        
        log.info("Successfully added sheet: {} to workbook", sheetName);
        return workbook;
    }

    /**
     * Create header rows following ExcelSchemaSheetCreator pattern
     * Row 0: technical names (hidden), Row 1: localized names
     * Appends to existing columns if sheet already has content
     */
    private void createHeaderRows(Workbook workbook, Sheet sheet, List<ColumnDef> columnProperties, Map<String, String> localizationMap) {
        // Get or create Row 0 (hidden technical names) and Row 1 (localized visible headers)
        Row hiddenRow = sheet.getRow(0);
        Row visibleRow = sheet.getRow(1);
        
        if (hiddenRow == null) {
            hiddenRow = sheet.createRow(0);
        }
        if (visibleRow == null) {
            visibleRow = sheet.createRow(1);
        }
        
        // Find starting column index (append after existing columns)
        int startCol = visibleRow.getLastCellNum();
        if (startCol < 0) startCol = 0;

        for (int i = 0; i < columnProperties.size(); i++) {
            int col = startCol + i;
            ColumnDef def = columnProperties.get(i);

            // Row 0: technical name
            Cell techCell = hiddenRow.getCell(col);
            if (techCell == null) {
                techCell = hiddenRow.createCell(col);
            }
            techCell.setCellValue(def.getName());

            // Row 1: localized display name
            Cell headerCell = visibleRow.getCell(col);
            if (headerCell == null) {
                headerCell = visibleRow.createCell(col);
            }
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
     * Fill data rows using header-to-column mapping approach
     * Maps data keys to existing header columns in the sheet
     */
    private void fillDataRows(Workbook workbook, Sheet sheet, List<ColumnDef> columnProperties, 
                             List<Map<String, Object>> dataRows) {
        log.debug("Filling {} data rows", dataRows.size());

        // Get header row (row 1, which is visible)
        Row headerRow = sheet.getRow(0);
        if (headerRow == null) {
            log.warn("No header row found in sheet, cannot fill data");
            return;
        }

        // Create mapping from header name to column index
        Map<String, Integer> headerToColumnMap = new HashMap<>();
        for (int colIdx = 0; colIdx < headerRow.getLastCellNum(); colIdx++) {
            Cell headerCell = headerRow.getCell(colIdx);
            if (headerCell != null && headerCell.getCellType() == CellType.STRING) {
                String headerName = headerCell.getStringCellValue();
                headerToColumnMap.put(headerName, colIdx);
            }
        }

        // Build map: parent column name -> ordered list of _MULTISELECT_* column indexes
        Map<String, List<Integer>> parentToMultiselectColIndexes = new HashMap<>();
        for (ColumnDef col : columnProperties) {
            if ("multiselect_item".equals(col.getType())) {
                parentToMultiselectColIndexes
                    .computeIfAbsent(col.getParentColumn(), k -> new ArrayList<>())
                    .add(headerToColumnMap.getOrDefault(col.getName(), -1));
            }
        }

        // Map column name -> ColumnDef once (O(1) lookup inside the row loop instead of a per-cell
        // stream scan). Merge fn keeps the first def on duplicate names, matching the prior findFirst().
        Map<String, ColumnDef> colDefByName = columnProperties.stream()
                .collect(Collectors.toMap(ColumnDef::getName, col -> col, (a, b) -> a));

        // Fill data rows using header mapping
        for (int rowIdx = 0; rowIdx < dataRows.size(); rowIdx++) {
            Map<String, Object> dataRow = dataRows.get(rowIdx);
            Row excelRow = sheet.getRow(rowIdx + 2); // Start from row 2 (after headers)
            if (excelRow == null) {
                excelRow = sheet.createRow(rowIdx + 2);
            }

            // For each data key-value pair, find matching header column and fill data
            for (Map.Entry<String, Object> entry : dataRow.entrySet()) {
                String dataKey = entry.getKey();
                Object value = entry.getValue();

                // If this key is a multiselect parent with comma-separated value, split into individual columns
                List<Integer> multiselectColIndexes = parentToMultiselectColIndexes.get(dataKey);
                if (multiselectColIndexes != null && value != null && !value.toString().trim().isEmpty()) {
                    String[] parts = value.toString().split(",");
                    for (int p = 0; p < parts.length && p < multiselectColIndexes.size(); p++) {
                        int msColIdx = multiselectColIndexes.get(p);
                        if (msColIdx < 0) continue;
                        String part = parts[p].trim();
                        if (!part.isEmpty()) {
                            Cell cell = excelRow.getCell(msColIdx);
                            if (cell == null) cell = excelRow.createCell(msColIdx);
                            cell.setCellValue(part);
                        }
                    }
                    continue;
                }

                // Find column index for this data key from header mapping
                Integer colIdx = headerToColumnMap.get(dataKey);
                if (colIdx != null && value != null) {
                    Cell cell = excelRow.getCell(colIdx);
                    if (cell == null) {
                        cell = excelRow.createCell(colIdx);
                    }

                    // Find corresponding ColumnDef for validation/formatting (O(1))
                    ColumnDef columnDef = colDefByName.get(dataKey);

                    setCellValue(cell, value, columnDef);
                } else if (colIdx == null) {
                    log.debug("No header column found for data key: {}", dataKey);
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
            if (column != null && column.getPrefix() != null && !column.getPrefix().isEmpty()) {
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
        
        // Find starting column index (same as headers)
        Row visibleRow = sheet.getRow(1);
        int totalExistingCols = visibleRow != null ? visibleRow.getLastCellNum() : 0;
        int startCol = Math.max(0, totalExistingCols - columns.size());
        
        // Apply styling to data rows
        int actualLastRow = ExcelUtil.findActualLastRowWithData(sheet);
        for (int rowIdx = 2; rowIdx <= actualLastRow; rowIdx++) {
            Row row = sheet.getRow(rowIdx);
            if (row == null) {
                continue;
            }
            
            boolean needsAutoHeight = false;
            
            for (int i = 0; i < columns.size(); i++) {
                int colIdx = startCol + i;
                ColumnDef column = columns.get(i);
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
                for (int i = 0; i < columns.size(); i++) {
                    if (columns.get(i).isAdjustHeight()) {
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

        // Find starting column index (same as headers)
        Row visibleRow = sheet.getRow(1);
        int totalExistingCols = visibleRow != null ? visibleRow.getLastCellNum() : 0;
        int startCol = Math.max(0, totalExistingCols - columns.size());

        // Build map: technical column name → column index (from hidden row 0)
        Map<String, Integer> headerNameToColIndex = new HashMap<>();
        Row technicalRow = sheet.getRow(0);
        if (technicalRow != null) {
            for (int i = 0; i < technicalRow.getLastCellNum(); i++) {
                Cell cell = technicalRow.getCell(i);
                if (cell != null && cell.getCellType() == CellType.STRING) {
                    headerNameToColIndex.put(cell.getStringCellValue(), i);
                }
            }
        }

        for (int i = 0; i < columns.size(); i++) {
            int colIndex = startCol + i;
            ColumnDef column = columns.get(i);
            
            // Apply enum dropdown validation
            if (column.getEnumValues() != null && !column.getEnumValues().isEmpty()) {
                DataValidationConstraint constraint;
                if (String.join(",", column.getEnumValues()).length() > INLINE_LIST_CHAR_LIMIT) {
                    Sheet dropdownSheet = getOrCreateDropdownSheet(workbook);
                    int dropColIdx = writeEnumToDropdownSheet(dropdownSheet, column.getEnumValues());
                    String rangeName = createDropdownNamedRange(workbook, column.getTechnicalName(), dropColIdx, column.getEnumValues().size());
                    constraint = dvHelper.createFormulaListConstraint(rangeName);
                } else {
                    constraint = dvHelper.createExplicitListConstraint(column.getEnumValues().toArray(new String[0]));
                }
                // Use actual data row count or config limit, whichever is larger to cover all rows
                // ExcelUtil.findActualLastRowWithData(sheet) is 0-based, so add 1 to get actual count, then compare with limit
                int actualDataRows = ExcelUtil.findActualLastRowWithData(sheet) + 1;
                int maxRow = Math.max(actualDataRows, config.getExcelRowLimit());
                CellRangeAddressList addressList = new CellRangeAddressList(2, maxRow, colIndex, colIndex);
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

            // Apply conditional required validation (formula-based DataValidation with STOP style)
            else if (column.getRequiredIf() != null) {
                String triggerColName = column.getRequiredIf().getColumn();
                Integer triggerColIdx = headerNameToColIndex.get(triggerColName);
                List<String> triggerValues = column.getRequiredIf().getValues();
                // Skip when there are no trigger values: an empty list has no condition to
                // enforce and would build a malformed =OR(AND(),...) formula.
                if (triggerColIdx != null && triggerValues != null && !triggerValues.isEmpty()) {
                    String triggerLetter = columnIndexToLetter(triggerColIdx);
                    String thisLetter = columnIndexToLetter(colIndex);

                    // Formula is valid when trigger column does NOT match any trigger value OR this cell is non-empty
                    String notTriggerPart = triggerValues.stream()
                            .map(v -> "$" + triggerLetter + "3<>\"" + v + "\"")
                            .collect(Collectors.joining(","));
                    String formula = "=OR(AND(" + notTriggerPart + "),LEN(TRIM($" + thisLetter + "3))>0)";

                    int actualDataRows = ExcelUtil.findActualLastRowWithData(sheet) + 1;
                    int maxRow = Math.max(actualDataRows, config.getExcelRowLimit());
                    CellRangeAddressList range = new CellRangeAddressList(2, maxRow, colIndex, colIndex);
                    DataValidationConstraint constraint = dvHelper.createCustomConstraint(formula);
                    DataValidation dv = dvHelper.createValidation(constraint, range);
                    dv.setErrorStyle(DataValidation.ErrorStyle.STOP);
                    dv.setShowErrorBox(true);
                    String errMsg = column.getErrorMessage() != null && !column.getErrorMessage().isEmpty()
                            ? column.getErrorMessage()
                            : "This field is required for the selected payment provider.";
                    dv.createErrorBox(
                        LocalizationUtil.getLocalizedMessage(localizationMap, "HCM_VALIDATION_CONDITIONAL_REQUIRED", "Required Field"),
                        LocalizationUtil.getLocalizedMessage(localizationMap, column.getErrorMessage(), errMsg)
                    );
                    sheet.addValidationData(dv);

                    // Apply visual validation (conditional formatting): highlight empty required cells
                    // and cells with invalid format when the trigger condition is met
                    applyConditionalVisualValidation(workbook, sheet, column, colIndex,
                            triggerColIdx, triggerValues, localizationMap);
                }
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

        // Apply payee-field-specific conditional formatting rules (payment provider conditions)
        int actualDataRows = ExcelUtil.findActualLastRowWithData(sheet) + 1;
        int maxRow = Math.max(actualDataRows, config.getExcelRowLimit());
        applyPayeeFieldConditionalFormatting(sheet, headerNameToColIndex, maxRow);
    }

    /**
     * Applies conditional formatting rules for payee/worker detail fields based on payment provider.
     * All rules use only sheet data (Excel formulas) — no API validation required.
     *
     * BANK provider:
     *   payeeName        — required; max 35 chars when beneficiaryCode is filled, else max 100
     *   beneficiaryCode  — required; max 35 chars
     *   bankAccount      — required; must be exactly 10 numeric digits
     *   bankCode         — required; must be empty, 3 digits, or 9 digits
     *
     * MTN provider:
     *   payeePhoneNumber — required
     *
     * Non-BANK:
     *   payeeName        — not required, but max 35 chars if filled
     */
    private void applyPayeeFieldConditionalFormatting(Sheet sheet, Map<String, Integer> colIndexMap, int maxRow) {
        Integer providerIdx      = colIndexMap.get(ProcessingConstants.PAYMENT_PROVIDER_COL);
        Integer payeeNameIdx     = colIndexMap.get(ProcessingConstants.PAYEE_NAME_COL);
        Integer beneficiaryIdx   = colIndexMap.get(ProcessingConstants.BENEFICIARY_CODE_COL);
        Integer bankAccountIdx   = colIndexMap.get(ProcessingConstants.BANK_ACCOUNT_COL);
        Integer bankCodeIdx      = colIndexMap.get(ProcessingConstants.BANK_CODE_COL);
        Integer payeePhoneIdx    = colIndexMap.get(ProcessingConstants.PAYEE_PHONE_NUMBER_COL);

        if (providerIdx == null) {
            log.debug("Payment provider column not found in sheet — skipping payee conditional formatting");
            return;
        }

        XSSFSheetConditionalFormatting cf = (XSSFSheetConditionalFormatting) sheet.getSheetConditionalFormatting();
        String p = columnIndexToLetter(providerIdx);

        // --- payeeName ---
        if (payeeNameIdx != null) {
            String n = columnIndexToLetter(payeeNameIdx);
            String b = beneficiaryIdx != null ? columnIndexToLetter(beneficiaryIdx) : null;

            // Required when BANK or MTN and empty (skip if provider is empty)
            addPayeeCfRule(cf, maxRow, payeeNameIdx,
                    "AND(OR($" + p + "3=\"BANK\",$" + p + "3=\"MTN\"),LEN(TRIM($" + n + "3))=0)");

            if (b != null) {
                // BANK + beneficiaryCode filled: max 35 chars
                addPayeeCfRule(cf, maxRow, payeeNameIdx,
                        "AND($" + p + "3=\"BANK\",LEN(TRIM($" + b + "3))>0,$" + n + "3<>\"\",LEN($" + n + "3)>35)");
                // BANK + beneficiaryCode empty: max 100 chars
                addPayeeCfRule(cf, maxRow, payeeNameIdx,
                        "AND($" + p + "3=\"BANK\",LEN(TRIM($" + b + "3))=0,$" + n + "3<>\"\",LEN($" + n + "3)>100)");
            } else {
                // No beneficiaryCode column — apply single max 100 rule for BANK
                addPayeeCfRule(cf, maxRow, payeeNameIdx,
                        "AND($" + p + "3=\"BANK\",$" + n + "3<>\"\",LEN($" + n + "3)>100)");
            }

            // Non-BANK, non-empty provider: not required, but max 35 chars if filled
            addPayeeCfRule(cf, maxRow, payeeNameIdx,
                    "AND($" + p + "3<>\"BANK\",$" + p + "3<>\"\",$" + n + "3<>\"\",LEN($" + n + "3)>35)");
        }

        // --- beneficiaryCode ---
        if (beneficiaryIdx != null) {
            String b = columnIndexToLetter(beneficiaryIdx);

            // Required when BANK and empty
            addPayeeCfRule(cf, maxRow, beneficiaryIdx,
                    "AND($" + p + "3=\"BANK\",LEN(TRIM($" + b + "3))=0)");

            // Non-empty: max 35 chars (BANK only) or contains whitespace (any provider)
            addPayeeCfRule(cf, maxRow, beneficiaryIdx,
                    "AND($" + b + "3<>\"\",OR(AND($" + p + "3=\"BANK\",LEN($" + b + "3)>35),LEN($" + b + "3)<>LEN(SUBSTITUTE($" + b + "3,\" \",\"\"))))");
        }

        // --- bankAccount ---
        if (bankAccountIdx != null) {
            String a = columnIndexToLetter(bankAccountIdx);

            // Required when BANK and empty
            addPayeeCfRule(cf, maxRow, bankAccountIdx,
                    "AND($" + p + "3=\"BANK\",LEN(TRIM($" + a + "3))=0)");

            // Non-empty: exactly 10 numeric digits (BANK only) or contains whitespace (any provider)
            addPayeeCfRule(cf, maxRow, bankAccountIdx,
                    "AND($" + a + "3<>\"\",OR(AND($" + p + "3=\"BANK\",OR(LEN($" + a + "3)<>10,NOT(ISNUMBER(VALUE($" + a + "3))))),LEN($" + a + "3)<>LEN(SUBSTITUTE($" + a + "3,\" \",\"\"))))");
        }

        // --- bankCode ---
        if (bankCodeIdx != null) {
            String bc = columnIndexToLetter(bankCodeIdx);

            // Required when BANK and empty
            addPayeeCfRule(cf, maxRow, bankCodeIdx,
                    "AND($" + p + "3=\"BANK\",LEN(TRIM($" + bc + "3))=0)");

            // Non-empty: 3 or 9 numeric digits (BANK only) or contains whitespace (any provider)
            addPayeeCfRule(cf, maxRow, bankCodeIdx,
                    "AND($" + bc + "3<>\"\",OR(AND($" + p + "3=\"BANK\",OR(NOT(ISNUMBER(VALUE($" + bc + "3))),AND(LEN($" + bc + "3)<>3,LEN($" + bc + "3)<>9))),LEN($" + bc + "3)<>LEN(SUBSTITUTE($" + bc + "3,\" \",\"\"))))");
        }

        // --- payeePhoneNumber (MTN: required) ---
        if (payeePhoneIdx != null) {
            String pp = columnIndexToLetter(payeePhoneIdx);
            addPayeeCfRule(cf, maxRow, payeePhoneIdx,
                    "AND($" + p + "3=\"MTN\",LEN(TRIM($" + pp + "3))=0)");
        }

        log.info("Applied payee field conditional formatting rules");
    }

    private void addPayeeCfRule(XSSFSheetConditionalFormatting cf, int maxRow, int colIndex, String formula) {
        CellRangeAddress[] regions = {new CellRangeAddress(2, maxRow, colIndex, colIndex)};
        XSSFConditionalFormattingRule rule = cf.createConditionalFormattingRule(formula);
        PatternFormatting fill = rule.createPatternFormatting();
        setValidationErrorColor(fill);
        fill.setFillPattern(PatternFormatting.SOLID_FOREGROUND);
        cf.addConditionalFormatting(regions, rule);
    }
    
    /**
     * Convert a 0-based column index to an Excel column letter (A, B, ..., Z, AA, AB, ...)
     */
    private String columnIndexToLetter(int index) {
        StringBuilder sb = new StringBuilder();
        index++; // 0-based to 1-based
        while (index > 0) {
            index--;
            sb.insert(0, (char) ('A' + (index % 26)));
            index /= 26;
        }
        return sb.toString();
    }

    private Sheet getOrCreateDropdownSheet(Workbook workbook) {
        Sheet existing = workbook.getSheet(DROPDOWN_HELPER_SHEET_NAME);
        if (existing != null) {
            return existing;
        }
        Sheet sheet = workbook.createSheet(DROPDOWN_HELPER_SHEET_NAME);
        workbook.setSheetHidden(workbook.getSheetIndex(DROPDOWN_HELPER_SHEET_NAME), true);
        return sheet;
    }

    private int writeEnumToDropdownSheet(Sheet dropdownSheet, List<String> values) {
        Row firstRow = dropdownSheet.getRow(0);
        int colIdx = (firstRow != null && firstRow.getLastCellNum() > 0) ? firstRow.getLastCellNum() : 0;
        for (int rowIdx = 0; rowIdx < values.size(); rowIdx++) {
            Row row = dropdownSheet.getRow(rowIdx);
            if (row == null) row = dropdownSheet.createRow(rowIdx);
            row.createCell(colIdx).setCellValue(values.get(rowIdx));
        }
        return colIdx;
    }

    private String createDropdownNamedRange(Workbook workbook, String technicalName, int colIdx, int rowCount) {
        String safeName = (technicalName != null ? technicalName : "col_" + colIdx)
                .replaceAll("[^A-Za-z0-9_]", "_");
        String rangeName = "_DR_" + safeName;
        Name existing = workbook.getName(rangeName);
        if (existing != null) {
            workbook.removeName(existing);
        }
        String colLetter = columnIndexToLetter(colIdx);
        Name name = workbook.createName();
        name.setNameName(rangeName);
        name.setRefersToFormula("'" + DROPDOWN_HELPER_SHEET_NAME + "'!$" + colLetter + "$1:$" + colLetter + "$" + rowCount);
        return rangeName;
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
                            .freezeTillData(column.isFreezeTillData())
                            .freezeColumnIfFilled(column.isFreezeColumnIfFilled())
                            .width(column.getWidth())
                            .wrapText(column.isWrapText())
                            .build();
                    expandedColumns.add(multiCol);
                }
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
                        // Copy conditional required validation
                        .requiredIf(column.getRequiredIf())
                        .build();
                expandedColumns.add(regularCol);
            }
        }
        
        return expandedColumns;
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
                // Use actual data row count or config limit, whichever is larger to cover all rows
                int actualDataRows = ExcelUtil.findActualLastRowWithData(sheet) + 1;
                int maxRow = Math.max(actualDataRows, config.getExcelRowLimit());
                CellRangeAddress[] regions = {new CellRangeAddress(2, maxRow, colIndex, colIndex)};
                
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
     * Apply visual validation (conditional formatting) that only triggers when the requiredIf condition is met.
     * E.g., highlight bankAccount red only when paymentProvider = BANK and the value is invalid.
     */
    private void applyConditionalVisualValidation(Workbook workbook, Sheet sheet, ColumnDef column, int colIndex,
                                                   int triggerColIdx, List<String> triggerValues,
                                                   Map<String, String> localizationMap) {
        try {
            String errorMessage = buildErrorMessage(column, localizationMap);
            String colLetter = getColumnLetter(colIndex + 1);
            String triggerLetter = columnIndexToLetter(triggerColIdx);

            // Build trigger match: OR($AA3="BANK",$AA3="MTN")
            String triggerMatch = triggerValues.stream()
                    .map(v -> "$" + triggerLetter + "3=\"" + v + "\"")
                    .collect(Collectors.joining(","));
            String triggerCondition = triggerValues.size() == 1 ? triggerMatch : "OR(" + triggerMatch + ")";

            XSSFSheetConditionalFormatting cf = (XSSFSheetConditionalFormatting) sheet.getSheetConditionalFormatting();
            int actualDataRows = ExcelUtil.findActualLastRowWithData(sheet) + 1;
            int maxRow = Math.max(actualDataRows, config.getExcelRowLimit());
            CellRangeAddress[] regions = {new CellRangeAddress(2, maxRow, colIndex, colIndex)};

            // Rule 1: required-but-empty — highlight when trigger is met and cell is empty
            String requiredEmptyFormula = "AND(" + triggerCondition + ",LEN(TRIM(" + colLetter + "3))=0)";
            XSSFConditionalFormattingRule emptyRule = cf.createConditionalFormattingRule(requiredEmptyFormula);
            PatternFormatting emptyFill = emptyRule.createPatternFormatting();
            setValidationErrorColor(emptyFill);
            emptyFill.setFillPattern(PatternFormatting.SOLID_FOREGROUND);
            cf.addConditionalFormatting(regions, emptyRule);

            // Rule 2: format violation — highlight when trigger is met, cell is non-empty, but format is invalid
            String violation = buildFormatViolation(column, colLetter);
            if (violation != null) {
                String formatFormula = "AND(" + triggerCondition + "," + colLetter + "3<>\"\"," + violation + ")";
                XSSFConditionalFormattingRule formatRule = cf.createConditionalFormattingRule(formatFormula);
                PatternFormatting formatFill = formatRule.createPatternFormatting();
                setValidationErrorColor(formatFill);
                formatFill.setFillPattern(PatternFormatting.SOLID_FOREGROUND);
                cf.addConditionalFormatting(regions, formatRule);
            }

            addCellComments(sheet, colIndex, errorMessage, localizationMap);
            log.info("Applied conditional visual validation for column '{}': {}", column.getName(), errorMessage);
        } catch (Exception e) {
            log.warn("Failed to apply conditional visual validation for column '{}': {}", column.getName(), e.getMessage());
        }
    }

    /**
     * Build the format violation part of a conditional formatting formula.
     * Returns null if no format constraints exist.
     */
    private String buildFormatViolation(ColumnDef column, String colLetter) {
        if ("string".equals(column.getType())) {
            if (column.getMinLength() != null && column.getMaxLength() != null) {
                return "OR(LEN(" + colLetter + "3)<" + column.getMinLength()
                        + ",LEN(" + colLetter + "3)>" + column.getMaxLength() + ")";
            } else if (column.getMaxLength() != null) {
                return "LEN(" + colLetter + "3)>" + column.getMaxLength();
            } else if (column.getMinLength() != null) {
                return "LEN(" + colLetter + "3)<" + column.getMinLength();
            }
        } else if ("number".equals(column.getType())) {
            if (column.getMinimum() != null && column.getMaximum() != null) {
                return "OR(NOT(ISNUMBER(" + colLetter + "3)),"
                        + colLetter + "3<" + column.getMinimum() + ","
                        + colLetter + "3>" + column.getMaximum() + ")";
            }
            return "NOT(ISNUMBER(" + colLetter + "3))";
        }
        return null;
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