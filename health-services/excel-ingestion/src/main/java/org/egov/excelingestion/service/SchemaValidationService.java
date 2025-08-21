package org.egov.excelingestion.service;

import lombok.extern.slf4j.Slf4j;
import org.egov.excelingestion.config.ProcessingConstants;
import org.egov.excelingestion.config.ValidationConstants;
import org.egov.excelingestion.web.models.ValidationError;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;

@Service
@Slf4j
public class SchemaValidationService {
    
    private final MDMSService mdmsService;
    
    public SchemaValidationService(MDMSService mdmsService) {
        this.mdmsService = mdmsService;
    }

    /**
     * Validates sheet data against pre-fetched schema with localization support
     */
    public List<ValidationError> validateDataWithPreFetchedSchema(List<Map<String, Object>> sheetData,
                                                                String sheetName, 
                                                                Map<String, Object> schema,
                                                                Map<String, String> localizationMap) {
        List<ValidationError> errors = new ArrayList<>();
        
        if (schema == null) {
            log.info("No schema provided for validation of sheet: {}", sheetName);
            return errors;
        }
        
        try {
            // Extract validation rules from schema
            Map<String, ValidationRule> validationRules = extractValidationRules(schema);
            
            // Validate each row against the schema 
            for (int rowIndex = 0; rowIndex < sheetData.size(); rowIndex++) {
                Map<String, Object> rowData = sheetData.get(rowIndex);
                int excelRowNumber = rowIndex + 3; // +1 for 0-based to 1-based, +2 for two header rows
                
                List<ValidationError> rowErrors = validateRowAgainstSchema(
                    rowData, excelRowNumber, sheetName, validationRules, localizationMap);
                errors.addAll(rowErrors);
            }
            
            // Perform uniqueness validation across all rows
            List<ValidationError> uniquenessErrors = validateUniquenessConstraints(
                sheetData, sheetName, validationRules, localizationMap);
            errors.addAll(uniquenessErrors);
            
        } catch (Exception e) {
            log.error("Error during schema validation for sheet {}: {}", sheetName, e.getMessage(), e);
            ValidationError error = ValidationError.builder()
                    .rowNumber(1)
                    .sheetName(sheetName)
                    .status(ValidationConstants.STATUS_ERROR)
                    .errorDetails("Schema validation failed: " + e.getMessage())
                    .build();
            errors.add(error);
        }
        
        return errors;
    }


    /**
     * Extracts validation rules from schema
     */
    private Map<String, ValidationRule> extractValidationRules(Map<String, Object> schema) {
        Map<String, ValidationRule> rules = new HashMap<>();
        
        if (schema != null) {
            // The schema passed here is already the properties section from MDMS
            // Look for stringProperties, numberProperties, enumProperties directly
            extractStringPropertyRules(schema, rules);
            extractNumberPropertyRules(schema, rules);
            extractEnumPropertyRules(schema, rules);
        }
        
        log.info("Extracted {} validation rules from schema", rules.size());
        return rules;
    }

    /**
     * Extracts string property validation rules
     */
    @SuppressWarnings("unchecked")
    private void extractStringPropertyRules(Map<String, Object> schema, Map<String, ValidationRule> rules) {
        if (schema.containsKey("stringProperties")) {
            List<Map<String, Object>> stringProps = (List<Map<String, Object>>) schema.get("stringProperties");
            for (Map<String, Object> prop : stringProps) {
                String name = (String) prop.get("name");
                if (name != null) {
                    ValidationRule rule = new ValidationRule();
                    rule.setFieldName(name);
                    rule.setType("string");
                    rule.setRequired((Boolean) prop.getOrDefault("isRequired", false));
                    rule.setMinLength((Integer) prop.get("minLength"));
                    rule.setMaxLength((Integer) prop.get("maxLength"));
                    rule.setUnique((Boolean) prop.getOrDefault("isUnique", false));
                    rule.setErrorMessage((String) prop.get("errorMessage"));
                    
                    // New string validation properties
                    rule.setPattern((String) prop.get("pattern"));
                    
                    // Multi-select validation
                    if (prop.containsKey("multiSelectDetails")) {
                        Map<String, Object> multiSelectMap = (Map<String, Object>) prop.get("multiSelectDetails");
                        MultiSelectDetails multiSelect = new MultiSelectDetails();
                        multiSelect.setEnumValues((List<String>) multiSelectMap.get("enum"));
                        multiSelect.setMinSelections((Integer) multiSelectMap.get("minSelections"));
                        multiSelect.setMaxSelections((Integer) multiSelectMap.get("maxSelections"));
                        rule.setMultiSelectDetails(multiSelect);
                    }
                    
                    rules.put(name, rule);
                }
            }
        }
    }

