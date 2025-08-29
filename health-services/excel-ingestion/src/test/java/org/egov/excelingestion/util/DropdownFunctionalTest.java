package org.egov.excelingestion.util;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Functional test to create actual Excel files with working dropdowns for manual verification
 */
public class DropdownFunctionalTest {

    public static void main(String[] args) {
        System.out.println("🧪 Creating Functional Dropdown Test Files");
        System.out.println("==========================================\n");
        
        try {
            createTraditionalBoundaryExcel();
            createHierarchicalBoundaryExcel();
            createCombinedExcel();
            
            System.out.println("\n✅ All functional test Excel files created successfully!");
            System.out.println("📝 You can now manually test the dropdowns in Excel:");
            System.out.println("   • traditional_boundary_test.xlsx");
            System.out.println("   • hierarchical_boundary_test.xlsx"); 
            System.out.println("   • combined_boundary_test.xlsx");
            
        } catch (Exception e) {
            System.err.println("❌ Test failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void createTraditionalBoundaryExcel() throws IOException {
        System.out.println("📄 Creating Traditional Boundary Test File...");
        
        XSSFWorkbook workbook = new XSSFWorkbook();
        
        // Main data sheet
        Sheet dataSheet = workbook.createSheet("User_Data");
        createTraditionalBoundarySheet(dataSheet, workbook);
        
        // Helper sheets
        createBoundariesHelperSheet(workbook);
        createCodeMapHelperSheet(workbook);
        
        // Save file
        try (FileOutputStream fos = new FileOutputStream("traditional_boundary_test.xlsx")) {
            workbook.write(fos);
            System.out.println("✅ traditional_boundary_test.xlsx created");
        }
        
        workbook.close();
    }

    private static void createHierarchicalBoundaryExcel() throws IOException {
        System.out.println("📄 Creating Hierarchical Boundary Test File...");
        
        XSSFWorkbook workbook = new XSSFWorkbook();
        
        // Main data sheet
        Sheet dataSheet = workbook.createSheet("Facility_Data");
        createHierarchicalBoundarySheet(dataSheet, workbook);
        
        // Helper sheets
        createCascadingHelperSheet(workbook);
        createLevel2HelperSheet(workbook);
        createLookupHelperSheet(workbook);
        createCodeMapHelperSheet(workbook);
        
        // Save file
        try (FileOutputStream fos = new FileOutputStream("hierarchical_boundary_test.xlsx")) {
            workbook.write(fos);
            System.out.println("✅ hierarchical_boundary_test.xlsx created");
        }
        
        workbook.close();
    }

    private static void createCombinedExcel() throws IOException {
        System.out.println("📄 Creating Combined Boundary Test File...");
        
        XSSFWorkbook workbook = new XSSFWorkbook();
        
        // Create both types of sheets
        Sheet traditionalSheet = workbook.createSheet("Users_Traditional");
        Sheet hierarchicalSheet = workbook.createSheet("Facilities_Hierarchical");
        
        createTraditionalBoundarySheet(traditionalSheet, workbook);
        createHierarchicalBoundarySheet(hierarchicalSheet, workbook);
        
        // Create all helper sheets
        createBoundariesHelperSheet(workbook);
        createCascadingHelperSheet(workbook);
        createLevel2HelperSheet(workbook);
        createLookupHelperSheet(workbook);
        createCodeMapHelperSheet(workbook);
        
        // Save file
        try (FileOutputStream fos = new FileOutputStream("combined_boundary_test.xlsx")) {
            workbook.write(fos);
            System.out.println("✅ combined_boundary_test.xlsx created");
        }
        
        workbook.close();
    }

    private static void createTraditionalBoundarySheet(Sheet sheet, XSSFWorkbook workbook) {
        // Create header rows
        Row hiddenRow = sheet.createRow(0);
        Row visibleRow = sheet.createRow(1);
        
        // Schema columns
        hiddenRow.createCell(0).setCellValue("USER_NAME");
        hiddenRow.createCell(1).setCellValue("USER_EMAIL");
        visibleRow.createCell(0).setCellValue("User Name");
        visibleRow.createCell(1).setCellValue("User Email");
        
        // Boundary columns (Traditional approach)
        hiddenRow.createCell(2).setCellValue("BOUNDARY_LEVEL");
        hiddenRow.createCell(3).setCellValue("BOUNDARY_NAME");
        hiddenRow.createCell(4).setCellValue("HCM_ADMIN_CONSOLE_BOUNDARY_CODE");
        
        visibleRow.createCell(2).setCellValue("Level");
        visibleRow.createCell(3).setCellValue("Boundary");
        visibleRow.createCell(4).setCellValue("Boundary Code");
        
        // Add data validations
        DataValidationHelper dvHelper = sheet.getDataValidationHelper();
        
        // Level dropdown (for rows 3-50)
        DataValidationConstraint levelConstraint = dvHelper.createFormulaListConstraint("Levels");
        CellRangeAddressList levelAddr = new CellRangeAddressList(2, 49, 2, 2);
        DataValidation levelValidation = dvHelper.createValidation(levelConstraint, levelAddr);
        levelValidation.setShowErrorBox(true);
        levelValidation.setShowPromptBox(true);
        levelValidation.createPromptBox("Select Level", "Choose a boundary level from the dropdown");
        levelValidation.createErrorBox("Invalid Level", "Please select a valid level from the dropdown");
        sheet.addValidationData(levelValidation);
        
        // Boundary dropdown (dependent on level, for rows 3-50)
        String boundaryFormula = "IF(C3=\"\",\"\",INDIRECT(\"Level_\"&MATCH(C3,Levels,0)))";
        DataValidationConstraint boundaryConstraint = dvHelper.createFormulaListConstraint(boundaryFormula);
        CellRangeAddressList boundaryAddr = new CellRangeAddressList(2, 49, 3, 3);
        DataValidation boundaryValidation = dvHelper.createValidation(boundaryConstraint, boundaryAddr);
        boundaryValidation.setShowErrorBox(true);
        boundaryValidation.setShowPromptBox(true);
        boundaryValidation.createPromptBox("Select Boundary", "Choose a boundary from the dropdown");
        boundaryValidation.createErrorBox("Invalid Boundary", "Please select a valid boundary");
        sheet.addValidationData(boundaryValidation);
        
        // Add formulas for boundary code auto-population (rows 3-50)
        for (int row = 2; row < 50; row++) {
            Row dataRow = sheet.createRow(row);
            Cell codeCell = dataRow.createCell(4);
            String formula = "IF(D" + (row + 1) + "=\"\",\"\",VLOOKUP(D" + (row + 1) + ",BoundaryCodeMap,2,FALSE))";
            codeCell.setCellFormula(formula);
        }
        
        // Set column widths
        sheet.setColumnWidth(0, 25 * 256); // User Name
        sheet.setColumnWidth(1, 30 * 256); // User Email
        sheet.setColumnWidth(2, 20 * 256); // Level
        sheet.setColumnWidth(3, 35 * 256); // Boundary
        sheet.setColumnWidth(4, 20 * 256); // Code
        
        // Hide code column
        sheet.setColumnHidden(4, true);
        
        // Create named ranges if not exist
        if (workbook.getName("Levels") == null) {
            Name levelsRange = workbook.createName();
            levelsRange.setNameName("Levels");
            levelsRange.setRefersToFormula("_h_Boundaries_h_!$A$1:$C$1");
        }
        
        if (workbook.getName("Level_1") == null) {
            Name level1Range = workbook.createName();
            level1Range.setNameName("Level_1");
            level1Range.setRefersToFormula("_h_Boundaries_h_!$A$2:$A$10");
            
            Name level2Range = workbook.createName();
            level2Range.setNameName("Level_2");
            level2Range.setRefersToFormula("_h_Boundaries_h_!$B$2:$B$10");
            
            Name level3Range = workbook.createName();
            level3Range.setNameName("Level_3");
            level3Range.setRefersToFormula("_h_Boundaries_h_!$C$2:$C$10");
        }
        
        if (workbook.getName("BoundaryCodeMap") == null) {
            Name codeMapRange = workbook.createName();
            codeMapRange.setNameName("BoundaryCodeMap");
            codeMapRange.setRefersToFormula("_h_BoundaryCodeMap_h_!$A$1:$B$20");
        }
    }

    private static void createHierarchicalBoundarySheet(Sheet sheet, XSSFWorkbook workbook) {
        // Create header rows
        Row hiddenRow = sheet.createRow(0);
        Row visibleRow = sheet.createRow(1);
        
        // Schema columns
        hiddenRow.createCell(0).setCellValue("FACILITY_NAME");
        hiddenRow.createCell(1).setCellValue("FACILITY_TYPE");
        visibleRow.createCell(0).setCellValue("Facility Name");
        visibleRow.createCell(1).setCellValue("Facility Type");
        
        // Hierarchical boundary columns
        hiddenRow.createCell(2).setCellValue("ADMIN_DISTRICT");
        hiddenRow.createCell(3).setCellValue("ADMIN_VILLAGE");
        hiddenRow.createCell(4).setCellValue("ADMIN_ZONE");
        hiddenRow.createCell(5).setCellValue("HCM_ADMIN_CONSOLE_BOUNDARY_CODE");
        
        visibleRow.createCell(2).setCellValue("District Level");
        visibleRow.createCell(3).setCellValue("Village Level");
        visibleRow.createCell(4).setCellValue("Zone Level");
        visibleRow.createCell(5).setCellValue("Boundary Code");
        
        // Add cascading data validations
        DataValidationHelper dvHelper = sheet.getDataValidationHelper();
        
        // First column (District) - uses Level2Boundaries
        DataValidationConstraint col1Constraint = dvHelper.createFormulaListConstraint("Level2Boundaries");
        CellRangeAddressList col1Addr = new CellRangeAddressList(2, 49, 2, 2);
        DataValidation col1Validation = dvHelper.createValidation(col1Constraint, col1Addr);
        col1Validation.setShowPromptBox(true);
        col1Validation.createPromptBox("Select District", "Choose a district from the dropdown");
        sheet.addValidationData(col1Validation);
        
        // Second column (Village) - cascades from first
        String col2Formula = "IF(C3=\"\",\"\",INDIRECT(VLOOKUP(C3,BoundaryLookup,2,FALSE)))";
        DataValidationConstraint col2Constraint = dvHelper.createFormulaListConstraint(col2Formula);
        CellRangeAddressList col2Addr = new CellRangeAddressList(2, 49, 3, 3);
        DataValidation col2Validation = dvHelper.createValidation(col2Constraint, col2Addr);
        col2Validation.setShowPromptBox(true);
        col2Validation.createPromptBox("Select Village", "Choose a village from the dropdown");
        sheet.addValidationData(col2Validation);
        
        // Third column (Zone) - cascades from second
        String col3Formula = "IF(D3=\"\",\"\",INDIRECT(VLOOKUP(D3,BoundaryLookup,2,FALSE)))";
        DataValidationConstraint col3Constraint = dvHelper.createFormulaListConstraint(col3Formula);
        CellRangeAddressList col3Addr = new CellRangeAddressList(2, 49, 4, 4);
        DataValidation col3Validation = dvHelper.createValidation(col3Constraint, col3Addr);
        col3Validation.setShowPromptBox(true);
        col3Validation.createPromptBox("Select Zone", "Choose a zone from the dropdown");
        sheet.addValidationData(col3Validation);
        
        // Add dynamic boundary code formulas (rows 3-50)
        for (int row = 2; row < 50; row++) {
            Row dataRow = sheet.createRow(row);
            Cell codeCell = dataRow.createCell(5);
            // Check from rightmost to leftmost for the last selected level
            String formula = "IF(E" + (row + 1) + "<>\"\",VLOOKUP(E" + (row + 1) + ",BoundaryCodeMap,2,FALSE)," +
                           "IF(D" + (row + 1) + "<>\"\",VLOOKUP(D" + (row + 1) + ",BoundaryCodeMap,2,FALSE)," +
                           "IF(C" + (row + 1) + "<>\"\",VLOOKUP(C" + (row + 1) + ",BoundaryCodeMap,2,FALSE),\"\")))";
            codeCell.setCellFormula(formula);
        }
        
        // Set column widths
        sheet.setColumnWidth(0, 25 * 256); // Facility Name
        sheet.setColumnWidth(1, 20 * 256); // Facility Type
        sheet.setColumnWidth(2, 25 * 256); // District
        sheet.setColumnWidth(3, 25 * 256); // Village
        sheet.setColumnWidth(4, 25 * 256); // Zone
        sheet.setColumnWidth(5, 20 * 256); // Code
        
        // Hide code column
        sheet.setColumnHidden(5, true);
        
        // Create named ranges
        if (workbook.getName("Level2Boundaries") == null) {
            Name level2Range = workbook.createName();
            level2Range.setNameName("Level2Boundaries");
            level2Range.setRefersToFormula("_h_Level2Boundaries_h_!$A$1:$A$10");
        }
        
        if (workbook.getName("BoundaryLookup") == null) {
            Name lookupRange = workbook.createName();
            lookupRange.setNameName("BoundaryLookup");
            lookupRange.setRefersToFormula("_h_BoundaryLookup_h_!$A$1:$B$20");
        }
        
        if (workbook.getName("BoundaryCodeMap") == null) {
            Name codeMapRange = workbook.createName();
            codeMapRange.setNameName("BoundaryCodeMap");
            codeMapRange.setRefersToFormula("_h_BoundaryCodeMap_h_!$A$1:$B$20");
        }
    }

    // Helper sheet creation methods

    private static void createBoundariesHelperSheet(XSSFWorkbook workbook) {
        if (workbook.getSheet("_h_Boundaries_h_") != null) return;
        
        Sheet sheet = workbook.createSheet("_h_Boundaries_h_");
        
        // Headers
        Row headerRow = sheet.createRow(0);
        headerRow.createCell(0).setCellValue("District Level");
        headerRow.createCell(1).setCellValue("Village Level");
        headerRow.createCell(2).setCellValue("Zone Level");
        
        // Sample data
        String[] districts = {"District Alpha", "District Beta", "District Gamma"};
        String[] villages = {"Village Alpha 1 (District Alpha)", "Village Alpha 2 (District Alpha)", 
                           "Village Beta 1 (District Beta)", "Village Gamma 1 (District Gamma)"};
        String[] zones = {"Zone A1-1 (Village Alpha 1)", "Zone A1-2 (Village Alpha 1)", 
                         "Zone A2-1 (Village Alpha 2)", "Zone B1-1 (Village Beta 1)"};
        
        for (int i = 0; i < Math.max(districts.length, Math.max(villages.length, zones.length)); i++) {
            Row dataRow = sheet.createRow(i + 1);
            if (i < districts.length) dataRow.createCell(0).setCellValue(districts[i]);
            if (i < villages.length) dataRow.createCell(1).setCellValue(villages[i]);
            if (i < zones.length) dataRow.createCell(2).setCellValue(zones[i]);
        }
        
        workbook.setSheetHidden(workbook.getSheetIndex("_h_Boundaries_h_"), true);
    }

    private static void createCascadingHelperSheet(XSSFWorkbook workbook) {
        if (workbook.getSheet("_h_CascadingBoundaries_h_") != null) return;
        
        Sheet sheet = workbook.createSheet("_h_CascadingBoundaries_h_");
        
        // Create parent-children columns
        Row headerRow = sheet.createRow(0);
        headerRow.createCell(0).setCellValue("District Alpha");
        headerRow.createCell(1).setCellValue("District Beta");
        headerRow.createCell(2).setCellValue("Village Alpha 1");
        
        // Children data
        sheet.createRow(1).createCell(0).setCellValue("Village Alpha 1");
        sheet.createRow(2).createCell(0).setCellValue("Village Alpha 2");
        sheet.createRow(1).createCell(1).setCellValue("Village Beta 1");
        sheet.createRow(1).createCell(2).setCellValue("Zone A1-1");
        sheet.createRow(2).createCell(2).setCellValue("Zone A1-2");
        
        workbook.setSheetHidden(workbook.getSheetIndex("_h_CascadingBoundaries_h_"), true);
    }

    private static void createLevel2HelperSheet(XSSFWorkbook workbook) {
        if (workbook.getSheet("_h_Level2Boundaries_h_") != null) return;
        
        Sheet sheet = workbook.createSheet("_h_Level2Boundaries_h_");
        
        // Level 2 boundaries (districts)
        sheet.createRow(0).createCell(0).setCellValue("District Alpha");
        sheet.createRow(1).createCell(0).setCellValue("District Beta");
        sheet.createRow(2).createCell(0).setCellValue("District Gamma");
        
        workbook.setSheetHidden(workbook.getSheetIndex("_h_Level2Boundaries_h_"), true);
    }

    private static void createLookupHelperSheet(XSSFWorkbook workbook) {
        if (workbook.getSheet("_h_BoundaryLookup_h_") != null) return;
        
        Sheet sheet = workbook.createSheet("_h_BoundaryLookup_h_");
        
        // Header
        Row headerRow = sheet.createRow(0);
        headerRow.createCell(0).setCellValue("DisplayName");
        headerRow.createCell(1).setCellValue("SanitizedName");
        
        // Lookup data for INDIRECT formulas
        String[][] lookupData = {
            {"District Alpha", "District_Alpha"},
            {"District Beta", "District_Beta"},
            {"Village Alpha 1", "Village_Alpha_1"},
            {"Village Alpha 2", "Village_Alpha_2"},
            {"Village Beta 1", "Village_Beta_1"}
        };
        
        for (int i = 0; i < lookupData.length; i++) {
            Row dataRow = sheet.createRow(i + 1);
            dataRow.createCell(0).setCellValue(lookupData[i][0]);
            dataRow.createCell(1).setCellValue(lookupData[i][1]);
        }
        
        workbook.setSheetHidden(workbook.getSheetIndex("_h_BoundaryLookup_h_"), true);
    }

    private static void createCodeMapHelperSheet(XSSFWorkbook workbook) {
        if (workbook.getSheet("_h_BoundaryCodeMap_h_") != null) return;
        
        Sheet sheet = workbook.createSheet("_h_BoundaryCodeMap_h_");
        
        // Header
        Row headerRow = sheet.createRow(0);
        headerRow.createCell(0).setCellValue("BoundaryName");
        headerRow.createCell(1).setCellValue("BoundaryCode");
        
        // Boundary code mapping data
        String[][] codeData = {
            {"District Alpha", "DISTRICT_A"},
            {"District Beta", "DISTRICT_B"},
            {"District Gamma", "DISTRICT_C"},
            {"Village Alpha 1 (District Alpha)", "VILLAGE_A1"},
            {"Village Alpha 2 (District Alpha)", "VILLAGE_A2"},
            {"Village Beta 1 (District Beta)", "VILLAGE_B1"},
            {"Village Gamma 1 (District Gamma)", "VILLAGE_C1"},
            {"Zone A1-1 (Village Alpha 1)", "ZONE_A1_1"},
            {"Zone A1-2 (Village Alpha 1)", "ZONE_A1_2"},
            {"Zone A2-1 (Village Alpha 2)", "ZONE_A2_1"},
            {"Zone B1-1 (Village Beta 1)", "ZONE_B1_1"}
        };
        
        for (int i = 0; i < codeData.length; i++) {
            Row dataRow = sheet.createRow(i + 1);
            dataRow.createCell(0).setCellValue(codeData[i][0]);
            dataRow.createCell(1).setCellValue(codeData[i][1]);
        }
        
        workbook.setSheetHidden(workbook.getSheetIndex("_h_BoundaryCodeMap_h_"), true);
    }
}