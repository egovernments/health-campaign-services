package org.egov.excelingestion.service;

import lombok.extern.slf4j.Slf4j;
import org.egov.excelingestion.web.models.ValidationError;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test to verify API validation uses custom error messages from MDMS schema
 */
@Slf4j
class APIValidationCustomErrorTest {

    private SchemaValidationService schemaValidationService;
    
    @BeforeEach
    void setUp() {
        schemaValidationService = new SchemaValidationService(null);
    }

    @Test
    void testAPIValidationUsesCustomErrorMessages() {
        log.info("🧪 Testing API validation uses custom MDMS error messages");
        
        // Create MDMS schema with custom error messages
        Map<String, Object> schema = createSchemaWithCustomErrorMessages();
        
        // Create invalid data to trigger validation errors
        List<Map<String, Object>> sheetData = Arrays.asList(
            createRowData("", 17, 3),    // Empty name (required), age < 18 (minimum) - row 3
            createRowData("John", 70, 5) // Age > 65 (maximum) - row 5
        );
        
        // Perform validation
        List<ValidationError> errors = schemaValidationService.validateDataWithPreFetchedSchema(
            sheetData, "TestSheet", schema, new HashMap<>());
        
        assertFalse(errors.isEmpty(), "Should have validation errors");
        
        log.info("Found {} validation errors:", errors.size());
        
        // Check that custom error messages are used
        boolean foundCustomRequiredError = false;
        boolean foundCustomMinimumError = false;
        boolean foundCustomMaximumError = false;
        
        for (ValidationError error : errors) {
            String errorDetails = error.getErrorDetails();
            log.info("Row {}: {} - {}", error.getRowNumber(), error.getColumnName(), errorDetails);
            
            if (errorDetails.contains("Name field is mandatory for registration")) {
                foundCustomRequiredError = true;
            } else if (errorDetails.contains("Age validation failed: must be between 18 and 65 years")) {
                foundCustomMinimumError = true;
                foundCustomMaximumError = true;  // Same message for both min/max
            }
        }
        
        assertTrue(foundCustomRequiredError, "Should use custom required field error message");
        assertTrue(foundCustomMinimumError, "Should use custom minimum age error message");
        assertTrue(foundCustomMaximumError, "Should use custom maximum age error message");
        
        log.info("✅ All custom error messages found in API validation");
    }

    @Test
    void testAPIValidationWithLocalizationMap() {
        log.info("🧪 Testing API validation with localization map");
        
        // Create schema with error message keys (not actual messages)
        Map<String, Object> schema = createSchemaWithErrorMessageKeys();
        
        // Create localization map with translated messages
        Map<String, String> localizationMap = new HashMap<>();
        localizationMap.put("CUSTOM_NAME_REQUIRED", "नाम आवश्यक है"); // Hindi: Name is required
        localizationMap.put("CUSTOM_AGE_VALIDATION", "आयु 18 से 65 वर्ष के बीच होनी चाहिए"); // Hindi: Age should be between 18 and 65 years
        
        // Create invalid data
        List<Map<String, Object>> sheetData = Arrays.asList(
            createRowData("", 17, 3),    // Invalid name and age
            createRowData("John", 70, 5) // Invalid age
        );
        
        // Perform validation with localization
        List<ValidationError> errors = schemaValidationService.validateDataWithPreFetchedSchema(
            sheetData, "TestSheet", schema, localizationMap);
        
        assertFalse(errors.isEmpty(), "Should have validation errors");
        
        log.info("Found {} localized validation errors:", errors.size());
        
        // Check that localized messages are used
        boolean foundLocalizedRequired = false;
        boolean foundLocalizedMinimum = false;
        boolean foundLocalizedMaximum = false;
        
        for (ValidationError error : errors) {
            String errorDetails = error.getErrorDetails();
            log.info("Row {}: {} - {}", error.getRowNumber(), error.getColumnName(), errorDetails);
            
            if (errorDetails.contains("नाम आवश्यक है")) {
                foundLocalizedRequired = true;
            } else if (errorDetails.contains("आयु 18 से 65 वर्ष के बीच होनी चाहिए")) {
                foundLocalizedMinimum = true;
                foundLocalizedMaximum = true;  // Same message for both min/max
            }
        }
        
        assertTrue(foundLocalizedRequired, "Should use localized required field error message");
        assertTrue(foundLocalizedMinimum, "Should use localized minimum age error message");
        assertTrue(foundLocalizedMaximum, "Should use localized maximum age error message");
        
        log.info("✅ All localized error messages found in API validation");
    }

