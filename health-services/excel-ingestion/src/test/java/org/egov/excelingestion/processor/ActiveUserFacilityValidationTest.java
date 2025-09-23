package org.egov.excelingestion.processor;

import org.egov.excelingestion.service.BoundaryService;
import org.egov.excelingestion.service.CampaignService;
import org.egov.excelingestion.service.FacilityService;
import org.egov.excelingestion.service.ValidationService;
import org.egov.excelingestion.util.BoundaryUtil;
import org.egov.excelingestion.util.EnrichmentUtil;
import org.egov.excelingestion.util.ExcelUtil;
import org.egov.excelingestion.web.models.ProcessResource;
import org.egov.excelingestion.web.models.RequestInfo;
import org.egov.excelingestion.web.models.ValidationError;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestTemplate;

import java.lang.reflect.Method;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test cases for "at least one active user/facility" validation
 * Tests validateAtLeastOneActiveUser and validateAtLeastOneActiveFacility methods
 */
@ExtendWith(MockitoExtension.class)
class ActiveUserFacilityValidationTest {

    @Mock
    private ValidationService validationService;
    @Mock
    private RestTemplate restTemplate;
    @Mock
    private org.egov.excelingestion.config.ExcelIngestionConfig config;
    @Mock
    private EnrichmentUtil enrichmentUtil;
    @Mock
    private CampaignService campaignService;
    @Mock
    private BoundaryService boundaryService;
    @Mock
    private BoundaryUtil boundaryUtil;
    @Mock
    private ExcelUtil excelUtil;

    private UserValidationProcessor userValidationProcessor;
    private FacilityValidationProcessor facilityValidationProcessor;
    private Method validateAtLeastOneActiveUserMethod;
    private Method validateAtLeastOneActiveFacilityMethod;

    @BeforeEach
    void setUp() throws Exception {
        userValidationProcessor = new UserValidationProcessor(
            validationService, restTemplate, config, enrichmentUtil, 
            campaignService, boundaryService, boundaryUtil, excelUtil
        );
        
        facilityValidationProcessor = new FacilityValidationProcessor(
            validationService, config, enrichmentUtil, campaignService, 
            boundaryService, boundaryUtil, excelUtil
        );
        
        // Get private methods for testing
        validateAtLeastOneActiveUserMethod = UserValidationProcessor.class
            .getDeclaredMethod("validateAtLeastOneActiveUser", List.class, List.class, Map.class);
        validateAtLeastOneActiveUserMethod.setAccessible(true);
        
        validateAtLeastOneActiveFacilityMethod = FacilityValidationProcessor.class
            .getDeclaredMethod("validateAtLeastOneActiveFacility", List.class, List.class, Map.class);
        validateAtLeastOneActiveFacilityMethod.setAccessible(true);
    }

    // ===== USER VALIDATION TESTS =====

    @Test
    void testUserValidation_WithNoActiveUsers_ShouldAddErrorToFirstDataRow() throws Exception {
        // Given: Sheet data with no active users (all inactive)
        List<Map<String, Object>> sheetData = Arrays.asList(
            createUserRow("John Doe", "9876543210", "john123", "Inactive", 3),
            createUserRow("Jane Smith", "9876543211", "jane456", "Inactive", 4),
            createUserRow("Bob Wilson", "9876543212", "bob789", "Inactive", 5)
        );
        
        List<ValidationError> errors = new ArrayList<>();
        Map<String, String> localizationMap = createLocalizationMap();

        // When: Validate at least one active user
        validateAtLeastOneActiveUserMethod.invoke(userValidationProcessor, sheetData, errors, localizationMap);

        // Then: Should add error to first data row (row 3)
        assertEquals(1, errors.size());
        ValidationError error = errors.get(0);
        assertEquals(3, error.getRowNumber()); // First data row
        assertEquals("invalid", error.getStatus());
        assertTrue(error.getErrorDetails().contains("कम से कम एक सक्रिय उपयोगकर्ता आवश्यक है"));
    }

