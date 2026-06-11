package org.egov.excelingestion.service;

import lombok.extern.slf4j.Slf4j;
import org.egov.excelingestion.web.models.ValidationError;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test cases specifically for validating that errors appear in correct rows 
 * when users skip rows in Excel (e.g., fill rows 3, 5, 7 but skip 4, 6)
 */
@Slf4j
class SkippedRowsValidationTest {

    private SchemaValidationService schemaValidationService;
    
    @BeforeEach
    void setUp() {
        schemaValidationService = new SchemaValidationService(null);
    }

    @Test
    void testValidationErrorsInCorrectRowsWhenSkippingRows() {
        log.info("🧪 Testing validation errors appear in correct rows when skipping Excel rows");
        
        // Create schema with validation rules
        Map<String, Object> schema = createTestSchema();
        
        // Create data that simulates skipped rows in Excel
        // User filled: row 3, skipped row 4, filled row 5, skipped row 6, filled row 7
        List<Map<String, Object>> sheetData = Arrays.asList(
            createRowData("John", 25, 3),      // Excel row 3 - valid data
            createRowData("", 15, 5),          // Excel row 5 - invalid (empty name, age < 18) 
            createRowData("Alice", 70, 7)      // Excel row 7 - invalid (age > 65)
        );
        
        // Perform validation
        List<ValidationError> errors = schemaValidationService.validateDataWithPreFetchedSchema(
            sheetData, "TestSheet", schema, new HashMap<>());
        
        // Verify errors appear in correct Excel rows
        assertFalse(errors.isEmpty(), "Should have validation errors");
        
        // Find errors by row number
        Map<Integer, List<ValidationError>> errorsByRow = groupErrorsByRow(errors);
        
        // Row 3 should have no errors (valid data)
        assertFalse(errorsByRow.containsKey(3), "Row 3 should have no validation errors");
        
        // Row 5 should have errors (empty name, age < 18)  
        assertTrue(errorsByRow.containsKey(5), "Row 5 should have validation errors");
        List<ValidationError> row5Errors = errorsByRow.get(5);
        assertTrue(row5Errors.size() >= 2, "Row 5 should have at least 2 errors (name required, age minimum)");
        
        // Row 7 should have errors (age > 65)
        assertTrue(errorsByRow.containsKey(7), "Row 7 should have validation errors");
        List<ValidationError> row7Errors = errorsByRow.get(7);
        assertTrue(row7Errors.size() >= 1, "Row 7 should have at least 1 error (age maximum)");
        
        // Verify no errors appear in skipped rows (4, 6) 
        assertFalse(errorsByRow.containsKey(4), "Skipped row 4 should have no errors");
        assertFalse(errorsByRow.containsKey(6), "Skipped row 6 should have no errors");
        
        log.info("✅ Validation errors correctly appear in filled rows (3, 5, 7), not skipped rows (4, 6)");
    }

    @Test
    void testUniquenessValidationWithSkippedRows() {
        log.info("🧪 Testing uniqueness validation works correctly with skipped rows");
        
        // Create schema with unique field validation
        Map<String, Object> schema = createSchemaWithUniqueField();
        
        // Create data with duplicates in non-consecutive rows
        // User filled: row 3, skipped row 4, filled row 5, skipped row 6, filled row 7
        List<Map<String, Object>> sheetData = Arrays.asList(
            createEmailRowData("john@example.com", 25, 3),     // Excel row 3
            createEmailRowData("alice@example.com", 30, 5),    // Excel row 5  
            createEmailRowData("john@example.com", 35, 7)      // Excel row 7 - duplicate email
        );
        
        // Perform validation
        List<ValidationError> errors = schemaValidationService.validateDataWithPreFetchedSchema(
            sheetData, "TestSheet", schema, new HashMap<>());
        
        // Should have uniqueness errors
        assertFalse(errors.isEmpty(), "Should have uniqueness validation errors");
        
        // Group errors by row
        Map<Integer, List<ValidationError>> errorsByRow = groupErrorsByRow(errors);
        
        // Both row 3 and row 7 should have uniqueness errors (duplicate email)
        assertTrue(errorsByRow.containsKey(3), "Row 3 should have uniqueness error");
        assertTrue(errorsByRow.containsKey(7), "Row 7 should have uniqueness error");
        
        // Row 5 should have no errors (unique email)
        assertFalse(errorsByRow.containsKey(5), "Row 5 should have no uniqueness errors");
        
        // Verify error messages mention correct row numbers
        boolean foundRow3Reference = errors.stream()
            .anyMatch(error -> error.getRowNumber() == 3 && 
                      error.getErrorDetails().contains("row(s): 7"));
        assertTrue(foundRow3Reference, "Row 3 error should reference duplicate in row 7");
        
        boolean foundRow7Reference = errors.stream()
            .anyMatch(error -> error.getRowNumber() == 7 && 
                      error.getErrorDetails().contains("row(s): 3"));
        assertTrue(foundRow7Reference, "Row 7 error should reference duplicate in row 3");
        
        log.info("✅ Uniqueness validation correctly identifies duplicates in actual Excel rows");
    }

