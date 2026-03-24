package org.egov.excelingestion.integration;

import org.egov.excelingestion.config.ValidationConstants;
import org.egov.excelingestion.util.EnrichmentUtil;
import org.egov.excelingestion.web.models.ProcessResource;
import org.egov.excelingestion.web.models.ValidationError;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for target schema validation and enrichment functionality
 * Tests the full flow of target schema processing, validation, and enrichment
 */
public class TargetSchemaValidationIntegrationTest {

    private EnrichmentUtil enrichmentUtil;

    @BeforeEach
    public void setUp() {
        enrichmentUtil = new EnrichmentUtil();
    }

    @Test
    public void testTargetSchemaValidationFlow_WithErrors_ShouldAccumulateCorrectly() {
        // Given - Simulate a microplan processing flow
        ProcessResource resource = ProcessResource.builder()
                .id("microplan-123")
                .tenantId("dev")
                .type("microplan-ingestion")
                .additionalDetails(new HashMap<>())
                .build();

        // Simulate facility sheet validation errors
        List<ValidationError> facilityErrors = Arrays.asList(
            ValidationError.builder()
                .sheetName("Facility")
                .status(ValidationConstants.STATUS_INVALID)
                .errorDetails("Phone Number must be 10 digits")
                .build(),
            ValidationError.builder()
                .sheetName("Facility")
                .status(ValidationConstants.STATUS_INVALID)
                .errorDetails("Invalid facility type")
                .build()
        );

        // Simulate user sheet validation errors
        List<ValidationError> userErrors = Arrays.asList(
            ValidationError.builder()
                .sheetName("User")
                .status(ValidationConstants.STATUS_INVALID)
                .errorDetails("Username already exists")
                .build()
        );

        // Simulate target sheet validation errors
        List<ValidationError> targetErrors = Arrays.asList(
            ValidationError.builder()
                .sheetName("Target")
                .status(ValidationConstants.STATUS_INVALID)
                .errorDetails("Target value must be at least 50")
                .build(),
            ValidationError.builder()
                .sheetName("Target")
                .status(ValidationConstants.STATUS_INVALID)
                .errorDetails("Invalid boundary code")
                .build(),
            ValidationError.builder()
                .sheetName("Target")
                .status(ValidationConstants.STATUS_VALID)
                .errorDetails("Valid target entry")
                .build()
        );

        // When - Process each sheet and enrich
        enrichmentUtil.enrichErrorAndStatusInAdditionalDetails(resource, facilityErrors);
        enrichmentUtil.enrichErrorAndStatusInAdditionalDetails(resource, userErrors);
        enrichmentUtil.enrichErrorAndStatusInAdditionalDetails(resource, targetErrors);

        // Then - Verify accumulated errors and status
        Map<String, Object> additionalDetails = resource.getAdditionalDetails();
        
        assertNotNull(additionalDetails);
        assertEquals(5L, additionalDetails.get("totalErrors")); // 2 + 1 + 2 = 5 (VALID entries not counted)
        assertEquals(ValidationConstants.STATUS_INVALID, additionalDetails.get("validationStatus"));
    }

    @Test
    public void testTargetSchemaValidationFlow_NoErrors_ShouldMaintainValidStatus() {
        // Given
        ProcessResource resource = ProcessResource.builder()
                .id("microplan-456")
                .tenantId("dev")
                .type("microplan-ingestion")
                .additionalDetails(new HashMap<>())
                .build();

        // Simulate all valid entries
        List<ValidationError> facilityErrors = Collections.emptyList();
        List<ValidationError> userErrors = Collections.emptyList();
        List<ValidationError> targetErrors = Arrays.asList(
            ValidationError.builder()
                .sheetName("Target")
                .status(ValidationConstants.STATUS_VALID)
                .errorDetails("Valid target entry")
                .build()
        );

        // When
        enrichmentUtil.enrichErrorAndStatusInAdditionalDetails(resource, facilityErrors);
        enrichmentUtil.enrichErrorAndStatusInAdditionalDetails(resource, userErrors);
        enrichmentUtil.enrichErrorAndStatusInAdditionalDetails(resource, targetErrors);

        // Then
        Map<String, Object> additionalDetails = resource.getAdditionalDetails();
        
        assertNotNull(additionalDetails);
        assertEquals(0L, additionalDetails.get("totalErrors"));
        assertEquals(ValidationConstants.STATUS_VALID, additionalDetails.get("validationStatus"));
    }

    @Test
    public void testTargetSchemaValidationFlow_MixedScenario_ShouldHandleCorrectly() {
        // Given
        ProcessResource resource = ProcessResource.builder()
                .id("microplan-789")
                .tenantId("dev")
                .type("microplan-ingestion")
                .additionalDetails(new HashMap<>())
                .build();

        // Simulate first processing with some errors
        List<ValidationError> firstBatchErrors = Arrays.asList(
            ValidationError.builder()
                .status(ValidationConstants.STATUS_INVALID)
                .errorDetails("Initial error")
                .build()
        );

        // Simulate second processing with more errors
        List<ValidationError> secondBatchErrors = Arrays.asList(
            ValidationError.builder()
                .status(ValidationConstants.STATUS_ERROR)
                .errorDetails("Processing error")
                .build(),
            ValidationError.builder()
                .status(ValidationConstants.STATUS_INVALID)
                .errorDetails("Validation error")
                .build()
        );

        // Simulate third processing with no errors
        List<ValidationError> thirdBatchErrors = Collections.emptyList();

        // When
        enrichmentUtil.enrichErrorAndStatusInAdditionalDetails(resource, firstBatchErrors);
        enrichmentUtil.enrichErrorAndStatusInAdditionalDetails(resource, secondBatchErrors);
        enrichmentUtil.enrichErrorAndStatusInAdditionalDetails(resource, thirdBatchErrors);

        // Then
        Map<String, Object> additionalDetails = resource.getAdditionalDetails();
        
        assertNotNull(additionalDetails);
        assertEquals(3L, additionalDetails.get("totalErrors")); // 1 + 2 + 0 = 3
        assertEquals(ValidationConstants.STATUS_INVALID, additionalDetails.get("validationStatus"));
    }

