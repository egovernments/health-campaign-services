package org.egov.excelingestion.util;

import org.egov.excelingestion.config.ValidationConstants;
import org.egov.excelingestion.web.models.ProcessResource;
import org.egov.excelingestion.web.models.ValidationError;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockitoAnnotations;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test cases for per-sheet status functionality in EnrichmentUtil
 */
class EnrichmentUtilPerSheetStatusTest {

    private EnrichmentUtil enrichmentUtil;
    private ProcessResource resource;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        enrichmentUtil = new EnrichmentUtil();
        resource = ProcessResource.builder()
                .id("test-resource-1")
                .tenantId("test-tenant")
                .type("user-microplan-ingestion")
                .additionalDetails(new HashMap<>())
                .build();
    }

    /**
     * Scenario A1: Enrich for SHEET_KIND_USER with empty errors
     * Expected: userSheetStatus == "valid"; validationStatus == "valid"
     */
    @Test
    void testUserSheetWithNoErrors() {
        List<ValidationError> errors = new ArrayList<>();

        enrichmentUtil.enrichErrorAndStatusInAdditionalDetails(resource, errors, ValidationConstants.SHEET_KIND_USER);

        Map<String, Object> additionalDetails = resource.getAdditionalDetails();
        assertEquals("valid", additionalDetails.get(ValidationConstants.ADDITIONAL_DETAILS_USER_SHEET_STATUS),
                "User sheet status should be valid when no errors");
        assertEquals("valid", additionalDetails.get("validationStatus"),
                "Overall validation status should be valid when no errors");
    }

    /**
     * Scenario A2: Enrich for SHEET_KIND_USER with 2 errors
     * Expected: userSheetStatus == "invalid"; validationStatus == "invalid"
     */
    @Test
    void testUserSheetWithErrors() {
        List<ValidationError> errors = new ArrayList<>();
        errors.add(createValidationError(1, "Column1", "error1", ValidationConstants.STATUS_INVALID));
        errors.add(createValidationError(2, "Column2", "error2", ValidationConstants.STATUS_ERROR));

        enrichmentUtil.enrichErrorAndStatusInAdditionalDetails(resource, errors, ValidationConstants.SHEET_KIND_USER);

        Map<String, Object> additionalDetails = resource.getAdditionalDetails();
        assertEquals("invalid", additionalDetails.get(ValidationConstants.ADDITIONAL_DETAILS_USER_SHEET_STATUS),
                "User sheet status should be invalid when errors exist");
        assertEquals("invalid", additionalDetails.get("validationStatus"),
                "Overall validation status should be invalid when errors exist");
    }

    /**
     * Scenario A3: Enrich for SHEET_KIND_BOUNDARY with errors
     * Expected: boundarySheetStatus == "invalid"; userSheetStatus absent
     */
    @Test
    void testBoundarySheetWithErrors() {
        List<ValidationError> errors = new ArrayList<>();
        errors.add(createValidationError(1, "Column1", "error", ValidationConstants.STATUS_INVALID));

        enrichmentUtil.enrichErrorAndStatusInAdditionalDetails(resource, errors, ValidationConstants.SHEET_KIND_BOUNDARY);

        Map<String, Object> additionalDetails = resource.getAdditionalDetails();
        assertEquals("invalid", additionalDetails.get(ValidationConstants.ADDITIONAL_DETAILS_BOUNDARY_SHEET_STATUS),
                "Boundary sheet status should be invalid");
        assertNull(additionalDetails.get(ValidationConstants.ADDITIONAL_DETAILS_USER_SHEET_STATUS),
                "User sheet status should not be present");
    }

    /**
     * Scenario A4: Enrich for SHEET_KIND_FACILITY with errors
     * Expected: facilitySheetStatus == "invalid"; others absent
     */
    @Test
    void testFacilitySheetWithErrors() {
        List<ValidationError> errors = new ArrayList<>();
        errors.add(createValidationError(1, "Column1", "error", ValidationConstants.STATUS_ERROR));

        enrichmentUtil.enrichErrorAndStatusInAdditionalDetails(resource, errors, ValidationConstants.SHEET_KIND_FACILITY);

        Map<String, Object> additionalDetails = resource.getAdditionalDetails();
        assertEquals("invalid", additionalDetails.get(ValidationConstants.ADDITIONAL_DETAILS_FACILITY_SHEET_STATUS),
                "Facility sheet status should be invalid");
        assertNull(additionalDetails.get(ValidationConstants.ADDITIONAL_DETAILS_USER_SHEET_STATUS),
                "User sheet status should not be present");
        assertNull(additionalDetails.get(ValidationConstants.ADDITIONAL_DETAILS_BOUNDARY_SHEET_STATUS),
                "Boundary sheet status should not be present");
    }

    /**
     * Scenario A5: Enrich for user (clean) then boundary (errors) on same resource
     * Expected: userSheetStatus="valid", boundarySheetStatus="invalid", validationStatus="invalid"
     */
    @Test
    void testMultipleSheetProcessing() {
        // Process user sheet with no errors
        List<ValidationError> emptyErrors = new ArrayList<>();
        enrichmentUtil.enrichErrorAndStatusInAdditionalDetails(resource, emptyErrors, ValidationConstants.SHEET_KIND_USER);

        // Process boundary sheet with errors
        List<ValidationError> boundaryErrors = new ArrayList<>();
        boundaryErrors.add(createValidationError(1, "Column1", "error", ValidationConstants.STATUS_INVALID));
        enrichmentUtil.enrichErrorAndStatusInAdditionalDetails(resource, boundaryErrors, ValidationConstants.SHEET_KIND_BOUNDARY);

        Map<String, Object> additionalDetails = resource.getAdditionalDetails();
        assertEquals("valid", additionalDetails.get(ValidationConstants.ADDITIONAL_DETAILS_USER_SHEET_STATUS),
                "User sheet status should remain valid");
        assertEquals("invalid", additionalDetails.get(ValidationConstants.ADDITIONAL_DETAILS_BOUNDARY_SHEET_STATUS),
                "Boundary sheet status should be invalid");
        assertEquals("invalid", additionalDetails.get("validationStatus"),
                "Overall validation status should be invalid (AND logic)");
    }

    /**
     * Scenario A6: additionalDetails map is null on input
     * Expected: Method initializes the map; no NPE
     */
    @Test
    void testNullAdditionalDetailsMap() {
        resource.setAdditionalDetails(null);
        List<ValidationError> errors = new ArrayList<>();

        assertDoesNotThrow(() -> {
            enrichmentUtil.enrichErrorAndStatusInAdditionalDetails(resource, errors, ValidationConstants.SHEET_KIND_USER);
        }, "Should not throw NPE when additionalDetails is null");

        assertNotNull(resource.getAdditionalDetails(), "additionalDetails should be initialized");
        assertEquals("valid", resource.getAdditionalDetails().get(ValidationConstants.ADDITIONAL_DETAILS_USER_SHEET_STATUS));
    }

    /**
     * Scenario A7: Enrich called twice for SHEET_KIND_USER with errors then clean
     * Expected: userSheetStatus stays "invalid" (no overwrite-to-valid on re-run)
     */
    @Test
    void testNoOverwriteToValidOnRerun() {
        // First call with errors
        List<ValidationError> errors = new ArrayList<>();
        errors.add(createValidationError(1, "Column1", "error", ValidationConstants.STATUS_INVALID));
        enrichmentUtil.enrichErrorAndStatusInAdditionalDetails(resource, errors, ValidationConstants.SHEET_KIND_USER);

        Map<String, Object> additionalDetails = resource.getAdditionalDetails();
        assertEquals("invalid", additionalDetails.get(ValidationConstants.ADDITIONAL_DETAILS_USER_SHEET_STATUS));

        // Second call with no errors (should not overwrite)
        List<ValidationError> cleanErrors = new ArrayList<>();
        enrichmentUtil.enrichErrorAndStatusInAdditionalDetails(resource, cleanErrors, ValidationConstants.SHEET_KIND_USER);

        // Status should remain invalid
        assertEquals("invalid", additionalDetails.get(ValidationConstants.ADDITIONAL_DETAILS_USER_SHEET_STATUS),
                "User sheet status should not be overwritten to valid on re-run");
    }

    /**
     * Scenario A8: validationStatus backward-compat verification
     * The validationStatus should be the AND of all sheet statuses
     */
    @Test
    void testValidationStatusBackwardCompat() {
        // All sheets valid
        enrichmentUtil.enrichErrorAndStatusInAdditionalDetails(
                resource, new ArrayList<>(), ValidationConstants.SHEET_KIND_USER);
        enrichmentUtil.enrichErrorAndStatusInAdditionalDetails(
                resource, new ArrayList<>(), ValidationConstants.SHEET_KIND_BOUNDARY);
        enrichmentUtil.enrichErrorAndStatusInAdditionalDetails(
                resource, new ArrayList<>(), ValidationConstants.SHEET_KIND_FACILITY);

        assertEquals("valid", resource.getAdditionalDetails().get("validationStatus"),
                "validationStatus should be valid when all sheets are valid");

        // Reset for next test
        resource.setAdditionalDetails(new HashMap<>());

        // User invalid, others valid
        List<ValidationError> userErrors = new ArrayList<>();
        userErrors.add(createValidationError(1, "Col", "err", ValidationConstants.STATUS_INVALID));
        enrichmentUtil.enrichErrorAndStatusInAdditionalDetails(resource, userErrors, ValidationConstants.SHEET_KIND_USER);
        enrichmentUtil.enrichErrorAndStatusInAdditionalDetails(
                resource, new ArrayList<>(), ValidationConstants.SHEET_KIND_BOUNDARY);

        assertEquals("invalid", resource.getAdditionalDetails().get("validationStatus"),
                "validationStatus should be invalid when any sheet is invalid (AND logic)");
    }

    // Helper method to create ValidationError
    private ValidationError createValidationError(int rowNumber, String columnName, String errorDetails, String status) {
        return ValidationError.builder()
                .rowNumber(rowNumber)
                .columnName(columnName)
                .errorDetails(errorDetails)
                .status(status)
                .build();
    }
}
