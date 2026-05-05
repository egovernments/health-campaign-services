package org.egov.project.service;

import org.egov.common.exception.InvalidTenantIdException;
import org.egov.common.helper.RequestInfoTestBuilder;
import org.egov.common.models.core.SearchResponse;
import org.egov.common.models.project.Project;
import org.egov.common.models.project.ProjectFacility;
import org.egov.common.models.project.ProjectFacilityBulkResponse;
import org.egov.common.models.project.ProjectFacilitySearch;
import org.egov.common.models.project.ProjectFacilitySearchRequest;
import org.egov.common.models.project.ProjectRequest;
import org.egov.project.helper.ProjectFacilityTestBuilder;
import org.egov.project.repository.ProjectFacilityRepository;
import org.egov.tracer.model.CustomException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;


@ExtendWith(MockitoExtension.class)
class ProjectFacilityServiceSearchTest {

    @InjectMocks
    private ProjectFacilityService projectFacilityService;

    @Mock
    private ProjectFacilityRepository projectFacilityRepository;

    @Mock
    private ProjectService projectService;

    private ArrayList<ProjectFacility> projectFacilities;

    @BeforeEach
    void setUp() {
        projectFacilities = new ArrayList<>();
    }

    // ==================== Normal Search (via searchFacilities) ====================

    @Test
    @DisplayName("should not raise exception if no search results are found")
    void shouldNotRaiseExceptionIfNoProjectFacilityFound() throws Exception {
        when(projectFacilityRepository.findWithCount(any(ProjectFacilitySearch.class), any(Integer.class),
                any(Integer.class), any(String.class), eq(null), any(Boolean.class)))
                .thenReturn(SearchResponse.<ProjectFacility>builder().response(Collections.emptyList()).build());
        ProjectFacilitySearch projectFacilitySearch = ProjectFacilitySearch.builder()
                .id(Collections.singletonList("ID101")).facilityId(Collections.singletonList("some-facility-id")).build();
        ProjectFacilitySearchRequest projectFacilitySearchRequest = ProjectFacilitySearchRequest.builder()
                .projectFacility(projectFacilitySearch).requestInfo(RequestInfoTestBuilder.builder()
                        .withCompleteRequestInfo().build()).build();

        assertDoesNotThrow(() -> projectFacilityService.searchFacilities(projectFacilitySearchRequest, 10,
                0, "default", null, false));
    }


    @Test
    @DisplayName("should not raise exception if no search results are found for search by id")
    void shouldNotRaiseExceptionIfNoProjectFacilityFoundForSearchById() throws InvalidTenantIdException {
        when(projectFacilityRepository.findById(anyString(), anyList(),anyBoolean())).thenReturn(Collections.emptyList());
        ProjectFacilitySearch projectFacilitySearch = ProjectFacilitySearch.builder()
                .id(Collections.singletonList("ID101")).build();
        ProjectFacilitySearchRequest projectFacilitySearchRequest = ProjectFacilitySearchRequest.builder()
                .projectFacility(projectFacilitySearch).requestInfo(RequestInfoTestBuilder.builder()
                        .withCompleteRequestInfo().build()).build();

        assertDoesNotThrow(() -> projectFacilityService.searchFacilities(projectFacilitySearchRequest, 10, 0,
                "default", null, false));
    }

    @Test
    @DisplayName("should return project facility if search criteria is matched")
    void shouldReturnProjectFacilityIfSearchCriteriaIsMatched() throws Exception {
        when(projectFacilityRepository.findWithCount(any(ProjectFacilitySearch.class), any(Integer.class),
                any(Integer.class), any(String.class), eq(null), any(Boolean.class))).thenReturn(SearchResponse.<ProjectFacility>builder()
                .response(projectFacilities).build());
        projectFacilities.add(ProjectFacilityTestBuilder.builder().withId().withId().withAuditDetails().build());
        ProjectFacilitySearch projectFacilitySearch = ProjectFacilitySearch.builder()
                .id(Collections.singletonList("ID101")).projectId(Collections.singletonList("some-projectId")).build();
        ProjectFacilitySearchRequest projectFacilitySearchRequest = ProjectFacilitySearchRequest.builder()
                .projectFacility(projectFacilitySearch).requestInfo(RequestInfoTestBuilder.builder()
                        .withCompleteRequestInfo().build()).build();

        ProjectFacilityBulkResponse response = projectFacilityService.searchFacilities(projectFacilitySearchRequest,
                10, 0, "default", null, false);

        assertEquals(1, response.getProjectFacilities().size());
        assertNotNull(response.getResponseInfo());
        assertNull(response.getFacilityMap());
    }

