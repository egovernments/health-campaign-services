package org.egov.excelingestion.service;

import lombok.extern.slf4j.Slf4j;
import org.egov.excelingestion.web.models.ValidationError;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test to verify all 7 remaining validation types use custom error messages from MDMS schema
 */
@Slf4j
class AllValidationTypesCustomErrorTest {

    private SchemaValidationService schemaValidationService;
    
    @BeforeEach
    void setUp() {
        schemaValidationService = new SchemaValidationService(null);
    }

    @Test
    void testAllValidationTypesWithCustomErrorMessages() {
        log.info("🧪 Testing all 7 validation types use custom MDMS error messages");
        
        // Create MDMS schema with custom error messages for all validation types
        Map<String, Object> schema = createSchemaWithAllCustomErrors();
        
        // Create invalid data to trigger all validation errors
        List<Map<String, Object>> sheetData = Arrays.asList(
            createInvalidRowData()
        );
        
        // Perform validation
        List<ValidationError> errors = schemaValidationService.validateDataWithPreFetchedSchema(
            sheetData, "TestSheet", schema, new HashMap<>());
        
        assertFalse(errors.isEmpty(), "Should have validation errors");
        
        log.info("Found {} validation errors:", errors.size());
        
        // Check that custom error messages are used for all validation types
        boolean foundPatternError = false;
        boolean foundExclusiveMinError = false; 
        boolean foundExclusiveMaxError = false;
        boolean foundMultipleOfError = false;
        boolean foundNumberFormatError = false;
        boolean foundEnumError = false;
        
        for (ValidationError error : errors) {
            String errorDetails = error.getErrorDetails();
            log.info("Row {}: {} - {}", error.getRowNumber(), error.getColumnName(), errorDetails);
            
            if (errorDetails.contains("Please enter a valid phone number format")) {
                foundPatternError = true;
            } else if (errorDetails.contains("Score must be greater than zero")) {
                foundExclusiveMinError = true;
            } else if (errorDetails.contains("Percentage must be less than 100")) {
                foundExclusiveMaxError = true;
            } else if (errorDetails.contains("Count must be in multiples of 5")) {
                foundMultipleOfError = true;
            } else if (errorDetails.contains("Please enter a valid numeric value")) {
                foundNumberFormatError = true;
            } else if (errorDetails.contains("Please select a valid gender option")) {
                foundEnumError = true;
            }
        }
        
        assertTrue(foundPatternError, "Should use custom pattern error message");
        assertTrue(foundExclusiveMinError, "Should use custom exclusive minimum error message");
        assertTrue(foundExclusiveMaxError, "Should use custom exclusive maximum error message");
        assertTrue(foundMultipleOfError, "Should use custom multiple of error message");
        assertTrue(foundNumberFormatError, "Should use custom number format error message");
        assertTrue(foundEnumError, "Should use custom enum error message");
        
        log.info("✅ All custom error messages found for validation types");
    }

