package org.egov.project.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.producer.Producer;
import org.egov.project.repository.ProjectStaffRepository;
import org.egov.project.web.models.ProjectStaff;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

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

    ProjectStaff create(ProjectStaff projectStaff){
        
        // TODO  - Check if project staff data is valid or not
        validateProjectStaff(projectStaff);

        // TODO - Check if project staff exists in redis or db

        checkIfExists(projectStaff);

        // TODO - Check if this user is present in the user service

        checkIfUserIsValid(projectStaff);

        // TODO - CHeck if this project exists or not
        checkIfProjectExists(projectStaff);

        // TODO - Generate Project Staff Id using id gen
        generateProjectStaffId(projectStaff);

        ProjectStaff savedStaff = projectStaffRepository.save(projectStaff);

        return savedStaff;
    }

    private void generateProjectStaffId(ProjectStaff projectStaff) {
    }

    private void checkIfProjectExists(ProjectStaff projectStaff) {
    }

    private void checkIfUserIsValid(ProjectStaff projectStaff) {
    }

    private void checkIfExists(ProjectStaff projectStaff) {
    }

    private void validateProjectStaff(ProjectStaff projectStaff) {
    }
}
