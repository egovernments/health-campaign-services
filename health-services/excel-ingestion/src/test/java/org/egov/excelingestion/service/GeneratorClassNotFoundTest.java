package org.egov.excelingestion.service;

import org.egov.excelingestion.config.ErrorConstants;
import org.egov.excelingestion.exception.CustomExceptionHandler;
import org.egov.excelingestion.web.models.ProcessorGenerationConfig;
import org.egov.excelingestion.web.models.SheetGenerationConfig;
import org.egov.tracer.model.CustomException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test to verify generator class not found throws correct error code
 */
@ExtendWith(MockitoExtension.class)
class GeneratorClassNotFoundTest {

    @Mock
    private ApplicationContext applicationContext;
    
    @Mock
    private CustomExceptionHandler exceptionHandler;

    private GenerationConfigValidationService validationService;

    @BeforeEach
    void setUp() {
        validationService = new GenerationConfigValidationService(
            applicationContext,
            exceptionHandler
        );
    }

    @Test
    void testGeneratorClassNotFound_ThrowsCorrectErrorCode() {
        // Arrange
        SheetGenerationConfig sheetConfig = SheetGenerationConfig.builder()
            .sheetNameKey("HCM_CONSOLE_BOUNDARY_HIERARCHY")
            .schemaName(null)
            .generationClass("NonExistentGenerator")
            .isGenerationClassViaExcelPopulator(true)
            .order(1)
            .visible(true)
            .build();

        ProcessorGenerationConfig config = ProcessorGenerationConfig.builder()
            .processorType("microplan-ingestion")
            .sheets(Arrays.asList(sheetConfig))
            .build();

        // Mock the exception handler to throw the expected CustomException
        doThrow(new CustomException(
                ErrorConstants.GENERATOR_CLASS_NOT_FOUND,
                ErrorConstants.GENERATOR_CLASS_NOT_FOUND_MESSAGE.replace("{0}", "NonExistentGenerator")
        )).when(exceptionHandler).throwCustomException(
            eq(ErrorConstants.GENERATOR_CLASS_NOT_FOUND),
            anyString(),
            any(ClassNotFoundException.class)
        );

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class, () -> {
            validationService.validateProcessorConfig(config);
        });

        // Verify correct error code and message
        assertEquals(ErrorConstants.GENERATOR_CLASS_NOT_FOUND, exception.getCode());
        assertTrue(exception.getMessage().contains("NonExistentGenerator"));
        
        // Verify validation was called
        verify(exceptionHandler).throwCustomException(
            eq(ErrorConstants.GENERATOR_CLASS_NOT_FOUND),
            anyString(),
            any(ClassNotFoundException.class)
        );
    }

    @Test 
    void testExistingGeneratorClass_DoesNotThrowException() {
        // Arrange
        SheetGenerationConfig sheetConfig = SheetGenerationConfig.builder()
            .sheetNameKey("HCM_CONSOLE_BOUNDARY_HIERARCHY")
            .schemaName(null)
            .generationClass("BoundaryHierarchySheetGenerator") // This exists
            .isGenerationClassViaExcelPopulator(true)
            .order(1)
            .visible(true)
            .build();

        ProcessorGenerationConfig config = ProcessorGenerationConfig.builder()
            .processorType("microplan-ingestion")
            .sheets(Arrays.asList(sheetConfig))
            .build();

        // Mock ApplicationContext to return empty array (class exists but no beans found)
        when(applicationContext.getBeanNamesForType(any(Class.class))).thenReturn(new String[0]);

        // Act & Assert - should not throw exception for existing class
        assertDoesNotThrow(() -> {
            validationService.validateProcessorConfig(config);
        });

        // Verify no exception was thrown
        verifyNoInteractions(exceptionHandler);
    }

    @Test
    void testSchemaBasedGeneration_SkipsClassValidation() {
        // Arrange - sheet with schema but no generation class (schema-based)
        SheetGenerationConfig sheetConfig = SheetGenerationConfig.builder()
            .sheetNameKey("HCM_ADMIN_CONSOLE_FACILITIES_LIST")
            .schemaName("facility-microplan-ingestion")
            .generationClass(null) // No custom generator
            .order(1)
            .visible(true)
            .build();

        ProcessorGenerationConfig config = ProcessorGenerationConfig.builder()
            .processorType("microplan-ingestion")
            .sheets(Arrays.asList(sheetConfig))
            .build();

        // Mock ApplicationContext for SchemaBasedSheetGenerator check
        when(applicationContext.getBeanNamesForType(any(Class.class))).thenReturn(new String[]{"schemaBasedSheetGenerator"});

        // Act & Assert - should not validate custom generators for schema-based sheets
        assertDoesNotThrow(() -> {
            validationService.validateProcessorConfig(config);
        });

        // Verify no class validation was performed for custom generators
        verifyNoInteractions(exceptionHandler);
    }

    @Test
    void testMultipleSheets_ValidatesAllGeneratorClasses() {
        // Arrange
        SheetGenerationConfig validSheet = SheetGenerationConfig.builder()
            .sheetNameKey("VALID_SHEET")
            .generationClass("BoundaryHierarchySheetGenerator")
            .isGenerationClassViaExcelPopulator(true)
            .order(1)
            .visible(true)
            .build();

        SheetGenerationConfig invalidSheet = SheetGenerationConfig.builder()
            .sheetNameKey("INVALID_SHEET")  
            .generationClass("NonExistentGenerator")
            .isGenerationClassViaExcelPopulator(true)
            .order(2)
            .visible(true)
            .build();

        ProcessorGenerationConfig config = ProcessorGenerationConfig.builder()
            .processorType("microplan-ingestion")
            .sheets(Arrays.asList(validSheet, invalidSheet))
            .build();

        // Mock ApplicationContext behavior
        when(applicationContext.getBeanNamesForType(any(Class.class))).thenReturn(new String[0]);

        // Mock exception for invalid sheet
        doThrow(new CustomException(
                ErrorConstants.GENERATOR_CLASS_NOT_FOUND,
                ErrorConstants.GENERATOR_CLASS_NOT_FOUND_MESSAGE.replace("{0}", "NonExistentGenerator")
        )).when(exceptionHandler).throwCustomException(
            eq(ErrorConstants.GENERATOR_CLASS_NOT_FOUND),
            contains("NonExistentGenerator"),
            any(ClassNotFoundException.class)
        );

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class, () -> {
            validationService.validateProcessorConfig(config);
        });

        // Verify it fails on the invalid generator
        assertEquals(ErrorConstants.GENERATOR_CLASS_NOT_FOUND, exception.getCode());
        assertTrue(exception.getMessage().contains("NonExistentGenerator"));
    }
}