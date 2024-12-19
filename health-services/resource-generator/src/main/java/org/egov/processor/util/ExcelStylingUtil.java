package org.egov.processor.util;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.*;
import org.springframework.stereotype.Component;

import java.awt.Color;

import static org.egov.processor.config.ServiceConstants.*;

@Component
public class ExcelStylingUtil {

    public void styleCell(Cell cell) {
        Sheet sheet = cell.getSheet();
        Workbook workbook = sheet.getWorkbook();
        XSSFWorkbook xssfWorkbook = (XSSFWorkbook) workbook;

        // Create a cell style
        XSSFCellStyle cellStyle = (XSSFCellStyle) workbook.createCellStyle();

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

        // Adjust the column width
        int columnIndex = cell.getColumnIndex();
        sheet.setColumnWidth(columnIndex, COLUMN_WIDTH * 256); // Width is measured in units of 1/256 of a character
    }

    /**
     * Converts a HEX color string to XSSFColor using the XSSFWorkbook context.
     *
     * @param hexColor The HEX color string (e.g., "93C47D").
     * @param xssfWorkbook The XSSFWorkbook context for styles.
     * @return XSSFColor The corresponding XSSFColor object.
     */
    public static XSSFColor hexToXSSFColor(String hexColor, XSSFWorkbook xssfWorkbook) {
        // Convert HEX to RGB
        int red = Integer.valueOf(hexColor.substring(0, 2), 16);
        int green = Integer.valueOf(hexColor.substring(2, 4), 16);
        int blue = Integer.valueOf(hexColor.substring(4, 6), 16);

        red = (int) (red * 1.1);   // increase red component by 10%
        green = (int) (green * 1.1); // increase green component by 10%
        blue = (int) (blue * 1.1);   // increase blue component by 10%

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
