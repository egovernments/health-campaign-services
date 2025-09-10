package org.egov.excelingestion.util;

import org.egov.excelingestion.config.ProcessingConstants;
import org.egov.excelingestion.config.ValidationConstants;
import org.egov.excelingestion.web.models.ProcessResource;
import org.egov.excelingestion.web.models.ValidationError;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for EnrichmentUtil
 * Tests UUID enrichment and error/status enrichment in additionalDetails
 */
public class EnrichmentUtilTest {

    private EnrichmentUtil enrichmentUtil;

    @BeforeEach
    public void setUp() {
        enrichmentUtil = new EnrichmentUtil();
    }

    // testEnrichGenerateResource test removed as method no longer exists

    @Test
    public void testEnrichProcessResource_ShouldSetIdAndStatus() {
        // Given
        ProcessResource resource = ProcessResource.builder()
                .type("microplan-ingestion")
                .tenantId("test-tenant")
                .build();

        // When & Then
        // enrichProcessResource method removed - ID is now set by ProcessingService
        // This test is no longer relevant as enrichment doesn't handle ID generation
    }

    @Test
    public void testEnrichErrorAndStatusInAdditionalDetails_NewResource_ShouldAddErrors() {
        // Given
        ProcessResource resource = ProcessResource.builder()
                .id("test-id")
                .build();
        
        List<ValidationError> errors = Arrays.asList(
            ValidationError.builder()
                .status(ValidationConstants.STATUS_INVALID)
                .errorDetails("Error 1")
                .build(),
            ValidationError.builder()
                .status(ValidationConstants.STATUS_INVALID)
                .errorDetails("Error 2")
                .build(),
            ValidationError.builder()
                .status(ValidationConstants.STATUS_VALID)
                .errorDetails("Valid entry")
                .build()
        );

        // When
        enrichmentUtil.enrichErrorAndStatusInAdditionalDetails(resource, errors);

        // Then
        assertNotNull(resource.getAdditionalDetails());
        assertEquals(2L, resource.getAdditionalDetails().get("totalErrors")); // Only INVALID errors counted
        assertEquals(ValidationConstants.STATUS_INVALID, resource.getAdditionalDetails().get("validationStatus"));
    }

    @Test
    public void testEnrichErrorAndStatusInAdditionalDetails_ExistingErrors_ShouldAddToTotal() {
        // Given
        ProcessResource resource = ProcessResource.builder()
                .id("test-id")
                .additionalDetails(new HashMap<>())
                .build();
        
        // Set existing errors
        resource.getAdditionalDetails().put("totalErrors", 3L);
        resource.getAdditionalDetails().put("validationStatus", ValidationConstants.STATUS_INVALID);
        
        List<ValidationError> newErrors = Arrays.asList(
            ValidationError.builder()
                .status(ValidationConstants.STATUS_INVALID)
                .errorDetails("New Error 1")
                .build(),
            ValidationError.builder()
                .status(ValidationConstants.STATUS_ERROR)
                .errorDetails("New Error 2")
                .build()
        );

        // When
        enrichmentUtil.enrichErrorAndStatusInAdditionalDetails(resource, newErrors);

        // Then
        assertEquals(5L, resource.getAdditionalDetails().get("totalErrors")); // 3 existing + 2 new
        assertEquals(ValidationConstants.STATUS_INVALID, resource.getAdditionalDetails().get("validationStatus"));
    }

    @Test
    public void testEnrichErrorAndStatusInAdditionalDetails_NoErrors_ShouldSetValidStatus() {
        // Given
        ProcessResource resource = ProcessResource.builder()
                .id("test-id")
                .build();
        
        List<ValidationError> errors = Collections.emptyList();

        // When
        enrichmentUtil.enrichErrorAndStatusInAdditionalDetails(resource, errors);

        // Then
        assertNotNull(resource.getAdditionalDetails());
        assertEquals(0L, resource.getAdditionalDetails().get("totalErrors"));
        assertEquals(ValidationConstants.STATUS_VALID, resource.getAdditionalDetails().get("validationStatus"));
    }

