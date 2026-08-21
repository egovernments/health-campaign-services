package org.egov.excelingestion.service;

import org.egov.common.producer.Producer;
import org.egov.excelingestion.config.KafkaTopicConfig;
import org.egov.excelingestion.constants.GenerationConstants;
import org.egov.excelingestion.util.EnrichmentUtil;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.contract.request.User;
import org.egov.excelingestion.web.models.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AsyncGenerationServiceTest {

    @Mock private ExcelWorkflowService excelWorkflowService;
    @Mock private Producer producer;
    @Mock private KafkaTopicConfig kafkaTopicConfig;
    @Mock private EnrichmentUtil enrichmentUtil;

    private AsyncGenerationService asyncGenerationService;

    @BeforeEach
    void setUp() {
        when(kafkaTopicConfig.getGenerationUpdateTopic()).thenReturn("test-update-topic");
        asyncGenerationService = new AsyncGenerationService(
                excelWorkflowService, producer, kafkaTopicConfig, enrichmentUtil);
    }

    @Test
    void shouldEmitInProgressThenCompletedOnSuccess() throws Exception {
        GenerateResource generateResource = createGenerateResource("test-id", "dev");
        RequestInfo requestInfo = createRequestInfo();

        GenerateResource completedResource = createGenerateResource("test-id", "dev");
        completedResource.setFileStoreId("file-store-id-123");
        when(excelWorkflowService.generateAndUploadExcel(any())).thenReturn(completedResource);

        // The same (mutable) resource instance is pushed for each transition, so snapshot the
        // status/fileStoreId at push time instead of matching the post-run object state.
        List<String> pushedStatuses = new ArrayList<>();
        List<String> pushedFileStoreIds = new ArrayList<>();
        doAnswer(inv -> {
            GenerateResource gr = inv.getArgument(2);
            pushedStatuses.add(gr.getStatus());
            pushedFileStoreIds.add(gr.getFileStoreId());
            return null;
        }).when(producer).push(eq("dev"), eq("test-update-topic"), any(GenerateResource.class));

        asyncGenerationService.processGeneration(generateResource, requestInfo);

        verify(excelWorkflowService).generateAndUploadExcel(any(GenerateResourceRequest.class));
        assertEquals(Arrays.asList(GenerationConstants.STATUS_IN_PROGRESS, GenerationConstants.STATUS_COMPLETED),
                pushedStatuses);
        assertEquals("file-store-id-123", pushedFileStoreIds.get(1));
    }

    @Test
    void shouldUpdateStatusToFailedOnException() throws Exception {
        GenerateResource generateResource = createGenerateResource("test-id", "dev");
        RequestInfo requestInfo = createRequestInfo();

        when(excelWorkflowService.generateAndUploadExcel(any()))
                .thenThrow(new RuntimeException("Generation failed"));

        // Snapshot status at push time (same mutable instance pushed for each transition).
        List<String> pushedStatuses = new ArrayList<>();
        doAnswer(inv -> {
            GenerateResource gr = inv.getArgument(2);
            pushedStatuses.add(gr.getStatus());
            return null;
        }).when(producer).push(eq("dev"), eq("test-update-topic"), any(GenerateResource.class));

        asyncGenerationService.processGeneration(generateResource, requestInfo);

        verify(enrichmentUtil).enrichErrorDetailsInAdditionalDetails(any(GenerateResource.class), any(Exception.class));
        assertEquals(Arrays.asList(GenerationConstants.STATUS_IN_PROGRESS, GenerationConstants.STATUS_FAILED),
                pushedStatuses);
    }

    private GenerateResource createGenerateResource(String id, String tenantId) {
        return GenerateResource.builder()
                .id(id)
                .tenantId(tenantId)
                .status(GenerationConstants.STATUS_QUEUED)
                .type("EXCEL")
                .createdTime(System.currentTimeMillis())
                .lastModifiedTime(System.currentTimeMillis())
                .build();
    }

    private RequestInfo createRequestInfo() {
        User userInfo = User.builder()
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
