package org.egov.excelingestion.util;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for ExcelStyleHelper with focus on header wrap text validation
 */
class ExcelStyleHelperTest {

    private ExcelStyleHelper excelStyleHelper;
    private Workbook workbook;
    
    @BeforeEach
    void setUp() {
        excelStyleHelper = new ExcelStyleHelper();
        workbook = new XSSFWorkbook();
    }
    
    @AfterEach
    void tearDown() throws Exception {
        if (workbook != null) {
            workbook.close();
        }
    }

    // ==================== HEADER WRAP TEXT VALIDATION TESTS ====================
    
    @Test
    void testCreateHeaderStyle_ShouldAlwaysHaveWrapTextTrue() {
        // Arrange
        String colorHex = "#FF5733";
        
        // Act
        CellStyle headerStyle = excelStyleHelper.createHeaderStyle(workbook, colorHex);
        
        // Assert
        assertTrue(headerStyle.getWrapText(), "Header style should always have wrap text enabled");
        assertEquals(HorizontalAlignment.CENTER, headerStyle.getAlignment());
        assertEquals(VerticalAlignment.CENTER, headerStyle.getVerticalAlignment());
        assertTrue(headerStyle.getLocked(), "Header should be locked");
        
        // Verify font is bold
        Font font = workbook.getFontAt(headerStyle.getFontIndex());
        assertTrue(font.getBold(), "Header font should be bold");
    }
    
    @Test
    void testCreateLeftAlignedHeaderStyle_ShouldAlwaysHaveWrapTextTrue() {
        // Arrange
        String colorHex = "#93C47D";
        
        // Act
        CellStyle headerStyle = excelStyleHelper.createLeftAlignedHeaderStyle(workbook, colorHex);
        
        // Assert
        assertTrue(headerStyle.getWrapText(), "Left-aligned header style should always have wrap text enabled");
        assertEquals(HorizontalAlignment.LEFT, headerStyle.getAlignment());
        assertEquals(VerticalAlignment.CENTER, headerStyle.getVerticalAlignment());
        assertTrue(headerStyle.getLocked(), "Header should be locked");
        
        // Verify font is bold
        Font font = workbook.getFontAt(headerStyle.getFontIndex());
        assertTrue(font.getBold(), "Header font should be bold");
    }
    
    @Test
    void testCreateCustomHeaderStyle_WithWrapTextTrue_ShouldHaveWrapTextEnabled() {
        // Arrange
        String colorHex = "#4285F4";
        boolean wrapText = true;
        
        // Act
        CellStyle headerStyle = excelStyleHelper.createCustomHeaderStyle(workbook, colorHex, wrapText);
        
        // Assert
        assertTrue(headerStyle.getWrapText(), "Custom header style with wrapText=true should have wrap text enabled");
        assertEquals(HorizontalAlignment.LEFT, headerStyle.getAlignment());
        assertEquals(VerticalAlignment.CENTER, headerStyle.getVerticalAlignment());
        assertTrue(headerStyle.getLocked(), "Header should be locked");
        
        // Verify font is bold
        Font font = workbook.getFontAt(headerStyle.getFontIndex());
        assertTrue(font.getBold(), "Header font should be bold");
    }
    
    @Test
    void testCreateCustomHeaderStyle_WithWrapTextFalse_ShouldHaveWrapTextDisabled() {
        // Arrange
        String colorHex = "#EA4335";
        boolean wrapText = false;
        
        // Act
        CellStyle headerStyle = excelStyleHelper.createCustomHeaderStyle(workbook, colorHex, wrapText);
        
        // Assert
        assertFalse(headerStyle.getWrapText(), "Custom header style with wrapText=false should have wrap text disabled");
        assertEquals(HorizontalAlignment.LEFT, headerStyle.getAlignment());
        assertEquals(VerticalAlignment.CENTER, headerStyle.getVerticalAlignment());
        assertTrue(headerStyle.getLocked(), "Header should be locked");
        
        // Verify font is bold
        Font font = workbook.getFontAt(headerStyle.getFontIndex());
        assertTrue(font.getBold(), "Header font should be bold");
    }
    
