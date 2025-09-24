package org.egov.excelingestion.processor;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.egov.excelingestion.config.ErrorConstants;
import org.egov.excelingestion.exception.CustomExceptionHandler;
import org.egov.excelingestion.service.CampaignService;
import org.egov.excelingestion.service.MDMSService;
import org.egov.excelingestion.service.SchemaValidationService;
import org.egov.excelingestion.service.ValidationService;
import org.egov.excelingestion.util.BoundaryUtil;
import org.egov.excelingestion.util.EnrichmentUtil;
import org.egov.excelingestion.web.models.ProcessResource;
import org.egov.excelingestion.web.models.RequestInfo;
import org.egov.excelingestion.web.models.ValidationError;
import org.egov.excelingestion.web.models.ValidationColumnInfo;
import org.egov.tracer.model.CustomException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import org.egov.excelingestion.util.ExcelUtil;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.egov.excelingestion.util.ExcelUtil;

/**
 * Test class for BoundaryHierarchyTargetProcessor
 * Tests target schema validation, error column addition, and enrichment
 */
@ExtendWith(MockitoExtension.class)
public class BoundaryHierarchyTargetProcessorTest {

    @Mock
    private MDMSService mdmsService;
    
    @Mock
    private SchemaValidationService schemaValidationService;
    
    @Mock
    private ValidationService validationService;
    
    @Mock
    private EnrichmentUtil enrichmentUtil;
    
    @Mock
    private CustomExceptionHandler exceptionHandler;

    @Mock
    private ExcelUtil excelUtil;
    
    @Mock
    private CampaignService campaignService;
    
    @Mock
    private BoundaryUtil boundaryUtil;
    
    @InjectMocks
    private BoundaryHierarchyTargetProcessor processor;
    
    private ProcessResource resource;
    private RequestInfo requestInfo;
    private Map<String, String> localizationMap;
    private Workbook workbook;
    private Sheet sheet;

