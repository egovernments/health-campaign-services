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
import java.util.List;
import java.util.stream.Collectors;

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

    private List<String> getIdGens() {
        List<String> ids = new ArrayList<String>();
        ids.add("1");
        ids.add("2");
        return ids;
    }

    @Test
    @DisplayName("should successfully project staff service")
    void shouldSuccessfullyProjectStaffService() throws Exception {

        ProjectStaff projectStaff = ProjectStaff.builder().build();
        List<ProjectStaff> projectStaffList = new ArrayList<ProjectStaff>();
        projectStaffList.add(projectStaff);

        List<String> ids = getIdGens();

        ProjectStaffRequest projectStaffRequest = ProjectStaffRequest.builder()
                .projectStaff(projectStaffList)
                .requestInfo(RequestInfo.builder().userInfo(User.builder().build())
                .build()
        ).build();

        when(idGenService.getIdList(any(), any(), any(), any(), any())).thenReturn(ids);
        when(projectStaffRepository.save(any(List.class))).thenReturn(projectStaffList);
        when(projectRepository.validateProjectId(any(List.class))).thenReturn(
            projectStaffList
                .stream()
                .map(ProjectStaff::getProjectId)
                .collect(Collectors.toList())
        );

        projectStaffService.create(projectStaffRequest);

        verify(projectStaffRepository, times(1)).save(projectStaffList);
    }

}