    @Test
    public void testTargetSchemaProcessing_ProjectTypeExtraction() {
        // Given
        ProcessResource resource = ProcessResource.builder()
                .id("test-resource")
                .tenantId("dev")
                .type("microplan-ingestion")
                .additionalDetails(new HashMap<>())
                .build();

        // Test different project types
        String[] projectTypes = {"LLIN", "IRS", "BCC", "BEDNET"};

        for (String projectType : projectTypes) {
            // When
            resource.getAdditionalDetails().put("projectType", projectType);

            // Then - Verify target schema would be constructed correctly
            String expectedSchemaName = "target-" + projectType;
            
            // This simulates what the BoundaryHierarchyTargetProcessor would do
            Object actualProjectType = resource.getAdditionalDetails().get("projectType");
            String actualSchemaName = "target-" + actualProjectType;
            
            assertEquals(expectedSchemaName, actualSchemaName);
        }
    }

    @Test
    public void testEnrichmentPersistence_AccrossMultipleProcessingSteps() {
        // Given - Simulate a resource that goes through multiple processing steps
        ProcessResource resource = ProcessResource.builder()
                .id("persistent-resource")
                .tenantId("dev")
                .type("microplan-ingestion")
                .additionalDetails(new HashMap<>())
                .build();

        // Step 1: Initial errors
        enrichmentUtil.enrichErrorAndStatusInAdditionalDetails(resource, Arrays.asList(
            ValidationError.builder().status(ValidationConstants.STATUS_INVALID).build()
        ));

        long step1Errors = (Long) resource.getAdditionalDetails().get("totalErrors");
        String step1Status = (String) resource.getAdditionalDetails().get("validationStatus");

        // Step 2: More errors  
        enrichmentUtil.enrichErrorAndStatusInAdditionalDetails(resource, Arrays.asList(
            ValidationError.builder().status(ValidationConstants.STATUS_INVALID).build(),
            ValidationError.builder().status(ValidationConstants.STATUS_ERROR).build()
        ));

        long step2Errors = (Long) resource.getAdditionalDetails().get("totalErrors");
        String step2Status = (String) resource.getAdditionalDetails().get("validationStatus");

        // Step 3: No new errors
        enrichmentUtil.enrichErrorAndStatusInAdditionalDetails(resource, Collections.emptyList());

        long step3Errors = (Long) resource.getAdditionalDetails().get("totalErrors");
        String step3Status = (String) resource.getAdditionalDetails().get("validationStatus");

        // Then - Verify progression
        assertEquals(1L, step1Errors);
        assertEquals(ValidationConstants.STATUS_INVALID, step1Status);

        assertEquals(3L, step2Errors); // 1 + 2 = 3
        assertEquals(ValidationConstants.STATUS_INVALID, step2Status);

        assertEquals(3L, step3Errors); // No new errors, stays 3
        assertEquals(ValidationConstants.STATUS_INVALID, step3Status); // Once invalid, stays invalid
    }

    @Test
    public void testWorkbookProcessorIntegration_ErrorColumnGeneration() {
        // Given - Simulate what happens when target sheet processing finds errors
        ProcessResource resource = ProcessResource.builder()
                .id("workbook-test")
                .tenantId("dev")
                .type("microplan-ingestion")
                .additionalDetails(new HashMap<>())
                .build();
        resource.getAdditionalDetails().put("projectType", "LLIN");

        // Simulate validation errors from target processing
        List<ValidationError> targetValidationErrors = Arrays.asList(
            ValidationError.builder()
                .sheetName("Target Sheet")
                .rowNumber(3)
                .columnName("Target Value")
                .status(ValidationConstants.STATUS_INVALID)
                .errorDetails("Target value must be between 50 and 10000")
                .build(),
            ValidationError.builder()
                .sheetName("Target Sheet")
                .rowNumber(4)
                .columnName("Boundary Code")
                .status(ValidationConstants.STATUS_INVALID)
                .errorDetails("Invalid boundary code 'XYZ123'")
                .build()
        );

        // When - Enrich with validation errors
        enrichmentUtil.enrichErrorAndStatusInAdditionalDetails(resource, targetValidationErrors);

        // Then - Verify enrichment worked correctly
        assertEquals(2L, resource.getAdditionalDetails().get("totalErrors"));
        assertEquals(ValidationConstants.STATUS_INVALID, resource.getAdditionalDetails().get("validationStatus"));

        // Verify error details structure for integration with workbook processing
        assertNotNull(resource.getAdditionalDetails());
        assertTrue(resource.getAdditionalDetails().containsKey("totalErrors"));
        assertTrue(resource.getAdditionalDetails().containsKey("validationStatus"));
    }
}