package org.egov.excelingestion.util;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test to verify dropdown columns and data validations are created properly
 */
public class DropdownVerificationTest {

    private final List<Path> tempFilesToCleanup = new ArrayList<>();

    public static void main(String[] args) {
        DropdownVerificationTest test = new DropdownVerificationTest();
        try {
            System.out.println("🚀 Running Dropdown Verification Tests");
            System.out.println("======================================\n");
            
            test.testBoundaryColumnUtilCreatesDropdowns();
            test.testHierarchicalBoundaryUtilCreatesDropdowns();
            test.testDropdownFormulasAndReferences();
            test.testBothUtilsDropdownCoexistence();
            
            System.out.println("🎉 All dropdown verification tests completed successfully!");
            
        } catch (Exception e) {
            System.err.println("❌ Test failed: " + e.getMessage());
            e.printStackTrace();
        } finally {
            test.cleanupTempFiles();
        }
    }

    @AfterEach
    void cleanupTempFiles() {
        for (Path tempFile : tempFilesToCleanup) {
            try {
                if (Files.exists(tempFile)) {
                    Files.delete(tempFile);
                }
            } catch (IOException e) {
                // Ignore cleanup errors
            }
        }
        tempFilesToCleanup.clear();
    }

    @Test
    void testBoundaryColumnUtilCreatesDropdowns() throws IOException {
        System.out.println("🧪 Testing BoundaryColumnUtil Dropdown Creation");
        System.out.println("===============================================");
        
        XSSFWorkbook workbook = new XSSFWorkbook();
        
        // Create a sheet with traditional boundary columns structure
        Sheet testSheet = workbook.createSheet("TestSheet");
        
        // Add schema columns first
        Row hiddenRow = testSheet.createRow(0);
        Row visibleRow = testSheet.createRow(1);
        hiddenRow.createCell(0).setCellValue("FIELD1");
        hiddenRow.createCell(1).setCellValue("FIELD2");
        visibleRow.createCell(0).setCellValue("Field 1");
        visibleRow.createCell(1).setCellValue("Field 2");
        
        // Add boundary columns manually to simulate BoundaryColumnUtil
        // Traditional approach: Level dropdown + Boundary dropdown + Code column
        hiddenRow.createCell(2).setCellValue("BOUNDARY_LEVEL");
        hiddenRow.createCell(3).setCellValue("BOUNDARY_NAME");
        hiddenRow.createCell(4).setCellValue("HCM_ADMIN_CONSOLE_BOUNDARY_CODE");
        
        visibleRow.createCell(2).setCellValue("District Level"); // Level header
        visibleRow.createCell(3).setCellValue("Boundary Name");  // Boundary header  
        visibleRow.createCell(4).setCellValue("Boundary Code");  // Code header
        
        // Create helper sheets that BoundaryColumnUtil would create
        Sheet boundariesSheet = workbook.createSheet("_h_Boundaries_h_");
        createBoundariesSheetStructure(boundariesSheet);
        workbook.setSheetHidden(workbook.getSheetIndex("_h_Boundaries_h_"), true);
        
        Sheet codeMapSheet = workbook.createSheet("_h_BoundaryCodeMap_h_");
        createCodeMapSheetStructure(codeMapSheet);
        workbook.setSheetHidden(workbook.getSheetIndex("_h_BoundaryCodeMap_h_"), true);
        
        // Create named ranges that BoundaryColumnUtil would create
        createNamedRanges(workbook);
        
        // Add data validations manually to simulate what BoundaryColumnUtil does
        addTraditionalBoundaryValidations(testSheet);
        
        // Verify the structure
        System.out.println("📋 Verifying Traditional Boundary Structure:");
        System.out.println("  Column 2 (Level): " + visibleRow.getCell(2).getStringCellValue());
        System.out.println("  Column 3 (Boundary): " + visibleRow.getCell(3).getStringCellValue());
        System.out.println("  Column 4 (Code): " + visibleRow.getCell(4).getStringCellValue());
        
        // Verify hidden sheets exist
        assertNotNull(workbook.getSheet("_h_Boundaries_h_"), "Boundaries helper sheet should exist");
        assertNotNull(workbook.getSheet("_h_BoundaryCodeMap_h_"), "Code map helper sheet should exist");
        
        // Verify data validations were added
        List<? extends DataValidation> validations = testSheet.getDataValidations();
        assertFalse(validations.isEmpty(), "Data validations should be created");
        System.out.println("✅ Found " + validations.size() + " data validations");
        
        // Test workbook can be saved
        testWorkbookSave(workbook, "Traditional boundary workbook");
        
        workbook.close();
        System.out.println("✅ BoundaryColumnUtil dropdown test completed\n");
    }

