package org.egov.excelingestion.processor;

import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.egov.excelingestion.service.BoundaryService;
import org.egov.excelingestion.service.CampaignService;
import org.egov.excelingestion.service.SchemaValidationService;
import org.egov.excelingestion.service.ValidationService;
import org.egov.excelingestion.util.BoundaryUtil;
import org.egov.excelingestion.util.ExcelUtil;
import org.egov.excelingestion.web.models.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Test cases for boundary validation in target sheets
 * Tests validateCampaignBoundaries method in BoundaryHierarchyTargetProcessor
 */
@ExtendWith(MockitoExtension.class)
class BoundaryValidationTest {

    @Mock
    private org.egov.excelingestion.service.MDMSService mdmsService;
    @Mock
    private SchemaValidationService schemaValidationService;
    @Mock
    private ValidationService validationService;
    @Mock
    private org.egov.excelingestion.util.EnrichmentUtil enrichmentUtil;
    @Mock
    private org.egov.excelingestion.exception.CustomExceptionHandler exceptionHandler;
    @Mock
    private org.egov.excelingestion.util.ExcelUtil excelUtil;
    @Mock
    private CampaignService campaignService;
    @Mock
    private BoundaryUtil boundaryUtil;

    private BoundaryHierarchyTargetProcessor targetProcessor;
    private Method validateCampaignBoundariesMethod;

    @BeforeEach
    void setUp() throws Exception {
        targetProcessor = new BoundaryHierarchyTargetProcessor(
            mdmsService, schemaValidationService, validationService, 
            enrichmentUtil, exceptionHandler, excelUtil, campaignService, boundaryUtil
        );
        
        // Get private method for testing
        validateCampaignBoundariesMethod = BoundaryHierarchyTargetProcessor.class
            .getDeclaredMethod("validateCampaignBoundaries", List.class, ProcessResource.class, 
                              RequestInfo.class, Map.class);
        validateCampaignBoundariesMethod.setAccessible(true);
    }

    @Test
    void testBoundaryValidation_WithInvalidBoundaryCodes_ShouldAddErrorDetails() throws Exception {
        // Given: Sheet data with boundary codes not in campaign
        List<Map<String, Object>> sheetData = Arrays.asList(
            createTargetRow("VALID_BOUNDARY_001", "Village A", "100", 3),
            createTargetRow("INVALID_BOUNDARY_999", "Invalid Village", "50", 4),
            createTargetRow("ANOTHER_INVALID_888", "Another Invalid", "75", 5)
        );
        
        ProcessResource resource = createProcessResource();
        RequestInfo requestInfo = new RequestInfo();
        Map<String, String> localizationMap = createLocalizationMap();
        
        // Mock campaign boundaries - only VALID_BOUNDARY_001 is in campaign
        Set<String> validBoundaryCodes = Set.of("VALID_BOUNDARY_001", "CAMPAIGN_BOUNDARY_002");
        when(boundaryUtil.getEnrichedBoundaryCodesFromCampaign(
            resource.getId(), resource.getReferenceId(), resource.getTenantId(), 
            resource.getHierarchyType(), requestInfo)).thenReturn(validBoundaryCodes);

        // When: Validate campaign boundaries
        @SuppressWarnings("unchecked")
        List<ValidationError> errors = (List<ValidationError>) validateCampaignBoundariesMethod
            .invoke(targetProcessor, sheetData, resource, requestInfo, localizationMap);

        // Then: Should have errors for invalid boundary codes
        assertNotNull(errors);
        assertEquals(2, errors.size()); // Two invalid boundaries
        
        // Check first error (INVALID_BOUNDARY_999)
        ValidationError error1 = errors.stream()
            .filter(e -> e.getRowNumber() == 4)
            .findFirst()
            .orElse(null);
        assertNotNull(error1);
        assertEquals("invalid", error1.getStatus());
        assertTrue(error1.getErrorDetails().contains("सीमा कोड अभियान में मौजूद नहीं है"));
        
        // Check second error (ANOTHER_INVALID_888)
        ValidationError error2 = errors.stream()
            .filter(e -> e.getRowNumber() == 5)
            .findFirst()
            .orElse(null);
        assertNotNull(error2);
        assertEquals("invalid", error2.getStatus());
        assertTrue(error2.getErrorDetails().contains("सीमा कोड अभियान में मौजूद नहीं है"));
    }