    @BeforeEach
    public void setUp() {
        // Setup ProcessResource with referenceId and hierarchyType for boundary validation
        resource = ProcessResource.builder()
                .id("test-id")
                .tenantId("test-tenant")
                .type("microplan-ingestion")
                .referenceId("campaign-123")
                .hierarchyType("ADMIN")
                .additionalDetails(new HashMap<>())
                .build();
        
        requestInfo = new RequestInfo();
        localizationMap = new HashMap<>();
        localizationMap.put("HCM_CONSOLE_BOUNDARY_HIERARCHY", "Target Sheet");
        
        // Setup workbook and sheet
        workbook = new XSSFWorkbook();
        sheet = workbook.createSheet("Target Sheet");
        
        // Create header row
        Row headerRow = sheet.createRow(0);
        headerRow.createCell(0).setCellValue("Boundary Code");
        headerRow.createCell(1).setCellValue("Target Value");
        
        // Create data row
        Row dataRow = sheet.createRow(2);
        dataRow.createCell(0).setCellValue("B001");
        dataRow.createCell(1).setCellValue("100");

        // Mock campaignService to return projectType
        lenient().when(campaignService.getProjectTypeFromCampaign(any(), any(), any())).thenReturn("LLIN");
        
        // Mock boundaryUtil to return valid boundary codes
        Set<String> validBoundaryCodes = new HashSet<>(Arrays.asList("B001", "B002", "B003"));
        lenient().when(boundaryUtil.getEnrichedBoundaryCodesFromCampaign(any(), any(), any(), any(), any()))
                .thenReturn(validBoundaryCodes);
        
        lenient().when(excelUtil.convertSheetToMapListCached(any(), any(), any())).thenAnswer(invocation -> {
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
    public void testProcessWorkbook_WithValidSchema_ShouldValidateAndEnrich() {
        // Given
        Map<String, Object> schema = createMockSchema();
        List<ValidationError> validationErrors = Arrays.asList(
            ValidationError.builder()
                .sheetName("Target Sheet")
                .rowNumber(3)
                .columnName("Target Value")
                .status("INVALID")
                .errorDetails("Field 'Target Value' must be at least 50")
                .build()
        );
        
        ValidationColumnInfo columnInfo = new ValidationColumnInfo(2, 3);
        
        when(mdmsService.searchMDMS(any(), eq("test-tenant"), eq("HCM-ADMIN-CONSOLE.schemas"), any(), eq(1), eq(0)))
                .thenReturn(Arrays.asList(createMdmsResponse(schema)));
        when(schemaValidationService.validateDataWithPreFetchedSchema(any(), any(), any(), any()))
                .thenReturn(validationErrors);
        when(validationService.addValidationColumns(any(), any())).thenReturn(columnInfo);
        
        // When
        Workbook result = processor.processWorkbook(workbook, "Target Sheet", resource, requestInfo, localizationMap);
        
        // Then
        assertNotNull(result);
        verify(validationService).addValidationColumns(sheet, localizationMap);
        verify(validationService).processValidationErrors(sheet, validationErrors, columnInfo, localizationMap);
        verify(enrichmentUtil).enrichErrorAndStatusInAdditionalDetails(resource, validationErrors);
    }

    @Test
    public void testProcessWorkbook_NoProjectType_ShouldSkipValidation() {
        // Given
        when(campaignService.getProjectTypeFromCampaign(any(), any(), any())).thenReturn(null);
        
        // When
        Workbook result = processor.processWorkbook(workbook, "Target Sheet", resource, requestInfo, localizationMap);
        
        // Then
        assertNotNull(result);
        verify(mdmsService, never()).searchMDMS(any(), any(), any(), any(), anyInt(), anyInt());
        verify(validationService, never()).addValidationColumns(any(), any());
        verifyNoInteractions(enrichmentUtil);
    }

    @Test
    public void testProcessWorkbook_SchemaNotFound_ShouldThrowException() {
        // Given
        when(mdmsService.searchMDMS(any(), eq("test-tenant"), eq("HCM-ADMIN-CONSOLE.schemas"), any(), anyInt(), anyInt()))
                .thenReturn(Collections.emptyList());
        doThrow(new CustomException("SCHEMA_NOT_FOUND", "Schema not found"))
                .when(exceptionHandler).throwCustomException(eq(ErrorConstants.SCHEMA_NOT_FOUND_IN_MDMS), anyString());
        
        // When & Then
        assertThrows(CustomException.class, () -> {
            processor.processWorkbook(workbook, "Target Sheet", resource, requestInfo, localizationMap);
        });
        
        verify(exceptionHandler).throwCustomException(
                eq(ErrorConstants.SCHEMA_NOT_FOUND_IN_MDMS),
                contains("target-LLIN")
        );
    }

    @Test
    public void testProcessWorkbook_NoValidationErrors_ShouldNotAddErrorColumns() {
        // Given
        Map<String, Object> schema = createMockSchema();
        List<ValidationError> validationErrors = Collections.emptyList();
        
        when(mdmsService.searchMDMS(any(), eq("test-tenant"), eq("HCM-ADMIN-CONSOLE.schemas"), any(), eq(1), eq(0)))
                .thenReturn(Arrays.asList(createMdmsResponse(schema)));
        when(schemaValidationService.validateDataWithPreFetchedSchema(any(), any(), any(), any()))
                .thenReturn(validationErrors);
        
        // When
        Workbook result = processor.processWorkbook(workbook, "Target Sheet", resource, requestInfo, localizationMap);
        
        // Then
        assertNotNull(result);
        verify(validationService, never()).addValidationColumns(any(), any());
        verify(validationService, never()).processValidationErrors(any(), any(), any(), any());
        verify(enrichmentUtil).enrichErrorAndStatusInAdditionalDetails(resource, validationErrors);
    }

    @Test
    public void testProcessWorkbook_SheetNotFound_ShouldReturnWorkbook() {
        // When
        Workbook result = processor.processWorkbook(workbook, "NonExistentSheet", resource, requestInfo, localizationMap);
        
        // Then
        assertNotNull(result);
        verify(validationService, never()).addValidationColumns(any(), any());
        verifyNoInteractions(enrichmentUtil);
    }

    @Test
    public void testExtractProjectType_ValidAdditionalDetails_ShouldReturnProjectType() {
        // Given
        Map<String, Object> schema = createMockSchema();
        when(mdmsService.searchMDMS(any(), eq("test-tenant"), eq("HCM-ADMIN-CONSOLE.schemas"), any(), eq(1), eq(0)))
                .thenReturn(Arrays.asList(createMdmsResponse(schema)));
        when(schemaValidationService.validateDataWithPreFetchedSchema(any(), any(), any(), any()))
                .thenReturn(Collections.emptyList());
        
        // When
        Workbook result = processor.processWorkbook(workbook, "Target Sheet", resource, requestInfo, localizationMap);
        
        // Then - verify that MDMS is called with correct schema name
        verify(mdmsService).searchMDMS(any(), any(), any(), argThat(filters -> {
            Map<String, Object> filtersMap = (Map<String, Object>) filters;
            return "target-LLIN".equals(filtersMap.get("title"));
        }), anyInt(), anyInt());
    }

    private Map<String, Object> createMockSchema() {
        Map<String, Object> properties = new HashMap<>();
        
        Map<String, Object> targetValueProperty = new HashMap<>();
        targetValueProperty.put("type", "number");
        targetValueProperty.put("minimum", 50);
        targetValueProperty.put("maximum", 10000);
        
        properties.put("Target Value", targetValueProperty);
        
        Map<String, Object> schema = new HashMap<>();
        schema.put("properties", properties);
        
        return schema;
    }
    
    private Map<String, Object> createMdmsResponse(Map<String, Object> schema) {
        Map<String, Object> response = new HashMap<>();
        response.put("data", schema);
        return response;
    }
}