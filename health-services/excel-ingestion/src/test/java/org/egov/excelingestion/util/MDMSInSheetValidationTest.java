package org.egov.excelingestion.util;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.egov.excelingestion.config.ExcelIngestionConfig;
import org.egov.excelingestion.web.models.excel.ColumnDef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test cases for MDMS-based in-sheet Excel validation
 * Tests that minimum/maximum number validation from MDMS schema is applied to Excel sheets
 */
@Slf4j
class MDMSInSheetValidationTest {

    private ExcelDataPopulator excelDataPopulator;
    private ExcelIngestionConfig config;

    @BeforeEach
    void setUp() {
        config = new ExcelIngestionConfig();
        config.setExcelRowLimit(1000);
        
        ExcelStyleHelper styleHelper = new ExcelStyleHelper();
        CellProtectionManager protectionManager = new CellProtectionManager(config, styleHelper);
        
        excelDataPopulator = new ExcelDataPopulator(config, styleHelper, protectionManager);
    }

    @Test
    void testNumberValidationFromMDMSSchema() throws Exception {
        log.info("🧪 Testing MDMS number validation creates Excel in-sheet validation rules");
        
        // Create columns with MDMS number validation properties (min/max only)
        List<ColumnDef> columns = Arrays.asList(
            createNumberColumn("age", 18.0, 65.0),        // Min-Max validation
            createNumberColumn("salary", 10000.0, null),  // Min only validation  
            createNumberColumn("score", null, 100.0)      // Max only validation
        );

        // Generate Excel workbook with validation
        Workbook workbook = excelDataPopulator.populateSheetWithData(
            "ValidationTest", columns, null);

        Sheet sheet = workbook.getSheetAt(0);
        
        // Verify that data validations are applied
        List<? extends DataValidation> validations = sheet.getDataValidations();
        assertFalse(validations.isEmpty(), "Sheet should have data validations");
        
        log.info("✅ Found {} data validations on sheet", validations.size());
        
        // Test each validation
        for (DataValidation validation : validations) {
            DataValidationConstraint constraint = validation.getValidationConstraint();
            
            // Check that constraint is for decimal/number validation
            assertEquals(DataValidationConstraint.ValidationType.DECIMAL, constraint.getValidationType(),
                        "Validation should be for decimal/number type");
            
            // Verify error handling is configured correctly
            assertEquals(DataValidation.ErrorStyle.STOP, validation.getErrorStyle(),
                        "Validation should stop on error");
            assertTrue(validation.getShowErrorBox(), "Error box should be shown");
            assertFalse(validation.getShowPromptBox(), "Prompt box should not be shown");
            
            log.info("✅ Validation: {} - Formula: {}", 
                    validation.getErrorBoxTitle(), constraint.getFormula1());
        }
        
        workbook.close();
        log.info("✅ MDMS number validation test passed");
    }

    @Test
    void testOnlyNumberValidationIsApplied() throws Exception {
        log.info("🧪 Testing only number validation is applied from MDMS, not string");
        
        // Create mixed columns - only number should get validation
        List<ColumnDef> columns = Arrays.asList(
            createNumberColumn("age", 18.0, 65.0),  // Should get validation
            createStringColumn("name"),              // Should NOT get validation
            createEnumColumn("category", Arrays.asList("A", "B", "C"))  // Should get enum validation
        );

        // Generate Excel workbook with validation
        Workbook workbook = excelDataPopulator.populateSheetWithData(
            "MixedValidationTest", columns, null);

        Sheet sheet = workbook.getSheetAt(0);
        
        // Verify that only number and enum validations are applied (not string)
        List<? extends DataValidation> validations = sheet.getDataValidations();
        assertEquals(2, validations.size(), "Should have 2 validations (number + enum only)");
        
        // Check validation types
        boolean hasDecimalValidation = false;
        boolean hasListValidation = false;
        
        for (DataValidation validation : validations) {
            DataValidationConstraint constraint = validation.getValidationConstraint();
            
            if (constraint.getValidationType() == DataValidationConstraint.ValidationType.DECIMAL) {
                hasDecimalValidation = true;
                log.info("✅ Found number validation: {}", constraint.getFormula1());
            } else if (constraint.getValidationType() == DataValidationConstraint.ValidationType.LIST) {
                hasListValidation = true;
                log.info("✅ Found enum validation");
            }
        }
        
        assertTrue(hasDecimalValidation, "Should have decimal validation for numbers");
        assertTrue(hasListValidation, "Should have list validation for enums");
        
        workbook.close();
        log.info("✅ Only number validation test passed");
    }

