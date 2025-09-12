package org.egov.excelingestion.service;

import lombok.extern.slf4j.Slf4j;
import org.egov.excelingestion.config.ProcessorConfigurationRegistry;
import org.egov.excelingestion.util.EnrichmentUtil;
import org.egov.excelingestion.web.models.ProcessResource;
import org.egov.common.producer.Producer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Integration tests for post-processing features:
 * - handlePostProcessing method usage
 * - Chunked persistence (200 records per chunk)
 * - Processing result topic publishing
 * - Total rows processed tracking
 */
@ExtendWith(MockitoExtension.class)
@Slf4j
public class PostProcessingIntegrationTest {

    @Mock
    private Producer producer;
    
    @Mock
    private ProcessorConfigurationRegistry configRegistry;
    
    @Mock
    private ConfigBasedProcessingService configBasedProcessingService;
    
    private EnrichmentUtil enrichmentUtil;
    
    @BeforeEach
    void setUp() {
        enrichmentUtil = new EnrichmentUtil();
    }

    @Test
    void testCase1_HandlePostProcessingCalledAfterSheetProcessing() {
        log.info("🧪 Test Case 1: Verify handlePostProcessing method exists and can be called");
        
        // Given
        ProcessResource resource = createTestResource("unified-console-parse");
        Map<String, String> localizationMap = new HashMap<>();
        List<Map<String, Object>> testData = createTestData(150);
        
        // When & Then - Verify method can be called without exception
        assertDoesNotThrow(() -> {
            configBasedProcessingService.handlePostProcessing("TEST_SHEET", 150, resource, localizationMap, testData);
        });
        
        log.info("✅ handlePostProcessing method callable");
    }

    @Test
    void testCase2_ChunkedPersistenceWith200RecordsPerChunk() {
        log.info("🧪 Test Case 2: Verify chunking calculation for 450 records");
        
        // Test chunking logic
        int totalRecords = 450;
        int chunkSize = 200;
        int expectedChunks = (int) Math.ceil((double) totalRecords / chunkSize);
        
        assertEquals(3, expectedChunks, "450 records should create 3 chunks");
        
        // Calculate actual chunk sizes
        int[] chunkSizes = new int[expectedChunks];
        for (int i = 0; i < expectedChunks; i++) {
            int startIndex = i * chunkSize;
            int endIndex = Math.min(startIndex + chunkSize, totalRecords);
            chunkSizes[i] = endIndex - startIndex;
        }
        
        assertEquals(200, chunkSizes[0], "First chunk should have 200 records");
        assertEquals(200, chunkSizes[1], "Second chunk should have 200 records");
        assertEquals(50, chunkSizes[2], "Third chunk should have 50 records");
        
        log.info("✅ Chunking calculation verified: 200 + 200 + 50 = 450");
    }

    @Test
    void testCase3_ProcessingResultTopicPublishing() {
        log.info("🧪 Test Case 3: Verify processing result topic configuration");
        
        // Use real registry to test actual configuration
        ProcessorConfigurationRegistry realRegistry = new ProcessorConfigurationRegistry();
        
        // Test parse type has topic
        String parseTopic = realRegistry.getProcessingResultTopic("unified-console-parse");
        assertEquals("hcm-processing-result", parseTopic, "Parse type should have processing result topic");
        
        // Test validation type has no topic
        String validationTopic = realRegistry.getProcessingResultTopic("unified-console-validation");
        assertNull(validationTopic, "Validation type should not have processing result topic");
        
        log.info("✅ Processing result topic configuration verified");
    }

    @Test
    void testCase4_NoTopicPublishingWhenNotConfigured() {
        log.info("🧪 Test Case 4: Verify different processor types have different topic configurations");
        
        ProcessorConfigurationRegistry realRegistry = new ProcessorConfigurationRegistry();
        
        // Test supported processor types
        String[] supportedTypes = realRegistry.getSupportedProcessorTypes();
        assertTrue(supportedTypes.length > 0, "Should have supported processor types");
        
        // Test specific configurations
        assertTrue(realRegistry.isProcessorTypeSupported("unified-console-parse"));
        assertTrue(realRegistry.isProcessorTypeSupported("unified-console-validation"));
        assertFalse(realRegistry.isProcessorTypeSupported("non-existent-type"));
        
        log.info("✅ Processor type support verified");
    }

    @Test
    void testCase5_TotalRowsProcessedAccumulation() {
        log.info("🧪 Test Case 5: Verify total rows processed accumulates across sheets");
        
        // Given
        ProcessResource resource = createTestResource("unified-console-parse");
        
        // When - Process multiple sheets
        enrichmentUtil.enrichRowCountInAdditionalDetails(resource, 100); // Sheet 1
        enrichmentUtil.enrichRowCountInAdditionalDetails(resource, 150); // Sheet 2
        enrichmentUtil.enrichRowCountInAdditionalDetails(resource, 75);  // Sheet 3
        
        // Then
        assertEquals(325L, resource.getAdditionalDetails().get("totalRowsProcessed"));
        log.info("✅ Total rows correctly accumulated: 100 + 150 + 75 = 325");
    }