    @Test
    void testBoundaryValidation_WithAllValidBoundaryCodes_ShouldReturnNoErrors() throws Exception {
        // Given: Sheet data with all valid boundary codes
        List<Map<String, Object>> sheetData = Arrays.asList(
            createTargetRow("VALID_BOUNDARY_001", "Village A", "100", 3),
            createTargetRow("VALID_BOUNDARY_002", "Village B", "150", 4),
            createTargetRow("VALID_BOUNDARY_003", "Village C", "200", 5)
        );
        
        ProcessResource resource = createProcessResource();
        RequestInfo requestInfo = new RequestInfo();
        Map<String, String> localizationMap = createLocalizationMap();
        
        // Mock campaign boundaries - all boundary codes are valid
        Set<String> validBoundaryCodes = Set.of(
            "VALID_BOUNDARY_001", "VALID_BOUNDARY_002", "VALID_BOUNDARY_003"
        );
        when(boundaryUtil.getEnrichedBoundaryCodesFromCampaign(
            resource.getId(), resource.getReferenceId(), resource.getTenantId(), 
            resource.getHierarchyType(), requestInfo)).thenReturn(validBoundaryCodes);

        // When: Validate campaign boundaries
        @SuppressWarnings("unchecked")
        List<ValidationError> errors = (List<ValidationError>) validateCampaignBoundariesMethod
            .invoke(targetProcessor, sheetData, resource, requestInfo, localizationMap);

        // Then: Should have no errors
        assertNotNull(errors);
        assertTrue(errors.isEmpty());
    }

    @Test
    void testBoundaryValidation_WithMixedBoundaryCodes_ShouldOnlyFlagInvalid() throws Exception {
        // Given: Sheet data with mix of valid and invalid boundary codes
        List<Map<String, Object>> sheetData = Arrays.asList(
            createTargetRow("VALID_BOUNDARY_001", "Valid Village", "100", 3),
            createTargetRow("INVALID_BOUNDARY_999", "Invalid Village", "50", 4),
            createTargetRow("VALID_BOUNDARY_002", "Another Valid Village", "125", 5),
            createTargetRow("ANOTHER_INVALID_777", "Another Invalid", "75", 6)
        );
        
        ProcessResource resource = createProcessResource();
        RequestInfo requestInfo = new RequestInfo();
        Map<String, String> localizationMap = createLocalizationMap();
        
        // Mock campaign boundaries
        Set<String> validBoundaryCodes = Set.of("VALID_BOUNDARY_001", "VALID_BOUNDARY_002");
        when(boundaryUtil.getEnrichedBoundaryCodesFromCampaign(
            resource.getId(), resource.getReferenceId(), resource.getTenantId(), 
            resource.getHierarchyType(), requestInfo)).thenReturn(validBoundaryCodes);

        // When: Validate campaign boundaries
        @SuppressWarnings("unchecked")
        List<ValidationError> errors = (List<ValidationError>) validateCampaignBoundariesMethod
            .invoke(targetProcessor, sheetData, resource, requestInfo, localizationMap);

        // Then: Should have errors only for invalid boundary codes
        assertNotNull(errors);
        assertEquals(2, errors.size());
        
        // Check that only invalid boundaries have errors
        List<Integer> errorRows = errors.stream()
            .map(ValidationError::getRowNumber)
            .sorted()
            .toList();
        assertEquals(Arrays.asList(4, 6), errorRows); // Rows with invalid boundaries
    }

    @Test
    void testBoundaryValidation_WithEmptyBoundaryCode_ShouldSkipValidation() throws Exception {
        // Given: Sheet data with empty/null boundary codes
        List<Map<String, Object>> sheetData = Arrays.asList(
            createTargetRow("", "Village with empty boundary", "100", 3),
            createTargetRow(null, "Village with null boundary", "150", 4),
            createTargetRow("VALID_BOUNDARY_001", "Valid Village", "200", 5)
        );
        
        ProcessResource resource = createProcessResource();
        RequestInfo requestInfo = new RequestInfo();
        Map<String, String> localizationMap = createLocalizationMap();
        
        // Mock campaign boundaries
        Set<String> validBoundaryCodes = Set.of("VALID_BOUNDARY_001");
        when(boundaryUtil.getEnrichedBoundaryCodesFromCampaign(
            resource.getId(), resource.getReferenceId(), resource.getTenantId(), 
            resource.getHierarchyType(), requestInfo)).thenReturn(validBoundaryCodes);

        // When: Validate campaign boundaries
        @SuppressWarnings("unchecked")
        List<ValidationError> errors = (List<ValidationError>) validateCampaignBoundariesMethod
            .invoke(targetProcessor, sheetData, resource, requestInfo, localizationMap);

        // Then: Should have no errors (empty/null boundaries are skipped)
        assertNotNull(errors);
        assertTrue(errors.isEmpty());
    }