    @Test
    void testValidationErrorMessages() throws Exception {
        log.info("🧪 Testing validation error messages are correctly configured");
        
        // Create columns with different validation scenarios
        List<ColumnDef> columns = Arrays.asList(
            createNumberColumn("age", 18.0, 65.0),    // Between validation
            createNumberColumn("salary", 10000.0, null), // Minimum only
            createNumberColumn("score", null, 100.0)     // Maximum only
        );

        // Generate Excel workbook
        Workbook workbook = excelDataPopulator.populateSheetWithData(
            "ErrorMessageTest", columns, null);

        Sheet sheet = workbook.getSheetAt(0);
        
        // Verify error messages are set correctly
        List<? extends DataValidation> validations = sheet.getDataValidations();
        assertEquals(3, validations.size(), "Should have 3 validations");
        
        for (DataValidation validation : validations) {
            // Verify error box configuration
            assertTrue(validation.getShowErrorBox(), "Error box should be shown");
            assertFalse(validation.getShowPromptBox(), "Prompt box should NOT be shown");
            assertEquals(DataValidation.ErrorStyle.STOP, validation.getErrorStyle(), 
                        "Should stop on error");
            
            // Check that error title and text are set
            assertNotNull(validation.getErrorBoxTitle(), "Error title should be set");
            assertNotNull(validation.getErrorBoxText(), "Error message should be set");
            
            log.info("✅ Validation error: {} - {}", 
                    validation.getErrorBoxTitle(), validation.getErrorBoxText());
        }
        
        workbook.close();
        log.info("✅ Error message configuration test passed");
    }

    @Test
    void testValidationRangeCoversAllDataRows() throws Exception {
        log.info("🧪 Testing validation range covers all data rows up to limit");
        
        List<ColumnDef> columns = Arrays.asList(
            createNumberColumn("testField", 1.0, 100.0)
        );

        Workbook workbook = excelDataPopulator.populateSheetWithData(
            "RangeTest", columns, null);

        Sheet sheet = workbook.getSheetAt(0);
        List<? extends DataValidation> validations = sheet.getDataValidations();
        
        assertFalse(validations.isEmpty(), "Should have validations");
        
        DataValidation validation = validations.get(0);
        CellRangeAddressList regions = validation.getRegions();
        
        // Validation should cover from row 2 (0-indexed, so row 3 in Excel) to row limit
        assertEquals(1, regions.countRanges(), "Should have one validation range");
        assertEquals(2, regions.getCellRangeAddress(0).getFirstRow(), "Should start from row 3 (0-indexed 2)");
        assertEquals(config.getExcelRowLimit(), regions.getCellRangeAddress(0).getLastRow(), 
                    "Should cover up to row limit");
        
        workbook.close();
        log.info("✅ Validation range test passed");
    }

    // Helper methods to create test columns

    private ColumnDef createNumberColumn(String name, Double min, Double max) {
        return ColumnDef.builder()
                .name(name)
                .type("number")
                .minimum(min)
                .maximum(max)
                .build();
    }

    private ColumnDef createStringColumn(String name) {
        return ColumnDef.builder()
                .name(name)
                .type("string")
                .build();
    }

    private ColumnDef createEnumColumn(String name, List<String> enumValues) {
        return ColumnDef.builder()
                .name(name)
                .type("enum")
                .enumValues(enumValues)
                .build();
    }
}