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
            // Extract validation rules from schema with localization support
            Map<String, ValidationRule> validationRules = extractValidationRules(schema, localizationMap);
            
            // Validate each row against the schema 
            for (int rowIndex = 0; rowIndex < sheetData.size(); rowIndex++) {
                Map<String, Object> rowData = sheetData.get(rowIndex);
                if (rowData == null) {
                    continue; // Skip null rows
                }
                // Use actual Excel row number from data if available, otherwise fallback to calculated
                int excelRowNumber = rowData.containsKey("__actualRowNumber__") ? 
                    (Integer) rowData.get("__actualRowNumber__") : rowIndex + 3;
                
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
     * Extracts validation rules from schema with localization support
     */
    private Map<String, ValidationRule> extractValidationRules(Map<String, Object> schema, Map<String, String> localizationMap) {
        Map<String, ValidationRule> rules = new HashMap<>();
        
        if (schema != null) {
            // The schema passed here is already the properties section from MDMS
            // Look for stringProperties, numberProperties, enumProperties directly
            extractStringPropertyRules(schema, rules, localizationMap);
            extractNumberPropertyRules(schema, rules, localizationMap);
            extractEnumPropertyRules(schema, rules, localizationMap);
        }
        
        log.info("Extracted {} validation rules from schema", rules.size());
        return rules;
    }

    /**
     * Extracts string property validation rules
     */
    @SuppressWarnings("unchecked")
    private void extractStringPropertyRules(Map<String, Object> schema, Map<String, ValidationRule> rules) {
        extractStringPropertyRules(schema, rules, null);
    }
    
    /**
     * Extracts string property validation rules with localization support
     */
    @SuppressWarnings("unchecked")
    private void extractStringPropertyRules(Map<String, Object> schema, Map<String, ValidationRule> rules, Map<String, String> localizationMap) {
        if (schema.containsKey("stringProperties")) {
            List<Map<String, Object>> stringProps = (List<Map<String, Object>>) schema.get("stringProperties");
            for (Map<String, Object> prop : stringProps) {
                String name = (String) prop.get("name");
                if (name != null) {
                    ValidationRule rule = new ValidationRule();
                    rule.setFieldName(name); // Keep technical name for lookup
                    // Set localized display name if available
                    if (localizationMap != null && localizationMap.containsKey(name)) {
                        rule.setDisplayName(localizationMap.get(name));
                    }
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
        extractNumberPropertyRules(schema, rules, null);
    }
    
    /**
     * Extracts number property validation rules with localization support
     */
    @SuppressWarnings("unchecked")
    private void extractNumberPropertyRules(Map<String, Object> schema, Map<String, ValidationRule> rules, Map<String, String> localizationMap) {
        if (schema.containsKey("numberProperties")) {
            List<Map<String, Object>> numberProps = (List<Map<String, Object>>) schema.get("numberProperties");
            for (Map<String, Object> prop : numberProps) {
                String name = (String) prop.get("name");
                if (name != null) {
                    ValidationRule rule = new ValidationRule();
                    rule.setFieldName(name); // Keep technical name for lookup
                    // Set localized display name if available
                    if (localizationMap != null && localizationMap.containsKey(name)) {
                        rule.setDisplayName(localizationMap.get(name));
                    }
                    rule.setType("number");
                    rule.setRequired((Boolean) prop.getOrDefault("isRequired", false));
                    rule.setMinimum((Number) prop.get("minimum"));
                    rule.setMaximum((Number) prop.get("maximum"));
                    rule.setErrorMessage((String) prop.get("errorMessage"));
                    rule.setUnique((Boolean.TRUE.equals(prop.get("isUnique"))));
                    
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
        extractEnumPropertyRules(schema, rules, null);
    }
    
    /**
     * Extracts enum property validation rules with localization support
     */
    @SuppressWarnings("unchecked")
    private void extractEnumPropertyRules(Map<String, Object> schema, Map<String, ValidationRule> rules, Map<String, String> localizationMap) {
        if (schema.containsKey("enumProperties")) {
            List<Map<String, Object>> enumProps = (List<Map<String, Object>>) schema.get("enumProperties");
            for (Map<String, Object> prop : enumProps) {
                String name = (String) prop.get("name");
                if (name != null) {
                    ValidationRule rule = new ValidationRule();
                    rule.setFieldName(name); // Keep technical name for lookup
                    // Set localized display name if available
                    if (localizationMap != null && localizationMap.containsKey(name)) {
                        rule.setDisplayName(localizationMap.get(name));
                    }
                    rule.setType("enum");
                    rule.setRequired((Boolean) prop.getOrDefault("isRequired", false));
                    rule.setAllowedValues((List<String>) prop.get("enum"));
                    rule.setErrorMessage((String) prop.get("errorMessage"));
                    rule.setUnique((Boolean.TRUE.equals(prop.get("isUnique"))));
                    
                    // Multi-select validation for enum properties
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
        
        // Second, validate multi-select field groups (collect values from _MULTISELECT_* columns)
        Map<String, List<String>> multiSelectGroups = collectMultiSelectValues(rowData, rules);
        
        for (Map.Entry<String, List<String>> entry : multiSelectGroups.entrySet()) {
            String parentFieldName = entry.getKey();
            List<String> selectedValues = entry.getValue();
            ValidationRule parentRule = rules.get(parentFieldName);
            
            if (parentRule != null && parentRule.getMultiSelectDetails() != null) {
                validateCollectedMultiSelectValues(parentFieldName, selectedValues, parentRule, 
                    rowNumber, sheetName, errors, localizationMap);
            }
        }
        
        // Third, validate individual child multi-select fields for enum compliance
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
        
        // Note: We don't add a ValidationError with STATUS_VALID here because:
        // 1. The ValidationService will set the row status to VALID if there are no errors
        // 2. Adding a VALID error here conflicts with later additions of uniqueness validation errors
        // The ValidationService.determineRowStatus() method handles setting the correct status
        
        return errors;
    }

    private void validateStringField(Object value, ValidationRule rule, int rowNumber, 
            String sheetName, List<ValidationError> errors, Map<String, String> localizationMap) {
        
        String strValue = value.toString();
        
        // Length validation
        if (rule.getMinLength() != null && strValue.length() < rule.getMinLength()) {
            String errorMessage;
            if (rule.getErrorMessage() != null && !rule.getErrorMessage().isEmpty()) {
                // Use custom error message and try to localize it
                errorMessage = getLocalizedMessage(localizationMap, rule.getErrorMessage(), rule.getErrorMessage());
            } else {
                // Use dynamic message with localization
                errorMessage = getLocalizedMessage(localizationMap, "HCM_VALIDATION_MIN_LENGTH", 
                        String.format("Field '%s' must be at least %d characters", rule.getDisplayName(), rule.getMinLength()),
                        rule.getDisplayName(), rule.getMinLength().toString());
            }
            errors.add(createValidationError(rowNumber, sheetName, rule.getFieldName(), errorMessage));
        }
        
        if (rule.getMaxLength() != null && strValue.length() > rule.getMaxLength()) {
            String errorMessage;
            if (rule.getErrorMessage() != null && !rule.getErrorMessage().isEmpty()) {
                // Use custom error message and try to localize it
                errorMessage = getLocalizedMessage(localizationMap, rule.getErrorMessage(), rule.getErrorMessage());
            } else {
                // Use dynamic message with localization
                errorMessage = getLocalizedMessage(localizationMap, "HCM_VALIDATION_MAX_LENGTH", 
                        String.format("Field '%s' must not exceed %d characters", rule.getDisplayName(), rule.getMaxLength()),
                        rule.getDisplayName(), rule.getMaxLength().toString());
            }
            errors.add(createValidationError(rowNumber, sheetName, rule.getFieldName(), errorMessage));
        }
        
        // Pattern validation
        if (rule.getPattern() != null && !rule.getPattern().trim().isEmpty()) {
            try {
                Pattern pattern = Pattern.compile(rule.getPattern());
                if (!pattern.matcher(strValue).matches()) {
                    String errorMessage;
                    if (rule.getErrorMessage() != null && !rule.getErrorMessage().isEmpty()) {
                        // Use custom error message and try to localize it
                        errorMessage = getLocalizedMessage(localizationMap, rule.getErrorMessage(), rule.getErrorMessage());
                    } else {
                        // Use dynamic message with localization
                        errorMessage = getLocalizedMessage(localizationMap, "HCM_VALIDATION_PATTERN", 
                                String.format("Field '%s' does not match required pattern", rule.getDisplayName()),
                                rule.getDisplayName());
                    }
                    errors.add(createValidationError(rowNumber, sheetName, rule.getFieldName(), errorMessage));
                }
            } catch (PatternSyntaxException e) {
                log.warn("Invalid regex pattern '{}' for field '{}'", rule.getPattern(), rule.getDisplayName());
                String errorMessage = getLocalizedMessage(localizationMap, "HCM_VALIDATION_INVALID_PATTERN", 
                        String.format("Field '%s' has invalid validation pattern", rule.getDisplayName()),
                        rule.getDisplayName());
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
                String errorMessage;
                if (rule.getErrorMessage() != null && !rule.getErrorMessage().isEmpty()) {
                    // Use custom error message and try to localize it
                    errorMessage = getLocalizedMessage(localizationMap, rule.getErrorMessage(), rule.getErrorMessage());
                } else {
                    // Use dynamic message with localization
                    errorMessage = getLocalizedMessage(localizationMap, "HCM_VALIDATION_MIN_NUMBER", 
                            String.format("Field '%s' must be at least %s", rule.getFieldName(), rule.getMinimum()),
                            rule.getFieldName(), rule.getMinimum().toString());
                }
                errors.add(createValidationError(rowNumber, sheetName, rule.getFieldName(), errorMessage));
            }
            
            if (rule.getMaximum() != null && numValue > rule.getMaximum().doubleValue()) {
                String errorMessage;
                if (rule.getErrorMessage() != null && !rule.getErrorMessage().isEmpty()) {
                    // Use custom error message and try to localize it
                    errorMessage = getLocalizedMessage(localizationMap, rule.getErrorMessage(), rule.getErrorMessage());
                } else {
                    // Use dynamic message with localization
                    errorMessage = getLocalizedMessage(localizationMap, "HCM_VALIDATION_MAX_NUMBER", 
                            String.format("Field '%s' must not exceed %s", rule.getFieldName(), rule.getMaximum()),
                            rule.getFieldName(), rule.getMaximum().toString());
                }
                errors.add(createValidationError(rowNumber, sheetName, rule.getFieldName(), errorMessage));
            }
            
            // Exclusive minimum validation
            if (rule.getExclusiveMinimum() != null && numValue <= rule.getExclusiveMinimum().doubleValue()) {
                String errorMessage;
                if (rule.getErrorMessage() != null && !rule.getErrorMessage().isEmpty()) {
                    // Use custom error message and try to localize it
                    errorMessage = getLocalizedMessage(localizationMap, rule.getErrorMessage(), rule.getErrorMessage());
                } else {
                    // Use dynamic message with localization
                    errorMessage = getLocalizedMessage(localizationMap, "HCM_VALIDATION_EXCLUSIVE_MIN", 
                            String.format("Field '%s' must be greater than %s", rule.getFieldName(), rule.getExclusiveMinimum()),
                            rule.getFieldName(), rule.getExclusiveMinimum().toString());
                }
                errors.add(createValidationError(rowNumber, sheetName, rule.getFieldName(), errorMessage));
            }
            
            // Exclusive maximum validation
            if (rule.getExclusiveMaximum() != null && numValue >= rule.getExclusiveMaximum().doubleValue()) {
                String errorMessage;
                if (rule.getErrorMessage() != null && !rule.getErrorMessage().isEmpty()) {
                    // Use custom error message and try to localize it
                    errorMessage = getLocalizedMessage(localizationMap, rule.getErrorMessage(), rule.getErrorMessage());
                } else {
                    // Use dynamic message with localization
                    errorMessage = getLocalizedMessage(localizationMap, "HCM_VALIDATION_EXCLUSIVE_MAX", 
                            String.format("Field '%s' must be less than %s", rule.getFieldName(), rule.getExclusiveMaximum()),
                            rule.getFieldName(), rule.getExclusiveMaximum().toString());
                }
                errors.add(createValidationError(rowNumber, sheetName, rule.getFieldName(), errorMessage));
            }
            
            // Multiple of validation
            if (rule.getMultipleOf() != null) {
                double divisor = rule.getMultipleOf().doubleValue();
                if (divisor != 0 && (numValue % divisor) != 0) {
                    String errorMessage;
                    if (rule.getErrorMessage() != null && !rule.getErrorMessage().isEmpty()) {
                        // Use custom error message and try to localize it
                        errorMessage = getLocalizedMessage(localizationMap, rule.getErrorMessage(), rule.getErrorMessage());
                    } else {
                        // Use dynamic message with localization
                        errorMessage = getLocalizedMessage(localizationMap, "HCM_VALIDATION_MULTIPLE_OF", 
                                String.format("Field '%s' must be a multiple of %s", rule.getFieldName(), rule.getMultipleOf()),
                                rule.getFieldName(), rule.getMultipleOf().toString());
                    }
                    errors.add(createValidationError(rowNumber, sheetName, rule.getFieldName(), errorMessage));
                }
            }
            
        } catch (NumberFormatException e) {
            String errorMessage;
            if (rule.getErrorMessage() != null && !rule.getErrorMessage().isEmpty()) {
                // Use custom error message and try to localize it
                errorMessage = getLocalizedMessage(localizationMap, rule.getErrorMessage(), rule.getErrorMessage());
            } else {
                // Use dynamic message with localization
                errorMessage = getLocalizedMessage(localizationMap, "HCM_VALIDATION_INVALID_NUMBER", 
                        String.format("Field '%s' must be a valid number", rule.getFieldName()), rule.getFieldName());
            }
            errors.add(createValidationError(rowNumber, sheetName, rule.getFieldName(), errorMessage));
        }
    }

    private void validateEnumField(Object value, ValidationRule rule, int rowNumber, 
            String sheetName, List<ValidationError> errors, Map<String, String> localizationMap) {
        
        String strValue = value.toString();
        if (rule.getAllowedValues() != null && !rule.getAllowedValues().contains(strValue)) {
            String errorMessage;
            if (rule.getErrorMessage() != null && !rule.getErrorMessage().isEmpty()) {
                // Use custom error message and try to localize it
                errorMessage = getLocalizedMessage(localizationMap, rule.getErrorMessage(), rule.getErrorMessage());
            } else {
                // Use dynamic message with localization
                errorMessage = getLocalizedMessage(localizationMap, "HCM_VALIDATION_INVALID_ENUM", 
                        String.format("Field '%s' contains invalid value '%s'", rule.getFieldName(), strValue),
                        rule.getFieldName(), strValue);
            }
            errors.add(createValidationError(rowNumber, sheetName, rule.getFieldName(), errorMessage));
        }
    }

    private void validateMultiSelectField(String value, ValidationRule rule, int rowNumber, 
            String sheetName, List<ValidationError> errors, Map<String, String> localizationMap) {
        
        MultiSelectDetails multiSelect = rule.getMultiSelectDetails();
        if (multiSelect == null) {
            return;
        }
        
        // Parse multi-select value (using constant separator)
        String[] selectedValues = value.split(ValidationConstants.MULTI_SELECT_SEPARATOR);
        List<String> trimmedValues = new ArrayList<>();
        
        for (String selectedValue : selectedValues) {
            String trimmed = selectedValue.trim();
            if (!trimmed.isEmpty()) {
                trimmedValues.add(trimmed);
            }
        }
        
        // Validate for duplicate selections
        Set<String> uniqueValues = new HashSet<>(trimmedValues);
        if (uniqueValues.size() < trimmedValues.size()) {
            String errorMessage = getLocalizedMessage(localizationMap, ValidationConstants.HCM_VALIDATION_DUPLICATE_SELECTIONS, 
                    String.format("Field '%s' contains duplicate selections", rule.getFieldName()),
                    rule.getFieldName());
            errors.add(createValidationError(rowNumber, sheetName, rule.getFieldName(), errorMessage));
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
        private String fieldName; // Technical field name for lookup
        private String displayName; // Localized field name for display in errors
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
        
        public String getDisplayName() { return displayName != null ? displayName : fieldName; }
        public void setDisplayName(String displayName) { this.displayName = displayName; }
        
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
            
            // Map to track value -> list of row indices where it appears
            Map<String, List<Integer>> valueToRows = new HashMap<>();
            
            // Collect all values for this field
            for (int rowIndex = 0; rowIndex < sheetData.size(); rowIndex++) {
                Map<String, Object> rowData = sheetData.get(rowIndex);
                if (rowData == null) {
                    continue; // Skip null rows
                }
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
                        Map<String, Object> rowData = sheetData.get(rowIndex);
                        // Use actual Excel row number from data if available, otherwise fallback to calculated
                        int excelRowNumber = rowData.containsKey("__actualRowNumber__") ? 
                            (Integer) rowData.get("__actualRowNumber__") : rowIndex + 3;
                        
                        // Create list of other row numbers with same value
                        List<String> otherRows = rowIndices.stream()
                                .filter(idx -> idx != rowIndex)
                                .map(idx -> {
                                    Map<String, Object> otherRowData = sheetData.get(idx);
                                    return String.valueOf(otherRowData.containsKey("__actualRowNumber__") ? 
                                        (Integer) otherRowData.get("__actualRowNumber__") : idx + 3);
                                })
                                .collect(Collectors.toList());
                        
                        ValidationRule fieldRule = uniqueFields.get(fieldName);
                        String displayName = fieldRule != null ? fieldRule.getDisplayName() : fieldName;
                        String errorMessage = getLocalizedMessage(localizationMap, 
                                "HCM_VALIDATION_DUPLICATE_VALUE",
                                String.format("Field '%s' must be unique. Value '%s' is also found in row(s): %s", 
                                        displayName, duplicateValue, String.join(", ", otherRows)),
                                displayName, duplicateValue, String.join(", ", otherRows));
                        
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
        // Check required fields - special handling for multi-select fields
        if (rule.isRequired()) {
            boolean isEmpty = false;
            
            if (value == null || value.toString().trim().isEmpty()) {
                isEmpty = true;
            } else if (rule.getMultiSelectDetails() != null) {
                // For multi-select fields, check if all values are empty after parsing
                String[] selectedValues = value.toString().split(ValidationConstants.MULTI_SELECT_SEPARATOR);
                boolean hasNonEmptyValue = false;
                
                for (String selectedValue : selectedValues) {
                    if (!selectedValue.trim().isEmpty()) {
                        hasNonEmptyValue = true;
                        break;
                    }
                }
                isEmpty = !hasNonEmptyValue;
            }
            
            if (isEmpty) {
                String localizationKey = rule.getMultiSelectDetails() != null 
                    ? ValidationConstants.HCM_VALIDATION_REQUIRED_MULTI_SELECT
                    : "HCM_VALIDATION_REQUIRED_FIELD";
                    
                String defaultMessage = rule.getMultiSelectDetails() != null
                    ? String.format("Required multi-select field '%s' must have at least one selection", rule.getDisplayName())
                    : String.format("Required field '%s' is missing", rule.getDisplayName());
                
                String errorMessage;
                if (rule.getErrorMessage() != null && !rule.getErrorMessage().isEmpty()) {
                    // Try to localize the custom error message
                    errorMessage = getLocalizedMessage(localizationMap, rule.getErrorMessage(), rule.getErrorMessage());
                } else {
                    // Use dynamic message with localization
                    errorMessage = getLocalizedMessage(localizationMap, localizationKey, defaultMessage, rule.getDisplayName());
                }
                    
                errors.add(ValidationError.builder()
                        .rowNumber(rowNumber)
                        .sheetName(sheetName)
                        .columnName(fieldName)
                        .status(ValidationConstants.STATUS_INVALID)
                        .errorDetails(errorMessage)
                        .build());
                return;
            }
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
     * Collect multi-select values from _MULTISELECT_* columns grouped by parent field
     */
    private Map<String, List<String>> collectMultiSelectValues(Map<String, Object> rowData, Map<String, ValidationRule> rules) {
        Map<String, List<String>> multiSelectGroups = new HashMap<>();
        
        for (String fieldName : rowData.keySet()) {
            if (fieldName.contains("_MULTISELECT_")) {
                // Extract parent field name (everything before _MULTISELECT_)
                String parentFieldName = fieldName.substring(0, fieldName.indexOf("_MULTISELECT_"));
                
                // Check if parent has multi-select rules
                ValidationRule parentRule = rules.get(parentFieldName);
                if (parentRule != null && parentRule.getMultiSelectDetails() != null) {
                    // Always ensure parent field is in the map for validation
                    multiSelectGroups.computeIfAbsent(parentFieldName, k -> new ArrayList<>());
                    
                    Object value = rowData.get(fieldName);
                    if (value != null && !value.toString().trim().isEmpty()) {
                        multiSelectGroups.get(parentFieldName).add(value.toString().trim());
                    }
                }
            }
        }
        return multiSelectGroups;
    }
    
    /**
     * Validate collected multi-select values for min/max/duplicate constraints
     */
    private void validateCollectedMultiSelectValues(String fieldName, List<String> selectedValues, 
                                                   ValidationRule rule, int rowNumber, String sheetName, 
                                                   List<ValidationError> errors, Map<String, String> localizationMap) {
        MultiSelectDetails multiSelect = rule.getMultiSelectDetails();
        if (multiSelect == null) {
            return;
        }
        
        // Check if field is required and has no selections
        if (rule.isRequired() && selectedValues.isEmpty()) {
            String errorMessage = getLocalizedMessage(localizationMap, ValidationConstants.HCM_VALIDATION_REQUIRED_MULTI_SELECT, 
                    String.format("Required multi-select field '%s' must have at least one selection", rule.getDisplayName()),
                    rule.getDisplayName());
            errors.add(createValidationError(rowNumber, sheetName, fieldName, errorMessage));
            // Don't return here - continue with min/max validation even for required fields
        }
        
        // Validate minimum selections (applies even to empty fields)
        if (multiSelect.getMinSelections() != null && selectedValues.size() < multiSelect.getMinSelections()) {
            String errorMessage = getLocalizedMessage(localizationMap, "HCM_VALIDATION_MIN_SELECTIONS", 
                    String.format("Field '%s' must have at least %d selections", rule.getDisplayName(), multiSelect.getMinSelections()),
                    rule.getDisplayName(), multiSelect.getMinSelections().toString());
            errors.add(createValidationError(rowNumber, sheetName, fieldName, errorMessage));
        }
        
        // Skip further validation if no values selected
        if (selectedValues.isEmpty()) {
            return;
        }
        
        // Validate for duplicate selections (only if we have values)
        Set<String> uniqueValues = new HashSet<>(selectedValues);
        if (uniqueValues.size() < selectedValues.size()) {
            String errorMessage = getLocalizedMessage(localizationMap, ValidationConstants.HCM_VALIDATION_DUPLICATE_SELECTIONS, 
                    String.format("Field '%s' contains duplicate selections", rule.getDisplayName()),
                    rule.getDisplayName());
            errors.add(createValidationError(rowNumber, sheetName, fieldName, errorMessage));
        }
        
        // Validate maximum selections
        if (multiSelect.getMaxSelections() != null && selectedValues.size() > multiSelect.getMaxSelections()) {
            String errorMessage = getLocalizedMessage(localizationMap, "HCM_VALIDATION_MAX_SELECTIONS", 
                    String.format("Field '%s' must have at most %d selections", rule.getDisplayName(), multiSelect.getMaxSelections()),
                    rule.getDisplayName(), multiSelect.getMaxSelections().toString());
            errors.add(createValidationError(rowNumber, sheetName, fieldName, errorMessage));
        }
        
        // Validate each selected value against allowed enum values
        if (multiSelect.getEnumValues() != null && !multiSelect.getEnumValues().isEmpty()) {
            for (String selectedValue : selectedValues) {
                if (!multiSelect.getEnumValues().contains(selectedValue)) {
                    String errorMessage = getLocalizedMessage(localizationMap, "HCM_VALIDATION_INVALID_MULTI_SELECT", 
                            String.format("Field '%s' contains invalid value '%s'", rule.getDisplayName(), selectedValue),
                            rule.getDisplayName(), selectedValue);
                    errors.add(createValidationError(rowNumber, sheetName, fieldName, errorMessage));
                    break; // Only show first invalid value to avoid too many errors
                }
            }
        }
    }
    
    /**
     * Check if field is a child multi-select field (contains _MULTISELECT_)
     */
    private boolean isChildMultiSelectField(String fieldName) {
        if (fieldName == null) {
            return false;
        }
        
        // Check if field contains _MULTISELECT_ pattern
        return fieldName.contains("_MULTISELECT_");
    }
    
    /**
     * Get parent field name by removing _MULTISELECT_X suffix
     */
    private String getParentFieldName(String childFieldName) {
        if (childFieldName.contains("_MULTISELECT_")) {
            return childFieldName.substring(0, childFieldName.indexOf("_MULTISELECT_"));
        }
        // Fallback for legacy pattern _X
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