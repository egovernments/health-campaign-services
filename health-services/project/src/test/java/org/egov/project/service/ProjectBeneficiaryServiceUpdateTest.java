package org.egov.project.service;

import digit.models.coremodels.UserSearchRequest;
import org.egov.common.contract.request.User;
import org.egov.project.helper.BeneficiaryRequestTestBuilder;
import org.egov.project.helper.ProjectBeneficiaryTestBuilder;
import org.egov.project.helper.ProjectStaffRequestTestBuilder;
import org.egov.project.helper.ProjectStaffTestBuilder;
import org.egov.project.repository.ProjectBeneficiaryRepository;
import org.egov.project.web.models.ApiOperation;
import org.egov.project.web.models.BeneficiaryRequest;
import org.egov.project.web.models.ProjectBeneficiary;
import org.egov.project.web.models.ProjectStaff;
import org.egov.project.web.models.ProjectStaffRequest;
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
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectBeneficiaryServiceUpdateTest {

    @InjectMocks
    private ProjectBeneficiaryService projectBeneficiaryService;

    @Mock
    private ProjectService projectService;

    @Mock
    private ProjectBeneficiaryRepository projectBeneficiaryRepository;


    private BeneficiaryRequest request;

    private List<String> projectBeneficiaryIds;

    @BeforeEach
    void setUp() throws Exception {
        request = BeneficiaryRequestTestBuilder.builder()
                .withOneProjectBeneficiary()
                .build();
        request.setApiOperation(ApiOperation.UPDATE);
        projectBeneficiaryIds = request.getProjectBeneficiary().stream().map(ProjectBeneficiary::getId)
                .collect(Collectors.toList());
    }


    private void mockValidateProjectId() {
        lenient().when(projectService.validateProjectIds(any(List.class)))
                .thenReturn(Collections.singletonList("some-project-id"));
    }


    private void mockFindById() {
        lenient().when(projectBeneficiaryRepository.findById(projectBeneficiaryIds)).thenReturn(request.getProjectBeneficiary());
    }


    @Test
    @DisplayName("should update the lastModifiedTime in the result")
    void shouldUpdateTheLastModifiedTimeInTheResult() {
        Long time = request.getProjectBeneficiary().get(0).getAuditDetails().getLastModifiedTime();
        mockValidateProjectId();
        mockFindById();

        List<ProjectBeneficiary> result = projectBeneficiaryService.update(request);

        assertNotEquals(time, result.get(0).getAuditDetails().getLastModifiedTime());
    }

    @Test
    @DisplayName("should update the row version in the result")
    void shouldUpdateTheRowVersionInTheResult() {
        Integer rowVersion = request.getProjectBeneficiary().get(0).getRowVersion();
        mockValidateProjectId();
        mockFindById();

        List<ProjectBeneficiary> result = projectBeneficiaryService.update(request);

        assertEquals(rowVersion, result.get(0).getRowVersion() - 1);
    }

    @Test
    @DisplayName("should check if the request has valid project ids")
    void shouldCheckIfTheRequestHasValidProductIds() {
        mockValidateProjectId();
        mockFindById();

        projectBeneficiaryService.update(request);

        verify(projectService, times(1)).validateProjectIds(any(List.class));
    }

    @Test
    @DisplayName("should throw exception for any invalid product id")
    void shouldThrowExceptionForAnyInvalidProductId() throws Exception {
       when(projectService.validateProjectIds(any(List.class))).thenReturn(Collections.emptyList());

        assertThrows(CustomException.class, () -> projectBeneficiaryService.update(request));
    }

    @Test
    @DisplayName("should fetch existing records using id")
    void shouldFetchExistingRecordsUsingId() {
        mockValidateProjectId();
        mockFindById();

        projectBeneficiaryService.update(request);

        verify(projectBeneficiaryRepository, times(1)).findById(anyList());
    }

    @Test
    @DisplayName("should throw exception if fetched records count doesn't match the count in request")
    void shouldThrowExceptionIfFetchedRecordsCountDoesntMatchTheCountInRequest() {
        mockValidateProjectId();
        when(projectBeneficiaryRepository.findById(anyList())).thenReturn(Collections.emptyList());

        assertThrows(CustomException.class, () -> projectBeneficiaryService.update(request));
    }

    @Test
    @DisplayName("should send the updates to kafka")
    void shouldSendTheUpdatesToKafka() {
        mockValidateProjectId();
        mockFindById();
        when(projectBeneficiaryRepository.save(anyList(), anyString())).thenReturn(request.getProjectBeneficiary());

        List<ProjectBeneficiary> projectBeneficiaries = projectBeneficiaryService.update(request);

        assertEquals(request.getProjectBeneficiary(), projectBeneficiaries);
    }

    @Test
    @DisplayName("Should throw exception for row versions mismatch")
    void shouldThrowExceptionIfRowVersionIsNotSimilar() throws Exception {
        ProjectBeneficiary projectBeneficiary = ProjectBeneficiaryTestBuilder.builder().withId().build();
        projectBeneficiary.setRowVersion(123);
        BeneficiaryRequest beneficiaryRequest = BeneficiaryRequestTestBuilder.builder().withOneProjectBeneficiaryHavingId().build();
        mockValidateProjectId();
        mockFindById();

        assertThrows(Exception.class, () -> projectBeneficiaryService.update(beneficiaryRequest));
    }
}