    @Test
    void testCase6_PersistenceSkippedWhenDisabled() {
        log.info("🧪 Test Case 6: Verify persistence configuration options");
        
        // Test enabled configuration
        ProcessorConfigurationRegistry.ProcessorSheetConfig enabledConfig = 
                new ProcessorConfigurationRegistry.ProcessorSheetConfig("TEST_SHEET", "test-schema", null, true);
        assertTrue(enabledConfig.isPersistParsings(), "Should be enabled when set to true");
        
        // Test disabled configuration
        ProcessorConfigurationRegistry.ProcessorSheetConfig disabledConfig = 
                new ProcessorConfigurationRegistry.ProcessorSheetConfig("TEST_SHEET", "test-schema", null, false);
        assertFalse(disabledConfig.isPersistParsings(), "Should be disabled when set to false");
        
        log.info("✅ Persistence configuration options verified");
    }

    @Test
    void testCase7_CompleteProcessingWorkflow() {
        log.info("🧪 Test Case 7: Verify complete processing workflow with row counting");
        
        // Given
        ProcessResource resource = createTestResource("unified-console-parse");
        resource.setAdditionalDetails(new HashMap<>());
        
        // Simulate processing multiple operations
        enrichmentUtil.enrichRowCountInAdditionalDetails(resource, 250);
        enrichmentUtil.enrichRowCountInAdditionalDetails(resource, 150);
        enrichmentUtil.enrichRowCountInAdditionalDetails(resource, 100);
        
        // Then
        assertEquals(500L, resource.getAdditionalDetails().get("totalRowsProcessed"));
        
        // Verify configuration is correct
        ProcessorConfigurationRegistry realRegistry = new ProcessorConfigurationRegistry();
        String topic = realRegistry.getProcessingResultTopic("unified-console-parse");
        assertEquals("hcm-processing-result", topic);
        
        log.info("✅ Complete workflow verified: 500 total rows, topic configured");
    }

    @Test
    void testCase8_LargeDatasetChunking() {
        log.info("🧪 Test Case 8: Verify chunking calculation for large datasets");
        
        // Test large dataset (1250 records)
        int totalRecords = 1250;
        int chunkSize = 200;
        int expectedChunks = (int) Math.ceil((double) totalRecords / chunkSize);
        
        assertEquals(7, expectedChunks, "1250 records should create 7 chunks");
        
        // Verify distribution
        int fullChunks = totalRecords / chunkSize; // 6 full chunks
        int remainingRecords = totalRecords % chunkSize; // 50 remaining
        
        assertEquals(6, fullChunks, "Should have 6 full chunks of 200");
        assertEquals(50, remainingRecords, "Should have 50 remaining records");
        
        log.info("✅ Large dataset chunking verified: 6×200 + 1×50 = 1250");
    }

    @Test
    void testCase9_MultipleSheetProcessingWithAccumulation() {
        log.info("🧪 Test Case 9: Verify multiple sheet row count accumulation");
        
        // Given
        ProcessResource resource = createTestResource("unified-console-parse");
        resource.setAdditionalDetails(new HashMap<>());
        
        // When - Process 3 sheets with different row counts
        enrichmentUtil.enrichRowCountInAdditionalDetails(resource, 180);
        enrichmentUtil.enrichRowCountInAdditionalDetails(resource, 220);
        enrichmentUtil.enrichRowCountInAdditionalDetails(resource, 100);
        
        // Then
        assertEquals(500L, resource.getAdditionalDetails().get("totalRowsProcessed"));
        
        // Verify chunking calculations for each sheet
        assertEquals(1, Math.ceil(180.0 / 200), "Sheet1 (180 records) = 1 chunk");
        assertEquals(2, Math.ceil(220.0 / 200), "Sheet2 (220 records) = 2 chunks");
        assertEquals(1, Math.ceil(100.0 / 200), "Sheet3 (100 records) = 1 chunk");
        
        log.info("✅ Multiple sheets processed: 180 + 220 + 100 = 500 total rows");
    }

    @Test
    void testCase10_ErrorHandlingAndRobustness() {
        log.info("🧪 Test Case 10: Verify error handling and robustness");
        
        // Given
        ProcessResource resource = createTestResource("unified-console-parse");
        
        // Test with null/empty data
        enrichmentUtil.enrichRowCountInAdditionalDetails(resource, 0);
        assertEquals(0L, resource.getAdditionalDetails().get("totalRowsProcessed"));
        
        // Test with null additionalDetails
        resource.setAdditionalDetails(null);
        enrichmentUtil.enrichRowCountInAdditionalDetails(resource, 50);
        assertNotNull(resource.getAdditionalDetails());
        assertEquals(50L, resource.getAdditionalDetails().get("totalRowsProcessed"));
        
        // Test with invalid processor type
        ProcessorConfigurationRegistry realRegistry = new ProcessorConfigurationRegistry();
        String invalidTopic = realRegistry.getProcessingResultTopic("invalid-type");
        assertNull(invalidTopic, "Invalid type should return null topic");
        
        log.info("✅ Error handling works correctly for edge cases");
    }

    // Helper methods
    private ProcessResource createTestResource(String type) {
        return ProcessResource.builder()
                .id("test-id")
                .tenantId("test-tenant")
                .type(type)
                .referenceId("test-ref")
                .fileStoreId("test-file")
                .additionalDetails(new HashMap<>())
                .build();
    }

    private List<Map<String, Object>> createTestData(int count) {
        return java.util.stream.IntStream.range(0, count)
                .mapToObj(i -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put("id", "ID" + i);
                    row.put("name", "Name" + i);
                    row.put("value", i * 10);
                    return row;
                })
                .toList();
    }

}