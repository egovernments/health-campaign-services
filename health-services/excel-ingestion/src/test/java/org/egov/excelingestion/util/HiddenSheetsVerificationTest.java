package org.egov.excelingestion.util;

import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test to verify expected hidden sheet patterns and naming conventions
 */
public class HiddenSheetsVerificationTest {

    private final List<Path> tempFilesToCleanup = new ArrayList<>();

    @AfterEach
    void cleanupTempFiles() {
        for (Path tempFile : tempFilesToCleanup) {
            try {
                if (Files.exists(tempFile)) {
                    Files.delete(tempFile);
                    System.out.println("🧹 Cleaned up test file: " + tempFile.getFileName());
                }
            } catch (IOException e) {
                System.err.println("⚠️  Could not delete temp file: " + tempFile + " - " + e.getMessage());
            }
        }
        tempFilesToCleanup.clear();
    }

    @Test
    void testHiddenSheetNamingConventions() {
        // Test the naming convention patterns used by boundary utilities
        String[] expectedHiddenSheetPatterns = {
            "_h_Boundaries_h_",           // BoundaryColumnUtil
            "_h_BoundaryCodeMap_h_",      // Both utilities (collision case)
            "_h_CascadingBoundaries_h_",  // HierarchicalBoundaryUtil
            "_h_Level2Boundaries_h_",     // HierarchicalBoundaryUtil
            "_h_BoundaryLookup_h_"        // HierarchicalBoundaryUtil
        };
        
        for (String pattern : expectedHiddenSheetPatterns) {
            assertTrue(pattern.startsWith("_h_"), "Hidden sheet should start with _h_: " + pattern);
            assertTrue(pattern.endsWith("_h_"), "Hidden sheet should end with _h_: " + pattern);
            assertTrue(pattern.length() > 6, "Hidden sheet name should have content between markers: " + pattern);
            
            // Verify pattern can be used as sheet name (Excel has 31 char limit)
            assertTrue(pattern.length() <= 31, "Sheet name should not exceed Excel limit: " + pattern);
        }
        
        System.out.println("✅ All hidden sheet naming conventions are valid");
    }

    @Test
    void testSheetCreationAndHiding() throws IOException {
        XSSFWorkbook workbook = new XSSFWorkbook();
        
        // Simulate creating the types of sheets both utilities would create
        String[] hiddenSheetNames = {
            "_h_Boundaries_h_",
            "_h_BoundaryCodeMap_h_",
            "_h_CascadingBoundaries_h_",
            "_h_Level2Boundaries_h_",
            "_h_BoundaryLookup_h_"
        };
        
        // Create regular visible sheets
        Sheet mainSheet1 = workbook.createSheet("Users");
        Sheet mainSheet2 = workbook.createSheet("Facilities");
        
        // Create hidden helper sheets
        for (String hiddenSheetName : hiddenSheetNames) {
            Sheet hiddenSheet = workbook.createSheet(hiddenSheetName);
            workbook.setSheetHidden(workbook.getSheetIndex(hiddenSheetName), true);
        }
        
        // Verify total sheet count
        assertEquals(7, workbook.getNumberOfSheets(), "Should have 2 visible + 5 hidden sheets");
        
        // Verify visible sheets
        assertFalse(workbook.isSheetHidden(workbook.getSheetIndex("Users")), "Users sheet should be visible");
        assertFalse(workbook.isSheetHidden(workbook.getSheetIndex("Facilities")), "Facilities sheet should be visible");
        
        // Verify hidden sheets
        int hiddenCount = 0;
        int visibleCount = 0;
        
        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
            String sheetName = workbook.getSheetName(i);
            boolean isHidden = workbook.isSheetHidden(i);
            
            if (sheetName.startsWith("_h_")) {
                assertTrue(isHidden, "Helper sheet should be hidden: " + sheetName);
                hiddenCount++;
            } else {
                assertFalse(isHidden, "Main sheet should be visible: " + sheetName);
                visibleCount++;
            }
        }
        
        assertEquals(5, hiddenCount, "Should have 5 hidden sheets");
        assertEquals(2, visibleCount, "Should have 2 visible sheets");
        
