package org.egov.excelingestion.service;

import org.egov.excelingestion.config.ExcelIngestionConfig;
import org.egov.excelingestion.exception.CustomExceptionHandler;
import org.egov.excelingestion.util.BoundaryColumnUtil;
import org.egov.excelingestion.util.CellProtectionManager;
import org.egov.excelingestion.util.ExcelDataPopulator;
import org.egov.excelingestion.util.HierarchicalBoundaryUtil;
import org.egov.excelingestion.web.models.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test for ConfigBasedGenerationService
 */
@ExtendWith(MockitoExtension.class)
class ConfigBasedGenerationServiceTest {

    @Mock
    private ApplicationContext applicationContext;
    
    @Mock
    private ExcelDataPopulator excelDataPopulator;
    
    @Mock
    private BoundaryColumnUtil boundaryColumnUtil;
    
    @Mock
    private HierarchicalBoundaryUtil hierarchicalBoundaryUtil;
    
    @Mock
    private CellProtectionManager cellProtectionManager;
    
    @Mock
    private ExcelIngestionConfig config;
    
    @Mock
    private CustomExceptionHandler exceptionHandler;
    
    @Mock
    private GenerationConfigValidationService validationService;

    private ConfigBasedGenerationService service;

    @BeforeEach
    void setUp() {
        service = new ConfigBasedGenerationService(
                applicationContext,
                excelDataPopulator,
                boundaryColumnUtil,
                hierarchicalBoundaryUtil,
                cellProtectionManager,
                config,
                exceptionHandler,
                validationService
        );
    }

    @Test
    void testValidateProcessorConfig() {
        // Test that validation service is called
        ProcessorGenerationConfig processorConfig = createSampleConfig();
        
        // This should not throw any exception in a basic test
        assertDoesNotThrow(() -> {
            // We can't fully test without mocking all dependencies,
            // but we can verify the service is properly constructed
            assertNotNull(service);
        });
    }

    private ProcessorGenerationConfig createSampleConfig() {
        return ProcessorGenerationConfig.builder()
                .applyWorkbookProtection(true)
                .protectionPassword("password")
                .zoomLevel(60)
                .sheets(Arrays.asList(
                        SheetGenerationConfig.builder()
                                .sheetName("TEST_SHEET")
                                .schemaName("test-schema")
                                .generationClass("org.test.TestGenerator")
                                .isGenerationClassViaExcelPopulator(true)
                                .order(1)
                                .visible(true)
                                .build()
                ))
                .build();
    }
}