package org.egov.excelingestion.config;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ProcessorConfigurationRegistry focusing on new configuration structure:
 * - Processing result topic configuration
 * - Per-type vs per-sheet configuration
 * - Configuration validation
 */
@Slf4j
public class ProcessorConfigurationRegistryTest {

    private ProcessorConfigurationRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new ProcessorConfigurationRegistry();
    }

    @Test
    void testProcessingResultTopicConfiguration() {
        log.info("🧪 Testing processing result topic configuration");
        
        // Test unified-console-parse has processing result topic
        String parseResultTopic = registry.getProcessingResultTopic("unified-console-parse");
        assertEquals("hcm-processing-result", parseResultTopic);
        
        // Test unified-console-validation has no processing result topic
        String validationResultTopic = registry.getProcessingResultTopic("unified-console-validation");
        assertNull(validationResultTopic);
        
        log.info("✅ Processing result topics configured correctly");
    }

    @Test
    void testSheetConfigurationStructure() {
        log.info("🧪 Testing sheet configuration structure without per-sheet topics");
        
        List<ProcessorConfigurationRegistry.ProcessorSheetConfig> parseConfig = 
                registry.getConfigByType("unified-console-parse");
        
        assertNotNull(parseConfig);
        assertEquals(3, parseConfig.size());
        
        // Verify no per-sheet topic configuration exists
        for (ProcessorConfigurationRegistry.ProcessorSheetConfig sheetConfig : parseConfig) {
            // The old getTriggerParsingCompleteTopic method should not exist
            assertNotNull(sheetConfig.getSheetNameKey());
            assertTrue(sheetConfig.isPersistParsings()); // All should have persistence enabled
        }
        
        log.info("✅ Sheet configuration structure is correct");
    }

    @Test
    void testUnsupportedProcessorType() {
        log.info("🧪 Testing unsupported processor type handling");
        
        List<ProcessorConfigurationRegistry.ProcessorSheetConfig> config = 
                registry.getConfigByType("non-existent-type");
        assertNull(config);
        
        String topic = registry.getProcessingResultTopic("non-existent-type");
        assertNull(topic);
        
        assertFalse(registry.isProcessorTypeSupported("non-existent-type"));
        
        log.info("✅ Unsupported processor types handled correctly");
    }
}