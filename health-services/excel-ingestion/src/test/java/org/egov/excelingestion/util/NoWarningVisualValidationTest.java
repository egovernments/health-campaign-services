package org.egov.excelingestion.util;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.egov.excelingestion.config.ExcelIngestionConfig;
import org.egov.excelingestion.web.models.excel.ColumnDef;
import org.junit.jupiter.api.Test;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test to verify pure visual validation with no warnings during data entry
 */
@Slf4j
class NoWarningVisualValidationTest {

    @Test
    void testNoWarningsOnDataEntry() throws IOException {
        log.info("🧪 Testing that no warnings appear during data entry - pure visual validation");
        
        // Set up ExcelDataPopulator
        ExcelIngestionConfig config = new ExcelIngestionConfig();
        config.setExcelRowLimit(1000);
        
        ExcelStyleHelper styleHelper = new ExcelStyleHelper();
        CellProtectionManager protectionManager = new CellProtectionManager(config, styleHelper);
        ExcelDataPopulator populator = new ExcelDataPopulator(config, styleHelper, protectionManager);
        
        // Create columns that would previously show warnings
        List<ColumnDef> columns = Arrays.asList(
            createNumberColumn("age", 18.0, 65.0),
            createTextColumn("name", 2, 50),
            createDropdownColumn("status", Arrays.asList("ACTIVE", "INACTIVE", "PENDING"))
        );
        
        // Create localization map with Hindi translations
        Map<String, String> localizationMap = new HashMap<>();
        localizationMap.put("HCM_VALIDATION_NUMBER_BETWEEN", "मान %s और %s के बीच होना चाहिए");
        localizationMap.put("HCM_VALIDATION_TEXT_LENGTH_BETWEEN", "पाठ की लंबाई %s और %s वर्णों के बीच होनी चाहिए");
        
        // Generate Excel workbook
        Workbook workbook = populator.populateSheetWithData("NoWarningTest", columns, null, localizationMap);
        
        // Get sheet and check validations
        Sheet sheet = workbook.getSheetAt(0);
        List<? extends DataValidation> validations = sheet.getDataValidations();
        
        log.info("Found {} data validations", validations.size());
        
        // Count validation styles
        int stopStyleCount = 0;
        int warningStyleCount = 0;
        int infoStyleCount = 0;
        
        for (DataValidation validation : validations) {
            int styleValue = validation.getErrorStyle();
            log.info("Validation style value: {} for range: {}", styleValue, validation.getRegions());
            
            if (styleValue == DataValidation.ErrorStyle.STOP) {
                stopStyleCount++;
            } else if (styleValue == DataValidation.ErrorStyle.WARNING) {
                warningStyleCount++;
            } else if (styleValue == DataValidation.ErrorStyle.INFO) {
                infoStyleCount++;
            }
        }
        
        log.info("Validation style counts - STOP: {}, WARNING: {}, INFO: {}", stopStyleCount, warningStyleCount, infoStyleCount);
        
        // Should only have STOP style for dropdowns (if any)
        // Number and string validations should NOT be present as data validations
        assertTrue(warningStyleCount == 0, "Should have no WARNING style validations (number/text should be visual-only)");
        
        // Check conditional formatting rules
        SheetConditionalFormatting conditionalFormatting = sheet.getSheetConditionalFormatting();
        int numConditionalFormattings = conditionalFormatting.getNumConditionalFormattings();
        log.info("Found {} conditional formatting rules", numConditionalFormattings);
        
        // Should have conditional formatting for number and text fields
        assertTrue(numConditionalFormattings >= 2, "Should have conditional formatting rules for number and text validation");
        
        // Verify conditional formatting targets correct columns
        boolean hasNumberFormatting = false;
        boolean hasTextFormatting = false;
        
        for (int i = 0; i < numConditionalFormattings; i++) {
            ConditionalFormatting cf = conditionalFormatting.getConditionalFormattingAt(i);
            CellRangeAddress[] ranges = cf.getFormattingRanges();
            for (CellRangeAddress range : ranges) {
                log.info("Conditional formatting range: {}", range.formatAsString());
                
                // Check if it's for age column (column 0)
                if (range.getFirstColumn() == 0 && range.getLastColumn() == 0) {
                    hasNumberFormatting = true;
                }
                
                // Check if it's for name column (column 1) 
                if (range.getFirstColumn() == 1 && range.getLastColumn() == 1) {
                    hasTextFormatting = true;
                }
            }
        }
        
        assertTrue(hasNumberFormatting, "Should have conditional formatting for number column (age)");
        assertTrue(hasTextFormatting, "Should have conditional formatting for text column (name)");
        
        // Check cell comments for tooltips
        Row headerRow = sheet.getRow(1);
        if (headerRow != null) {
            Cell ageHeaderCell = headerRow.getCell(0);
            Cell nameHeaderCell = headerRow.getCell(1);
            
            if (ageHeaderCell != null && ageHeaderCell.getCellComment() != null) {
                log.info("Age column comment: {}", ageHeaderCell.getCellComment().getString().getString());
            }
            
            if (nameHeaderCell != null && nameHeaderCell.getCellComment() != null) {
                log.info("Name column comment: {}", nameHeaderCell.getCellComment().getString().getString());
            }
        }
        
        // Save Excel file for manual verification
        try (FileOutputStream fos = new FileOutputStream("/tmp/no_warning_visual_validation_test.xlsx")) {
            workbook.write(fos);
            log.info("✅ Excel file saved to /tmp/no_warning_visual_validation_test.xlsx");
            log.info("   Manual verification instructions:");
            log.info("   1. Open the Excel file");
            log.info("   2. Try entering INVALID data:");
            log.info("      - Age: Enter 100 (outside 18-65 range)");
            log.info("      - Name: Enter 'A' (less than 2 characters)");
            log.info("   3. Verify NO warnings/prompts appear during entry");
            log.info("   4. Verify cell turns rose/red after entering invalid data");
            log.info("   5. Verify hovering over header shows validation rules");
            log.info("   6. For dropdown, validation should still work as before (blocking)");
        }
        
        workbook.close();
        
        log.info("✅ No warning visual validation test completed successfully!");
    }

