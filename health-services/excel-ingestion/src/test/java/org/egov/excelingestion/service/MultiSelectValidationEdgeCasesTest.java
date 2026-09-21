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
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class MultiSelectValidationEdgeCasesTest {

    @Mock
    private MDMSService mdmsService;

    @InjectMocks
    private SchemaValidationService schemaValidationService;

    private Map<String, String> localizationMap;

    @BeforeEach
    void setUp() {
        setupLocalizationMap();
    }

    // ==================== MULTI-SELECT FIELD NAME PATTERNS ====================

    @Test
    void testMultiSelectValidation_CorrectFieldNamePattern() {
        // Given - correct _MULTISELECT_ pattern
        Map<String, Object> schema = createMultiSelectSchema("skills", 1, 3, 
            Arrays.asList("Java", "Python", "JavaScript"));
        List<Map<String, Object>> data = Arrays.asList(
            createMultiSelectRowData("skills", Arrays.asList("Java", "Python"))
        );

        // When
        List<ValidationError> errors = schemaValidationService.validateDataWithPreFetchedSchema(
            data, "TestSheet", schema, localizationMap);

        // Then
        ValidationError skillsError = findErrorByColumn(errors, "skills");
        if (skillsError != null) {
            assertNotEquals(ValidationConstants.STATUS_INVALID, skillsError.getStatus(),
                "Correct field name pattern should be recognized");
        }
    }

    @Test
    void testMultiSelectValidation_LegacyFieldNamePattern() {
        // Given - legacy _1, _2 pattern (should still work as fallback)
        Map<String, Object> schema = createMultiSelectSchema("skills", 1, 3, 
            Arrays.asList("Java", "Python", "JavaScript"));
        Map<String, Object> rowData = new HashMap<>();
        rowData.put("skills_1", "Java");
        rowData.put("skills_2", "Python");
        rowData.put("skills_3", "");
        List<Map<String, Object>> data = Arrays.asList(rowData);

        // When
        List<ValidationError> errors = schemaValidationService.validateDataWithPreFetchedSchema(
            data, "TestSheet", schema, localizationMap);

        // Then - should handle gracefully, even if not the preferred pattern
        assertNotNull(errors, "Should return errors list without throwing exception");
    }

    @Test
    void testMultiSelectValidation_MixedFieldNamePatterns() {
        // Given - mix of both patterns (edge case)
        Map<String, Object> schema = createMultiSelectSchema("skills", 1, 5, 
            Arrays.asList("Java", "Python", "JavaScript", "Go", "Ruby"));
        Map<String, Object> rowData = new HashMap<>();
        rowData.put("skills_MULTISELECT_1", "Java");
        rowData.put("skills_MULTISELECT_2", "Python");
        rowData.put("skills_1", "JavaScript"); // Legacy pattern
        rowData.put("skills_2", "Go");         // Legacy pattern
        List<Map<String, Object>> data = Arrays.asList(rowData);

        // When
        List<ValidationError> errors = schemaValidationService.validateDataWithPreFetchedSchema(
            data, "TestSheet", schema, localizationMap);

        // Then
        assertNotNull(errors, "Should handle mixed patterns gracefully");
    }

    // ==================== EMPTY AND NULL VALUE EDGE CASES ====================

    @Test
    void testMultiSelectValidation_AllEmptyValues() {
        // Given - all multi-select columns are empty
        Map<String, Object> schema = createMultiSelectSchema("skills", 2, 3, 
            Arrays.asList("Java", "Python", "JavaScript"));
        List<Map<String, Object>> data = Arrays.asList(
            createMultiSelectRowData("skills", Arrays.asList()) // All empty
        );

        // When
        List<ValidationError> errors = schemaValidationService.validateDataWithPreFetchedSchema(
            data, "TestSheet", schema, localizationMap);

        // Then
        assertFalse(errors.isEmpty(), "All empty values should trigger min selection validation");
        ValidationError skillsError = findErrorByColumn(errors, "skills");
        assertNotNull(skillsError, "Should have error for empty multi-select with min selections");
        assertTrue(skillsError.getErrorDetails().contains("at least"), 
            "Should mention minimum selections requirement");
    }

    @Test
    void testMultiSelectValidation_NullValues() {
        // Given - null values in multi-select columns
        Map<String, Object> schema = createMultiSelectSchema("skills", 1, 3, 
            Arrays.asList("Java", "Python", "JavaScript"));
        Map<String, Object> rowData = new HashMap<>();
        rowData.put("skills_MULTISELECT_1", null);
        rowData.put("skills_MULTISELECT_2", "Python");
        rowData.put("skills_MULTISELECT_3", null);
        List<Map<String, Object>> data = Arrays.asList(rowData);

        // When
        List<ValidationError> errors = schemaValidationService.validateDataWithPreFetchedSchema(
            data, "TestSheet", schema, localizationMap);

        // Then
        ValidationError skillsError = findErrorByColumn(errors, "skills");
        if (skillsError != null) {
            // Should handle null values gracefully - count only non-null values
            assertTrue(skillsError.getErrorDetails().contains("Skills") || 
                      skillsError.getStatus().equals(ValidationConstants.STATUS_VALID),
                "Should handle null values gracefully");
        }
    }

    @Test
    void testMultiSelectValidation_WhitespaceOnlyValues() {
        // Given - whitespace-only values
        Map<String, Object> schema = createMultiSelectSchema("skills", 2, 3, 
            Arrays.asList("Java", "Python", "JavaScript"));
        Map<String, Object> rowData = new HashMap<>();
        rowData.put("skills_MULTISELECT_1", "  ");    // Whitespace only
        rowData.put("skills_MULTISELECT_2", "Python");
        rowData.put("skills_MULTISELECT_3", "\t");    // Tab only
        List<Map<String, Object>> data = Arrays.asList(rowData);

        // When
        List<ValidationError> errors = schemaValidationService.validateDataWithPreFetchedSchema(
            data, "TestSheet", schema, localizationMap);

        // Then
        assertFalse(errors.isEmpty(), "Whitespace-only values should not count as valid selections");
        ValidationError skillsError = findErrorByColumn(errors, "skills");
        assertNotNull(skillsError, "Should have error for insufficient valid selections");
    }

    // ==================== BOUNDARY VALUE TESTS ====================

    @Test
    void testMultiSelectValidation_ExactMinimumSelections() {
        // Given - exactly minimum required selections
        Map<String, Object> schema = createMultiSelectSchema("skills", 2, 5, 
            Arrays.asList("Java", "Python", "JavaScript", "Go", "Ruby"));
        List<Map<String, Object>> data = Arrays.asList(
            createMultiSelectRowData("skills", Arrays.asList("Java", "Python")) // Exactly 2
        );

        // When
        List<ValidationError> errors = schemaValidationService.validateDataWithPreFetchedSchema(
            data, "TestSheet", schema, localizationMap);

        // Then
        ValidationError skillsError = findErrorByColumn(errors, "skills");
        if (skillsError != null) {
            assertNotEquals(ValidationConstants.STATUS_INVALID, skillsError.getStatus(),
                "Exactly minimum selections should be valid");
        }
    }

    @Test
    void testMultiSelectValidation_ExactMaximumSelections() {
        // Given - exactly maximum allowed selections
        Map<String, Object> schema = createMultiSelectSchema("skills", 1, 3, 
            Arrays.asList("Java", "Python", "JavaScript", "Go", "Ruby"));
        List<Map<String, Object>> data = Arrays.asList(
            createMultiSelectRowData("skills", Arrays.asList("Java", "Python", "JavaScript")) // Exactly 3
        );

        // When
        List<ValidationError> errors = schemaValidationService.validateDataWithPreFetchedSchema(
            data, "TestSheet", schema, localizationMap);

        // Then
        ValidationError skillsError = findErrorByColumn(errors, "skills");
        if (skillsError != null) {
            assertNotEquals(ValidationConstants.STATUS_INVALID, skillsError.getStatus(),
                "Exactly maximum selections should be valid");
        }
    }

    @Test
    void testMultiSelectValidation_OneAboveMaximum() {
        // Given - one more than maximum allowed
        Map<String, Object> schema = createMultiSelectSchema("skills", 1, 3, 
            Arrays.asList("Java", "Python", "JavaScript", "Go", "Ruby"));
        List<Map<String, Object>> data = Arrays.asList(
            createMultiSelectRowData("skills", Arrays.asList("Java", "Python", "JavaScript", "Go")) // 4 selections
        );

        // When
        List<ValidationError> errors = schemaValidationService.validateDataWithPreFetchedSchema(
            data, "TestSheet", schema, localizationMap);

        // Then
        assertFalse(errors.isEmpty(), "One above maximum should trigger validation error");
        ValidationError skillsError = findErrorByColumn(errors, "skills");
        assertNotNull(skillsError, "Should have error for exceeding maximum selections");
        assertTrue(skillsError.getErrorDetails().contains("at most"), 
            "Should mention maximum selections limit");
    }

    @Test
    void testMultiSelectValidation_OneBelowMinimum() {
        // Given - one less than minimum required
        Map<String, Object> schema = createMultiSelectSchema("skills", 3, 5, 
            Arrays.asList("Java", "Python", "JavaScript", "Go", "Ruby"));
        List<Map<String, Object>> data = Arrays.asList(
            createMultiSelectRowData("skills", Arrays.asList("Java", "Python")) // 2 selections, need 3
        );

        // When
        List<ValidationError> errors = schemaValidationService.validateDataWithPreFetchedSchema(
            data, "TestSheet", schema, localizationMap);

        // Then
        assertFalse(errors.isEmpty(), "One below minimum should trigger validation error");
        ValidationError skillsError = findErrorByColumn(errors, "skills");
        assertNotNull(skillsError, "Should have error for insufficient selections");
        assertTrue(skillsError.getErrorDetails().contains("at least"), 
            "Should mention minimum selections requirement");
    }

    // ==================== EXTREME VALUE TESTS ====================

    @Test
    void testMultiSelectValidation_ZeroMinimumSelections() {
        // Given - minimum of 0 selections (optional multi-select)
        Map<String, Object> schema = createMultiSelectSchema("skills", 0, 3, 
            Arrays.asList("Java", "Python", "JavaScript"));
        List<Map<String, Object>> data = Arrays.asList(
            createMultiSelectRowData("skills", Arrays.asList()) // No selections
        );

        // When
        List<ValidationError> errors = schemaValidationService.validateDataWithPreFetchedSchema(
            data, "TestSheet", schema, localizationMap);

        // Then
        ValidationError skillsError = findErrorByColumn(errors, "skills");
        if (skillsError != null && skillsError.getErrorDetails().contains("at least")) {
            fail("Zero minimum selections should allow empty multi-select");
        }
    }

    @Test
    void testMultiSelectValidation_VeryHighMaximumSelections() {
        // Given - very high maximum (more than available options)
        Map<String, Object> schema = createMultiSelectSchema("skills", 1, 100, 
            Arrays.asList("Java", "Python", "JavaScript")); // Only 3 options, max 100
        List<Map<String, Object>> data = Arrays.asList(
            createMultiSelectRowData("skills", Arrays.asList("Java", "Python", "JavaScript")) // All 3
        );

        // When
        List<ValidationError> errors = schemaValidationService.validateDataWithPreFetchedSchema(
            data, "TestSheet", schema, localizationMap);

        // Then
        ValidationError skillsError = findErrorByColumn(errors, "skills");
        if (skillsError != null) {
            assertNotEquals(ValidationConstants.STATUS_INVALID, skillsError.getStatus(),
                "Should handle high maximum gracefully when all available options are selected");
        }
    }

    // ==================== DUPLICATE DETECTION EDGE CASES ====================

    @Test
    void testMultiSelectValidation_CaseSensitiveDuplicates() {
        // Given - case-sensitive duplicates
        Map<String, Object> schema = createMultiSelectSchema("skills", 1, 5, 
            Arrays.asList("Java", "JAVA", "java", "Python")); // Case-sensitive enum
        List<Map<String, Object>> data = Arrays.asList(
            createMultiSelectRowData("skills", Arrays.asList("Java", "JAVA")) // Different cases
        );

        // When
        List<ValidationError> errors = schemaValidationService.validateDataWithPreFetchedSchema(
            data, "TestSheet", schema, localizationMap);

        // Then
        ValidationError skillsError = findErrorByColumn(errors, "skills");
        if (skillsError != null) {
            // Should treat case-sensitive values as different (no duplicate error)
            assertFalse(skillsError.getErrorDetails().contains("duplicate"),
                "Case-sensitive values should be treated as different");
        }
    }

    @Test
    void testMultiSelectValidation_ExactDuplicates() {
        // Given - exact duplicate values
        Map<String, Object> schema = createMultiSelectSchema("skills", 1, 5, 
            Arrays.asList("Java", "Python", "JavaScript"));
        List<Map<String, Object>> data = Arrays.asList(
            createMultiSelectRowData("skills", Arrays.asList("Java", "Java", "Python")) // Exact duplicate
        );

        // When
        List<ValidationError> errors = schemaValidationService.validateDataWithPreFetchedSchema(
            data, "TestSheet", schema, localizationMap);

        // Then
        assertFalse(errors.isEmpty(), "Exact duplicates should trigger validation error");
        ValidationError skillsError = findErrorByColumn(errors, "skills");
        assertNotNull(skillsError, "Should have error for duplicate selections");
        assertTrue(skillsError.getErrorDetails().contains("duplicate"), 
            "Should mention duplicate selections");
    }

    @Test
    void testMultiSelectValidation_DuplicatesWithWhitespace() {
        // Given - duplicates with different whitespace
        Map<String, Object> schema = createMultiSelectSchema("skills", 1, 5, 
            Arrays.asList("Java", "Python", "JavaScript"));
        Map<String, Object> rowData = new HashMap<>();
        rowData.put("skills_MULTISELECT_1", "Java");
        rowData.put("skills_MULTISELECT_2", " Java "); // Same value with whitespace
        rowData.put("skills_MULTISELECT_3", "Python");
        List<Map<String, Object>> data = Arrays.asList(rowData);

        // When
        List<ValidationError> errors = schemaValidationService.validateDataWithPreFetchedSchema(
            data, "TestSheet", schema, localizationMap);

        // Then
        assertFalse(errors.isEmpty(), "Values with whitespace should be trimmed and considered duplicates");
        ValidationError skillsError = findErrorByColumn(errors, "skills");
        assertNotNull(skillsError, "Should have error for duplicate selections after trimming");
        assertTrue(skillsError.getErrorDetails().contains("duplicate"), 
            "Should detect duplicates after whitespace trimming");
    }

    // ==================== ENUM VALUE EDGE CASES ====================

    @Test
    void testMultiSelectValidation_EmptyEnumValuesList() {
        // Given - empty enum values list
        Map<String, Object> schema = createMultiSelectSchema("skills", 1, 3, 
            Arrays.asList()); // Empty enum list
        List<Map<String, Object>> data = Arrays.asList(
            createMultiSelectRowData("skills", Arrays.asList("AnyValue"))
        );

        // When
        List<ValidationError> errors = schemaValidationService.validateDataWithPreFetchedSchema(
            data, "TestSheet", schema, localizationMap);

        // Then
        // Should handle empty enum list gracefully - might skip enum validation
        assertNotNull(errors, "Should handle empty enum list gracefully");
    }

    @Test
    void testMultiSelectValidation_NullEnumValuesList() {
        // Given - null enum values list
        Map<String, Object> schema = createMultiSelectSchema("skills", 1, 3, null);
        List<Map<String, Object>> data = Arrays.asList(
            createMultiSelectRowData("skills", Arrays.asList("AnyValue"))
        );

        // When
        List<ValidationError> errors = schemaValidationService.validateDataWithPreFetchedSchema(
            data, "TestSheet", schema, localizationMap);

        // Then
        assertNotNull(errors, "Should handle null enum list gracefully");
    }

    @Test
    void testMultiSelectValidation_SpecialCharactersInEnumValues() {
        // Given - enum values with special characters
        Map<String, Object> schema = createMultiSelectSchema("skills", 1, 3, 
            Arrays.asList("C++", "C#", ".NET", "Node.js", "Vue.js"));
        List<Map<String, Object>> data = Arrays.asList(
            createMultiSelectRowData("skills", Arrays.asList("C++", "C#"))
        );

        // When
        List<ValidationError> errors = schemaValidationService.validateDataWithPreFetchedSchema(
            data, "TestSheet", schema, localizationMap);

        // Then
        ValidationError skillsError = findErrorByColumn(errors, "skills");
        if (skillsError != null) {
            assertNotEquals(ValidationConstants.STATUS_INVALID, skillsError.getStatus(),
                "Should handle special characters in enum values");
        }
    }

    // ==================== PERFORMANCE EDGE CASES ====================

    @Test
    void testMultiSelectValidation_ManySelections() {
        // Given - large number of selections
        List<String> manySkills = IntStream.range(1, 101)
            .mapToObj(i -> "Skill" + i)
            .toList();
        Map<String, Object> schema = createMultiSelectSchema("skills", 1, 50, manySkills);
        
        List<String> selectedSkills = manySkills.subList(0, 50); // Select first 50
        List<Map<String, Object>> data = Arrays.asList(
            createMultiSelectRowData("skills", selectedSkills)
        );

        // When
        long startTime = System.currentTimeMillis();
        List<ValidationError> errors = schemaValidationService.validateDataWithPreFetchedSchema(
            data, "TestSheet", schema, localizationMap);
        long endTime = System.currentTimeMillis();

        // Then
        assertTrue(endTime - startTime < 5000, "Many selections validation should complete quickly");
        ValidationError skillsError = findErrorByColumn(errors, "skills");
        if (skillsError != null) {
            assertNotEquals(ValidationConstants.STATUS_INVALID, skillsError.getStatus(),
                "Should handle many valid selections");
        }
    }

    @Test
    void testMultiSelectValidation_ManyDuplicates() {
        // Given - many duplicate values (stress test)
        Map<String, Object> schema = createMultiSelectSchema("skills", 1, 100, 
            Arrays.asList("Java", "Python"));
        
        List<String> manyDuplicates = Arrays.asList(
            "Java", "Java", "Java", "Python", "Python", "Java", "Python"
        );
        List<Map<String, Object>> data = Arrays.asList(
            createMultiSelectRowData("skills", manyDuplicates)
        );

        // When
        List<ValidationError> errors = schemaValidationService.validateDataWithPreFetchedSchema(
            data, "TestSheet", schema, localizationMap);

        // Then
        assertFalse(errors.isEmpty(), "Many duplicates should trigger validation error");
        ValidationError skillsError = findErrorByColumn(errors, "skills");
        assertNotNull(skillsError, "Should detect duplicates efficiently");
        assertTrue(skillsError.getErrorDetails().contains("duplicate"), 
            "Should mention duplicate selections");
    }

    // ==================== REQUIRED FIELD EDGE CASES ====================

    @Test
    void testMultiSelectValidation_RequiredWithZeroMinimum() {
        // Given - required field but minimum is 0 (edge case)
        Map<String, Object> schema = createRequiredMultiSelectSchema("skills", 0, 3, 
            Arrays.asList("Java", "Python", "JavaScript"));
        List<Map<String, Object>> data = Arrays.asList(
            createMultiSelectRowData("skills", Arrays.asList()) // Empty
        );

        // When
        List<ValidationError> errors = schemaValidationService.validateDataWithPreFetchedSchema(
            data, "TestSheet", schema, localizationMap);

        // Then
        // Should trigger required field validation even though min is 0
        assertFalse(errors.isEmpty(), "Required field should trigger error even with min=0");
        ValidationError skillsError = findErrorByColumn(errors, "skills");
        assertNotNull(skillsError, "Should have error for required empty field");
    }

    @Test
    void testMultiSelectValidation_RequiredWithHighMinimum() {
        // Given - required field with high minimum
        Map<String, Object> schema = createRequiredMultiSelectSchema("skills", 5, 10, 
            Arrays.asList("Java", "Python", "JavaScript", "Go", "Ruby", "C++", "C#"));
        List<Map<String, Object>> data = Arrays.asList(
            createMultiSelectRowData("skills", Arrays.asList()) // Empty
        );

        // When
        List<ValidationError> errors = schemaValidationService.validateDataWithPreFetchedSchema(
            data, "TestSheet", schema, localizationMap);

        // Then
        assertFalse(errors.isEmpty(), "Required field with high minimum should trigger error");
        ValidationError skillsError = findErrorByColumn(errors, "skills");
        assertNotNull(skillsError, "Should have error for required empty field with high minimum");
        // Could have both required and min selection errors
        assertTrue(skillsError.getErrorDetails().contains("Required") || 
                  skillsError.getErrorDetails().contains("at least"),
                  "Should mention either required field or minimum selections");
    }

    // ==================== HELPER METHODS ====================

    private void setupLocalizationMap() {
        localizationMap = new HashMap<>();
        localizationMap.put("skills", "Skills");
        localizationMap.put("languages", "Programming Languages");
        localizationMap.put("tools", "Development Tools");
    }

    private Map<String, Object> createMultiSelectSchema(String fieldName, int minSelections, 
                                                       int maxSelections, List<String> enumValues) {
        Map<String, Object> schema = new HashMap<>();
        
        Map<String, Object> enumProperty = new HashMap<>();
        enumProperty.put("name", fieldName);
        enumProperty.put("isRequired", false);
        enumProperty.put("enum", enumValues);
        
        Map<String, Object> multiSelectDetails = new HashMap<>();
        multiSelectDetails.put("enum", enumValues);
        multiSelectDetails.put("minSelections", minSelections);
        multiSelectDetails.put("maxSelections", maxSelections);
        enumProperty.put("multiSelectDetails", multiSelectDetails);
        
        schema.put("enumProperties", Arrays.asList(enumProperty));
        return schema;
    }

    private Map<String, Object> createRequiredMultiSelectSchema(String fieldName, int minSelections, 
                                                               int maxSelections, List<String> enumValues) {
        Map<String, Object> schema = createMultiSelectSchema(fieldName, minSelections, maxSelections, enumValues);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> enumProps = (List<Map<String, Object>>) schema.get("enumProperties");
        enumProps.get(0).put("isRequired", true);
        return schema;
    }

    private Map<String, Object> createMultiSelectRowData(String fieldName, List<String> selections) {
        Map<String, Object> rowData = new HashMap<>();
        
        // Add individual multi-select columns
        for (int i = 0; i < selections.size(); i++) {
            rowData.put(fieldName + "_MULTISELECT_" + (i + 1), selections.get(i));
        }
        
        // Add empty columns for remaining slots (up to 10 for testing)
        for (int i = selections.size(); i < 10; i++) {
            rowData.put(fieldName + "_MULTISELECT_" + (i + 1), "");
        }
        
        return rowData;
    }

    private ValidationError findErrorByColumn(List<ValidationError> errors, String columnName) {
        return errors.stream()
            .filter(error -> columnName.equals(error.getColumnName()))
            .findFirst()
            .orElse(null);
    }
}