    /**
     * Extracts number property validation rules
     */
    @SuppressWarnings("unchecked")
    private void extractNumberPropertyRules(Map<String, Object> schema, Map<String, ValidationRule> rules) {
        if (schema.containsKey("numberProperties")) {
            List<Map<String, Object>> numberProps = (List<Map<String, Object>>) schema.get("numberProperties");
            for (Map<String, Object> prop : numberProps) {
                String name = (String) prop.get("name");
                if (name != null) {
                    ValidationRule rule = new ValidationRule();
                    rule.setFieldName(name);
                    rule.setType("number");
                    rule.setRequired((Boolean) prop.getOrDefault("isRequired", false));
                    rule.setMinimum((Number) prop.get("minimum"));
                    rule.setMaximum((Number) prop.get("maximum"));
                    rule.setErrorMessage((String) prop.get("errorMessage"));
                    
                    // New number validation properties
                    rule.setMultipleOf((Number) prop.get("multipleOf"));
                    rule.setExclusiveMinimum((Number) prop.get("exclusiveMinimum"));
                    rule.setExclusiveMaximum((Number) prop.get("exclusiveMaximum"));
                    
                    rules.put(name, rule);
                }
            }
        }
    }

    /**
     * Extracts enum property validation rules
     */
    @SuppressWarnings("unchecked")
    private void extractEnumPropertyRules(Map<String, Object> schema, Map<String, ValidationRule> rules) {
        if (schema.containsKey("enumProperties")) {
            List<Map<String, Object>> enumProps = (List<Map<String, Object>>) schema.get("enumProperties");
            for (Map<String, Object> prop : enumProps) {
                String name = (String) prop.get("name");
                if (name != null) {
                    ValidationRule rule = new ValidationRule();
                    rule.setFieldName(name);
                    rule.setType("enum");
                    rule.setRequired((Boolean) prop.getOrDefault("isRequired", false));
                    rule.setAllowedValues((List<String>) prop.get("enum"));
                    rule.setErrorMessage((String) prop.get("errorMessage"));
                    
                    rules.put(name, rule);
                }
            }
        }
    }

    /**
     * Validates a single row against schema rules
     */
    private List<ValidationError> validateRowAgainstSchema(Map<String, Object> rowData, 
            int rowNumber, String sheetName, Map<String, ValidationRule> rules, Map<String, String> localizationMap) {
        
        List<ValidationError> errors = new ArrayList<>();
        
        // First, validate fields that have direct rules
        for (ValidationRule rule : rules.values()) {
            String fieldName = rule.getFieldName();
            Object value = rowData.get(fieldName);
            
            validateField(fieldName, value, rule, rowNumber, sheetName, errors, localizationMap);
        }
        
        // Second, validate child multi-select fields (fields ending with _1, _2, etc.)
        for (String fieldName : rowData.keySet()) {
            if (isChildMultiSelectField(fieldName) && !rules.containsKey(fieldName)) {
                String parentFieldName = getParentFieldName(fieldName);
                ValidationRule parentRule = rules.get(parentFieldName);
                
                if (parentRule != null && parentRule.getMultiSelectDetails() != null) {
                    Object value = rowData.get(fieldName);
                    // Validate child field using parent's multi-select rules
                    validateChildMultiSelectField(fieldName, value, parentRule, rowNumber, sheetName, errors, localizationMap);
                }
            }
        }
        
        // If no validation errors, mark row as valid
        if (errors.isEmpty()) {
            errors.add(ValidationError.builder()
                    .rowNumber(rowNumber)
                    .sheetName(sheetName)
                    .status(ValidationConstants.STATUS_VALID)
                    .errorDetails("")
                    .build());
        }
        
        return errors;
    }

