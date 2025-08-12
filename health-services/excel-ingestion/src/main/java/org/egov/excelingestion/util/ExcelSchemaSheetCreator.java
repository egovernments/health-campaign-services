package org.egov.excelingestion.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import java.awt.Color;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class ExcelSchemaSheetCreator {

    public static Workbook addSchemaSheetFromJson(String json, String sheetName, Workbook workbook) throws IOException {
        return addSchemaSheetFromJson(json, sheetName, workbook, null);
    }
    
    public static Workbook addSchemaSheetFromJson(String json, String sheetName, Workbook workbook, Map<String, String> localizationMap) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(json);

        List<ColumnDef> columns = new ArrayList<>();

        // Merge stringProperties & numberProperties
        StreamSupport.stream(root.path("stringProperties").spliterator(), false)
                .map(ExcelSchemaSheetCreator::parseColumnDef)
                .forEach(columns::add);

        StreamSupport.stream(root.path("numberProperties").spliterator(), false)
                .map(ExcelSchemaSheetCreator::parseColumnDef)
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
    
    public static Workbook addEnhancedSchemaSheetFromJson(String json, String sheetName, Workbook workbook, Map<String, String> localizationMap) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(json);

        List<EnhancedColumnDef> columns = new ArrayList<>();

        // Process stringProperties 
        StreamSupport.stream(root.path("stringProperties").spliterator(), false)
                .map(node -> parseEnhancedColumnDef(node, "string"))
                .forEach(columns::add);

        // Process numberProperties
        StreamSupport.stream(root.path("numberProperties").spliterator(), false)
                .map(node -> parseEnhancedColumnDef(node, "number"))
                .forEach(columns::add);

        // Process enumProperties (new support)
        StreamSupport.stream(root.path("enumProperties").spliterator(), false)
                .map(node -> parseEnhancedColumnDef(node, "enum"))
                .forEach(columns::add);

        // Sort by orderNumber, stable for ties
        columns.sort(Comparator.comparingInt(EnhancedColumnDef::getOrderNumber));

        // Expand multiselect columns
        List<EnhancedColumnDef> expandedColumns = expandMultiSelectColumns(columns);

        Sheet sheet = workbook.createSheet(sheetName);

        // Create Row 1 (hidden technical names) and Row 2 (localized visible headers)
        Row hiddenRow = sheet.createRow(0);
        Row visibleRow = sheet.createRow(1);

        int colIndex = 0;
        for (EnhancedColumnDef def : expandedColumns) {
            // Row 1: technical name
            Cell techCell = hiddenRow.createCell(colIndex);
            techCell.setCellValue(def.getTechnicalName());

            // Row 2: localized display name
            Cell headerCell = visibleRow.createCell(colIndex);
            String displayName = localizationMap != null && localizationMap.containsKey(def.getDisplayName()) 
                ? localizationMap.get(def.getDisplayName()) 
                : def.getDisplayName();
            headerCell.setCellValue(displayName);
            headerCell.setCellStyle(createColoredHeaderStyle(workbook, def.getColorHex()));

            // Hide column if specified
            if (def.isHideColumn()) {
                sheet.setColumnHidden(colIndex, true);
            }

            // Autosize
            sheet.autoSizeColumn(colIndex);
            colIndex++;
        }

        // Hide first row
        sheet.createFreezePane(0, 2);
        hiddenRow.setZeroHeight(true);

        // Apply validations
        applyValidations(workbook, sheet, expandedColumns);

        // Apply multiselect formulas
        applyMultiSelectFormulas(sheet, expandedColumns);

        // Prepare cell locking and apply sheet protection (same as facility sheet)
        prepareEnhancedCellLocking(workbook, sheet, expandedColumns);
        
        // Protect the User sheet with password (same as facility sheet)
        sheet.protectSheet("passwordhere");

        return workbook;
    }

    private static ColumnDef parseColumnDef(JsonNode node) {
        ColumnDef def = new ColumnDef();
        def.setName(node.path("name").asText());
        def.setDescription(node.path("description").asText());
        def.setColorHex(node.path("color").asText());
        def.setOrderNumber(node.path("orderNumber").asInt(9999));
        def.setFreezeColumnIfFilled(node.path("freezeColumnIfFilled").asBoolean(false));
        return def;
    }

    private static CellStyle createColoredHeaderStyle(Workbook workbook, String colorHex) {
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

    private static void prepareCellLocking(Workbook workbook, Sheet sheet, List<ColumnDef> columns) {
        // First, unlock all cells by default
        CellStyle unlockedStyle = workbook.createCellStyle();
        unlockedStyle.setLocked(false);
        
        // Apply unlocked style to all data cells in schema columns
        for (int rowIdx = 2; rowIdx <= Math.max(sheet.getLastRowNum(), 5000); rowIdx++) {
            Row row = sheet.getRow(rowIdx);
            if (row == null) {
                row = sheet.createRow(rowIdx);
            }
            for (int col = 0; col < columns.size(); col++) {
                Cell cell = row.getCell(col);
                if (cell == null) {
                    cell = row.createCell(col);
                }
                cell.setCellStyle(unlockedStyle);
            }
        }
        
        // Now lock only filled cells for columns with freezeColumnIfFilled=true
        for (int col = 0; col < columns.size(); col++) {
            if (columns.get(col).isFreezeColumnIfFilled()) {
                for (int rowIdx = 2; rowIdx <= sheet.getLastRowNum(); rowIdx++) {
                    Row row = sheet.getRow(rowIdx);
                    if (row != null) {
                        Cell cell = row.getCell(col);
                        if (cell != null && cell.getCellType() != CellType.BLANK && !cell.toString().trim().isEmpty()) {
                            CellStyle lockedStyle = workbook.createCellStyle();
                            lockedStyle.setLocked(true);
                            cell.setCellStyle(lockedStyle);
                        }
                    }
                }
            }
        }
        
        // Don't protect sheet here - let the caller handle protection after all columns are added
    }

    // Enhanced parsing method for new schema types
    private static EnhancedColumnDef parseEnhancedColumnDef(JsonNode node, String type) {
        EnhancedColumnDef def = new EnhancedColumnDef();
        def.setName(node.path("name").asText());
        def.setType(type);
        def.setDescription(node.path("description").asText());
        def.setColorHex(node.path("color").asText());
        def.setOrderNumber(node.path("orderNumber").asInt(9999));
        def.setFreezeColumnIfFilled(node.path("freezeColumnIfFilled").asBoolean(false));
        def.setHideColumn(node.path("hideColumn").asBoolean(false));
        def.setRequired(node.path("isRequired").asBoolean(false));
        
        // Handle enum properties
        if ("enum".equals(type) && node.has("enum")) {
            List<String> enumValues = new ArrayList<>();
            node.path("enum").forEach(enumNode -> enumValues.add(enumNode.asText()));
            def.setEnumValues(enumValues);
        }
        
        // Handle multiSelectDetails for string properties
        if (node.has("multiSelectDetails")) {
            JsonNode multiSelectNode = node.path("multiSelectDetails");
            MultiSelectDetails details = new MultiSelectDetails();
            details.setMaxSelections(multiSelectNode.path("maxSelections").asInt(1));
            details.setMinSelections(multiSelectNode.path("minSelections").asInt(0));
            
            List<String> enumValues = new ArrayList<>();
            multiSelectNode.path("enum").forEach(enumNode -> enumValues.add(enumNode.asText()));
            details.setEnumValues(enumValues);
            
            def.setMultiSelectDetails(details);
        }
        
        return def;
    }
    
    private static List<EnhancedColumnDef> expandMultiSelectColumns(List<EnhancedColumnDef> columns) {
        List<EnhancedColumnDef> expandedColumns = new ArrayList<>();
        
        for (EnhancedColumnDef column : columns) {
            if (column.getMultiSelectDetails() != null) {
                MultiSelectDetails details = column.getMultiSelectDetails();
                int maxSelections = details.getMaxSelections();
                
                // Create individual columns for each selection
                for (int i = 1; i <= maxSelections; i++) {
                    EnhancedColumnDef multiCol = new EnhancedColumnDef();
                    multiCol.setName(column.getName());
                    multiCol.setTechnicalName(column.getName() + "_" + i);
                    multiCol.setDisplayName(column.getName() + " " + i);
                    multiCol.setType("multiselect_item");
                    multiCol.setColorHex(column.getColorHex());
                    multiCol.setOrderNumber(column.getOrderNumber());
                    multiCol.setEnumValues(details.getEnumValues());
                    multiCol.setParentColumn(column.getName());
                    multiCol.setMultiSelectIndex(i);
                    multiCol.setFreezeColumnIfFilled(column.isFreezeColumnIfFilled());
                    expandedColumns.add(multiCol);
                }
                
                // Add the hidden concatenated column
                EnhancedColumnDef hiddenCol = new EnhancedColumnDef();
                hiddenCol.setName(column.getName());
                hiddenCol.setTechnicalName(column.getName());
                hiddenCol.setDisplayName(column.getName());
                hiddenCol.setType("multiselect_hidden");
                hiddenCol.setColorHex(column.getColorHex());
                hiddenCol.setOrderNumber(column.getOrderNumber());
                hiddenCol.setHideColumn(column.isHideColumn());
                hiddenCol.setParentColumn(column.getName());
                hiddenCol.setMultiSelectMaxSelections(maxSelections);
                hiddenCol.setFreezeColumnIfFilled(column.isFreezeColumnIfFilled());
                expandedColumns.add(hiddenCol);
            } else {
                // Regular column (string, number, enum)
                column.setTechnicalName(column.getName());
                column.setDisplayName(column.getName());
                expandedColumns.add(column);
            }
        }
        
        return expandedColumns;
    }
    
    private static void applyValidations(Workbook workbook, Sheet sheet, List<EnhancedColumnDef> columns) {
        DataValidationHelper dvHelper = sheet.getDataValidationHelper();
        
        for (int colIndex = 0; colIndex < columns.size(); colIndex++) {
            EnhancedColumnDef column = columns.get(colIndex);
            
            // Apply enum dropdown validation
            if ("enum".equals(column.getType()) || "multiselect_item".equals(column.getType())) {
                if (column.getEnumValues() != null && !column.getEnumValues().isEmpty()) {
                    String[] enumArray = column.getEnumValues().toArray(new String[0]);
                    DataValidationConstraint constraint = dvHelper.createExplicitListConstraint(enumArray);
                    CellRangeAddressList addressList = new CellRangeAddressList(2, 5000, colIndex, colIndex);
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
    
    private static void applyMultiSelectFormulas(Sheet sheet, List<EnhancedColumnDef> columns) {
        Map<String, List<Integer>> multiSelectGroups = new HashMap<>();
        Map<String, Integer> hiddenColumnIndexes = new HashMap<>();
        
        // Group columns by parent and find hidden column indexes
        for (int i = 0; i < columns.size(); i++) {
            EnhancedColumnDef column = columns.get(i);
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
                for (int row = 2; row <= 5000; row++) {
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
    
    private static void prepareEnhancedCellLocking(Workbook workbook, Sheet sheet, List<EnhancedColumnDef> columns) {
        // First, unlock all cells by default
        CellStyle unlockedStyle = workbook.createCellStyle();
        unlockedStyle.setLocked(false);
        
        // Apply unlocked style to all data cells
        for (int rowIdx = 2; rowIdx <= Math.max(sheet.getLastRowNum(), 5000); rowIdx++) {
            Row row = sheet.getRow(rowIdx);
            if (row == null) {
                row = sheet.createRow(rowIdx);
            }
            for (int col = 0; col < columns.size(); col++) {
                Cell cell = row.getCell(col);
                if (cell == null) {
                    cell = row.createCell(col);
                }
                cell.setCellStyle(unlockedStyle);
            }
        }
    }
    
    private static String getColumnLetter(int columnIndex) {
        StringBuilder columnName = new StringBuilder();
        while (columnIndex > 0) {
            columnIndex--; // Make it 0-indexed
            columnName.insert(0, (char) ('A' + columnIndex % 26));
            columnIndex /= 26;
        }
        return columnName.toString();
    }

    private static class ColumnDef {
        private String name;
        private String description;
        private String colorHex;
        private int orderNumber;
        private boolean freezeColumnIfFilled;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getColorHex() {
            return colorHex;
        }

        public void setColorHex(String colorHex) {
            this.colorHex = colorHex;
        }

        public int getOrderNumber() {
            return orderNumber;
        }

        public void setOrderNumber(int orderNumber) {
            this.orderNumber = orderNumber;
        }

        public boolean isFreezeColumnIfFilled() {
            return freezeColumnIfFilled;
        }

        public void setFreezeColumnIfFilled(boolean freezeColumnIfFilled) {
            this.freezeColumnIfFilled = freezeColumnIfFilled;
        }
    }
    
    private static class EnhancedColumnDef {
        private String name;
        private String technicalName;
        private String displayName;
        private String type;
        private String description;
        private String colorHex;
        private int orderNumber;
        private boolean freezeColumnIfFilled;
        private boolean hideColumn;
        private boolean required;
        private List<String> enumValues;
        private MultiSelectDetails multiSelectDetails;
        private String parentColumn;
        private int multiSelectIndex;
        private int multiSelectMaxSelections;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getTechnicalName() {
            return technicalName;
        }

        public void setTechnicalName(String technicalName) {
            this.technicalName = technicalName;
        }

        public String getDisplayName() {
            return displayName;
        }

        public void setDisplayName(String displayName) {
            this.displayName = displayName;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getColorHex() {
            return colorHex;
        }

        public void setColorHex(String colorHex) {
            this.colorHex = colorHex;
        }

        public int getOrderNumber() {
            return orderNumber;
        }

        public void setOrderNumber(int orderNumber) {
            this.orderNumber = orderNumber;
        }

        public boolean isFreezeColumnIfFilled() {
            return freezeColumnIfFilled;
        }

        public void setFreezeColumnIfFilled(boolean freezeColumnIfFilled) {
            this.freezeColumnIfFilled = freezeColumnIfFilled;
        }

        public boolean isHideColumn() {
            return hideColumn;
        }

        public void setHideColumn(boolean hideColumn) {
            this.hideColumn = hideColumn;
        }

        public boolean isRequired() {
            return required;
        }

        public void setRequired(boolean required) {
            this.required = required;
        }

        public List<String> getEnumValues() {
            return enumValues;
        }

        public void setEnumValues(List<String> enumValues) {
            this.enumValues = enumValues;
        }

        public MultiSelectDetails getMultiSelectDetails() {
            return multiSelectDetails;
        }

        public void setMultiSelectDetails(MultiSelectDetails multiSelectDetails) {
            this.multiSelectDetails = multiSelectDetails;
        }

        public String getParentColumn() {
            return parentColumn;
        }

        public void setParentColumn(String parentColumn) {
            this.parentColumn = parentColumn;
        }

        public int getMultiSelectIndex() {
            return multiSelectIndex;
        }

        public void setMultiSelectIndex(int multiSelectIndex) {
            this.multiSelectIndex = multiSelectIndex;
        }

        public int getMultiSelectMaxSelections() {
            return multiSelectMaxSelections;
        }

        public void setMultiSelectMaxSelections(int multiSelectMaxSelections) {
            this.multiSelectMaxSelections = multiSelectMaxSelections;
        }
    }
    
    private static class MultiSelectDetails {
        private int maxSelections;
        private int minSelections;
        private List<String> enumValues;

        public int getMaxSelections() {
            return maxSelections;
        }

        public void setMaxSelections(int maxSelections) {
            this.maxSelections = maxSelections;
        }

        public int getMinSelections() {
            return minSelections;
        }

        public void setMinSelections(int minSelections) {
            this.minSelections = minSelections;
        }

        public List<String> getEnumValues() {
            return enumValues;
        }

        public void setEnumValues(List<String> enumValues) {
            this.enumValues = enumValues;
        }
    }
}
