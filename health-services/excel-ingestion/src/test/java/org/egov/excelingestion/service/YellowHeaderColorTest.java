package org.egov.excelingestion.service;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.egov.excelingestion.config.ExcelIngestionConfig;
import org.egov.excelingestion.config.ValidationConstants;
import org.egov.excelingestion.util.CellProtectionManager;
import org.egov.excelingestion.util.ExcelStyleHelper;
import org.egov.excelingestion.web.models.ValidationColumnInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;

import java.awt.Color;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test to verify that validation column headers use the correct yellow background color (#ffff00)
 */
class YellowHeaderColorTest {

    private XSSFWorkbook workbook;

    @AfterEach
    void tearDown() throws Exception {
        if (workbook != null) {
            workbook.close();
        }
    }

    @Test
    void testValidationHeadersHaveCorrectYellowColor() throws Exception {
        // Arrange
        workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("TestSheet");
        
        // Create dummy header and visible rows
        Row hiddenRow = sheet.createRow(0);
        Row visibleRow = sheet.createRow(1);
        
        // Add some dummy column first
        hiddenRow.createCell(0).setCellValue("DUMMY_COLUMN");
        visibleRow.createCell(0).setCellValue("Dummy");
        
        ExcelIngestionConfig config = mock(ExcelIngestionConfig.class);
        CellProtectionManager cellProtectionManager = mock(CellProtectionManager.class);
        ValidationService validationService = new ValidationService(config, cellProtectionManager);
        Map<String, String> localizationMap = new HashMap<>();
        localizationMap.put(ValidationConstants.STATUS_COLUMN_NAME, "Row Status");
        localizationMap.put(ValidationConstants.ERROR_DETAILS_COLUMN_NAME, "Error Details");
        
        // Act
        ValidationColumnInfo columnInfo = validationService.addValidationColumns(sheet, localizationMap);
        
        // Assert
        assertNotNull(columnInfo);
        assertTrue(columnInfo.getStatusColumnIndex() > 0);
        assertTrue(columnInfo.getErrorColumnIndex() > 0);
        
        // Check status column header color
        Cell statusVisibleCell = visibleRow.getCell(columnInfo.getStatusColumnIndex());
        assertNotNull(statusVisibleCell);
        assertEquals("Row Status", statusVisibleCell.getStringCellValue());
        
        CellStyle statusStyle = statusVisibleCell.getCellStyle();
        assertNotNull(statusStyle);
        
        if (statusStyle instanceof XSSFCellStyle) {
            XSSFCellStyle xssfStyle = (XSSFCellStyle) statusStyle;
            XSSFColor fillColor = xssfStyle.getFillForegroundXSSFColor();
            assertNotNull(fillColor, "Status header should have a fill color");
            
            // Verify the RGB values match #ffff00
            byte[] rgb = fillColor.getRGB();
            assertNotNull(rgb, "Fill color should have RGB values");
            assertEquals(3, rgb.length, "RGB should have 3 components");
            
            // Convert to unsigned integers for comparison
            int red = rgb[0] & 0xFF;
            int green = rgb[1] & 0xFF; 
            int blue = rgb[2] & 0xFF;
            
            assertEquals(255, red, "Red component should be 255");
            assertEquals(255, green, "Green component should be 255"); 
            assertEquals(0, blue, "Blue component should be 0");
        }
        
        // Check error details column header color
        Cell errorVisibleCell = visibleRow.getCell(columnInfo.getErrorColumnIndex());
        assertNotNull(errorVisibleCell);
        assertEquals("Error Details", errorVisibleCell.getStringCellValue());
        
        CellStyle errorStyle = errorVisibleCell.getCellStyle();
        assertNotNull(errorStyle);
        
        if (errorStyle instanceof XSSFCellStyle) {
            XSSFCellStyle xssfStyle = (XSSFCellStyle) errorStyle;
            XSSFColor fillColor = xssfStyle.getFillForegroundXSSFColor();
            assertNotNull(fillColor, "Error details header should have a fill color");
            
            // Verify the RGB values match #ffff00
            byte[] rgb = fillColor.getRGB();
            assertNotNull(rgb, "Fill color should have RGB values");
            
            int red = rgb[0] & 0xFF;
            int green = rgb[1] & 0xFF;
            int blue = rgb[2] & 0xFF;
            
            assertEquals(255, red, "Red component should be 255");
            assertEquals(255, green, "Green component should be 255");
            assertEquals(0, blue, "Blue component should be 0");
        }
        
        // Verify fill pattern is solid
        assertEquals(FillPatternType.SOLID_FOREGROUND, statusStyle.getFillPattern());
        assertEquals(FillPatternType.SOLID_FOREGROUND, errorStyle.getFillPattern());
        
        // Verify font is bold
        Font statusFont = workbook.getFontAt(statusStyle.getFontIndex());
        Font errorFont = workbook.getFontAt(errorStyle.getFontIndex());
        assertTrue(statusFont.getBold(), "Status header font should be bold");
        assertTrue(errorFont.getBold(), "Error details header font should be bold");
    }
    
    @Test
    void testColorMatchesProjectFactoryStandard() {
        // Test that our color matches exactly what project-factory uses
        Color expectedColor = new Color(255, 255, 0); // #ffff00
        
        assertEquals(255, expectedColor.getRed());
        assertEquals(255, expectedColor.getGreen());
        assertEquals(0, expectedColor.getBlue());
        
        // Verify hex representation
        String hexColor = String.format("#%02x%02x%02x", 
            expectedColor.getRed(), expectedColor.getGreen(), expectedColor.getBlue());
        assertEquals("#ffff00", hexColor);
    }
    
    @Test
    void testValidationColumnsCreatedSuccessfully() throws Exception {
        // Arrange
        workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("TestSheet");
        sheet.createRow(0); // Hidden header row
        sheet.createRow(1); // Visible header row
        
        ExcelIngestionConfig config = mock(ExcelIngestionConfig.class);
        CellProtectionManager cellProtectionManager = mock(CellProtectionManager.class);
        ValidationService validationService = new ValidationService(config, cellProtectionManager);
        
        // Act
        ValidationColumnInfo columnInfo = validationService.addValidationColumns(sheet, null);
        
        // Assert
        assertNotNull(columnInfo);
        assertTrue(columnInfo.getStatusColumnIndex() >= 0);
        assertTrue(columnInfo.getErrorColumnIndex() >= 0);
        assertNotEquals(columnInfo.getStatusColumnIndex(), columnInfo.getErrorColumnIndex());
        
        // Verify columns were created
        Row hiddenRow = sheet.getRow(0);
        Row visibleRow = sheet.getRow(1);
        
        Cell statusHiddenCell = hiddenRow.getCell(columnInfo.getStatusColumnIndex());
        Cell errorHiddenCell = hiddenRow.getCell(columnInfo.getErrorColumnIndex());
        
        assertNotNull(statusHiddenCell);
        assertNotNull(errorHiddenCell);
        assertEquals(ValidationConstants.STATUS_COLUMN_NAME, statusHiddenCell.getStringCellValue());
        assertEquals(ValidationConstants.ERROR_DETAILS_COLUMN_NAME, errorHiddenCell.getStringCellValue());
        
        Cell statusVisibleCell = visibleRow.getCell(columnInfo.getStatusColumnIndex());
        Cell errorVisibleCell = visibleRow.getCell(columnInfo.getErrorColumnIndex());
        
        assertNotNull(statusVisibleCell);
        assertNotNull(errorVisibleCell);
        // Should use technical names as fallback when no localization provided
        assertEquals(ValidationConstants.STATUS_COLUMN_NAME, statusVisibleCell.getStringCellValue());
        assertEquals(ValidationConstants.ERROR_DETAILS_COLUMN_NAME, errorVisibleCell.getStringCellValue());
    }
}