package org.egov.project.service;

import org.egov.project.web.models.ProjectResource;
import org.egov.project.web.models.ProjectResourceRequest;
import org.springframework.stereotype.Service;

@Service
public class ProjectResourceService {
    public ProjectResource create(ProjectResourceRequest request) {
        return request.getProjectResource();
    }
}
