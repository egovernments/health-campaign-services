package org.egov.excelingestion.service;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.egov.excelingestion.config.ExcelIngestionConfig;
import org.egov.excelingestion.service.MDMSConfigService;
import org.egov.excelingestion.exception.CustomExceptionHandler;
import org.egov.excelingestion.util.EnrichmentUtil;
import org.egov.excelingestion.util.ExcelUtil;
import org.egov.excelingestion.util.RequestInfoConverter;
import org.egov.excelingestion.web.models.*;
import org.egov.common.contract.request.RequestInfo;
import org.egov.excelingestion.web.models.filestore.FileStoreResponse;
import org.egov.excelingestion.web.models.filestore.FileInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class ExcelProcessingServiceTest {

    @Mock
    private ValidationService validationService;

    @Mock
    private SchemaValidationService schemaValidationService;

    @Mock
    private ConfigBasedProcessingService configBasedProcessingService;

    @Mock
    private FileStoreService fileStoreService;

    @Mock
    private LocalizationService localizationService;

    @Mock
    private RequestInfoConverter requestInfoConverter;

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private CustomExceptionHandler exceptionHandler;

    @Mock
    private ExcelIngestionConfig config;

    @Mock
    private EnrichmentUtil enrichmentUtil;

    @Mock
    private MDMSConfigService mdmsConfigService;

    @Mock
    private ExcelUtil excelUtil;

    @Mock
    private ImmutableJoinService immutableJoinService;

    private ExcelProcessingService excelProcessingService;

    private ProcessResourceRequest request;
    private ProcessResource resource;
    private RequestInfo requestInfo;

    @BeforeEach
    void setUp() {
        // Create service with mocks
        excelProcessingService = new ExcelProcessingService(
            validationService, schemaValidationService, configBasedProcessingService,
            fileStoreService, localizationService, requestInfoConverter,
            restTemplate, exceptionHandler, config, enrichmentUtil, mdmsConfigService, excelUtil,
            immutableJoinService
        );

        requestInfo = RequestInfo.builder().build();
        resource = ProcessResource.builder()
                .id("test-id")
                .tenantId("pb.amritsar")
                .type("USER")
                .fileStoreId("test-file-store-id")
                .referenceId("ref-123")
                .build();
        request = ProcessResourceRequest.builder()
                .requestInfo(requestInfo)
                .resourceDetails(resource)
                .build();

        // Use lenient mocking to avoid UnnecessaryStubbing errors
        lenient().when(requestInfoConverter.extractLocale(any())).thenReturn("en_IN");
        lenient().when(localizationService.getLocalizedMessages(any(), any(), any(), any()))
                .thenReturn(new HashMap<>());
        lenient().when(config.getFilestoreHost()).thenReturn("http://filestore");
        lenient().when(config.getFilestoreUrlEndpoint()).thenReturn("/filestore/v1/files/url");
        lenient().when(configBasedProcessingService.preValidateAndFetchSchemas(any(), any(), any(), any()))
                .thenReturn(new HashMap<>());
    }

    @Test
    void testProcessExcelFile_FileDownloadError() {
        // Given - FileStoreService throws exception during download
        when(fileStoreService.downloadExcelFromFileStore(anyString(), anyString()))
                .thenThrow(new RuntimeException("File download failed"));

        // When & Then
        assertThrows(RuntimeException.class, () -> {
            excelProcessingService.processExcelFile(request);
        });
        
        verify(fileStoreService).downloadExcelFromFileStore(eq("test-file-store-id"), eq("pb.amritsar"));
    }

    @Test
    void testProcessExcelFile_InvalidFileStoreResponse() {
        // Given - FileStoreService throws exception for invalid response
        when(fileStoreService.downloadExcelFromFileStore(anyString(), anyString()))
                .thenThrow(new RuntimeException("File URL retrieval error"));

        // When & Then
        assertThrows(RuntimeException.class, () -> {
            excelProcessingService.processExcelFile(request);
        });
        
        verify(fileStoreService).downloadExcelFromFileStore(eq("test-file-store-id"), eq("pb.amritsar"));
    }

    @Test
    void testProcessExcelFile_EmptyFileStoreResponse() {
        // Given - FileStoreService throws exception for empty response
        when(fileStoreService.downloadExcelFromFileStore(anyString(), anyString()))
                .thenThrow(new RuntimeException("File URL retrieval error"));

        // When & Then
        assertThrows(RuntimeException.class, () -> {
            excelProcessingService.processExcelFile(request);
        });
        
        verify(fileStoreService).downloadExcelFromFileStore(eq("test-file-store-id"), eq("pb.amritsar"));
    }

    @Test
    void testProcessExcelFile_LocalizationServiceCalled() {
        // Given
        // Setup FileStoreService to fail
        when(fileStoreService.downloadExcelFromFileStore(anyString(), anyString()))
                .thenThrow(new RuntimeException("File download failed"));
        
        // When
        assertThrows(RuntimeException.class, () -> {
            excelProcessingService.processExcelFile(request);
        });

        // Then - Verify localization service was called for schema localization
        verify(localizationService).getLocalizedMessages(
                eq("pb.amritsar"), eq("hcm-admin-schemas"), eq("en_IN"), eq(requestInfo));
    }

    @Test
    void testProcessExcelFile_BoundaryHierarchyLocalization() {
        // Given - Resource with hierarchy type
        resource = ProcessResource.builder()
                .id("test-id")
                .tenantId("pb.amritsar")
                .type("USER")
                .hierarchyType("ADMIN")
                .fileStoreId("test-file-store-id")
                .referenceId("ref-123")
                .build();
        request = ProcessResourceRequest.builder()
                .requestInfo(requestInfo)
                .resourceDetails(resource)
                .build();
        
        // Setup FileStoreService to fail
        when(fileStoreService.downloadExcelFromFileStore(anyString(), anyString()))
                .thenThrow(new RuntimeException("File download failed"));
        
        // When
        assertThrows(RuntimeException.class, () -> {
            excelProcessingService.processExcelFile(request);
        });

        // Then - Verify both boundary and schema localization were called
        verify(localizationService).getLocalizedMessages(
                eq("pb.amritsar"), eq("hcm-boundary-admin"), eq("en_IN"), eq(requestInfo));
        verify(localizationService).getLocalizedMessages(
                eq("pb.amritsar"), eq("hcm-admin-schemas"), eq("en_IN"), eq(requestInfo));
    }

    @Test
    void testProcessExcelFile_EnrichmentServiceCalled() {
        // Given
        // Setup FileStoreService to fail
        when(fileStoreService.downloadExcelFromFileStore(anyString(), anyString()))
                .thenThrow(new RuntimeException("File download failed"));
        
        // When
        assertThrows(RuntimeException.class, () -> {
            excelProcessingService.processExcelFile(request);
        });

        // Then - Verify processing completed (enrichProcessResource no longer called)
    }

    @Test
    void testProcessExcelFile_ConfigBasedProcessingCalled() {
        // Given - Setup FileStoreService to succeed but config processing to fail
        when(fileStoreService.downloadExcelFromFileStore(anyString(), anyString()))
                .thenReturn(createTestWorkbook());
        when(configBasedProcessingService.preValidateAndFetchSchemas(any(), any(), any(), any()))
                .thenThrow(new RuntimeException("Config processing failed"));
        
        // When
        assertThrows(RuntimeException.class, () -> {
            excelProcessingService.processExcelFile(request);
        });

        // Then - Verify config-based processing was called after file download
        verify(configBasedProcessingService).preValidateAndFetchSchemas(
                any(), eq(resource), eq(requestInfo), any());
    }

    @Test
    void testProcessExcelFile_FileStoreUrlConstruction() {
        // Given - FileStoreService should be called for download
        when(fileStoreService.downloadExcelFromFileStore(anyString(), anyString()))
                .thenThrow(new RuntimeException("File download failed"));
        
        // When
        assertThrows(RuntimeException.class, () -> {
            excelProcessingService.processExcelFile(request);
        });

        // Then - Verify FileStoreService was called with correct parameters
        verify(fileStoreService).downloadExcelFromFileStore(eq("test-file-store-id"), eq("pb.amritsar"));
    }

    @Test
    void testProcessExcelFile_RequestInfoConverterCalled() {
        // Given
        // Setup FileStoreService to fail
        when(fileStoreService.downloadExcelFromFileStore(anyString(), anyString()))
                .thenThrow(new RuntimeException("File download failed"));
        
        // When
        assertThrows(RuntimeException.class, () -> {
            excelProcessingService.processExcelFile(request);
        });

        // Then - Verify locale extraction was called
        verify(requestInfoConverter).extractLocale(requestInfo);
    }

    @Test
    void testProcessExcelFile_ValidationServiceIntegration() {
        // Test that validation service methods would be called in success case
        // This test verifies the service setup and integration points
        
        // Given - Setup FileStoreService to succeed but config processing to fail
        when(fileStoreService.downloadExcelFromFileStore(anyString(), anyString()))
                .thenReturn(createTestWorkbook());
        when(configBasedProcessingService.preValidateAndFetchSchemas(any(), any(), any(), any()))
                .thenThrow(new RuntimeException("Config processing failed"));
        
        // When
        assertThrows(RuntimeException.class, () -> {
            excelProcessingService.processExcelFile(request);
        });

        // Then - Verify that we got to the point where services are initialized
        // These should be called after successful file download
        verify(localizationService, atLeastOnce()).getLocalizedMessages(any(), any(), any(), any());
        verify(configBasedProcessingService).preValidateAndFetchSchemas(any(), any(), any(), any());
    }


    @Test
    void testProcessExcelFile_RowLimitExceeded_FailsFast() {
        // Given - workbook with 5 data rows but a max limit of 2
        when(fileStoreService.downloadExcelFromFileStore(anyString(), anyString()))
                .thenReturn(createWorkbookWithDataRows(5));
        when(config.getMaxProcessRowLimit()).thenReturn(2);
        // Mimic the real handler: throwing stops processing
        doThrow(new org.egov.tracer.model.CustomException(
                org.egov.excelingestion.config.ErrorConstants.EXCEL_ROW_LIMIT_EXCEEDED, "too many rows"))
                .when(exceptionHandler)
                .throwCustomException(eq(org.egov.excelingestion.config.ErrorConstants.EXCEL_ROW_LIMIT_EXCEEDED), anyString());

        // When & Then - guard trips before any validation/schema work
        assertThrows(org.egov.tracer.model.CustomException.class, () -> {
            excelProcessingService.processExcelFile(request);
        });

        verify(exceptionHandler).throwCustomException(
                eq(org.egov.excelingestion.config.ErrorConstants.EXCEL_ROW_LIMIT_EXCEEDED), anyString());
        // Guard runs before schema pre-validation
        verify(configBasedProcessingService, never()).preValidateAndFetchSchemas(any(), any(), any(), any());
    }

    @Test
    void testProcessExcelFile_UnderRowLimit_PassesGuard() {
        // Given - workbook with 5 data rows and a generous limit
        when(fileStoreService.downloadExcelFromFileStore(anyString(), anyString()))
                .thenReturn(createWorkbookWithDataRows(5));
        when(config.getMaxProcessRowLimit()).thenReturn(100000);
        // Stop right after the guard so we don't run the full pipeline
        when(configBasedProcessingService.preValidateAndFetchSchemas(any(), any(), any(), any()))
                .thenThrow(new RuntimeException("stop after guard"));

        // When
        assertThrows(RuntimeException.class, () -> {
            excelProcessingService.processExcelFile(request);
        });

        // Then - guard let it through to schema pre-validation, and never raised the limit error
        verify(configBasedProcessingService).preValidateAndFetchSchemas(any(), any(), any(), any());
        verify(exceptionHandler, never()).throwCustomException(
                eq(org.egov.excelingestion.config.ErrorConstants.EXCEL_ROW_LIMIT_EXCEEDED), anyString());
    }

    @Test
    @SuppressWarnings("unchecked")
    void noSchemaSheet_isStillPassedToImmutableJoin() {

        when(fileStoreService.downloadExcelFromFileStore(anyString(), anyString()))
                .thenReturn(createTestWorkbook());           // sheet "TestSheet"
        when(config.getMaxProcessRowLimit()).thenReturn(100000);
        // getSchemaForSheet("TestSheet") -> mdmsConfigService not stubbed -> null -> sheet has NO schema.
        // Capture the sheet map handed to the join, then stop processing right after.
        ArgumentCaptor<Map<String, Map<String, Object>>> captor = ArgumentCaptor.forClass(Map.class);
        when(immutableJoinService.applyImmutableBaseline(any(), any(), captor.capture()))
                .thenThrow(new RuntimeException("stop after join"));

        assertThrows(RuntimeException.class, () -> excelProcessingService.processExcelFile(request));

        assertTrue(captor.getValue().containsKey("TestSheet"),
                "a visible no-schema sheet must still be included in the immutable-join sheet list");
    }

    private Workbook createTestWorkbook() {
        return createWorkbookWithDataRows(1);
    }

    /**
     * Builds a workbook with the two header rows (row 0 technical, row 1 localized) plus
     * {@code dataRows} data rows starting at row index 2 — matching the service's layout.
     */
    private Workbook createWorkbookWithDataRows(int dataRows) {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("TestSheet");

        Row headerRow = sheet.createRow(0);
        headerRow.createCell(0).setCellValue("Field1");
        headerRow.createCell(1).setCellValue("Field2");

        Row secondHeaderRow = sheet.createRow(1);
        secondHeaderRow.createCell(0).setCellValue("Header1");
        secondHeaderRow.createCell(1).setCellValue("Header2");

        for (int i = 0; i < dataRows; i++) {
            Row dataRow = sheet.createRow(2 + i);
            dataRow.createCell(0).setCellValue("Value1_" + i);
            dataRow.createCell(1).setCellValue("Value2_" + i);
        }

        return workbook;
    }
}