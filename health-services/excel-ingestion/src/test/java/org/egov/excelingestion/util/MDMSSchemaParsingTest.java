package org.egov.excelingestion.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.egov.excelingestion.config.ExcelIngestionConfig;
import org.egov.excelingestion.generator.SchemaBasedSheetGenerator;
import org.egov.excelingestion.web.models.excel.ColumnDef;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * Integration test to verify MDMS schema parsing with all validation types
 */
@Slf4j
public class MDMSSchemaParsingTest {

    @Test
    void testCompleteSchemaParsingWithAllValidations() throws Exception {
        log.info("🧪 Testing complete MDMS schema parsing with all validation types");
        
        // Create a mock MDMS schema with all validation properties
        String mockSchema = """
        {
            "stringProperties": [
                {
                    "name": "userName",
                    "description": "User name field",
                    "isRequired": true,
                    "minLength": 3,
                    "maxLength": 50,
                    "pattern": "^[a-zA-Z]+\\\\$",
                    "errorMessage": "HCM_USER_NAME_ERROR"
                },
                {
                    "name": "email",
                    "description": "Email field", 
                    "isRequired": true,
                    "minLength": 5,
                    "maxLength": 100,
                    "pattern": "^[a-zA-Z0-9._%-]+@[a-zA-Z0-9.-]+\\\\.[a-zA-Z]{2,}\\\\$",
                    "errorMessage": "HCM_EMAIL_ERROR"
                }
            ],
            "numberProperties": [
                {
                    "name": "age",
                    "description": "Age field",
                    "isRequired": true,
                    "minimum": 18,
                    "maximum": 65,
                    "errorMessage": "HCM_AGE_ERROR"
                },
                {
                    "name": "salary",
                    "description": "Salary field",
                    "minimum": 10000,
                    "exclusiveMaximum": 100000,
                    "multipleOf": 1000,
                    "errorMessage": "HCM_SALARY_ERROR"
                },
                {
                    "name": "rating",
                    "description": "Rating field",
                    "exclusiveMinimum": 0,
                    "exclusiveMaximum": 10,
                    "errorMessage": "HCM_RATING_ERROR"
                }
            ],
            "enumProperties": [
                {
                    "name": "category",
                    "description": "Category field",
                    "isRequired": true,
                    "enum": ["A", "B", "C"],
                    "errorMessage": "HCM_CATEGORY_ERROR"
                }
            ]
        }
        """;
        
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode schemaNode = objectMapper.readTree(mockSchema);
        
        // Create SchemaBasedSheetGenerator instance
        SchemaBasedSheetGenerator generator = new SchemaBasedSheetGenerator(null, null);
        
        // Parse string properties
        List<ColumnDef> stringColumns = parseStringProperties(generator, schemaNode);
        log.info("📋 Parsed {} string columns", stringColumns.size());
        
        // Parse number properties  
        List<ColumnDef> numberColumns = parseNumberProperties(generator, schemaNode);
        log.info("📋 Parsed {} number columns", numberColumns.size());
        
        // Parse enum properties
        List<ColumnDef> enumColumns = parseEnumProperties(generator, schemaNode);
        log.info("📋 Parsed {} enum columns", enumColumns.size());
        
        // Verify string column parsing
        verifyStringColumns(stringColumns);
        
        // Verify number column parsing
        verifyNumberColumns(numberColumns);
        
        // Verify enum column parsing
        verifyEnumColumns(enumColumns);
        
        // Test Excel generation with all validations
        testExcelGenerationWithParsedColumns(stringColumns, numberColumns, enumColumns);
        
        log.info("✅ Complete MDMS schema parsing test passed");
    }
    
