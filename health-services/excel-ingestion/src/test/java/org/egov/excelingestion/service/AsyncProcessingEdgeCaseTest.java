package org.egov.excelingestion.service;

import org.egov.common.producer.Producer;
import org.egov.excelingestion.config.KafkaTopicConfig;
import org.egov.excelingestion.config.ProcessingConstants;
import org.egov.excelingestion.util.RequestInfoConverter;
import org.egov.excelingestion.web.models.*;
import org.egov.common.contract.models.AuditDetails;
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
class AsyncProcessingEdgeCaseTest {

    @Mock
    private ExcelProcessingService excelProcessingService;

    @Mock
    private Producer producer;

    @Mock
    private KafkaTopicConfig kafkaTopicConfig;

    @Mock
    private ConfigBasedProcessingService configBasedProcessingService;

    @Mock
    private RequestInfoConverter requestInfoConverter;

    private AsyncProcessingService asyncProcessingService;

    @BeforeEach
    void setUp() {
        // Setup mock KafkaTopicConfig
        when(kafkaTopicConfig.getProcessingUpdateTopic()).thenReturn("test-topic");
        
        // Setup mock RequestInfoConverter
        when(requestInfoConverter.extractLocale(any())).thenReturn("en_IN");
        
        asyncProcessingService = new AsyncProcessingService(excelProcessingService, producer, kafkaTopicConfig, configBasedProcessingService, requestInfoConverter);
    }