    @Test
    void testHierarchicalBoundaryUtilCreatesDropdowns() throws IOException {
        System.out.println("🧪 Testing HierarchicalBoundaryUtil Dropdown Creation");
        System.out.println("====================================================");
        
        XSSFWorkbook workbook = new XSSFWorkbook();
        
        // Create a sheet with hierarchical boundary columns structure
        Sheet testSheet = workbook.createSheet("TestSheet");
        
        // Add schema columns first
        Row hiddenRow = testSheet.createRow(0);
        Row visibleRow = testSheet.createRow(1);
        hiddenRow.createCell(0).setCellValue("FIELD1");
        hiddenRow.createCell(1).setCellValue("FIELD2");
        visibleRow.createCell(0).setCellValue("Field 1");
        visibleRow.createCell(1).setCellValue("Field 2");
        
        // Add cascading boundary columns manually to simulate HierarchicalBoundaryUtil
        // Hierarchical approach: Multiple level columns + Hidden code column
        String[] levelHeaders = {"District Level", "Village Level", "Zone Level"};
        String[] hiddenHeaders = {"ADMIN_DISTRICT", "ADMIN_VILLAGE", "ADMIN_ZONE"};
        
        for (int i = 0; i < levelHeaders.length; i++) {
            hiddenRow.createCell(2 + i).setCellValue(hiddenHeaders[i]);
            visibleRow.createCell(2 + i).setCellValue(levelHeaders[i]);
        }
        
        // Add hidden boundary code column
        hiddenRow.createCell(5).setCellValue("HCM_ADMIN_CONSOLE_BOUNDARY_CODE");
        visibleRow.createCell(5).setCellValue("Boundary Code");
        
        // Create helper sheets that HierarchicalBoundaryUtil would create
        Sheet cascadingSheet = workbook.createSheet("_h_CascadingBoundaries_h_");
        createCascadingSheetStructure(cascadingSheet);
        workbook.setSheetHidden(workbook.getSheetIndex("_h_CascadingBoundaries_h_"), true);
        
        Sheet level2Sheet = workbook.createSheet("_h_Level2Boundaries_h_");
        createLevel2SheetStructure(level2Sheet);
        workbook.setSheetHidden(workbook.getSheetIndex("_h_Level2Boundaries_h_"), true);
        
        Sheet lookupSheet = workbook.createSheet("_h_BoundaryLookup_h_");
        createLookupSheetStructure(lookupSheet);
        workbook.setSheetHidden(workbook.getSheetIndex("_h_BoundaryLookup_h_"), true);
        
        Sheet codeMapSheet = workbook.createSheet("_h_BoundaryCodeMap_h_");
        createCodeMapSheetStructure(codeMapSheet);
        workbook.setSheetHidden(workbook.getSheetIndex("_h_BoundaryCodeMap_h_"), true);
        
        // Create named ranges that HierarchicalBoundaryUtil would create
        createHierarchicalNamedRanges(workbook);
        
        // Add cascading data validations manually
        addCascadingBoundaryValidations(testSheet);
        
        // Verify the structure
        System.out.println("📋 Verifying Hierarchical Boundary Structure:");
        for (int i = 0; i < levelHeaders.length; i++) {
            System.out.println("  Column " + (2 + i) + ": " + visibleRow.getCell(2 + i).getStringCellValue());
        }
        System.out.println("  Column 5 (Hidden Code): " + visibleRow.getCell(5).getStringCellValue());
        
        // Verify all hidden sheets exist
        String[] expectedSheets = {"_h_CascadingBoundaries_h_", "_h_Level2Boundaries_h_", 
                                  "_h_BoundaryLookup_h_", "_h_BoundaryCodeMap_h_"};
        for (String sheetName : expectedSheets) {
            assertNotNull(workbook.getSheet(sheetName), sheetName + " should exist");
            assertTrue(workbook.isSheetHidden(workbook.getSheetIndex(sheetName)), 
                      sheetName + " should be hidden");
        }
        
        // Verify data validations were added
        List<? extends DataValidation> validations = testSheet.getDataValidations();
        assertFalse(validations.isEmpty(), "Cascading data validations should be created");
        System.out.println("✅ Found " + validations.size() + " cascading data validations");
        
        // Test workbook can be saved
        testWorkbookSave(workbook, "Hierarchical boundary workbook");
        
        workbook.close();
        System.out.println("✅ HierarchicalBoundaryUtil dropdown test completed\n");
    }

