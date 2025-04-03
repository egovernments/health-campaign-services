package org.egov.processor.util;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.*;
import org.springframework.stereotype.Component;

import java.awt.Color;

import static org.egov.processor.config.ServiceConstants.*;

@Component
public class ExcelStylingUtil {

    public void styleCell(Cell cell) {
        if(cell == null)
            return;

        Sheet sheet = cell.getSheet();
        Workbook workbook = sheet.getWorkbook();
        XSSFWorkbook xssfWorkbook = (XSSFWorkbook) workbook;

        // Create a cell style
        XSSFCellStyle cellStyle;
        try {
            cellStyle = (XSSFCellStyle) workbook.createCellStyle();
        } catch (Exception e) {
            throw new IllegalStateException(ERROR_WHILE_CREATING_CELL_STYLE, e);
        }

        // Set background color
        XSSFColor backgroundColor = hexToXSSFColor(HEX_BACKGROUND_COLOR, xssfWorkbook);
        cellStyle.setFillForegroundColor(backgroundColor);
        cellStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        // Set font style (bold)
        XSSFFont font = (XSSFFont) workbook.createFont();
        font.setBold(true);
        cellStyle.setFont(font);

        // Set alignment and wrap text
        cellStyle.setAlignment(HorizontalAlignment.LEFT);
        cellStyle.setVerticalAlignment(VerticalAlignment.TOP);
        cellStyle.setWrapText(true);

        // Lock the cell if FREEZE_CELL is true
        if (FREEZE_CELL) {
            cellStyle.setLocked(true);
        }

        // Apply the style to the cell
        cell.setCellStyle(cellStyle);

    }

    /**
     * Adjusts the column width to fit the content of the given cell, adding padding for readability.
     *
     * @param cell the cell whose column width is to be adjusted; does nothing if null.
     */
    public void adjustColumnWidthForCell(Cell cell) {
        if (cell == null) {
            return;
        }

        Sheet sheet = cell.getSheet();
        int columnIndex = cell.getColumnIndex();
        int maxWidth = sheet.getColumnWidth(columnIndex);

        // Calculate the width needed for the current cell content
        String cellValue = cell.toString(); // Convert cell content to string
        int cellWidth = cellValue.length() * 256; // Approximate width (1/256th of character width)

        // Use the maximum width seen so far, including padding for readability
        int padding = COLUMN_PADDING; // Adjust padding as needed
        int newWidth = Math.max(maxWidth, cellWidth + padding);

        sheet.setColumnWidth(columnIndex, newWidth);
    }

    /**
     * Converts a HEX color string to XSSFColor using the XSSFWorkbook context.
     *
     * @param hexColor The HEX color string (e.g., "93C47D").
     * @param xssfWorkbook The XSSFWorkbook context for styles.
     * @return XSSFColor The corresponding XSSFColor object.
     * @throws IllegalArgumentException if the hex color is null, empty, or in invalid format
     */
    public static XSSFColor hexToXSSFColor(String hexColor, XSSFWorkbook xssfWorkbook) {

        if (hexColor == null || hexColor.length() < 6)
            throw new IllegalArgumentException(INVALID_HEX + hexColor);

        // Convert HEX to RGB
        int red = Integer.valueOf(hexColor.substring(0, 2), 16);
        int green = Integer.valueOf(hexColor.substring(2, 4), 16);
        int blue = Integer.valueOf(hexColor.substring(4, 6), 16);

        red = (int) (red * BRIGHTEN_FACTOR);   // increase red component by 10%
        green = (int) (green * BRIGHTEN_FACTOR); // increase green component by 10%
        blue = (int) (blue * BRIGHTEN_FACTOR);   // increase blue component by 10%

        // Clamp the values to be between 0 and 255
        red = Math.min(255, Math.max(0, red));
        green = Math.min(255, Math.max(0, green));
        blue = Math.min(255, Math.max(0, blue));

        // Create IndexedColorMap from the workbook's styles source
        IndexedColorMap colorMap = xssfWorkbook.getStylesSource().getIndexedColors();

        // Create XSSFColor using the XSSFWorkbook context and colorMap
        return new XSSFColor(new Color(red, green, blue), colorMap);
    }
}
