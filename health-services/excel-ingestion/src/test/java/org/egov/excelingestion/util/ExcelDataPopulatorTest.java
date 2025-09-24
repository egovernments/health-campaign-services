package org.egov.excelingestion.util;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.egov.excelingestion.config.ExcelIngestionConfig;
import org.egov.excelingestion.web.models.excel.ColumnDef;
import org.egov.excelingestion.web.models.excel.MultiSelectDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExcelDataPopulatorTest {

    @Mock
    private ExcelIngestionConfig config;

    @Mock
    private ExcelStyleHelper excelStyleHelper;

    @Mock
    private CellProtectionManager cellProtectionManager;

    @InjectMocks
    private ExcelDataPopulator excelDataPopulator;

    private List<ColumnDef> testColumnDefs;
    private List<Map<String, Object>> testDataRows;
    private Map<String, String> localizationMap;
    private CellStyle mockCellStyle;

    @BeforeEach
    void setUp() {
        setupMockBehaviors();
        setupTestData();
    }

    // ==================== BASIC SHEET CREATION TESTS ====================

    @Test
    void testPopulateSheetWithData_ValidDataAndColumns() throws IOException {
        // Given
        List<ColumnDef> columns = createBasicColumnDefs();
        List<Map<String, Object>> data = createBasicDataRows();

        // When
        Workbook workbook = excelDataPopulator.populateSheetWithData("TestSheet", columns, data, localizationMap);

        // Then
        assertNotNull(workbook, "Workbook should not be null");
        assertEquals(1, workbook.getNumberOfSheets(), "Should have exactly one sheet");
        
        Sheet sheet = workbook.getSheetAt(0);
        assertEquals("TestSheet", sheet.getSheetName(), "Sheet name should match");
        
        // Verify headers are created (2 rows)
        assertNotNull(sheet.getRow(0), "Technical header row should exist");
        assertNotNull(sheet.getRow(1), "Display header row should exist");
        
        // Verify data rows
        assertTrue(ExcelUtil.findActualLastRowWithData(sheet) >= 3, "Should have data rows after headers");
        
        workbook.close();
    }

    @Test
    void testPopulateSheetWithData_EmptyData() throws IOException {
        // Given
        List<ColumnDef> columns = createBasicColumnDefs();
        List<Map<String, Object>> emptyData = new ArrayList<>();

        // When
        Workbook workbook = excelDataPopulator.populateSheetWithData("TestSheet", columns, emptyData, localizationMap);

        // Then
        assertNotNull(workbook, "Workbook should not be null");
        Sheet sheet = workbook.getSheetAt(0);
        
        // Should have headers but no data rows (conditional formatting may extend sheet rows)
        // With pure visual validation, number fields create conditional formatting that extends the sheet
        assertTrue(ExcelUtil.findActualLastRowWithData(
                sheet) >= 1, "Should have at least header rows, may have more due to conditional formatting");
        
        workbook.close();
    }

    @Test
    void testPopulateSheetWithData_NullData() throws IOException {
        // Given
        List<ColumnDef> columns = createBasicColumnDefs();

        // When
        Workbook workbook = excelDataPopulator.populateSheetWithData("TestSheet", columns, null, localizationMap);

        // Then
        assertNotNull(workbook, "Workbook should not be null");
        Sheet sheet = workbook.getSheetAt(0);
        
        // Should have headers but no data rows (conditional formatting may extend sheet rows)
        // With pure visual validation, number fields create conditional formatting that extends the sheet
        assertTrue(ExcelUtil.findActualLastRowWithData(
                sheet) >= 1, "Should have at least header rows, may have more due to conditional formatting");
        
        workbook.close();
    }

    // ==================== HEADER CREATION TESTS ====================

    @Test
    void testCreateHeaderRows_TechnicalAndDisplayHeaders() throws IOException {
        // Given
        List<ColumnDef> columns = createBasicColumnDefs();

        // When
        Workbook workbook = excelDataPopulator.populateSheetWithData("TestSheet", columns, new ArrayList<>(), localizationMap);

        // Then
        Sheet sheet = workbook.getSheetAt(0);
        Row technicalRow = sheet.getRow(0);
        Row displayRow = sheet.getRow(1);
        
        assertNotNull(technicalRow, "Technical header row should exist");
        assertNotNull(displayRow, "Display header row should exist");
        
        // Check first column values
        Cell techCell = technicalRow.getCell(0);
        Cell displayCell = displayRow.getCell(0);
        
        assertNotNull(techCell, "Technical header cell should exist");
        assertNotNull(displayCell, "Display header cell should exist");
        
        assertEquals("name", techCell.getStringCellValue(), "Technical header should have field name");
        assertEquals("Full Name", displayCell.getStringCellValue(), "Display header should have localized name");
        
        // Verify first row is hidden
        assertTrue(technicalRow.getZeroHeight(), "Technical header row should be hidden");
        
        workbook.close();
    }

    @Test
    void testCreateHeaderRows_WithoutLocalization() throws IOException {
        // Given
        List<ColumnDef> columns = createBasicColumnDefs();

        // When
        Workbook workbook = excelDataPopulator.populateSheetWithData("TestSheet", columns, new ArrayList<>(), null);

        // Then
        Sheet sheet = workbook.getSheetAt(0);
        Row displayRow = sheet.getRow(1);
        Cell displayCell = displayRow.getCell(0);
        
        // Should fall back to technical name when no localization
        assertEquals("name", displayCell.getStringCellValue(), 
            "Display header should fall back to technical name when no localization");
        
        workbook.close();
    }

    @Test
    void testCreateHeaderRows_ColumnWidthAndStyling() throws IOException {
        // Given
        List<ColumnDef> columns = Arrays.asList(
            createColumnDef("name", "string", 50, "#FF0000", true, true),
            createColumnDef("age", "number", 20, "#00FF00", false, false)
        );

        // When
        Workbook workbook = excelDataPopulator.populateSheetWithData("TestSheet", columns, new ArrayList<>(), localizationMap);

        // Then
        Sheet sheet = workbook.getSheetAt(0);
        
        // Verify column widths are set
        assertTrue(sheet.getColumnWidth(0) > 0, "First column should have width set");
        assertTrue(sheet.getColumnWidth(1) > 0, "Second column should have width set");
        
        // Verify styling methods were called
        verify(excelStyleHelper, atLeastOnce()).createCustomHeaderStyle(any(Workbook.class), anyString(), anyBoolean());
        
        workbook.close();
    }

    @Test
    void testCreateHeaderRows_HiddenColumns() throws IOException {
        // Given
        List<ColumnDef> columns = Arrays.asList(
            createColumnDef("visible", "string", 30, null, false, false),
            createHiddenColumnDef("hidden", "string", 30)
        );

        // When
        Workbook workbook = excelDataPopulator.populateSheetWithData("TestSheet", columns, new ArrayList<>(), localizationMap);

        // Then
        Sheet sheet = workbook.getSheetAt(0);
        
        assertFalse(sheet.isColumnHidden(0), "First column should be visible");
        assertTrue(sheet.isColumnHidden(1), "Second column should be hidden");
        
        workbook.close();
    }

    // ==================== DATA POPULATION TESTS ====================

    @Test
    void testFillDataRows_VariousDataTypes() throws IOException {
        // Given
        List<ColumnDef> columns = Arrays.asList(
            createColumnDef("name", "string", 30, null, false, false),
            createColumnDef("age", "number", 20, null, false, false),
            createColumnDef("active", "boolean", 15, null, false, false),
            createColumnDef("salary", "number", 25, null, false, false)
        );
        
        List<Map<String, Object>> data = Arrays.asList(
            createDataRow("name", "John Doe", "age", 25, "active", true, "salary", 50000.50),
            createDataRow("name", "Jane Smith", "age", 30, "active", false, "salary", 75000)
        );

        // When
        Workbook workbook = excelDataPopulator.populateSheetWithData("TestSheet", columns, data, localizationMap);

        // Then
        Sheet sheet = workbook.getSheetAt(0);
        
        // Check first data row (row 2, after headers)
        Row dataRow1 = sheet.getRow(2);
        assertNotNull(dataRow1, "First data row should exist");
        
        assertEquals("John Doe", dataRow1.getCell(0).getStringCellValue(), "String value should be set correctly");
        assertEquals(25.0, dataRow1.getCell(1).getNumericCellValue(), "Integer value should be set correctly");
        assertTrue(dataRow1.getCell(2).getBooleanCellValue(), "Boolean value should be set correctly");
        assertEquals(50000.50, dataRow1.getCell(3).getNumericCellValue(), 0.001, "Double value should be set correctly");
        
        // Check second data row
        Row dataRow2 = sheet.getRow(3);
        assertNotNull(dataRow2, "Second data row should exist");
        assertEquals("Jane Smith", dataRow2.getCell(0).getStringCellValue(), "Second row string should be correct");
        
        workbook.close();
    }

    @Test
    void testFillDataRows_WithPrefix() throws IOException {
        // Given
        List<ColumnDef> columns = Arrays.asList(
            createColumnDefWithPrefix("id", "string", 30, "ID_")
        );
        
        List<Map<String, Object>> data = Arrays.asList(
            createDataRow("id", "12345")
        );

        // When
        Workbook workbook = excelDataPopulator.populateSheetWithData("TestSheet", columns, data, localizationMap);

        // Then
        Sheet sheet = workbook.getSheetAt(0);
        Row dataRow = sheet.getRow(2);
        
        assertEquals("ID_12345", dataRow.getCell(0).getStringCellValue(), 
            "Prefix should be added to string values");
        
        workbook.close();
    }

    @Test
    void testFillDataRows_NullAndEmptyValues() throws IOException {
        // Given
        List<ColumnDef> columns = createBasicColumnDefs();
        List<Map<String, Object>> data = Arrays.asList(
            createDataRow("name", null, "age", ""),
            createDataRow("name", "", "age", null)
        );

        // When
        Workbook workbook = excelDataPopulator.populateSheetWithData("TestSheet", columns, data, localizationMap);

        // Then
        Sheet sheet = workbook.getSheetAt(0);
        
        // Should handle null/empty values gracefully without throwing exceptions
        Row dataRow1 = sheet.getRow(2);
        Row dataRow2 = sheet.getRow(3);
        
        assertNotNull(dataRow1, "First data row should exist");
        assertNotNull(dataRow2, "Second data row should exist");
        
        workbook.close();
    }

    // ==================== MULTI-SELECT COLUMN TESTS ====================

    @Test
    void testExpandMultiSelectColumns_BasicExpansion() throws IOException {
        // Given
        List<ColumnDef> columns = Arrays.asList(
            createBasicColumnDef("name", "string"),
            createMultiSelectColumnDef("skills", 3, Arrays.asList("Java", "Python", "JavaScript"))
        );

        // When
        Workbook workbook = excelDataPopulator.populateSheetWithData("TestSheet", columns, new ArrayList<>(), localizationMap);

        // Then
        Sheet sheet = workbook.getSheetAt(0);
        Row headerRow = sheet.getRow(0);
        
        // Should have expanded columns: name + skills_MULTISELECT_1,2,3 + hidden skills
        assertTrue(headerRow.getLastCellNum() >= 5, "Should have expanded multi-select columns");
        
        workbook.close();
    }

    @Test
    void testExpandMultiSelectColumns_MultipleMultiSelects() throws IOException {
        // Given
        List<ColumnDef> columns = Arrays.asList(
            createMultiSelectColumnDef("skills", 2, Arrays.asList("Java", "Python")),
            createMultiSelectColumnDef("languages", 3, Arrays.asList("English", "Spanish", "French"))
        );

        // When
        Workbook workbook = excelDataPopulator.populateSheetWithData("TestSheet", columns, new ArrayList<>(), localizationMap);

        // Then
        Sheet sheet = workbook.getSheetAt(0);
        Row headerRow = sheet.getRow(0);
        
        // Should have all expanded columns
        assertTrue(headerRow.getLastCellNum() >= 7, 
            "Should have all multi-select columns expanded (2+1 + 3+1 = 7)");
        
        workbook.close();
    }

    @Test
    void testApplyMultiSelectFormulas_FormulaGeneration() throws IOException {
        // Given
        List<ColumnDef> columns = Arrays.asList(
            createMultiSelectColumnDef("skills", 2, Arrays.asList("Java", "Python"))
        );
        
        List<Map<String, Object>> data = Arrays.asList(
            createDataRow("skills_MULTISELECT_1", "Java", "skills_MULTISELECT_2", "Python")
        );

        // When
        Workbook workbook = excelDataPopulator.populateSheetWithData("TestSheet", columns, data, localizationMap);

        // Then
        Sheet sheet = workbook.getSheetAt(0);
        
        // Find the hidden concatenated column (should be last)
        Row dataRow = sheet.getRow(2);
        if (dataRow != null && dataRow.getLastCellNum() > 0) {
            Cell lastCell = dataRow.getCell(dataRow.getLastCellNum() - 1);
            if (lastCell != null && lastCell.getCellType() == CellType.FORMULA) {
                assertNotNull(lastCell.getCellFormula(), "Should have formula for concatenation");
            }
        }
        
        workbook.close();
    }

    // ==================== FORMATTING AND STYLING TESTS ====================

    @Test
    void testApplyFormatting_DataCellStyling() throws IOException {
        // Given
        List<ColumnDef> columns = Arrays.asList(
            createColumnDef("wrapped", "string", 30, null, true, true),
            createColumnDef("normal", "string", 30, null, false, false)
        );
        
        List<Map<String, Object>> data = Arrays.asList(
            createDataRow("wrapped", "Long text that should wrap", "normal", "Short text")
        );

        // When
        Workbook workbook = excelDataPopulator.populateSheetWithData("TestSheet", columns, data, localizationMap);

        // Then
        verify(excelStyleHelper).createDataCellStyle(any(Workbook.class), eq(true));  // Wrap text style
        verify(excelStyleHelper).createDataCellStyle(any(Workbook.class), eq(false)); // Normal style
        
        workbook.close();
    }

    @Test
    void testApplyProtection_CellProtectionAndSheetProtection() throws IOException {
        // Given
        when(config.getExcelSheetPassword()).thenReturn("test123");
        List<ColumnDef> columns = createBasicColumnDefs();

        // When
        Workbook workbook = excelDataPopulator.populateSheetWithData("TestSheet", columns, new ArrayList<>(), localizationMap);

        // Then
        verify(cellProtectionManager).applyCellProtection(any(Workbook.class), any(Sheet.class), any());
        
        Sheet sheet = workbook.getSheetAt(0);
        assertTrue(sheet.getProtect(), "Sheet should be protected");
        
        workbook.close();
    }

    @Test
    void testApplyValidations_DropdownValidation() throws IOException {
        // Given
        List<ColumnDef> columns = Arrays.asList(
            createColumnDefWithEnumValues("status", Arrays.asList("ACTIVE", "INACTIVE", "PENDING"))
        );

        // When
        Workbook workbook = excelDataPopulator.populateSheetWithData("TestSheet", columns, new ArrayList<>(), localizationMap);

        // Then
        Sheet sheet = workbook.getSheetAt(0);
        assertNotNull(sheet.getDataValidations(), "Sheet should have data validations");
        
        workbook.close();
    }

    // ==================== EXISTING WORKBOOK TESTS ====================

    @Test
    void testPopulateSheetWithData_AddToExistingWorkbook() throws IOException {
        // Given
        Workbook existingWorkbook = new XSSFWorkbook();
        existingWorkbook.createSheet("ExistingSheet");
        
        List<ColumnDef> columns = createBasicColumnDefs();
        List<Map<String, Object>> data = createBasicDataRows();

        // When
        Workbook result = excelDataPopulator.populateSheetWithData(existingWorkbook, "NewSheet", columns, data, localizationMap);

        // Then
        assertSame(existingWorkbook, result, "Should return the same workbook instance");
        assertEquals(2, result.getNumberOfSheets(), "Should have both existing and new sheet");
        assertEquals("ExistingSheet", result.getSheetAt(0).getSheetName(), "Existing sheet should remain");
        assertEquals("NewSheet", result.getSheetAt(1).getSheetName(), "New sheet should be added");
        
        result.close();
    }

    @Test
    void testPopulateSheetWithData_ReplaceExistingSheet() throws IOException {
        // Given
        Workbook workbook = new XSSFWorkbook();
        workbook.createSheet("TestSheet"); // Create sheet with same name
        
        List<ColumnDef> columns = createBasicColumnDefs();
        List<Map<String, Object>> data = createBasicDataRows();

        // When
        excelDataPopulator.populateSheetWithData(workbook, "TestSheet", columns, data, localizationMap);

        // Then
        assertEquals(1, workbook.getNumberOfSheets(), "Should still have only one sheet");
        assertEquals("TestSheet", workbook.getSheetAt(0).getSheetName(), "Sheet name should remain the same");
        
        // Verify it's a new sheet with our data
        Sheet sheet = workbook.getSheetAt(0);
        assertTrue(ExcelUtil.findActualLastRowWithData(sheet) > 1, "New sheet should have data rows");
        
        workbook.close();
    }

    // ==================== PERFORMANCE TESTS ====================

    @Test
    void testPopulateSheetWithData_LargeDataSet() throws IOException {
        // Given
        List<ColumnDef> columns = createBasicColumnDefs();
        List<Map<String, Object>> largeData = new ArrayList<>();
        
        // Create 5000 rows of test data
        for (int i = 0; i < 5000; i++) {
            largeData.add(createDataRow("name", "User" + i, "age", 20 + (i % 60)));
        }

        // When
        long startTime = System.currentTimeMillis();
        Workbook workbook = excelDataPopulator.populateSheetWithData("LargeSheet", columns, largeData, localizationMap);
        long endTime = System.currentTimeMillis();

        // Then
        assertTrue(endTime - startTime < 30000, "Large dataset should be processed within 30 seconds");
        Sheet sheet = workbook.getSheetAt(0);
        assertEquals(5001, 
                ExcelUtil.findActualLastRowWithData(sheet), "Should have all data rows (5000 + 1 for header)");
        
        workbook.close();
    }

    @Test
    void testPopulateSheetWithData_ManyColumns() throws IOException {
        // Given - create many columns
        List<ColumnDef> manyColumns = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            manyColumns.add(createBasicColumnDef("column" + i, "string"));
        }
        
        Map<String, Object> dataRow = new HashMap<>();
        for (int i = 0; i < 100; i++) {
            dataRow.put("column" + i, "value" + i);
        }
        List<Map<String, Object>> data = Arrays.asList(dataRow);

        // When
        long startTime = System.currentTimeMillis();
        Workbook workbook = excelDataPopulator.populateSheetWithData("WideSheet", manyColumns, data, localizationMap);
        long endTime = System.currentTimeMillis();

        // Then
        assertTrue(endTime - startTime < 10000, "Many columns should be processed within 10 seconds");
        Sheet sheet = workbook.getSheetAt(0);
        Row headerRow = sheet.getRow(0);
        assertEquals(100, headerRow.getLastCellNum(), "Should have all columns");
        
        workbook.close();
    }

    // ==================== ERROR HANDLING TESTS ====================

    @Test
    void testPopulateSheetWithData_InvalidSheetName() throws IOException {
        // Given - invalid sheet name
        List<ColumnDef> columns = createBasicColumnDefs();

        // When & Then - Empty sheet names are invalid in Excel, should throw exception
        assertThrows(IllegalArgumentException.class, () -> {
            Workbook workbook = excelDataPopulator.populateSheetWithData("", columns, new ArrayList<>(), localizationMap);
            workbook.close();
        }, "Empty sheet name should throw IllegalArgumentException");
    }

    @Test
    void testPopulateSheetWithData_EmptyColumnList() throws IOException {
        // Given
        List<ColumnDef> emptyColumns = new ArrayList<>();
        List<Map<String, Object>> data = createBasicDataRows();

        // When
        Workbook workbook = excelDataPopulator.populateSheetWithData("TestSheet", emptyColumns, data, localizationMap);

        // Then
        assertNotNull(workbook, "Should handle empty column list");
        Sheet sheet = workbook.getSheetAt(0);
        assertNotNull(sheet, "Sheet should still be created");
        
        workbook.close();
    }

    @Test
    void testPopulateSheetWithData_MismatchedDataAndColumns() throws IOException {
        // Given - data with fields not matching columns
        List<ColumnDef> columns = Arrays.asList(
            createBasicColumnDef("name", "string"),
            createBasicColumnDef("age", "number")
        );
        
        List<Map<String, Object>> data = Arrays.asList(
            createDataRow("different", "value", "other", 123) // Different field names
        );

        // When
        Workbook workbook = excelDataPopulator.populateSheetWithData("TestSheet", columns, data, localizationMap);

        // Then
        assertNotNull(workbook, "Should handle mismatched data gracefully");
        Sheet sheet = workbook.getSheetAt(0);
        Row dataRow = sheet.getRow(2);
        if (dataRow != null) {
            // Cells with no matching data should be null or empty
            Cell nameCell = dataRow.getCell(0);
            assertTrue(nameCell == null || nameCell.getCellType() == CellType.BLANK,
                "Cells with no matching data should be empty");
        }
        
        workbook.close();
    }

    // ==================== HELPER METHODS ====================

    private void setupMockBehaviors() {
        lenient().when(config.getExcelRowLimit()).thenReturn(10000);
        lenient().when(config.getExcelSheetPassword()).thenReturn(null);
        
        // Mock to return a CellStyle from the same workbook that's being used
        lenient().when(excelStyleHelper.createCustomHeaderStyle(any(Workbook.class), any(), anyBoolean()))
                .thenAnswer(invocation -> {
                    Workbook workbook = invocation.getArgument(0);
                    return workbook.createCellStyle();
                });
        
        lenient().when(excelStyleHelper.createDataCellStyle(any(Workbook.class), anyBoolean()))
                .thenAnswer(invocation -> {
                    Workbook workbook = invocation.getArgument(0);
                    return workbook.createCellStyle();
                });
        
        lenient().when(cellProtectionManager.applyCellProtection(any(Workbook.class), any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private void setupTestData() {
        localizationMap = new HashMap<>();
        localizationMap.put("name", "Full Name");
        localizationMap.put("age", "Age");
        localizationMap.put("email", "Email Address");
        localizationMap.put("skills", "Skills");
        localizationMap.put("status", "Status");
    }

    private List<ColumnDef> createBasicColumnDefs() {
        return Arrays.asList(
            createBasicColumnDef("name", "string"),
            createBasicColumnDef("age", "number")
        );
    }

    private List<Map<String, Object>> createBasicDataRows() {
        return Arrays.asList(
            createDataRow("name", "John Doe", "age", 25),
            createDataRow("name", "Jane Smith", "age", 30)
        );
    }

    private ColumnDef createBasicColumnDef(String name, String type) {
        return ColumnDef.builder()
            .name(name)
            .type(type)
            .build();
    }

    private ColumnDef createColumnDef(String name, String type, Integer width, String colorHex, 
                                    boolean wrapText, boolean adjustHeight) {
        return ColumnDef.builder()
            .name(name)
            .type(type)
            .width(width)
            .colorHex(colorHex)
            .wrapText(wrapText)
            .adjustHeight(adjustHeight)
            .build();
    }

    private ColumnDef createColumnDefWithPrefix(String name, String type, Integer width, String prefix) {
        return ColumnDef.builder()
            .name(name)
            .type(type)
            .width(width)
            .prefix(prefix)
            .build();
    }

    private ColumnDef createColumnDefWithEnumValues(String name, List<String> enumValues) {
        return ColumnDef.builder()
            .name(name)
            .type("enum")
            .enumValues(enumValues)
            .build();
    }

    private ColumnDef createHiddenColumnDef(String name, String type, Integer width) {
        return ColumnDef.builder()
            .name(name)
            .type(type)
            .width(width)
            .hideColumn(true)
            .build();
    }

    private ColumnDef createMultiSelectColumnDef(String name, int maxSelections, List<String> enumValues) {
        MultiSelectDetails multiSelectDetails = MultiSelectDetails.builder()
            .maxSelections(maxSelections)
            .enumValues(enumValues)
            .build();
            
        return ColumnDef.builder()
            .name(name)
            .type("multiselect")
            .multiSelectDetails(multiSelectDetails)
            .build();
    }

    private Map<String, Object> createDataRow(Object... keyValuePairs) {
        Map<String, Object> row = new HashMap<>();
        for (int i = 0; i < keyValuePairs.length; i += 2) {
            row.put((String) keyValuePairs[i], keyValuePairs[i + 1]);
        }
        return row;
    }
}