    @Test
    void testAllValidationTypesWithLocalization() {
        log.info("🧪 Testing all 7 validation types with localization");
        
        // Create schema with error message keys (not actual messages)
        Map<String, Object> schema = createSchemaWithErrorMessageKeys();
        
        // Create localization map with translated messages
        Map<String, String> localizationMap = new HashMap<>();
        localizationMap.put("CUSTOM_PATTERN_ERROR", "कृपया वैध फोन नंबर प्रारूप दर्ज करें"); // Hindi
        localizationMap.put("CUSTOM_EXCLUSIVE_MIN_ERROR", "स्कोर शून्य से अधिक होना चाहिए"); // Hindi
        localizationMap.put("CUSTOM_EXCLUSIVE_MAX_ERROR", "प्रतिशत 100 से कम होना चाहिए"); // Hindi
        localizationMap.put("CUSTOM_MULTIPLE_ERROR", "संख्या 5 के गुणज में होनी चाहिए"); // Hindi
        localizationMap.put("CUSTOM_NUMBER_ERROR", "कृपया वैध संख्यात्मक मान दर्ज करें"); // Hindi
        localizationMap.put("CUSTOM_ENUM_ERROR", "कृपया वैध लिंग विकल्प चुनें"); // Hindi
        
        // Create invalid data
        List<Map<String, Object>> sheetData = Arrays.asList(
            createInvalidRowData()
        );
        
        // Perform validation with localization
        List<ValidationError> errors = schemaValidationService.validateDataWithPreFetchedSchema(
            sheetData, "TestSheet", schema, localizationMap);
        
        assertFalse(errors.isEmpty(), "Should have validation errors");
        
        log.info("Found {} localized validation errors:", errors.size());
        
        // Check that localized messages are used
        boolean foundLocalizedPattern = false;
        boolean foundLocalizedExclusiveMin = false;
        boolean foundLocalizedExclusiveMax = false;
        boolean foundLocalizedMultiple = false;
        boolean foundLocalizedNumber = false;
        boolean foundLocalizedEnum = false;
        
        for (ValidationError error : errors) {
            String errorDetails = error.getErrorDetails();
            log.info("Row {}: {} - {}", error.getRowNumber(), error.getColumnName(), errorDetails);
            
            if (errorDetails.contains("कृपया वैध फोन नंबर प्रारूप दर्ज करें")) {
                foundLocalizedPattern = true;
            } else if (errorDetails.contains("स्कोर शून्य से अधिक होना चाहिए")) {
                foundLocalizedExclusiveMin = true;
            } else if (errorDetails.contains("प्रतिशत 100 से कम होना चाहिए")) {
                foundLocalizedExclusiveMax = true;
            } else if (errorDetails.contains("संख्या 5 के गुणज में होनी चाहिए")) {
                foundLocalizedMultiple = true;
            } else if (errorDetails.contains("कृपया वैध संख्यात्मक मान दर्ज करें")) {
                foundLocalizedNumber = true;
            } else if (errorDetails.contains("कृपया वैध लिंग विकल्प चुनें")) {
                foundLocalizedEnum = true;
            }
        }
        
        assertTrue(foundLocalizedPattern, "Should use localized pattern error message");
        assertTrue(foundLocalizedExclusiveMin, "Should use localized exclusive min error message");
        assertTrue(foundLocalizedExclusiveMax, "Should use localized exclusive max error message");
        assertTrue(foundLocalizedMultiple, "Should use localized multiple of error message");
        assertTrue(foundLocalizedNumber, "Should use localized number format error message");
        assertTrue(foundLocalizedEnum, "Should use localized enum error message");
        
        log.info("✅ All localized error messages found in validation");
    }

    // Helper methods
    
    private Map<String, Object> createSchemaWithAllCustomErrors() {
        Map<String, Object> schema = new HashMap<>();
        
        // String properties with pattern validation
        List<Map<String, Object>> stringProperties = Arrays.asList(
            createStringProperty("phone", true, null, null, "^\\d{10}$", "Please enter a valid phone number format")
        );
        schema.put("stringProperties", stringProperties);
        
        // Number properties with all number validation types
        List<Map<String, Object>> numberProperties = Arrays.asList(
            createNumberProperty("score", true, null, null, 0.0, null, null, "Score must be greater than zero"),
            createNumberProperty("percentage", true, null, null, null, 100.0, null, "Percentage must be less than 100"),
            createNumberProperty("count", true, null, null, null, null, 5.0, "Count must be in multiples of 5"),
            createNumberProperty("invalidNumber", true, null, null, null, null, null, "Please enter a valid numeric value")
        );
        schema.put("numberProperties", numberProperties);
        
        // Enum properties
        List<Map<String, Object>> enumProperties = Arrays.asList(
            createEnumProperty("gender", true, Arrays.asList("Male", "Female", "Other"), "Please select a valid gender option")
        );
        schema.put("enumProperties", enumProperties);
        
        return schema;
    }
    
