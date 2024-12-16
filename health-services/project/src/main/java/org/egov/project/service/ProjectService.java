package org.egov.project.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.egov.common.contract.models.AuditDetails;
import jakarta.validation.Valid;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.models.core.ProjectSearchURLParams;
import org.egov.common.models.project.Project;
import org.egov.common.models.project.ProjectRequest;
import org.egov.common.models.project.ProjectSearchRequest;
import org.egov.common.producer.Producer;
import org.egov.project.config.ProjectConfiguration;
import org.egov.project.repository.ProjectRepository;
import org.egov.project.service.enrichment.ProjectEnrichment;
import org.egov.project.util.ProjectServiceUtil;
import org.egov.project.validator.project.ProjectValidator;
import org.egov.tracer.model.CustomException;
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

    private final ProjectServiceUtil projectServiceUtil;

    private final ObjectMapper objectMapper;

    @Autowired
    public ProjectService(
            ProjectRepository projectRepository,
            ProjectValidator projectValidator, ProjectEnrichment projectEnrichment, ProjectConfiguration projectConfiguration, Producer producer,ProjectServiceUtil projectServiceUtil) {
        this.projectRepository = projectRepository;
        this.projectValidator = projectValidator;
        this.projectEnrichment = projectEnrichment;
        this.projectConfiguration = projectConfiguration;
        this.producer = producer;
        this.projectServiceUtil = projectServiceUtil;
        this.objectMapper = new ObjectMapper();
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

    public List<Project> searchProject(
            ProjectRequest project,
            Integer limit,
            Integer offset,
            String tenantId,
            Long lastChangedSince,
            Boolean includeDeleted,
            Boolean includeAncestors,
            Boolean includeDescendants,
            Long createdFrom,
            Long createdTo
    ) {
        projectValidator.validateSearchProjectRequest(project, limit, offset, tenantId, createdFrom, createdTo);
        List<Project> projects = projectRepository.getProjects(
                project,
                limit,
                offset,
                tenantId,
                lastChangedSince,
                includeDeleted,
                includeAncestors,
                includeDescendants,
                createdFrom,
                createdTo
        );
        return projects;
    }

    public List<Project> searchProject(ProjectSearchRequest projectSearchRequest, @Valid ProjectSearchURLParams urlParams) {
        projectValidator.validateSearchV2ProjectRequest(projectSearchRequest, urlParams);
        return projectRepository.getProjects(projectSearchRequest.getProject(), urlParams);
    }

    public ProjectRequest updateProject(ProjectRequest request) {
        /*
         * Validate the update project request
         */
        projectValidator.validateUpdateProjectRequest(request);
        log.info("Update project request validated");

        /*
         * Search for projects based on project IDs provided in the request
         */
        List<Project> projectsFromDB = searchProject(
            getSearchProjectRequest(request.getProjects(), request.getRequestInfo(), false),
            projectConfiguration.getMaxLimit(), projectConfiguration.getDefaultOffset(),
            request.getProjects().get(0).getTenantId(), null, false, false, false, null, null
        );
        log.info("Fetched projects for update request");

        /*
         * Validate the update project request against the projects fetched from the database
         */
        projectValidator.validateUpdateAgainstDB(request.getProjects(), projectsFromDB);

        /*
         * Process each project in the update request
         */
        for (Project project : request.getProjects()) {
            processProjectUpdate(request, project, projectsFromDB);
        }

        return request;
    }

    private void processProjectUpdate(ProjectRequest request, Project project, List<Project> projectsFromDB) {
        /*
         * Convert project ID to string for comparison
         */
        String projectId = String.valueOf(project.getId());

        /*
         * Find the project from the database that matches the current project ID
         */
        Project projectFromDB = findProjectById(projectId, projectsFromDB);
        boolean isCascadingProjectDateUpdate = request.isCascadingProjectDateUpdate();

        if (projectFromDB != null) {
            /*
             * Merge additional details of the project from the request and project from DB
             */
            projectServiceUtil.mergeAdditionalDetails(project, projectFromDB);

            /*
             * Handle cases where cascading project date update is true
             */
            if (isCascadingProjectDateUpdate) {
                handleUpdateProjectDates(request, project, projectFromDB);
            }
            /*
             * Handle cases for normal update flow
             */
            else {
                handleNormalUpdate(request, project, projectFromDB);
            }
        }
    }

    private Project findProjectById(String projectId, List<Project> projectsFromDB) {
        /*
         * Find and return the project with the matching ID from the list of projects fetched from the database
         */
        return projectsFromDB.stream()
            .filter(p -> projectId.equals(String.valueOf(p.getId())))
            .findFirst()
            .orElse(null);
    }


    private void handleNormalUpdate(ProjectRequest request, Project project, Project projectFromDB) {
        /*
         * Ensure that start and end dates are not being updated when flag is false
         */
        if (!project.getStartDate().equals(projectFromDB.getStartDate()) ||
            !project.getEndDate().equals(projectFromDB.getEndDate())) {
            throw new CustomException("PROJECT_CASCADE_UPDATE_DATE_ERROR",
                "Can't Update Date Range if Cascade Project Date Update  false");
        }

        /*
         * Enrich the project with values other than the start, end dates, and AdditionalDetails,
         * and push the update to the message broker
         */
        projectEnrichment.enrichProjectOnUpdate(request, project, projectFromDB);
        producer.push(projectConfiguration.getUpdateProjectTopic(), request);
    }

    private void handleUpdateProjectDates(ProjectRequest request, Project project, Project projectFromDB) {
        /*
         * Save original values of start date, end date, and additional details
         */
        Long originalStartDate = projectFromDB.getStartDate();
        Long originalEndDate = projectFromDB.getEndDate();
        Object originalAdditionalDetails = projectFromDB.getAdditionalDetails();
        AuditDetails originalAuditDetails = projectFromDB.getAuditDetails();


        /*
         * Update the project with new start date, end date, and additional details
         */
        projectFromDB.setStartDate(project.getStartDate());
        projectFromDB.setEndDate(project.getEndDate());
        projectFromDB.setAdditionalDetails(project.getAdditionalDetails());
        projectFromDB.setAuditDetails(project.getAuditDetails());

        /*
         * Ensure that no other properties are being updated besides the start and end dates
         */
        if (!objectMapper.valueToTree(projectFromDB).equals(objectMapper.valueToTree(project))) {
            throw new CustomException(
                "PROJECT_CASCADE_UPDATE_ERROR",
                "Can only update Project dates and additional details if cascade Project date update true"
            );
        }

        /*
         * Restore original values of start date, end date, and additional details
         */
        projectFromDB.setStartDate(originalStartDate);
        projectFromDB.setEndDate(originalEndDate);
        projectFromDB.setAdditionalDetails(originalAdditionalDetails);
        projectFromDB.setAuditDetails(originalAuditDetails);

        /*
         * Update lastModifiedTime and lastModifiedBy for the project
         */
        projectEnrichment.enrichProjectRequestOnUpdate(project, projectFromDB, request.getRequestInfo());

        /*
         * Check and enrich cascading project dates and push the update to the message broker
         */
        checkAndEnrichCascadingProjectDates(request, project);
        producer.push(projectConfiguration.getUpdateProjectDateTopic(), request);
    }


    /**
     * Checks and enriches cascading project dates.
     *
     * @param request The project request containing projects and request information.
     */
    private void checkAndEnrichCascadingProjectDates(ProjectRequest request, Project project) {
        /*
         * Retrieve tenant ID from the first project in the request
         */
        String tenantId = request.getProjects().get(0).getTenantId();
        String projectId = String.valueOf(project.getId());

        /*
         * Fetch projects from the database with ancestors and descendants
         */
        List<Project> projectsFromDbWithAncestorsAndDescendants = searchProject(
            getSearchProjectRequest(request.getProjects(), request.getRequestInfo(), false),
            projectConfiguration.getMaxLimit(),
            projectConfiguration.getDefaultOffset(),
            tenantId,
            null,
            false,
            true,
            true,
            null,
            null
        );

        /*
         * Create a map of projects from the database with ancestors and descendants
         */
        Map<String, Project> projectFromDbWithAncestorsAndDescendantsMap = projectServiceUtil.createProjectMap(projectsFromDbWithAncestorsAndDescendants);
        Project projectFromDbWithAncestorsAndDescendants = projectFromDbWithAncestorsAndDescendantsMap.get(projectId);

        /*
         * Enrich project cascading dates based on the retrieved data
         */
        projectEnrichment.enrichProjectCascadingDatesOnUpdate(project, projectFromDbWithAncestorsAndDescendants);
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


    public Integer countAllProjects(ProjectSearchRequest projectSearchRequest, ProjectSearchURLParams urlParams) {
        return projectRepository.getProjectCount(projectSearchRequest.getProject(), urlParams);
    }
}