    private void validateStringField(Object value, ValidationRule rule, int rowNumber, 
            String sheetName, List<ValidationError> errors, Map<String, String> localizationMap) {
        
        String strValue = value.toString();
        
        // Length validation
        if (rule.getMinLength() != null && strValue.length() < rule.getMinLength()) {
            String errorMessage = getLocalizedMessage(localizationMap, "HCM_VALIDATION_MIN_LENGTH", 
                    String.format("Field '%s' must be at least %d characters", rule.getFieldName(), rule.getMinLength()),
                    rule.getFieldName(), rule.getMinLength().toString());
            errors.add(createValidationError(rowNumber, sheetName, rule.getFieldName(), errorMessage));
        }
        
        if (rule.getMaxLength() != null && strValue.length() > rule.getMaxLength()) {
            String errorMessage = getLocalizedMessage(localizationMap, "HCM_VALIDATION_MAX_LENGTH", 
                    String.format("Field '%s' must not exceed %d characters", rule.getFieldName(), rule.getMaxLength()),
                    rule.getFieldName(), rule.getMaxLength().toString());
            errors.add(createValidationError(rowNumber, sheetName, rule.getFieldName(), errorMessage));
        }
        
        // Pattern validation
        if (rule.getPattern() != null && !rule.getPattern().trim().isEmpty()) {
            try {
                Pattern pattern = Pattern.compile(rule.getPattern());
                if (!pattern.matcher(strValue).matches()) {
                    String errorMessage = getLocalizedMessage(localizationMap, "HCM_VALIDATION_PATTERN", 
                            String.format("Field '%s' does not match required pattern", rule.getFieldName()),
                            rule.getFieldName());
                    errors.add(createValidationError(rowNumber, sheetName, rule.getFieldName(), errorMessage));
                }
            } catch (PatternSyntaxException e) {
                log.warn("Invalid regex pattern '{}' for field '{}'", rule.getPattern(), rule.getFieldName());
                String errorMessage = getLocalizedMessage(localizationMap, "HCM_VALIDATION_INVALID_PATTERN", 
                        String.format("Field '%s' has invalid validation pattern", rule.getFieldName()),
                        rule.getFieldName());
                errors.add(createValidationError(rowNumber, sheetName, rule.getFieldName(), errorMessage));
            }
        }
        
        // Multi-select validation
        if (rule.getMultiSelectDetails() != null) {
            validateMultiSelectField(strValue, rule, rowNumber, sheetName, errors, localizationMap);
        }
    }

