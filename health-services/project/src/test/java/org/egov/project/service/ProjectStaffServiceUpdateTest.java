package org.egov.project.service;

import digit.models.coremodels.UserSearchRequest;
import org.egov.common.contract.request.User;
import org.egov.common.service.UserService;
import org.egov.project.config.ProjectConfiguration;
import org.egov.project.helper.ProjectStaffRequestTestBuilder;
import org.egov.project.repository.ProjectStaffRepository;
import org.egov.project.web.models.ApiOperation;
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
class ProjectStaffServiceUpdateTest {

    @InjectMocks
    private ProjectStaffService projectStaffService;

    @Mock
    private ProjectService projectService;

    @Mock
    private ProjectStaffRepository projectStaffRepository;

    @Mock
    private UserService userService;

    @Mock
    private ProjectConfiguration projectConfiguration;

    private ProjectStaffRequest request;

    private List<String> projectStaffIds;

    @BeforeEach
    void setUp() throws Exception {
        request = ProjectStaffRequestTestBuilder.builder()
                .withOneProjectStaffHavingId()
                .build();
        request.setApiOperation(ApiOperation.UPDATE);
        projectStaffIds = request.getProjectStaff().stream().map(ProjectStaff::getId)
                .collect(Collectors.toList());
        lenient().when(projectConfiguration.getUpdateProjectStaffTopic()).thenReturn("update-topic");
    }


    private void mockValidateProjectId() {
        lenient().when(projectService.validateProjectIds(any(List.class)))
                .thenReturn(Collections.singletonList("some-project-id"));
    }


    private void mockValidateUsers() {
        when(userService.search(any(UserSearchRequest.class))).thenReturn(getUserList());
    }

    private List<User> getUserList() {
        return Arrays.asList(
                User.builder().uuid("user1").build()
        );
    }

    private List<String> getIdGens() {
        List<String> ids = new ArrayList<String>();
        ids.add("some-id");
        ids.add("some-id-2");
        return ids;
    }

    private void mockFindById() {
        when(projectStaffRepository.findById(projectStaffIds)).thenReturn(request.getProjectStaff());
    }


    @Test
    @DisplayName("should update the lastModifiedTime in the result")
    void shouldUpdateTheLastModifiedTimeInTheResult() {
        Long time = request.getProjectStaff().get(0).getAuditDetails().getLastModifiedTime();
        mockValidateProjectId();
        mockValidateUsers();
        mockFindById();

        List<ProjectStaff> result = projectStaffService.update(request);

        assertNotEquals(time, result.get(0).getAuditDetails().getLastModifiedTime());
    }

    @Test
    @DisplayName("should update the row version in the result")
    void shouldUpdateTheRowVersionInTheResult() {
        Integer rowVersion = request.getProjectStaff().get(0).getRowVersion();
        mockValidateProjectId();
        mockValidateUsers();
        mockFindById();

        List<ProjectStaff> result = projectStaffService.update(request);

        assertEquals(rowVersion, result.get(0).getRowVersion() - 1);
    }

    @Test
    @DisplayName("should check if the request has valid project ids")
    void shouldCheckIfTheRequestHasValidProductIds() {
        mockValidateProjectId();
        mockValidateUsers();
        mockFindById();

        projectStaffService.update(request);

        verify(projectService, times(1)).validateProjectIds(any(List.class));
    }

    @Test
    @DisplayName("should throw exception for any invalid product id")
    void shouldThrowExceptionForAnyInvalidProductId() throws Exception {
        when(projectService.validateProjectIds(any(List.class))).thenReturn(Collections.emptyList());

        assertThrows(CustomException.class, () -> projectStaffService.update(request));
    }

    @Test
    @DisplayName("should fetch existing records using id")
    void shouldFetchExistingRecordsUsingId() {
        mockValidateProjectId();
        mockValidateUsers();
        mockFindById();

        projectStaffService.update(request);

        verify(projectStaffRepository, times(1)).findById(anyList());
    }

    @Test
    @DisplayName("should throw exception if fetched records count doesn't match the count in request")
    void shouldThrowExceptionIfFetchedRecordsCountDoesntMatchTheCountInRequest() {
        mockValidateProjectId();
        mockValidateUsers();
        when(projectStaffRepository.findById(anyList())).thenReturn(Collections.emptyList());

        assertThrows(CustomException.class, () -> projectStaffService.update(request));
    }

    @Test
    @DisplayName("should send the updates to kafka")
    void shouldSendTheUpdatesToKafka() {
        mockValidateProjectId();
        mockValidateUsers();
        mockFindById();
        when(projectStaffRepository.save(anyList(), anyString())).thenReturn(request.getProjectStaff());

        List<ProjectStaff> ProjectStaffs = projectStaffService.update(request);

        assertEquals(request.getProjectStaff(), ProjectStaffs);
    }

    @Test
    @DisplayName("Should throw exception for row versions mismatch")
    void shouldThrowExceptionIfRowVersionIsNotSimilar() throws Exception {
        ProjectStaffRequest projectStaffRequest = ProjectStaffRequestTestBuilder.builder()
                .withOneProjectStaffHavingId().build();
        projectStaffRequest.getProjectStaff().get(0).setRowVersion(123);
        mockValidateProjectId();
        mockValidateUsers();
        mockFindById();

        assertThrows(Exception.class, () -> projectStaffService.update(projectStaffRequest));
    }
}
