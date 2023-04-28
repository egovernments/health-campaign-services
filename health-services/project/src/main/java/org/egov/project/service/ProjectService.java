package org.egov.project.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.models.project.Project;
import org.egov.common.models.project.ProjectRequest;
import org.egov.common.producer.Producer;
import org.egov.project.config.ProjectConfiguration;
import org.egov.project.repository.ProjectRepository;
import org.egov.project.service.enrichment.ProjectEnrichment;
import org.egov.project.validator.project.ProjectValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ProjectService {

    private final ProjectRepository projectRepository;

    private final ProjectValidator projectValidator;

    private final ProjectEnrichment projectEnrichment;

    private final ProjectConfiguration projectConfiguration;

    private final Producer producer;

    @Autowired
    public ProjectService(
            ProjectRepository projectRepository,
            ProjectValidator projectValidator, ProjectEnrichment projectEnrichment, ProjectConfiguration projectConfiguration, Producer producer) {
        this.projectRepository = projectRepository;
        this.projectValidator = projectValidator;
        this.projectEnrichment = projectEnrichment;
        this.projectConfiguration = projectConfiguration;
        this.producer = producer;
    }

    public List<String> validateProjectIds(List<String> productIds) {
        return projectRepository.validateIds(productIds, "id");
    }

    public List<Project> findByIds(List<String> projectIds){
        return projectRepository.findById(projectIds);
    }

    public ProjectRequest createProject(ProjectRequest projectRequest) {
        projectValidator.validateCreateProjectRequest(projectRequest);
        //Get parent projects if "parent" is present (For enrichment of projectHierarchy)
        List<Project> parentProjects = getParentProjects(projectRequest);
        //Validate Parent in request against projects fetched form database
        if (parentProjects != null)
            projectValidator.validateParentAgainstDB(projectRequest.getProjects(), parentProjects);
        projectEnrichment.enrichProjectOnCreate(projectRequest, parentProjects);
        log.info("Enriched with Project Number, Ids and AuditDetails");
        producer.push(projectConfiguration.getSaveProjectTopic(), projectRequest);
        log.info("Pushed to kafka");
        return projectRequest;
    }

    public List<Project> searchProject(ProjectRequest project, Integer limit, Integer offset, String tenantId, Long lastChangedSince, Boolean includeDeleted, Boolean includeAncestors, Boolean includeDescendants, Long createdFrom, Long createdTo) {
        projectValidator.validateSearchProjectRequest(project, limit, offset, tenantId, createdFrom, createdTo);
        List<Project> projects = projectRepository.getProjects(project, limit, offset, tenantId, lastChangedSince, includeDeleted, includeAncestors, includeDescendants, createdFrom, createdTo);
        return projects;
    }

    public ProjectRequest updateProject(ProjectRequest project) {
        projectValidator.validateUpdateProjectRequest(project);
        log.info("Update project request validated");
        //Search projects based on project ids
        List<Project> projectsFromDB = searchProject(getSearchProjectRequest(project.getProjects(), project.getRequestInfo(), false), projectConfiguration.getMaxLimit(), projectConfiguration.getDefaultOffset(), project.getProjects().get(0).getTenantId(), null, false, false, false, null, null);
        log.info("Fetched projects for update request");
        //Validate Update project request against projects fetched form database
        projectValidator.validateUpdateAgainstDB(project.getProjects(), projectsFromDB);
        projectEnrichment.enrichProjectOnUpdate(project, projectsFromDB);
        log.info("Enriched with project Number, Ids and AuditDetails");
        producer.push(projectConfiguration.getUpdateProjectTopic(), project);
        log.info("Pushed to kafka");

        return project;
    }

    /* Search for parent projects based on "parent" field and returns parent projects  */
    private List<Project> getParentProjects(ProjectRequest projectRequest) {
        List<Project> parentProjects = null;
        List<Project> projectsForSearchRequest = projectRequest.getProjects().stream().filter(p -> StringUtils.isNotBlank(p.getParent())).collect(Collectors.toList());
        if (projectsForSearchRequest.size() > 0) {
            parentProjects = searchProject(getSearchProjectRequest(projectsForSearchRequest, projectRequest.getRequestInfo(), true), projectConfiguration.getMaxLimit(), projectConfiguration.getDefaultOffset(), projectRequest.getProjects().get(0).getTenantId(), null, false, false, false, null, null);
        }
        log.info("Fetched parent projects from DB");
        return parentProjects;
    }

    /* Construct Project Request object for search which contains project id and tenantId */
    private ProjectRequest getSearchProjectRequest(List<Project> projects, RequestInfo requestInfo, Boolean isParentProjectSearch) {
        List<Project> projectList = new ArrayList<>();

        for (Project project: projects) {
            String projectId = isParentProjectSearch ? project.getParent() : project.getId();
            Project newProject = Project.builder()
                    .id(projectId)
                    .tenantId(project.getTenantId())
                    .build();

            projectList.add(newProject);
        }
        return ProjectRequest.builder()
                .requestInfo(requestInfo)
                .projects(projectList)
                .build();
    }

    /**
     * @return Count of List of matching projects
     */
    public Integer countAllProjects(ProjectRequest project, String tenantId, Long lastChangedSince, Boolean includeDeleted, Long createdFrom, Long createdTo) {
        return projectRepository.getProjectCount(project, tenantId, lastChangedSince, includeDeleted, createdFrom, createdTo);
    }
}
