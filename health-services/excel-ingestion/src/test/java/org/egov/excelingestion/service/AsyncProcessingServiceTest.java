package org.egov.excelingestion.service;

import org.egov.common.producer.Producer;
import org.egov.excelingestion.config.ProcessingConstants;
import org.egov.excelingestion.web.models.*;
import org.egov.common.contract.models.AuditDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AsyncProcessingServiceTest {

    @Mock
    private ExcelProcessingService excelProcessingService;

    @Mock
    private Producer producer;

    private AsyncProcessingService asyncProcessingService;

    @BeforeEach
    void setUp() {
        asyncProcessingService = new AsyncProcessingService(excelProcessingService, producer);
        // Set the topic value that would normally be injected by @Value
        ReflectionTestUtils.setField(asyncProcessingService, "updateProcessingTopic", "test-update-processing-topic");
    }

    @Test
    void shouldProcessExcelAsynchronouslyAndPreserveId() throws Exception {
        // Given
        ProcessResource processResource = createProcessResource("test-id-123", "dev");
        RequestInfo requestInfo = createRequestInfo();
        
        ProcessResource completedResource = createProcessResource("different-id", "dev");
        completedResource.setProcessedFileStoreId("processed-file-123");
        when(excelProcessingService.processExcelFile(any())).thenReturn(completedResource);

        // When
        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
            try {
                asyncProcessingService.processExcelAsync(processResource, requestInfo);
            } catch (Exception e) {
                fail("Async processing should not throw exception: " + e.getMessage());
            }
        });

        // Wait for async completion
        future.get(5, TimeUnit.SECONDS);

        // Then
        verify(excelProcessingService, timeout(5000)).processExcelFile(any(ProcessResourceRequest.class));
        verify(producer, timeout(5000)).push(eq("dev"), eq("test-update-processing-topic"), argThat(resource -> {
            ProcessResource pr = (ProcessResource) resource;
            return ProcessingConstants.STATUS_COMPLETED.equals(pr.getStatus()) &&
                   "test-id-123".equals(pr.getId()) && // Original ID preserved
                   "processed-file-123".equals(pr.getProcessedFileStoreId());
        }));
    }

    @Test
    void shouldUpdateStatusToFailedOnProcessingException() throws Exception {
        // Given
        ProcessResource processResource = createProcessResource("test-id-456", "dev");
        RequestInfo requestInfo = createRequestInfo();
        
        when(excelProcessingService.processExcelFile(any()))
            .thenThrow(new RuntimeException("Processing failed"));

        // When
        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
            try {
                asyncProcessingService.processExcelAsync(processResource, requestInfo);
            } catch (Exception e) {
                // Expected to catch and handle the exception
            }
        });

        future.get(5, TimeUnit.SECONDS);

        // Then
        verify(producer, timeout(5000)).push(eq("dev"), eq("test-update-processing-topic"), argThat(resource -> {
            ProcessResource pr = (ProcessResource) resource;
            return ProcessingConstants.STATUS_FAILED.equals(pr.getStatus()) &&
                   "test-id-456".equals(pr.getId()) &&
                   pr.getAdditionalDetails() != null &&
                   pr.getAdditionalDetails().containsKey("errorCode") &&
                   pr.getAdditionalDetails().containsKey("errorMessage");
        }));
    }

    @Test
    void shouldHandleConcurrentProcessingRequestsWithDifferentIds() throws Exception {
        // Given
        ProcessResource resource1 = createProcessResource("proc-id-1", "dev");
        ProcessResource resource2 = createProcessResource("proc-id-2", "dev");
        RequestInfo requestInfo = createRequestInfo();
        
        ProcessResource completed1 = createProcessResource("any-id", "dev");
        completed1.setProcessedFileStoreId("file-1");
        ProcessResource completed2 = createProcessResource("any-id", "dev");
        completed2.setProcessedFileStoreId("file-2");
        
        when(excelProcessingService.processExcelFile(any()))
            .thenReturn(completed1, completed2);

        // When - Process multiple processing requests concurrently
        CompletableFuture<Void> future1 = CompletableFuture.runAsync(() -> {
            try {
                asyncProcessingService.processExcelAsync(resource1, requestInfo);
            } catch (Exception e) {
                fail("Should not throw exception");
            }
        });

        CompletableFuture<Void> future2 = CompletableFuture.runAsync(() -> {
            try {
                asyncProcessingService.processExcelAsync(resource2, requestInfo);
            } catch (Exception e) {
                fail("Should not throw exception");
            }
        });

        // Wait for both to complete
        CompletableFuture.allOf(future1, future2).get(10, TimeUnit.SECONDS);

        // Then
        verify(excelProcessingService, timeout(5000).times(2)).processExcelFile(any());
        verify(producer, timeout(5000).times(2)).push(eq("dev"), eq("test-update-processing-topic"), any(ProcessResource.class));
        
        // Verify both original IDs are preserved
        verify(producer, timeout(5000)).push(eq("dev"), eq("test-update-processing-topic"), argThat(resource -> {
            ProcessResource pr = (ProcessResource) resource;
            return "proc-id-1".equals(pr.getId());
        }));
        verify(producer, timeout(5000)).push(eq("dev"), eq("test-update-processing-topic"), argThat(resource -> {
            ProcessResource pr = (ProcessResource) resource;
            return "proc-id-2".equals(pr.getId());
        }));
    }

    // Helper methods
    private ProcessResource createProcessResource(String id, String tenantId) {
        AuditDetails auditDetails = AuditDetails.builder()
                .createdBy("user-123")
                .lastModifiedBy("user-123")
                .createdTime(System.currentTimeMillis())
                .lastModifiedTime(System.currentTimeMillis())
                .build();

        return ProcessResource.builder()
                .id(id)
                .tenantId(tenantId)
                .status(ProcessingConstants.STATUS_PENDING)
                .type("EXCEL")
                .hierarchyType("ADMIN")
                .referenceId("ref-" + id)
                .fileStoreId("file-" + id)
                .auditDetails(auditDetails)
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