    @Test
    @DisplayName("should return from cache if search criteria has id only")
    void shouldReturnFromCacheIfSearchCriteriaHasIdOnly() throws Exception {
        projectFacilities.add(ProjectFacilityTestBuilder.builder().withId().withAuditDetails().withDeleted().build());
        ProjectFacilitySearch projectFacilitySearch = ProjectFacilitySearch.builder()
                .id(Collections.singletonList("ID101")).build();
        ProjectFacilitySearchRequest projectFacilitySearchRequest = ProjectFacilitySearchRequest.builder()
                .projectFacility(projectFacilitySearch).requestInfo(RequestInfoTestBuilder.builder()
                        .withCompleteRequestInfo().build()).build();
        when(projectFacilityRepository.findById(eq("some-tenant-id"), anyList(), anyBoolean())).thenReturn(projectFacilities);

        ProjectFacilityBulkResponse response = projectFacilityService.searchFacilities(projectFacilitySearchRequest,
                10, 0, "some-tenant-id", null, true);

        assertEquals(1, response.getProjectFacilities().size());
        assertNotNull(response.getResponseInfo());
    }

    @Test
    @DisplayName("should return response with totalCount for criteria search")
    void shouldReturnResponseWithTotalCountForCriteriaSearch() throws Exception {
        projectFacilities.add(ProjectFacilityTestBuilder.builder().withId().withAuditDetails().build());
        when(projectFacilityRepository.findWithCount(any(ProjectFacilitySearch.class), any(Integer.class),
                any(Integer.class), any(String.class), eq(null), any(Boolean.class)))
                .thenReturn(SearchResponse.<ProjectFacility>builder()
                        .response(projectFacilities).totalCount(5L).build());
        ProjectFacilitySearch projectFacilitySearch = ProjectFacilitySearch.builder()
                .projectId(Collections.singletonList("some-projectId")).build();
        ProjectFacilitySearchRequest projectFacilitySearchRequest = ProjectFacilitySearchRequest.builder()
                .projectFacility(projectFacilitySearch).requestInfo(RequestInfoTestBuilder.builder()
                        .withCompleteRequestInfo().build()).build();

        ProjectFacilityBulkResponse response = projectFacilityService.searchFacilities(projectFacilitySearchRequest,
                10, 0, "default", null, false);

        assertEquals(5L, response.getTotalCount());
        assertEquals(1, response.getProjectFacilities().size());
    }

    // ==================== Hierarchy Search (via searchFacilities) ====================

    @Test
    @DisplayName("should return facility map and project facility for hierarchy search")
    void shouldReturnFacilityMapAndProjectFacilityForHierarchySearch() throws Exception {
        // Setup project
        Project project = Project.builder().id("P1").tenantId("default")
                .projectHierarchy("ROOT.S1.P1").build();
        when(projectService.searchProject(any(ProjectRequest.class),
                anyInt(), anyInt(), anyString(), any(), anyBoolean(),
                anyBoolean(), anyBoolean(), any(), any(), anyBoolean()))
                .thenReturn(Collections.singletonList(project));

        // Setup descendant facilities
        Map<String, List<String>> descendantMap = new HashMap<>();
        descendantMap.put("VILLAGE", Arrays.asList("F1", "F2"));
        when(projectFacilityRepository.findFacilitiesByDescendants(eq("P1"), anyList(), eq("default")))
                .thenReturn(descendantMap);

        // Setup ancestor facilities
        Map<String, List<String>> ancestorMap = new HashMap<>();
        ancestorMap.put("DISTRICT", Collections.singletonList("F3"));
        when(projectFacilityRepository.findFacilitiesByAncestors(anyList(), anyList(), eq("default")))
                .thenReturn(ancestorMap);

        // Setup project facility fetch
        ProjectFacility pf = ProjectFacilityTestBuilder.builder().withId().withAuditDetails().build();
        when(projectFacilityRepository.findWithCount(any(ProjectFacilitySearch.class),
                eq(1), eq(0), eq("default"), eq(null), eq(false)))
                .thenReturn(SearchResponse.<ProjectFacility>builder()
                        .response(Collections.singletonList(pf)).build());

        ProjectFacilitySearch search = ProjectFacilitySearch.builder()
                .projectId(Collections.singletonList("P1"))
                .boundaryTypes(Arrays.asList("VILLAGE", "DISTRICT")).build();
        ProjectFacilitySearchRequest request = ProjectFacilitySearchRequest.builder()
                .projectFacility(search).requestInfo(RequestInfoTestBuilder.builder()
                        .withCompleteRequestInfo().build()).build();

        ProjectFacilityBulkResponse response = projectFacilityService.searchFacilities(request,
                10, 0, "default", null, false);

        assertNotNull(response.getFacilityMap());
        assertEquals(2, response.getFacilityMap().get("VILLAGE").size());
        assertEquals(1, response.getFacilityMap().get("DISTRICT").size());
        assertEquals(1, response.getProjectFacilities().size());
        assertNotNull(response.getResponseInfo());
    }

