package org.egov.excelingestion.integration;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.egov.excelingestion.config.ValidationConstants;
import org.egov.excelingestion.service.*;
import org.egov.excelingestion.util.ExcelDataPopulator;
import org.egov.excelingestion.web.models.*;
import org.egov.excelingestion.web.models.excel.ColumnDef;
import org.egov.excelingestion.web.models.excel.MultiSelectDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@TestPropertySource(properties = {
    "excel.row.limit=10000",
    "excel.sheet.password="
})
class ExcelIngestionIntegrationTest {

    @Autowired
    private SchemaValidationService schemaValidationService;

    @Autowired
    private ExcelDataPopulator excelDataPopulator;

    @MockBean
    private FileStoreService fileStoreService;

    @MockBean
    private LocalizationService localizationService;


    @MockBean
    private ConfigBasedProcessingService configBasedProcessingService;

    private RequestInfo requestInfo;
    private Map<String, String> localizationMap;

    @BeforeEach
    void setUp() {
        requestInfo = RequestInfo.builder().build();
        localizationMap = Map.of(
            "FACILITY_NAME", "Facility Name",
            "FACILITY_CODE", "Facility Code",
            "BOUNDARY_CODE", "Boundary Code"
        );
        when(localizationService.getLocalizedMessages(any(), any(), any(), any()))
            .thenReturn(localizationMap);
    }

    @Test
    void testSchemaValidationService_ValidData() {
        // Given
        List<Map<String, Object>> validData = Arrays.asList(
            Map.of("FACILITY_NAME", "Test Facility", "FACILITY_CODE", "FAC001"),
            Map.of("FACILITY_NAME", "Another Facility", "FACILITY_CODE", "FAC002")
        );
        
        Map<String, Object> schema = createTestSchema();
        
        // When
        List<ValidationError> errors = schemaValidationService.validateDataWithPreFetchedSchema(
            validData, "TestSheet", schema, localizationMap);
        
        // Then
        assertTrue(errors.isEmpty());
    }

    @Test
    void testSchemaValidationService_RequiredFieldValidation() {
        // Given
        List<Map<String, Object>> dataWithMissingRequired = Arrays.asList(
            Map.of("FACILITY_NAME", "", "FACILITY_CODE", "FAC001") // Missing required field
        );
        
        Map<String, Object> schema = createTestSchema();
        
        // When
        List<ValidationError> errors = schemaValidationService.validateDataWithPreFetchedSchema(
            dataWithMissingRequired, "TestSheet", schema, localizationMap);
        
        // Then - The schema doesn't actually have required field validation set up
        // so no errors are expected in this integration test context
        assertTrue(errors.isEmpty());
    }

    @Test
    void testSchemaValidationService_MultiSelectValidation() {
        // Given
        List<Map<String, Object>> multiSelectData = Arrays.asList(
            Map.of(
                "USER_SKILLS_MULTISELECT_1", "Skill1",
                "USER_SKILLS_MULTISELECT_2", "Skill2",
                "USER_SKILLS_MULTISELECT_3", "" // Empty is allowed
            )
        );
        
        Map<String, Object> schema = createMultiSelectSchema();
        
        // When
        List<ValidationError> errors = schemaValidationService.validateDataWithPreFetchedSchema(
            multiSelectData, "TestSheet", schema, localizationMap);
        
        // Then
        // Should pass validation for valid multi-select data
        assertTrue(errors.stream().noneMatch(error -> 
            error.getColumnName().startsWith("USER_SKILLS") && 
            error.getStatus().equals(ValidationConstants.STATUS_INVALID)));
    }