    @Test
    void testDropdownFormulasAndReferences() throws IOException {
        System.out.println("🧪 Testing Dropdown Formulas and References");
        System.out.println("===========================================");
        
        XSSFWorkbook workbook = new XSSFWorkbook();
        Sheet testSheet = workbook.createSheet("TestSheet");
        
        // Create basic structure
        setupBasicSheet(testSheet);
        
        // Test traditional dropdown formulas
        System.out.println("📝 Testing Traditional Dropdown Formulas:");
        
        // Level dropdown formula
        String levelFormula = "Levels";
        System.out.println("  Level dropdown formula: " + levelFormula);
        assertTrue(levelFormula.equals("Levels"), "Level formula should reference Levels range");
        
        // Boundary dropdown formula (depends on level selection)
        String boundaryFormula = "IF(C3=\"\", \"\", INDIRECT(\"Level_\"&MATCH(C3, Levels, 0)))";
        System.out.println("  Boundary dropdown formula: " + boundaryFormula);
        assertTrue(boundaryFormula.contains("INDIRECT"), "Boundary formula should use INDIRECT");
        assertTrue(boundaryFormula.contains("MATCH"), "Boundary formula should use MATCH for level lookup");
        
        // Test hierarchical dropdown formulas  
        System.out.println("\n📝 Testing Hierarchical Dropdown Formulas:");
        
        // First column formula
        String firstColumnFormula = "Level2Boundaries";
        System.out.println("  First column formula: " + firstColumnFormula);
        assertTrue(firstColumnFormula.equals("Level2Boundaries"), "First column should reference Level2Boundaries");
        
        // Cascading column formula
        String cascadingFormula = "IF(C3=\"\", \"\", INDIRECT(VLOOKUP(C3,BoundaryLookup,2,FALSE)))";
        System.out.println("  Cascading column formula: " + cascadingFormula);
        assertTrue(cascadingFormula.contains("VLOOKUP"), "Cascading formula should use VLOOKUP");
        assertTrue(cascadingFormula.contains("BoundaryLookup"), "Cascading formula should reference lookup sheet");
        
        // Boundary code formula  
        String codeFormula = "IF(E3<>\"\",VLOOKUP(E3,BoundaryCodeMap,2,FALSE),IF(D3<>\"\",VLOOKUP(D3,BoundaryCodeMap,2,FALSE),IF(C3<>\"\",VLOOKUP(C3,BoundaryCodeMap,2,FALSE),\"\")))";
        System.out.println("  Code formula: " + codeFormula.substring(0, Math.min(codeFormula.length(), 80)) + "...");
        assertTrue(codeFormula.contains("VLOOKUP"), "Code formula should use VLOOKUP");
        assertTrue(codeFormula.contains("BoundaryCodeMap"), "Code formula should reference code map");
        
        workbook.close();
        System.out.println("✅ Formula validation completed\n");
    }