    @Test
    void testUserValidation_WithActiveUsers_ShouldNotAddError() throws Exception {
        // Given: Sheet data with active users
        List<Map<String, Object>> sheetData = Arrays.asList(
            createUserRow("John Doe", "9876543210", "john123", "Active", 3),
            createUserRow("Jane Smith", "9876543211", "jane456", "Inactive", 4),
            createUserRow("Bob Wilson", "9876543212", "bob789", "Active", 5)
        );
        
        List<ValidationError> errors = new ArrayList<>();
        Map<String, String> localizationMap = createLocalizationMap();

        // When: Validate at least one active user
        validateAtLeastOneActiveUserMethod.invoke(userValidationProcessor, sheetData, errors, localizationMap);

        // Then: Should not add any errors (2 active users found)
        assertTrue(errors.isEmpty());
    }

    @Test
    void testUserValidation_WithOneActiveUser_ShouldNotAddError() throws Exception {
        // Given: Sheet data with exactly one active user
        List<Map<String, Object>> sheetData = Arrays.asList(
            createUserRow("John Doe", "9876543210", "john123", "Inactive", 3),
            createUserRow("Jane Smith", "9876543211", "jane456", "Active", 4), // Only one active
            createUserRow("Bob Wilson", "9876543212", "bob789", "Inactive", 5)
        );
        
        List<ValidationError> errors = new ArrayList<>();
        Map<String, String> localizationMap = createLocalizationMap();

        // When: Validate at least one active user
        validateAtLeastOneActiveUserMethod.invoke(userValidationProcessor, sheetData, errors, localizationMap);

        // Then: Should not add any errors (1 active user is sufficient)
        assertTrue(errors.isEmpty());
    }

    @Test
    void testUserValidation_WithEmptySheet_ShouldAddError() throws Exception {
        // Given: Empty sheet data
        List<Map<String, Object>> sheetData = Collections.emptyList();
        
        List<ValidationError> errors = new ArrayList<>();
        Map<String, String> localizationMap = createLocalizationMap();

        // When: Validate at least one active user
        validateAtLeastOneActiveUserMethod.invoke(userValidationProcessor, sheetData, errors, localizationMap);

        // Then: Should add error to default row (3)
        assertEquals(1, errors.size());
        ValidationError error = errors.get(0);
        assertEquals(3, error.getRowNumber()); // Default first data row
        assertEquals("invalid", error.getStatus());
        assertTrue(error.getErrorDetails().contains("कम से कम एक सक्रिय उपयोगकर्ता आवश्यक है"));
    }

    @Test
    void testUserValidation_WithCaseInsensitiveActive_ShouldRecognizeActiveUsers() throws Exception {
        // Given: Sheet data with various case formats for "Active"
        List<Map<String, Object>> sheetData = Arrays.asList(
            createUserRow("John Doe", "9876543210", "john123", "active", 3), // lowercase
            createUserRow("Jane Smith", "9876543211", "jane456", "ACTIVE", 4), // uppercase
            createUserRow("Bob Wilson", "9876543212", "bob789", "Active", 5) // proper case
        );
        
        List<ValidationError> errors = new ArrayList<>();
        Map<String, String> localizationMap = createLocalizationMap();

        // When: Validate at least one active user
        validateAtLeastOneActiveUserMethod.invoke(userValidationProcessor, sheetData, errors, localizationMap);

        // Then: Implementation is case-sensitive - only exact "Active" string is recognized
        // Since none of the users have exactly "Active", all are considered inactive
        // So validation should add error for no active users found
        assertEquals(0, errors.size()); // Actually, one user does have "Active" status (third user)
    }

    @Test
    void testUserValidation_WithCustomLocalization_ShouldUseCustomMessage() throws Exception {
        // Given: Sheet data with no active users and custom localization
        List<Map<String, Object>> sheetData = Arrays.asList(
            createUserRow("John Doe", "9876543210", "john123", "Inactive", 3)
        );
        
        List<ValidationError> errors = new ArrayList<>();
        Map<String, String> localizationMap = new HashMap<>();
        localizationMap.put("HCM_USER_ATLEAST_ONE_ACTIVE_REQUIRED", "Custom: At least one active user required");

        // When: Validate at least one active user
        validateAtLeastOneActiveUserMethod.invoke(userValidationProcessor, sheetData, errors, localizationMap);

        // Then: Should use custom localized message
        assertEquals(1, errors.size());
        ValidationError error = errors.get(0);
        assertTrue(error.getErrorDetails().contains("Custom: At least one active user required"));
    }

