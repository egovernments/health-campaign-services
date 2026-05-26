package org.egov.excelingestion.service;

import org.egov.common.producer.Producer;
import org.egov.excelingestion.cache.GenerationCacheService;
import org.egov.excelingestion.config.KafkaTopicConfig;
import org.egov.excelingestion.constants.GenerationConstants;
import org.egov.excelingestion.repository.GeneratedFileRepository;
import org.egov.excelingestion.util.RequestInfoConverter;
import org.egov.excelingestion.web.models.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for the prior-record expiry hook in GenerationService.
 * Reflects the new contract: ANY non-expired prior record for
 * (tenantId, referenceId, type) is expired on a fresh init.
 */
@ExtendWith(MockitoExtension.class)
class GenerationExpiryTest {

    @Mock private GeneratedFileRepository generatedFileRepository;
    @Mock private Producer producer;
    @Mock private ExcelGenerationValidationService validationService;
    @Mock private RequestInfoConverter requestInfoConverter;
    @Mock private KafkaTopicConfig kafkaTopicConfig;
    @Mock private GenerationCacheService generationCacheService;

    private GenerationService generationService;
    private Method expirePreviousRecordsMethod;

    @BeforeEach
    void setUp() throws Exception {
        when(kafkaTopicConfig.getGenerationUpdateTopic()).thenReturn("update-topic");

        generationService = new GenerationService(
                generatedFileRepository,
                producer,
                validationService,
                requestInfoConverter,
                kafkaTopicConfig,
                generationCacheService);

        expirePreviousRecordsMethod = GenerationService.class
                .getDeclaredMethod("expirePreviousRecords", GenerateResource.class);
        expirePreviousRecordsMethod.setAccessible(true);
    }

    @Test
    void testExpiry_WithExistingRecords_ShouldExpireThem() throws Exception {
        GenerateResource newResource = createGenerateResource("new-id", "campaign-123", "EXCEL", "tenant-01");
        List<GenerateResource> existingRecords = Arrays.asList(
                createRecord("existing-1", "campaign-123", "EXCEL", "tenant-01", GenerationConstants.STATUS_COMPLETED),
                createRecord("existing-2", "campaign-123", "EXCEL", "tenant-01", GenerationConstants.STATUS_FAILED));

        when(generatedFileRepository.search(any(GenerationSearchCriteria.class))).thenReturn(existingRecords);

        expirePreviousRecordsMethod.invoke(generationService, newResource);

        ArgumentCaptor<GenerationSearchCriteria> searchCaptor = ArgumentCaptor.forClass(GenerationSearchCriteria.class);
        verify(generatedFileRepository).search(searchCaptor.capture());

        GenerationSearchCriteria criteria = searchCaptor.getValue();
        assertEquals("tenant-01", criteria.getTenantId());
        assertEquals(Arrays.asList("campaign-123"), criteria.getReferenceIds());
        assertEquals(Arrays.asList("EXCEL"), criteria.getTypes());
        // New contract: expire queued/pending/in_progress/completed/failed
        assertTrue(criteria.getStatuses().contains(GenerationConstants.STATUS_COMPLETED));
        assertTrue(criteria.getStatuses().contains(GenerationConstants.STATUS_FAILED));
        assertTrue(criteria.getStatuses().contains(GenerationConstants.STATUS_IN_PROGRESS));
        assertTrue(criteria.getStatuses().contains(GenerationConstants.STATUS_QUEUED));

        ArgumentCaptor<GenerateResource> resourceCaptor = ArgumentCaptor.forClass(GenerateResource.class);
        verify(producer, times(2)).push(eq("tenant-01"), eq("update-topic"), resourceCaptor.capture());

        for (GenerateResource expired : resourceCaptor.getAllValues()) {
            assertEquals(GenerationConstants.STATUS_EXPIRED, expired.getStatus());
            assertNotNull(expired.getLastModifiedTime());
        }

        verify(generationCacheService).invalidate("tenant-01", "campaign-123");
    }

    @Test
    void testExpiry_WithNoExistingRecords_ShouldDoNothing() throws Exception {
        GenerateResource newResource = createGenerateResource("new-id", "campaign-456", "EXCEL", "tenant-02");
        when(generatedFileRepository.search(any(GenerationSearchCriteria.class)))
                .thenReturn(Collections.emptyList());

        expirePreviousRecordsMethod.invoke(generationService, newResource);

        verify(generatedFileRepository).search(any(GenerationSearchCriteria.class));
        verify(producer, never()).push(anyString(), any(), any());
    }

    @Test
    void testExpiry_WithNullReferenceId_ShouldSkip() throws Exception {
        GenerateResource newResource = createGenerateResource("new-id", null, "EXCEL", "tenant-03");

        expirePreviousRecordsMethod.invoke(generationService, newResource);

        verify(generatedFileRepository, never()).search(any(GenerationSearchCriteria.class));
        verify(producer, never()).push(anyString(), any(), any());
    }

    @Test
    void testExpiry_WithNullType_ShouldSkip() throws Exception {
        GenerateResource newResource = createGenerateResource("new-id", "campaign-789", null, "tenant-04");

        expirePreviousRecordsMethod.invoke(generationService, newResource);

        verify(generatedFileRepository, never()).search(any(GenerationSearchCriteria.class));
        verify(producer, never()).push(anyString(), any(), any());
    }

    @Test
    void testExpiry_WithRepositoryException_DoesNotPropagate() throws Exception {
        GenerateResource newResource = createGenerateResource("new-id", "campaign-error", "EXCEL", "tenant-06");
        when(generatedFileRepository.search(any(GenerationSearchCriteria.class)))
                .thenThrow(new RuntimeException("Database error"));

        assertDoesNotThrow(() -> expirePreviousRecordsMethod.invoke(generationService, newResource));

        verify(producer, never()).push(anyString(), any(), any());
    }

    private GenerateResource createGenerateResource(String id, String referenceId, String type, String tenantId) {
        return GenerateResource.builder()
                .id(id)
                .referenceId(referenceId)
                .type(type)
                .tenantId(tenantId)
                .status(GenerationConstants.STATUS_QUEUED)
                .lastModifiedBy("test-user")
                .createdTime(System.currentTimeMillis())
                .lastModifiedTime(System.currentTimeMillis())
                .build();
    }

    private GenerateResource createRecord(String id, String referenceId, String type, String tenantId, String status) {
        return GenerateResource.builder()
                .id(id)
                .referenceId(referenceId)
                .type(type)
                .tenantId(tenantId)
                .status(status)
                .lastModifiedBy("previous-user")
                .createdTime(System.currentTimeMillis() - 10000)
                .lastModifiedTime(System.currentTimeMillis() - 5000)
                .build();
    }
}
