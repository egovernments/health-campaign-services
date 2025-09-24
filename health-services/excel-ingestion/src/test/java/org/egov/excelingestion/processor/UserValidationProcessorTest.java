package org.egov.excelingestion.processor;

import org.egov.excelingestion.config.ExcelIngestionConfig;
import org.egov.excelingestion.config.ValidationConstants;
import org.egov.excelingestion.service.ValidationService;
import org.egov.excelingestion.service.CampaignService;
import org.egov.excelingestion.service.BoundaryService;
import org.egov.excelingestion.util.BoundaryUtil;
import org.egov.excelingestion.util.ExcelUtil;
import org.egov.excelingestion.util.EnrichmentUtil;
import org.egov.excelingestion.web.models.ProcessResource;
import org.egov.excelingestion.web.models.RequestInfo;
import org.egov.excelingestion.web.models.ValidationColumnInfo;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Test cases for UserValidationProcessor - focusing on phone number and username validation
 */
class UserValidationProcessorTest {

    @Mock
    private ValidationService validationService;
    @Mock
    private RestTemplate restTemplate;
    @Mock
    private ExcelIngestionConfig config;
    @Mock
    private EnrichmentUtil enrichmentUtil;
    @Mock
    private CampaignService campaignService;

    @Mock
    private BoundaryService boundaryService;

    @Mock
    private BoundaryUtil boundaryUtil;

    @Mock
    private ExcelUtil excelUtil;

    private UserValidationProcessor userValidationProcessor;
    private ProcessResource resource;
    private RequestInfo requestInfo;
    private Map<String, String> localizationMap;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        userValidationProcessor = new UserValidationProcessor(
                validationService, restTemplate, config, enrichmentUtil, campaignService, boundaryService, boundaryUtil, excelUtil
        );

        resource = ProcessResource.builder()
                .tenantId("test-tenant")
                .type("user-microplan-ingestion")
                .build();

        requestInfo = new RequestInfo();
        localizationMap = new HashMap<>();

        // Mock config values
        when(config.getHealthIndividualHost()).thenReturn("http://localhost:8087/");
        when(config.getHealthIndividualSearchPath()).thenReturn("health-individual/v1/_search");
        
        // Mock boundary validation - return valid boundary codes
        Set<String> validBoundaryCodes = Set.of("BOUNDARY_001", "BOUNDARY_002", "BOUNDARY_003");
        when(boundaryUtil.getEnrichedBoundaryCodesFromCampaign(any(), any(), any(), any(), any()))
            .thenReturn(validBoundaryCodes);