    @Test
    void testVisualValidationWithLocalizedMessages() throws IOException {
        log.info("🧪 Testing visual validation uses localized error messages");
        
        // Set up ExcelDataPopulator
        ExcelIngestionConfig config = new ExcelIngestionConfig();
        config.setExcelRowLimit(1000);
        
        ExcelStyleHelper styleHelper = new ExcelStyleHelper();
        CellProtectionManager protectionManager = new CellProtectionManager(config, styleHelper);
        ExcelDataPopulator populator = new ExcelDataPopulator(config, styleHelper, protectionManager);
        
        // Create columns with custom error messages
        List<ColumnDef> columns = Arrays.asList(
            createColumnWithCustomError("age", 18.0, 65.0, "CUSTOM_AGE_ERROR"),
            createColumnWithCustomError("salary", 10000.0, null, "CUSTOM_SALARY_ERROR")
        );
        
        // Create localization map
        Map<String, String> localizationMap = new HashMap<>();
        localizationMap.put("CUSTOM_AGE_ERROR", "आयु 18-65 वर्ष के बीच होनी चाहिए"); // Hindi: Age should be between 18-65 years
        localizationMap.put("CUSTOM_SALARY_ERROR", "न्यूनतम वेतन ₹10,000 होना चाहिए"); // Hindi: Minimum salary should be ₹10,000
        
        // Generate Excel workbook
        Workbook workbook = populator.populateSheetWithData("LocalizedVisualTest", columns, null, localizationMap);
        
        // Check comments contain localized messages
        Sheet sheet = workbook.getSheetAt(0);
        Row headerRow = sheet.getRow(1);
        
        if (headerRow != null) {
            Cell ageCell = headerRow.getCell(0);
            Cell salaryCell = headerRow.getCell(1);
            
            if (ageCell != null && ageCell.getCellComment() != null) {
                String ageComment = ageCell.getCellComment().getString().getString();
                log.info("Age cell comment: {}", ageComment);
                assertTrue(ageComment.contains("आयु 18-65 वर्ष के बीच होनी चाहिए"), 
                    "Age comment should contain localized Hindi message");
            }
            
            if (salaryCell != null && salaryCell.getCellComment() != null) {
                String salaryComment = salaryCell.getCellComment().getString().getString();
                log.info("Salary cell comment: {}", salaryComment);
                assertTrue(salaryComment.contains("न्यूनतम वेतन ₹10,000 होना चाहिए"), 
                    "Salary comment should contain localized Hindi message");
            }
        }
        
        workbook.close();
        log.info("✅ Visual validation with localized messages test completed!");
    }
    
    // Helper methods
    
    private ColumnDef createNumberColumn(String name, Double min, Double max) {
        ColumnDef column = new ColumnDef();
        column.setName(name);
        column.setType("number");
        column.setMinimum(min);
        column.setMaximum(max);
        return column;
    }
    
    private ColumnDef createTextColumn(String name, Integer minLength, Integer maxLength) {
        ColumnDef column = new ColumnDef();
        column.setName(name);
        column.setType("string");
        column.setMinLength(minLength);
        column.setMaxLength(maxLength);
        return column;
    }
    
    private ColumnDef createDropdownColumn(String name, List<String> enumValues) {
        ColumnDef column = new ColumnDef();
        column.setName(name);
        column.setType("enum");
        column.setEnumValues(enumValues);
        return column;
    }
    
    private ColumnDef createColumnWithCustomError(String name, Double min, Double max, String errorMessage) {
        ColumnDef column = new ColumnDef();
        column.setName(name);
        column.setType("number");
        column.setMinimum(min);
        column.setMaximum(max);
        column.setErrorMessage(errorMessage);
        return column;
    }
}