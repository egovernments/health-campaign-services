package org.egov.excelingestion.service;

import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.egov.excelingestion.config.ErrorConstants;
import org.egov.excelingestion.config.ProcessorConfigurationRegistry;
import org.egov.excelingestion.exception.CustomExceptionHandler;
import org.egov.excelingestion.processor.BoundaryHierarchyTargetProcessor;
import org.egov.excelingestion.web.models.ProcessResource;
import org.egov.excelingestion.web.models.RequestInfo;
import org.egov.tracer.model.CustomException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Test class for ConfigBasedProcessingService workbook processor functionality
 * Tests workbook processor execution, error handling, and custom exception propagation
 */
@ExtendWith(MockitoExtension.class)
public class ConfigBasedProcessingServiceWorkbookTest {

    @Mock
    private ProcessorConfigurationRegistry configRegistry;
    
    @Mock
    private CustomExceptionHandler exceptionHandler;
    
    @Mock
    private MDMSService mdmsService;
    
    @Mock
    private ApplicationContext applicationContext;
    
    @Mock
    private BoundaryHierarchyTargetProcessor mockProcessor;
    
    @InjectMocks
    private ConfigBasedProcessingService service;
    
    private ProcessResource resource;
    private RequestInfo requestInfo;
    private Map<String, String> localizationMap;
    private Workbook workbook;
    private List<ProcessorConfigurationRegistry.ProcessorSheetConfig> config;

    @BeforeEach
    public void setUp() {
        resource = ProcessResource.builder()
                .id("test-id")
                .tenantId("test-tenant")
                .type("microplan-ingestion")
                .build();
        
        requestInfo = new RequestInfo();
        localizationMap = new HashMap<>();
        localizationMap.put("HCM_CONSOLE_BOUNDARY_HIERARCHY", "Target Sheet");
        
        workbook = new XSSFWorkbook();
        workbook.createSheet("Target Sheet");
        
        // Create mock configuration
        config = Arrays.asList(
            new ProcessorConfigurationRegistry.ProcessorSheetConfig(
                "HCM_CONSOLE_BOUNDARY_HIERARCHY",
                null, // No schema validation for this test
                "org.egov.excelingestion.processor.BoundaryHierarchyTargetProcessor"
            )
        );
    }

    @Test
    public void testProcessWorkbookWithProcessor_ValidProcessor_ShouldExecuteAndReturnProcessedWorkbook() throws Exception {
        // Given
        Workbook processedWorkbook = new XSSFWorkbook();
        processedWorkbook.createSheet("Processed Target Sheet");
        
        when(configRegistry.isProcessorTypeSupported("microplan-ingestion")).thenReturn(true);
        when(configRegistry.getConfigByType("microplan-ingestion")).thenReturn(config);
        when(applicationContext.getBean(org.egov.excelingestion.processor.BoundaryHierarchyTargetProcessor.class))
                .thenReturn(mockProcessor);
        when(mockProcessor.processWorkbook(workbook, "Target Sheet", resource, requestInfo, localizationMap))
                .thenReturn(processedWorkbook);

        // When
        Workbook result = service.processWorkbookWithProcessor("Target Sheet", workbook, resource, requestInfo, localizationMap);

        // Then
        assertNotNull(result);
        assertEquals(processedWorkbook, result);
        verify(mockProcessor).processWorkbook(workbook, "Target Sheet", resource, requestInfo, localizationMap);
    }

    @Test
    public void testProcessWorkbookWithProcessor_NoProcessorConfigured_ShouldReturnNull() {
        // Given
        List<ProcessorConfigurationRegistry.ProcessorSheetConfig> configWithoutProcessor = Arrays.asList(
            new ProcessorConfigurationRegistry.ProcessorSheetConfig(
                "HCM_CONSOLE_BOUNDARY_HIERARCHY",
                "some-schema",
                null // No processor class
            )
        );
        
        when(configRegistry.isProcessorTypeSupported("microplan-ingestion")).thenReturn(true);
        when(configRegistry.getConfigByType("microplan-ingestion")).thenReturn(configWithoutProcessor);

        // When
        Workbook result = service.processWorkbookWithProcessor("Target Sheet", workbook, resource, requestInfo, localizationMap);

        // Then
        assertNull(result);
        verifyNoInteractions(applicationContext);
        verifyNoInteractions(mockProcessor);
    }