    @Test
    @DisplayName("should return empty response when project not found in hierarchy search")
    void shouldReturnEmptyResponseWhenProjectNotFoundInHierarchySearch() throws Exception {
        when(projectService.searchProject(any(ProjectRequest.class),
                anyInt(), anyInt(), anyString(), any(), anyBoolean(),
                anyBoolean(), anyBoolean(), any(), any(), anyBoolean()))
                .thenReturn(Collections.emptyList());

        ProjectFacilitySearch search = ProjectFacilitySearch.builder()
                .projectId(Collections.singletonList("NONEXISTENT"))
                .boundaryTypes(Collections.singletonList("VILLAGE")).build();
        ProjectFacilitySearchRequest request = ProjectFacilitySearchRequest.builder()
                .projectFacility(search).requestInfo(RequestInfoTestBuilder.builder()
                        .withCompleteRequestInfo().build()).build();

        ProjectFacilityBulkResponse response = projectFacilityService.searchFacilities(request,
                10, 0, "default", null, false);

        assertNotNull(response.getFacilityMap());
        assertTrue(response.getFacilityMap().isEmpty());
        assertTrue(response.getProjectFacilities().isEmpty());
        assertEquals(0L, response.getTotalCount());
    }

    @Test
    @DisplayName("should throw exception when projectId is missing in hierarchy search")
    void shouldThrowExceptionWhenProjectIdMissingInHierarchySearch() {
        ProjectFacilitySearch search = ProjectFacilitySearch.builder()
                .boundaryTypes(Collections.singletonList("VILLAGE")).build();
        ProjectFacilitySearchRequest request = ProjectFacilitySearchRequest.builder()
                .projectFacility(search).requestInfo(RequestInfoTestBuilder.builder()
                        .withCompleteRequestInfo().build()).build();

        assertThrows(CustomException.class, () -> projectFacilityService.searchFacilities(request,
                10, 0, "default", null, false));
    }

    @Test
    @DisplayName("should throw exception when projectId is empty list in hierarchy search")
    void shouldThrowExceptionWhenProjectIdIsEmptyListInHierarchySearch() {
        ProjectFacilitySearch search = ProjectFacilitySearch.builder()
                .projectId(Collections.emptyList())
                .boundaryTypes(Collections.singletonList("VILLAGE")).build();
        ProjectFacilitySearchRequest request = ProjectFacilitySearchRequest.builder()
                .projectFacility(search).requestInfo(RequestInfoTestBuilder.builder()
                        .withCompleteRequestInfo().build()).build();

        assertThrows(CustomException.class, () -> projectFacilityService.searchFacilities(request,
                10, 0, "default", null, false));
    }

