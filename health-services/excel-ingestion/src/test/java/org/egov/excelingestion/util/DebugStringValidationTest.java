package org.egov.excelingestion.util;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.egov.excelingestion.config.ExcelIngestionConfig;
import org.egov.excelingestion.web.models.excel.ColumnDef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * Debug test to create an actual Excel file and examine string validations
 */
@Slf4j
public class DebugStringValidationTest {

    private ExcelDataPopulator excelDataPopulator;
    private ExcelIngestionConfig config;

    @BeforeEach
    void setUp() {
        config = new ExcelIngestionConfig();
        config.setExcelRowLimit(1000);
        config.setExcelSheetPassword("password123");
        
        ExcelStyleHelper excelStyleHelper = new ExcelStyleHelper();
        CellProtectionManager cellProtectionManager = new CellProtectionManager(config, excelStyleHelper);
        
        excelDataPopulator = new ExcelDataPopulator(config, excelStyleHelper, cellProtectionManager);
    }

    @Test
    void createDebugExcelWithStringValidations() throws IOException {
        log.info("🧪 Creating debug Excel file with string validations");
        
        // Create columns with string validation properties
        List<ColumnDef> columns = Arrays.asList(
            createStringColumnWithLength("name", 3, 50),        // Min-Max length validation
            createStringColumnWithLength("email", 5, null),     // Min only validation  
            createStringColumnWithLength("code", null, 10),     // Max only validation
            createStringColumnWithPattern("phone", "^\\d{10}$") // Pattern validation (should be processing-only)
        );
        
        // Generate Excel workbook with validation
        Workbook workbook = excelDataPopulator.populateSheetWithData(
            "StringValidationDebug", columns, null);
        
        // Save the workbook to a file for manual inspection
        try (FileOutputStream fileOut = new FileOutputStream("/tmp/debug_string_validation.xlsx")) {
            workbook.write(fileOut);
            log.info("✅ Debug Excel file saved to: /tmp/debug_string_validation.xlsx");
        }
        
        // Verify validations in the sheet
        Sheet sheet = workbook.getSheetAt(0);
        List<? extends DataValidation> validations = sheet.getDataValidations();
        
        log.info("📊 Total validations found: {}", validations.size());
        
        for (int i = 0; i < validations.size(); i++) {
            DataValidation validation = validations.get(i);
            DataValidationConstraint constraint = validation.getValidationConstraint();
            
            log.info("🔍 Validation {}: Type={}, Formula1={}, Formula2={}, ErrorTitle={}, ErrorText={}", 
                i + 1,
                constraint.getValidationType(),
                constraint.getFormula1(),
                constraint.getFormula2(),
                validation.getErrorBoxTitle(),
                validation.getErrorBoxText()
            );
        }
        
        workbook.close();
        log.info("✅ Debug test completed - check the Excel file manually");
    }
    
    private ColumnDef createStringColumnWithLength(String name, Integer minLength, Integer maxLength) {
        return ColumnDef.builder()
                .name(name)
                .type("string")
                .minLength(minLength)
                .maxLength(maxLength)
                .build();
    }
    
    private ColumnDef createStringColumnWithPattern(String name, String pattern) {
        return ColumnDef.builder()
                .name(name)
                .type("string")
                .pattern(pattern)
                .build();
    }
}