package org.egov.excelingestion.generator;

import org.egov.excelingestion.service.BoundaryService;
import org.egov.excelingestion.service.CampaignService;
import org.egov.excelingestion.service.FacilityService;
import org.egov.excelingestion.service.MDMSService;
import org.egov.excelingestion.util.BoundaryUtil;
import org.egov.excelingestion.util.CellProtectionManager;
import org.egov.excelingestion.util.ExcelDataPopulator;
import org.egov.excelingestion.util.HierarchicalBoundaryUtil;
import org.egov.excelingestion.util.SchemaColumnDefUtil;
import org.egov.excelingestion.exception.CustomExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive test cases for facility merging logic in FacilitySheetGenerator
 * Focus on testing mergeAndDeduplicateFacilities method and priority handling
 */
@ExtendWith(MockitoExtension.class)
class FacilitySheetGeneratorMergeTest {

    @Mock
    private MDMSService mdmsService;
    @Mock
    private CampaignService campaignService;
    @Mock
    private FacilityService facilityService;
    @Mock
    private BoundaryService boundaryService;
    @Mock
    private BoundaryUtil boundaryUtil;
    @Mock
    private CellProtectionManager cellProtectionManager;
    @Mock
    private CustomExceptionHandler exceptionHandler;
    @Mock
    private SchemaColumnDefUtil schemaColumnDefUtil;
    @Mock
    private ExcelDataPopulator excelDataPopulator;
    @Mock
    private HierarchicalBoundaryUtil hierarchicalBoundaryUtil;

    private FacilitySheetGenerator facilitySheetGenerator;
    private Method mergeAndDeduplicateMethod;

    @BeforeEach
    void setUp() throws Exception {
        facilitySheetGenerator = new FacilitySheetGenerator(
            mdmsService, campaignService, facilityService, boundaryService,
            boundaryUtil, cellProtectionManager, exceptionHandler,
            schemaColumnDefUtil, excelDataPopulator, hierarchicalBoundaryUtil
        );
        
        // Get private method for testing
        mergeAndDeduplicateMethod = FacilitySheetGenerator.class
            .getDeclaredMethod("mergeAndDeduplicateFacilities", List.class, List.class);
        mergeAndDeduplicateMethod.setAccessible(true);
    }

    @Test
    void testMerge_CampaignFacilityReplacePermanentFacility_SameId() throws Exception {
        // Given: Permanent facility and campaign facility with same ID
        List<Map<String, Object>> permanentFacilities = Arrays.asList(
            createFacility("FACILITY_001", "Primary Health Center", "Permanent", "Active")
        );
        
        List<Map<String, Object>> campaignFacilities = Arrays.asList(
            createFacility("FACILITY_001", "Updated Primary Health Center", "Temporary", "Inactive")
        );

        // When
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> result = (List<Map<String, Object>>) 
            mergeAndDeduplicateMethod.invoke(facilitySheetGenerator, permanentFacilities, campaignFacilities);

        // Then: Campaign facility should replace permanent facility (campaign takes priority)
        assertEquals(1, result.size());
        Map<String, Object> mergedFacility = result.get(0);
        assertEquals("FACILITY_001", mergedFacility.get("HCM_ADMIN_CONSOLE_FACILITY_CODE"));
        assertEquals("Updated Primary Health Center", mergedFacility.get("HCM_ADMIN_CONSOLE_FACILITY_NAME"));
        assertEquals("Temporary", mergedFacility.get("HCM_ADMIN_CONSOLE_FACILITY_STATUS"));
        assertEquals("Inactive", mergedFacility.get("HCM_ADMIN_CONSOLE_FACILITY_USAGE"));
    }

    @Test
    void testMerge_CampaignFacilityWithoutId_NewFacility() throws Exception {
        // Given: Permanent facility with ID and campaign facility without ID (new facility)
        List<Map<String, Object>> permanentFacilities = Arrays.asList(
            createFacility("FACILITY_001", "Existing Health Center", "Permanent", "Active")
        );
        
        List<Map<String, Object>> campaignFacilities = Arrays.asList(
            createFacility(null, "New Health Center", "Temporary", "Active") // No ID = new facility
        );

        // When
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> result = (List<Map<String, Object>>) 
            mergeAndDeduplicateMethod.invoke(facilitySheetGenerator, permanentFacilities, campaignFacilities);

        // Then: Both facilities should be included
        assertEquals(2, result.size());
        
        // Check permanent facility
        Map<String, Object> permanentFacility = result.stream()
            .filter(f -> "FACILITY_001".equals(f.get("HCM_ADMIN_CONSOLE_FACILITY_CODE")))
            .findFirst().orElse(null);
        assertNotNull(permanentFacility);
        assertEquals("Existing Health Center", permanentFacility.get("HCM_ADMIN_CONSOLE_FACILITY_NAME"));
        
        // Check new facility
        Map<String, Object> newFacility = result.stream()
            .filter(f -> f.get("HCM_ADMIN_CONSOLE_FACILITY_CODE") == null)
            .findFirst().orElse(null);
        assertNotNull(newFacility);
        assertEquals("New Health Center", newFacility.get("HCM_ADMIN_CONSOLE_FACILITY_NAME"));
    }