    private Map<String, Object> createSchemaWithErrorMessageKeys() {
        Map<String, Object> schema = new HashMap<>();
        
        // String properties with localization keys
        List<Map<String, Object>> stringProperties = Arrays.asList(
            createStringProperty("phone", true, null, null, "^\\d{10}$", "CUSTOM_PATTERN_ERROR")
        );
        schema.put("stringProperties", stringProperties);
        
        // Number properties with localization keys  
        List<Map<String, Object>> numberProperties = Arrays.asList(
            createNumberProperty("score", true, null, null, 0.0, null, null, "CUSTOM_EXCLUSIVE_MIN_ERROR"),
            createNumberProperty("percentage", true, null, null, null, 100.0, null, "CUSTOM_EXCLUSIVE_MAX_ERROR"),
            createNumberProperty("count", true, null, null, null, null, 5.0, "CUSTOM_MULTIPLE_ERROR"),
            createNumberProperty("invalidNumber", true, null, null, null, null, null, "CUSTOM_NUMBER_ERROR")
        );
        schema.put("numberProperties", numberProperties);
        
        // Enum properties with localization keys
        List<Map<String, Object>> enumProperties = Arrays.asList(
            createEnumProperty("gender", true, Arrays.asList("Male", "Female", "Other"), "CUSTOM_ENUM_ERROR")
        );
        schema.put("enumProperties", enumProperties);
        
        return schema;
    }
    
    private Map<String, Object> createInvalidRowData() {
        Map<String, Object> rowData = new HashMap<>();
        rowData.put("phone", "123");  // Invalid pattern (should be 10 digits)
        rowData.put("score", 0);      // Invalid exclusiveMinimum (should be > 0)
        rowData.put("percentage", 100); // Invalid exclusiveMaximum (should be < 100)
        rowData.put("count", 7);      // Invalid multipleOf (should be multiple of 5)
        rowData.put("invalidNumber", "not_a_number"); // Invalid number format
        rowData.put("gender", "Unknown"); // Invalid enum value
        rowData.put("__actualRowNumber__", 3);
        return rowData;
    }
    
    private Map<String, Object> createStringProperty(String name, boolean required, Integer minLength, Integer maxLength, String pattern, String errorMessage) {
        Map<String, Object> property = new HashMap<>();
        property.put("name", name);
        property.put("isRequired", required);
        if (minLength != null) property.put("minLength", minLength);
        if (maxLength != null) property.put("maxLength", maxLength);
        if (pattern != null) property.put("pattern", pattern);
        if (errorMessage != null) property.put("errorMessage", errorMessage);
        return property;
    }
    
    private Map<String, Object> createNumberProperty(String name, boolean required, Double minimum, Double maximum, 
            Double exclusiveMinimum, Double exclusiveMaximum, Double multipleOf, String errorMessage) {
        Map<String, Object> property = new HashMap<>();
        property.put("name", name);
        property.put("isRequired", required);
        if (minimum != null) property.put("minimum", minimum);
        if (maximum != null) property.put("maximum", maximum);
        if (exclusiveMinimum != null) property.put("exclusiveMinimum", exclusiveMinimum);
        if (exclusiveMaximum != null) property.put("exclusiveMaximum", exclusiveMaximum);
        if (multipleOf != null) property.put("multipleOf", multipleOf);
        if (errorMessage != null) property.put("errorMessage", errorMessage);
        return property;
    }
    
    private Map<String, Object> createEnumProperty(String name, boolean required, List<String> enumValues, String errorMessage) {
        Map<String, Object> property = new HashMap<>();
        property.put("name", name);
        property.put("isRequired", required);
        property.put("enum", enumValues);
        if (errorMessage != null) property.put("errorMessage", errorMessage);
        return property;
    }
}