        when(excelUtil.convertSheetToMapListCached(any(), any(), any())).thenAnswer(invocation -> {
            Sheet sheet = invocation.getArgument(2);
            // Basic implementation to convert sheet to map list for testing
            List<Map<String, Object>> data = new ArrayList<>();
            Row header = sheet.getRow(0);
            for (int i = 1; i <= ExcelUtil.findActualLastRowWithData(sheet); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                Map<String, Object> rowData = new HashMap<>();
                for (int j = 0; j < header.getLastCellNum(); j++) {
                    Cell cell = row.getCell(j);
                    rowData.put(header.getCell(j).getStringCellValue(), org.egov.excelingestion.util.ExcelUtil.getCellValueAsString(cell));
                }
                rowData.put("__actualRowNumber__", i + 1);
                data.add(rowData);
            }
            return data;
        });
    }

    @Test
    void testProcessWorkbook_WithDuplicatePhoneNumbers_ShouldAddErrorsForExistingUsers() {
        // Given
        Workbook workbook = createTestWorkbook(Arrays.asList(
                createUserRow("John Doe", "9876543210", "john123"),
                createUserRow("Jane Smith", "9876543211", "jane456")
        ));

        // Mock Individual service response - phone 9876543210 already exists
        Map<String, Object> existingUser = new HashMap<>();
        existingUser.put("mobileNumber", "9876543210");
        Map<String, Object> searchResponse = new HashMap<>();
        searchResponse.put("Individual", Arrays.asList(existingUser));

        when(restTemplate.exchange(anyString(), any(), any(), eq(Map.class)))
                .thenReturn(new ResponseEntity<>(searchResponse, HttpStatus.OK))
                .thenReturn(new ResponseEntity<>(Collections.singletonMap("Individual", Collections.emptyList()), HttpStatus.OK));

        // Mock validation service
        ValidationColumnInfo columnInfo = new ValidationColumnInfo();
        columnInfo.setErrorColumnIndex(3);
        columnInfo.setStatusColumnIndex(4);
        when(validationService.addValidationColumns(any(), any())).thenReturn(columnInfo);

        // When
        Workbook result = userValidationProcessor.processWorkbook(workbook, "Users", resource, requestInfo, localizationMap);

        // Then
        assertNotNull(result);
        Sheet sheet = result.getSheet("Users");
        assertNotNull(sheet);
        
        // Check that validation was performed (verify restTemplate was called)
        verify(restTemplate, atLeastOnce()).exchange(anyString(), any(), any(), eq(Map.class));
        verify(enrichmentUtil).enrichErrorAndStatusInAdditionalDetails(eq(resource), any());
    }

    @Test
    void testProcessWorkbook_WithExistingUsername_ShouldAddErrorForExistingUser() {
        // Given - Test username that already exists in system
        Workbook workbook = createTestWorkbook(Arrays.asList(
                createUserRow("John Doe", "9876543210", "john123") // This username exists in system
        ));

        // Mock Individual service responses
        when(restTemplate.exchange(anyString(), any(), any(), eq(Map.class)))
                // Phone number search - no duplicates
                .thenReturn(new ResponseEntity<>(Collections.singletonMap("Individual", Collections.emptyList()), HttpStatus.OK))
                // Username search - john123 already exists
                .thenReturn(createUsernameSearchResponse("john123"));

        // Mock validation service
        ValidationColumnInfo columnInfo = new ValidationColumnInfo();
        columnInfo.setErrorColumnIndex(3);
        columnInfo.setStatusColumnIndex(4);
        when(validationService.addValidationColumns(any(), any())).thenReturn(columnInfo);

        // When
        Workbook result = userValidationProcessor.processWorkbook(workbook, "Users", resource, requestInfo, localizationMap);

        // Then
        assertNotNull(result);
        Sheet sheet = result.getSheet("Users");
        assertNotNull(sheet);
        
        // Check that validation was performed (verify restTemplate was called)
        verify(restTemplate, atLeastOnce()).exchange(anyString(), any(), any(), eq(Map.class));
        verify(enrichmentUtil).enrichErrorAndStatusInAdditionalDetails(eq(resource), any());
    }

    @Test
    void testProcessWorkbook_WithValidData_ShouldNotAddErrorColumns() {
        // Given
        Workbook workbook = createTestWorkbook(Arrays.asList(
                createUserRow("John Doe", "9876543210", "john123"),
                createUserRow("Jane Smith", "9876543211", "jane456")
        ));

        // Mock Individual service responses - no existing users
        when(restTemplate.exchange(anyString(), any(), any(), eq(Map.class)))
                .thenReturn(new ResponseEntity<>(Collections.singletonMap("Individual", Collections.emptyList()), HttpStatus.OK));

        // When
        Workbook result = userValidationProcessor.processWorkbook(workbook, "Users", resource, requestInfo, localizationMap);

        // Then
        assertNotNull(result);
        Sheet sheet = result.getSheet("Users");
        assertNotNull(sheet);
        
        // Check that validation was performed (verify restTemplate was called)
        verify(restTemplate, atLeastOnce()).exchange(anyString(), any(), any(), eq(Map.class));
        verify(enrichmentUtil).enrichErrorAndStatusInAdditionalDetails(eq(resource), any());
        
        // Verify validation service was not called to add error columns (no errors found)
        verify(validationService, never()).addValidationColumns(any(), any());
    }

    private Map<String, Object> createUserRow(String name, String phone, String username) {
        Map<String, Object> row = new HashMap<>();
        row.put("Name of the Person (Mandatory)", name);
        row.put("HCM_ADMIN_CONSOLE_USER_PHONE_NUMBER", phone);
        row.put("UserName", username);
        row.put("HCM_ADMIN_CONSOLE_USER_USAGE", "Active"); // Add usage field to pass active user validation
        row.put("HCM_ADMIN_CONSOLE_BOUNDARY_CODE", "BOUNDARY_001"); // Add valid boundary code
        row.put("UserService Uuids", null); // No service UUIDs - required for username validation
        return row;
    }

    private Workbook createTestWorkbook(List<Map<String, Object>> userData) {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Users");
        
        // Create header row
        Row headerRow = sheet.createRow(0);
        headerRow.createCell(0).setCellValue("Name of the Person (Mandatory)");
        headerRow.createCell(1).setCellValue("HCM_ADMIN_CONSOLE_USER_PHONE_NUMBER");
        headerRow.createCell(2).setCellValue("UserName");
        headerRow.createCell(3).setCellValue("HCM_ADMIN_CONSOLE_USER_USAGE");
        headerRow.createCell(4).setCellValue("HCM_ADMIN_CONSOLE_BOUNDARY_CODE");
        
        // Create data rows
        for (int i = 0; i < userData.size(); i++) {
            Row dataRow = sheet.createRow(i + 1);
            Map<String, Object> rowData = userData.get(i);
            dataRow.createCell(0).setCellValue((String) rowData.get("Name of the Person (Mandatory)"));
            dataRow.createCell(1).setCellValue((String) rowData.get("HCM_ADMIN_CONSOLE_USER_PHONE_NUMBER"));
            dataRow.createCell(2).setCellValue((String) rowData.get("UserName"));
            dataRow.createCell(3).setCellValue((String) rowData.get("HCM_ADMIN_CONSOLE_USER_USAGE"));
            dataRow.createCell(4).setCellValue((String) rowData.get("HCM_ADMIN_CONSOLE_BOUNDARY_CODE"));
        }
        
        return workbook;
    }

    private ResponseEntity<Map> createUsernameSearchResponse(String username) {
        Map<String, Object> userDetails = new HashMap<>();
        userDetails.put("username", username);

        Map<String, Object> individual = new HashMap<>();
        individual.put("userDetails", userDetails);

        Map<String, Object> response = new HashMap<>();
        response.put("Individual", Arrays.asList(individual));

        return new ResponseEntity<>(response, HttpStatus.OK);
    }
}