    @Test
    @DisplayName("should handle project with no hierarchy string in hierarchy search")
    void shouldHandleProjectWithNoHierarchyStringInHierarchySearch() throws Exception {
        Project project = Project.builder().id("P1").tenantId("default")
                .projectHierarchy(null).build();
        when(projectService.searchProject(any(ProjectRequest.class),
                anyInt(), anyInt(), anyString(), any(), anyBoolean(),
                anyBoolean(), anyBoolean(), any(), any(), anyBoolean()))
                .thenReturn(Collections.singletonList(project));

        Map<String, List<String>> descendantMap = new HashMap<>();
        when(projectFacilityRepository.findFacilitiesByDescendants(eq("P1"), anyList(), eq("default")))
                .thenReturn(descendantMap);

        // Ancestors: only P1 itself (no hierarchy to parse)
        Map<String, List<String>> ancestorMap = new HashMap<>();
        ancestorMap.put("DISTRICT", Collections.singletonList("F1"));
        when(projectFacilityRepository.findFacilitiesByAncestors(eq(Collections.singletonList("P1")), anyList(), eq("default")))
                .thenReturn(ancestorMap);

        ProjectFacility pf = ProjectFacilityTestBuilder.builder().withId().withAuditDetails().build();
        when(projectFacilityRepository.findWithCount(any(ProjectFacilitySearch.class),
                eq(1), eq(0), eq("default"), eq(null), eq(false)))
                .thenReturn(SearchResponse.<ProjectFacility>builder()
                        .response(Collections.singletonList(pf)).build());

        ProjectFacilitySearch search = ProjectFacilitySearch.builder()
                .projectId(Collections.singletonList("P1"))
                .boundaryTypes(Collections.singletonList("DISTRICT")).build();
        ProjectFacilitySearchRequest request = ProjectFacilitySearchRequest.builder()
                .projectFacility(search).requestInfo(RequestInfoTestBuilder.builder()
                        .withCompleteRequestInfo().build()).build();

        ProjectFacilityBulkResponse response = projectFacilityService.searchFacilities(request,
                10, 0, "default", null, false);

        assertNotNull(response.getFacilityMap());
        assertEquals(1, response.getFacilityMap().get("DISTRICT").size());
        assertEquals(1, response.getProjectFacilities().size());
    }

    @Test
    @DisplayName("should throw exception when project search fails in hierarchy search")
    void shouldThrowExceptionWhenProjectSearchFailsInHierarchySearch() throws Exception {
        when(projectService.searchProject(any(ProjectRequest.class),
                anyInt(), anyInt(), anyString(), any(), anyBoolean(),
                anyBoolean(), anyBoolean(), any(), any(), anyBoolean()))
                .thenThrow(new RuntimeException("DB connection failed"));

        ProjectFacilitySearch search = ProjectFacilitySearch.builder()
                .projectId(Collections.singletonList("P1"))
                .boundaryTypes(Collections.singletonList("VILLAGE")).build();
        ProjectFacilitySearchRequest request = ProjectFacilitySearchRequest.builder()
                .projectFacility(search).requestInfo(RequestInfoTestBuilder.builder()
                        .withCompleteRequestInfo().build()).build();

        assertThrows(CustomException.class, () -> projectFacilityService.searchFacilities(request,
                10, 0, "default", null, false));
    }

    @Test
    @DisplayName("should throw exception when facility hierarchy query fails")
    void shouldThrowExceptionWhenFacilityHierarchyQueryFails() throws Exception {
        Project project = Project.builder().id("P1").tenantId("default")
                .projectHierarchy("ROOT.P1").build();
        when(projectService.searchProject(any(ProjectRequest.class),
                anyInt(), anyInt(), anyString(), any(), anyBoolean(),
                anyBoolean(), anyBoolean(), any(), any(), anyBoolean()))
                .thenReturn(Collections.singletonList(project));

        when(projectFacilityRepository.findFacilitiesByDescendants(eq("P1"), anyList(), eq("default")))
                .thenThrow(new InvalidTenantIdException("bad tenant"));

        ProjectFacilitySearch search = ProjectFacilitySearch.builder()
                .projectId(Collections.singletonList("P1"))
                .boundaryTypes(Collections.singletonList("VILLAGE")).build();
        ProjectFacilitySearchRequest request = ProjectFacilitySearchRequest.builder()
                .projectFacility(search).requestInfo(RequestInfoTestBuilder.builder()
                        .withCompleteRequestInfo().build()).build();

        assertThrows(CustomException.class, () -> projectFacilityService.searchFacilities(request,
                10, 0, "default", null, false));
    }
}