    @Test
    void testMerge_SameName_CampaignTakesPriorityOverPermanent() throws Exception {
        // Given: Permanent and campaign facilities with same name but different IDs
        List<Map<String, Object>> permanentFacilities = Arrays.asList(
            createFacility("PERM_001", "Community Health Center", "Permanent", "Active")
        );
        
        List<Map<String, Object>> campaignFacilities = Arrays.asList(
            createFacility(null, "Community Health Center", "Temporary", "Inactive") // Same name, no ID
        );

        // When
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> result = (List<Map<String, Object>>) 
            mergeAndDeduplicateMethod.invoke(facilitySheetGenerator, permanentFacilities, campaignFacilities);

        // Then: Campaign facility should replace permanent facility (name-based deduplication)
        assertEquals(1, result.size());
        Map<String, Object> mergedFacility = result.get(0);
        assertEquals("Community Health Center", mergedFacility.get("HCM_ADMIN_CONSOLE_FACILITY_NAME"));
        assertNull(mergedFacility.get("HCM_ADMIN_CONSOLE_FACILITY_CODE")); // Campaign facility has no ID
        assertEquals("Temporary", mergedFacility.get("HCM_ADMIN_CONSOLE_FACILITY_STATUS"));
        assertEquals("Inactive", mergedFacility.get("HCM_ADMIN_CONSOLE_FACILITY_USAGE"));
    }

    @Test
    void testMerge_MultiplePermanentFacilities_NoDuplicates() throws Exception {
        // Given: Multiple permanent facilities with different IDs
        List<Map<String, Object>> permanentFacilities = Arrays.asList(
            createFacility("FACILITY_001", "Health Center 1", "Permanent", "Active"),
            createFacility("FACILITY_002", "Health Center 2", "Permanent", "Active"),
            createFacility("FACILITY_003", "Health Center 3", "Permanent", "Inactive")
        );
        
        List<Map<String, Object>> campaignFacilities = new ArrayList<>();

        // When
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> result = (List<Map<String, Object>>) 
            mergeAndDeduplicateMethod.invoke(facilitySheetGenerator, permanentFacilities, campaignFacilities);

        // Then: All permanent facilities should be included
        assertEquals(3, result.size());
        assertEquals("FACILITY_001", result.get(0).get("HCM_ADMIN_CONSOLE_FACILITY_CODE"));
        assertEquals("FACILITY_002", result.get(1).get("HCM_ADMIN_CONSOLE_FACILITY_CODE"));
        assertEquals("FACILITY_003", result.get(2).get("HCM_ADMIN_CONSOLE_FACILITY_CODE"));
    }

    @Test
    void testMerge_DuplicateNameInPermanentFacilities_FirstWins() throws Exception {
        // Given: Two permanent facilities with same name but different IDs
        List<Map<String, Object>> permanentFacilities = Arrays.asList(
            createFacility("FACILITY_001", "Duplicate Health Center", "Permanent", "Active"),
            createFacility("FACILITY_002", "Duplicate Health Center", "Permanent", "Inactive") // Same name
        );
        
        List<Map<String, Object>> campaignFacilities = new ArrayList<>();

        // When
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> result = (List<Map<String, Object>>) 
            mergeAndDeduplicateMethod.invoke(facilitySheetGenerator, permanentFacilities, campaignFacilities);

        // Then: Only first facility should be kept (first occurrence wins)
        assertEquals(1, result.size());
        Map<String, Object> mergedFacility = result.get(0);
        assertEquals("FACILITY_001", mergedFacility.get("HCM_ADMIN_CONSOLE_FACILITY_CODE"));
        assertEquals("Duplicate Health Center", mergedFacility.get("HCM_ADMIN_CONSOLE_FACILITY_NAME"));
        assertEquals("Active", mergedFacility.get("HCM_ADMIN_CONSOLE_FACILITY_USAGE"));
    }