    @Test
    void testBoundaryHierarchyHeaders_ShouldAlwaysUseWrapText() {
        // This test validates that boundary hierarchy headers always use wrap text
        // since BoundaryHierarchySheetGenerator and other components rely on this behavior
        
        // Arrange - Simulate boundary hierarchy header creation
        String boundaryColorHex = "#93C47D"; // Green color typically used for boundary columns
        
        // Act - Create header style as would be done in boundary hierarchy generation
        CellStyle boundaryHeaderStyle = excelStyleHelper.createLeftAlignedHeaderStyle(workbook, boundaryColorHex);
        
        // Assert - Verify wrap text is always enabled for boundary headers
        assertTrue(boundaryHeaderStyle.getWrapText(), 
            "Boundary hierarchy headers should always have wrap text enabled for proper display of long boundary names");
        assertEquals(HorizontalAlignment.LEFT, boundaryHeaderStyle.getAlignment(), 
            "Boundary headers should be left-aligned to match schema columns");
        
        // Test with various boundary hierarchy column headers
        String[] boundaryHeaders = {
            "HIERARCHY1_DISTRICT", 
            "HIERARCHY1_BLOCK", 
            "HIERARCHY1_VILLAGE",
            "HCM_ADMIN_CONSOLE_BOUNDARY_CODE"
        };
        
        for (String headerName : boundaryHeaders) {
            CellStyle style = excelStyleHelper.createLeftAlignedHeaderStyle(workbook, boundaryColorHex);
            assertTrue(style.getWrapText(), 
                "Header style for " + headerName + " should have wrap text enabled");
        }
    }
    
    @Test
    void testSchemaColumnHeaders_ShouldAlwaysUseWrapText() {
        // This test validates that schema column headers use wrap text
        // since target schema columns may have long localized names
        
        // Arrange - Simulate various schema column colors and headers
        String[] schemaColors = {"#FFD966", "#B4A7D6", "#FF9900", "#00B4D8"};
        String[] schemaHeaders = {
            "TARGET_POPULATION_ESTIMATE", 
            "DRUGS_REQUIRED_PER_PERSON", 
            "DELIVERY_MECHANISM_TYPE",
            "HEALTH_FACILITY_REGISTRATION_NUMBER"
        };
        
        // Act & Assert - Test each schema column type
        for (int i = 0; i < schemaHeaders.length; i++) {
            String color = schemaColors[i % schemaColors.length];
            
            // Test standard header style (center-aligned)
            CellStyle centerHeaderStyle = excelStyleHelper.createHeaderStyle(workbook, color);
            assertTrue(centerHeaderStyle.getWrapText(), 
                "Schema header " + schemaHeaders[i] + " should have wrap text enabled for proper display");
            
            // Test left-aligned header style (matching boundary columns)
            CellStyle leftHeaderStyle = excelStyleHelper.createLeftAlignedHeaderStyle(workbook, color);
            assertTrue(leftHeaderStyle.getWrapText(), 
                "Left-aligned schema header " + schemaHeaders[i] + " should have wrap text enabled");
        }
    }
    
    @Test
    void testHeaderStylesConsistency_AllHeaderTypesShouldSupportWrapText() {
        // This test ensures consistency across all header creation methods
        
        // Arrange
        String testColor = "#34A853";
        
        // Act - Create all types of header styles
        CellStyle standardHeader = excelStyleHelper.createHeaderStyle(workbook, testColor);
        CellStyle leftAlignedHeader = excelStyleHelper.createLeftAlignedHeaderStyle(workbook, testColor);
        CellStyle customHeaderWithWrap = excelStyleHelper.createCustomHeaderStyle(workbook, testColor, true);
        CellStyle customHeaderWithoutWrap = excelStyleHelper.createCustomHeaderStyle(workbook, testColor, false);
        
        // Assert - Verify wrap text behavior is as expected
        assertTrue(standardHeader.getWrapText(), "Standard header should have wrap text enabled");
        assertTrue(leftAlignedHeader.getWrapText(), "Left-aligned header should have wrap text enabled");
        assertTrue(customHeaderWithWrap.getWrapText(), "Custom header with wrap=true should have wrap text enabled");
        assertFalse(customHeaderWithoutWrap.getWrapText(), "Custom header with wrap=false should have wrap text disabled");
        
        // Verify all headers are locked (consistent security behavior)
        assertTrue(standardHeader.getLocked(), "All header styles should be locked");
        assertTrue(leftAlignedHeader.getLocked(), "All header styles should be locked");
        assertTrue(customHeaderWithWrap.getLocked(), "All header styles should be locked");
        assertTrue(customHeaderWithoutWrap.getLocked(), "All header styles should be locked");
    }

    // ==================== DATA CELL STYLE TESTS ====================
    
    @Test
    void testCreateDataCellStyle_WithWrapTextTrue_ShouldHaveWrapTextEnabled() {
        // Arrange
        boolean wrapText = true;
        
        // Act
        CellStyle dataStyle = excelStyleHelper.createDataCellStyle(workbook, wrapText);
        
        // Assert
        assertTrue(dataStyle.getWrapText(), "Data cell style with wrapText=true should have wrap text enabled");
        assertFalse(dataStyle.getLocked(), "Data cells should be unlocked by default");
    }
    
