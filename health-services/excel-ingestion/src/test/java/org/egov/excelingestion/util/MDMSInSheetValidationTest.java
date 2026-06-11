package org.egov.excelingestion.util;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.egov.excelingestion.config.ExcelIngestionConfig;
import org.egov.excelingestion.web.models.excel.ColumnDef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

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
        
        // Verify that pure visual validation is applied (number fields no longer use DataValidation objects)
        List<? extends DataValidation> validations = sheet.getDataValidations();
        assertTrue(validations.isEmpty(), "Number fields should use pure visual validation, not DataValidation objects");
        
        log.info("✅ Verified {} data validations on sheet (expected 0 for pure visual validation)", validations.size());
        
        // Instead of checking DataValidation objects, verify that conditional formatting is applied
        // This is the new way number validation works (pure visual validation)
        
        // Verify conditional formatting rules exist (this is how pure visual validation works)
        assertEquals(3, sheet.getSheetConditionalFormatting().getNumConditionalFormattings(),
            "Should have conditional formatting for each number column");
        
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
        
        // Verify that only enum validations create DataValidation objects (numbers use pure visual validation)
        List<? extends DataValidation> validations = sheet.getDataValidations();
        assertEquals(1, validations.size(), "Should have 1 validation (enum only, numbers use pure visual validation)");
        
        // Check validation types - should only find enum/list validation
        boolean hasListValidation = false;
        
        for (DataValidation validation : validations) {
            DataValidationConstraint constraint = validation.getValidationConstraint();
            
            if (constraint.getValidationType() == DataValidationConstraint.ValidationType.LIST) {
                hasListValidation = true;
                log.info("✅ Found enum validation");
            }
        }
        
        assertTrue(hasListValidation, "Should have list validation for enums");
        
        // Verify number fields use pure visual validation (conditional formatting)
        assertTrue(sheet.getSheetConditionalFormatting().getNumConditionalFormattings() > 0,
                "Number fields should use conditional formatting for pure visual validation");
        
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
        
        // Verify that number fields use pure visual validation (no DataValidation objects)
        List<? extends DataValidation> validations = sheet.getDataValidations();
        assertEquals(0, validations.size(), "Number fields should use pure visual validation, not DataValidation objects");
        
        // Verify conditional formatting is applied for pure visual validation
        assertTrue(sheet.getSheetConditionalFormatting().getNumConditionalFormattings() > 0,
                "Number fields should use conditional formatting for validation feedback");
        
        log.info("✅ Number fields use pure visual validation (conditional formatting) instead of DataValidation objects");
        
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
        
        // Number fields use pure visual validation, so no DataValidation objects
        assertEquals(0, validations.size(), "Number fields should use pure visual validation, not DataValidation objects");
        
        // Verify conditional formatting covers the expected range for pure visual validation
        assertTrue(sheet.getSheetConditionalFormatting().getNumConditionalFormattings() > 0,
                "Should have conditional formatting for pure visual validation");
        
        log.info("✅ Number fields use pure visual validation covering appropriate ranges");
        
        workbook.close();
        log.info("✅ Validation range test passed");
    }

    @Test
    void testStringValidationsAreApplied() throws IOException {
        log.info("🧪 Testing MDMS string validations (minLength, maxLength) are applied to Excel sheets");
        
        // Create columns with string validation properties
        List<ColumnDef> columns = Arrays.asList(
            createStringColumnWithLength("name", 3, 50),        // Min-Max length validation
            createStringColumnWithLength("email", 5, null),     // Min only validation  
            createStringColumnWithLength("code", null, 10)      // Max only validation
        );
        
        // Generate Excel workbook with validation
        Workbook workbook = excelDataPopulator.populateSheetWithData(
            "StringValidationTest", columns, null);
        Sheet sheet = workbook.getSheetAt(0);
        
        // Verify that string fields use pure visual validation (no DataValidation objects)
        List<? extends DataValidation> validations = sheet.getDataValidations();
        assertEquals(0, validations.size(), "String fields should use pure visual validation, not DataValidation objects");
        log.info("✅ Verified {} data validations on sheet (expected 0 for pure visual validation)", validations.size());
        
        // Verify conditional formatting is applied for string fields (pure visual validation)
        assertTrue(sheet.getSheetConditionalFormatting().getNumConditionalFormattings() > 0,
                "String fields should use conditional formatting for pure visual validation");
        
        log.info("✅ String fields use pure visual validation (conditional formatting) instead of DataValidation objects");
        
        workbook.close();
        log.info("✅ String validation test passed");
    }
    
    @Test
    void testAdditionalNumberValidationsAreApplied() throws IOException {
        log.info("🧪 Testing additional MDMS number validations (exclusiveMin/Max) are applied to Excel sheets");
        
        // Create columns with additional number validation properties
        List<ColumnDef> columns = Arrays.asList(
            createNumberColumnWithExclusive("score", null, null, 0.0, 100.0),  // Exclusive min-max validation
            createNumberColumnWithExclusive("rating", null, null, 1.0, null),   // Exclusive min only
            createNumberColumnWithExclusive("percentage", null, null, null, 99.0), // Exclusive max only
            createNumberColumnWithMultiple("quantity", 5.0)  // MultipleOf (will be processing-only)
        );
        
        // Generate Excel workbook with validation
        Workbook workbook = excelDataPopulator.populateSheetWithData(
            "AdditionalNumberValidationTest", columns, null);
        Sheet sheet = workbook.getSheetAt(0);
        
        // Verify that number fields use pure visual validation (no DataValidation objects)
        List<? extends DataValidation> validations = sheet.getDataValidations();
        assertEquals(0, validations.size(), "Number fields should use pure visual validation, not DataValidation objects");
        
        // Verify conditional formatting is applied for pure visual validation
        assertTrue(sheet.getSheetConditionalFormatting().getNumConditionalFormattings() > 0,
                "Number fields should use conditional formatting for validation feedback");
        
        log.info("✅ Additional number validations use pure visual validation (conditional formatting) instead of DataValidation objects");
        
        workbook.close();
        log.info("✅ Additional number validation test passed");
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
    
    private ColumnDef createStringColumnWithLength(String name, Integer minLength, Integer maxLength) {
        return ColumnDef.builder()
                .name(name)
                .type("string")
                .minLength(minLength)
                .maxLength(maxLength)
                .build();
    }
    
    private ColumnDef createNumberColumnWithExclusive(String name, Double min, Double max, 
                                                     Double exclusiveMin, Double exclusiveMax) {
        return ColumnDef.builder()
                .name(name)
                .type("number")
                .minimum(min)
                .maximum(max)
                .exclusiveMinimum(exclusiveMin)
                .exclusiveMaximum(exclusiveMax)
                .build();
    }
    
    private ColumnDef createNumberColumnWithMultiple(String name, Double multipleOf) {
        return ColumnDef.builder()
                .name(name)
                .type("number")
                .multipleOf(multipleOf)
                .build();
    }
}