package org.egov.excelingestion.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test cases for Excel processing with skipped rows - testing that actual row numbers
 * are correctly preserved in the convertSheetToMapList method
 */
@Slf4j  
class SkippedRowsExcelProcessingTest {

    private ExcelProcessingService excelProcessingService;
    
    @BeforeEach
    void setUp() {
        excelProcessingService = new ExcelProcessingService(
            null, null, null, null, null, null, null, null, null, null, null);
    }

    @Test
    void testConvertSheetToMapListWithSkippedRows() throws Exception {
        log.info("🧪 Testing convertSheetToMapList preserves actual Excel row numbers with skipped rows");
        
        // Create Excel workbook with skipped rows
        Workbook workbook = createTestWorkbookWithSkippedRows();
        Sheet sheet = workbook.getSheetAt(0);
        
        // Use reflection to access private method
        Method convertMethod = ExcelProcessingService.class.getDeclaredMethod("convertSheetToMapList", Sheet.class);
        convertMethod.setAccessible(true);
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> result = (List<Map<String, Object>>) convertMethod.invoke(excelProcessingService, sheet);
        
        // Should have 3 rows of data (rows 3, 6, 8 from Excel)
        assertEquals(3, result.size(), "Should extract 3 data rows");
        
        // Verify actual row numbers are preserved
        Map<String, Object> firstRow = result.get(0);  // Excel row 3
        Map<String, Object> secondRow = result.get(1); // Excel row 6
        Map<String, Object> thirdRow = result.get(2);  // Excel row 8
        
        assertEquals(3, firstRow.get("__actualRowNumber__"), "First data row should be Excel row 3");
        assertEquals(6, secondRow.get("__actualRowNumber__"), "Second data row should be Excel row 6");
        assertEquals(8, thirdRow.get("__actualRowNumber__"), "Third data row should be Excel row 8");
        
        // Verify data content
        assertEquals("John", firstRow.get("name"), "First row should contain 'John'");
        assertEquals("Jane", secondRow.get("name"), "Second row should contain 'Jane'");
        assertEquals("Bob", thirdRow.get("name"), "Third row should contain 'Bob'");
        
        assertEquals(25.0, firstRow.get("age"), "First row age should be 25");
        assertEquals(30.0, secondRow.get("age"), "Second row age should be 30");
        assertEquals(35.0, thirdRow.get("age"), "Third row age should be 35");
        
        workbook.close();
        
        log.info("✅ convertSheetToMapList correctly preserves Excel row numbers: 3, 6, 8");
    }

    @Test
    void testConvertSheetWithLargeRowGaps() throws Exception {
        log.info("🧪 Testing convertSheetToMapList with large gaps between rows");
        
        // Create Excel workbook with large gaps (rows 5, 15, 25)
        Workbook workbook = createTestWorkbookWithLargeGaps();
        Sheet sheet = workbook.getSheetAt(0);
        
        // Use reflection to access private method
        Method convertMethod = ExcelProcessingService.class.getDeclaredMethod("convertSheetToMapList", Sheet.class);
        convertMethod.setAccessible(true);
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> result = (List<Map<String, Object>>) convertMethod.invoke(excelProcessingService, sheet);
        
        // Should have 3 rows of data
        assertEquals(3, result.size(), "Should extract 3 data rows despite large gaps");
        
        // Verify actual row numbers
        assertEquals(5, result.get(0).get("__actualRowNumber__"), "First data row should be Excel row 5");
        assertEquals(15, result.get(1).get("__actualRowNumber__"), "Second data row should be Excel row 15");
        assertEquals(25, result.get(2).get("__actualRowNumber__"), "Third data row should be Excel row 25");
        
        workbook.close();
        
        log.info("✅ convertSheetToMapList handles large row gaps correctly: 5, 15, 25");
    }

    @Test
    void testConvertSheetWithEmptyRowsInBetween() throws Exception {
        log.info("🧪 Testing convertSheetToMapList skips completely empty rows");
        
        // Create workbook where some rows exist but are completely empty
        Workbook workbook = createTestWorkbookWithEmptyRows();
        Sheet sheet = workbook.getSheetAt(0);
        
        Method convertMethod = ExcelProcessingService.class.getDeclaredMethod("convertSheetToMapList", Sheet.class);
        convertMethod.setAccessible(true);
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> result = (List<Map<String, Object>>) convertMethod.invoke(excelProcessingService, sheet);
        
        // Should only extract rows with data, skip empty rows
        assertEquals(2, result.size(), "Should only extract rows with actual data");
        
        // Verify we get the rows with data
        assertEquals(3, result.get(0).get("__actualRowNumber__"), "First data row should be Excel row 3");
        assertEquals(7, result.get(1).get("__actualRowNumber__"), "Second data row should be Excel row 7");
        
        workbook.close();
        
        log.info("✅ convertSheetToMapList correctly skips empty rows");
    }

