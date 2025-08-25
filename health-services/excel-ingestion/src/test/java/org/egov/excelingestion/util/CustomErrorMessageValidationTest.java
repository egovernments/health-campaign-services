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
        
        // Verify validations were applied
        Sheet sheet = workbook.getSheetAt(0);
        List<? extends DataValidation> validations = sheet.getDataValidations();
        
        assertEquals(4, validations.size(), "Should have 4 validations");
        
        // Check that custom error messages are used
        boolean foundAgeError = false;
        boolean foundIncomeError = false;
        boolean foundScoreError = false;
        boolean foundPercentageError = false;
        
        for (DataValidation validation : validations) {
            String errorText = validation.getErrorBoxText();
            log.info("Validation error message: {}", errorText);
            
            if (errorText.contains("Age must be between 18 and 65 years for eligibility")) {
                foundAgeError = true;
            } else if (errorText.contains("Minimum income requirement is Rs. 10,000")) {
                foundIncomeError = true;
            } else if (errorText.contains("Value must be at most 100")) { // Dynamic message
                foundScoreError = true;
            } else if (errorText.contains("Please enter a valid percentage between 0 and 100")) {
                foundPercentageError = true;
            }
        }
        
        assertTrue(foundAgeError, "Should have custom age error message");
        assertTrue(foundIncomeError, "Should have custom income error message");
        assertTrue(foundScoreError, "Should have dynamic score error message (no custom message)");
        assertTrue(foundPercentageError, "Should have custom percentage error message");
        
        // Save Excel file for manual verification
        try (FileOutputStream fos = new FileOutputStream("/tmp/custom_error_message_test.xlsx")) {
            workbook.write(fos);
            log.info("✅ Excel file saved to /tmp/custom_error_message_test.xlsx");
            log.info("   Try entering invalid values to see custom error messages:");
            log.info("   - Age: Enter 17 to see 'Age must be between 18 and 65 years for eligibility'");
            log.info("   - Income: Enter 5000 to see 'Minimum income requirement is Rs. 10,000'");
            log.info("   - Score: Enter 101 to see 'Value must be at most 100' (dynamic)");
            log.info("   - Percentage: Enter 150 to see 'Please enter a valid percentage between 0 and 100'");
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