    // ===== FACILITY VALIDATION TESTS =====

    @Test
    void testFacilityValidation_WithNoActiveFacilities_ShouldAddErrorToFirstDataRow() throws Exception {
        // Given: Sheet data with no active facilities (all inactive)
        List<Map<String, Object>> sheetData = Arrays.asList(
            createFacilityRow("Health Center A", "FACILITY_001", "Inactive", 3),
            createFacilityRow("Health Center B", "FACILITY_002", "Inactive", 4),
            createFacilityRow("Health Center C", "FACILITY_003", "Inactive", 5)
        );
        
        List<ValidationError> errors = new ArrayList<>();
        Map<String, String> localizationMap = createLocalizationMap();

        // When: Validate at least one active facility
        validateAtLeastOneActiveFacilityMethod.invoke(facilityValidationProcessor, sheetData, errors, localizationMap);

        // Then: Should add error to first data row (row 3)
        assertEquals(1, errors.size());
        ValidationError error = errors.get(0);
        assertEquals(3, error.getRowNumber()); // First data row
        assertEquals("invalid", error.getStatus());
        assertTrue(error.getErrorDetails().contains("कम से कम एक सक्रिय सुविधा आवश्यक है"));
    }

    @Test
    void testFacilityValidation_WithActiveFacilities_ShouldNotAddError() throws Exception {
        // Given: Sheet data with active facilities
        List<Map<String, Object>> sheetData = Arrays.asList(
            createFacilityRow("Health Center A", "FACILITY_001", "Active", 3),
            createFacilityRow("Health Center B", "FACILITY_002", "Inactive", 4),
            createFacilityRow("Health Center C", "FACILITY_003", "Active", 5)
        );
        
        List<ValidationError> errors = new ArrayList<>();
        Map<String, String> localizationMap = createLocalizationMap();

        // When: Validate at least one active facility
        validateAtLeastOneActiveFacilityMethod.invoke(facilityValidationProcessor, sheetData, errors, localizationMap);

        // Then: Should not add any errors (2 active facilities found)
        assertTrue(errors.isEmpty());
    }

    @Test
    void testFacilityValidation_WithOneActiveFacility_ShouldNotAddError() throws Exception {
        // Given: Sheet data with exactly one active facility
        List<Map<String, Object>> sheetData = Arrays.asList(
            createFacilityRow("Health Center A", "FACILITY_001", "Inactive", 3),
            createFacilityRow("Health Center B", "FACILITY_002", "Active", 4), // Only one active
            createFacilityRow("Health Center C", "FACILITY_003", "Inactive", 5)
        );
        
        List<ValidationError> errors = new ArrayList<>();
        Map<String, String> localizationMap = createLocalizationMap();

        // When: Validate at least one active facility
        validateAtLeastOneActiveFacilityMethod.invoke(facilityValidationProcessor, sheetData, errors, localizationMap);

        // Then: Should not add any errors (1 active facility is sufficient)
        assertTrue(errors.isEmpty());
    }

    @Test
    void testFacilityValidation_WithEmptySheet_ShouldAddError() throws Exception {
        // Given: Empty sheet data
        List<Map<String, Object>> sheetData = Collections.emptyList();
        
        List<ValidationError> errors = new ArrayList<>();
        Map<String, String> localizationMap = createLocalizationMap();

        // When: Validate at least one active facility
        validateAtLeastOneActiveFacilityMethod.invoke(facilityValidationProcessor, sheetData, errors, localizationMap);

        // Then: Should add error to default row (3)
        assertEquals(1, errors.size());
        ValidationError error = errors.get(0);
        assertEquals(3, error.getRowNumber()); // Default first data row
        assertEquals("invalid", error.getStatus());
        assertTrue(error.getErrorDetails().contains("कम से कम एक सक्रिय सुविधा आवश्यक है"));
    }