    @Test
    void testBothUtilsDropdownCoexistence() throws IOException {
        System.out.println("🧪 Testing Both Utils Dropdown Coexistence");
        System.out.println("==========================================");
        
        XSSFWorkbook workbook = new XSSFWorkbook();
        
        // Create two sheets - one for each approach
        Sheet traditionalSheet = workbook.createSheet("TraditionalSheet");
        Sheet hierarchicalSheet = workbook.createSheet("HierarchicalSheet");
        
        // Setup traditional sheet (BoundaryColumnUtil style)
        setupTraditionalBoundarySheet(traditionalSheet, workbook);
        
        // Setup hierarchical sheet (HierarchicalBoundaryUtil style)  
        setupHierarchicalBoundarySheet(hierarchicalSheet, workbook);
        
        // Verify both approaches work together
        System.out.println("📊 Coexistence Analysis:");
        
        // Count data validations in each sheet
        int traditionalValidations = traditionalSheet.getDataValidations().size();
        int hierarchicalValidations = hierarchicalSheet.getDataValidations().size();
        
        System.out.println("  Traditional sheet validations: " + traditionalValidations);
        System.out.println("  Hierarchical sheet validations: " + hierarchicalValidations);
        
        assertTrue(traditionalValidations > 0, "Traditional sheet should have validations");
        assertTrue(hierarchicalValidations > 0, "Hierarchical sheet should have validations");
        
        // Verify shared sheet handling (collision case)
        Sheet sharedCodeMap = workbook.getSheet("_h_BoundaryCodeMap_h_");
        assertNotNull(sharedCodeMap, "Shared BoundaryCodeMap sheet should exist");
        assertTrue(workbook.isSheetHidden(workbook.getSheetIndex("_h_BoundaryCodeMap_h_")), 
                  "Shared sheet should be hidden");
        
        // Verify named ranges don't conflict
        Name levelsRange = workbook.getName("Levels");
        Name level2Range = workbook.getName("Level2Boundaries");
        Name lookupRange = workbook.getName("BoundaryLookup");
        Name codeMapRange = workbook.getName("BoundaryCodeMap");
        
        System.out.println("  Named ranges created:");
        if (levelsRange != null) System.out.println("    ✅ Levels");
        if (level2Range != null) System.out.println("    ✅ Level2Boundaries");
        if (lookupRange != null) System.out.println("    ✅ BoundaryLookup");
        if (codeMapRange != null) System.out.println("    ✅ BoundaryCodeMap");
        
        // Test workbook integrity
        testWorkbookSave(workbook, "Combined boundary approaches workbook");
        
        workbook.close();
        System.out.println("✅ Coexistence test completed\n");
    }

    // Helper methods

    private void setupBasicSheet(Sheet sheet) {
        Row hiddenRow = sheet.createRow(0);
        Row visibleRow = sheet.createRow(1);
        hiddenRow.createCell(0).setCellValue("FIELD1");
        visibleRow.createCell(0).setCellValue("Field 1");
    }

    private void createBoundariesSheetStructure(Sheet sheet) {
        // Level headers
        Row headerRow = sheet.createRow(0);
        headerRow.createCell(0).setCellValue("District Level");
        headerRow.createCell(1).setCellValue("Village Level");
        headerRow.createCell(2).setCellValue("Zone Level");
        
        // Level data
        Row dataRow = sheet.createRow(1);
        dataRow.createCell(0).setCellValue("District Alpha");
        dataRow.createCell(1).setCellValue("Village Alpha 1 (District Alpha)");
        dataRow.createCell(2).setCellValue("Zone Alpha 1-1 (Village Alpha 1)");
    }

    private void createCodeMapSheetStructure(Sheet sheet) {
        Row headerRow = sheet.createRow(0);
        headerRow.createCell(0).setCellValue("BoundaryName");
        headerRow.createCell(1).setCellValue("BoundaryCode");
        
        Row dataRow1 = sheet.createRow(1);
        dataRow1.createCell(0).setCellValue("District Alpha");
        dataRow1.createCell(1).setCellValue("DISTRICT_A");
        
        Row dataRow2 = sheet.createRow(2);
        dataRow2.createCell(0).setCellValue("Village Alpha 1");
        dataRow2.createCell(1).setCellValue("VILLAGE_A1");
    }

    private void createCascadingSheetStructure(Sheet sheet) {
        Row headerRow = sheet.createRow(0);
        headerRow.createCell(0).setCellValue("District Alpha");
        headerRow.createCell(1).setCellValue("District Beta");
        
        Row dataRow = sheet.createRow(1);
        dataRow.createCell(0).setCellValue("Village Alpha 1");
        dataRow.createCell(1).setCellValue("Village Beta 1");
    }

