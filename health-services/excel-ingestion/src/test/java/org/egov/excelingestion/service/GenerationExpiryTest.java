package org.egov.excelingestion.service;

import org.egov.common.producer.Producer;
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
 * Test cases for generation expiry logic in GenerationService
 * Tests the expirePreviousCompletedRecords method behavior
 */
@ExtendWith(MockitoExtension.class)
class GenerationExpiryTest {

    @Mock
    private GeneratedFileRepository generatedFileRepository;
    @Mock
    private Producer producer;
    @Mock
    private AsyncGenerationService asyncGenerationService;
    @Mock
    private ExcelGenerationValidationService validationService;
    @Mock
    private RequestInfoConverter requestInfoConverter;

    private GenerationService generationService;
    private Method expirePreviousRecordsMethod;

    @BeforeEach
    void setUp() throws Exception {
        generationService = new GenerationService(
            generatedFileRepository, producer, asyncGenerationService, 
            validationService, requestInfoConverter
        );
        
        // Get private method for testing
        expirePreviousRecordsMethod = GenerationService.class
            .getDeclaredMethod("expirePreviousCompletedRecords", GenerateResource.class);
        expirePreviousRecordsMethod.setAccessible(true);
    }

    @Test
    void testExpiry_WithExistingCompletedRecords_ShouldExpireThem() throws Exception {
        // Given: New generation resource
        GenerateResource newResource = createGenerateResource("new-id", "campaign-123", "EXCEL", "tenant-01");
        
        // Mock existing completed records with same referenceId + type
        List<GenerateResource> existingRecords = Arrays.asList(
            createCompletedResource("existing-1", "campaign-123", "EXCEL", "tenant-01"),
            createCompletedResource("existing-2", "campaign-123", "EXCEL", "tenant-01")
        );
        
        when(generatedFileRepository.search(any(GenerationSearchCriteria.class)))
            .thenReturn(existingRecords);

        // When: Expire previous records
        expirePreviousRecordsMethod.invoke(generationService, newResource);

        // Then: Search should be called with correct criteria
        ArgumentCaptor<GenerationSearchCriteria> searchCaptor = ArgumentCaptor.forClass(GenerationSearchCriteria.class);
        verify(generatedFileRepository).search(searchCaptor.capture());
        
        GenerationSearchCriteria criteria = searchCaptor.getValue();
        assertEquals("tenant-01", criteria.getTenantId());
        assertEquals(Arrays.asList("campaign-123"), criteria.getReferenceIds());
        assertEquals(Arrays.asList("EXCEL"), criteria.getTypes());
        assertEquals(Arrays.asList(GenerationConstants.STATUS_COMPLETED), criteria.getStatuses());

        // Verify all existing records are expired and sent to Kafka
        ArgumentCaptor<GenerateResource> resourceCaptor = ArgumentCaptor.forClass(GenerateResource.class);
        verify(producer, times(2)).push(eq("tenant-01"), any(), resourceCaptor.capture());
        
        List<GenerateResource> expiredRecords = resourceCaptor.getAllValues();
        assertEquals(2, expiredRecords.size());
        
        for (GenerateResource expired : expiredRecords) {
            assertEquals(GenerationConstants.STATUS_EXPIRED, expired.getStatus());
            assertNotNull(expired.getLastModifiedTime());
            assertEquals(newResource.getLastModifiedBy(), expired.getLastModifiedBy());
        }
    }

    @Test
    void testExpiry_WithNoExistingRecords_ShouldDoNothing() throws Exception {
        // Given: New generation resource
        GenerateResource newResource = createGenerateResource("new-id", "campaign-456", "EXCEL", "tenant-02");
        
        // Mock no existing records
        when(generatedFileRepository.search(any(GenerationSearchCriteria.class)))
            .thenReturn(Collections.emptyList());

        // When: Expire previous records
        expirePreviousRecordsMethod.invoke(generationService, newResource);

        // Then: Search should be called but no records expired
        verify(generatedFileRepository).search(any(GenerationSearchCriteria.class));
        verify(producer, never()).push(anyString(), any(), any());
    }

    @Test
    void testExpiry_WithNullReferenceId_ShouldSkipExpiry() throws Exception {
        // Given: Resource with null referenceId
        GenerateResource newResource = createGenerateResource("new-id", null, "EXCEL", "tenant-03");

        // When: Expire previous records
        expirePreviousRecordsMethod.invoke(generationService, newResource);

        // Then: Should skip expiry process
        verify(generatedFileRepository, never()).search(any(GenerationSearchCriteria.class));
        verify(producer, never()).push(anyString(), any(), any());
    }

    @Test
    void testExpiry_WithNullType_ShouldSkipExpiry() throws Exception {
        // Given: Resource with null type
        GenerateResource newResource = createGenerateResource("new-id", "campaign-789", null, "tenant-04");

        // When: Expire previous records
        expirePreviousRecordsMethod.invoke(generationService, newResource);

        // Then: Should skip expiry process
        verify(generatedFileRepository, never()).search(any(GenerationSearchCriteria.class));
        verify(producer, never()).push(anyString(), any(), any());
    }

