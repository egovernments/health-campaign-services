package org.egov.excelingestion.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.egov.excelingestion.util.ExcelUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test cases for Excel processing with skipped rows - testing that actual row numbers
 * are correctly preserved in the convertSheetToMapList method
 */
@Slf4j
class SkippedRowsExcelProcessingTest {

    private ExcelUtil excelUtil;

    @BeforeEach
    void setUp() {
        excelUtil = new ExcelUtil();
    }

    @Test
    void testConvertSheetToMapListWithSkippedRows() throws Exception {
        log.info("🧪 Testing convertSheetToMapList preserves actual Excel row numbers with skipped rows");

        // Create Excel workbook with skipped rows
        Workbook workbook = createTestWorkbookWithSkippedRows();
        Sheet sheet = workbook.getSheetAt(0);

        List<Map<String, Object>> result = excelUtil.convertSheetToMapListCached("fileId", "sheetName", sheet);

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

        assertEquals(25, ((Number) firstRow.get("age")).longValue(), "First row age should be 25");
        assertEquals(30, ((Number) secondRow.get("age")).longValue(), "Second row age should be 30");
        assertEquals(35, ((Number) thirdRow.get("age")).longValue(), "Third row age should be 35");


        workbook.close();

        log.info("✅ convertSheetToMapList correctly preserves Excel row numbers: 3, 6, 8");
    }

