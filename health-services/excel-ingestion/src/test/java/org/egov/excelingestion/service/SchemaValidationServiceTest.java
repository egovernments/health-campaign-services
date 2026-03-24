package org.egov.excelingestion.service;

import org.egov.excelingestion.config.ValidationConstants;
import org.egov.excelingestion.web.models.ValidationError;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class SchemaValidationServiceTest {

    @Mock
    private MDMSService mdmsService;

    @InjectMocks
    private SchemaValidationService schemaValidationService;

    private Map<String, Object> testSchema;
    private Map<String, String> localizationMap;
    private List<Map<String, Object>> testSheetData;

    @BeforeEach
    void setUp() {
        setupTestSchema();
        setupLocalizationMap();
        setupTestData();
    }

    // ==================== BASIC VALIDATION TESTS ====================

    @Test
    void testValidateDataWithPreFetchedSchema_ValidData() {
        // Given
        List<Map<String, Object>> validData = Arrays.asList(
            createRowData("name", "John Doe", "age", 25, "email", "john@example.com")
        );

        // When
        List<ValidationError> errors = schemaValidationService.validateDataWithPreFetchedSchema(
            validData, "TestSheet", testSchema, localizationMap);

        // Then
        assertTrue(errors.isEmpty(), "Valid data should not produce validation errors");
    }

    @Test
    void testValidateDataWithPreFetchedSchema_NullSchema() {
        // Given
        List<Map<String, Object>> data = Arrays.asList(createRowData("name", "John"));

        // When
        List<ValidationError> errors = schemaValidationService.validateDataWithPreFetchedSchema(
            data, "TestSheet", null, localizationMap);

        // Then
        assertTrue(errors.isEmpty(), "Null schema should return empty errors list");
    }

    @Test
    void testValidateDataWithPreFetchedSchema_EmptyData() {
        // When
        List<ValidationError> errors = schemaValidationService.validateDataWithPreFetchedSchema(
            new ArrayList<>(), "TestSheet", testSchema, localizationMap);

        // Then
        assertTrue(errors.isEmpty(), "Empty data should not produce validation errors");
    }

    // ==================== REQUIRED FIELD VALIDATION TESTS ====================

    @Test
    void testRequiredFieldValidation_MissingRequiredField() {
        // Given - missing required 'name' field
        List<Map<String, Object>> dataWithMissingRequired = Arrays.asList(
            createRowData("age", 25, "email", "john@example.com")
        );

        // When
        List<ValidationError> errors = schemaValidationService.validateDataWithPreFetchedSchema(
            dataWithMissingRequired, "TestSheet", testSchema, localizationMap);

        // Then
        assertFalse(errors.isEmpty(), "Missing required field should produce validation errors");
        ValidationError nameError = findErrorByColumn(errors, "name");
        assertNotNull(nameError, "Should have error for missing 'name' field");
        assertEquals(ValidationConstants.STATUS_INVALID, nameError.getStatus());
        assertTrue(nameError.getErrorDetails().contains("Required field"), "Should mention field is required");
    }

    @Test
    void testRequiredFieldValidation_EmptyStringForRequiredField() {
        // Given - empty string for required field
        List<Map<String, Object>> dataWithEmptyRequired = Arrays.asList(
            createRowData("name", "", "age", 25, "email", "john@example.com")
        );

        // When
        List<ValidationError> errors = schemaValidationService.validateDataWithPreFetchedSchema(
            dataWithEmptyRequired, "TestSheet", testSchema, localizationMap);

        // Then
        assertFalse(errors.isEmpty(), "Empty required field should produce validation errors");
        ValidationError nameError = findErrorByColumn(errors, "name");
        assertNotNull(nameError, "Should have error for empty 'name' field");
        assertTrue(nameError.getErrorDetails().contains("Required field"), "Should mention field is required");
    }

    @Test
    void testRequiredFieldValidation_WhitespaceOnlyForRequiredField() {
        // Given - whitespace only for required field
        List<Map<String, Object>> dataWithWhitespaceRequired = Arrays.asList(
            createRowData("name", "   ", "age", 25, "email", "john@example.com")
        );

        // When
        List<ValidationError> errors = schemaValidationService.validateDataWithPreFetchedSchema(
            dataWithWhitespaceRequired, "TestSheet", testSchema, localizationMap);

        // Then
        assertFalse(errors.isEmpty(), "Whitespace-only required field should produce validation errors");
        ValidationError nameError = findErrorByColumn(errors, "name");
        assertNotNull(nameError, "Should have error for whitespace-only 'name' field");
    }

    // ==================== STRING VALIDATION TESTS ====================

    @Test
    void testStringValidation_MinLength() {
        // Given - name too short (min length = 2)
        List<Map<String, Object>> dataWithShortName = Arrays.asList(
            createRowData("name", "A", "age", 25, "email", "john@example.com")
        );

        // When
        List<ValidationError> errors = schemaValidationService.validateDataWithPreFetchedSchema(
            dataWithShortName, "TestSheet", testSchema, localizationMap);

        // Then
        assertFalse(errors.isEmpty(), "String shorter than min length should produce validation errors");
        ValidationError nameError = findErrorByColumn(errors, "name");
        assertNotNull(nameError, "Should have error for short 'name' field");
        assertTrue(nameError.getErrorDetails().contains("at least"), "Should mention minimum length requirement");
    }

    @Test
    void testStringValidation_MaxLength() {
        // Given - name too long (max length = 100)
        String longName = "A".repeat(101);
        List<Map<String, Object>> dataWithLongName = Arrays.asList(
            createRowData("name", longName, "age", 25, "email", "john@example.com")
        );

        // When
        List<ValidationError> errors = schemaValidationService.validateDataWithPreFetchedSchema(
            dataWithLongName, "TestSheet", testSchema, localizationMap);

        // Then
        assertFalse(errors.isEmpty(), "String longer than max length should produce validation errors");
        ValidationError nameError = findErrorByColumn(errors, "name");
        assertNotNull(nameError, "Should have error for long 'name' field");
        assertTrue(nameError.getErrorDetails().contains("not exceed"), "Should mention maximum length requirement");
    }

    @Test
    void testStringValidation_Pattern() {
        // Given - invalid email pattern
        List<Map<String, Object>> dataWithInvalidEmail = Arrays.asList(
            createRowData("name", "John Doe", "age", 25, "email", "invalid-email")
        );

        // When
        List<ValidationError> errors = schemaValidationService.validateDataWithPreFetchedSchema(
            dataWithInvalidEmail, "TestSheet", testSchema, localizationMap);

        // Then
        assertFalse(errors.isEmpty(), "Invalid pattern should produce validation errors");
        ValidationError emailError = findErrorByColumn(errors, "email");
        assertNotNull(emailError, "Should have error for invalid 'email' pattern");
        assertTrue(emailError.getErrorDetails().contains("pattern"), "Should mention pattern validation");
    }

    // ==================== NUMBER VALIDATION TESTS ====================

    @Test
    void testNumberValidation_Minimum() {
        // Given - age below minimum (min = 0)
        List<Map<String, Object>> dataWithNegativeAge = Arrays.asList(
            createRowData("name", "John Doe", "age", -5, "email", "john@example.com")
        );

        // When
        List<ValidationError> errors = schemaValidationService.validateDataWithPreFetchedSchema(
            dataWithNegativeAge, "TestSheet", testSchema, localizationMap);

        // Then
        assertFalse(errors.isEmpty(), "Number below minimum should produce validation errors");
        ValidationError ageError = findErrorByColumn(errors, "age");
        assertNotNull(ageError, "Should have error for negative 'age' field");
        assertTrue(ageError.getErrorDetails().contains("at least"), "Should mention minimum value requirement");
    }

    @Test
    void testNumberValidation_Maximum() {
        // Given - age above maximum (max = 150)
        List<Map<String, Object>> dataWithHighAge = Arrays.asList(
            createRowData("name", "John Doe", "age", 200, "email", "john@example.com")
        );

        // When
        List<ValidationError> errors = schemaValidationService.validateDataWithPreFetchedSchema(
            dataWithHighAge, "TestSheet", testSchema, localizationMap);

        // Then
        assertFalse(errors.isEmpty(), "Number above maximum should produce validation errors");
        ValidationError ageError = findErrorByColumn(errors, "age");
        assertNotNull(ageError, "Should have error for high 'age' field");
        assertTrue(ageError.getErrorDetails().contains("not exceed"), "Should mention maximum value requirement");
    }

    @Test
    void testNumberValidation_InvalidNumberFormat() {
        // Given - invalid number format
        List<Map<String, Object>> dataWithInvalidNumber = Arrays.asList(
            createRowData("name", "John Doe", "age", "not-a-number", "email", "john@example.com")
        );

        // When
        List<ValidationError> errors = schemaValidationService.validateDataWithPreFetchedSchema(
            dataWithInvalidNumber, "TestSheet", testSchema, localizationMap);

        // Then
        assertFalse(errors.isEmpty(), "Invalid number format should produce validation errors");
        ValidationError ageError = findErrorByColumn(errors, "age");
        assertNotNull(ageError, "Should have error for invalid 'age' number format");
        assertTrue(ageError.getErrorDetails().contains("valid number"), "Should mention number format requirement");
    }

    // ==================== ENUM VALIDATION TESTS ====================

    @Test
    void testEnumValidation_ValidValue() {
        // Given - valid status value
        List<Map<String, Object>> dataWithValidStatus = Arrays.asList(
            createRowData("name", "John Doe", "age", 25, "status", "ACTIVE")
        );

        // When
        List<ValidationError> errors = schemaValidationService.validateDataWithPreFetchedSchema(
            dataWithValidStatus, "TestSheet", testSchema, localizationMap);

        // Then
        // Should only check that there's no error for the status field specifically
        ValidationError statusError = findErrorByColumn(errors, "status");
        if (statusError != null) {
            assertNotEquals(ValidationConstants.STATUS_INVALID, statusError.getStatus(),
                "Valid enum value should not produce validation errors");
        }
    }

    @Test
    void testEnumValidation_InvalidValue() {
        // Given - invalid status value
        List<Map<String, Object>> dataWithInvalidStatus = Arrays.asList(
            createRowData("name", "John Doe", "age", 25, "status", "INVALID_STATUS")
        );

        // When
        List<ValidationError> errors = schemaValidationService.validateDataWithPreFetchedSchema(
            dataWithInvalidStatus, "TestSheet", testSchema, localizationMap);

        // Then
        assertFalse(errors.isEmpty(), "Invalid enum value should produce validation errors");
        ValidationError statusError = findErrorByColumn(errors, "status");
        assertNotNull(statusError, "Should have error for invalid 'status' enum value");
        assertTrue(statusError.getErrorDetails().contains("invalid value"), "Should mention invalid enum value");
    }

    // ==================== MULTI-SELECT VALIDATION TESTS ====================

    @Test
    void testMultiSelectValidation_ValidSelections() {
        // Given - valid multi-select with individual columns
        List<Map<String, Object>> dataWithValidMultiSelect = Arrays.asList(
            createRowDataWithMultiSelect("name", "John Doe", "skills", Arrays.asList("Java", "Python"))
        );

        // When
        List<ValidationError> errors = schemaValidationService.validateDataWithPreFetchedSchema(
            dataWithValidMultiSelect, "TestSheet", createMultiSelectSchema(), localizationMap);

        // Then
        ValidationError skillsError = findErrorByColumn(errors, "skills");
        if (skillsError != null) {
            assertNotEquals(ValidationConstants.STATUS_INVALID, skillsError.getStatus(),
                "Valid multi-select should not produce validation errors");
        }
    }

    @Test
    void testMultiSelectValidation_MinSelections() {
        // Given - less than minimum selections (min = 2)
        List<Map<String, Object>> dataWithTooFewSelections = Arrays.asList(
            createRowDataWithMultiSelect("name", "John Doe", "skills", Arrays.asList("Java"))
        );

        // When
        List<ValidationError> errors = schemaValidationService.validateDataWithPreFetchedSchema(
            dataWithTooFewSelections, "TestSheet", createMultiSelectSchema(), localizationMap);

        // Then
        assertFalse(errors.isEmpty(), "Too few multi-select selections should produce validation errors");
        ValidationError skillsError = findErrorByColumn(errors, "skills");
        assertNotNull(skillsError, "Should have error for insufficient 'skills' selections");
        assertTrue(skillsError.getErrorDetails().contains("at least"), "Should mention minimum selections requirement");
    }

    @Test
    void testMultiSelectValidation_MaxSelections() {
        // Given - more than maximum selections (max = 3)
        List<Map<String, Object>> dataWithTooManySelections = Arrays.asList(
            createRowDataWithMultiSelect("name", "John Doe", "skills", 
                Arrays.asList("Java", "Python", "JavaScript", "Go"))
        );

        // When
        List<ValidationError> errors = schemaValidationService.validateDataWithPreFetchedSchema(
            dataWithTooManySelections, "TestSheet", createMultiSelectSchema(), localizationMap);

        // Then
        assertFalse(errors.isEmpty(), "Too many multi-select selections should produce validation errors");
        ValidationError skillsError = findErrorByColumn(errors, "skills");
        assertNotNull(skillsError, "Should have error for excessive 'skills' selections");
        assertTrue(skillsError.getErrorDetails().contains("at most"), "Should mention maximum selections requirement");
    }

    @Test
    void testMultiSelectValidation_DuplicateSelections() {
        // Given - duplicate selections
        List<Map<String, Object>> dataWithDuplicates = Arrays.asList(
            createRowDataWithMultiSelect("name", "John Doe", "skills", 
                Arrays.asList("Java", "Python", "Java"))
        );

        // When
        List<ValidationError> errors = schemaValidationService.validateDataWithPreFetchedSchema(
            dataWithDuplicates, "TestSheet", createMultiSelectSchema(), localizationMap);

        // Then
        assertFalse(errors.isEmpty(), "Duplicate multi-select selections should produce validation errors");
        ValidationError skillsError = findErrorByColumn(errors, "skills");
        assertNotNull(skillsError, "Should have error for duplicate 'skills' selections");
        assertTrue(skillsError.getErrorDetails().contains("duplicate"), "Should mention duplicate selections");
    }

    @Test
    void testMultiSelectValidation_RequiredEmpty() {
        // Given - empty required multi-select
        List<Map<String, Object>> dataWithEmptyMultiSelect = Arrays.asList(
            createRowDataWithMultiSelect("name", "John Doe", "skills", Arrays.asList())
        );

        // When
        List<ValidationError> errors = schemaValidationService.validateDataWithPreFetchedSchema(
            dataWithEmptyMultiSelect, "TestSheet", createRequiredMultiSelectSchema(), localizationMap);

        // Then
        assertFalse(errors.isEmpty(), "Empty required multi-select should produce validation errors");
        ValidationError skillsError = findErrorByColumn(errors, "skills");
        assertNotNull(skillsError, "Should have error for empty required 'skills' multi-select");
        assertTrue(skillsError.getErrorDetails().contains("Required") || skillsError.getErrorDetails().contains("at least"), 
            "Should mention required field or minimum selections");
    }

    @Test
    void testMultiSelectValidation_InvalidEnumValue() {
        // Given - invalid enum value in multi-select
        List<Map<String, Object>> dataWithInvalidEnum = Arrays.asList(
            createRowDataWithMultiSelect("name", "John Doe", "skills", 
                Arrays.asList("Java", "InvalidSkill"))
        );

        // When
        List<ValidationError> errors = schemaValidationService.validateDataWithPreFetchedSchema(
            dataWithInvalidEnum, "TestSheet", createMultiSelectSchema(), localizationMap);

        // Then
        assertFalse(errors.isEmpty(), "Invalid enum value in multi-select should produce validation errors");
        ValidationError skillsError = findErrorByColumn(errors, "skills");
        assertNotNull(skillsError, "Should have error for invalid enum value in 'skills'");
        assertTrue(skillsError.getErrorDetails().contains("invalid value"), "Should mention invalid enum value");
    }

    // ==================== UNIQUENESS VALIDATION TESTS ====================

    @Test
    void testUniquenessValidation_DuplicateValues() {
        // Given - duplicate email values
        List<Map<String, Object>> dataWithDuplicates = Arrays.asList(
            createRowData("name", "John Doe", "email", "john@example.com"),
            createRowData("name", "Jane Doe", "email", "john@example.com") // Duplicate email
        );

        // When
        List<ValidationError> errors = schemaValidationService.validateDataWithPreFetchedSchema(
            dataWithDuplicates, "TestSheet", testSchema, localizationMap);

        // Then
        assertFalse(errors.isEmpty(), "Duplicate unique field values should produce validation errors");
        List<ValidationError> emailErrors = findErrorsByColumn(errors, "email");
        assertEquals(2, emailErrors.size(), "Should have errors for both duplicate email entries");
        
        for (ValidationError error : emailErrors) {
            assertTrue(error.getErrorDetails().contains("must be unique"), "Should mention uniqueness requirement");
            assertTrue(error.getErrorDetails().contains("also found in row"), "Should mention other row with same value");
        }
    }

    @Test
    void testUniquenessValidation_IgnoreEmptyValues() {
        // Given - multiple empty values for unique field (should not trigger uniqueness error)
        List<Map<String, Object>> dataWithEmptyUnique = Arrays.asList(
            createRowData("name", "John Doe", "email", ""),
            createRowData("name", "Jane Doe", "email", "")
        );

        // When
        List<ValidationError> errors = schemaValidationService.validateDataWithPreFetchedSchema(
            dataWithEmptyUnique, "TestSheet", testSchema, localizationMap);

        // Then
        List<ValidationError> uniqueErrors = errors.stream()
            .filter(error -> error.getErrorDetails().contains("must be unique"))
            .toList();
        assertTrue(uniqueErrors.isEmpty(), "Empty values should not trigger uniqueness validation");
    }

    // ==================== LOCALIZATION TESTS ====================

    @Test
    void testLocalization_ErrorMessagesUseLocalizedFieldNames() {
        // Given - data with missing required field
        List<Map<String, Object>> dataWithMissingField = Arrays.asList(
            createRowData("age", 25, "email", "john@example.com") // Missing 'name'
        );

        // When
        List<ValidationError> errors = schemaValidationService.validateDataWithPreFetchedSchema(
            dataWithMissingField, "TestSheet", testSchema, localizationMap);

        // Then
        assertFalse(errors.isEmpty(), "Should have validation errors");
        ValidationError nameError = findErrorByColumn(errors, "name");
        assertNotNull(nameError, "Should have error for missing 'name' field");
        assertTrue(nameError.getErrorDetails().contains("Full Name"), 
            "Error message should use localized field name 'Full Name' instead of technical 'name'");
    }

    // ==================== EDGE CASES AND ERROR SCENARIOS ====================

    @Test
    void testEdgeCase_NullRowData() {
        // Given - null in row data list
        List<Map<String, Object>> dataWithNull = new ArrayList<>();
        dataWithNull.add(createRowData("name", "John Doe"));
        dataWithNull.add(null);
        dataWithNull.add(createRowData("name", "Jane Doe"));

        // When & Then - should not throw exception
        assertDoesNotThrow(() -> {
            List<ValidationError> errors = schemaValidationService.validateDataWithPreFetchedSchema(
                dataWithNull, "TestSheet", testSchema, localizationMap);
        }, "Null row data should be handled gracefully");
    }

    @Test
    void testEdgeCase_ExceptionInValidation() {
        // Given - schema that might cause validation issues
        Map<String, Object> malformedSchema = createMalformedSchema();

        // When & Then - should handle exceptions gracefully
        assertDoesNotThrow(() -> {
            List<ValidationError> errors = schemaValidationService.validateDataWithPreFetchedSchema(
                testSheetData, "TestSheet", malformedSchema, localizationMap);
            // Should return errors indicating validation failure, not throw exception
            assertFalse(errors.isEmpty(), "Malformed schema should produce validation errors");
        }, "Malformed schema should be handled gracefully");
    }

    @Test
    void testEdgeCase_VeryLargeDataSet() {
        // Given - large dataset to test performance
        List<Map<String, Object>> largeDataSet = new ArrayList<>();
        for (int i = 0; i < 10000; i++) {
            largeDataSet.add(createRowData("name", "User" + i, "age", 25 + (i % 50), "email", "user" + i + "@example.com"));
        }

        // When
        long startTime = System.currentTimeMillis();
        List<ValidationError> errors = schemaValidationService.validateDataWithPreFetchedSchema(
            largeDataSet, "TestSheet", testSchema, localizationMap);
        long endTime = System.currentTimeMillis();

        // Then - should complete in reasonable time (less than 10 seconds for 10k rows)
        assertTrue(endTime - startTime < 10000, "Large dataset validation should complete in reasonable time");
        // Most data should be valid (allow up to 10% errors)
        assertTrue(errors.isEmpty() || errors.size() < largeDataSet.size() / 10, 
            "Most valid data should pass validation");
    }

    // ==================== HELPER METHODS ====================

    private void setupTestSchema() {
        testSchema = new HashMap<>();
        
        // String properties
        List<Map<String, Object>> stringProperties = Arrays.asList(
            createStringProperty("name", true, 2, 100, null),
            createStringProperty("email", false, null, null, "(?i)^[\\w._%+-]+@[\\w.-]+\\.[A-Z]{2,}$")
        );
        testSchema.put("stringProperties", stringProperties);
        
        // Number properties
        List<Map<String, Object>> numberProperties = Arrays.asList(
            createNumberProperty("age", false, 0, 150)
        );
        testSchema.put("numberProperties", numberProperties);
        
        // Enum properties
        List<Map<String, Object>> enumProperties = Arrays.asList(
            createEnumProperty("status", false, Arrays.asList("ACTIVE", "INACTIVE", "PENDING"))
        );
        testSchema.put("enumProperties", enumProperties);
    }

    private void setupLocalizationMap() {
        localizationMap = new HashMap<>();
        localizationMap.put("name", "Full Name");
        localizationMap.put("age", "Age");
        localizationMap.put("email", "Email Address");
        localizationMap.put("status", "Status");
        localizationMap.put("skills", "Skills");
    }

    private void setupTestData() {
        testSheetData = Arrays.asList(
            createRowData("name", "John Doe", "age", 25, "email", "john@example.com", "status", "ACTIVE"),
            createRowData("name", "Jane Smith", "age", 30, "email", "jane@example.com", "status", "INACTIVE")
        );
    }

    private Map<String, Object> createStringProperty(String name, boolean required, Integer minLength, 
                                                   Integer maxLength, String pattern) {
        Map<String, Object> prop = new HashMap<>();
        prop.put("name", name);
        prop.put("isRequired", required);
        prop.put("isUnique", "email".equals(name)); // Make email unique for testing
        if (minLength != null) prop.put("minLength", minLength);
        if (maxLength != null) prop.put("maxLength", maxLength);
        if (pattern != null) prop.put("pattern", pattern);
        return prop;
    }

    private Map<String, Object> createNumberProperty(String name, boolean required, Number minimum, Number maximum) {
        Map<String, Object> prop = new HashMap<>();
        prop.put("name", name);
        prop.put("isRequired", required);
        if (minimum != null) prop.put("minimum", minimum);
        if (maximum != null) prop.put("maximum", maximum);
        return prop;
    }

    private Map<String, Object> createEnumProperty(String name, boolean required, List<String> enumValues) {
        Map<String, Object> prop = new HashMap<>();
        prop.put("name", name);
        prop.put("isRequired", required);
        prop.put("enum", enumValues);
        return prop;
    }

    private Map<String, Object> createMultiSelectSchema() {
        Map<String, Object> schema = new HashMap<>();
        
        // Multi-select enum property
        Map<String, Object> skillsProperty = createEnumProperty("skills", false, 
            Arrays.asList("Java", "Python", "JavaScript", "Go", "Ruby"));
        
        // Add multi-select details
        Map<String, Object> multiSelectDetails = new HashMap<>();
        multiSelectDetails.put("enum", Arrays.asList("Java", "Python", "JavaScript", "Go", "Ruby"));
        multiSelectDetails.put("minSelections", 2);
        multiSelectDetails.put("maxSelections", 3);
        skillsProperty.put("multiSelectDetails", multiSelectDetails);
        
        schema.put("enumProperties", Arrays.asList(skillsProperty));
        
        // Add basic string property for name
        schema.put("stringProperties", Arrays.asList(
            createStringProperty("name", true, 2, 100, null)
        ));
        
        return schema;
    }

    private Map<String, Object> createRequiredMultiSelectSchema() {
        Map<String, Object> schema = createMultiSelectSchema();
        // Make skills required
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> enumProps = (List<Map<String, Object>>) schema.get("enumProperties");
        enumProps.get(0).put("isRequired", true);
        return schema;
    }

    private Map<String, Object> createMalformedSchema() {
        Map<String, Object> schema = new HashMap<>();
        // Create intentionally malformed schema
        schema.put("stringProperties", "not-a-list"); // Should be a list
        schema.put("invalidProperty", Arrays.asList("invalid"));
        return schema;
    }

    private Map<String, Object> createRowData(Object... keyValuePairs) {
        Map<String, Object> rowData = new HashMap<>();
        for (int i = 0; i < keyValuePairs.length; i += 2) {
            rowData.put((String) keyValuePairs[i], keyValuePairs[i + 1]);
        }
        return rowData;
    }

    private Map<String, Object> createRowDataWithMultiSelect(String nameKey, String nameValue, 
                                                           String multiSelectKey, List<String> selections) {
        Map<String, Object> rowData = new HashMap<>();
        rowData.put(nameKey, nameValue);
        
        // Add individual multi-select columns
        for (int i = 0; i < selections.size(); i++) {
            rowData.put(multiSelectKey + "_MULTISELECT_" + (i + 1), selections.get(i));
        }
        
        // Add empty columns for remaining slots (up to 5)
        for (int i = selections.size(); i < 5; i++) {
            rowData.put(multiSelectKey + "_MULTISELECT_" + (i + 1), "");
        }
        
        return rowData;
    }

    private ValidationError findErrorByColumn(List<ValidationError> errors, String columnName) {
        return errors.stream()
            .filter(error -> columnName.equals(error.getColumnName()))
            .findFirst()
            .orElse(null);
    }

    private List<ValidationError> findErrorsByColumn(List<ValidationError> errors, String columnName) {
        return errors.stream()
            .filter(error -> columnName.equals(error.getColumnName()))
            .toList();
    }
}