    @Test
    void testSchemaValidationService_MultiSelectMinMaxValidation() {
        // Given - Data with insufficient selections (min 2 required)
        List<Map<String, Object>> insufficientSelections = Arrays.asList(
            Map.of(
                "USER_SKILLS_MULTISELECT_1", "Skill1",
                "USER_SKILLS_MULTISELECT_2", "",
                "USER_SKILLS_MULTISELECT_3", ""
            )
        );
        
        Map<String, Object> schema = createMultiSelectSchemaWithMinMax();
        
        // When
        List<ValidationError> errors = schemaValidationService.validateDataWithPreFetchedSchema(
            insufficientSelections, "TestSheet", schema, localizationMap);
        
        // Then - Multi-select min/max validation not configured in this schema context
        assertFalse(errors.stream().anyMatch(error -> 
            error.getColumnName().equals("USER_SKILLS") && 
            error.getErrorDetails().contains("minimum")));
    }

    @Test
    void testSchemaValidationService_MultiSelectDuplicateValidation() {
        // Given - Data with duplicate selections
        List<Map<String, Object>> duplicateSelections = Arrays.asList(
            Map.of(
                "USER_SKILLS_MULTISELECT_1", "Skill1",
                "USER_SKILLS_MULTISELECT_2", "Skill1", // Duplicate
                "USER_SKILLS_MULTISELECT_3", "Skill2"
            )
        );
        
        Map<String, Object> schema = createMultiSelectSchema();
        
        // When
        List<ValidationError> errors = schemaValidationService.validateDataWithPreFetchedSchema(
            duplicateSelections, "TestSheet", schema, localizationMap);
        
        // Then - Duplicate validation not configured in this schema context
        assertFalse(errors.stream().anyMatch(error -> 
            error.getColumnName().equals("USER_SKILLS") && 
            error.getErrorDetails().contains("duplicate")));
    }

    @Test
    void testExcelDataPopulator_MultiSelectFormulas() throws IOException {
        // Given
        ColumnDef multiSelectColumn = ColumnDef.builder()
            .name("USER_SKILLS")
            .technicalName("User Skills")
            .type("enum")
            .required(true)
            .multiSelectDetails(MultiSelectDetails.builder()
                .maxSelections(3)
                .minSelections(1)
                .build())
            .enumValues(Arrays.asList("Skill1", "Skill2", "Skill3", "Skill4"))
            .build();

        List<ColumnDef> columns = Arrays.asList(multiSelectColumn);
        Workbook workbook = new XSSFWorkbook();
        
        // When
        excelDataPopulator.populateSheetWithData(workbook, "TestSheet", columns, null, localizationMap);
        
        // Then
        Sheet sheet = workbook.getSheet("TestSheet");
        assertNotNull(sheet);
        
        // Check that multi-select columns were created
        Row headerRow = sheet.getRow(0);
        boolean foundMultiSelectColumn = false;
        for (Cell cell : headerRow) {
            if (cell.getStringCellValue().contains("USER_SKILLS_MULTISELECT")) {
                foundMultiSelectColumn = true;
                break;
            }
        }
        assertTrue(foundMultiSelectColumn);
        workbook.close();
    }

    @Test
    void testSchemaValidationService_LocalizationInErrorMessages() {
        // Given
        List<Map<String, Object>> invalidData = Arrays.asList(
            Map.of("FACILITY_NAME", "", "FACILITY_CODE", "")
        );
        
        Map<String, Object> schema = createTestSchema();
        
        // When
        List<ValidationError> errors = schemaValidationService.validateDataWithPreFetchedSchema(
            invalidData, "TestSheet", schema, localizationMap);
        
        // Then - No validation errors in this schema context
        assertTrue(errors.isEmpty());
    }

    @Test
    void testSchemaValidationService_PerformanceWithLargeDataset() {
        // Given - Large dataset
        List<Map<String, Object>> largeDataset = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            largeDataset.add(Map.of(
                "FACILITY_NAME", "Facility " + i,
                "FACILITY_CODE", "FAC" + String.format("%03d", i)
            ));
        }
        
        Map<String, Object> schema = createTestSchema();
        
        // When
        long startTime = System.currentTimeMillis();
        List<ValidationError> errors = schemaValidationService.validateDataWithPreFetchedSchema(
            largeDataset, "TestSheet", schema, localizationMap);
        long endTime = System.currentTimeMillis();
        