    @Test
    void testMerge_DuplicateNameInCampaignFacilities_FirstWins() throws Exception {
        // Given: Two campaign facilities with same name (both without IDs)
        List<Map<String, Object>> permanentFacilities = new ArrayList<>();
        
        List<Map<String, Object>> campaignFacilities = Arrays.asList(
            createFacility(null, "New Health Center", "Temporary", "Active"),
            createFacility(null, "New Health Center", "Temporary", "Inactive") // Same name
        );

        // When
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> result = (List<Map<String, Object>>) 
            mergeAndDeduplicateMethod.invoke(facilitySheetGenerator, permanentFacilities, campaignFacilities);

        // Then: Only first facility should be kept
        assertEquals(1, result.size());
        Map<String, Object> mergedFacility = result.get(0);
        assertEquals("New Health Center", mergedFacility.get("HCM_ADMIN_CONSOLE_FACILITY_NAME"));
        assertEquals("Active", mergedFacility.get("HCM_ADMIN_CONSOLE_FACILITY_USAGE"));
    }

    @Test
    void testMerge_ComplexScenario_MixedIdAndNameBasedMerging() throws Exception {
        // Given: Complex scenario with multiple merge cases
        List<Map<String, Object>> permanentFacilities = Arrays.asList(
            createFacility("FACILITY_001", "Health Center A", "Permanent", "Active"),
            createFacility("FACILITY_002", "Health Center B", "Permanent", "Active"),
            createFacility("FACILITY_003", "Health Center C", "Permanent", "Inactive")
        );
        
        List<Map<String, Object>> campaignFacilities = Arrays.asList(
            createFacility("FACILITY_001", "Updated Health Center A", "Temporary", "Inactive"), // Same ID - should replace
            createFacility(null, "Health Center B", "Temporary", "Active"), // Same name - should replace
            createFacility("FACILITY_004", "Health Center D", "Temporary", "Active"), // New ID
            createFacility(null, "Health Center E", "Temporary", "Active") // Completely new
        );

        // When
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> result = (List<Map<String, Object>>) 
            mergeAndDeduplicateMethod.invoke(facilitySheetGenerator, permanentFacilities, campaignFacilities);

        // Then: Should have 5 unique facilities
        assertEquals(5, result.size());
        
        // Check FACILITY_001 - replaced by campaign
        Map<String, Object> facility001 = findFacilityByCode(result, "FACILITY_001");
        assertNotNull(facility001);
        assertEquals("Updated Health Center A", facility001.get("HCM_ADMIN_CONSOLE_FACILITY_NAME"));
        assertEquals("Temporary", facility001.get("HCM_ADMIN_CONSOLE_FACILITY_STATUS"));
        
        // Check Health Center B - replaced by campaign (name-based)
        Map<String, Object> facilityB = findFacilityByName(result, "Health Center B");
        assertNotNull(facilityB);
        assertNull(facilityB.get("HCM_ADMIN_CONSOLE_FACILITY_CODE")); // Campaign facility has no ID
        assertEquals("Temporary", facilityB.get("HCM_ADMIN_CONSOLE_FACILITY_STATUS"));
        
        // Check FACILITY_003 - permanent facility kept
        Map<String, Object> facility003 = findFacilityByCode(result, "FACILITY_003");
        assertNotNull(facility003);
        assertEquals("Health Center C", facility003.get("HCM_ADMIN_CONSOLE_FACILITY_NAME"));
        assertEquals("Permanent", facility003.get("HCM_ADMIN_CONSOLE_FACILITY_STATUS"));
        
        // Check FACILITY_004 - new campaign facility
        Map<String, Object> facility004 = findFacilityByCode(result, "FACILITY_004");
        assertNotNull(facility004);
        assertEquals("Health Center D", facility004.get("HCM_ADMIN_CONSOLE_FACILITY_NAME"));
        
        // Check Health Center E - new campaign facility
        Map<String, Object> facilityE = findFacilityByName(result, "Health Center E");
        assertNotNull(facilityE);
        assertNull(facilityE.get("HCM_ADMIN_CONSOLE_FACILITY_CODE"));
    }

    @Test
    void testMerge_EmptyLists_EmptyResult() throws Exception {
        // Given: Empty lists
        List<Map<String, Object>> permanentFacilities = new ArrayList<>();
        List<Map<String, Object>> campaignFacilities = new ArrayList<>();

        // When
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> result = (List<Map<String, Object>>) 
            mergeAndDeduplicateMethod.invoke(facilitySheetGenerator, permanentFacilities, campaignFacilities);

        // Then: Empty result
        assertTrue(result.isEmpty());
    }