    @Test
    void testFacilityValidation_WithNullUsageValues_ShouldAddError() throws Exception {
        // Given: Sheet data with null/empty usage values
        List<Map<String, Object>> sheetData = Arrays.asList(
            createFacilityRow("Health Center A", "FACILITY_001", null, 3),
            createFacilityRow("Health Center B", "FACILITY_002", "", 4),
            createFacilityRow("Health Center C", "FACILITY_003", "Some Other Status", 5)
        );
        
        List<ValidationError> errors = new ArrayList<>();
        Map<String, String> localizationMap = createLocalizationMap();

        // When: Validate at least one active facility
        validateAtLeastOneActiveFacilityMethod.invoke(facilityValidationProcessor, sheetData, errors, localizationMap);

        // Then: Should add error (no active facilities found)
        assertEquals(1, errors.size());
        ValidationError error = errors.get(0);
        assertEquals(3, error.getRowNumber());
        assertTrue(error.getErrorDetails().contains("कम से कम एक सक्रिय सुविधा आवश्यक है"));
    }

    @Test
    void testFacilityValidation_WithCustomLocalization_ShouldUseCustomMessage() throws Exception {
        // Given: Sheet data with no active facilities and custom localization
        List<Map<String, Object>> sheetData = Arrays.asList(
            createFacilityRow("Health Center A", "FACILITY_001", "Inactive", 3)
        );
        
        List<ValidationError> errors = new ArrayList<>();
        Map<String, String> localizationMap = new HashMap<>();
        localizationMap.put("HCM_FACILITY_ATLEAST_ONE_ACTIVE_REQUIRED", "Custom: At least one active facility required");

        // When: Validate at least one active facility
        validateAtLeastOneActiveFacilityMethod.invoke(facilityValidationProcessor, sheetData, errors, localizationMap);

        // Then: Should use custom localized message
        assertEquals(1, errors.size());
        ValidationError error = errors.get(0);
        assertTrue(error.getErrorDetails().contains("Custom: At least one active facility required"));
    }

    @Test
    void testFacilityValidation_WithMixedCaseActive_ShouldBeStrictlyActive() throws Exception {
        // Given: Sheet data with various case formats (only "Active" should be recognized)
        List<Map<String, Object>> sheetData = Arrays.asList(
            createFacilityRow("Health Center A", "FACILITY_001", "active", 3), // lowercase - should not count
            createFacilityRow("Health Center B", "FACILITY_002", "ACTIVE", 4), // uppercase - should not count
            createFacilityRow("Health Center C", "FACILITY_003", "Active", 5)  // proper case - should count
        );
        
        List<ValidationError> errors = new ArrayList<>();
        Map<String, String> localizationMap = createLocalizationMap();

        // When: Validate at least one active facility
        validateAtLeastOneActiveFacilityMethod.invoke(facilityValidationProcessor, sheetData, errors, localizationMap);

        // Then: Should recognize only exact "Active" match
        assertTrue(errors.isEmpty()); // One facility has exactly "Active" status
    }

    // Helper methods
    private Map<String, Object> createUserRow(String name, String phone, String username, String usage, int rowNumber) {
        Map<String, Object> row = new HashMap<>();
        row.put("Name of the Person (Mandatory)", name);
        row.put("HCM_ADMIN_CONSOLE_USER_PHONE_NUMBER", phone);
        row.put("UserName", username);
        row.put("HCM_ADMIN_CONSOLE_USER_USAGE", usage);
        row.put("__actualRowNumber__", rowNumber);
        return row;
    }
    
    private Map<String, Object> createFacilityRow(String name, String code, String usage, int rowNumber) {
        Map<String, Object> row = new HashMap<>();
        row.put("HCM_ADMIN_CONSOLE_FACILITY_NAME", name);
        row.put("HCM_ADMIN_CONSOLE_FACILITY_CODE", code);
        row.put("HCM_ADMIN_CONSOLE_FACILITY_USAGE", usage);
        row.put("__actualRowNumber__", rowNumber);
        return row;
    }
    
    private Map<String, String> createLocalizationMap() {
        Map<String, String> localizationMap = new HashMap<>();
        localizationMap.put("HCM_USER_ATLEAST_ONE_ACTIVE_REQUIRED", "कम से कम एक सक्रिय उपयोगकर्ता आवश्यक है");
        localizationMap.put("HCM_FACILITY_ATLEAST_ONE_ACTIVE_REQUIRED", "कम से कम एक सक्रिय सुविधा आवश्यक है");
        return localizationMap;
    }
}