package org.egov.project.service;

import org.egov.common.data.query.exception.QueryBuilderException;
import org.egov.common.helper.RequestInfoTestBuilder;
import org.egov.project.helper.ProjectStaffTestBuilder;
import org.egov.project.repository.ProjectStaffRepository;
import org.egov.project.web.models.ProjectStaff;
import org.egov.project.web.models.ProjectStaffSearch;
import org.egov.project.web.models.ProjectStaffSearchRequest;
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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectStaffServiceSearchTest {

    @InjectMocks
    private ProjectStaffService projectStaffService;

    @Mock
    private ProjectStaffRepository projectStaffRepository;

    private ArrayList<ProjectStaff> projectStaffs;

    @BeforeEach
    void setUp() {
        projectStaffs = new ArrayList<>();
    }

    private ProjectStaffSearchRequest getProjectStaffSearchRequest() {
        ProjectStaffSearch projectStaffSearch = ProjectStaffSearch.builder().id("ID101").userId("some-user-id").build();
        return ProjectStaffSearchRequest.builder()
                .projectStaff(projectStaffSearch).requestInfo(RequestInfoTestBuilder.builder()
                        .withCompleteRequestInfo().build()).build();
    }

    @Test
    @DisplayName("should not raise exception if no search results are found")
    void shouldNotRaiseExceptionIfNoProductsFound() throws Exception {
        when(projectStaffRepository.find(any(ProjectStaffSearch.class), any(Integer.class),
                any(Integer.class), any(String.class), eq(null), any(Boolean.class))).thenReturn(Collections.emptyList());
        ProjectStaffSearchRequest projectStaffSearchRequest = getProjectStaffSearchRequest();

        assertDoesNotThrow(() -> projectStaffService.search(projectStaffSearchRequest, 10, 0, "default", null, false));
    }


    @Test
    @DisplayName("should return products if search criteria is matched")
    void shouldReturnProductsIfSearchCriteriaIsMatched() throws Exception {
        when(projectStaffRepository.find(any(ProjectStaffSearch.class), any(Integer.class),
                any(Integer.class), any(String.class), eq(null), any(Boolean.class))).thenReturn(projectStaffs);
        projectStaffs.add(ProjectStaffTestBuilder.builder().withId().withAuditDetails().build());
        ProjectStaffSearchRequest projectStaffSearchRequest = getProjectStaffSearchRequest();

        List<ProjectStaff> projectStaffs = projectStaffService.search(projectStaffSearchRequest, 10, 0, "default", null, false);

        assertEquals(1, projectStaffs.size());
    }

    @Test
    @DisplayName("should return from cache if search criteria has id only")
    void shouldReturnFromCacheIfSearchCriteriaHasIdOnly() throws Exception {
        projectStaffs.add(ProjectStaffTestBuilder.builder().withId().withAuditDetails().build());
        ProjectStaffSearch projectStaffSearch = ProjectStaffSearch.builder().id("ID101").build();
        ProjectStaffSearchRequest staffSearchRequest = ProjectStaffSearchRequest.builder()
                .projectStaff(projectStaffSearch).requestInfo(RequestInfoTestBuilder.builder()
                        .withCompleteRequestInfo().build()).build();
        when(projectStaffRepository.findById(anyList())).thenReturn(projectStaffs);

        List<ProjectStaff> productVariants = projectStaffService.search(staffSearchRequest,
                10, 0, "default", null, false);

        assertEquals(1, productVariants.size());
    }
}