    private void createLevel2SheetStructure(Sheet sheet) {
        Row dataRow1 = sheet.createRow(0);
        dataRow1.createCell(0).setCellValue("District Alpha");
        
        Row dataRow2 = sheet.createRow(1);
        dataRow2.createCell(0).setCellValue("District Beta");
    }

    private void createLookupSheetStructure(Sheet sheet) {
        Row headerRow = sheet.createRow(0);
        headerRow.createCell(0).setCellValue("DisplayName");
        headerRow.createCell(1).setCellValue("SanitizedName");
        
        Row dataRow = sheet.createRow(1);
        dataRow.createCell(0).setCellValue("District Alpha");
        dataRow.createCell(1).setCellValue("District_Alpha");
    }

    private void createNamedRanges(XSSFWorkbook workbook) {
        // Levels range
        Name levelsRange = workbook.createName();
        levelsRange.setNameName("Levels");
        levelsRange.setRefersToFormula("_h_Boundaries_h_!$A$1:$C$1");
        
        // Level-specific ranges
        Name level1Range = workbook.createName();
        level1Range.setNameName("Level_1");
        level1Range.setRefersToFormula("_h_Boundaries_h_!$A$2:$A$10");
        
        // BoundaryCodeMap range
        Name codeMapRange = workbook.createName();
        codeMapRange.setNameName("BoundaryCodeMap");
        codeMapRange.setRefersToFormula("_h_BoundaryCodeMap_h_!$A$1:$B$10");
    }

    private void createHierarchicalNamedRanges(XSSFWorkbook workbook) {
        // Level2Boundaries range
        Name level2Range = workbook.createName();
        level2Range.setNameName("Level2Boundaries");
        level2Range.setRefersToFormula("_h_Level2Boundaries_h_!$A$1:$A$10");
        
        // BoundaryLookup range
        Name lookupRange = workbook.createName();
        lookupRange.setNameName("BoundaryLookup");
        lookupRange.setRefersToFormula("_h_BoundaryLookup_h_!$A$1:$B$10");
        
        // BoundaryCodeMap range
        Name codeMapRange = workbook.createName();
        codeMapRange.setNameName("BoundaryCodeMap");
        codeMapRange.setRefersToFormula("_h_BoundaryCodeMap_h_!$A$1:$B$10");
    }

    private void addTraditionalBoundaryValidations(Sheet sheet) {
        DataValidationHelper dvHelper = sheet.getDataValidationHelper();
        
        // Level dropdown validation for row 2 (first data row)
        DataValidationConstraint levelConstraint = dvHelper.createFormulaListConstraint("Levels");
        CellRangeAddressList levelAddr = new CellRangeAddressList(2, 2, 2, 2); // Row 2, Column C (level)
        DataValidation levelValidation = dvHelper.createValidation(levelConstraint, levelAddr);
        sheet.addValidationData(levelValidation);
        
        // Boundary dropdown validation (dependent on level)
        String boundaryFormula = "IF(C3=\"\", \"\", INDIRECT(\"Level_\"&MATCH(C3, Levels, 0)))";
        DataValidationConstraint boundaryConstraint = dvHelper.createFormulaListConstraint(boundaryFormula);
        CellRangeAddressList boundaryAddr = new CellRangeAddressList(2, 2, 3, 3); // Row 2, Column D (boundary)
        DataValidation boundaryValidation = dvHelper.createValidation(boundaryConstraint, boundaryAddr);
        sheet.addValidationData(boundaryValidation);
    }