    @Test
    public void testProcessWorkbookWithProcessor_SheetNotConfigured_ShouldReturnNull() {
        // Given
        when(configRegistry.isProcessorTypeSupported("microplan-ingestion")).thenReturn(true);
        when(configRegistry.getConfigByType("microplan-ingestion")).thenReturn(config);

        // When
        Workbook result = service.processWorkbookWithProcessor("UnknownSheet", workbook, resource, requestInfo, localizationMap);

        // Then
        assertNull(result);
        verifyNoInteractions(applicationContext);
        verifyNoInteractions(mockProcessor);
    }

    @Test
    public void testProcessWorkbookWithProcessor_NoConfigForType_ShouldThrowException() {
        // Given
        when(configRegistry.isProcessorTypeSupported("unknown-type")).thenReturn(false);
        when(configRegistry.getSupportedProcessorTypes()).thenReturn(new String[]{"microplan-ingestion"});
        doThrow(new CustomException("PROCESSOR_TYPE_NOT_SUPPORTED", "Processor type not supported"))
                .when(exceptionHandler).throwCustomException(anyString(), anyString(), any(Exception.class));

        // When & Then
        assertThrows(CustomException.class, () -> {
            service.processWorkbookWithProcessor("Target Sheet", workbook, 
                    ProcessResource.builder().type("unknown-type").build(), requestInfo, localizationMap);
        });
    }

    @Test
    public void testProcessWorkbookWithProcessor_ProcessorBeanNotFound_ShouldThrowException() throws Exception {
        // Given - Bean not found in Spring context
        when(configRegistry.isProcessorTypeSupported("microplan-ingestion")).thenReturn(true);
        when(configRegistry.getConfigByType("microplan-ingestion")).thenReturn(config);
        when(applicationContext.getBean(any(Class.class)))
                .thenThrow(new RuntimeException("Bean not found in application context"));
        doThrow(new CustomException("PROCESSOR_EXECUTION_ERROR", "Error executing processor"))
                .when(exceptionHandler).throwCustomException(anyString(), anyString());

        // When & Then
        assertThrows(CustomException.class, () -> {
            service.processWorkbookWithProcessor("Target Sheet", workbook, resource, requestInfo, localizationMap);
        });
        
        verify(exceptionHandler).throwCustomException(
                eq(ErrorConstants.PROCESSOR_EXECUTION_ERROR),
                contains("Bean not found in application context")
        );
    }

    @Test
    public void testProcessWorkbookWithProcessor_ProcessorThrowsCustomException_ShouldPropagateException() throws Exception {
        // Given
        CustomException customException = new CustomException("SCHEMA_NOT_FOUND", "Schema not found");
        
        when(configRegistry.isProcessorTypeSupported("microplan-ingestion")).thenReturn(true);
        when(configRegistry.getConfigByType("microplan-ingestion")).thenReturn(config);
        when(applicationContext.getBean(org.egov.excelingestion.processor.BoundaryHierarchyTargetProcessor.class))
                .thenReturn(mockProcessor);
        when(mockProcessor.processWorkbook(workbook, "Target Sheet", resource, requestInfo, localizationMap))
                .thenThrow(customException);

        // When & Then
        CustomException thrownException = assertThrows(CustomException.class, () -> {
            service.processWorkbookWithProcessor("Target Sheet", workbook, resource, requestInfo, localizationMap);
        });
        
        assertEquals(customException, thrownException);
        verify(exceptionHandler, never()).throwCustomException(eq(ErrorConstants.PROCESSOR_EXECUTION_ERROR), anyString());
    }

