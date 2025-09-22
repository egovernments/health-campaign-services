package org.egov.excelingestion.service;

import org.egov.common.producer.Producer;
import org.egov.excelingestion.config.KafkaTopicConfig;
import org.egov.excelingestion.constants.GenerationConstants;
import org.egov.excelingestion.util.EnrichmentUtil;
import org.egov.excelingestion.web.models.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AsyncGenerationServiceTest {

    @Mock
    private ExcelWorkflowService excelWorkflowService;

    @Mock
    private Producer producer;

    @Mock
    private KafkaTopicConfig kafkaTopicConfig;

    @Mock
    private EnrichmentUtil enrichmentUtil;

    private AsyncGenerationService asyncGenerationService;

    @BeforeEach
    void setUp() {
        // Setup mock KafkaTopicConfig
        when(kafkaTopicConfig.getGenerationUpdateTopic()).thenReturn("test-update-topic");
        asyncGenerationService = new AsyncGenerationService(excelWorkflowService, producer, kafkaTopicConfig, enrichmentUtil);
    }

    @Test
    void shouldProcessGenerationAsynchronously() throws Exception {
        // Given
        GenerateResource generateResource = createGenerateResource("test-id", "dev");
        RequestInfo requestInfo = createRequestInfo();
        
        GenerateResource completedResource = createGenerateResource("test-id", "dev");
        completedResource.setFileStoreId("file-store-id-123");
        when(excelWorkflowService.generateAndUploadExcel(any())).thenReturn(completedResource);

        // When
        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
            try {
                asyncGenerationService.processGenerationAsync(generateResource, requestInfo);
            } catch (Exception e) {
                fail("Async processing should not throw exception: " + e.getMessage());
            }
        });

        // Wait for async completion
        future.get(5, TimeUnit.SECONDS);

        // Then
        verify(excelWorkflowService, timeout(5000)).generateAndUploadExcel(any(GenerateResourceRequest.class));
        verify(producer, timeout(5000)).push(eq("dev"), eq("test-update-topic"), argThat(resource -> {
            GenerateResource gr = (GenerateResource) resource;
            return GenerationConstants.STATUS_COMPLETED.equals(gr.getStatus()) &&
                   "file-store-id-123".equals(gr.getFileStoreId());
        }));
    }

    @Test
    void shouldUpdateStatusToFailedOnException() throws Exception {
        // Given
        GenerateResource generateResource = createGenerateResource("test-id", "dev");
        RequestInfo requestInfo = createRequestInfo();
        
        when(excelWorkflowService.generateAndUploadExcel(any()))
            .thenThrow(new RuntimeException("Generation failed"));

        // When
        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
            try {
                asyncGenerationService.processGenerationAsync(generateResource, requestInfo);
            } catch (Exception e) {
                // Expected to catch and handle the exception
            }
        });

        future.get(5, TimeUnit.SECONDS);

        // Then
        verify(enrichmentUtil, timeout(5000)).enrichErrorDetailsInAdditionalDetails(any(GenerateResource.class), any(Exception.class));
        verify(producer, timeout(5000)).push(eq("dev"), eq("test-update-topic"), argThat(resource -> {
            GenerateResource gr = (GenerateResource) resource;
            return GenerationConstants.STATUS_FAILED.equals(gr.getStatus());
        }));
    }

    @Test
    void shouldHandleConcurrentGenerationRequests() throws Exception {
        // Given
        GenerateResource resource1 = createGenerateResource("id-1", "dev");
        GenerateResource resource2 = createGenerateResource("id-2", "dev");
        RequestInfo requestInfo = createRequestInfo();
        
        GenerateResource completed1 = createGenerateResource("id-1", "dev");
        completed1.setFileStoreId("file-1");
        GenerateResource completed2 = createGenerateResource("id-2", "dev");
        completed2.setFileStoreId("file-2");
        
        when(excelWorkflowService.generateAndUploadExcel(any()))
            .thenReturn(completed1, completed2);

        // When - Process multiple generations concurrently
        CompletableFuture<Void> future1 = CompletableFuture.runAsync(() -> {
            try {
                asyncGenerationService.processGenerationAsync(resource1, requestInfo);
            } catch (Exception e) {
                fail("Should not throw exception");
            }
        });

        CompletableFuture<Void> future2 = CompletableFuture.runAsync(() -> {
            try {
                asyncGenerationService.processGenerationAsync(resource2, requestInfo);
            } catch (Exception e) {
                fail("Should not throw exception");
            }
        });

        // Wait for both to complete
        CompletableFuture.allOf(future1, future2).get(10, TimeUnit.SECONDS);

        // Then
        verify(excelWorkflowService, timeout(5000).times(2)).generateAndUploadExcel(any());
        verify(producer, timeout(5000).times(2)).push(eq("dev"), eq("test-update-topic"), any(GenerateResource.class));
    }

    // Helper methods
    private GenerateResource createGenerateResource(String id, String tenantId) {
        return GenerateResource.builder()
                .id(id)
                .tenantId(tenantId)
                .status(GenerationConstants.STATUS_PENDING)
                .type("EXCEL")
                .createdTime(System.currentTimeMillis())
                .lastModifiedTime(System.currentTimeMillis())
                .build();
    }

    private RequestInfo createRequestInfo() {
        UserInfo userInfo = UserInfo.builder()
                .uuid("user-123")
                .build();
        
        return RequestInfo.builder()
                .apiId("test-api")
                .ver("1.0")
                .ts(System.currentTimeMillis())
                .userInfo(userInfo)
                .build();
    }
}