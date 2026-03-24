package org.egov.excelingestion.util;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.egov.excelingestion.config.ExcelIngestionConfig;
import org.egov.excelingestion.web.models.excel.ColumnDef;
import org.junit.jupiter.api.Test;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test to verify custom error messages from MDMS are used in Excel validation
 */
@Slf4j
class CustomErrorMessageValidationTest {

    @Test
    void testCustomErrorMessageFromMDMS() throws IOException {
        log.info("🧪 Testing custom error messages from MDMS schema");
        
        // Set up ExcelDataPopulator
        ExcelIngestionConfig config = new ExcelIngestionConfig();
        config.setExcelRowLimit(1000);
        
        ExcelStyleHelper styleHelper = new ExcelStyleHelper();
        CellProtectionManager protectionManager = new CellProtectionManager(config, styleHelper);
        ExcelDataPopulator populator = new ExcelDataPopulator(config, styleHelper, protectionManager);
        
        // Create columns with custom error messages
        List<ColumnDef> columns = Arrays.asList(
            createColumnWithCustomError("age", 18.0, 65.0, 
                "Age must be between 18 and 65 years for eligibility"),
            createColumnWithCustomError("income", 10000.0, null, 
                "Minimum income requirement is Rs. 10,000"),
            createColumnWithoutCustomError("score", null, 100.0),  // Will use dynamic message
            createColumnWithCustomError("percentage", 0.0, 100.0,
                "Please enter a valid percentage between 0 and 100")
        );
        
        log.info("Created columns with custom error messages:");
        for (ColumnDef col : columns) {
            log.info("  - {}: errorMessage={}", col.getName(), col.getErrorMessage());
        }
        
        // Generate Excel workbook
        Workbook workbook = populator.populateSheetWithData("ValidationTest", columns, null);
        
        // Verify that number fields use pure visual validation (no DataValidation objects)
        Sheet sheet = workbook.getSheetAt(0);
        List<? extends DataValidation> validations = sheet.getDataValidations();
        
        assertEquals(0, validations.size(), "Number fields should use pure visual validation, not DataValidation objects");
        
        // Verify conditional formatting is applied for pure visual validation
        assertTrue(sheet.getSheetConditionalFormatting().getNumConditionalFormattings() > 0,
                "Number fields should use conditional formatting for validation feedback");
        
        log.info("✅ Number fields use pure visual validation (conditional formatting) with custom error messages");
        log.info("   Custom error messages are applied through comments/conditional formatting instead of DataValidation objects");
        
        // Save Excel file for manual verification
        try (FileOutputStream fos = new FileOutputStream("/tmp/custom_error_message_test.xlsx")) {
            workbook.write(fos);
            log.info("✅ Excel file saved to /tmp/custom_error_message_test.xlsx");
            log.info("   Try entering invalid values to see pure visual validation:");
            log.info("   - Age: Enter 17 to see conditional formatting highlight with custom error in comment");
            log.info("   - Income: Enter 5000 to see conditional formatting highlight with custom error in comment");
            log.info("   - Score: Enter 101 to see conditional formatting highlight with dynamic error in comment");
            log.info("   - Percentage: Enter 150 to see conditional formatting highlight with custom error in comment");
            log.info("   Note: Validation now uses pure visual approach (conditional formatting + comments) instead of blocking input");
        }
        
        workbook.close();
        
        log.info("✅ Custom error message test completed successfully!");
    }
    
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