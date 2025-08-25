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
 * Integration test to verify MDMS number validation works end-to-end
 * Creates real Excel files with in-sheet validation
 */
@Slf4j
class MDMSValidationIntegrationTest {

    @Test
    void testCreateExcelWithMDMSNumberValidation() throws IOException {
        log.info("🧪 Creating Excel file with MDMS number validation");
        
        // Set up ExcelDataPopulator
        ExcelIngestionConfig config = new ExcelIngestionConfig();
        config.setExcelRowLimit(1000);
        
        ExcelStyleHelper styleHelper = new ExcelStyleHelper();
        CellProtectionManager protectionManager = new CellProtectionManager(config, styleHelper);
        ExcelDataPopulator populator = new ExcelDataPopulator(config, styleHelper, protectionManager);
        
        // Create columns with MANUALLY SET minimum/maximum values
        List<ColumnDef> columns = Arrays.asList(
            createTestColumn("age", "number", 18.0, 65.0),      // Age between 18-65
            createTestColumn("score", "number", null, 100.0),   // Score max 100
            createTestColumn("salary", "number", 10000.0, null), // Salary min 10000
            createTestColumn("name", "string", null, null)       // String (no validation)
        );
        
        log.info("Created columns:");
        for (ColumnDef col : columns) {
            log.info("  - {}: type={}, min={}, max={}", 
                col.getName(), col.getType(), col.getMinimum(), col.getMaximum());
        }
        
        // Generate Excel workbook
        Workbook workbook = populator.populateSheetWithData("TestSheet", columns, null);
        
        // Verify validations were applied
        Sheet sheet = workbook.getSheetAt(0);
        List<? extends DataValidation> validations = sheet.getDataValidations();
        
        log.info("✅ Excel sheet created with {} data validations", validations.size());
        
        // Log validation details
        for (int i = 0; i < validations.size(); i++) {
            DataValidation validation = validations.get(i);
            log.info("Validation {}: Type={}, Formula={}, Error={}", 
                i+1, 
                validation.getValidationConstraint().getValidationType(),
                validation.getValidationConstraint().getFormula1(),
                validation.getErrorBoxText());
        }
        
        // Should have 3 validations (age, score, salary)
        assertEquals(3, validations.size(), "Should have 3 number validations");
        
        // Save Excel file for manual verification
        try (FileOutputStream fos = new FileOutputStream("/tmp/mdms_validation_test.xlsx")) {
            workbook.write(fos);
            log.info("✅ Excel file saved to /tmp/mdms_validation_test.xlsx for manual testing");
            log.info("   Open the file and try entering values outside the ranges:");
            log.info("   - Age: Should accept 18-65, reject 17 or 66");
            log.info("   - Score: Should accept 0-100, reject 101");
            log.info("   - Salary: Should accept 10000+, reject 9999");
        }
        
        workbook.close();
        
        log.info("✅ MDMS validation integration test completed successfully!");
    }
    
    /**
     * Helper to create a test column with validation properties
     */
    private ColumnDef createTestColumn(String name, String type, Double min, Double max) {
        ColumnDef column = new ColumnDef();
        column.setName(name);
        column.setType(type);
        column.setMinimum(min);
        column.setMaximum(max);
        
        log.info("Created column: {} (type={}, min={}, max={})", name, type, min, max);
        return column;
    }
}