        // Test that workbook can be saved with hidden sheets
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        workbook.write(bos);
        assertTrue(bos.size() > 0, "Workbook with hidden sheets should be saveable");
        
        workbook.close();
        System.out.println("✅ Sheet creation and hiding test passed");
    }

    @Test
    void testPotentialSheetNameCollisions() {
        // Test the known collision case
        Set<String> boundaryColumnUtilSheets = new HashSet<>();
        boundaryColumnUtilSheets.add("_h_Boundaries_h_");
        boundaryColumnUtilSheets.add("_h_BoundaryCodeMap_h_");
        
        Set<String> hierarchicalBoundaryUtilSheets = new HashSet<>();
        hierarchicalBoundaryUtilSheets.add("_h_CascadingBoundaries_h_");
        hierarchicalBoundaryUtilSheets.add("_h_Level2Boundaries_h_");
        hierarchicalBoundaryUtilSheets.add("_h_BoundaryLookup_h_");
        hierarchicalBoundaryUtilSheets.add("_h_BoundaryCodeMap_h_"); // Collision!
        
        // Find intersections
        Set<String> collisions = new HashSet<>(boundaryColumnUtilSheets);
        collisions.retainAll(hierarchicalBoundaryUtilSheets);
        
        assertEquals(1, collisions.size(), "Should have exactly 1 collision");
        assertTrue(collisions.contains("_h_BoundaryCodeMap_h_"), "Collision should be _h_BoundaryCodeMap_h_");
        
        System.out.println("⚠️  Detected sheet name collision: " + collisions.iterator().next());
        System.out.println("   This collision should be handled gracefully since both utilities create similar structure");
        
        // Verify combined sheet set
        Set<String> combinedSheets = new HashSet<>();
        combinedSheets.addAll(boundaryColumnUtilSheets);
        combinedSheets.addAll(hierarchicalBoundaryUtilSheets);
        
        assertEquals(5, combinedSheets.size(), "Combined should have 5 unique sheets (due to 1 collision)");
    }

    @Test
    void testSheetHidingLogic() throws IOException {
        XSSFWorkbook workbook = new XSSFWorkbook();
        
        // Create mix of sheets to test hiding logic
        workbook.createSheet("VisibleSheet1");
        workbook.createSheet("_h_HiddenSheet1_h_");
        workbook.createSheet("VisibleSheet2");
        workbook.createSheet("_h_HiddenSheet2_h_");
        workbook.createSheet("_h_AnotherHidden_h_");
        
        // Apply hiding logic (simulating ConfigBasedGenerationService logic)
        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
            String sheetName = workbook.getSheetName(i);
            if (sheetName.startsWith("_h_")) {
                workbook.setSheetHidden(i, true);
            }
        }
        
        // Verify hiding worked correctly
        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
            String sheetName = workbook.getSheetName(i);
            boolean shouldBeHidden = sheetName.startsWith("_h_");
            boolean isHidden = workbook.isSheetHidden(i);
            
            assertEquals(shouldBeHidden, isHidden, 
                "Sheet " + sheetName + " hiding status should match pattern");
        }
        
        // Count results
        long hiddenCount = 0;
        long visibleCount = 0;
        
        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
            if (workbook.isSheetHidden(i)) {
                hiddenCount++;
            } else {
                visibleCount++;
            }
        }
        
        assertEquals(3, hiddenCount, "Should have 3 hidden sheets");
        assertEquals(2, visibleCount, "Should have 2 visible sheets");
        
        workbook.close();
        System.out.println("✅ Sheet hiding logic test passed");
    }

    @Test
    void testWorkbookIntegrityWithManySheets() throws IOException {
        XSSFWorkbook workbook = new XSSFWorkbook();
        
        // Create a scenario similar to real usage with both utilities
        
        // Main sheets
        workbook.createSheet("Campaign_Config");
        workbook.createSheet("Users");
        workbook.createSheet("Facilities");
        workbook.createSheet("Boundary_Hierarchy");
        
        // Helper sheets from BoundaryColumnUtil (for Users sheet)
        workbook.createSheet("_h_Boundaries_h_");
        workbook.setSheetHidden(workbook.getSheetIndex("_h_Boundaries_h_"), true);
        
        // Helper sheets from HierarchicalBoundaryUtil (for Facilities sheet)
        workbook.createSheet("_h_CascadingBoundaries_h_");
        workbook.createSheet("_h_Level2Boundaries_h_");
        workbook.createSheet("_h_BoundaryLookup_h_");
        workbook.setSheetHidden(workbook.getSheetIndex("_h_CascadingBoundaries_h_"), true);
        workbook.setSheetHidden(workbook.getSheetIndex("_h_Level2Boundaries_h_"), true);
        workbook.setSheetHidden(workbook.getSheetIndex("_h_BoundaryLookup_h_"), true);
        
        // Shared helper sheet (collision case)
        workbook.createSheet("_h_BoundaryCodeMap_h_");
        workbook.setSheetHidden(workbook.getSheetIndex("_h_BoundaryCodeMap_h_"), true);
        
        // Verify final state
        assertEquals(9, workbook.getNumberOfSheets(), "Should have 9 total sheets");
        
        int visibleSheets = 0;
        int hiddenSheets = 0;
        
        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
            String sheetName = workbook.getSheetName(i);
            boolean isHidden = workbook.isSheetHidden(i);
            
            if (isHidden) {
                hiddenSheets++;
                assertTrue(sheetName.startsWith("_h_"), "Hidden sheet should follow naming convention: " + sheetName);
            } else {
                visibleSheets++;
                assertFalse(sheetName.startsWith("_h_"), "Visible sheet should not follow hidden naming convention: " + sheetName);
            }
        }
        
        assertEquals(4, visibleSheets, "Should have 4 visible sheets");
        assertEquals(5, hiddenSheets, "Should have 5 hidden sheets");
        
        // Test workbook can be saved with complex structure
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        workbook.write(bos);
        assertTrue(bos.size() > 1000, "Complex workbook should have substantial size");
        
        workbook.close();
        System.out.println("✅ Complex workbook integrity test passed - " + bos.size() + " bytes");
    }

    @Test
    void testSheetAccessAndOperations() throws IOException {
        XSSFWorkbook workbook = new XSSFWorkbook();
        
        // Create and populate hidden sheets with basic structure
        Sheet boundariesSheet = workbook.createSheet("_h_Boundaries_h_");
        boundariesSheet.createRow(0).createCell(0).setCellValue("District Level");
        boundariesSheet.createRow(1).createCell(0).setCellValue("District Alpha");
        workbook.setSheetHidden(workbook.getSheetIndex("_h_Boundaries_h_"), true);
        
        Sheet codeMapSheet = workbook.createSheet("_h_BoundaryCodeMap_h_");
        codeMapSheet.createRow(0).createCell(0).setCellValue("BoundaryDisplay");
        codeMapSheet.getRow(0).createCell(1).setCellValue("BoundaryCode");
        codeMapSheet.createRow(1).createCell(0).setCellValue("District Alpha");
        codeMapSheet.getRow(1).createCell(1).setCellValue("DISTRICT_A");
        workbook.setSheetHidden(workbook.getSheetIndex("_h_BoundaryCodeMap_h_"), true);
        
        // Verify we can still access and read hidden sheets programmatically
        Sheet retrievedBoundaries = workbook.getSheet("_h_Boundaries_h_");
        assertNotNull(retrievedBoundaries, "Should be able to access hidden sheet by name");
        assertEquals("District Level", retrievedBoundaries.getRow(0).getCell(0).getStringCellValue());
        
        Sheet retrievedCodeMap = workbook.getSheet("_h_BoundaryCodeMap_h_");
        assertNotNull(retrievedCodeMap, "Should be able to access hidden code map sheet");
        assertEquals("BoundaryCode", retrievedCodeMap.getRow(0).getCell(1).getStringCellValue());
        assertEquals("DISTRICT_A", retrievedCodeMap.getRow(1).getCell(1).getStringCellValue());
        
        // Verify sheets are actually hidden
        assertTrue(workbook.isSheetHidden(workbook.getSheetIndex("_h_Boundaries_h_")));
        assertTrue(workbook.isSheetHidden(workbook.getSheetIndex("_h_BoundaryCodeMap_h_")));
        
        workbook.close();
        System.out.println("✅ Sheet access and operations test passed");
    }
}