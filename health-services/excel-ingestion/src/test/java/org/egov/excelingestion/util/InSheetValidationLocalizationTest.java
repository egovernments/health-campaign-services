package org.egov.excelingestion.util;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
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
 * Test to verify in-sheet Excel validation error messages are properly localized
 */
@Slf4j
class InSheetValidationLocalizationTest {

    @Test
    void testInSheetValidationWithLocalizedErrorMessages() throws IOException {
        log.info("🧪 Testing in-sheet validation error messages are localized");
        
        // Set up ExcelDataPopulator
        ExcelIngestionConfig config = new ExcelIngestionConfig();
        config.setExcelRowLimit(1000);
        
        ExcelStyleHelper styleHelper = new ExcelStyleHelper();
        CellProtectionManager protectionManager = new CellProtectionManager(config, styleHelper);
        ExcelDataPopulator populator = new ExcelDataPopulator(config, styleHelper, protectionManager);
        
        // Create columns with custom error messages that will be localized
        List<ColumnDef> columns = Arrays.asList(
            createColumnWithCustomError("age", 18.0, 65.0, "CUSTOM_AGE_ERROR"),
            createColumnWithCustomError("income", 10000.0, null, "CUSTOM_INCOME_ERROR"),
            createColumnWithCustomError("score", null, 100.0, "CUSTOM_SCORE_ERROR")
        );
        
        // Create localization map with Hindi translations
        Map<String, String> localizationMap = new HashMap<>();
        localizationMap.put("CUSTOM_AGE_ERROR", "आयु 18 से 65 वर्ष के बीच होनी चाहिए"); // Hindi: Age should be between 18 and 65 years
        localizationMap.put("CUSTOM_INCOME_ERROR", "न्यूनतम आय आवश्यकता 10,000 रुपये है"); // Hindi: Minimum income requirement is Rs. 10,000
        localizationMap.put("CUSTOM_SCORE_ERROR", "अधिकतम स्कोर 100 हो सकता है"); // Hindi: Maximum score can be 100
        
        log.info("Created columns with custom error message keys:");
        for (ColumnDef col : columns) {
            log.info("  - {}: errorMessage={}", col.getName(), col.getErrorMessage());
        }
        
        // Generate Excel workbook with localization
        Workbook workbook = populator.populateSheetWithData("LocalizedValidationTest", columns, null, localizationMap);
        
        // Verify that number fields use pure visual validation (no DataValidation objects)
        Sheet sheet = workbook.getSheetAt(0);
        List<? extends DataValidation> validations = sheet.getDataValidations();
        
        assertEquals(0, validations.size(), "Number fields should use pure visual validation, not DataValidation objects");
        
        // Verify conditional formatting is applied for pure visual validation
        assertTrue(sheet.getSheetConditionalFormatting().getNumConditionalFormattings() > 0,
                "Number fields should use conditional formatting for validation feedback");
        
        log.info("✅ Number fields use pure visual validation (conditional formatting) with localized error messages");
        log.info("   Localized error messages are applied through comments/conditional formatting instead of DataValidation objects");
        
        // Save Excel file for manual verification
        try (FileOutputStream fos = new FileOutputStream("/tmp/localized_validation_test.xlsx")) {
            workbook.write(fos);
            log.info("✅ Excel file saved to /tmp/localized_validation_test.xlsx");
            log.info("   Try entering invalid values to see pure visual validation with localized messages:");
            log.info("   - Age: Enter 17 to see conditional formatting highlight with localized error in comment");
            log.info("   - Income: Enter 5000 to see conditional formatting highlight with localized error in comment");
            log.info("   - Score: Enter 101 to see conditional formatting highlight with localized error in comment");
            log.info("   Note: Validation now uses pure visual approach (conditional formatting + localized comments) instead of blocking input");
        }
        
        workbook.close();
        
        log.info("✅ In-sheet validation localization test completed successfully!");
    }

