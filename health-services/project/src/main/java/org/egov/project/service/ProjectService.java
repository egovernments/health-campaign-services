package org.egov.project.service;

import lombok.extern.slf4j.Slf4j;
import org.egov.project.repository.ProjectRepository;
import org.egov.project.web.models.Project;
import org.egov.project.web.models.ProjectSearchRequest;
import org.egov.project.web.models.ProjectStaff;
import org.egov.project.web.models.ProjectStaffSearchRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.egov.common.utils.CommonUtils.havingTenantId;
import static org.egov.common.utils.CommonUtils.includeDeleted;
import static org.egov.common.utils.CommonUtils.isSearchByIdOnly;
import static org.egov.common.utils.CommonUtils.lastChangedSince;

@Service
@Slf4j
public class ProjectService {

    private final ProjectRepository projectRepository;

    @Autowired
    public ProjectService(
            ProjectRepository projectRepository
    ) {
        this.projectRepository = projectRepository;
    }

    public List<String> validateProjectIds(List<String> productIds) {
        return projectRepository.validateProjectIds(productIds);
    }

    public List<Project> findByIds(List<String> projectIds){
        return projectRepository.findById(projectIds);
    }
}