    @Test
    void testMerge_NullAndEmptyNames_FilteredOut() throws Exception {
        // Given: Test the filtering behavior - facilities need valid IDs AND valid names to survive
        List<Map<String, Object>> permanentFacilities = Arrays.asList(
            createFacility("FACILITY_001", "Valid Permanent", "Permanent", "Active"), // Valid ID + name = KEPT
            createFacility("FACILITY_002", "", "Permanent", "Active"), // Valid ID + empty name = FILTERED OUT in step 3
            createFacility("FACILITY_003", null, "Permanent", "Active"), // Valid ID + null name = FILTERED OUT in step 3
            createFacility("", "Some Name", "Permanent", "Active"), // Empty ID = FILTERED OUT in step 1
            createFacility(null, "Another Name", "Permanent", "Active") // Null ID = FILTERED OUT in step 1
        );
        
        List<Map<String, Object>> campaignFacilities = Arrays.asList(
            createFacility(null, "Valid Campaign", "Temporary", "Active"), // No ID + valid name = KEPT
            createFacility("CAMP_001", "Campaign With ID", "Temporary", "Active"), // Valid ID + name = KEPT  
            createFacility(null, "", "Temporary", "Active"), // No ID + empty name = FILTERED OUT in step 2
            createFacility(null, null, "Temporary", "Active") // No ID + null name = FILTERED OUT in step 2
        );

        // When
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> result = (List<Map<String, Object>>) 
            mergeAndDeduplicateMethod.invoke(facilitySheetGenerator, permanentFacilities, campaignFacilities);

        // Then: Only 3 facilities should survive all filtering stages
        assertEquals(3, result.size()); 
        
        // Verify which facilities made it through
        assertTrue(result.stream().anyMatch(f -> 
            "FACILITY_001".equals(f.get("HCM_ADMIN_CONSOLE_FACILITY_CODE")) &&
            "Valid Permanent".equals(f.get("HCM_ADMIN_CONSOLE_FACILITY_NAME"))
        ), "Permanent facility with valid ID and name should be kept");
        
        assertTrue(result.stream().anyMatch(f -> 
            "CAMP_001".equals(f.get("HCM_ADMIN_CONSOLE_FACILITY_CODE")) &&
            "Campaign With ID".equals(f.get("HCM_ADMIN_CONSOLE_FACILITY_NAME"))
        ), "Campaign facility with valid ID and name should be kept");
        
        assertTrue(result.stream().anyMatch(f -> 
            f.get("HCM_ADMIN_CONSOLE_FACILITY_CODE") == null &&
            "Valid Campaign".equals(f.get("HCM_ADMIN_CONSOLE_FACILITY_NAME"))
        ), "Campaign facility with valid name (no ID) should be kept");
    }

    @Test
    void testMerge_PreserveInsertionOrder() throws Exception {
        // Given: Multiple facilities to test order preservation
        List<Map<String, Object>> permanentFacilities = Arrays.asList(
            createFacility("FACILITY_001", "First Facility", "Permanent", "Active"),
            createFacility("FACILITY_002", "Second Facility", "Permanent", "Active"),
            createFacility("FACILITY_003", "Third Facility", "Permanent", "Active")
        );
        
        List<Map<String, Object>> campaignFacilities = Arrays.asList(
            createFacility("FACILITY_004", "Fourth Facility", "Temporary", "Active"),
            createFacility(null, "Fifth Facility", "Temporary", "Active")
        );

        // When
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> result = (List<Map<String, Object>>) 
            mergeAndDeduplicateMethod.invoke(facilitySheetGenerator, permanentFacilities, campaignFacilities);

        // Then: Order should be preserved (permanent facilities first, then campaign)
        assertEquals(5, result.size());
        assertEquals("First Facility", result.get(0).get("HCM_ADMIN_CONSOLE_FACILITY_NAME"));
        assertEquals("Second Facility", result.get(1).get("HCM_ADMIN_CONSOLE_FACILITY_NAME"));
        assertEquals("Third Facility", result.get(2).get("HCM_ADMIN_CONSOLE_FACILITY_NAME"));
        assertEquals("Fourth Facility", result.get(3).get("HCM_ADMIN_CONSOLE_FACILITY_NAME"));
        assertEquals("Fifth Facility", result.get(4).get("HCM_ADMIN_CONSOLE_FACILITY_NAME"));
    }

    // Helper methods
    private Map<String, Object> createFacility(String code, String name, String status, String usage) {
        Map<String, Object> facility = new HashMap<>();
        facility.put("HCM_ADMIN_CONSOLE_FACILITY_CODE", code);
        facility.put("HCM_ADMIN_CONSOLE_FACILITY_NAME", name);
        facility.put("HCM_ADMIN_CONSOLE_FACILITY_STATUS", status);
        facility.put("HCM_ADMIN_CONSOLE_FACILITY_USAGE", usage);
        return facility;
    }
    
    private Map<String, Object> findFacilityByCode(List<Map<String, Object>> facilities, String code) {
        return facilities.stream()
            .filter(f -> code.equals(f.get("HCM_ADMIN_CONSOLE_FACILITY_CODE")))
            .findFirst()
            .orElse(null);
    }
    
    private Map<String, Object> findFacilityByName(List<Map<String, Object>> facilities, String name) {
        return facilities.stream()
            .filter(f -> name.equals(f.get("HCM_ADMIN_CONSOLE_FACILITY_NAME")))
            .findFirst()
            .orElse(null);
    }
}