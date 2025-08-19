package org.egov.excelingestion.util;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.springframework.stereotype.Component;

import java.awt.Color;

/**
 * Utility class for creating Excel cell styles and formatting
 */
@Component
public class ExcelStyleHelper {

    /**
     * Creates a header style with background color, borders, center alignment, and bold font
     *
     * @param workbook The workbook to create style for
     * @param colorHex The background color in hex format (e.g., "#FF0000")
     * @return CellStyle with header formatting
     */
    public CellStyle createHeaderStyle(Workbook workbook, String colorHex) {
        XSSFCellStyle style = (XSSFCellStyle) workbook.createCellStyle();
        
        // Set background color
        Color color = Color.decode(colorHex);
        XSSFColor xssfColor = new XSSFColor(color, null);
        style.setFillForegroundColor(xssfColor);
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        
        // Set borders
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        
        // Center align text
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        
        // Bold font
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        
        // Lock the cell
        style.setLocked(true);
        
        return style;
    }

}