    @Test
    void testLargeGapsInRowNumbers() {
        log.info("🧪 Testing validation with large gaps between filled rows");
        
        Map<String, Object> schema = createTestSchema();
        
        // Simulate user filling rows 5, 15, 25 (large gaps)
        List<Map<String, Object>> sheetData = Arrays.asList(
            createRowData("John", 25, 5),      // Excel row 5
            createRowData("", 15, 15),         // Excel row 15 - invalid data
            createRowData("Alice", 30, 25)     // Excel row 25
        );
        
        List<ValidationError> errors = schemaValidationService.validateDataWithPreFetchedSchema(
            sheetData, "TestSheet", schema, new HashMap<>());
        
        Map<Integer, List<ValidationError>> errorsByRow = groupErrorsByRow(errors);
        
        // Only row 15 should have errors
        assertFalse(errorsByRow.containsKey(5), "Row 5 should have no errors");
        assertTrue(errorsByRow.containsKey(15), "Row 15 should have validation errors");
        assertFalse(errorsByRow.containsKey(25), "Row 25 should have no errors");
        
        // Verify no errors in intermediate rows (6-14, 16-24)
        for (int row = 6; row <= 14; row++) {
            assertFalse(errorsByRow.containsKey(row), "Gap row " + row + " should have no errors");
        }
        for (int row = 16; row <= 24; row++) {
            assertFalse(errorsByRow.containsKey(row), "Gap row " + row + " should have no errors");
        }
        
        log.info("✅ Validation works correctly with large gaps in row numbers");
    }

    @Test
    void testMixedValidAndInvalidDataWithSkippedRows() {
        log.info("🧪 Testing mixed valid/invalid data with skipped rows");
        
        Map<String, Object> schema = createTestSchema();
        
        // Mixed scenario: some valid, some invalid, with gaps
        List<Map<String, Object>> sheetData = Arrays.asList(
            createRowData("John", 25, 3),      // Row 3 - valid
            createRowData("Jane", 17, 6),      // Row 6 - invalid (age < 18)
            createRowData("Bob", 30, 8),       // Row 8 - valid  
            createRowData("", 25, 12),         // Row 12 - invalid (empty name)
            createRowData("Alice", 40, 15)     // Row 15 - valid
        );
        
        List<ValidationError> errors = schemaValidationService.validateDataWithPreFetchedSchema(
            sheetData, "TestSheet", schema, new HashMap<>());
        
        Map<Integer, List<ValidationError>> errorsByRow = groupErrorsByRow(errors);
        
        // Valid rows should have no errors
        assertFalse(errorsByRow.containsKey(3), "Valid row 3 should have no errors");
        assertFalse(errorsByRow.containsKey(8), "Valid row 8 should have no errors");
        assertFalse(errorsByRow.containsKey(15), "Valid row 15 should have no errors");
        
        // Invalid rows should have errors
        assertTrue(errorsByRow.containsKey(6), "Invalid row 6 should have errors");
        assertTrue(errorsByRow.containsKey(12), "Invalid row 12 should have errors");
        
        // Verify error types - let's check what we actually got
        List<ValidationError> row6Errors = errorsByRow.get(6);
        log.info("Row 6 errors: {}", row6Errors.stream().map(ValidationError::getErrorDetails).toList());
        assertTrue(row6Errors.stream().anyMatch(e -> e.getErrorDetails().toLowerCase().contains("age") || 
                                                     e.getErrorDetails().contains("17") ||
                                                     e.getErrorDetails().contains("18")),
                   "Row 6 should have age validation error");
        
        List<ValidationError> row12Errors = errorsByRow.get(12);
        log.info("Row 12 errors: {}", row12Errors.stream().map(ValidationError::getErrorDetails).toList());
        assertTrue(row12Errors.stream().anyMatch(e -> e.getErrorDetails().toLowerCase().contains("required") ||
                                                      e.getErrorDetails().toLowerCase().contains("empty") ||
                                                      e.getErrorDetails().toLowerCase().contains("name")),
                   "Row 12 should have required field error");
        
        log.info("✅ Mixed valid/invalid data validation works correctly with skipped rows");
    }