    @Test
    public void testProcessWorkbookWithProcessor_ProcessorThrowsGenericException_ShouldWrapException() throws Exception {
        // Given
        RuntimeException genericException = new RuntimeException("Some processing error");
        
        when(configRegistry.isProcessorTypeSupported("microplan-ingestion")).thenReturn(true);
        when(configRegistry.getConfigByType("microplan-ingestion")).thenReturn(config);
        when(applicationContext.getBean(org.egov.excelingestion.processor.BoundaryHierarchyTargetProcessor.class))
                .thenReturn(mockProcessor);
        when(mockProcessor.processWorkbook(workbook, "Target Sheet", resource, requestInfo, localizationMap))
                .thenThrow(genericException);
        doThrow(new CustomException("PROCESSOR_EXECUTION_ERROR", "Error executing processor"))
                .when(exceptionHandler).throwCustomException(eq(ErrorConstants.PROCESSOR_EXECUTION_ERROR), anyString());

        // When & Then
        assertThrows(CustomException.class, () -> {
            service.processWorkbookWithProcessor("Target Sheet", workbook, resource, requestInfo, localizationMap);
        });
        
        verify(exceptionHandler).throwCustomException(
                eq(ErrorConstants.PROCESSOR_EXECUTION_ERROR),
                contains("Some processing error")
        );
    }

    @Test
    public void testProcessWorkbookWithProcessor_LocalizedSheetName_ShouldMatchCorrectly() throws Exception {
        // Given
        Workbook processedWorkbook = new XSSFWorkbook();
        
        when(configRegistry.isProcessorTypeSupported("microplan-ingestion")).thenReturn(true);
        when(configRegistry.getConfigByType("microplan-ingestion")).thenReturn(config);
        when(applicationContext.getBean(org.egov.excelingestion.processor.BoundaryHierarchyTargetProcessor.class))
                .thenReturn(mockProcessor);
        when(mockProcessor.processWorkbook(workbook, "Target Sheet", resource, requestInfo, localizationMap))
                .thenReturn(processedWorkbook);

        // When
        Workbook result = service.processWorkbookWithProcessor("Target Sheet", workbook, resource, requestInfo, localizationMap);

        // Then
        assertNotNull(result);
        verify(mockProcessor).processWorkbook(workbook, "Target Sheet", resource, requestInfo, localizationMap);
    }

    @Test
    public void testProcessWorkbookWithProcessor_MultipleProcessorsConfigured_ShouldUseCorrectOne() throws Exception {
        // Given
        List<ProcessorConfigurationRegistry.ProcessorSheetConfig> multiConfig = Arrays.asList(
            new ProcessorConfigurationRegistry.ProcessorSheetConfig("SHEET1", "schema1", "Processor1"),
            new ProcessorConfigurationRegistry.ProcessorSheetConfig("HCM_CONSOLE_BOUNDARY_HIERARCHY", null, 
                    "org.egov.excelingestion.processor.BoundaryHierarchyTargetProcessor"),
            new ProcessorConfigurationRegistry.ProcessorSheetConfig("SHEET3", "schema3", "Processor3")
        );
        
        Workbook processedWorkbook = new XSSFWorkbook();
        
        when(configRegistry.isProcessorTypeSupported("microplan-ingestion")).thenReturn(true);
        when(configRegistry.getConfigByType("microplan-ingestion")).thenReturn(multiConfig);
        when(applicationContext.getBean(org.egov.excelingestion.processor.BoundaryHierarchyTargetProcessor.class))
                .thenReturn(mockProcessor);
        when(mockProcessor.processWorkbook(workbook, "Target Sheet", resource, requestInfo, localizationMap))
                .thenReturn(processedWorkbook);

        // When
        Workbook result = service.processWorkbookWithProcessor("Target Sheet", workbook, resource, requestInfo, localizationMap);

        // Then
        assertNotNull(result);
        assertEquals(processedWorkbook, result);
        verify(mockProcessor).processWorkbook(workbook, "Target Sheet", resource, requestInfo, localizationMap);
    }
}