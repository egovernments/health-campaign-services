package org.egov.excelingestion.processor;

import org.egov.excelingestion.service.CampaignService;
import org.egov.excelingestion.service.SchemaValidationService;
import org.egov.excelingestion.service.ValidationService;
import org.egov.excelingestion.util.BoundaryUtil;
import org.egov.excelingestion.web.models.*;
import org.egov.common.contract.request.RequestInfo;
import org.egov.tracer.model.CustomException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests validateCampaignBoundaries in BoundaryHierarchyTargetProcessor.
 * A boundary mismatch aborts the upload with a named error code rather than writing a sheet error,
 * so the code and message reach additionalDetails (HCMPRE-4496).
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

        // Mirror the real handler: turn the call into the exception it would actually throw
        lenient().doAnswer(invocation -> {
            throw new CustomException(invocation.getArgument(0), invocation.getArgument(1));
        }).when(exceptionHandler).throwCustomException(anyString(), anyString());

        validateCampaignBoundariesMethod = BoundaryHierarchyTargetProcessor.class
            .getDeclaredMethod("validateCampaignBoundaries", List.class, ProcessResource.class,
                              RequestInfo.class, Map.class);
        validateCampaignBoundariesMethod.setAccessible(true);
    }

    /** Invokes the private method, unwrapping reflection so callers see the real exception. */
    private void validate(List<Map<String, Object>> sheetData, ProcessResource resource,
                          RequestInfo requestInfo, Map<String, String> localizationMap) throws Throwable {
        try {
            validateCampaignBoundariesMethod.invoke(targetProcessor, sheetData, resource, requestInfo, localizationMap);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    @Test
    void invalidBoundaryCode_abortsWithNamedCodeAndTheOffendingRow() {
        List<Map<String, Object>> sheetData = Arrays.asList(
            createTargetRow("VALID_BOUNDARY_001", "Village A", "100", 3),
            createTargetRow("INVALID_BOUNDARY_999", "Invalid Village", "50", 4),
            createTargetRow("ANOTHER_INVALID_888", "Another Invalid", "75", 5)
        );
        ProcessResource resource = createProcessResource();
        RequestInfo requestInfo = new RequestInfo();

        when(boundaryUtil.getEnrichedBoundaryCodesFromCampaign(
            resource.getId(), resource.getReferenceId(), resource.getTenantId(),
            resource.getHierarchyType(), requestInfo))
            .thenReturn(Set.of("VALID_BOUNDARY_001", "CAMPAIGN_BOUNDARY_002"));

        CustomException thrown = assertThrows(CustomException.class,
            () -> validate(sheetData, resource, requestInfo, createLocalizationMap()));

        assertEquals("HCM_BOUNDARY_CODE_NOT_IN_CAMPAIGN_BOUNDARIES", thrown.getCode());
        assertTrue(thrown.getMessage().contains("row 4"), thrown.getMessage());
    }

    @Test
    void allBoundariesValid_passesWithoutAborting() throws Throwable {
        List<Map<String, Object>> sheetData = Arrays.asList(
            createTargetRow("VALID_BOUNDARY_001", "Village A", "100", 3),
            createTargetRow("VALID_BOUNDARY_002", "Village B", "150", 4)
        );
        ProcessResource resource = createProcessResource();
        RequestInfo requestInfo = new RequestInfo();

        when(boundaryUtil.getEnrichedBoundaryCodesFromCampaign(
            resource.getId(), resource.getReferenceId(), resource.getTenantId(),
            resource.getHierarchyType(), requestInfo))
            .thenReturn(Set.of("VALID_BOUNDARY_001", "VALID_BOUNDARY_002"));
        when(boundaryUtil.getLowestLevelBoundaryCodesFromCampaign(
            resource.getId(), resource.getReferenceId(), resource.getTenantId(),
            resource.getHierarchyType(), requestInfo))
            .thenReturn(Set.of("VALID_BOUNDARY_001", "VALID_BOUNDARY_002"));

        validate(sheetData, resource, requestInfo, createLocalizationMap());

        verify(exceptionHandler, never()).throwCustomException(anyString(), anyString());
    }

    @Test
    void droppedCampaignBoundary_abortsWithMissingTargetsCode() {
        // User deleted the row for VALID_BOUNDARY_002 but left every remaining code valid
        List<Map<String, Object>> sheetData = List.of(
            createTargetRow("VALID_BOUNDARY_001", "Village A", "100", 3)
        );
        ProcessResource resource = createProcessResource();
        RequestInfo requestInfo = new RequestInfo();

        when(boundaryUtil.getEnrichedBoundaryCodesFromCampaign(
            resource.getId(), resource.getReferenceId(), resource.getTenantId(),
            resource.getHierarchyType(), requestInfo))
            .thenReturn(Set.of("VALID_BOUNDARY_001", "VALID_BOUNDARY_002"));
        when(boundaryUtil.getLowestLevelBoundaryCodesFromCampaign(
            resource.getId(), resource.getReferenceId(), resource.getTenantId(),
            resource.getHierarchyType(), requestInfo))
            .thenReturn(Set.of("VALID_BOUNDARY_001", "VALID_BOUNDARY_002"));

        CustomException thrown = assertThrows(CustomException.class,
            () -> validate(sheetData, resource, requestInfo, createLocalizationMap()));

        assertEquals("HCM_TARGET_LOWEST_LEVEL_BOUNDARIES_MISSING", thrown.getCode());
        assertTrue(thrown.getMessage().contains("VALID_BOUNDARY_002"), thrown.getMessage());
    }

    @Test
    void swappedBoundary_reportsTheMissingBoundaryFirst() {
        // Replacing one boundary with another trips both rules; the missing check runs first
        List<Map<String, Object>> sheetData = List.of(
            createTargetRow("NOT_IN_CAMPAIGN", "Swapped in", "100", 3)
        );
        ProcessResource resource = createProcessResource();
        RequestInfo requestInfo = new RequestInfo();

        when(boundaryUtil.getEnrichedBoundaryCodesFromCampaign(
            resource.getId(), resource.getReferenceId(), resource.getTenantId(),
            resource.getHierarchyType(), requestInfo))
            .thenReturn(Set.of("VALID_BOUNDARY_001"));
        when(boundaryUtil.getLowestLevelBoundaryCodesFromCampaign(
            resource.getId(), resource.getReferenceId(), resource.getTenantId(),
            resource.getHierarchyType(), requestInfo))
            .thenReturn(Set.of("VALID_BOUNDARY_001"));

        CustomException thrown = assertThrows(CustomException.class,
            () -> validate(sheetData, resource, requestInfo, createLocalizationMap()));

        assertEquals("HCM_TARGET_LOWEST_LEVEL_BOUNDARIES_MISSING", thrown.getCode());
    }

    @Test
    void emptyBoundaryCodes_areLeftToSchemaValidation() throws Throwable {
        List<Map<String, Object>> sheetData = Arrays.asList(
            createTargetRow("", "Village with empty boundary", "100", 3),
            createTargetRow(null, "Village with null boundary", "150", 4),
            createTargetRow("VALID_BOUNDARY_001", "Valid Village", "200", 5)
        );
        ProcessResource resource = createProcessResource();
        RequestInfo requestInfo = new RequestInfo();

        when(boundaryUtil.getEnrichedBoundaryCodesFromCampaign(
            resource.getId(), resource.getReferenceId(), resource.getTenantId(),
            resource.getHierarchyType(), requestInfo))
            .thenReturn(Set.of("VALID_BOUNDARY_001"));

        validate(sheetData, resource, requestInfo, createLocalizationMap());

        verify(exceptionHandler, never()).throwCustomException(anyString(), anyString());
    }

    @Test
    void campaignLookupFailure_skipsValidationInsteadOfBlamingTheUser() throws Throwable {
        List<Map<String, Object>> sheetData = List.of(
            createTargetRow("BOUNDARY_001", "Village A", "100", 3)
        );
        ProcessResource resource = createProcessResource();
        RequestInfo requestInfo = new RequestInfo();

        when(boundaryUtil.getEnrichedBoundaryCodesFromCampaign(
            resource.getId(), resource.getReferenceId(), resource.getTenantId(),
            resource.getHierarchyType(), requestInfo))
            .thenThrow(new RuntimeException("Boundary service error"));

        validate(sheetData, resource, requestInfo, createLocalizationMap());

        verify(exceptionHandler, never()).throwCustomException(anyString(), anyString());
    }

    @Test
    void manyMissingBoundaries_areTruncatedInTheMessage() {
        List<Map<String, Object>> sheetData = List.of(
            createTargetRow("VALID_BOUNDARY_001", "Village A", "100", 3)
        );
        ProcessResource resource = createProcessResource();
        RequestInfo requestInfo = new RequestInfo();

        Set<String> campaignBoundaries = new HashSet<>();
        campaignBoundaries.add("VALID_BOUNDARY_001");
        for (int i = 0; i < 10; i++) {
            campaignBoundaries.add("MISSING_" + i);
        }
        when(boundaryUtil.getEnrichedBoundaryCodesFromCampaign(
            resource.getId(), resource.getReferenceId(), resource.getTenantId(),
            resource.getHierarchyType(), requestInfo))
            .thenReturn(campaignBoundaries);
        when(boundaryUtil.getLowestLevelBoundaryCodesFromCampaign(
            resource.getId(), resource.getReferenceId(), resource.getTenantId(),
            resource.getHierarchyType(), requestInfo))
            .thenReturn(campaignBoundaries);

        CustomException thrown = assertThrows(CustomException.class,
            () -> validate(sheetData, resource, requestInfo, createLocalizationMap()));

        assertEquals("HCM_TARGET_LOWEST_LEVEL_BOUNDARIES_MISSING", thrown.getCode());
        assertTrue(thrown.getMessage().endsWith("..."), thrown.getMessage());
    }

    @Test
    void missingBoundaryNamesInTheMessageAreLocalized() {
        List<Map<String, Object>> sheetData = List.of(
            createTargetRow("VALID_BOUNDARY_001", "Village A", "100", 3)
        );
        ProcessResource resource = createProcessResource();
        RequestInfo requestInfo = new RequestInfo();

        Map<String, String> localizationMap = new HashMap<>();
        localizationMap.put("MISSING_BOUNDARY", "Aldeia Desconhecida");

        when(boundaryUtil.getEnrichedBoundaryCodesFromCampaign(any(), any(), any(), any(), any()))
            .thenReturn(Set.of("VALID_BOUNDARY_001", "MISSING_BOUNDARY"));
        when(boundaryUtil.getLowestLevelBoundaryCodesFromCampaign(any(), any(), any(), any(), any()))
            .thenReturn(Set.of("VALID_BOUNDARY_001", "MISSING_BOUNDARY"));

        CustomException thrown = assertThrows(CustomException.class,
            () -> validate(sheetData, resource, requestInfo, localizationMap));

        assertTrue(thrown.getMessage().contains("Aldeia Desconhecida"), thrown.getMessage());
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
        return new HashMap<>();
    }
}