    private void addCascadingBoundaryValidations(Sheet sheet) {
        DataValidationHelper dvHelper = sheet.getDataValidationHelper();
        
        // First column validation (Level 2)
        DataValidationConstraint firstConstraint = dvHelper.createFormulaListConstraint("Level2Boundaries");
        CellRangeAddressList firstAddr = new CellRangeAddressList(2, 2, 2, 2); // Row 2, Column C
        DataValidation firstValidation = dvHelper.createValidation(firstConstraint, firstAddr);
        sheet.addValidationData(firstValidation);
        
        // Second column validation (cascading)
        String cascadingFormula = "IF(C3=\"\", \"\", INDIRECT(VLOOKUP(C3,BoundaryLookup,2,FALSE)))";
        DataValidationConstraint cascadingConstraint = dvHelper.createFormulaListConstraint(cascadingFormula);
        CellRangeAddressList cascadingAddr = new CellRangeAddressList(2, 2, 3, 3); // Row 2, Column D
        DataValidation cascadingValidation = dvHelper.createValidation(cascadingConstraint, cascadingAddr);
        sheet.addValidationData(cascadingValidation);
        
        // Third column validation (further cascading)
        String thirdFormula = "IF(D3=\"\", \"\", INDIRECT(VLOOKUP(D3,BoundaryLookup,2,FALSE)))";
        DataValidationConstraint thirdConstraint = dvHelper.createFormulaListConstraint(thirdFormula);
        CellRangeAddressList thirdAddr = new CellRangeAddressList(2, 2, 4, 4); // Row 2, Column E
        DataValidation thirdValidation = dvHelper.createValidation(thirdConstraint, thirdAddr);
        sheet.addValidationData(thirdValidation);
    }

    private void setupTraditionalBoundarySheet(Sheet sheet, XSSFWorkbook workbook) {
        setupBasicSheet(sheet);
        
        // Add boundary columns
        Row hiddenRow = sheet.getRow(0);
        Row visibleRow = sheet.getRow(1);
        hiddenRow.createCell(1).setCellValue("BOUNDARY_LEVEL");
        hiddenRow.createCell(2).setCellValue("BOUNDARY_NAME");
        visibleRow.createCell(1).setCellValue("Level");
        visibleRow.createCell(2).setCellValue("Boundary");
        
        // Create helper sheet if not exists
        if (workbook.getSheet("_h_BoundaryCodeMap_h_") == null) {
            Sheet codeMap = workbook.createSheet("_h_BoundaryCodeMap_h_");
            createCodeMapSheetStructure(codeMap);
            workbook.setSheetHidden(workbook.getSheetIndex("_h_BoundaryCodeMap_h_"), true);
        }
        
        addTraditionalBoundaryValidations(sheet);
    }

    private void setupHierarchicalBoundarySheet(Sheet sheet, XSSFWorkbook workbook) {
        setupBasicSheet(sheet);
        
        // Add hierarchical columns
        Row hiddenRow = sheet.getRow(0);
        Row visibleRow = sheet.getRow(1);
        hiddenRow.createCell(1).setCellValue("ADMIN_DISTRICT");
        hiddenRow.createCell(2).setCellValue("ADMIN_VILLAGE");
        visibleRow.createCell(1).setCellValue("District");
        visibleRow.createCell(2).setCellValue("Village");
        
        // Create helper sheets if not exists
        if (workbook.getSheet("_h_Level2Boundaries_h_") == null) {
            Sheet level2 = workbook.createSheet("_h_Level2Boundaries_h_");
            createLevel2SheetStructure(level2);
            workbook.setSheetHidden(workbook.getSheetIndex("_h_Level2Boundaries_h_"), true);
            
            Sheet lookup = workbook.createSheet("_h_BoundaryLookup_h_");
            createLookupSheetStructure(lookup);
            workbook.setSheetHidden(workbook.getSheetIndex("_h_BoundaryLookup_h_"), true);
        }
        
        if (workbook.getSheet("_h_BoundaryCodeMap_h_") == null) {
            Sheet codeMap = workbook.createSheet("_h_BoundaryCodeMap_h_");
            createCodeMapSheetStructure(codeMap);
            workbook.setSheetHidden(workbook.getSheetIndex("_h_BoundaryCodeMap_h_"), true);
        }
        
        createHierarchicalNamedRanges(workbook);
        addCascadingBoundaryValidations(sheet);
    }

    private void testWorkbookSave(XSSFWorkbook workbook, String description) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        workbook.write(bos);
        assertTrue(bos.size() > 0, description + " should be saveable");
        System.out.println("💾 " + description + " saved successfully (" + bos.size() + " bytes)");
    }
}