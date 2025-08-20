package org.egov.excelingestion.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.egov.excelingestion.web.models.excel.ColumnDef;
import org.egov.excelingestion.web.models.excel.MultiSelectDetails;
import java.awt.Color;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.egov.excelingestion.config.ExcelIngestionConfig;
import org.springframework.stereotype.Component;

@Component
public class ExcelSchemaSheetCreator {
    
    private final ExcelIngestionConfig config;
    private final ExcelStyleHelper excelStyleHelper;
    private final CellProtectionManager cellProtectionManager;
    
    public ExcelSchemaSheetCreator(ExcelIngestionConfig config, ExcelStyleHelper excelStyleHelper, 
                                 CellProtectionManager cellProtectionManager) {
        this.config = config;
        this.excelStyleHelper = excelStyleHelper;
        this.cellProtectionManager = cellProtectionManager;
    }

    public Workbook addSchemaSheetFromJson(String json, String sheetName, Workbook workbook) throws IOException {
        return addSchemaSheetFromJson(json, sheetName, workbook, null);
    }
    
    public Workbook addSchemaSheetFromJson(String json, String sheetName, Workbook workbook, Map<String, String> localizationMap) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(json);

        List<ColumnDef> columns = new ArrayList<>();

        // Merge stringProperties & numberProperties
        StreamSupport.stream(root.path("stringProperties").spliterator(), false)
                .map(node -> parseSimpleColumnDef(node))
                .forEach(columns::add);

        StreamSupport.stream(root.path("numberProperties").spliterator(), false)
                .map(node -> parseSimpleColumnDef(node))
                .forEach(columns::add);

        // Sort by orderNumber, stable for ties
        columns.sort(Comparator.comparingInt(ColumnDef::getOrderNumber));

        Sheet sheet = workbook.createSheet(sheetName);

        // Create Row 1 (hidden technical names) and Row 2 (localized visible headers)
        Row hiddenRow = sheet.createRow(0);
        Row visibleRow = sheet.createRow(1);

        CellStyle headerStyle = workbook.createCellStyle();
        Font boldFont = workbook.createFont();
        boldFont.setBold(true);
        headerStyle.setFont(boldFont);

        for (int col = 0; col < columns.size(); col++) {
            ColumnDef def = columns.get(col);

            // Row 1: technical name
            Cell techCell = hiddenRow.createCell(col);
            techCell.setCellValue(def.getName());

            // Row 2: localized description
            Cell headerCell = visibleRow.createCell(col);
            // Use localized name if available, otherwise fall back to description
            String displayName = localizationMap != null && localizationMap.containsKey(def.getName()) 
                ? localizationMap.get(def.getName()) 
                : def.getName();
            headerCell.setCellValue(displayName);
            headerCell.setCellStyle(createColoredHeaderStyle(workbook, def.getColorHex()));

            // Autosize
            sheet.autoSizeColumn(col);
        }

        // Hide first row
        sheet.createFreezePane(0, 2);
        hiddenRow.setZeroHeight(true); // hides the row

        // Prepare cells but don't protect sheet yet - let the caller handle protection
        prepareCellLocking(workbook, sheet, columns);

