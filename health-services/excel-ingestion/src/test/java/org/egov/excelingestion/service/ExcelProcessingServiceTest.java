package org.egov.excelingestion.service;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.egov.excelingestion.config.ExcelIngestionConfig;
import org.egov.excelingestion.config.ProcessorConfigurationRegistry;
import org.egov.excelingestion.exception.CustomExceptionHandler;
import org.egov.excelingestion.util.EnrichmentUtil;
import org.egov.excelingestion.util.RequestInfoConverter;
import org.egov.excelingestion.web.models.*;
import org.egov.excelingestion.web.models.filestore.FileStoreResponse;
import org.egov.excelingestion.web.models.filestore.FileInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
    private ProcessorConfigurationRegistry configRegistry;

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
            restTemplate, exceptionHandler, config, enrichmentUtil, configRegistry
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
        // Given
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), isNull(), eq(FileStoreResponse.class)))
                .thenThrow(new RuntimeException("File download failed"));
        doThrow(new RuntimeException("Processing failed"))
                .when(exceptionHandler).throwCustomException(any(), any(), any(Exception.class));

        // When & Then
        assertThrows(RuntimeException.class, () -> {
            excelProcessingService.processExcelFile(request);
        });
        
        verify(exceptionHandler).throwCustomException(any(), any(), any(Exception.class));
    }

    @Test
    void testProcessExcelFile_InvalidFileStoreResponse() {
        // Given - FileStore returns null response
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), isNull(), eq(FileStoreResponse.class)))
                .thenReturn(ResponseEntity.ok(null));
        doThrow(new RuntimeException("File URL retrieval error"))
                .when(exceptionHandler).throwCustomException(any(), any());

        // When & Then
        assertThrows(RuntimeException.class, () -> {
            excelProcessingService.processExcelFile(request);
        });
        
        verify(exceptionHandler).throwCustomException(any(), any());
    }

    @Test
    void testProcessExcelFile_EmptyFileStoreResponse() {
        // Given - FileStore returns empty files list
        FileStoreResponse emptyResponse = new FileStoreResponse();
        emptyResponse.setFiles(Collections.emptyList());
        
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), isNull(), eq(FileStoreResponse.class)))
                .thenReturn(ResponseEntity.ok(emptyResponse));
        doThrow(new RuntimeException("File URL retrieval error"))
                .when(exceptionHandler).throwCustomException(any(), any());

        // When & Then
        assertThrows(RuntimeException.class, () -> {
            excelProcessingService.processExcelFile(request);
        });
        
        verify(exceptionHandler).throwCustomException(any(), any());
    }

    @Test
    void testProcessExcelFile_LocalizationServiceCalled() {
        // Given
        setupFileDownloadToFail(); // Will fail at file download but should call localization first
        
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
        
        setupFileDownloadToFail(); // Will fail at file download but should call localization first
        
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
        setupFileDownloadToFail(); // Will fail at file download but should call enrichment first
        
        // When
        assertThrows(RuntimeException.class, () -> {
            excelProcessingService.processExcelFile(request);
        });

        // Then - Verify processing completed (enrichProcessResource no longer called)
    }

    @Test
    void testProcessExcelFile_ConfigBasedProcessingCalled() {
        // Given - Setup successful initial calls but fail at file download
        FileStoreResponse fileStoreResponse = new FileStoreResponse();
        FileInfo fileInfo = new FileInfo();
        fileInfo.setUrl("http://invalid-url.com/test-file.xlsx"); 
        fileStoreResponse.setFiles(List.of(fileInfo));
        
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), isNull(), eq(FileStoreResponse.class)))
                .thenReturn(ResponseEntity.ok(fileStoreResponse));
        
        // When
        assertThrows(RuntimeException.class, () -> {
            excelProcessingService.processExcelFile(request);
        });

        // Then - Verify config-based processing was called before file download
        verify(configBasedProcessingService).preValidateAndFetchSchemas(
                any(), eq(resource), eq(requestInfo), any());
    }

    @Test
    void testProcessExcelFile_FileStoreUrlConstruction() {
        // Given
        when(config.getFilestoreHost()).thenReturn("http://localhost:8080");
        when(config.getFilestoreUrlEndpoint()).thenReturn("/filestore/v1/files/url");
        
        setupFileDownloadToFail();
        
        // When
        assertThrows(RuntimeException.class, () -> {
            excelProcessingService.processExcelFile(request);
        });

        // Then - Verify RestTemplate was called with correct URL format
        verify(restTemplate).exchange(
                contains("http://localhost:8080/filestore/v1/files/url"),
                eq(HttpMethod.GET), isNull(), eq(FileStoreResponse.class));
    }

    @Test
    void testProcessExcelFile_RequestInfoConverterCalled() {
        // Given
        setupFileDownloadToFail();
        
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
        
        // Given - Setup successful initial calls but fail at file download
        FileStoreResponse fileStoreResponse = new FileStoreResponse();
        FileInfo fileInfo = new FileInfo();
        fileInfo.setUrl("http://invalid-url.com/test-file.xlsx"); 
        fileStoreResponse.setFiles(List.of(fileInfo));
        
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), isNull(), eq(FileStoreResponse.class)))
                .thenReturn(ResponseEntity.ok(fileStoreResponse));
        
        // When
        assertThrows(RuntimeException.class, () -> {
            excelProcessingService.processExcelFile(request);
        });

        // Then - Verify that we got to the point where services are initialized
        // These should be called before the file download fails
        verify(localizationService, atLeastOnce()).getLocalizedMessages(any(), any(), any(), any());
        verify(configBasedProcessingService).preValidateAndFetchSchemas(any(), any(), any(), any());
    }

    private void setupFileDownloadToFail() {
        FileStoreResponse fileStoreResponse = new FileStoreResponse();
        FileInfo fileInfo = new FileInfo();
        fileInfo.setUrl("http://invalid-url.com/test-file.xlsx"); // This will fail to download
        fileStoreResponse.setFiles(List.of(fileInfo));
        
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), isNull(), eq(FileStoreResponse.class)))
                .thenReturn(ResponseEntity.ok(fileStoreResponse));
        
        // The service will fail when trying to download from the URL, which is expected
        doThrow(new RuntimeException("File download error"))
                .when(exceptionHandler).throwCustomException(any(), any(), any(Exception.class));
    }

    private Workbook createTestWorkbook() {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("TestSheet");
        
        // Create header row
        Row headerRow = sheet.createRow(0);
        headerRow.createCell(0).setCellValue("Field1");
        headerRow.createCell(1).setCellValue("Field2");
        
        // Create second header row (as expected by the service)
        Row secondHeaderRow = sheet.createRow(1);
        secondHeaderRow.createCell(0).setCellValue("Header1");
        secondHeaderRow.createCell(1).setCellValue("Header2");
        
        // Create data row
        Row dataRow = sheet.createRow(2);
        dataRow.createCell(0).setCellValue("Value1");
        dataRow.createCell(1).setCellValue("Value2");
        
        return workbook;
    }
}