    private void validateNumberField(Object value, ValidationRule rule, int rowNumber, 
            String sheetName, List<ValidationError> errors, Map<String, String> localizationMap) {
        
        try {
            Double numValue = Double.parseDouble(value.toString());
            
            if (rule.getMinimum() != null && numValue < rule.getMinimum().doubleValue()) {
                String errorMessage = getLocalizedMessage(localizationMap, "HCM_VALIDATION_MIN_NUMBER", 
                        String.format("Field '%s' must be at least %s", rule.getFieldName(), rule.getMinimum()),
                        rule.getFieldName(), rule.getMinimum().toString());
                errors.add(createValidationError(rowNumber, sheetName, rule.getFieldName(), errorMessage));
            }
            
            if (rule.getMaximum() != null && numValue > rule.getMaximum().doubleValue()) {
                String errorMessage = getLocalizedMessage(localizationMap, "HCM_VALIDATION_MAX_NUMBER", 
                        String.format("Field '%s' must not exceed %s", rule.getFieldName(), rule.getMaximum()),
                        rule.getFieldName(), rule.getMaximum().toString());
                errors.add(createValidationError(rowNumber, sheetName, rule.getFieldName(), errorMessage));
            }
            
            // Exclusive minimum validation
            if (rule.getExclusiveMinimum() != null && numValue <= rule.getExclusiveMinimum().doubleValue()) {
                String errorMessage = getLocalizedMessage(localizationMap, "HCM_VALIDATION_EXCLUSIVE_MIN", 
                        String.format("Field '%s' must be greater than %s", rule.getFieldName(), rule.getExclusiveMinimum()),
                        rule.getFieldName(), rule.getExclusiveMinimum().toString());
                errors.add(createValidationError(rowNumber, sheetName, rule.getFieldName(), errorMessage));
            }
            
            // Exclusive maximum validation
            if (rule.getExclusiveMaximum() != null && numValue >= rule.getExclusiveMaximum().doubleValue()) {
                String errorMessage = getLocalizedMessage(localizationMap, "HCM_VALIDATION_EXCLUSIVE_MAX", 
                        String.format("Field '%s' must be less than %s", rule.getFieldName(), rule.getExclusiveMaximum()),
                        rule.getFieldName(), rule.getExclusiveMaximum().toString());
                errors.add(createValidationError(rowNumber, sheetName, rule.getFieldName(), errorMessage));
            }
            
            // Multiple of validation
            if (rule.getMultipleOf() != null) {
                double divisor = rule.getMultipleOf().doubleValue();
                if (divisor != 0 && (numValue % divisor) != 0) {
                    String errorMessage = getLocalizedMessage(localizationMap, "HCM_VALIDATION_MULTIPLE_OF", 
                            String.format("Field '%s' must be a multiple of %s", rule.getFieldName(), rule.getMultipleOf()),
                            rule.getFieldName(), rule.getMultipleOf().toString());
                    errors.add(createValidationError(rowNumber, sheetName, rule.getFieldName(), errorMessage));
                }
            }
            
        } catch (NumberFormatException e) {
            String errorMessage = getLocalizedMessage(localizationMap, "HCM_VALIDATION_INVALID_NUMBER", 
                    String.format("Field '%s' must be a valid number", rule.getFieldName()), rule.getFieldName());
            errors.add(createValidationError(rowNumber, sheetName, rule.getFieldName(), errorMessage));
        }
    }

    private void validateEnumField(Object value, ValidationRule rule, int rowNumber, 
            String sheetName, List<ValidationError> errors, Map<String, String> localizationMap) {
        
        String strValue = value.toString();
        if (rule.getAllowedValues() != null && !rule.getAllowedValues().contains(strValue)) {
            String errorMessage = getLocalizedMessage(localizationMap, "HCM_VALIDATION_INVALID_ENUM", 
                    String.format("Field '%s' contains invalid value '%s'", rule.getFieldName(), strValue),
                    rule.getFieldName(), strValue);
            errors.add(createValidationError(rowNumber, sheetName, rule.getFieldName(), errorMessage));
        }
    }

    private void validateMultiSelectField(String value, ValidationRule rule, int rowNumber, 
            String sheetName, List<ValidationError> errors, Map<String, String> localizationMap) {
        
        MultiSelectDetails multiSelect = rule.getMultiSelectDetails();
        if (multiSelect == null) {
            return;
        }
        
        // Parse multi-select value (assuming comma-separated)
        String[] selectedValues = value.split(",");
        List<String> trimmedValues = new ArrayList<>();
        
        for (String selectedValue : selectedValues) {
            String trimmed = selectedValue.trim();
            if (!trimmed.isEmpty()) {
                trimmedValues.add(trimmed);
            }
        }
        
        // Validate minimum selections
        if (multiSelect.getMinSelections() != null && trimmedValues.size() < multiSelect.getMinSelections()) {
            String errorMessage = getLocalizedMessage(localizationMap, "HCM_VALIDATION_MIN_SELECTIONS", 
                    String.format("Field '%s' must have at least %d selections", rule.getFieldName(), multiSelect.getMinSelections()),
                    rule.getFieldName(), multiSelect.getMinSelections().toString());
            errors.add(createValidationError(rowNumber, sheetName, rule.getFieldName(), errorMessage));
        }
        
        // Validate maximum selections
        if (multiSelect.getMaxSelections() != null && trimmedValues.size() > multiSelect.getMaxSelections()) {
            String errorMessage = getLocalizedMessage(localizationMap, "HCM_VALIDATION_MAX_SELECTIONS", 
                    String.format("Field '%s' must have at most %d selections", rule.getFieldName(), multiSelect.getMaxSelections()),
                    rule.getFieldName(), multiSelect.getMaxSelections().toString());
            errors.add(createValidationError(rowNumber, sheetName, rule.getFieldName(), errorMessage));
        }
        
        // Validate each selected value against allowed enum values
        if (multiSelect.getEnumValues() != null && !multiSelect.getEnumValues().isEmpty()) {
            for (String selectedValue : trimmedValues) {
                if (!multiSelect.getEnumValues().contains(selectedValue)) {
                    String errorMessage = getLocalizedMessage(localizationMap, "HCM_VALIDATION_INVALID_MULTI_SELECT", 
                            String.format("Field '%s' contains invalid value '%s'", rule.getFieldName(), selectedValue),
                            rule.getFieldName(), selectedValue);
                    errors.add(createValidationError(rowNumber, sheetName, rule.getFieldName(), errorMessage));
                    break; // Only show first invalid value to avoid too many errors
                }
            }
        }
    }

