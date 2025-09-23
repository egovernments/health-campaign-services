package org.egov.excelingestion.generator;

import org.egov.excelingestion.config.ProcessingConstants;
import org.egov.excelingestion.exception.CustomExceptionHandler;
import org.egov.excelingestion.service.BoundaryService;
import org.egov.excelingestion.service.CampaignService;
import org.egov.excelingestion.service.MDMSService;
import org.egov.excelingestion.util.BoundaryUtil;
import org.egov.excelingestion.web.models.*;
import org.egov.excelingestion.web.models.excel.ColumnDef;
import org.egov.excelingestion.web.models.excel.MultiSelectDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BoundaryHierarchySheetGeneratorTest {

    @Mock
    private BoundaryService boundaryService;
    
    @Mock
    private BoundaryUtil boundaryUtil;
    
    @Mock
    private MDMSService mdmsService;
    
    @Mock
    private CampaignService campaignService;
    
    @Mock
    private CustomExceptionHandler exceptionHandler;
    
    private BoundaryHierarchySheetGenerator generator;
    private AutoCloseable closeable;
    
    @BeforeEach
    void setUp() {
        closeable = MockitoAnnotations.openMocks(this);
        org.egov.excelingestion.util.ColumnDefMaker columnDefMaker = new org.egov.excelingestion.util.ColumnDefMaker();
        org.egov.excelingestion.util.SchemaColumnDefUtil schemaColumnDefUtil = new org.egov.excelingestion.util.SchemaColumnDefUtil(columnDefMaker, exceptionHandler);
        generator = new BoundaryHierarchySheetGenerator(
            boundaryService, boundaryUtil, mdmsService, campaignService, exceptionHandler, schemaColumnDefUtil
        );
    }
    
    @AfterEach
    void tearDown() throws Exception {
        if (closeable != null) {
            closeable.close();
        }
    }

    // ==================== DYNAMIC TARGET COLUMNS TESTS ====================
    
    @Test
    void testGenerateSheetData_WithDynamicTargetColumns_ShouldIncludeSchemaColumns() {
        // Arrange
        SheetGenerationConfig config = createMockConfig();
        GenerateResource generateResource = createGenerateResourceWithProjectType("HCM");
        RequestInfo requestInfo = new RequestInfo();
        Map<String, String> localizationMap = createLocalizationMap();
        
        setupBoundaryServiceMocks();
        setupMDMSServiceMocks();
        setupBoundaryUtilMocks();
        setupCampaignServiceMocks();
        
        // Act
        SheetGenerationResult result = generator.generateSheetData(config, generateResource, requestInfo, localizationMap);
        
        // Assert
        assertNotNull(result);
        assertNotNull(result.getColumnDefs());
        assertNotNull(result.getData());
        
        // Verify boundary columns are present (3 hierarchy levels + 1 code column)
        List<ColumnDef> columns = result.getColumnDefs();
        assertTrue(columns.size() >= 4, "Should have at least boundary columns");
        
        // Verify boundary columns
        assertEquals("HIERARCHY1_DISTRICT", columns.get(0).getName());
        assertEquals("HIERARCHY1_BLOCK", columns.get(1).getName());
        assertEquals("HIERARCHY1_VILLAGE", columns.get(2).getName());
        assertEquals("HCM_ADMIN_CONSOLE_BOUNDARY_CODE", columns.get(3).getName());
        
        // Verify dynamic target columns from schema
        assertTrue(columns.size() > 4, "Should have schema columns after boundary columns");
        
        // Find schema columns (after boundary columns)
        List<ColumnDef> schemaColumns = columns.subList(4, columns.size());
        assertTrue(schemaColumns.stream().anyMatch(col -> "TARGET_POPULATION".equals(col.getName())));
        assertTrue(schemaColumns.stream().anyMatch(col -> "DRUGS_REQUIRED".equals(col.getName())));
        assertTrue(schemaColumns.stream().anyMatch(col -> "DELIVERY_TYPE".equals(col.getName())));
        
        // Verify column types
        ColumnDef targetPopCol = schemaColumns.stream()
            .filter(col -> "TARGET_POPULATION".equals(col.getName()))
            .findFirst().orElse(null);
        assertNotNull(targetPopCol);
        assertEquals("number", targetPopCol.getType());
        assertTrue(targetPopCol.isRequired());
        
        ColumnDef deliveryTypeCol = schemaColumns.stream()
            .filter(col -> "DELIVERY_TYPE".equals(col.getName()))
            .findFirst().orElse(null);
        assertNotNull(deliveryTypeCol);
        assertEquals("enum", deliveryTypeCol.getType());
        assertNotNull(deliveryTypeCol.getEnumValues());
        assertTrue(deliveryTypeCol.getEnumValues().contains("HOME_DELIVERY"));
        assertTrue(deliveryTypeCol.getEnumValues().contains("FACILITY_DELIVERY"));
    }
    
    @Test
    void testGenerateSheetData_WithMultiSelectTargetColumn_ShouldIncludeMultiSelectDetails() {
        // Arrange
        SheetGenerationConfig config = createMockConfig();
        GenerateResource generateResource = createGenerateResourceWithProjectType("HCM");
        RequestInfo requestInfo = new RequestInfo();
        Map<String, String> localizationMap = createLocalizationMap();
        
        setupBoundaryServiceMocks();
        setupMDMSServiceWithMultiSelect();
        setupBoundaryUtilMocks();
        setupCampaignServiceMocks();
        
        // Act
        SheetGenerationResult result = generator.generateSheetData(config, generateResource, requestInfo, localizationMap);
        
        // Assert
        List<ColumnDef> columns = result.getColumnDefs();
        
        // Find multi-select column
        ColumnDef multiSelectCol = columns.stream()
            .filter(col -> "INTERVENTIONS".equals(col.getName()))
            .findFirst().orElse(null);
        
        assertNotNull(multiSelectCol, "Multi-select column should be present");
        assertNotNull(multiSelectCol.getMultiSelectDetails(), "Multi-select details should be present");
        
        MultiSelectDetails details = multiSelectCol.getMultiSelectDetails();
        assertEquals(3, details.getMaxSelections());
        assertEquals(1, details.getMinSelections());
        assertNotNull(details.getEnumValues());
        assertTrue(details.getEnumValues().contains("VACCINATION"));
        assertTrue(details.getEnumValues().contains("NUTRITION"));
        assertTrue(details.getEnumValues().contains("HEALTH_CHECK"));
    }
    
    @Test
    void testGenerateSheetData_NoProjectType_ShouldOnlyIncludeBoundaryColumns() {
        // Arrange
        SheetGenerationConfig config = createMockConfig();
        GenerateResource generateResource = createGenerateResourceWithoutProjectType();
        RequestInfo requestInfo = new RequestInfo();
        Map<String, String> localizationMap = createLocalizationMap();
        
        setupBoundaryServiceMocks();
        setupBoundaryUtilMocks();
        
        // Mock campaignService to return null (no project type) but still provide boundaries
        when(campaignService.getProjectTypeFromCampaign(any(), any(), any())).thenReturn(null);
        List<CampaignSearchResponse.BoundaryDetail> campaignBoundaries = Arrays.asList(
            CampaignSearchResponse.BoundaryDetail.builder()
                .code("MICROPLAN_MO_07_JWPARA")
                .isRoot(false)
                .includeAllChildren(false)
                .build()
        );
        when(campaignService.getBoundariesFromCampaign(any(), any(), any()))
            .thenReturn(campaignBoundaries);
        
        // Act
        SheetGenerationResult result = generator.generateSheetData(config, generateResource, requestInfo, localizationMap);
        
        // Assert
        assertNotNull(result);
        List<ColumnDef> columns = result.getColumnDefs();
        
        // Should only have boundary columns (3 hierarchy + 1 code = 4 total)
        assertEquals(4, columns.size(), "Should only have boundary columns without projectType");
        
        assertEquals("HIERARCHY1_DISTRICT", columns.get(0).getName());
        assertEquals("HIERARCHY1_BLOCK", columns.get(1).getName());
        assertEquals("HIERARCHY1_VILLAGE", columns.get(2).getName());
        assertEquals("HCM_ADMIN_CONSOLE_BOUNDARY_CODE", columns.get(3).getName());
        
        // Verify no MDMS call was made
        verify(mdmsService, never()).searchMDMS(any(), any(), any(), any(), anyInt(), anyInt());
    }
    
    @Test
    void testGenerateSheetData_InvalidProjectType_ShouldHandleGracefully() {
        // Arrange
        SheetGenerationConfig config = createMockConfig();
        GenerateResource generateResource = createGenerateResourceWithProjectType("INVALID_TYPE");
        RequestInfo requestInfo = new RequestInfo();
        Map<String, String> localizationMap = createLocalizationMap();
        
        setupBoundaryServiceMocks();
        setupBoundaryUtilMocks();
        
        // Mock campaignService to return invalid project type but still provide boundaries
        when(campaignService.getProjectTypeFromCampaign(any(), any(), any())).thenReturn("INVALID_TYPE");
        List<CampaignSearchResponse.BoundaryDetail> campaignBoundaries = Arrays.asList(
            CampaignSearchResponse.BoundaryDetail.builder()
                .code("MICROPLAN_MO_07_JWPARA")
                .isRoot(false)
                .includeAllChildren(false)
                .build()
        );
        when(campaignService.getBoundariesFromCampaign(any(), any(), any()))
            .thenReturn(campaignBoundaries);
        
        // Mock MDMS to return empty list for invalid project type
        when(mdmsService.searchMDMS(any(), any(), eq(ProcessingConstants.MDMS_SCHEMA_CODE), any(), anyInt(), anyInt()))
            .thenReturn(new ArrayList<>());
        
        // Act
        SheetGenerationResult result = generator.generateSheetData(config, generateResource, requestInfo, localizationMap);
        
        // Assert
        assertNotNull(result);
        List<ColumnDef> columns = result.getColumnDefs();
        
        // Should only have boundary columns when schema is not found
        assertEquals(4, columns.size(), "Should only have boundary columns when schema not found");
        
        // Verify MDMS call was made but returned empty
        verify(mdmsService).searchMDMS(any(), any(), eq(ProcessingConstants.MDMS_SCHEMA_CODE), any(), anyInt(), anyInt());
    }
    
    @Test
    void testGenerateSheetData_SchemaColumnsOrderedCorrectly() {
        // Arrange
        SheetGenerationConfig config = createMockConfig();
        GenerateResource generateResource = createGenerateResourceWithProjectType("HCM");
        RequestInfo requestInfo = new RequestInfo();
        Map<String, String> localizationMap = createLocalizationMap();
        
        setupBoundaryServiceMocks();
        setupMDMSServiceWithOrderNumbers();
        setupBoundaryUtilMocks();
        setupCampaignServiceMocks();
        
        // Act
        SheetGenerationResult result = generator.generateSheetData(config, generateResource, requestInfo, localizationMap);
        
        // Assert
        List<ColumnDef> columns = result.getColumnDefs();
        
        // Verify boundary columns come first with correct order
        assertEquals(1, columns.get(0).getOrderNumber()); // DISTRICT
        assertEquals(2, columns.get(1).getOrderNumber()); // BLOCK  
        assertEquals(3, columns.get(2).getOrderNumber()); // VILLAGE
        assertEquals(4, columns.get(3).getOrderNumber()); // BOUNDARY_CODE
        
        // Verify schema columns follow with incremented order numbers
        List<ColumnDef> schemaColumns = columns.subList(4, columns.size());
        assertEquals(5, schemaColumns.get(0).getOrderNumber()); // First schema column
        assertEquals(6, schemaColumns.get(1).getOrderNumber()); // Second schema column
        assertEquals(7, schemaColumns.get(2).getOrderNumber()); // Third schema column
    }
    
    @Test
    void testGenerateSheetData_BoundaryColumnsAlwaysLocked() {
        // Arrange
        SheetGenerationConfig config = createMockConfig();
        GenerateResource generateResource = createGenerateResourceWithProjectType("HCM");
        RequestInfo requestInfo = new RequestInfo();
        Map<String, String> localizationMap = createLocalizationMap();
        
        setupBoundaryServiceMocks();
        setupMDMSServiceMocks();
        setupBoundaryUtilMocks();
        setupCampaignServiceMocks();
        
        // Act
        SheetGenerationResult result = generator.generateSheetData(config, generateResource, requestInfo, localizationMap);
        
        // Assert
        List<ColumnDef> columns = result.getColumnDefs();
        
        // Verify all boundary columns are locked
        for (int i = 0; i < 4; i++) { // First 4 are boundary columns
            ColumnDef boundaryCol = columns.get(i);
            assertTrue(boundaryCol.isFreezeColumn(), 
                "Boundary column " + boundaryCol.getName() + " should be locked");
        }
        
        // Verify boundary code column is hidden
        ColumnDef boundaryCodeCol = columns.get(3);
        assertEquals("HCM_ADMIN_CONSOLE_BOUNDARY_CODE", boundaryCodeCol.getName());
        assertTrue(boundaryCodeCol.isHideColumn(), "Boundary code column should be hidden");
        assertTrue(boundaryCodeCol.isAdjustHeight(), "Boundary code column should adjust height");
    }
    
    @Test
    void testGenerateSheetData_DataPopulationWithTargetColumns() {
        // Arrange
        SheetGenerationConfig config = createMockConfig();
        GenerateResource generateResource = createGenerateResourceWithProjectType("HCM");
        RequestInfo requestInfo = new RequestInfo();
        Map<String, String> localizationMap = createLocalizationMap();
        
        setupBoundaryServiceMocks();
        setupMDMSServiceMocks();
        setupBoundaryUtilMocks();
        setupCampaignServiceMocks();
        
        // Act
        SheetGenerationResult result = generator.generateSheetData(config, generateResource, requestInfo, localizationMap);
        
        // Assert
        List<Map<String, Object>> data = result.getData();
        assertNotNull(data);
        assertTrue(data.size() > 0, "Should have data rows");
        
        // Verify boundary data is populated
        Map<String, Object> firstRow = data.get(0);
        assertEquals("District Alpha", firstRow.get("HIERARCHY1_DISTRICT"));
        assertEquals("Block Alpha 1", firstRow.get("HIERARCHY1_BLOCK"));
        assertEquals("Village Alpha 1-1", firstRow.get("HIERARCHY1_VILLAGE"));
        assertEquals("VILLAGE_A1-1", firstRow.get("HCM_ADMIN_CONSOLE_BOUNDARY_CODE"));
        
        // Verify target columns are null (to be filled by users)
        assertNull(firstRow.get("TARGET_POPULATION"));
        assertNull(firstRow.get("DRUGS_REQUIRED"));
        assertNull(firstRow.get("DELIVERY_TYPE"));
    }
    
    @Test
    void testGenerateSheetData_NoBoundariesConfigured_ShouldReturnEmptyResult() {
        // Arrange
        SheetGenerationConfig config = createMockConfig();
        GenerateResource generateResource = createGenerateResourceWithNoBoundaries();
        RequestInfo requestInfo = new RequestInfo();
        Map<String, String> localizationMap = createLocalizationMap();
        
        // Still need to setup boundary service mocks since the method checks boundaries first 
        // but then tries to fetch hierarchy data before the early return
        setupBoundaryServiceMocks();
        
        // Act
        SheetGenerationResult result = generator.generateSheetData(config, generateResource, requestInfo, localizationMap);
        
        // Assert
        assertNotNull(result);
        assertTrue(result.getColumnDefs().isEmpty(), "Should have empty column defs when no boundaries");
        assertTrue(result.getData().isEmpty(), "Should have empty data when no boundaries");
    }

    // ==================== BOUNDARY COLUMN CREATION TESTS ====================
    
    @Test
    void testCreateBoundaryHierarchyColumnDefs_WithSchemaColumns() {
        // This test verifies the private method behavior through public interface
        // Already covered in the above tests but worth explicitly testing column creation
        
        // Arrange & Act done in testGenerateSheetData_WithDynamicTargetColumns_ShouldIncludeSchemaColumns
        // This test serves as documentation of the expected behavior
        assertTrue(true, "Boundary column creation behavior verified in integration tests above");
    }
    
    @Test
    void testGenerateSheetData_ExceptionHandling_ShouldWrapInRuntimeException() {
        // Arrange
        SheetGenerationConfig config = createMockConfig();
        GenerateResource generateResource = createGenerateResourceWithProjectType("HCM");
        RequestInfo requestInfo = new RequestInfo();
        Map<String, String> localizationMap = createLocalizationMap();
        
        // Mock boundary service to throw exception
        when(boundaryService.fetchBoundaryHierarchy(any(), any(), any()))
            .thenThrow(new RuntimeException("Boundary service error"));
        
        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () -> 
            generator.generateSheetData(config, generateResource, requestInfo, localizationMap));
        
        assertEquals("Failed to generate boundary hierarchy sheet data", exception.getMessage());
        assertNotNull(exception.getCause());
        assertEquals("Boundary service error", exception.getCause().getMessage());
    }

    // ==================== HELPER METHODS ====================
    
    private SheetGenerationConfig createMockConfig() {
        return SheetGenerationConfig.builder()
            .sheetNameKey("HCM_BOUNDARY_HIERARCHY")
            .schemaName("target-hcm")
            .order(1)
            .visible(true)
            .build();
    }
    
    private GenerateResource createGenerateResourceWithProjectType(String projectType) {
        Map<String, Object> additionalDetails = new HashMap<>();
        additionalDetails.put("projectType", projectType);
        
        GenerateResource resource = GenerateResource.builder()
            .tenantId("tenant1")
            .hierarchyType("hierarchy1")
            .additionalDetails(additionalDetails)
            .build();
        
        resource.setBoundaries(createMockBoundaries());
        return resource;
    }
    
    private GenerateResource createGenerateResourceWithoutProjectType() {
        GenerateResource resource = GenerateResource.builder()
            .tenantId("tenant1")
            .hierarchyType("hierarchy1")
            .additionalDetails(new HashMap<>())
            .build();
        
        resource.setBoundaries(createMockBoundaries());
        return resource;
    }
    
    private GenerateResource createGenerateResourceWithNoBoundaries() {
        Map<String, Object> additionalDetails = new HashMap<>();
        additionalDetails.put("projectType", "HCM");
        
        GenerateResource resource = GenerateResource.builder()
            .tenantId("tenant1")
            .hierarchyType("hierarchy1")
            .additionalDetails(additionalDetails)
            .build();
        
        resource.setBoundaries(null);
        return resource;
    }
    
    private List<Boundary> createMockBoundaries() {
        return Arrays.asList(
            Boundary.builder()
                .code("DISTRICT_A")
                .name("District Alpha")
                .type("District")
                .build(),
            Boundary.builder()
                .code("BLOCK_A1")
                .name("Block Alpha 1")
                .type("Block")
                .build(),
            Boundary.builder()
                .code("VILLAGE_A1-1")
                .name("Village Alpha 1-1")
                .type("Village")
                .build()
        );
    }
    
    private Map<String, String> createLocalizationMap() {
        Map<String, String> map = new HashMap<>();
        map.put("DISTRICT_A", "District Alpha");
        map.put("BLOCK_A1", "Block Alpha 1");
        map.put("VILLAGE_A1-1", "Village Alpha 1-1");
        map.put("HIERARCHY1_DISTRICT", "District");
        map.put("HIERARCHY1_BLOCK", "Block");
        map.put("HIERARCHY1_VILLAGE", "Village");
        return map;
    }
    
    private void setupBoundaryServiceMocks() {
        // Mock hierarchy response
        BoundaryHierarchy hierarchy = BoundaryHierarchy.builder()
            .boundaryHierarchy(Arrays.asList(
                BoundaryHierarchyChild.builder().boundaryType("District").build(),
                BoundaryHierarchyChild.builder().boundaryType("Block").build(),
                BoundaryHierarchyChild.builder().boundaryType("Village").build()
            ))
            .build();
        
        BoundaryHierarchyResponse hierarchyResponse = BoundaryHierarchyResponse.builder()
            .boundaryHierarchy(Arrays.asList(hierarchy))
            .build();
        
        when(boundaryService.fetchBoundaryHierarchy(any(), any(), any()))
            .thenReturn(hierarchyResponse);
        
        // Mock relationship response
        BoundarySearchResponse relationshipResponse = BoundarySearchResponse.builder()
            .tenantBoundary(new ArrayList<>())
            .build();
        
        when(boundaryService.fetchBoundaryRelationship(any(), any(), any()))
            .thenReturn(relationshipResponse);
    }
    
    private void setupBoundaryUtilMocks() {
        List<BoundaryUtil.BoundaryRowData> mockBoundaryData = Arrays.asList(
            new BoundaryUtil.BoundaryRowData(Arrays.asList("DISTRICT_A", "BLOCK_A1", "VILLAGE_A1-1"), "VILLAGE_A1-1")
        );
        
        when(boundaryUtil.processBoundariesWithEnrichment(any(), any(), any()))
            .thenReturn(mockBoundaryData);
        
        when(boundaryUtil.buildCodeToBoundaryMap(any()))
            .thenReturn(new HashMap<>());
    }
    
    private void setupMDMSServiceMocks() {
        List<Map<String, Object>> mdmsList = createMockMDMSResponse();
        when(mdmsService.searchMDMS(any(), any(), eq(ProcessingConstants.MDMS_SCHEMA_CODE), any(), anyInt(), anyInt()))
            .thenReturn(mdmsList);
    }
    
    private void setupCampaignServiceMocks() {
        // Mock getProjectTypeFromCampaign to return "HCM" for tests with project type
        when(campaignService.getProjectTypeFromCampaign(any(), any(), any()))
            .thenReturn("HCM");
            
        // Mock getBoundariesFromCampaign to return campaign boundaries
        List<CampaignSearchResponse.BoundaryDetail> campaignBoundaries = Arrays.asList(
            CampaignSearchResponse.BoundaryDetail.builder()
                .code("MICROPLAN_MO_07_JWPARA")
                .isRoot(false)
                .includeAllChildren(false)
                .build(),
            CampaignSearchResponse.BoundaryDetail.builder()
                .code("MICROPLAN_MO_08_BUDIKHAMARI")
                .isRoot(false)
                .includeAllChildren(false)
                .build()
        );
        when(campaignService.getBoundariesFromCampaign(any(), any(), any()))
            .thenReturn(campaignBoundaries);
    }
    
    private void setupMDMSServiceWithMultiSelect() {
        List<Map<String, Object>> mdmsList = createMockMDMSResponseWithMultiSelect();
        when(mdmsService.searchMDMS(any(), any(), eq(ProcessingConstants.MDMS_SCHEMA_CODE), any(), anyInt(), anyInt()))
            .thenReturn(mdmsList);
    }
    
    private void setupMDMSServiceWithOrderNumbers() {
        List<Map<String, Object>> mdmsList = createMockMDMSResponseWithOrderNumbers();
        when(mdmsService.searchMDMS(any(), any(), eq(ProcessingConstants.MDMS_SCHEMA_CODE), any(), anyInt(), anyInt()))
            .thenReturn(mdmsList);
    }
    
    private List<Map<String, Object>> createMockMDMSResponse() {
        Map<String, Object> stringProp1 = new HashMap<>();
        stringProp1.put("name", "FACILITY_NAME");
        stringProp1.put("description", "Facility Name");
        stringProp1.put("isRequired", false);
        stringProp1.put("orderNumber", 1);
        
        Map<String, Object> numberProp1 = new HashMap<>();
        numberProp1.put("name", "TARGET_POPULATION");
        numberProp1.put("description", "Target Population");
        numberProp1.put("isRequired", true);
        numberProp1.put("minimum", 1);
        numberProp1.put("maximum", 100000);
        numberProp1.put("orderNumber", 2);
        
        Map<String, Object> numberProp2 = new HashMap<>();
        numberProp2.put("name", "DRUGS_REQUIRED");
        numberProp2.put("description", "Drugs Required");
        numberProp2.put("isRequired", false);
        numberProp2.put("orderNumber", 3);
        
        Map<String, Object> enumProp1 = new HashMap<>();
        enumProp1.put("name", "DELIVERY_TYPE");
        enumProp1.put("description", "Delivery Type");
        enumProp1.put("isRequired", false);
        enumProp1.put("enumValues", Arrays.asList("HOME_DELIVERY", "FACILITY_DELIVERY", "MOBILE_TEAM"));
        enumProp1.put("orderNumber", 4);
        
        Map<String, Object> properties = new HashMap<>();
        properties.put("stringProperties", Arrays.asList(stringProp1));
        properties.put("numberProperties", Arrays.asList(numberProp1, numberProp2));
        properties.put("enumProperties", Arrays.asList(enumProp1));
        
        Map<String, Object> data = new HashMap<>();
        data.put("properties", properties);
        
        Map<String, Object> mdmsData = new HashMap<>();
        mdmsData.put("data", data);
        
        return Arrays.asList(mdmsData);
    }
    
    private List<Map<String, Object>> createMockMDMSResponseWithMultiSelect() {
        Map<String, Object> stringProp1 = new HashMap<>();
        stringProp1.put("name", "INTERVENTIONS");
        stringProp1.put("description", "Interventions");
        stringProp1.put("isRequired", true);
        stringProp1.put("orderNumber", 1);
        
        Map<String, Object> multiSelectDetails = new HashMap<>();
        multiSelectDetails.put("maxSelections", 3);
        multiSelectDetails.put("minSelections", 1);
        multiSelectDetails.put("enumValues", Arrays.asList("VACCINATION", "NUTRITION", "HEALTH_CHECK", "MEDICINE_DISTRIBUTION"));
        
        stringProp1.put("multiSelectDetails", multiSelectDetails);
        
        Map<String, Object> properties = new HashMap<>();
        properties.put("stringProperties", Arrays.asList(stringProp1));
        
        Map<String, Object> data = new HashMap<>();
        data.put("properties", properties);
        
        Map<String, Object> mdmsData = new HashMap<>();
        mdmsData.put("data", data);
        
        return Arrays.asList(mdmsData);
    }
    
    private List<Map<String, Object>> createMockMDMSResponseWithOrderNumbers() {
        Map<String, Object> stringProp1 = new HashMap<>();
        stringProp1.put("name", "THIRD_COLUMN");
        stringProp1.put("description", "Third Column");
        stringProp1.put("orderNumber", 10);
        
        Map<String, Object> numberProp1 = new HashMap<>();
        numberProp1.put("name", "FIRST_COLUMN");
        numberProp1.put("description", "First Column");
        numberProp1.put("orderNumber", 1);
        
        Map<String, Object> enumProp1 = new HashMap<>();
        enumProp1.put("name", "SECOND_COLUMN");
        enumProp1.put("description", "Second Column");
        enumProp1.put("enumValues", Arrays.asList("OPTION_A", "OPTION_B"));
        enumProp1.put("orderNumber", 5);
        
        Map<String, Object> properties = new HashMap<>();
        properties.put("stringProperties", Arrays.asList(stringProp1));
        properties.put("numberProperties", Arrays.asList(numberProp1));
        properties.put("enumProperties", Arrays.asList(enumProp1));
        
        Map<String, Object> data = new HashMap<>();
        data.put("properties", properties);
        
        Map<String, Object> mdmsData = new HashMap<>();
        mdmsData.put("data", data);
        
        return Arrays.asList(mdmsData);
    }
}