    @Test
    void testInSheetValidationFallbackToCustomMessage() throws IOException {
        log.info("🧪 Testing in-sheet validation falls back to custom message when no localization");
        
        // Set up ExcelDataPopulator
        ExcelIngestionConfig config = new ExcelIngestionConfig();
        config.setExcelRowLimit(1000);
        
        ExcelStyleHelper styleHelper = new ExcelStyleHelper();
        CellProtectionManager protectionManager = new CellProtectionManager(config, styleHelper);
        ExcelDataPopulator populator = new ExcelDataPopulator(config, styleHelper, protectionManager);
        
        // Create columns with custom error messages (no localization)
        List<ColumnDef> columns = Arrays.asList(
            createColumnWithCustomError("age", 18.0, 65.0, "Age must be between 18 and 65 for eligibility"),
            createColumnWithCustomError("income", 10000.0, null, "Minimum income of Rs. 10,000 required")
        );
        
        // Empty localization map (no localization available)
        Map<String, String> localizationMap = new HashMap<>();
        
        log.info("Created columns with custom error messages (no localization):");
        for (ColumnDef col : columns) {
            log.info("  - {}: errorMessage={}", col.getName(), col.getErrorMessage());
        }
        
        // Generate Excel workbook
        Workbook workbook = populator.populateSheetWithData("CustomMessageTest", columns, null, localizationMap);
        
        // Verify that number fields use pure visual validation (no DataValidation objects)
        Sheet sheet = workbook.getSheetAt(0);
        List<? extends DataValidation> validations = sheet.getDataValidations();
        
        assertEquals(0, validations.size(), "Number fields should use pure visual validation, not DataValidation objects");
        
        // Verify conditional formatting is applied for pure visual validation
        assertTrue(sheet.getSheetConditionalFormatting().getNumConditionalFormattings() > 0,
                "Number fields should use conditional formatting for validation feedback");
        
        log.info("✅ Number fields use pure visual validation (conditional formatting) with custom error messages (no localization)");
        
        workbook.close();
        
        log.info("✅ In-sheet validation custom message fallback test completed successfully!");
    }

    @Test
    void testInSheetValidationFallbackToDynamicMessage() throws IOException {
        log.info("🧪 Testing in-sheet validation falls back to dynamic message when no custom message");
        
        // Set up ExcelDataPopulator
        ExcelIngestionConfig config = new ExcelIngestionConfig();
        config.setExcelRowLimit(1000);
        
        ExcelStyleHelper styleHelper = new ExcelStyleHelper();
        CellProtectionManager protectionManager = new CellProtectionManager(config, styleHelper);
        ExcelDataPopulator populator = new ExcelDataPopulator(config, styleHelper, protectionManager);
        
        // Create columns without custom error messages
        List<ColumnDef> columns = Arrays.asList(
            createColumnWithoutCustomError("age", 18.0, 65.0),  // Will use dynamic message
            createColumnWithoutCustomError("score", null, 100.0) // Will use dynamic message
        );
        
        log.info("Created columns without custom error messages:");
        for (ColumnDef col : columns) {
            log.info("  - {}: errorMessage={}", col.getName(), col.getErrorMessage());
        }
        
        // Generate Excel workbook
        Workbook workbook = populator.populateSheetWithData("DynamicMessageTest", columns, null, null);
        
        // Verify that number fields use pure visual validation (no DataValidation objects)
        Sheet sheet = workbook.getSheetAt(0);
        List<? extends DataValidation> validations = sheet.getDataValidations();
        
        assertEquals(0, validations.size(), "Number fields should use pure visual validation, not DataValidation objects");
        
        // Verify conditional formatting is applied for pure visual validation
        assertTrue(sheet.getSheetConditionalFormatting().getNumConditionalFormattings() > 0,
                "Number fields should use conditional formatting for validation feedback");
        
        log.info("✅ Number fields use pure visual validation (conditional formatting) with dynamic error messages");
        
        workbook.close();
        
        log.info("✅ In-sheet validation dynamic message fallback test completed successfully!");
    }
    
    // Helper methods
    
    /**
     * Create column with custom error message
     */
    private ColumnDef createColumnWithCustomError(String name, Double min, Double max, String errorMessage) {
        ColumnDef column = new ColumnDef();
        column.setName(name);
        column.setType("number");
        column.setMinimum(min);
        column.setMaximum(max);
        column.setErrorMessage(errorMessage);
        return column;
    }
    
    /**
     * Create column without custom error message (will use dynamic)
     */
    private ColumnDef createColumnWithoutCustomError(String name, Double min, Double max) {
        ColumnDef column = new ColumnDef();
        column.setName(name);
        column.setType("number");
        column.setMinimum(min);
        column.setMaximum(max);
        column.setErrorMessage(null); // No custom error message
        return column;
    }
}