    private ValidationError createValidationError(int rowNumber, String sheetName, 
            String columnName, String errorMessage) {
        return ValidationError.builder()
                .rowNumber(rowNumber)
                .sheetName(sheetName)
                .columnName(columnName)
                .status(ValidationConstants.STATUS_INVALID)
                .errorDetails(errorMessage)
                .build();
    }

    /**
     * Inner class for validation rules
     */
    private static class ValidationRule {
        private String fieldName;
        private String type;
        private boolean required;
        private Integer minLength;
        private Integer maxLength;
        private Number minimum;
        private Number maximum;
        private List<String> allowedValues;
        private boolean unique;
        private String errorMessage;
        
        // New validation properties
        private String pattern; // Regex pattern for string validation
        private Number multipleOf; // Number must be multiple of this value
        private Number exclusiveMinimum; // Number must be greater than (not equal to) this value
        private Number exclusiveMaximum; // Number must be less than (not equal to) this value
        private MultiSelectDetails multiSelectDetails; // Multi-select validation details

        // Getters and setters
        public String getFieldName() { return fieldName; }
        public void setFieldName(String fieldName) { this.fieldName = fieldName; }
        
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        
        public boolean isRequired() { return required; }
        public void setRequired(boolean required) { this.required = required; }
        
        public Integer getMinLength() { return minLength; }
        public void setMinLength(Integer minLength) { this.minLength = minLength; }
        
        public Integer getMaxLength() { return maxLength; }
        public void setMaxLength(Integer maxLength) { this.maxLength = maxLength; }
        
        public Number getMinimum() { return minimum; }
        public void setMinimum(Number minimum) { this.minimum = minimum; }
        
        public Number getMaximum() { return maximum; }
        public void setMaximum(Number maximum) { this.maximum = maximum; }
        
        public List<String> getAllowedValues() { return allowedValues; }
        public void setAllowedValues(List<String> allowedValues) { this.allowedValues = allowedValues; }
        
        public boolean isUnique() { return unique; }
        public void setUnique(boolean unique) { this.unique = unique; }
        
        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
        
        // New getters and setters
        public String getPattern() { return pattern; }
        public void setPattern(String pattern) { this.pattern = pattern; }
        
        public Number getMultipleOf() { return multipleOf; }
        public void setMultipleOf(Number multipleOf) { this.multipleOf = multipleOf; }
        
        public Number getExclusiveMinimum() { return exclusiveMinimum; }
        public void setExclusiveMinimum(Number exclusiveMinimum) { this.exclusiveMinimum = exclusiveMinimum; }
        
        public Number getExclusiveMaximum() { return exclusiveMaximum; }
        public void setExclusiveMaximum(Number exclusiveMaximum) { this.exclusiveMaximum = exclusiveMaximum; }
        
