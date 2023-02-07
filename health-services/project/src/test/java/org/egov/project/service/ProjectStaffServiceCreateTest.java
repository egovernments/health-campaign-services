package org.egov.project.service;

import digit.models.coremodels.UserSearchRequest;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.contract.request.User;
import org.egov.common.service.IdGenService;
import org.egov.common.service.UserService;
import org.egov.project.config.ProjectConfiguration;
import org.egov.project.helper.ProjectStaffRequestTestBuilder;
import org.egov.project.repository.ProjectStaffRepository;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectStaffServiceCreateTest {

    @InjectMocks
    private ProjectStaffService projectStaffService;

    @Mock
    private ProjectService projectService;

    @Mock
    private IdGenService idGenService;

    @Mock
    private ProjectStaffRepository projectStaffRepository;

    @Mock
    private UserService userService;

    @Mock
    private ProjectConfiguration projectConfiguration;

    private ProjectStaffRequest request;

    @BeforeEach
    void setUp() throws Exception {
        request = ProjectStaffRequestTestBuilder.builder()
                .withOneProjectStaff()
                .build();
        List<String> idList = new ArrayList<>();
        idList.add("some-id");
        lenient().when(idGenService.getIdList(any(RequestInfo.class),
                any(String.class),
                eq("project.staff.id"), eq(""), anyInt()))
                .thenReturn(idList);
        lenient().when(projectConfiguration.getCreateProjectStaffTopic()).thenReturn("create-topic");
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

    @Test
    @DisplayName("should enrich the formatted id in project staff")
    void shouldEnrichTheFormattedIdInProjectStaff() throws Exception {
        mockValidateProjectId();
        mockValidateUsers();

        List<ProjectStaff> projectStaffs = projectStaffService.create(request);

        assertEquals("some-id", projectStaffs.get(0).getId());
    }

    @Test
    @DisplayName("should send the enriched project staff to the kafka topic")
    void shouldSendTheEnrichedProjectStaffToTheKafkaTopic() throws Exception {
        mockValidateProjectId();
        mockValidateUsers();

        projectStaffService.create(request);

        verify(idGenService, times(1)).getIdList(any(RequestInfo.class),
                any(String.class),
                eq("project.staff.id"), eq(""), anyInt());
        verify(projectStaffRepository, times(1)).save(any(List.class), any(String.class));
    }

    @Test
    @DisplayName("should update audit details before pushing the project staff to kafka")
    void shouldUpdateAuditDetailsBeforePushingTheProjectStaffsToKafka() throws Exception {
        mockValidateProjectId();
        mockValidateUsers();

        List<ProjectStaff> projectStaffs = projectStaffService.create(request);

        assertNotNull(projectStaffs.stream().findAny().get().getAuditDetails().getCreatedBy());
        assertNotNull(projectStaffs.stream().findAny().get().getAuditDetails().getCreatedTime());
        assertNotNull(projectStaffs.stream().findAny().get().getAuditDetails().getLastModifiedBy());
        assertNotNull(projectStaffs.stream().findAny().get().getAuditDetails().getLastModifiedTime());
    }

    @Test
    @DisplayName("should set row version as 1 and deleted as false")
    void shouldSetRowVersionAs1AndDeletedAsFalse() throws Exception {
        mockValidateProjectId();
        mockValidateUsers();

        List<ProjectStaff> projectStaffs = projectStaffService.create(request);

        assertEquals(1, projectStaffs.stream().findAny().get().getRowVersion());
        assertFalse(projectStaffs.stream().findAny().get().getIsDeleted());
    }

    @Test
    @DisplayName("should validate correct product id")
    void shouldValidateCorrectProductId() throws Exception {
        mockValidateProjectId();
        mockValidateUsers();

        projectStaffService.create(request);

        verify(projectService, times(1)).validateProjectIds(any(List.class));
    }

    @Test
    @DisplayName("should throw exception for any invalid product id")
    void shouldThrowExceptionForAnyInvalidProductId() throws Exception {
        when(projectService.validateProjectIds(any(List.class))).thenReturn(Collections.emptyList());

        assertThrows(CustomException.class, () -> projectStaffService.create(request));
    }

}