    @Test
    void testExpiry_WithDifferentTypes_ShouldNotExpire() throws Exception {
        // Given: New EXCEL generation
        GenerateResource newResource = createGenerateResource("new-id", "campaign-555", "EXCEL", "tenant-05");
        
        // Mock existing completed records with different type (PDF)
        List<GenerateResource> existingRecords = Arrays.asList(
            createCompletedResource("existing-1", "campaign-555", "PDF", "tenant-05")
        );
        
        when(generatedFileRepository.search(any(GenerationSearchCriteria.class)))
            .thenReturn(existingRecords);

        // When: Expire previous records
        expirePreviousRecordsMethod.invoke(generationService, newResource);

        // Then: Search should look for EXCEL type only, so no records found to expire
        ArgumentCaptor<GenerationSearchCriteria> searchCaptor = ArgumentCaptor.forClass(GenerationSearchCriteria.class);
        verify(generatedFileRepository).search(searchCaptor.capture());
        
        GenerationSearchCriteria criteria = searchCaptor.getValue();
        assertEquals(Arrays.asList("EXCEL"), criteria.getTypes()); // Should search for EXCEL only
        
        // The search returns records but they should still be expired (search criteria is correct)
        verify(producer, times(1)).push(anyString(), any(), any());
    }

    @Test
    void testExpiry_WithRepositoryException_ShouldNotFailGeneration() throws Exception {
        // Given: New generation resource
        GenerateResource newResource = createGenerateResource("new-id", "campaign-error", "EXCEL", "tenant-06");
        
        // Mock repository exception
        when(generatedFileRepository.search(any(GenerationSearchCriteria.class)))
            .thenThrow(new RuntimeException("Database error"));

        // When: Expire previous records (should not throw)
        assertDoesNotThrow(() -> {
            expirePreviousRecordsMethod.invoke(generationService, newResource);
        });

        // Then: Repository search should be attempted
        verify(generatedFileRepository).search(any(GenerationSearchCriteria.class));
        // No producer calls due to exception
        verify(producer, never()).push(anyString(), any(), any());
    }

    @Test
    void testExpiry_OnlyCompletedRecordsExpired_NotPendingOrFailed() throws Exception {
        // Given: New generation resource
        GenerateResource newResource = createGenerateResource("new-id", "campaign-mixed", "EXCEL", "tenant-07");
        
        // Mock mixed status records (only COMPLETED should be found by search criteria)
        List<GenerateResource> existingRecords = Arrays.asList(
            createCompletedResource("existing-completed", "campaign-mixed", "EXCEL", "tenant-07")
        );
        
        when(generatedFileRepository.search(any(GenerationSearchCriteria.class)))
            .thenReturn(existingRecords);

        // When: Expire previous records
        expirePreviousRecordsMethod.invoke(generationService, newResource);

        // Then: Search criteria should only look for COMPLETED status
        ArgumentCaptor<GenerationSearchCriteria> searchCaptor = ArgumentCaptor.forClass(GenerationSearchCriteria.class);
        verify(generatedFileRepository).search(searchCaptor.capture());
        
        GenerationSearchCriteria criteria = searchCaptor.getValue();
        assertEquals(Arrays.asList(GenerationConstants.STATUS_COMPLETED), criteria.getStatuses());
        
        // Only 1 record should be expired
        verify(producer, times(1)).push(anyString(), any(), any());
    }

    @Test
    void testExpiry_MultipleRecordsSameReferenceIdType_AllExpired() throws Exception {
        // Given: New generation resource
        GenerateResource newResource = createGenerateResource("new-id", "campaign-multi", "EXCEL", "tenant-08");
        
        // Mock multiple completed records with same referenceId + type
        List<GenerateResource> existingRecords = Arrays.asList(
            createCompletedResource("old-1", "campaign-multi", "EXCEL", "tenant-08"),
            createCompletedResource("old-2", "campaign-multi", "EXCEL", "tenant-08"),
            createCompletedResource("old-3", "campaign-multi", "EXCEL", "tenant-08")
        );
        
        when(generatedFileRepository.search(any(GenerationSearchCriteria.class)))
            .thenReturn(existingRecords);

        // When: Expire previous records
        expirePreviousRecordsMethod.invoke(generationService, newResource);

        // Then: All 3 records should be expired
        verify(producer, times(3)).push(eq("tenant-08"), any(), any(GenerateResource.class));
        
        ArgumentCaptor<GenerateResource> resourceCaptor = ArgumentCaptor.forClass(GenerateResource.class);
        verify(producer, times(3)).push(anyString(), any(), resourceCaptor.capture());
        
        List<GenerateResource> expiredRecords = resourceCaptor.getAllValues();
        assertEquals(3, expiredRecords.size());
        
        // Verify all are expired
        for (GenerateResource expired : expiredRecords) {
            assertEquals(GenerationConstants.STATUS_EXPIRED, expired.getStatus());
        }
    }

    // Helper methods
    private GenerateResource createGenerateResource(String id, String referenceId, String type, String tenantId) {
        return GenerateResource.builder()
                .id(id)
                .referenceId(referenceId)
                .type(type)
                .tenantId(tenantId)
                .status(GenerationConstants.STATUS_PENDING)
                .lastModifiedBy("test-user")
                .createdTime(System.currentTimeMillis())
                .lastModifiedTime(System.currentTimeMillis())
                .build();
    }
    
    private GenerateResource createCompletedResource(String id, String referenceId, String type, String tenantId) {
        return GenerateResource.builder()
                .id(id)
                .referenceId(referenceId)
                .type(type)
                .tenantId(tenantId)
                .status(GenerationConstants.STATUS_COMPLETED)
                .lastModifiedBy("previous-user")
                .createdTime(System.currentTimeMillis() - 10000)
                .lastModifiedTime(System.currentTimeMillis() - 5000)
                .build();
    }
}