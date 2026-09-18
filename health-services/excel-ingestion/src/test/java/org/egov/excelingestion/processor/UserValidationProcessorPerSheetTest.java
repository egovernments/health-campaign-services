package org.egov.excelingestion.processor;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.egov.excelingestion.config.ExcelIngestionConfig;
import org.egov.excelingestion.config.ValidationConstants;
import org.egov.excelingestion.exception.CustomExceptionHandler;
import org.egov.excelingestion.service.*;
import org.egov.excelingestion.util.*;
import org.egov.excelingestion.web.models.ProcessResource;
import org.egov.common.contract.request.RequestInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.web.client.RestTemplate;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Test to verify that UserValidationProcessor correctly sets per-sheet status in additionalDetails
 */
class UserValidationProcessorPerSheetTest {

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
    @Mock
    private CustomExceptionHandler exceptionHandler;

    private UserValidationProcessor userValidationProcessor;
    private ProcessResource resource;
    private RequestInfo requestInfo;
    private Map<String, String> localizationMap;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        // Use real EnrichmentUtil, not mocked
        EnrichmentUtil realEnrichmentUtil = new EnrichmentUtil();

        userValidationProcessor = new UserValidationProcessor(
                validationService, restTemplate, config, realEnrichmentUtil, campaignService,
                boundaryService, boundaryUtil, excelUtil, exceptionHandler
        );

        resource = ProcessResource.builder()
                .id("test-resource-1")
                .referenceId("campaign-123")
                .tenantId("test-tenant")
                .type("user-microplan-ingestion")
                .fileStoreId("file-123")
                .additionalDetails(new HashMap<>())
                .build();

        requestInfo = new RequestInfo();
        localizationMap = new HashMap<>();

        // Mock config values
        when(config.getHealthIndividualHost()).thenReturn("http://localhost:8087/");
        when(config.getHealthIndividualSearchPath()).thenReturn("health-individual/v1/_search");
    }

    /**
     * Test that UserValidationProcessor sets userSheetStatus with correct sheetKind
     * This verifies that enrichErrorAndStatusInAdditionalDetails is called with SHEET_KIND_USER
     */
    @Test
    void testUserValidationProcessorPassesUserSheetKind() throws Exception {
        // Create a test workbook with user sheet
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("HCM_ADMIN_CONSOLE_USER_LIST");

        // Add header row
        Row headerRow = sheet.createRow(0);
        headerRow.createCell(0).setCellValue("User Name");
        headerRow.createCell(1).setCellValue("Mobile Number");

        // Add data row
        Row dataRow = sheet.createRow(1);
        dataRow.createCell(0).setCellValue("TestUser");
        dataRow.createCell(1).setCellValue("9999999999");

        // Mock excelUtil to return empty sheet data
        List<Map<String, Object>> sheetData = new ArrayList<>();
        when(excelUtil.convertSheetToMapListCached(anyString(), anyString(), any(Sheet.class)))
                .thenReturn(sheetData);

        // Process the workbook
        Workbook result = userValidationProcessor.processWorkbook(workbook, "HCM_ADMIN_CONSOLE_USER_LIST", resource, requestInfo, localizationMap);

        // Verify that additionalDetails has been populated
        Map<String, Object> additionalDetails = resource.getAdditionalDetails();
        assertNotNull(additionalDetails, "additionalDetails should be populated after processing");

        // Verify that userSheetStatus key exists (indicating SHEET_KIND_USER was passed)
        // The value may vary based on actual validation, but the key should exist
        assertNotNull(additionalDetails.get(ValidationConstants.ADDITIONAL_DETAILS_USER_SHEET_STATUS),
                "userSheetStatus should be set when UserValidationProcessor calls enrichErrorAndStatusInAdditionalDetails with SHEET_KIND_USER");

        workbook.close();
    }
}