    @Test
    void testConvertSheetWithPartiallyFilledRows() throws Exception {
        log.info("🧪 Testing convertSheetToMapList with partially filled rows");
        
        // Create workbook with partially filled rows (some cells empty)
        Workbook workbook = createTestWorkbookWithPartialData();
        Sheet sheet = workbook.getSheetAt(0);
        
        Method convertMethod = ExcelProcessingService.class.getDeclaredMethod("convertSheetToMapList", Sheet.class);
        convertMethod.setAccessible(true);
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> result = (List<Map<String, Object>>) convertMethod.invoke(excelProcessingService, sheet);
        
        // Should extract all rows that have at least some data
        assertEquals(3, result.size(), "Should extract all partially filled rows");
        
        // Verify row numbers
        assertEquals(3, result.get(0).get("__actualRowNumber__"), "First row should be Excel row 3");
        assertEquals(4, result.get(1).get("__actualRowNumber__"), "Second row should be Excel row 4"); 
        assertEquals(6, result.get(2).get("__actualRowNumber__"), "Third row should be Excel row 6");
        
        // Verify partial data handling
        assertEquals("John", result.get(0).get("name"));
        // Empty age cell could be null or empty string
        Object ageValue = result.get(0).get("age");
        assertTrue(ageValue == null || "".equals(ageValue), "Age should be null or empty");
        
        // Empty name cell could be null or empty string  
        Object nameValue = result.get(1).get("name");
        assertTrue(nameValue == null || "".equals(nameValue), "Name should be null or empty");
        assertEquals(30.0, result.get(1).get("age"));
        
        assertEquals("Bob", result.get(2).get("name"));
        assertEquals(35.0, result.get(2).get("age"));
        
        workbook.close();
        
        log.info("✅ convertSheetToMapList handles partially filled rows correctly");
    }

    // Helper methods to create test Excel workbooks

    private Workbook createTestWorkbookWithSkippedRows() {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("TestSheet");
        
        // Create headers (row 0)
        Row headerRow = sheet.createRow(0);
        headerRow.createCell(0).setCellValue("name");
        headerRow.createCell(1).setCellValue("age");
        
        // Create second header row (row 1) 
        Row header2Row = sheet.createRow(1);
        header2Row.createCell(0).setCellValue("Name");
        header2Row.createCell(1).setCellValue("Age");
        
        // Skip row 2 (empty)
        
        // Create data in row 3
        Row row3 = sheet.createRow(2); // 0-indexed, so row 2 is Excel row 3
        row3.createCell(0).setCellValue("John");
        row3.createCell(1).setCellValue(25);
        
        // Skip rows 4, 5 (empty)
        
        // Create data in row 6
        Row row6 = sheet.createRow(5); // 0-indexed, so row 5 is Excel row 6
        row6.createCell(0).setCellValue("Jane");
        row6.createCell(1).setCellValue(30);
        
        // Skip row 7 (empty)
        
        // Create data in row 8  
        Row row8 = sheet.createRow(7); // 0-indexed, so row 7 is Excel row 8
        row8.createCell(0).setCellValue("Bob");
        row8.createCell(1).setCellValue(35);
        
        return workbook;
    }
    
    private Workbook createTestWorkbookWithLargeGaps() {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("TestSheet");
        
        // Headers
        Row headerRow = sheet.createRow(0);
        headerRow.createCell(0).setCellValue("name");
        headerRow.createCell(1).setCellValue("age");
        
        Row header2Row = sheet.createRow(1);
        header2Row.createCell(0).setCellValue("Name");
        header2Row.createCell(1).setCellValue("Age");
        
        // Data in row 5 (0-indexed row 4)
        Row row5 = sheet.createRow(4);
        row5.createCell(0).setCellValue("Alice");
        row5.createCell(1).setCellValue(28);
        
        // Data in row 15 (0-indexed row 14)
        Row row15 = sheet.createRow(14);
        row15.createCell(0).setCellValue("Charlie");
        row15.createCell(1).setCellValue(32);
        
        // Data in row 25 (0-indexed row 24)
        Row row25 = sheet.createRow(24);
        row25.createCell(0).setCellValue("Diana");
        row25.createCell(1).setCellValue(27);
        
        return workbook;
    }
    
    private Workbook createTestWorkbookWithEmptyRows() {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("TestSheet");
        
        // Headers
        Row headerRow = sheet.createRow(0);
        headerRow.createCell(0).setCellValue("name");
        headerRow.createCell(1).setCellValue("age");
        
        Row header2Row = sheet.createRow(1);
        header2Row.createCell(0).setCellValue("Name");
        header2Row.createCell(1).setCellValue("Age");
        
        // Data in row 3
        Row row3 = sheet.createRow(2);
        row3.createCell(0).setCellValue("John");
        row3.createCell(1).setCellValue(25);
        
        // Empty row 4 (create row but no data)
        sheet.createRow(3);
        
        // Empty row 5 (create row but no data)
        sheet.createRow(4);
        
        // Empty row 6 (create row but no data)
        sheet.createRow(5);
        
        // Data in row 7
        Row row7 = sheet.createRow(6);
        row7.createCell(0).setCellValue("Jane");
        row7.createCell(1).setCellValue(30);
        
        return workbook;
    }
    
    private Workbook createTestWorkbookWithPartialData() {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("TestSheet");
        
        // Headers
        Row headerRow = sheet.createRow(0);
        headerRow.createCell(0).setCellValue("name");
        headerRow.createCell(1).setCellValue("age");
        
        Row header2Row = sheet.createRow(1);
        header2Row.createCell(0).setCellValue("Name");
        header2Row.createCell(1).setCellValue("Age");
        
        // Row 3: Name only
        Row row3 = sheet.createRow(2);
        row3.createCell(0).setCellValue("John");
        // age cell left empty
        
        // Row 4: Age only
        Row row4 = sheet.createRow(3);
        // name cell left empty
        row4.createCell(1).setCellValue(30);
        
        // Row 5: Completely empty (should be skipped)
        sheet.createRow(4);
        
        // Row 6: Both fields filled
        Row row6 = sheet.createRow(5);
        row6.createCell(0).setCellValue("Bob");
        row6.createCell(1).setCellValue(35);
        
        return workbook;
    }
}