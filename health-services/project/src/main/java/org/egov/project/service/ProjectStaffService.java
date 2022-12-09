package org.egov.project.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.producer.Producer;
import org.egov.project.repository.ProjectStaffRepository;
import org.egov.project.web.models.ProjectStaff;
import org.egov.project.web.models.ProjectStaffRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class ProjectStaffService {

    private final Producer producer;
    private final ObjectMapper objectMapper;
    private final ProjectStaffRepository projectStaffRepository;

    @Autowired
    public ProjectStaffService(
            Producer producer,
            ObjectMapper objectMapper,
            ProjectStaffRepository projectStaffRepository
    ) {
        this.producer = producer;
        this.objectMapper = objectMapper;
        this.projectStaffRepository = projectStaffRepository;
    }

    public List<ProjectStaff> create(ProjectStaffRequest projectStaffRequest){

        List<ProjectStaff> projectStaffs = projectStaffRequest.getProjectStaff();
        // TODO  - Check if project staff data is valid or not
        validateProjectStaff(projectStaffs);

        // TODO - Check if project staff exists in redis or db

        checkIfExists(projectStaffs);

        // TODO - Check if this user is present in the user service

        checkIfUserIsValid(projectStaffs);

        // TODO - CHeck if this project exists or not
        checkIfProjectExists(projectStaffs);

        // TODO - Generate Project Staff Id using id gen
        generateProjectStaffId(projectStaffs);

        saveProjectStaff(projectStaffs);

        return projectStaffs;
    }

    private void saveProjectStaff(List<ProjectStaff> projectStaffs) {
        projectStaffs.forEach((ProjectStaff projectStaff) -> {
            projectStaffRepository.save(projectStaff);
        });
    }

    private void generateProjectStaffId(List<ProjectStaff> projectStaff) {
    }

    private void checkIfProjectExists(List<ProjectStaff> projectStaff) {
    }

    private void checkIfUserIsValid(List<ProjectStaff> projectStaff) {
    }

    private void checkIfExists(List<ProjectStaff> projectStaff) {
    }

    private void validateProjectStaff(List<ProjectStaff> projectStaff) {
    }
}