        public MultiSelectDetails getMultiSelectDetails() { return multiSelectDetails; }
        public void setMultiSelectDetails(MultiSelectDetails multiSelectDetails) { this.multiSelectDetails = multiSelectDetails; }
    }
    
    /**
     * Inner class for multi-select validation details
     */
    private static class MultiSelectDetails {
        private List<String> enumValues;
        private Integer minSelections;
        private Integer maxSelections;
        
        public List<String> getEnumValues() { return enumValues; }
        public void setEnumValues(List<String> enumValues) { this.enumValues = enumValues; }
        
        public Integer getMinSelections() { return minSelections; }
        public void setMinSelections(Integer minSelections) { this.minSelections = minSelections; }
        
        public Integer getMaxSelections() { return maxSelections; }
        public void setMaxSelections(Integer maxSelections) { this.maxSelections = maxSelections; }
    }
    
    /**
     * Validates uniqueness constraints across all rows
     */
    private List<ValidationError> validateUniquenessConstraints(List<Map<String, Object>> sheetData,
                                                               String sheetName,
                                                               Map<String, ValidationRule> validationRules,
                                                               Map<String, String> localizationMap) {
        List<ValidationError> errors = new ArrayList<>();
        
        // Find fields that need uniqueness validation
        Map<String, ValidationRule> uniqueFields = validationRules.entrySet().stream()
                .filter(entry -> entry.getValue().isUnique())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        
        if (uniqueFields.isEmpty()) {
            return errors; // No unique fields to validate
        }
        
        // For each unique field, collect values and find duplicates
        for (Map.Entry<String, ValidationRule> entry : uniqueFields.entrySet()) {
            String fieldName = entry.getKey();
            ValidationRule rule = entry.getValue();
            
            // Map to track value -> list of row indices where it appears
            Map<String, List<Integer>> valueToRows = new HashMap<>();
            
            // Collect all values for this field
            for (int rowIndex = 0; rowIndex < sheetData.size(); rowIndex++) {
                Map<String, Object> rowData = sheetData.get(rowIndex);
                Object value = rowData.get(fieldName);
                
                // Skip null or empty values for uniqueness check
                if (value != null && !value.toString().trim().isEmpty()) {
                    String strValue = value.toString().trim();
                    valueToRows.computeIfAbsent(strValue, k -> new ArrayList<>()).add(rowIndex);
                }
            }
            
            // Find duplicates and create errors
            for (Map.Entry<String, List<Integer>> valueEntry : valueToRows.entrySet()) {
                String duplicateValue = valueEntry.getKey();
                List<Integer> rowIndices = valueEntry.getValue();
                
                if (rowIndices.size() > 1) {
                    // This value appears in multiple rows - create errors for all occurrences
                    for (int rowIndex : rowIndices) {
                        int excelRowNumber = rowIndex + 3; // +1 for 0-based to 1-based, +2 for two header rows
                        
                        // Create list of other row numbers with same value
                        List<String> otherRows = rowIndices.stream()
                                .filter(idx -> idx != rowIndex)
                                .map(idx -> String.valueOf(idx + 3))
                                .collect(Collectors.toList());
                        
                        String errorMessage = getLocalizedMessage(localizationMap, 
                                "HCM_VALIDATION_DUPLICATE_VALUE",
                                String.format("Field '%s' must be unique. Value '%s' is also found in row(s): %s", 
                                        fieldName, duplicateValue, String.join(", ", otherRows)),
                                fieldName, duplicateValue, String.join(", ", otherRows));
                        
                        errors.add(createValidationError(excelRowNumber, sheetName, fieldName, errorMessage));
                    }
                }
            }
        }
        
        log.info("Found {} uniqueness validation errors for sheet: {}", errors.size(), sheetName);
        return errors;
    }

    /**
     * Helper method to get localized message with fallback and clean formatting
     */
    private String getLocalizedMessage(Map<String, String> localizationMap, String key, String defaultMessage, String... params) {
        String message;
        if (localizationMap != null && localizationMap.containsKey(key)) {
            message = localizationMap.get(key);
            // Simple parameter replacement for {0}, {1}, etc.
            for (int i = 0; i < params.length; i++) {
                message = message.replace("{" + i + "}", params[i]);
            }
        } else {
            message = defaultMessage;
        }
        
        // Clean up the message - remove leading semicolons and whitespace
        return cleanErrorMessage(message);
    }
    
