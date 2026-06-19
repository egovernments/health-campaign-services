package org.egov.excelingestion.service;

import org.egov.excelingestion.web.models.RequestInfo;
import org.egov.excelingestion.web.models.UserInfo;
import org.egov.common.producer.Producer;
import org.egov.excelingestion.config.ErrorConstants;
import org.egov.excelingestion.config.KafkaTopicConfig;
import org.egov.excelingestion.web.models.ProcessorSheetConfig;
import org.egov.excelingestion.exception.CustomExceptionHandler;
import org.egov.excelingestion.repository.ProcessingRepository;
import org.egov.excelingestion.web.models.ProcessResource;
import org.egov.excelingestion.web.models.ProcessResourceRequest;
import org.egov.tracer.model.CustomException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test to verify processor class not found throws correct error code
 */
@ExtendWith(MockitoExtension.class)
class ProcessorClassNotFoundTest {

    @Mock
    private ProcessingRepository processingRepository;
    
    @Mock
    private Producer producer;
    
    @Mock
    private AsyncProcessingService asyncProcessingService;
    
    @Mock
    private ConfigBasedProcessingService configBasedProcessingService;
    
    @Mock
    private CustomExceptionHandler exceptionHandler;

    @Mock
    private KafkaTopicConfig kafkaTopicConfig;

    private ProcessingService processingService;

    @BeforeEach
    void setUp() {
        processingService = new ProcessingService(
            processingRepository, 
            producer, 
            asyncProcessingService, 
            configBasedProcessingService,
            exceptionHandler,
            kafkaTopicConfig
        );
    }

    @Test
    void testProcessorClassNotFound_ThrowsCorrectErrorCode() {
        // Arrange
        ProcessResourceRequest request = createProcessResourceRequest();
        
        // Mock config with non-existent processor class
        ProcessorSheetConfig sheetConfig = 
            new ProcessorSheetConfig(
                "HCM_CONSOLE_BOUNDARY_HIERARCHY", 
                null, 
                "NonExistentProcessor"
            );
        
        when(configBasedProcessingService.getConfigByType(eq("microplan-ingestion"), any(RequestInfo.class), eq("dev")))
            .thenReturn(Arrays.asList(sheetConfig));
        
        // Mock the exception handler to throw the expected CustomException
        doThrow(new CustomException(
                ErrorConstants.PROCESSOR_CLASS_NOT_FOUND,
                ErrorConstants.PROCESSOR_CLASS_NOT_FOUND_MESSAGE.replace("{0}", "NonExistentProcessor")
        )).when(exceptionHandler).throwCustomException(
            eq(ErrorConstants.PROCESSOR_CLASS_NOT_FOUND),
            anyString(),
            any(ClassNotFoundException.class)
        );

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class, () -> {
            processingService.initiateProcessing(request);
        });

        // Verify correct error code and message
        assertEquals(ErrorConstants.PROCESSOR_CLASS_NOT_FOUND, exception.getCode());
        assertTrue(exception.getMessage().contains("NonExistentProcessor"));
        
        // Verify validation was called
        verify(configBasedProcessingService).getConfigByType(eq("microplan-ingestion"), any(RequestInfo.class), eq("dev"));
        verify(exceptionHandler).throwCustomException(
            eq(ErrorConstants.PROCESSOR_CLASS_NOT_FOUND),
            anyString(),
            any(ClassNotFoundException.class)
        );
    }

    @Test
    void testProcessorClassFound_DoesNotThrowException() {
        // Arrange
        ProcessResourceRequest request = createProcessResourceRequest();
        
        // Mock config with existing processor class
        ProcessorSheetConfig sheetConfig = 
            new ProcessorSheetConfig(
                "HCM_CONSOLE_BOUNDARY_HIERARCHY", 
                null, 
                "BoundaryHierarchyTargetProcessor"  // This exists
            );
        
        when(configBasedProcessingService.getConfigByType(eq("microplan-ingestion"), any(RequestInfo.class), eq("dev")))
            .thenReturn(Arrays.asList(sheetConfig));

        // Act & Assert
        assertDoesNotThrow(() -> {
            processingService.initiateProcessing(request);
        });

        // Verify validation was called but no exception thrown
        verify(configBasedProcessingService).getConfigByType(eq("microplan-ingestion"), any(RequestInfo.class), eq("dev"));
        verify(asyncProcessingService).processExcelAsync(any(), any());
        verifyNoInteractions(exceptionHandler);
    }

    private ProcessResourceRequest createProcessResourceRequest() {
        ProcessResource resource = ProcessResource.builder()
            .type("microplan-ingestion")
            .tenantId("dev")
            .fileStoreId("test-filestore-id")
            .build();

        RequestInfo requestInfo = RequestInfo.builder()
            .apiId("test-api")
            .ver("1.0")
            .ts(System.currentTimeMillis())
            .userInfo(UserInfo.builder().uuid("test-user").build())
            .build();

        return ProcessResourceRequest.builder()
            .resourceDetails(resource)
            .requestInfo(requestInfo)
            .build();
    }
}