    @Test
    void testActualRowNumberPreservationInEdgeCases() {
        log.info("🧪 Testing actual row number preservation in edge cases");
        
        Map<String, Object> schema = createTestSchema();
        
        // Edge case: only one row filled, very high row number
        List<Map<String, Object>> sheetData = Arrays.asList(
            createRowData("", 15, 100)  // Row 100 - invalid data
        );
        
        List<ValidationError> errors = schemaValidationService.validateDataWithPreFetchedSchema(
            sheetData, "TestSheet", schema, new HashMap<>());
        
        assertFalse(errors.isEmpty(), "Should have validation errors");
        
        // All errors should be for row 100
        errors.forEach(error -> {
            assertEquals(100, error.getRowNumber(), 
                        "Error should be reported for actual Excel row 100, not calculated row number");
        });
        
        log.info("✅ Actual row numbers preserved correctly in edge cases");
    }

    // Helper methods
    
    private Map<String, Object> createTestSchema() {
        Map<String, Object> schema = new HashMap<>();
        
        // String properties
        List<Map<String, Object>> stringProperties = Arrays.asList(
            createPropertyDef("name", "string", true, 2, 50, null)
        );
        schema.put("stringProperties", stringProperties);
        
        // Number properties  
        List<Map<String, Object>> numberProperties = Arrays.asList(
            createPropertyDef("age", "number", true, 18.0, 65.0, null)
        );
        schema.put("numberProperties", numberProperties);
        
        return schema;
    }
    
    private Map<String, Object> createSchemaWithUniqueField() {
        Map<String, Object> schema = new HashMap<>();
        
        // String properties with unique constraint
        Map<String, Object> emailProperty = createPropertyDef("email", "string", true, 5, 100, null);
        emailProperty.put("isUnique", true);
        
        List<Map<String, Object>> stringProperties = Arrays.asList(emailProperty);
        schema.put("stringProperties", stringProperties);
        
        // Number properties
        List<Map<String, Object>> numberProperties = Arrays.asList(
            createPropertyDef("age", "number", true, 18.0, 65.0, null)
        );
        schema.put("numberProperties", numberProperties);
        
        return schema;
    }
    
    private Map<String, Object> createPropertyDef(String name, String type, boolean required, 
                                                Object min, Object max, String pattern) {
        Map<String, Object> property = new HashMap<>();
        property.put("name", name);
        property.put("isRequired", required);
        
        if ("string".equals(type)) {
            if (min != null) property.put("minLength", min);
            if (max != null) property.put("maxLength", max);
            if (pattern != null) property.put("pattern", pattern);
        } else if ("number".equals(type)) {
            if (min != null) property.put("minimum", min);
            if (max != null) property.put("maximum", max);
        }
        
        return property;
    }
    
    private Map<String, Object> createRowData(String name, int age, int actualRowNumber) {
        Map<String, Object> rowData = new HashMap<>();
        rowData.put("name", name);
        rowData.put("age", age);
        rowData.put("__actualRowNumber__", actualRowNumber);
        return rowData;
    }
    
    private Map<String, Object> createEmailRowData(String email, int age, int actualRowNumber) {
        Map<String, Object> rowData = new HashMap<>();
        rowData.put("email", email);
        rowData.put("age", age);
        rowData.put("__actualRowNumber__", actualRowNumber);
        return rowData;
    }
    
    private Map<Integer, List<ValidationError>> groupErrorsByRow(List<ValidationError> errors) {
        Map<Integer, List<ValidationError>> errorsByRow = new HashMap<>();
        for (ValidationError error : errors) {
            errorsByRow.computeIfAbsent(error.getRowNumber(), k -> new ArrayList<>()).add(error);
        }
        return errorsByRow;
    }
}