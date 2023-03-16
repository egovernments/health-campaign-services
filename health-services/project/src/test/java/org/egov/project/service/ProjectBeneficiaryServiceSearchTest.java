package org.egov.project.service;

import org.egov.common.data.query.exception.QueryBuilderException;
import org.egov.common.helper.RequestInfoTestBuilder;
import org.egov.common.models.project.ProjectBeneficiary;
import org.egov.project.helper.ProjectBeneficiaryTestBuilder;
import org.egov.project.repository.ProjectBeneficiaryRepository;
import org.egov.project.web.models.BeneficiarySearchRequest;
import org.egov.project.web.models.ProjectBeneficiarySearch;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;


@ExtendWith(MockitoExtension.class)
class ProjectBeneficiaryServiceSearchTest {

    @InjectMocks
    private ProjectBeneficiaryService projectBeneficiaryService;

    @Mock
    private ProjectBeneficiaryRepository projectBeneficiaryRepository;

    private ArrayList<ProjectBeneficiary> projectBeneficiary;

    @BeforeEach
    void setUp() throws QueryBuilderException {
        projectBeneficiary = new ArrayList<>();
    }

    @Test
    @DisplayName("should not raise exception if no search results are found")
    void shouldNotRaiseExceptionIfNoProjectBeneficiaryFound() throws Exception {
        when(projectBeneficiaryRepository.find(any(ProjectBeneficiarySearch.class), any(Integer.class),
                any(Integer.class), any(String.class), eq(null), any(Boolean.class))).thenReturn(Collections.emptyList());
        ProjectBeneficiarySearch projectBeneficiarySearch = ProjectBeneficiarySearch.builder()
                .id(Collections.singletonList("ID101")).projectId("some-id").build();
        BeneficiarySearchRequest beneficiarySearchRequest = BeneficiarySearchRequest.builder()
                .projectBeneficiary(projectBeneficiarySearch).requestInfo(RequestInfoTestBuilder.builder()
                        .withCompleteRequestInfo().build()).build();

        assertDoesNotThrow(() -> projectBeneficiaryService.search(beneficiarySearchRequest, 10, 0, "default", null, false));
    }


    @Test
    @DisplayName("should return project beneficiary if search criteria is matched")
    void shouldReturnProjectStaffIfSearchCriteriaIsMatched() throws Exception {
        when(projectBeneficiaryRepository.find(any(ProjectBeneficiarySearch.class), any(Integer.class),
                any(Integer.class), any(String.class), eq(null), any(Boolean.class))).thenReturn(projectBeneficiary);
        projectBeneficiary.add(ProjectBeneficiaryTestBuilder.builder().withId().withId().withAuditDetails().build());
        ProjectBeneficiarySearch projectBeneficiarySearch = ProjectBeneficiarySearch.builder()
                .id(Collections.singletonList("ID101")).projectId("some-projectId").build();
        BeneficiarySearchRequest beneficiarySearchRequest = BeneficiarySearchRequest.builder()
                .projectBeneficiary(projectBeneficiarySearch).requestInfo(RequestInfoTestBuilder.builder()
                        .withCompleteRequestInfo().build()).build();

        List<ProjectBeneficiary> projectStaffs = projectBeneficiaryService.search(beneficiarySearchRequest, 10, 0, "default", null, false);

        assertEquals(1, projectStaffs.size());
    }

    @Test
    @DisplayName("should return from cache if search criteria has id only")
    void shouldReturnFromCacheIfSearchCriteriaHasIdOnly() throws Exception {
        projectBeneficiary.add(ProjectBeneficiaryTestBuilder.builder().withId().withAuditDetails().withDeleted().build());
        ProjectBeneficiarySearch projectBeneficiarySearch = ProjectBeneficiarySearch.builder()
                .id(Collections.singletonList("ID101")).build();
        BeneficiarySearchRequest projectStaffSearchRequest = BeneficiarySearchRequest.builder()
                .projectBeneficiary(projectBeneficiarySearch).requestInfo(RequestInfoTestBuilder.builder()
                        .withCompleteRequestInfo().build()).build();
        when(projectBeneficiaryRepository.findById(anyList(), anyBoolean(), anyString())).thenReturn(projectBeneficiary);

        List<ProjectBeneficiary> projectBeneficiaries = projectBeneficiaryService.search(projectStaffSearchRequest,
                10, 0, null, null, true);

        assertEquals(1, projectBeneficiaries.size());
    }
}