    @SuppressWarnings("unchecked")
    private List<ColumnDef> parseStringProperties(SchemaBasedSheetGenerator generator, JsonNode schemaNode) {
        // Use reflection to access private method
        try {
            var method = generator.getClass().getDeclaredMethod("parseJsonToColumnDef", JsonNode.class, String.class);
            method.setAccessible(true);
            
            List<ColumnDef> columns = new java.util.ArrayList<>();
            JsonNode stringProps = schemaNode.path("stringProperties");
            for (JsonNode node : stringProps) {
                ColumnDef column = (ColumnDef) method.invoke(generator, node, "string");
                columns.add(column);
            }
            return columns;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse string properties", e);
        }
    }
    
    @SuppressWarnings("unchecked")
    private List<ColumnDef> parseNumberProperties(SchemaBasedSheetGenerator generator, JsonNode schemaNode) {
        try {
            var method = generator.getClass().getDeclaredMethod("parseJsonToColumnDef", JsonNode.class, String.class);
            method.setAccessible(true);
            
            List<ColumnDef> columns = new java.util.ArrayList<>();
            JsonNode numberProps = schemaNode.path("numberProperties");
            for (JsonNode node : numberProps) {
                ColumnDef column = (ColumnDef) method.invoke(generator, node, "number");
                columns.add(column);
            }
            return columns;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse number properties", e);
        }
    }
    
    @SuppressWarnings("unchecked") 
    private List<ColumnDef> parseEnumProperties(SchemaBasedSheetGenerator generator, JsonNode schemaNode) {
        try {
            var method = generator.getClass().getDeclaredMethod("parseJsonToColumnDef", JsonNode.class, String.class);
            method.setAccessible(true);
            
            List<ColumnDef> columns = new java.util.ArrayList<>();
            JsonNode enumProps = schemaNode.path("enumProperties");
            for (JsonNode node : enumProps) {
                ColumnDef column = (ColumnDef) method.invoke(generator, node, "enum");
                columns.add(column);
            }
            return columns;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse enum properties", e);
        }
    }
    
    private void verifyStringColumns(List<ColumnDef> columns) {
        log.info("🔍 Verifying string column parsing...");
        
        ColumnDef userNameCol = findColumn(columns, "userName");
        assert userNameCol != null : "userName column should exist";
        assert userNameCol.getMinLength() == 3 : "userName should have minLength=3";
        assert userNameCol.getMaxLength() == 50 : "userName should have maxLength=50"; 
        log.info("userName pattern value: '{}'", userNameCol.getPattern());
        assert userNameCol.getPattern() != null : "userName should have pattern";
        assert "HCM_USER_NAME_ERROR".equals(userNameCol.getErrorMessage()) : "userName should have errorMessage";
        log.info("✅ userName column validation properties verified");
        
        ColumnDef emailCol = findColumn(columns, "email");
        assert emailCol != null : "email column should exist";
        assert emailCol.getMinLength() == 5 : "email should have minLength=5";
        assert emailCol.getMaxLength() == 100 : "email should have maxLength=100";
        assert emailCol.getPattern() != null && emailCol.getPattern().contains("@") : "email should have email pattern";
        assert "HCM_EMAIL_ERROR".equals(emailCol.getErrorMessage()) : "email should have errorMessage";
        log.info("✅ email column validation properties verified");
    }
    
