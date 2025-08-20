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

    /**
     * Creates a header style with background color, borders, left alignment, and bold font
     * This style matches the alignment of schema column headers
     *
     * @param workbook The workbook to create style for
     * @param colorHex The background color in hex format (e.g., "#FF0000")
     * @return CellStyle with header formatting and left alignment
     */
    public CellStyle createLeftAlignedHeaderStyle(Workbook workbook, String colorHex) {
        XSSFCellStyle style = (XSSFCellStyle) workbook.createCellStyle();
        
        // Set background color
        Color color = Color.decode(colorHex);
        XSSFColor xssfColor = new XSSFColor(color, null);
        style.setFillForegroundColor(xssfColor);
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        
        
        // Left align text (matching schema columns)
        style.setAlignment(HorizontalAlignment.LEFT);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        
        // Bold font
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        
        // Lock the cell
        style.setLocked(true);
        
        return style;
    }

    /**
     * Creates a data cell style with text wrapping enabled
     *
     * @param workbook The workbook to create style for
     * @param wrapText Whether to wrap text in the cell
     * @return CellStyle with text wrapping settings
     */
    public CellStyle createDataCellStyle(Workbook workbook, boolean wrapText) {
        CellStyle style = workbook.createCellStyle();
        
        
        // Set text wrapping
        style.setWrapText(wrapText);
        
        // Default unlocked for data cells
        style.setLocked(false);
        
        return style;
    }

    /**
     * Creates a header style with custom options for color, text wrapping, and alignment
     *
     * @param workbook The workbook to create style for
     * @param colorHex The background color in hex format (e.g., "#FF0000")
     * @param wrapText Whether to wrap text in the header
     * @return CellStyle with customized header formatting
     */
    public CellStyle createCustomHeaderStyle(Workbook workbook, String colorHex, boolean wrapText) {
        XSSFCellStyle style = (XSSFCellStyle) workbook.createCellStyle();
        
        // Set background color if provided
        if (colorHex != null && !colorHex.isEmpty()) {
            try {
                Color color = Color.decode(colorHex);
                XSSFColor xssfColor = new XSSFColor(color, null);
                style.setFillForegroundColor(xssfColor);
                style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            } catch (Exception e) {
                // If color parsing fails, continue without color
            }
        }
        
        
        // Set alignment
        style.setAlignment(HorizontalAlignment.LEFT);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        
        // Bold font
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        
        // Set text wrapping
        style.setWrapText(wrapText);
        
        // Lock the header cell
        style.setLocked(true);
        
        return style;
    }

    /**
     * Creates a locked cell style for frozen columns
     *
     * @param workbook The workbook to create style for
     * @return CellStyle with cell locked
     */
    public CellStyle createLockedCellStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        
        
        // Lock the cell
        style.setLocked(true);
        
        return style;
    }

    /**
     * Creates an unlocked cell style for editable columns
     *
     * @param workbook The workbook to create style for
     * @return CellStyle with cell unlocked
     */
    public CellStyle createUnlockedCellStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        
        
        // Unlock the cell
        style.setLocked(false);
        
        return style;
    }

}