        return workbook;
    }
    
    public Workbook addEnhancedSchemaSheetFromJson(String json, String sheetName, Workbook workbook, Map<String, String> localizationMap) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(json);

        List<ColumnDef> columns = new ArrayList<>();

        // Process stringProperties 
        StreamSupport.stream(root.path("stringProperties").spliterator(), false)
                .map(node -> parseColumnDef(node, "string"))
                .forEach(columns::add);

        // Process numberProperties
        StreamSupport.stream(root.path("numberProperties").spliterator(), false)
                .map(node -> parseColumnDef(node, "number"))
                .forEach(columns::add);

        // Process enumProperties (new support)
        StreamSupport.stream(root.path("enumProperties").spliterator(), false)
                .map(node -> parseColumnDef(node, "enum"))
                .forEach(columns::add);

        // Sort by orderNumber, stable for ties
        columns.sort(Comparator.comparingInt(ColumnDef::getOrderNumber));

        // Expand multiselect columns
        List<ColumnDef> expandedColumns = expandMultiSelectColumns(columns);

        Sheet sheet = workbook.createSheet(sheetName);

        // Create Row 1 (hidden technical names) and Row 2 (localized visible headers)
        Row hiddenRow = sheet.createRow(0);
        Row visibleRow = sheet.createRow(1);

        int colIndex = 0;
        for (ColumnDef def : expandedColumns) {
            // Row 1: technical name
            Cell techCell = hiddenRow.createCell(colIndex);
            techCell.setCellValue(def.getTechnicalName());

            // Row 2: localized display name
            Cell headerCell = visibleRow.createCell(colIndex);
            String displayName = localizationMap != null && localizationMap.containsKey(def.getName()) 
                ? localizationMap.get(def.getName()) 
                : def.getName();
            headerCell.setCellValue(displayName);
            
            // Apply enhanced header styling with color and text wrapping
            headerCell.setCellStyle(excelStyleHelper.createCustomHeaderStyle(workbook, def.getColorHex(), def.isWrapText()));

            // Set column width if specified
            if (def.getWidth() != null && def.getWidth() > 0) {
                sheet.setColumnWidth(colIndex, def.getWidth() * 256); // POI uses 1/256th of character width
            } else {
                // Autosize if no specific width is set
                sheet.autoSizeColumn(colIndex);
            }

            // Hide column if specified
            if (def.isHideColumn()) {
                sheet.setColumnHidden(colIndex, true);
            }

            colIndex++;
        }

        // Hide first row
        sheet.createFreezePane(0, 2);
        hiddenRow.setZeroHeight(true);

        // Apply validations
        applyValidations(workbook, sheet, expandedColumns);

        // Apply multiselect formulas
        applyMultiSelectFormulas(sheet, expandedColumns);

        // Apply data cell styling and auto-height adjustment for rows
        applyDataCellStyling(workbook, sheet, expandedColumns);

        // Prepare cell locking and apply sheet protection (same as facility sheet)
        prepareEnhancedCellLocking(workbook, sheet, expandedColumns);
        
        // Protect the User sheet with password (same as facility sheet)
        sheet.protectSheet(config.getExcelSheetPassword());

        return workbook;
    }
    
    /**
     * Creates a processed version of the sheet with only columns marked for showInProcessed
     */
    public Workbook addProcessedSchemaSheetFromJson(String json, String sheetName, Workbook workbook, Map<String, String> localizationMap) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(json);

        List<ColumnDef> columns = new ArrayList<>();

        // Process stringProperties 
        StreamSupport.stream(root.path("stringProperties").spliterator(), false)
                .map(node -> parseColumnDef(node, "string"))
                .forEach(columns::add);

        // Process numberProperties
        StreamSupport.stream(root.path("numberProperties").spliterator(), false)
                .map(node -> parseColumnDef(node, "number"))
                .forEach(columns::add);

        // Process enumProperties
        StreamSupport.stream(root.path("enumProperties").spliterator(), false)
                .map(node -> parseColumnDef(node, "enum"))
                .forEach(columns::add);

        // Filter columns to show only those marked for showInProcessed
        List<ColumnDef> processedColumns = columns.stream()
                .filter(ColumnDef::isShowInProcessed)
                .sorted(Comparator.comparingInt(ColumnDef::getOrderNumber))
                .collect(Collectors.toList());

        // Expand multiselect columns
        List<ColumnDef> expandedColumns = expandMultiSelectColumns(processedColumns);

        Sheet sheet = workbook.createSheet(sheetName);

        // Create Row 1 (hidden technical names) and Row 2 (localized visible headers)
        Row hiddenRow = sheet.createRow(0);
        Row visibleRow = sheet.createRow(1);

        int colIndex = 0;
        for (ColumnDef def : expandedColumns) {
            // Row 1: technical name
            Cell techCell = hiddenRow.createCell(colIndex);
            techCell.setCellValue(def.getTechnicalName());

            // Row 2: localized display name
            Cell headerCell = visibleRow.createCell(colIndex);
            String displayName = localizationMap != null && localizationMap.containsKey(def.getName()) 
                ? localizationMap.get(def.getName()) 
                : def.getName();
            headerCell.setCellValue(displayName);
            
            // Apply enhanced header styling with color and text wrapping
            headerCell.setCellStyle(excelStyleHelper.createCustomHeaderStyle(workbook, def.getColorHex(), def.isWrapText()));

            // Set column width if specified
            if (def.getWidth() != null && def.getWidth() > 0) {
                sheet.setColumnWidth(colIndex, def.getWidth() * 256); // POI uses 1/256th of character width
            } else {
                // Autosize if no specific width is set
                sheet.autoSizeColumn(colIndex);
            }

            // Note: hideColumn is ignored for processed files - all included columns are visible

            colIndex++;
        }

        // Hide first row
        sheet.createFreezePane(0, 2);
        hiddenRow.setZeroHeight(true);

        // Apply data cell styling and auto-height adjustment for rows
        applyDataCellStyling(workbook, sheet, expandedColumns);

        // For processed files, typically no cell locking is applied as they're read-only outputs

        return workbook;
    }

    /**
     * Apply data cell styling including text wrapping, prefix text, and auto-height
     */
    private void applyDataCellStyling(Workbook workbook, Sheet sheet, List<ColumnDef> columns) {
        // Create styles for different requirements
        CellStyle wrapTextStyle = excelStyleHelper.createDataCellStyle(workbook, true);
        CellStyle normalStyle = excelStyleHelper.createDataCellStyle(workbook, false);
        
        // Apply styling to initial data rows
        for (int rowIdx = 2; rowIdx < 20; rowIdx++) { // Apply to first 20 rows for demonstration
            Row row = sheet.getRow(rowIdx);
            if (row == null) {
                row = sheet.createRow(rowIdx);
            }
            
            boolean needsAutoHeight = false;
            
            for (int colIdx = 0; colIdx < columns.size(); colIdx++) {
                ColumnDef column = columns.get(colIdx);
                Cell cell = row.getCell(colIdx);
                if (cell == null) {
                    cell = row.createCell(colIdx);
                }
                
                // Apply appropriate cell style
                if (column.isWrapText()) {
                    cell.setCellStyle(wrapTextStyle);
                    needsAutoHeight = true;
                } else {
                    cell.setCellStyle(normalStyle);
                }
                
                // Apply prefix to cell if specified and cell has content
                if (column.getPrefix() != null && !column.getPrefix().isEmpty() && 
                    cell.getCellType() != CellType.BLANK && !cell.toString().trim().isEmpty()) {
                    String currentValue = cell.toString();
                    cell.setCellValue(column.getPrefix() + currentValue);
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

    private ColumnDef parseSimpleColumnDef(JsonNode node) {
        return ColumnDef.builder()
                .name(node.path("name").asText())
                .description(node.path("description").asText())
                .colorHex(node.path("color").asText())
                .orderNumber(node.path("orderNumber").asInt(9999))
                .freezeColumnIfFilled(node.path("freezeColumnIfFilled").asBoolean(false))
                .width(node.has("width") ? node.path("width").asInt() : null)
                .wrapText(node.path("wrapText").asBoolean(false))
                .prefix(node.path("prefix").asText(null))
                .adjustHeight(node.path("adjustHeight").asBoolean(false))
                .hideColumn(node.path("hideColumn").asBoolean(false))
                .showInProcessed(node.path("showInProcessed").asBoolean(true))
                .freezeColumn(node.path("freezeColumn").asBoolean(false))
                .freezeTillData(node.path("freezeTillData").asBoolean(false))
                .unFreezeColumnTillData(node.path("unFreezeColumnTillData").asBoolean(false))
                .build();
    }

    private CellStyle createColoredHeaderStyle(Workbook workbook, String colorHex) {
        CellStyle style = workbook.createCellStyle();
        Font boldFont = workbook.createFont();
        boldFont.setBold(true);
        style.setFont(boldFont);

        if (colorHex != null && !colorHex.isEmpty()) {
            try {
                Color awtColor = Color.decode(colorHex);
                XSSFColor xssfColor = new XSSFColor(awtColor, null);
                ((XSSFCellStyle) style).setFillForegroundColor(xssfColor);
                style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            } catch (Exception ignored) {
            }
        }

        return style;
    }

    private void prepareCellLocking(Workbook workbook, Sheet sheet, List<ColumnDef> columns) {
        // Use the comprehensive cell protection manager to handle all protection features
        cellProtectionManager.applyCellProtection(workbook, sheet, columns);
        
        // Don't protect sheet here - let the caller handle protection after all columns are added
    }

    // Enhanced parsing method for new schema types
    private ColumnDef parseColumnDef(JsonNode node, String type) {
        ColumnDef.ColumnDefBuilder builder = ColumnDef.builder()
                .name(node.path("name").asText())
                .type(type)
                .description(node.path("description").asText())
                .colorHex(node.path("color").asText())
                .orderNumber(node.path("orderNumber").asInt(9999))
                .freezeColumnIfFilled(node.path("freezeColumnIfFilled").asBoolean(false))
                .hideColumn(node.path("hideColumn").asBoolean(false))
                .required(node.path("isRequired").asBoolean(false))
                .width(node.has("width") ? node.path("width").asInt() : null)
                .wrapText(node.path("wrapText").asBoolean(false))
                .prefix(node.path("prefix").asText(null))
                .adjustHeight(node.path("adjustHeight").asBoolean(false))
                .showInProcessed(node.path("showInProcessed").asBoolean(true))
                .freezeColumn(node.path("freezeColumn").asBoolean(false))
                .freezeTillData(node.path("freezeTillData").asBoolean(false))
                .unFreezeColumnTillData(node.path("unFreezeColumnTillData").asBoolean(false));
        
        // Handle enum properties
        if ("enum".equals(type) && node.has("enum")) {
            List<String> enumValues = new ArrayList<>();
            node.path("enum").forEach(enumNode -> enumValues.add(enumNode.asText()));
            builder.enumValues(enumValues);
        }
        
        // Handle multiSelectDetails for string properties
        if (node.has("multiSelectDetails")) {
            JsonNode multiSelectNode = node.path("multiSelectDetails");
            List<String> enumValues = new ArrayList<>();
            multiSelectNode.path("enum").forEach(enumNode -> enumValues.add(enumNode.asText()));
            
            MultiSelectDetails details = MultiSelectDetails.builder()
                    .maxSelections(multiSelectNode.path("maxSelections").asInt(1))
                    .minSelections(multiSelectNode.path("minSelections").asInt(0))
                    .enumValues(enumValues)
                    .build();
            
            builder.multiSelectDetails(details);
        }
        
        return builder.build();
    }
    
    private List<ColumnDef> expandMultiSelectColumns(List<ColumnDef> columns) {
        List<ColumnDef> expandedColumns = new ArrayList<>();
        
        for (ColumnDef column : columns) {
            if (column.getMultiSelectDetails() != null) {
                MultiSelectDetails details = column.getMultiSelectDetails();
                int maxSelections = details.getMaxSelections();
                
                // Create individual columns for each selection
                for (int i = 1; i <= maxSelections; i++) {
                    ColumnDef multiCol = ColumnDef.builder()
                            .name(column.getName() + "_" + i)
                            .technicalName(column.getName())
                            .type("multiselect_item")
                            .colorHex(column.getColorHex())
                            .orderNumber(column.getOrderNumber())
                            .enumValues(details.getEnumValues())
                            .parentColumn(column.getName())
                            .multiSelectIndex(i)
                            .freezeColumnIfFilled(column.isFreezeColumnIfFilled())
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
                        .build();
                expandedColumns.add(regularCol);
            }
        }
        
        return expandedColumns;
    }
    
    private void applyValidations(Workbook workbook, Sheet sheet, List<ColumnDef> columns) {
        DataValidationHelper dvHelper = sheet.getDataValidationHelper();
        
        for (int colIndex = 0; colIndex < columns.size(); colIndex++) {
            ColumnDef column = columns.get(colIndex);
            
            // Apply enum dropdown validation
            if ("enum".equals(column.getType()) || "multiselect_item".equals(column.getType())) {
                if (column.getEnumValues() != null && !column.getEnumValues().isEmpty()) {
                    String[] enumArray = column.getEnumValues().toArray(new String[0]);
                    DataValidationConstraint constraint = dvHelper.createExplicitListConstraint(enumArray);
                    CellRangeAddressList addressList = new CellRangeAddressList(2, config.getExcelRowLimit(), colIndex, colIndex);
                    DataValidation validation = dvHelper.createValidation(constraint, addressList);
                    validation.setErrorStyle(DataValidation.ErrorStyle.STOP);
                    validation.setShowErrorBox(true);
                    validation.createErrorBox("Invalid Selection", "Please select a value from the dropdown list.");
                    validation.setShowPromptBox(true);
                    validation.createPromptBox("Select Value", "Choose a value from the dropdown list.");
                    sheet.addValidationData(validation);
                }
            }
        }
    }
    
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
                StringBuilder formulaBuilder = new StringBuilder("=IF(AND(");
                for (String colLetter : colLetters) {
                    formulaBuilder.append("ISBLANK(").append(colLetter).append("2),");
                }
                // Remove last comma
                if (colLetters.size() > 0) {
                    formulaBuilder.setLength(formulaBuilder.length() - 1);
                }
                formulaBuilder.append("),\"\",TRIM(CONCATENATE(");
                
                for (String colLetter : colLetters) {
                    formulaBuilder.append("IF(ISBLANK(").append(colLetter).append("2),\"\",")
                                  .append(colLetter).append("2&\",\"),");
                }
                // Remove last comma
                if (colLetters.size() > 0) {
                    formulaBuilder.setLength(formulaBuilder.length() - 1);
                }
                formulaBuilder.append(")))");
                
                String formula = formulaBuilder.toString();
                
                // Apply formula to all rows
                for (int row = 2; row <= config.getExcelRowLimit(); row++) {
                    String rowFormula = formula.replace("2", String.valueOf(row));
                    Row excelRow = sheet.getRow(row);
                    if (excelRow == null) {
                        excelRow = sheet.createRow(row);
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
    
    private void prepareEnhancedCellLocking(Workbook workbook, Sheet sheet, List<ColumnDef> columns) {
        // Use the comprehensive cell protection manager to handle all protection features
        cellProtectionManager.applyCellProtection(workbook, sheet, columns);
    }
    
    /**
     * Find the last row that contains data in any cell
     */
    private int findLastDataRow(Sheet sheet) {
        int lastDataRow = 1; // Start from row 2 (index 1), as rows 0-1 are headers
        
        for (int rowIdx = 2; rowIdx <= sheet.getLastRowNum(); rowIdx++) {
            Row row = sheet.getRow(rowIdx);
            if (row != null) {
                for (Cell cell : row) {
                    if (cellHasValue(cell)) {
                        lastDataRow = rowIdx;
                        break;
                    }
                }
            }
        }
        
        return lastDataRow;
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
    
    private String getColumnLetter(int columnIndex) {
        StringBuilder columnName = new StringBuilder();
        while (columnIndex > 0) {
            columnIndex--; // Make it 0-indexed
            columnName.insert(0, (char) ('A' + columnIndex % 26));
            columnIndex /= 26;
        }
        return columnName.toString();
    }
}