    private void verifyNumberColumns(List<ColumnDef> columns) {
        log.info("🔍 Verifying number column parsing...");
        
        ColumnDef ageCol = findColumn(columns, "age");
        assert ageCol != null : "age column should exist";
        assert ageCol.getMinimum().doubleValue() == 18.0 : "age should have minimum=18";
        assert ageCol.getMaximum().doubleValue() == 65.0 : "age should have maximum=65";
        assert "HCM_AGE_ERROR".equals(ageCol.getErrorMessage()) : "age should have errorMessage";
        log.info("✅ age column validation properties verified");
        
        ColumnDef salaryCol = findColumn(columns, "salary");
        assert salaryCol != null : "salary column should exist"; 
        assert salaryCol.getMinimum().doubleValue() == 10000.0 : "salary should have minimum=10000";
        assert salaryCol.getExclusiveMaximum().doubleValue() == 100000.0 : "salary should have exclusiveMaximum=100000";
        assert salaryCol.getMultipleOf().doubleValue() == 1000.0 : "salary should have multipleOf=1000";
        assert "HCM_SALARY_ERROR".equals(salaryCol.getErrorMessage()) : "salary should have errorMessage";
        log.info("✅ salary column validation properties verified");
        
        ColumnDef ratingCol = findColumn(columns, "rating");
        assert ratingCol != null : "rating column should exist";
        assert ratingCol.getExclusiveMinimum().doubleValue() == 0.0 : "rating should have exclusiveMinimum=0";
        assert ratingCol.getExclusiveMaximum().doubleValue() == 10.0 : "rating should have exclusiveMaximum=10";
        assert "HCM_RATING_ERROR".equals(ratingCol.getErrorMessage()) : "rating should have errorMessage";
        log.info("✅ rating column validation properties verified");
    }
    
    private void verifyEnumColumns(List<ColumnDef> columns) {
        log.info("🔍 Verifying enum column parsing...");
        
        ColumnDef categoryCol = findColumn(columns, "category");
        assert categoryCol != null : "category column should exist";
        assert categoryCol.getEnumValues() != null : "category should have enumValues";
        assert categoryCol.getEnumValues().size() == 3 : "category should have 3 enum values";
        assert categoryCol.getEnumValues().contains("A") : "category should contain value A";
        assert categoryCol.getEnumValues().contains("B") : "category should contain value B";
        assert categoryCol.getEnumValues().contains("C") : "category should contain value C";
        assert "HCM_CATEGORY_ERROR".equals(categoryCol.getErrorMessage()) : "category should have errorMessage";
        log.info("✅ category column validation properties verified");
    }
    
    private ColumnDef findColumn(List<ColumnDef> columns, String name) {
        return columns.stream().filter(col -> name.equals(col.getName())).findFirst().orElse(null);
    }
    
    private void testExcelGenerationWithParsedColumns(List<ColumnDef> stringColumns, 
                                                     List<ColumnDef> numberColumns,
                                                     List<ColumnDef> enumColumns) throws IOException {
        log.info("🧪 Testing Excel generation with parsed columns...");
        
        // Combine all columns
        List<ColumnDef> allColumns = new java.util.ArrayList<>();
        allColumns.addAll(stringColumns);
        allColumns.addAll(numberColumns);  
        allColumns.addAll(enumColumns);
        
        // Create Excel with validations
        ExcelIngestionConfig config = new ExcelIngestionConfig();
        config.setExcelRowLimit(1000);
        
        ExcelStyleHelper styleHelper = new ExcelStyleHelper();
        CellProtectionManager protectionManager = new CellProtectionManager(config, styleHelper);
        ExcelDataPopulator populator = new ExcelDataPopulator(config, styleHelper, protectionManager);
        
        Workbook workbook = populator.populateSheetWithData("CompleteValidationTest", allColumns, null);
        
        // Save for manual inspection
        try (FileOutputStream fileOut = new FileOutputStream("/tmp/complete_validation_test.xlsx")) {
            workbook.write(fileOut);
            log.info("✅ Complete validation test Excel saved to: /tmp/complete_validation_test.xlsx");
        }
        
        // Verify validations
        Sheet sheet = workbook.getSheetAt(0);
        List<? extends DataValidation> validations = sheet.getDataValidations();
        
        log.info("📊 Total validations applied: {}", validations.size());
        // Should have validations for: userName, email (string), age, salary, rating (number), category (enum)
        // Expected: 2 string + 3 number + 1 enum = 6 validations
        assert validations.size() == 6 : "Should have 6 validations total";
        
        workbook.close();
        log.info("✅ Excel generation with parsed columns verified");
    }
}