    @Test
    void testConvertSheetWithLargeRowGaps() throws Exception {
        log.info("🧪 Testing convertSheetToMapList with large gaps between rows");

        // Create Excel workbook with large gaps (rows 5, 15, 25)
        Workbook workbook = createTestWorkbookWithLargeGaps();
        Sheet sheet = workbook.getSheetAt(0);

        List<Map<String, Object>> result = excelUtil.convertSheetToMapListCached("fileId", "sheetName", sheet);

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

        List<Map<String, Object>> result = excelUtil.convertSheetToMapListCached("fileId", "sheetName", sheet);

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

        List<Map<String, Object>> result = excelUtil.convertSheetToMapListCached("fileId", "sheetName", sheet);

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
        assertEquals(30, ((Number) result.get(1).get("age")).longValue(), "Age should be 30");


        assertEquals("Bob", result.get(2).get("name"));
        assertEquals(35, ((Number) result.get(2).get("age")).longValue(), "Age should be 35");

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

    @Test
    void testWithGapsAtStart() throws Exception {
        log.info("🧪 Testing with a large gap at the beginning");
        Workbook workbook = createWorkbookWithGapsAtStart();
        Sheet sheet = workbook.getSheetAt(0);
        List<Map<String, Object>> result = excelUtil.convertSheetToMapListCached("fileId", "sheetName", sheet);
        assertEquals(2, result.size(), "Should extract 2 rows from a sheet with a large initial gap");
        assertEquals(101, result.get(0).get("__actualRowNumber__"));
        assertEquals(102, result.get(1).get("__actualRowNumber__"));
        workbook.close();
    }

    @Test
    void testWithGapsAtEnd() throws Exception {
        log.info("🧪 Testing with a large gap at the end");
        Workbook workbook = createWorkbookWithGapsAtEnd();
        Sheet sheet = workbook.getSheetAt(0);
        List<Map<String, Object>> result = excelUtil.convertSheetToMapListCached("fileId", "sheetName", sheet);
        assertEquals(2, result.size(), "Should extract 2 rows from a sheet with a large trailing gap");
        assertEquals(3, result.get(0).get("__actualRowNumber__"));
        assertEquals(4, result.get(1).get("__actualRowNumber__"));
        workbook.close();
    }

    @Test
    void testWithMultipleSmallGaps() throws Exception {
        log.info("🧪 Testing with multiple small gaps");
        Workbook workbook = createWorkbookWithMultipleSmallGaps();
        Sheet sheet = workbook.getSheetAt(0);
        List<Map<String, Object>> result = excelUtil.convertSheetToMapListCached("fileId", "sheetName", sheet);
        assertEquals(4, result.size(), "Should extract 4 rows from a sheet with multiple small gaps");
        assertEquals(3, result.get(0).get("__actualRowNumber__"));
        assertEquals(6, result.get(1).get("__actualRowNumber__"));
        assertEquals(9, result.get(2).get("__actualRowNumber__"));
        assertEquals(12, result.get(3).get("__actualRowNumber__"));
        workbook.close();
    }

    @Test
    void testWithVeryLargeGap() throws Exception {
        log.info("🧪 Testing with a single very large gap");
        Workbook workbook = createWorkbookWithVeryLargeGap();
        Sheet sheet = workbook.getSheetAt(0);
        List<Map<String, Object>> result = excelUtil.convertSheetToMapListCached("fileId", "sheetName", sheet);
        assertEquals(2, result.size(), "Should extract 2 rows from a sheet with a very large gap");
        assertEquals(5, result.get(0).get("__actualRowNumber__"));
        assertEquals(205, result.get(1).get("__actualRowNumber__"));
        workbook.close();
    }

    @Test
    void testWithAlternatingDataAndGaps() throws Exception {
        log.info("🧪 Testing with alternating data and gaps");
        Workbook workbook = createWorkbookWithAlternatingDataAndGaps();
        Sheet sheet = workbook.getSheetAt(0);
        List<Map<String, Object>> result = excelUtil.convertSheetToMapListCached("fileId", "sheetName", sheet);
        assertEquals(5, result.size(), "Should extract 5 rows from a sheet with alternating data and gaps");
        assertEquals(3, result.get(0).get("__actualRowNumber__"));
        assertEquals(5, result.get(1).get("__actualRowNumber__"));
        assertEquals(7, result.get(2).get("__actualRowNumber__"));
        assertEquals(9, result.get(3).get("__actualRowNumber__"));
        assertEquals(11, result.get(4).get("__actualRowNumber__"));
        workbook.close();
    }

    @Test
    void testWithSingleRowAtEnd() throws Exception {
        log.info("🧪 Testing with a single row at the very end of a large sheet");
        Workbook workbook = createWorkbookWithSingleRowAtEnd();
        Sheet sheet = workbook.getSheetAt(0);
        List<Map<String, Object>> result = excelUtil.convertSheetToMapListCached("fileId", "sheetName", sheet);
        assertEquals(1, result.size(), "Should extract 1 row from the end of a large sheet");
        assertEquals(301, result.get(0).get("__actualRowNumber__"));
        workbook.close();
    }

    @Test
    void testWithContiguousBlockAfterGap() throws Exception {
        log.info("🧪 Testing with a contiguous block of data after a large gap");
        Workbook workbook = createWorkbookWithContiguousBlockAfterGap();
        Sheet sheet = workbook.getSheetAt(0);
        List<Map<String, Object>> result = excelUtil.convertSheetToMapListCached("fileId", "sheetName", sheet);
        assertEquals(3, result.size(), "Should extract a block of 3 rows after a large gap");
        assertEquals(51, result.get(0).get("__actualRowNumber__"));
        assertEquals(52, result.get(1).get("__actualRowNumber__"));
        assertEquals(53, result.get(2).get("__actualRowNumber__"));
        workbook.close();
    }

    @Test
    void testWithRandomGaps() throws Exception {
        log.info("🧪 Testing with randomly distributed gaps");
        Workbook workbook = createWorkbookWithRandomGaps();
        Sheet sheet = workbook.getSheetAt(0);
        List<Map<String, Object>> result = excelUtil.convertSheetToMapListCached("fileId", "sheetName", sheet);
        assertEquals(5, result.size(), "Should extract 5 rows from a sheet with random gaps");
        assertEquals(8, result.get(0).get("__actualRowNumber__"));
        assertEquals(19, result.get(1).get("__actualRowNumber__"));
        assertEquals(23, result.get(2).get("__actualRowNumber__"));
        assertEquals(38, result.get(3).get("__actualRowNumber__"));
        assertEquals(50, result.get(4).get("__actualRowNumber__"));
        workbook.close();
    }

    // Helper methods for the new tests

    private Workbook createWorkbookWithGapsAtStart() {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("TestSheet");
        // Headers at row 0, 1
        Row headerRow = sheet.createRow(0);
        headerRow.createCell(0).setCellValue("name");
        headerRow.createCell(1).setCellValue("age");
        Row header2Row = sheet.createRow(1);
        header2Row.createCell(0).setCellValue("Name");
        header2Row.createCell(1).setCellValue("Age");

        // Data starts at row 101 (index 100)
        Row row101 = sheet.createRow(100);
        row101.createCell(0).setCellValue("First");
        row101.createCell(1).setCellValue(10);
        Row row102 = sheet.createRow(101);
        row102.createCell(0).setCellValue("Second");
        row102.createCell(1).setCellValue(20);
        return workbook;
    }

    private Workbook createWorkbookWithGapsAtEnd() {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("TestSheet");
        Row headerRow = sheet.createRow(0);
        headerRow.createCell(0).setCellValue("name");
        headerRow.createCell(1).setCellValue("age");
        Row header2Row = sheet.createRow(1);
        header2Row.createCell(0).setCellValue("Name");
        header2Row.createCell(1).setCellValue("Age");

        // Data at row 3 and 4
        Row row3 = sheet.createRow(2);
        row3.createCell(0).setCellValue("A");
        row3.createCell(1).setCellValue(1);
        Row row4 = sheet.createRow(3);
        row4.createCell(0).setCellValue("B");
        row4.createCell(1).setCellValue(2);
        
        // Create a row far away to set the last row number, but leave it empty
        sheet.createRow(200);
        
        return workbook;
    }

    private Workbook createWorkbookWithMultipleSmallGaps() {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("TestSheet");
        Row headerRow = sheet.createRow(0);
        headerRow.createCell(0).setCellValue("name");
        Row header2Row = sheet.createRow(1);
        header2Row.createCell(0).setCellValue("Name");

        // Data at 3, 6, 9, 12
        sheet.createRow(2).createCell(0).setCellValue("R3");
        sheet.createRow(5).createCell(0).setCellValue("R6");
        sheet.createRow(8).createCell(0).setCellValue("R9");
        sheet.createRow(11).createCell(0).setCellValue("R12");
        return workbook;
    }

    private Workbook createWorkbookWithVeryLargeGap() {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("TestSheet");
        Row headerRow = sheet.createRow(0);
        headerRow.createCell(0).setCellValue("name");
        Row header2Row = sheet.createRow(1);
        header2Row.createCell(0).setCellValue("Name");

        // Data at row 5 and 205
        sheet.createRow(4).createCell(0).setCellValue("Start");
        sheet.createRow(204).createCell(0).setCellValue("End");
        return workbook;
    }

    private Workbook createWorkbookWithAlternatingDataAndGaps() {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("TestSheet");
        Row headerRow = sheet.createRow(0);
        headerRow.createCell(0).setCellValue("name");
        Row header2Row = sheet.createRow(1);
        header2Row.createCell(0).setCellValue("Name");

        for (int i = 2; i < 12; i += 2) {
            sheet.createRow(i).createCell(0).setCellValue("Row " + (i + 1));
        }
        return workbook;
    }

    private Workbook createWorkbookWithSingleRowAtEnd() {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("TestSheet");
        Row headerRow = sheet.createRow(0);
        headerRow.createCell(0).setCellValue("name");
        Row header2Row = sheet.createRow(1);
        header2Row.createCell(0).setCellValue("Name");

        // Single data row at 301
        sheet.createRow(300).createCell(0).setCellValue("Final Row");
        return workbook;
    }

    private Workbook createWorkbookWithContiguousBlockAfterGap() {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("TestSheet");
        Row headerRow = sheet.createRow(0);
        headerRow.createCell(0).setCellValue("name");
        Row header2Row = sheet.createRow(1);
        header2Row.createCell(0).setCellValue("Name");

        // Block of data after a gap
        sheet.createRow(50).createCell(0).setCellValue("Block 1");
        sheet.createRow(51).createCell(0).setCellValue("Block 2");
        sheet.createRow(52).createCell(0).setCellValue("Block 3");
        return workbook;
    }

    private Workbook createWorkbookWithRandomGaps() {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("TestSheet");
        Row headerRow = sheet.createRow(0);
        headerRow.createCell(0).setCellValue("name");
        Row header2Row = sheet.createRow(1);
        header2Row.createCell(0).setCellValue("Name");

        sheet.createRow(7).createCell(0).setCellValue("D1");
        sheet.createRow(18).createCell(0).setCellValue("D2");
        sheet.createRow(22).createCell(0).setCellValue("D3");
        sheet.createRow(37).createCell(0).setCellValue("D4");
        sheet.createRow(49).createCell(0).setCellValue("D5");
        return workbook;
    }
}