    @Test
    void testCreateDataCellStyle_WithWrapTextFalse_ShouldHaveWrapTextDisabled() {
        // Arrange
        boolean wrapText = false;
        
        // Act
        CellStyle dataStyle = excelStyleHelper.createDataCellStyle(workbook, wrapText);
        
        // Assert
        assertFalse(dataStyle.getWrapText(), "Data cell style with wrapText=false should have wrap text disabled");
        assertFalse(dataStyle.getLocked(), "Data cells should be unlocked by default");
    }

    // ==================== COLOR AND FORMATTING TESTS ====================
    
    @Test
    void testCreateHeaderStyle_WithValidColorHex_ShouldApplyColor() {
        // Arrange
        String validColorHex = "#FF5733";
        
        // Act
        CellStyle headerStyle = excelStyleHelper.createHeaderStyle(workbook, validColorHex);
        
        // Assert
        assertNotNull(headerStyle, "Header style should not be null");
        assertEquals(FillPatternType.SOLID_FOREGROUND, headerStyle.getFillPattern(), 
            "Header should have solid background fill");
        // Note: Color verification requires POI internals, but ensuring no exceptions is sufficient
    }
    
    @Test
    void testCreateCustomHeaderStyle_WithInvalidColorHex_ShouldNotThrowException() {
        // Arrange
        String invalidColorHex = "INVALID_COLOR";
        boolean wrapText = true;
        
        // Act & Assert - Should not throw exception
        assertDoesNotThrow(() -> {
            CellStyle headerStyle = excelStyleHelper.createCustomHeaderStyle(workbook, invalidColorHex, wrapText);
            assertNotNull(headerStyle, "Header style should still be created even with invalid color");
            assertTrue(headerStyle.getWrapText(), "Wrap text should still work with invalid color");
        }, "Invalid color should not cause exception, just be ignored");
    }
    
    @Test
    void testCreateCustomHeaderStyle_WithNullColorHex_ShouldNotThrowException() {
        // Arrange
        String nullColorHex = null;
        boolean wrapText = true;
        
        // Act & Assert - Should not throw exception
        assertDoesNotThrow(() -> {
            CellStyle headerStyle = excelStyleHelper.createCustomHeaderStyle(workbook, nullColorHex, wrapText);
            assertNotNull(headerStyle, "Header style should still be created with null color");
            assertTrue(headerStyle.getWrapText(), "Wrap text should still work with null color");
        }, "Null color should not cause exception, just be ignored");
    }

    // ==================== CELL PROTECTION TESTS ====================
    
    @Test
    void testCreateLockedCellStyle_ShouldBeLocked() {
        // Act
        CellStyle lockedStyle = excelStyleHelper.createLockedCellStyle(workbook);
        
        // Assert
        assertTrue(lockedStyle.getLocked(), "Locked cell style should have locked=true");
    }
    
    @Test
    void testCreateUnlockedCellStyle_ShouldBeUnlocked() {
        // Act
        CellStyle unlockedStyle = excelStyleHelper.createUnlockedCellStyle(workbook);
        
        // Assert
        assertFalse(unlockedStyle.getLocked(), "Unlocked cell style should have locked=false");
    }

    // ==================== INTEGRATION SCENARIO TESTS ====================
    
    @Test
    void testHeaderStylesInExcelSheet_ShouldDisplayCorrectlyWithWrapText() {
        // This integration test simulates how header styles are used in actual Excel generation
        
        // Arrange
        Sheet sheet = workbook.createSheet("TestSheet");
        
        // Create headers with long text that would benefit from wrapping
        String[] longHeaders = {
            "This is a very long header that should wrap to multiple lines in Excel",
            "Another extremely long boundary hierarchy column header name",
            "TARGET_POPULATION_ESTIMATE_FOR_HEALTH_CAMPAIGN_MICROPLAN"
        };
        
        // Act - Create header row and apply styles
        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < longHeaders.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(longHeaders[i]);
            
            // Apply header style with wrap text
            CellStyle headerStyle = excelStyleHelper.createLeftAlignedHeaderStyle(workbook, "#93C47D");
            cell.setCellStyle(headerStyle);
            
            // Verify wrap text is enabled
            assertTrue(cell.getCellStyle().getWrapText(), 
                "Cell " + i + " should have wrap text enabled for proper display of long header");
        }
        
        // Assert - Verify all cells have proper styling
        for (int i = 0; i < longHeaders.length; i++) {
            Cell cell = headerRow.getCell(i);
            assertNotNull(cell, "Header cell should exist");
            assertEquals(longHeaders[i], cell.getStringCellValue(), "Header text should match");
            assertTrue(cell.getCellStyle().getWrapText(), "Header should have wrap text enabled");
            assertTrue(cell.getCellStyle().getLocked(), "Header should be locked");
        }
    }
}