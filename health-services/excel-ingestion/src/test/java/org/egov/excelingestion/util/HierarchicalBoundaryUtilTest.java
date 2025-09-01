package org.egov.excelingestion.util;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.egov.excelingestion.config.ExcelIngestionConfig;
import org.egov.excelingestion.service.BoundaryService;
import org.egov.excelingestion.web.models.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class HierarchicalBoundaryUtilTest {

    @Mock
    private ExcelIngestionConfig config;
    
    @Mock
    private BoundaryService boundaryService;
    
    @Mock
    private BoundaryUtil boundaryUtil;
    
    @Mock
    private ExcelStyleHelper excelStyleHelper;
    
    private HierarchicalBoundaryUtil hierarchicalBoundaryUtil;
    private XSSFWorkbook workbook;
    private AutoCloseable closeable;
    
    @BeforeEach
    void setUp() {
        closeable = MockitoAnnotations.openMocks(this);
        hierarchicalBoundaryUtil = new HierarchicalBoundaryUtil(
            config, boundaryService, boundaryUtil, excelStyleHelper
        );
        workbook = new XSSFWorkbook();
        
        // Setup default config
        when(config.getExcelRowLimit()).thenReturn(1000);
        when(config.getDefaultHeaderColor()).thenReturn("BLUE");
    }
    
    @AfterEach
    void tearDown() throws Exception {
        if (workbook != null) {
            workbook.close();
        }
        if (closeable != null) {
            closeable.close();
        }
    }
    
    @Test
    void testAddHierarchicalBoundaryColumn_SheetNotFound() {
        // Arrange
        List<Boundary> boundaries = Arrays.asList(
            Boundary.builder().code("B1").name("Boundary1").type("Village").build()
        );
        Map<String, String> localizationMap = new HashMap<>();
        
        // Act
        hierarchicalBoundaryUtil.addHierarchicalBoundaryColumn(
            workbook, "NonExistentSheet", localizationMap, boundaries, 
            "hierarchy1", "tenant1", new RequestInfo()
        );
        
        // Assert - Should not throw exception, just log warning
        verify(boundaryService, never()).fetchBoundaryRelationship(any(), any(), any());
    }
    
    @Test
    void testAddHierarchicalBoundaryColumn_NoBoundariesConfigured() {
        // Arrange
        Sheet sheet = workbook.createSheet("TestSheet");
        Map<String, String> localizationMap = new HashMap<>();
        
        // Act
        hierarchicalBoundaryUtil.addHierarchicalBoundaryColumn(
            workbook, "TestSheet", localizationMap, null, 
            "hierarchy1", "tenant1", new RequestInfo()
        );
        
        // Assert
        verify(boundaryService, never()).fetchBoundaryRelationship(any(), any(), any());
        assertTrue(sheet.getLastRowNum() <= 0);
    }
    
    @Test
    void testAddHierarchicalBoundaryColumn_EmptyBoundariesList() {
        // Arrange
        Sheet sheet = workbook.createSheet("TestSheet");
        Map<String, String> localizationMap = new HashMap<>();
        List<Boundary> emptyBoundaries = new ArrayList<>();
        
        // Act
        hierarchicalBoundaryUtil.addHierarchicalBoundaryColumn(
            workbook, "TestSheet", localizationMap, emptyBoundaries, 
            "hierarchy1", "tenant1", new RequestInfo()
        );
        
        // Assert
        verify(boundaryService, never()).fetchBoundaryRelationship(any(), any(), any());
        assertTrue(sheet.getLastRowNum() <= 0);
    }
    
    @Test
    void testAddHierarchicalBoundaryColumn_InsufficientHierarchyLevels() {
        // Arrange
        Sheet sheet = workbook.createSheet("TestSheet");
        Map<String, String> localizationMap = new HashMap<>();
        List<Boundary> boundaries = Arrays.asList(
            Boundary.builder().code("B1").name("Boundary1").type("Village").build()
        );
        
        // Mock boundary service responses
        BoundarySearchResponse searchResponse = new BoundarySearchResponse();
        when(boundaryService.fetchBoundaryRelationship(any(), any(), any())).thenReturn(searchResponse);
        
        BoundaryHierarchyResponse hierarchyResponse = new BoundaryHierarchyResponse();
        BoundaryHierarchy hierarchy = new BoundaryHierarchy();
        BoundaryHierarchyChild child = new BoundaryHierarchyChild();
        child.setBoundaryType("Level1");
        hierarchy.setBoundaryHierarchy(Arrays.asList(child)); // Only 1 level
        hierarchyResponse.setBoundaryHierarchy(Arrays.asList(hierarchy));
        when(boundaryService.fetchBoundaryHierarchy(any(), any(), any())).thenReturn(hierarchyResponse);
        
        when(boundaryUtil.buildCodeToBoundaryMap(any())).thenReturn(new HashMap<>());
        
        // Act
        hierarchicalBoundaryUtil.addHierarchicalBoundaryColumn(
            workbook, "TestSheet", localizationMap, boundaries, 
            "hierarchy1", "tenant1", new RequestInfo()
        );
        
        // Assert - Should log warning and return early
        assertTrue(sheet.getLastRowNum() <= 0);
    }
    
    @Test
    void testAddHierarchicalBoundaryColumn_SuccessfulCreation() {
        // Arrange
        Sheet sheet = workbook.createSheet("TestSheet");
        Row hiddenRow = sheet.createRow(0);
        Row visibleRow = sheet.createRow(1);
        hiddenRow.createCell(0).setCellValue("FIELD1");
        visibleRow.createCell(0).setCellValue("Field 1");
        
        Map<String, String> localizationMap = new HashMap<>();
        localizationMap.put("HIERARCHY1_LEVEL2", "Level 2");
        localizationMap.put("HIERARCHY1_LEVEL3", "Level 3");
        localizationMap.put("HCM_ADMIN_CONSOLE_BOUNDARY_CODE", "Boundary Code");
        localizationMap.put("BOUNDARY1", "Boundary 1");
        localizationMap.put("BOUNDARY2", "Boundary 2");
        
        List<Boundary> boundaries = Arrays.asList(
            Boundary.builder().code("B1").name("BOUNDARY1").type("Village").build()
        );
        
        // Mock boundary service responses
        BoundarySearchResponse searchResponse = new BoundarySearchResponse();
        when(boundaryService.fetchBoundaryRelationship(any(), any(), any())).thenReturn(searchResponse);
        
        BoundaryHierarchyResponse hierarchyResponse = new BoundaryHierarchyResponse();
        BoundaryHierarchy hierarchy = new BoundaryHierarchy();
        
        BoundaryHierarchyChild level1 = new BoundaryHierarchyChild();
        level1.setBoundaryType("Level1");
        BoundaryHierarchyChild level2 = new BoundaryHierarchyChild();
        level2.setBoundaryType("Level2");
        BoundaryHierarchyChild level3 = new BoundaryHierarchyChild();
        level3.setBoundaryType("Level3");
        
        hierarchy.setBoundaryHierarchy(Arrays.asList(level1, level2, level3));
        hierarchyResponse.setBoundaryHierarchy(Arrays.asList(hierarchy));
        when(boundaryService.fetchBoundaryHierarchy(any(), any(), any())).thenReturn(hierarchyResponse);
        
        // Mock boundary util responses
        when(boundaryUtil.buildCodeToBoundaryMap(any())).thenReturn(new HashMap<>());
        
        BoundaryUtil.BoundaryRowData rowData = new BoundaryUtil.BoundaryRowData(
            Arrays.asList("ROOT", "BOUNDARY1", "BOUNDARY2"),
            "BOUNDARY2"
        );
        when(boundaryUtil.processBoundariesWithEnrichment(any(), any(), any()))
            .thenReturn(Arrays.asList(rowData));
        
        // Don't mock style to avoid casting issues
        
        // Act
        hierarchicalBoundaryUtil.addHierarchicalBoundaryColumn(
            workbook, "TestSheet", localizationMap, boundaries, 
            "hierarchy1", "tenant1", new RequestInfo()
        );
        
        // Assert
        // The method adds columns, so verify the sheet has more columns now
        Row updatedVisibleRow = sheet.getRow(1);
        assertTrue(updatedVisibleRow.getLastCellNum() > 1);
        
        // Verify hidden sheet was created
        assertNotNull(workbook.getSheet("_h_SimpleLookup_h_"));
        
        // Verify hidden sheet was created
        Sheet hiddenSheet = workbook.getSheet("_h_SimpleLookup_h_");
        assertNotNull(hiddenSheet);
        assertTrue(workbook.isSheetHidden(workbook.getSheetIndex("_h_SimpleLookup_h_")));
    }
    
    private BoundaryHierarchyChild createBoundaryHierarchyChild(String boundaryType) {
        BoundaryHierarchyChild child = new BoundaryHierarchyChild();
        child.setBoundaryType(boundaryType);
        return child;
    }
    
    @Test
    void testCreateHashedKey_HandlesNullInput() throws Exception {
        // Use reflection to test private method
        java.lang.reflect.Method method = HierarchicalBoundaryUtil.class.getDeclaredMethod("createHashedKey", String.class);
        method.setAccessible(true);
        
        String result = (String) method.invoke(hierarchicalBoundaryUtil, (String) null);
        assertEquals("H_EMPTY", result);
    }
    
    @Test
    void testCreateHashedKey_HandlesEmptyInput() throws Exception {
        // Use reflection to test private method
        java.lang.reflect.Method method = HierarchicalBoundaryUtil.class.getDeclaredMethod("createHashedKey", String.class);
        method.setAccessible(true);
        
        String result = (String) method.invoke(hierarchicalBoundaryUtil, "");
        assertEquals("H_EMPTY", result);
    }
    
    @Test
    void testCreateHashedKey_GeneratesConsistentHash() throws Exception {
        // Use reflection to test private method
        java.lang.reflect.Method method = HierarchicalBoundaryUtil.class.getDeclaredMethod("createHashedKey", String.class);
        method.setAccessible(true);
        
        String input = "boundary1#boundary2#boundary3";
        String hash1 = (String) method.invoke(hierarchicalBoundaryUtil, input);
        String hash2 = (String) method.invoke(hierarchicalBoundaryUtil, input);
        
        assertEquals(hash1, hash2);
        assertTrue(hash1.startsWith("H_"));
        assertTrue(hash1.substring(2).matches("[a-f0-9]+"));
    }
    
    @Test
    void testSanitizeForKey_HandlesSpecialCharacters() throws Exception {
        // Use reflection to test private method
        java.lang.reflect.Method method = HierarchicalBoundaryUtil.class.getDeclaredMethod("sanitizeForKey", String.class);
        method.setAccessible(true);
        
        String input = "Test@#$%^&*()Name!";
        String result = (String) method.invoke(hierarchicalBoundaryUtil, input);
        
        assertEquals("Test_Name", result);
    }
    
    @Test
    void testHashFunction_ConsistentResults() throws Exception {
        // Test that hashing works consistently
        java.lang.reflect.Method method = HierarchicalBoundaryUtil.class.getDeclaredMethod("createHashedKey", String.class);
        method.setAccessible(true);
        
        String input1 = "State1#District1#Block1";
        String input2 = "State1#District1#Block1";
        String input3 = "State1#District2#Block1";
        
        String hash1 = (String) method.invoke(hierarchicalBoundaryUtil, input1);
        String hash2 = (String) method.invoke(hierarchicalBoundaryUtil, input2);
        String hash3 = (String) method.invoke(hierarchicalBoundaryUtil, input3);
        
        assertEquals(hash1, hash2); // Same input should give same hash
        assertNotEquals(hash1, hash3); // Different input should give different hash
        
        assertTrue(hash1.startsWith("H_"));
        assertTrue(hash2.startsWith("H_"));
        assertTrue(hash3.startsWith("H_"));
    }
    
    @Test
    void testSanitization_EdgeCases() throws Exception {
        java.lang.reflect.Method method = HierarchicalBoundaryUtil.class.getDeclaredMethod("sanitizeForKey", String.class);
        method.setAccessible(true);
        
        // Test null
        String nullResult = (String) method.invoke(hierarchicalBoundaryUtil, (String) null);
        assertEquals("Empty", nullResult);
        
        // Test empty
        String emptyResult = (String) method.invoke(hierarchicalBoundaryUtil, "");
        assertEquals("Empty", emptyResult);
        
        // Test only special characters
        String specialResult = (String) method.invoke(hierarchicalBoundaryUtil, "@#$%^&*()");
        assertEquals("Empty", specialResult);
        
        // Test consecutive underscores
        String underscoreResult = (String) method.invoke(hierarchicalBoundaryUtil, "Test___Name");
        assertEquals("Test_Name", underscoreResult);
    }
    
    @Test
    void testNameForRange_ExcelCompatibility() throws Exception {
        java.lang.reflect.Method method = HierarchicalBoundaryUtil.class.getDeclaredMethod("sanitizeNameForRange", String.class);
        method.setAccessible(true);
        
        // Test starts with letter or underscore
        String digitResult = (String) method.invoke(hierarchicalBoundaryUtil, "123Test");
        assertTrue(digitResult.startsWith("L_"));
        
        // Test normal name
        String normalResult = (String) method.invoke(hierarchicalBoundaryUtil, "TestName");
        assertEquals("TestName", normalResult);
        
        // Test with special chars
        String specialResult = (String) method.invoke(hierarchicalBoundaryUtil, "Test@Name");
        assertEquals("Test_Name", specialResult);
        
        // Ensure all results are valid Excel range names
        assertTrue(digitResult.matches("^[A-Za-z_].*"));
        assertTrue(normalResult.matches("^[A-Za-z_].*"));
        assertTrue(specialResult.matches("^[A-Za-z_].*"));
    }
    
    @Test
    void testAddHierarchicalBoundaryColumn_HandlesLargeBoundaryList() {
        // Arrange - Create a scenario with many boundaries that would exceed 255 char limit
        Sheet sheet = workbook.createSheet("TestSheet");
        sheet.createRow(0);
        sheet.createRow(1);
        
        Map<String, String> localizationMap = new HashMap<>();
        localizationMap.put("HIERARCHY1_LEVEL2", "Level 2");
        localizationMap.put("HCM_ADMIN_CONSOLE_BOUNDARY_CODE", "Boundary Code");
        
        // Create many boundaries with long names to exceed 255 character limit
        List<Boundary> boundaries = new ArrayList<>();
        for (int i = 1; i <= 50; i++) {
            String boundaryName = "VERY_LONG_BOUNDARY_NAME_" + String.format("%02d", i) + "_WITH_LOTS_OF_CHARACTERS";
            localizationMap.put(boundaryName, "Long Boundary Name " + i);
            boundaries.add(Boundary.builder().code("B" + i).name(boundaryName).type("Village").build());
        }
        
        // Mock boundary service responses
        BoundarySearchResponse searchResponse = new BoundarySearchResponse();
        when(boundaryService.fetchBoundaryRelationship(any(), any(), any())).thenReturn(searchResponse);
        
        BoundaryHierarchyResponse hierarchyResponse = new BoundaryHierarchyResponse();
        BoundaryHierarchy hierarchy = new BoundaryHierarchy();
        hierarchy.setBoundaryHierarchy(Arrays.asList(
            createBoundaryHierarchyChild("Level1"),
            createBoundaryHierarchyChild("Level2")
        ));
        hierarchyResponse.setBoundaryHierarchy(Arrays.asList(hierarchy));
        when(boundaryService.fetchBoundaryHierarchy(any(), any(), any())).thenReturn(hierarchyResponse);
        
        when(boundaryUtil.buildCodeToBoundaryMap(any())).thenReturn(new HashMap<>());
        
        // Create boundary data with long names
        List<BoundaryUtil.BoundaryRowData> boundaryData = new ArrayList<>();
        for (int i = 1; i <= 50; i++) {
            String boundaryName = "VERY_LONG_BOUNDARY_NAME_" + String.format("%02d", i) + "_WITH_LOTS_OF_CHARACTERS";
            boundaryData.add(new BoundaryUtil.BoundaryRowData(Arrays.asList("ROOT", boundaryName), boundaryName));
        }
        when(boundaryUtil.processBoundariesWithEnrichment(any(), any(), any())).thenReturn(boundaryData);
        
        // Act - This should not throw IllegalArgumentException about 255 character limit
        assertDoesNotThrow(() -> {
            hierarchicalBoundaryUtil.addHierarchicalBoundaryColumn(
                workbook, "TestSheet", localizationMap, boundaries, 
                "hierarchy1", "tenant1", new RequestInfo()
            );
        });
        
        // Assert - Verify that the process completed successfully
        Sheet hiddenSheet = workbook.getSheet("_h_SimpleLookup_h_");
        assertNotNull(hiddenSheet, "Hidden lookup sheet should be created");
        
        // Verify that data validation was applied (doesn't throw exception)
        List<? extends DataValidation> validations = sheet.getDataValidations();
        assertNotNull(validations, "Data validations should be present");
        
        // Verify columns were added 
        Row updatedVisibleRow = sheet.getRow(1);
        assertTrue(updatedVisibleRow.getLastCellNum() > 1, "Cascading columns should be added");
        
        // Verify that large boundary list was handled properly (should use range-based validation)
        // This test primarily ensures no exception is thrown during the process
    }
}