    @Test
    void shouldHandleNullRequestInfoGracefully() throws Exception {
        // Given
        ProcessResource processResource = createProcessResource("test-id", "dev");
        RequestInfo requestInfo = null; // Null request info
        
        ProcessResource completedResource = createProcessResource("any-id", "dev");
        when(excelProcessingService.processExcelFile(any())).thenReturn(completedResource);

        // When
        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
            try {
                asyncProcessingService.processExcelAsync(processResource, requestInfo);
            } catch (Exception e) {
                fail("Should handle null RequestInfo gracefully: " + e.getMessage());
            }
        });

        future.get(5, TimeUnit.SECONDS);

        // Then - Should complete without user info updates
        verify(producer, timeout(5000)).push(eq("dev"), eq("test-topic"), argThat(resource -> {
            ProcessResource pr = (ProcessResource) resource;
            return ProcessingConstants.STATUS_COMPLETED.equals(pr.getStatus()) &&
                   "test-id".equals(pr.getId());
        }));
    }

    @Test
    void shouldHandleNullUserInfoInRequestInfo() throws Exception {
        // Given
        ProcessResource processResource = createProcessResource("test-id", "dev");
        RequestInfo requestInfo = RequestInfo.builder()
                .apiId("test-api")
                .userInfo(null) // Null user info
                .build();
        
        ProcessResource completedResource = createProcessResource("any-id", "dev");
        when(excelProcessingService.processExcelFile(any())).thenReturn(completedResource);

        // When
        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
            try {
                asyncProcessingService.processExcelAsync(processResource, requestInfo);
            } catch (Exception e) {
                fail("Should handle null UserInfo gracefully: " + e.getMessage());
            }
        });

        future.get(5, TimeUnit.SECONDS);

        // Then - Should complete without setting lastModifiedBy
        verify(producer, timeout(5000)).push(eq("dev"), eq("test-topic"), any(ProcessResource.class));
    }

    @Test
    void shouldHandleNullAuditDetailsGracefully() throws Exception {
        // Given
        ProcessResource processResource = ProcessResource.builder()
                .id("test-id")
                .tenantId("dev")
                .status(ProcessingConstants.STATUS_PENDING)
                .auditDetails(null) // Null audit details
                .build();
        
        RequestInfo requestInfo = createRequestInfo();
        ProcessResource completedResource = createProcessResource("any-id", "dev");
        when(excelProcessingService.processExcelFile(any())).thenReturn(completedResource);

        // When
        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
            try {
                asyncProcessingService.processExcelAsync(processResource, requestInfo);
            } catch (Exception e) {
                fail("Should handle null AuditDetails gracefully: " + e.getMessage());
            }
        });

        future.get(5, TimeUnit.SECONDS);

        // Then - Should complete without audit detail updates
        verify(producer, timeout(5000)).push(eq("dev"), eq("test-topic"), any(ProcessResource.class));
    }

    @Test
    void shouldPreserveOriginalIdEvenWhenProcessedResourceHasDifferentId() throws Exception {
        // Given
        ProcessResource processResource = createProcessResource("original-id-123", "dev");
        RequestInfo requestInfo = createRequestInfo();
        
        // Processed resource returns completely different ID
        ProcessResource processedResource = createProcessResource("completely-different-id", "dev");
        processedResource.setProcessedFileStoreId("processed-file");
        when(excelProcessingService.processExcelFile(any())).thenReturn(processedResource);

        // When
        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
            try {
                asyncProcessingService.processExcelAsync(processResource, requestInfo);
            } catch (Exception e) {
                fail("Should preserve original ID: " + e.getMessage());
            }
        });

        future.get(5, TimeUnit.SECONDS);

        // Then - Original ID should be preserved
        verify(producer, timeout(5000)).push(eq("dev"), eq("test-topic"), argThat(resource -> {
            ProcessResource pr = (ProcessResource) resource;
            return "original-id-123".equals(pr.getId()) && // Original ID preserved
                   ProcessingConstants.STATUS_COMPLETED.equals(pr.getStatus()) &&
                   "processed-file".equals(pr.getProcessedFileStoreId()); // But processed data copied
        }));
    }

    @Test
    void shouldCreateAdditionalDetailsIfNullOnError() throws Exception {
        // Given
        ProcessResource processResource = ProcessResource.builder()
                .id("test-id")
                .tenantId("dev")
                .status(ProcessingConstants.STATUS_PENDING)
                .additionalDetails(null) // No additional details initially
                .auditDetails(createAuditDetails())
                .build();
        
        RequestInfo requestInfo = createRequestInfo();
        when(excelProcessingService.processExcelFile(any()))
            .thenThrow(new RuntimeException("Processing failed"));

        // When
        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
            try {
                asyncProcessingService.processExcelAsync(processResource, requestInfo);
            } catch (Exception e) {
                // Expected to catch and handle
            }
        });

        future.get(5, TimeUnit.SECONDS);

        // Then - Should create additionalDetails and add error info
        verify(producer, timeout(5000)).push(eq("dev"), eq("test-topic"), argThat(resource -> {
            ProcessResource pr = (ProcessResource) resource;
            return ProcessingConstants.STATUS_FAILED.equals(pr.getStatus()) &&
                   pr.getAdditionalDetails() != null &&
                   pr.getAdditionalDetails().containsKey("errorCode") &&
                   pr.getAdditionalDetails().containsKey("errorMessage");
        }));
    }

    @Test
    void shouldHandleVeryLongErrorMessages() throws Exception {
        // Given
        ProcessResource processResource = createProcessResource("test-id", "dev");
        RequestInfo requestInfo = createRequestInfo();
        
        // Create very long error message
        String longErrorMessage = "A".repeat(10000); // 10K character error
        when(excelProcessingService.processExcelFile(any()))
            .thenThrow(new RuntimeException(longErrorMessage));

        // When
        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
            try {
                asyncProcessingService.processExcelAsync(processResource, requestInfo);
            } catch (Exception e) {
                // Expected to handle long errors
            }
        });

        future.get(5, TimeUnit.SECONDS);

        // Then - Should handle long error message without breaking
        verify(producer, timeout(5000)).push(eq("dev"), eq("test-topic"), argThat(resource -> {
            ProcessResource pr = (ProcessResource) resource;
            return ProcessingConstants.STATUS_FAILED.equals(pr.getStatus()) &&
                   pr.getAdditionalDetails().get("errorMessage").toString().length() > 1000;
        }));
    }

    // Helper methods
    private ProcessResource createProcessResource(String id, String tenantId) {
        return ProcessResource.builder()
                .id(id)
                .tenantId(tenantId)
                .status(ProcessingConstants.STATUS_PENDING)
                .type("EXCEL")
                .hierarchyType("ADMIN")
                .referenceId("ref-" + id)
                .fileStoreId("file-" + id)
                .auditDetails(createAuditDetails())
                .build();
    }

    private AuditDetails createAuditDetails() {
        return AuditDetails.builder()
                .createdBy("user-123")
                .lastModifiedBy("user-123")
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