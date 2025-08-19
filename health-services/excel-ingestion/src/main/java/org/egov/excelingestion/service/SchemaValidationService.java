package org.egov.excelingestion.service;

import lombok.extern.slf4j.Slf4j;
import org.egov.excelingestion.config.ProcessingConstants;
import org.egov.excelingestion.config.SheetSchemaConfig;
import org.egov.excelingestion.config.ValidationConstants;
import org.egov.excelingestion.web.models.ValidationError;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@Slf4j
public class SchemaValidationService {

    @Autowired
    private MDMSService mdmsService;
    
    @Autowired
    private SheetSchemaConfig sheetSchemaConfig;

    /**
     * Validates sheet data against JSON schema from MDMS
     */
    public List<ValidationError> validateDataWithSchema(List<Map<String, Object>> sheetData, 
            String sheetName, String tenantId, String type, String campaignType,
            org.egov.excelingestion.web.models.RequestInfo requestInfo) {
        return validateDataWithSchema(sheetData, sheetName, tenantId, type, campaignType, requestInfo, null);
    }
    
    /**
     * Validates sheet data against JSON schema from MDMS with localization support
     */
    public List<ValidationError> validateDataWithSchema(List<Map<String, Object>> sheetData, 
            String sheetName, String tenantId, String type, String campaignType,
            org.egov.excelingestion.web.models.RequestInfo requestInfo, 
            Map<String, String> localizationMap) {
        
        List<ValidationError> errors = new ArrayList<>();
        
        try {
            String schemaName = getSchemaNameForSheet(sheetName, type, localizationMap);
            if (schemaName == null) {
                log.info("No schema validation configured for sheet: {} with type: {}", sheetName, type);
                return errors;
            }
            
            // Fetch schema from MDMS
            Map<String, Object> schema = fetchSchemaFromMDMS(tenantId, schemaName, requestInfo);
            if (schema == null) {
                log.warn("No schema found for schema name: {}", schemaName);
                return errors;
            }
            
            // Extract validation rules from schema
            Map<String, ValidationRule> validationRules = extractValidationRules(schema);
            
            // Validate each row against the schema 
            for (int rowIndex = 0; rowIndex < sheetData.size(); rowIndex++) {
                Map<String, Object> rowData = sheetData.get(rowIndex);
                int excelRowNumber = rowIndex + 3; // +1 for 0-based to 1-based, +2 for two header rows
                
                List<ValidationError> rowErrors = validateRowAgainstSchema(
                    rowData, excelRowNumber, sheetName, validationRules);
                errors.addAll(rowErrors);
            }
            
        } catch (Exception e) {
            log.error("Error during schema validation: {}", e.getMessage(), e);
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
     * Fetches schema from MDMS v2 using title
     */
    private Map<String, Object> fetchSchemaFromMDMS(String tenantId, String schemaName,
            org.egov.excelingestion.web.models.RequestInfo requestInfo) {
        try {
            // Create MDMS search criteria with title filter
            Map<String, Object> mdmsCriteria = new HashMap<>();
            mdmsCriteria.put("tenantId", tenantId);
            mdmsCriteria.put("schemaCode", ProcessingConstants.MDMS_SCHEMA_CODE);
            
            // Add filters for title
            Map<String, Object> filters = new HashMap<>();
            filters.put("title", schemaName);
            mdmsCriteria.put("filters", filters);
            
            // Call MDMS service
            List<Map<String, Object>> mdmsResponse = mdmsService.searchMDMSData(requestInfo, mdmsCriteria);
            
            if (mdmsResponse != null && !mdmsResponse.isEmpty()) {
                return convertToValidationSchema(mdmsResponse.get(0));
            }
            
            return null;
        } catch (Exception e) {
            log.error("Error fetching schema from MDMS: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Converts MDMS response to validation schema
     */
    private Map<String, Object> convertToValidationSchema(Map<String, Object> mdmsData) {
        Map<String, Object> schema = new HashMap<>();
        
        // Extract properties from MDMS data structure
        if (mdmsData.containsKey("data")) {
            Object data = mdmsData.get("data");
            if (data instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> dataMap = (Map<String, Object>) data;
                schema.putAll(dataMap);
            }
        }
        
        return schema;
    }

    /**
     * Extracts validation rules from schema
     */
    private Map<String, ValidationRule> extractValidationRules(Map<String, Object> schema) {
        Map<String, ValidationRule> rules = new HashMap<>();
        
        // Schema structure: schema.properties.stringProperties, etc.
        Map<String, Object> propertiesSection = null;
        if (schema.containsKey("properties")) {
            propertiesSection = (Map<String, Object>) schema.get("properties");
        } else {
            // Fallback: use schema directly if no properties section
            propertiesSection = null;
        }
        
        if (propertiesSection != null) {
            // Extract string properties
            extractStringPropertyRules(propertiesSection, rules);
            
            // Extract number properties  
            extractNumberPropertyRules(propertiesSection, rules);
            
            // Extract enum properties
            extractEnumPropertyRules(propertiesSection, rules);
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
            int rowNumber, String sheetName, Map<String, ValidationRule> rules) {
        
        List<ValidationError> errors = new ArrayList<>();
        
        for (ValidationRule rule : rules.values()) {
            String fieldName = rule.getFieldName();
            Object value = rowData.get(fieldName);
            
            // Check required fields
            if (rule.isRequired() && (value == null || value.toString().trim().isEmpty())) {
                String errorMessage = rule.getErrorMessage() != null 
                    ? rule.getErrorMessage() 
                    : String.format("Required field '%s' is missing", fieldName);
                    
                errors.add(ValidationError.builder()
                        .rowNumber(rowNumber)
                        .sheetName(sheetName)
                        .columnName(fieldName)
                        .status(ValidationConstants.STATUS_INVALID)
                        .errorDetails(errorMessage)
                        .build());
                continue;
            }
            
            // Skip validation if value is empty and not required
            if (value == null || value.toString().trim().isEmpty()) {
                continue;
            }
            
            // Type-specific validation
            switch (rule.getType()) {
                case "string":
                    validateStringField(value, rule, rowNumber, sheetName, errors);
                    break;
                case "number":
                    validateNumberField(value, rule, rowNumber, sheetName, errors);
                    break;
                case "enum":
                    validateEnumField(value, rule, rowNumber, sheetName, errors);
                    break;
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
            String sheetName, List<ValidationError> errors) {
        
        String strValue = value.toString();
        
        // Length validation
        if (rule.getMinLength() != null && strValue.length() < rule.getMinLength()) {
            String errorMessage = String.format("Field '%s' must be at least %d characters", 
                    rule.getFieldName(), rule.getMinLength());
            errors.add(createValidationError(rowNumber, sheetName, rule.getFieldName(), errorMessage));
        }
        
        if (rule.getMaxLength() != null && strValue.length() > rule.getMaxLength()) {
            String errorMessage = String.format("Field '%s' must not exceed %d characters", 
                    rule.getFieldName(), rule.getMaxLength());
            errors.add(createValidationError(rowNumber, sheetName, rule.getFieldName(), errorMessage));
        }
    }

    private void validateNumberField(Object value, ValidationRule rule, int rowNumber, 
            String sheetName, List<ValidationError> errors) {
        
        try {
            Double numValue = Double.parseDouble(value.toString());
            
            if (rule.getMinimum() != null && numValue < rule.getMinimum().doubleValue()) {
                String errorMessage = String.format("Field '%s' must be at least %s", 
                        rule.getFieldName(), rule.getMinimum());
                errors.add(createValidationError(rowNumber, sheetName, rule.getFieldName(), errorMessage));
            }
            
            if (rule.getMaximum() != null && numValue > rule.getMaximum().doubleValue()) {
                String errorMessage = String.format("Field '%s' must not exceed %s", 
                        rule.getFieldName(), rule.getMaximum());
                errors.add(createValidationError(rowNumber, sheetName, rule.getFieldName(), errorMessage));
            }
            
        } catch (NumberFormatException e) {
            String errorMessage = String.format("Field '%s' must be a valid number", rule.getFieldName());
            errors.add(createValidationError(rowNumber, sheetName, rule.getFieldName(), errorMessage));
        }
    }

    private void validateEnumField(Object value, ValidationRule rule, int rowNumber, 
            String sheetName, List<ValidationError> errors) {
        
        String strValue = value.toString();
        if (rule.getAllowedValues() != null && !rule.getAllowedValues().contains(strValue)) {
            String errorMessage = String.format("Field '%s' must be one of: %s", 
                    rule.getFieldName(), String.join(", ", rule.getAllowedValues()));
            errors.add(createValidationError(rowNumber, sheetName, rule.getFieldName(), errorMessage));
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
    }
    
    /**
     * Maps sheet names to schema names using configuration
     * Handles localized sheet names and Excel's 31-character limit
     */
    private String getSchemaNameForSheet(String sheetName, String type, Map<String, String> localizationMap) {
        if (!sheetSchemaConfig.isProcessingTypeSupported(type)) {
            log.warn("No schema mapping configured for type: {}", type);
            return null;
        }
        
        // Get configuration for this processing type
        Map<String, String> typeConfig = sheetSchemaConfig.getConfigForType(type);
        if (typeConfig == null) {
            log.warn("No configuration found for processing type: {}", type);
            return null;
        }
        
        // Check each configured sheet against the provided sheet name
        for (Map.Entry<String, String> entry : typeConfig.entrySet()) {
            String sheetKey = entry.getKey();
            String schemaName = entry.getValue();
            
            // Get localized sheet name and trim to 31 characters if needed
            String localizedSheetName = getLocalizedSheetName(sheetKey, localizationMap);
            
            if (localizedSheetName.equals(sheetName)) {
                log.debug("Matched sheet '{}' to schema '{}'", sheetName, schemaName);
                return schemaName;
            }
        }
        
        log.warn("Unknown sheet name '{}' for processing type: {}", sheetName, type);
        return null;
    }
    
    /**
     * Gets localized sheet name and trims to 31 characters if needed
     */
    private String getLocalizedSheetName(String key, Map<String, String> localizationMap) {
        String localizedName = key;
        
        // Get localized value if available
        if (localizationMap != null && localizationMap.containsKey(key)) {
            localizedName = localizationMap.get(key);
        }
        
        // Trim to 31 characters if needed (Excel sheet name limit)
        if (localizedName.length() > 31) {
            localizedName = localizedName.substring(0, 31);
            log.debug("Trimmed sheet name from {} to {} (31 char limit)", 
                    localizationMap != null ? localizationMap.get(key) : key, localizedName);
        }
        
        return localizedName;
    }
}