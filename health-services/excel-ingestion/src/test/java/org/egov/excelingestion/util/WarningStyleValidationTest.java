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
 * Test to verify WARNING style validation behavior
 */
@Slf4j
class WarningStyleValidationTest {

    @Test
    void testValidationUsesWarningStyle() throws IOException {
        log.info("🧪 Testing validation uses WARNING style for number and text fields");
        
        // Set up ExcelDataPopulator
        ExcelIngestionConfig config = new ExcelIngestionConfig();
        config.setExcelRowLimit(1000);
        
        ExcelStyleHelper styleHelper = new ExcelStyleHelper();
        CellProtectionManager protectionManager = new CellProtectionManager(config, styleHelper);
        ExcelDataPopulator populator = new ExcelDataPopulator(config, styleHelper, protectionManager);
        
        // Create columns with different validation types
        List<ColumnDef> columns = Arrays.asList(
            createDropdownColumn("status", Arrays.asList("ACTIVE", "INACTIVE", "PENDING")),
            createNumberColumn("age", 18.0, 65.0),
            createTextColumn("name", 2, 50)
        );
        
        // Create localization map
        Map<String, String> localizationMap = new HashMap<>();
        localizationMap.put("HCM_VALIDATION_INVALID_NUMBER", "अमान्य संख्या");
        localizationMap.put("HCM_VALIDATION_INVALID_TEXT_LENGTH", "अमान्य पाठ लंबाई");
        localizationMap.put("HCM_VALIDATION_NUMBER_BETWEEN", "मान %s और %s के बीच होना चाहिए");
        localizationMap.put("HCM_VALIDATION_TEXT_LENGTH_BETWEEN", "पाठ की लंबाई %s और %s वर्णों के बीच होनी चाहिए");
        
        // Generate Excel workbook
        Workbook workbook = populator.populateSheetWithData("WarningStyleTest", columns, null, localizationMap);
        
        // Get sheet and validations
        Sheet sheet = workbook.getSheetAt(0);
        List<? extends DataValidation> validations = sheet.getDataValidations();
        
        assertEquals(3, validations.size(), "Should have 3 validations (dropdown, number, text)");
        
        // Check validation styles
        int dropdownCount = 0;
        int warningCount = 0;
        
        for (DataValidation validation : validations) {
            if (validation.getErrorStyle() == DataValidation.ErrorStyle.STOP) {
                dropdownCount++;
                log.info("Found STOP style validation (dropdown)");
            } else if (validation.getErrorStyle() == DataValidation.ErrorStyle.WARNING) {
                warningCount++;
                log.info("Found WARNING style validation (number/text)");
                assertTrue(validation.getShowErrorBox(), "WARNING validation should show error box on hover");
            }
        }
        
        assertEquals(1, dropdownCount, "Should have 1 STOP style validation for dropdown");
        assertEquals(2, warningCount, "Should have 2 WARNING style validations for number and text");
        
        // Save Excel file for manual verification
        try (FileOutputStream fos = new FileOutputStream("/tmp/warning_style_validation_test.xlsx")) {
            workbook.write(fos);
            log.info("✅ Excel file saved to /tmp/warning_style_validation_test.xlsx");
            log.info("   Manual test instructions:");
            log.info("   1. Open the Excel file");
            log.info("   2. Try entering invalid data:");
            log.info("      - Age: Enter 100 (outside 18-65 range)");
            log.info("      - Name: Enter 'A' (less than 2 characters)");
            log.info("   3. Verify:");
            log.info("      - Data is accepted (not blocked)");
            log.info("      - Cell shows warning indicator (triangle)");
            log.info("      - Hover shows localized error message");
            log.info("   4. For dropdown, invalid values should still be blocked");
        }
        
        workbook.close();
        
        log.info("✅ WARNING style validation test completed successfully!");
    }
    
    // Helper methods
    
    private ColumnDef createDropdownColumn(String name, List<String> enumValues) {
        ColumnDef column = new ColumnDef();
        column.setName(name);
        column.setType("enum");
        column.setEnumValues(enumValues);
        return column;
    }
    
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
}