    /**
     * Clean error message by removing leading semicolons and whitespace
     */
    private String cleanErrorMessage(String message) {
        if (message == null) {
            return "";
        }
        
        message = message.trim();
        
        // Remove leading semicolons (common in localized messages)
        while (message.startsWith(";")) {
            message = message.substring(1).trim();
        }
        
        return message;
    }
    
    /**
     * Validate a single field with its rule
     */
    private void validateField(String fieldName, Object value, ValidationRule rule, int rowNumber, 
                              String sheetName, List<ValidationError> errors, Map<String, String> localizationMap) {
        // Check required fields
        if (rule.isRequired() && (value == null || value.toString().trim().isEmpty())) {
            String errorMessage = rule.getErrorMessage() != null 
                ? rule.getErrorMessage() 
                : getLocalizedMessage(localizationMap, "HCM_VALIDATION_REQUIRED_FIELD", 
                        String.format("Required field '%s' is missing", fieldName), fieldName);
                
            errors.add(ValidationError.builder()
                    .rowNumber(rowNumber)
                    .sheetName(sheetName)
                    .columnName(fieldName)
                    .status(ValidationConstants.STATUS_INVALID)
                    .errorDetails(errorMessage)
                    .build());
            return;
        }
        
        // Skip validation if value is empty and not required
        if (value == null || value.toString().trim().isEmpty()) {
            return;
        }
        
        // Type-specific validation
        switch (rule.getType()) {
            case "string":
                validateStringField(value, rule, rowNumber, sheetName, errors, localizationMap);
                break;
            case "number":
                validateNumberField(value, rule, rowNumber, sheetName, errors, localizationMap);
                break;
            case "enum":
                validateEnumField(value, rule, rowNumber, sheetName, errors, localizationMap);
                break;
        }
    }
    
    /**
     * Check if field is a child multi-select field (ends with _1, _2, etc.)
     */
    private boolean isChildMultiSelectField(String fieldName) {
        if (fieldName == null || fieldName.length() < 3) {
            return false;
        }
        
        // Check if field ends with _X where X is a number
        int lastUnderscore = fieldName.lastIndexOf('_');
        if (lastUnderscore == -1 || lastUnderscore == fieldName.length() - 1) {
            return false;
        }
        
        String suffix = fieldName.substring(lastUnderscore + 1);
        try {
            Integer.parseInt(suffix);
            return true; // It's a number, so it's a child field
        } catch (NumberFormatException e) {
            return false;
        }
    }
    
    /**
     * Get parent field name by removing _X suffix
     */
    private String getParentFieldName(String childFieldName) {
        int lastUnderscore = childFieldName.lastIndexOf('_');
        return childFieldName.substring(0, lastUnderscore);
    }
    
    /**
     * Validate child multi-select field using parent's enum values
     */
    private void validateChildMultiSelectField(String fieldName, Object value, ValidationRule parentRule, 
                                             int rowNumber, String sheetName, List<ValidationError> errors, 
                                             Map<String, String> localizationMap) {
        if (value == null || value.toString().trim().isEmpty()) {
            return; // Empty child fields are usually OK
        }
        
        String strValue = value.toString().trim();
        MultiSelectDetails multiSelect = parentRule.getMultiSelectDetails();
        
        if (multiSelect != null && multiSelect.getEnumValues() != null && !multiSelect.getEnumValues().isEmpty()) {
            // For child fields, the value should be a single enum value (not comma-separated)
            if (!multiSelect.getEnumValues().contains(strValue)) {
                String errorMessage = getLocalizedMessage(localizationMap, "HCM_VALIDATION_INVALID_ENUM", 
                        String.format("Field '%s' contains invalid value '%s'", fieldName, strValue),
                        fieldName, strValue);
                
                errors.add(createValidationError(rowNumber, sheetName, fieldName, errorMessage));
            }
        }
    }
    
}