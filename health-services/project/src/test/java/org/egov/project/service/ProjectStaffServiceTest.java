package org.egov.project.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.contract.request.User;
import org.egov.common.producer.Producer;
import org.egov.common.service.IdGenService;
import org.egov.project.repository.ProjectRepository;
import org.egov.project.repository.ProjectStaffRepository;
import org.egov.project.repository.UserRepository;
import org.egov.project.web.models.ProjectStaff;
import org.egov.project.web.models.ProjectStaffRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.egov.project.service.ProjectStaffService.SAVE_KAFKA_TOPIC;
import static org.egov.project.service.ProjectStaffService.UPDATE_KAFKA_TOPIC;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectStaffServiceTest {

    @Mock
    private Producer producer;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private IdGenService idGenService;

    @Mock
    private ProjectStaffRepository projectStaffRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private ProjectStaffService projectStaffService;

    @Mock
    private UserRepository userRepository;


    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        projectStaffService = new ProjectStaffService(
                producer,
                objectMapper,
                idGenService,
                projectStaffRepository,
                projectRepository,
                userRepository
        );
    }

    private void mockIdGeneration(List<String> ids) throws Exception {
        when(idGenService.getIdList(any(), any(), any(), any(), any())).thenReturn(ids);
    }


    private void mockFindById(ProjectStaffRequest projectStaffRequest) {
        when(projectStaffRepository.findById(any(List.class))).thenReturn(projectStaffRequest.getProjectStaff()
                .stream()
                .map(ProjectStaff::getProjectId)
                .collect(Collectors.toList()));
    }

    private void mockValidateProjectIds(ProjectStaffRequest projectStaffRequest) {
        when(projectRepository.validateProjectIds(any(List.class))).thenReturn(
                projectStaffRequest.getProjectStaff()
                        .stream()
                        .map(ProjectStaff::getProjectId)
                        .collect(Collectors.toList())
        );
    }

    private void mockValidateUsers() {
        when(userRepository.searchByUserIds(any(List.class),any(String.class))).thenReturn(getUserList());
    }

    private List<String> getIdGens() {
        List<String> ids = new ArrayList<String>();
        ids.add("1");
        ids.add("2");
        return ids;
    }


    private List<ProjectStaff> getProjectStaffs() {
        ProjectStaff projectStaff = ProjectStaff.builder()
                .projectId("projectId")
                .userId("userId")
                .tenantId("tenantId")
                .rowVersion(1)
                .build();
        List<ProjectStaff> projectStaffList = new ArrayList<ProjectStaff>();
        projectStaffList.add(projectStaff);
        return projectStaffList;
    }

    private List<User> getUserList() {
        return Arrays.asList(
                User.builder().uuid("user1").build()
        );
    }

    private ProjectStaffRequest getProjectStaffRequest(List<ProjectStaff> projectStaffList) {
        return ProjectStaffRequest.builder()
                .projectStaff(projectStaffList)
                .requestInfo(RequestInfo.builder().userInfo(User.builder().build())
                        .build()
                ).build();
    }

    @Test
    @DisplayName("should successfully project staff service")
    void shouldSuccessfullyProjectStaffService() throws Exception {

        List<ProjectStaff> projectStaffList = getProjectStaffs();
        ProjectStaffRequest projectStaffRequest = getProjectStaffRequest(projectStaffList);
        List<String> ids = getIdGens();

        mockIdGeneration(ids);
        mockValidateUsers();
        mockValidateProjectIds(projectStaffRequest);

        projectStaffService.create(projectStaffRequest);

        verify(projectStaffRepository, times(1)).save(projectStaffList,SAVE_KAFKA_TOPIC);
    }

    @Test
    @DisplayName("should send the updates to kafka")
    void shouldSendTheUpdatesToKafka() throws Exception {
        List<ProjectStaff> projectStaffList = getProjectStaffs();
        ProjectStaffRequest projectStaffRequest = getProjectStaffRequest(projectStaffList);

        mockValidateUsers();
        mockValidateProjectIds(projectStaffRequest);
        mockFindById(projectStaffRequest);

        List<ProjectStaff> projectStaffs = projectStaffService.update(projectStaffRequest);

        verify(projectStaffRepository, times(1)).save(projectStaffs,UPDATE_KAFKA_TOPIC);

    }


}