    @Test
    void testBoundaryValidation_WithNoCampaignBoundaries_ShouldSkipValidation() throws Exception {
        // Given: Sheet data with boundary codes but no campaign boundaries
        List<Map<String, Object>> sheetData = Arrays.asList(
            createTargetRow("BOUNDARY_001", "Village A", "100", 3),
            createTargetRow("BOUNDARY_002", "Village B", "150", 4)
        );
        
        ProcessResource resource = createProcessResource();
        RequestInfo requestInfo = new RequestInfo();
        Map<String, String> localizationMap = createLocalizationMap();
        
        // Mock empty campaign boundaries
        Set<String> validBoundaryCodes = Collections.emptySet();
        when(boundaryUtil.getEnrichedBoundaryCodesFromCampaign(
            resource.getId(), resource.getReferenceId(), resource.getTenantId(), 
            resource.getHierarchyType(), requestInfo)).thenReturn(validBoundaryCodes);

        // When: Validate campaign boundaries
        @SuppressWarnings("unchecked")
        List<ValidationError> errors = (List<ValidationError>) validateCampaignBoundariesMethod
            .invoke(targetProcessor, sheetData, resource, requestInfo, localizationMap);

        // Then: Should validate and mark all boundaries as invalid (no valid boundaries to compare against)
        assertNotNull(errors);
        assertEquals(2, errors.size()); // Both boundaries are invalid since no valid campaign boundaries exist
    }

    @Test
    void testBoundaryValidation_WithException_ShouldReturnEmptyErrors() throws Exception {
        // Given: Sheet data with boundary codes
        List<Map<String, Object>> sheetData = Arrays.asList(
            createTargetRow("BOUNDARY_001", "Village A", "100", 3)
        );
        
        ProcessResource resource = createProcessResource();
        RequestInfo requestInfo = new RequestInfo();
        Map<String, String> localizationMap = createLocalizationMap();
        
        // Mock exception from boundary util
        when(boundaryUtil.getEnrichedBoundaryCodesFromCampaign(
            resource.getId(), resource.getReferenceId(), resource.getTenantId(), 
            resource.getHierarchyType(), requestInfo)).thenThrow(new RuntimeException("Boundary service error"));

        // When: Validate campaign boundaries
        @SuppressWarnings("unchecked")
        List<ValidationError> errors = (List<ValidationError>) validateCampaignBoundariesMethod
            .invoke(targetProcessor, sheetData, resource, requestInfo, localizationMap);

        // Then: Should handle exception gracefully and return empty errors
        assertNotNull(errors);
        assertTrue(errors.isEmpty());
    }

    @Test
    void testBoundaryValidation_WithLocalization_ShouldUseLocalizedErrorMessage() throws Exception {
        // Given: Sheet data with invalid boundary code
        List<Map<String, Object>> sheetData = Arrays.asList(
            createTargetRow("INVALID_BOUNDARY", "Invalid Village", "100", 3)
        );
        
        ProcessResource resource = createProcessResource();
        RequestInfo requestInfo = new RequestInfo();
        
        // Custom localization map with different message
        Map<String, String> localizationMap = new HashMap<>();
        localizationMap.put("HCM_BOUNDARY_CODE_NOT_IN_CAMPAIGN_BOUNDARIES", "Custom boundary error message");
        
        // Mock campaign boundaries (empty to make boundary invalid)
        Set<String> validBoundaryCodes = Set.of("VALID_BOUNDARY_ONLY");
        when(boundaryUtil.getEnrichedBoundaryCodesFromCampaign(any(), any(), any(), any(), any()))
            .thenReturn(validBoundaryCodes);

        // When: Validate campaign boundaries
        @SuppressWarnings("unchecked")
        List<ValidationError> errors = (List<ValidationError>) validateCampaignBoundariesMethod
            .invoke(targetProcessor, sheetData, resource, requestInfo, localizationMap);

        // Then: Should use custom localized error message
        assertNotNull(errors);
        assertEquals(1, errors.size());
        ValidationError error = errors.get(0);
        assertTrue(error.getErrorDetails().contains("Custom boundary error message"));
    }

    // Helper methods
    private Map<String, Object> createTargetRow(String boundaryCode, String boundaryName, String target, int rowNumber) {
        Map<String, Object> row = new HashMap<>();
        row.put("HCM_ADMIN_CONSOLE_BOUNDARY_CODE", boundaryCode);
        row.put("BOUNDARY_NAME", boundaryName);
        row.put("TARGET_VALUE", target);
        row.put("__actualRowNumber__", rowNumber);
        return row;
    }
    
    private ProcessResource createProcessResource() {
        return ProcessResource.builder()
                .id("test-process-id")
                .referenceId("campaign-123")
                .tenantId("test-tenant")
                .hierarchyType("ADMIN")
                .type("target-microplan-ingestion")
                .build();
    }
    
    private Map<String, String> createLocalizationMap() {
        Map<String, String> localizationMap = new HashMap<>();
        localizationMap.put("HCM_BOUNDARY_CODE_NOT_IN_CAMPAIGN_BOUNDARIES", "सीमा कोड अभियान में मौजूद नहीं है");
        return localizationMap;
    }
}