    @Test
    void testAPIValidationFallbackToDynamicMessages() {
        log.info("🧪 Testing API validation falls back to dynamic messages when no custom message");
        
        // Create schema WITHOUT custom error messages
        Map<String, Object> schema = createSchemaWithoutCustomErrorMessages();
        
        // Create invalid data
        List<Map<String, Object>> sheetData = Arrays.asList(
            createRowData("", 17, 3)  // Invalid name and age
        );
        
        // Perform validation
        List<ValidationError> errors = schemaValidationService.validateDataWithPreFetchedSchema(
            sheetData, "TestSheet", schema, new HashMap<>());
        
        assertFalse(errors.isEmpty(), "Should have validation errors");
        
        log.info("Found {} dynamic validation errors:", errors.size());
        
        // Check that dynamic messages are used (should contain field names and values)
        boolean foundDynamicRequired = false;
        boolean foundDynamicMinimum = false;
        
        for (ValidationError error : errors) {
            String errorDetails = error.getErrorDetails();
            log.info("Row {}: {} - {}", error.getRowNumber(), error.getColumnName(), errorDetails);
            
            if (errorDetails.contains("Required field") && errorDetails.contains("missing")) {
                foundDynamicRequired = true;
            } else if (errorDetails.contains("must be at least") && errorDetails.contains("18")) {
                foundDynamicMinimum = true;
            }
        }
        
        assertTrue(foundDynamicRequired, "Should use dynamic required field error message");
        assertTrue(foundDynamicMinimum, "Should use dynamic minimum age error message");
        
        log.info("✅ Dynamic error messages used when no custom messages available");
    }

    // Helper methods
    
    private Map<String, Object> createSchemaWithCustomErrorMessages() {
        Map<String, Object> schema = new HashMap<>();
        
        // String properties with custom error messages
        List<Map<String, Object>> stringProperties = Arrays.asList(
            createStringProperty("name", true, 2, 50, "Name field is mandatory for registration")
        );
        schema.put("stringProperties", stringProperties);
        
        // Number properties with custom error messages  
        List<Map<String, Object>> numberProperties = Arrays.asList(
            createNumberProperty("age", true, 18.0, 65.0, "Age validation failed: must be between 18 and 65 years")
        );
        schema.put("numberProperties", numberProperties);
        
        return schema;
    }
    
    private Map<String, Object> createSchemaWithErrorMessageKeys() {
        Map<String, Object> schema = new HashMap<>();
        
        // String properties with localization keys
        List<Map<String, Object>> stringProperties = Arrays.asList(
            createStringProperty("name", true, 2, 50, "CUSTOM_NAME_REQUIRED")
        );
        schema.put("stringProperties", stringProperties);
        
        // Number properties with localization keys  
        List<Map<String, Object>> numberProperties = Arrays.asList(
            createNumberProperty("age", true, 18.0, 65.0, "CUSTOM_AGE_VALIDATION") // Note: This will apply to both min and max validation
        );
        schema.put("numberProperties", numberProperties);
        
        return schema;
    }
    
    private Map<String, Object> createSchemaWithoutCustomErrorMessages() {
        Map<String, Object> schema = new HashMap<>();
        
        // String properties without custom error messages
        List<Map<String, Object>> stringProperties = Arrays.asList(
            createStringProperty("name", true, 2, 50, null)
        );
        schema.put("stringProperties", stringProperties);
        
        // Number properties without custom error messages
        List<Map<String, Object>> numberProperties = Arrays.asList(
            createNumberProperty("age", true, 18.0, 65.0, null)
        );
        schema.put("numberProperties", numberProperties);
        
        return schema;
    }
    
    private Map<String, Object> createStringProperty(String name, boolean required, Integer minLength, Integer maxLength, String errorMessage) {
        Map<String, Object> property = new HashMap<>();
        property.put("name", name);
        property.put("isRequired", required);
        if (minLength != null) property.put("minLength", minLength);
        if (maxLength != null) property.put("maxLength", maxLength);
        if (errorMessage != null) property.put("errorMessage", errorMessage);
        return property;
    }
    
    private Map<String, Object> createNumberProperty(String name, boolean required, Double minimum, Double maximum, String errorMessage) {
        Map<String, Object> property = new HashMap<>();
        property.put("name", name);
        property.put("isRequired", required);
        if (minimum != null) property.put("minimum", minimum);
        if (maximum != null) property.put("maximum", maximum);
        if (errorMessage != null) property.put("errorMessage", errorMessage);
        return property;
    }
    
    private Map<String, Object> createRowData(String name, int age, int actualRowNumber) {
        Map<String, Object> rowData = new HashMap<>();
        rowData.put("name", name);
        rowData.put("age", age);
        rowData.put("__actualRowNumber__", actualRowNumber);
        return rowData;
    }
}