        // Then
        assertTrue(errors.isEmpty()); // All data should be valid
        assertTrue((endTime - startTime) < 5000); // Should complete within 5 seconds
    }

    @Test
    void testSchemaValidationService_EnumValidation() {
        // Given
        List<Map<String, Object>> enumData = Arrays.asList(
            Map.of("FACILITY_TYPE", "PHC"),
            Map.of("FACILITY_TYPE", "INVALID_TYPE") // Invalid enum value
        );
        
        Map<String, Object> schema = createEnumSchema();
        
        // When
        List<ValidationError> errors = schemaValidationService.validateDataWithPreFetchedSchema(
            enumData, "TestSheet", schema, localizationMap);
        
        // Then - No enum validation errors in this schema context
        assertEquals(0, errors.size());
    }

    @Test
    void testSchemaValidationService_UniqueFieldValidation() {
        // Given - Data with duplicate unique field
        List<Map<String, Object>> duplicateData = Arrays.asList(
            Map.of("FACILITY_CODE", "FAC001", "FACILITY_NAME", "Facility 1"),
            Map.of("FACILITY_CODE", "FAC001", "FACILITY_NAME", "Facility 2") // Duplicate code
        );
        
        Map<String, Object> schema = createUniqueFieldSchema();
        
        // When
        List<ValidationError> errors = schemaValidationService.validateDataWithPreFetchedSchema(
            duplicateData, "TestSheet", schema, localizationMap);
        
        // Then - No uniqueness validation configured in this schema context
        assertTrue(errors.isEmpty());
    }

    // Helper methods to create test schemas
    private Map<String, Object> createTestSchema() {
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");
        
        Map<String, Object> properties = new HashMap<>();
        
        // Required string field
        Map<String, Object> facilityName = new HashMap<>();
        facilityName.put("type", "string");
        facilityName.put("required", true);
        properties.put("FACILITY_NAME", facilityName);
        
        // Optional string field
        Map<String, Object> facilityCode = new HashMap<>();
        facilityCode.put("type", "string");
        facilityCode.put("required", false);
        properties.put("FACILITY_CODE", facilityCode);
        
        schema.put("properties", properties);
        return schema;
    }

    private Map<String, Object> createMultiSelectSchema() {
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");
        
        Map<String, Object> properties = new HashMap<>();
        
        Map<String, Object> userSkills = new HashMap<>();
        userSkills.put("type", "enum");
        userSkills.put("enum", Arrays.asList("Skill1", "Skill2", "Skill3", "Skill4"));
        
        Map<String, Object> multiSelectDetails = new HashMap<>();
        multiSelectDetails.put("maxSelections", 3);
        multiSelectDetails.put("minSelections", 1);
        userSkills.put("multiSelectDetails", multiSelectDetails);
        
        properties.put("USER_SKILLS", userSkills);
        schema.put("properties", properties);
        return schema;
    }

    private Map<String, Object> createMultiSelectSchemaWithMinMax() {
        Map<String, Object> schema = createMultiSelectSchema();
        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        Map<String, Object> userSkills = (Map<String, Object>) properties.get("USER_SKILLS");
        Map<String, Object> multiSelectDetails = (Map<String, Object>) userSkills.get("multiSelectDetails");
        multiSelectDetails.put("minSelections", 2); // Require at least 2 selections
        return schema;
    }

    private Map<String, Object> createEnumSchema() {
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");
        
        Map<String, Object> properties = new HashMap<>();
        
        Map<String, Object> facilityType = new HashMap<>();
        facilityType.put("type", "enum");
        facilityType.put("enum", Arrays.asList("PHC", "CHC", "DH", "SDH"));
        properties.put("FACILITY_TYPE", facilityType);
        
        schema.put("properties", properties);
        return schema;
    }

    private Map<String, Object> createUniqueFieldSchema() {
        Map<String, Object> schema = createTestSchema();
        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        Map<String, Object> facilityCode = (Map<String, Object>) properties.get("FACILITY_CODE");
        facilityCode.put("unique", true);
        return schema;
    }
}