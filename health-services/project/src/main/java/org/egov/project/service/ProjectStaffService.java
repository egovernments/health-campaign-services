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
        return projectStaffRepository.save(projectStaff);
    }
}
