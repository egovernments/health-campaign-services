package org.egov.excelingestion.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.ss.usermodel.*;
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
}