    @Test
    public void testEnrichErrorAndStatusInAdditionalDetails_ExistingValidStatus_WithNewErrors_ShouldChangeToInvalid() {
        // Given
        ProcessResource resource = ProcessResource.builder()
                .id("test-id")
                .additionalDetails(new HashMap<>())
                .build();
        
        // Set existing valid status
        resource.getAdditionalDetails().put("totalErrors", 0L);
        resource.getAdditionalDetails().put("validationStatus", ValidationConstants.STATUS_VALID);
        
        List<ValidationError> newErrors = Arrays.asList(
            ValidationError.builder()
                .status(ValidationConstants.STATUS_INVALID)
                .errorDetails("First error")
                .build()
        );

        // When
        enrichmentUtil.enrichErrorAndStatusInAdditionalDetails(resource, newErrors);

        // Then
        assertEquals(1L, resource.getAdditionalDetails().get("totalErrors"));
        assertEquals(ValidationConstants.STATUS_INVALID, resource.getAdditionalDetails().get("validationStatus"));
    }

    @Test
    public void testEnrichErrorAndStatusInAdditionalDetails_ExistingInvalidStatus_WithNoNewErrors_ShouldStayInvalid() {
        // Given
        ProcessResource resource = ProcessResource.builder()
                .id("test-id")
                .additionalDetails(new HashMap<>())
                .build();
        
        // Set existing invalid status
        resource.getAdditionalDetails().put("totalErrors", 2L);
        resource.getAdditionalDetails().put("validationStatus", ValidationConstants.STATUS_INVALID);
        
        List<ValidationError> newErrors = Collections.emptyList(); // No new errors

        // When
        enrichmentUtil.enrichErrorAndStatusInAdditionalDetails(resource, newErrors);

        // Then
        assertEquals(2L, resource.getAdditionalDetails().get("totalErrors")); // Existing errors remain
        assertEquals(ValidationConstants.STATUS_INVALID, resource.getAdditionalDetails().get("validationStatus")); // Once invalid, stays invalid
    }

    @Test
    public void testEnrichErrorAndStatusInAdditionalDetails_OnlyValidStatusErrors_ShouldNotCount() {
        // Given
        ProcessResource resource = ProcessResource.builder()
                .id("test-id")
                .build();
        
        List<ValidationError> errors = Arrays.asList(
            ValidationError.builder()
                .status(ValidationConstants.STATUS_VALID)
                .errorDetails("Valid entry 1")
                .build(),
            ValidationError.builder()
                .status(ValidationConstants.STATUS_VALID)
                .errorDetails("Valid entry 2")
                .build()
        );

        // When
        enrichmentUtil.enrichErrorAndStatusInAdditionalDetails(resource, errors);

        // Then
        assertEquals(0L, resource.getAdditionalDetails().get("totalErrors")); // Valid status errors not counted
        assertEquals(ValidationConstants.STATUS_VALID, resource.getAdditionalDetails().get("validationStatus"));
    }

    @Test
    public void testEnrichErrorAndStatusInAdditionalDetails_NullAdditionalDetails_ShouldCreateNew() {
        // Given
        ProcessResource resource = ProcessResource.builder()
                .id("test-id")
                .additionalDetails(null)
                .build();
        
        List<ValidationError> errors = Arrays.asList(
            ValidationError.builder()
                .status(ValidationConstants.STATUS_INVALID)
                .errorDetails("Error")
                .build()
        );

        // When
        enrichmentUtil.enrichErrorAndStatusInAdditionalDetails(resource, errors);

        // Then
        assertNotNull(resource.getAdditionalDetails());
        assertEquals(1L, resource.getAdditionalDetails().get("totalErrors"));
        assertEquals(ValidationConstants.STATUS_INVALID, resource.getAdditionalDetails().get("validationStatus"));
    }

    @Test
    public void testEnrichErrorAndStatusInAdditionalDetails_ExistingErrorsAsInteger_ShouldHandleCorrectly() {
        // Given
        ProcessResource resource = ProcessResource.builder()
                .id("test-id")
                .additionalDetails(new HashMap<>())
                .build();
        
        // Set existing errors as Integer (different Number type)
        resource.getAdditionalDetails().put("totalErrors", 5); // Integer instead of Long
        resource.getAdditionalDetails().put("validationStatus", ValidationConstants.STATUS_INVALID);
        
        List<ValidationError> newErrors = Arrays.asList(
            ValidationError.builder()
                .status(ValidationConstants.STATUS_INVALID)
                .errorDetails("New error")
                .build()
        );

        // When
        enrichmentUtil.enrichErrorAndStatusInAdditionalDetails(resource, newErrors);

        // Then
        assertEquals(6L, resource.getAdditionalDetails().get("totalErrors")); // 5 + 1 = 6
        assertEquals(ValidationConstants.STATUS_INVALID, resource.getAdditionalDetails().get("validationStatus"));
    }
}