package org.egov.project.service;

import org.egov.common.helper.RequestInfoTestBuilder;
import org.egov.common.models.project.ProjectFacility;
import org.egov.project.helper.ProjectFacilityTestBuilder;
import org.egov.project.repository.ProjectFacilityRepository;
import org.egov.project.web.models.ProjectFacilitySearch;
import org.egov.project.web.models.ProjectFacilitySearchRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;


@ExtendWith(MockitoExtension.class)
class ProjectFacilityServiceSearchTest {

    @InjectMocks
    private ProjectFacilityService projectFacilityService;

    @Mock
    private ProjectFacilityRepository projectFacilityRepository;

    private ArrayList<ProjectFacility> projectFacilities;

    @BeforeEach
    void setUp() {
        projectFacilities = new ArrayList<>();
    }

    @Test
    @DisplayName("should not raise exception if no search results are found")
    void shouldNotRaiseExceptionIfNoProjectFacilityFound() throws Exception {
        when(projectFacilityRepository.find(any(ProjectFacilitySearch.class), any(Integer.class),
                any(Integer.class), any(String.class), eq(null), any(Boolean.class)))
                .thenReturn(Collections.emptyList());
        ProjectFacilitySearch projectFacilitySearch = ProjectFacilitySearch.builder()
                .id(Collections.singletonList("ID101")).facilityId(Collections.singletonList("some-facility-id")).build();
        ProjectFacilitySearchRequest projectFacilitySearchRequest = ProjectFacilitySearchRequest.builder()
                .projectFacility(projectFacilitySearch).requestInfo(RequestInfoTestBuilder.builder()
                        .withCompleteRequestInfo().build()).build();

        assertDoesNotThrow(() -> projectFacilityService.search(projectFacilitySearchRequest, 10, 
                0, "default", null, false));
    }


    @Test
    @DisplayName("should not raise exception if no search results are found for search by id")
    void shouldNotRaiseExceptionIfNoProjectFacilityFoundForSearchById() {
        when(projectFacilityRepository.findById(anyList(),anyBoolean())).thenReturn(Collections.emptyList());
        ProjectFacilitySearch projectFacilitySearch = ProjectFacilitySearch.builder()
                .id(Collections.singletonList("ID101")).build();
        ProjectFacilitySearchRequest projectFacilitySearchRequest = ProjectFacilitySearchRequest.builder()
                .projectFacility(projectFacilitySearch).requestInfo(RequestInfoTestBuilder.builder()
                        .withCompleteRequestInfo().build()).build();

        assertDoesNotThrow(() -> projectFacilityService.search(projectFacilitySearchRequest, 10, 0, 
                "default", null, false));
    }

    @Test
    @DisplayName("should return project facility if search criteria is matched")
    void shouldReturnProjectFacilityIfSearchCriteriaIsMatched() throws Exception {
        when(projectFacilityRepository.find(any(ProjectFacilitySearch.class), any(Integer.class),
                any(Integer.class), any(String.class), eq(null), any(Boolean.class))).thenReturn(projectFacilities);
        projectFacilities.add(ProjectFacilityTestBuilder.builder().withId().withId().withAuditDetails().build());
        ProjectFacilitySearch projectFacilitySearch = ProjectFacilitySearch.builder()
                .id(Collections.singletonList("ID101")).projectId(Collections.singletonList("some-projectId")).build();
        ProjectFacilitySearchRequest projectFacilitySearchRequest = ProjectFacilitySearchRequest.builder()
                .projectFacility(projectFacilitySearch).requestInfo(RequestInfoTestBuilder.builder()
                        .withCompleteRequestInfo().build()).build();

        List<ProjectFacility> projectFacilities = projectFacilityService.search(projectFacilitySearchRequest, 
                10, 0, "default", null, false);

        assertEquals(1, projectFacilities.size());
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
        when(projectFacilityRepository.findById(anyList(), anyBoolean())).thenReturn(projectFacilities);

        List<ProjectFacility> projectFacilities = projectFacilityService.search(projectFacilitySearchRequest,
                10, 0, null, null, true);

        assertEquals(1, projectFacilities.size());
    }
}
