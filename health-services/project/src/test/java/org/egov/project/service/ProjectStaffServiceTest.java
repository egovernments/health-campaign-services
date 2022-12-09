package org.egov.project.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.egov.common.producer.Producer;
import org.egov.project.repository.ProjectStaffRepository;
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
    private ProjectStaffRepository projectStaffRepository;

    @Mock
    private ProjectStaffService projectStaffService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        projectStaffService = new ProjectStaffService(
                producer,
                objectMapper,
                projectStaffRepository
        );
    }

    @Test
    @DisplayName("should successfully project staff service")
    void shouldSuccessfullyProjectStaffService()  {
        ProjectStaff projectStaff = ProjectStaff.builder().build();
        List<ProjectStaff> projectStaffList = new ArrayList<ProjectStaff>();
        projectStaffList.add(projectStaff);
        projectStaffList.add(projectStaff);

        ProjectStaffRequest projectStaffRequest = ProjectStaffRequest.builder()
                .projectStaff(
                        projectStaffList
                ).build();

        when(projectStaffRepository.save(any(ProjectStaff.class))).thenReturn(projectStaff);

        projectStaffService.create(projectStaffRequest);
        verify(projectStaffRepository, times(2)).save(projectStaff);
    }
}