package org.egov.project.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Collections;
import org.egov.common.contract.models.AuditDetails;
import jakarta.validation.Valid;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.exception.InvalidTenantIdException;
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
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.egov.common.utils.CommonUtils.getTenantId;

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

    private final RedisTemplate<String, String> redisTemplate;





  @Autowired
  public ProjectService(
      ProjectRepository projectRepository,
      ProjectValidator projectValidator, ProjectEnrichment projectEnrichment, ProjectConfiguration projectConfiguration, Producer producer, ProjectServiceUtil projectServiceUtil, RedisTemplate<String, String> redisTemplate) {
    this.projectRepository = projectRepository;
    this.projectValidator = projectValidator;
    this.projectEnrichment = projectEnrichment;
    this.projectConfiguration = projectConfiguration;
    this.producer = producer;
    this.projectServiceUtil = projectServiceUtil;
    this.objectMapper = new ObjectMapper();
    this.redisTemplate = redisTemplate;
  }

    public List<String> validateProjectIds(String tenantId, List<String> productIds) throws InvalidTenantIdException {
        return projectRepository.validateIds(tenantId, productIds, "id");
    }

    public List<Project> findByIds(String tenantId, List<String> projectIds) throws InvalidTenantIdException {
        return projectRepository.findById(tenantId, projectIds);
    }

  public ProjectRequest createProject(ProjectRequest projectRequest) throws  InvalidTenantIdException {
    projectValidator.validateCreateProjectRequest(projectRequest);
//Get parent projects if "parent" is present (For enrichment of projectHierarchy)
    List<Project> parentProjects = getParentProjects(projectRequest);
    //Validate Parent in request against projects fetched form database
    if (parentProjects != null) {
      projectValidator.validateParentAgainstDB(projectRequest.getProjects(), parentProjects);
    }
    projectEnrichment.enrichProjectOnCreate(projectRequest, parentProjects);
    log.info("Enriched with Project Number, Ids and AuditDetails");
     String tenantId = getTenantId(projectRequest.getProjects());
    producer.push(tenantId, projectConfiguration.getSaveProjectTopic(), projectRequest);
    log.info("Pushed to kafka");

    // ✅ Save project IDs in Redis after Kafka push
    for (Project project : projectRequest.getProjects()) {
      String redisKey = projectConfiguration.getProjectCacheKey() + project.getId();

      if (StringUtils.isNotBlank(project.getProjectHierarchy())) {
        try {
          redisTemplate.opsForValue()
              .set(redisKey, project.getProjectHierarchy(), Duration.ofDays(1));
          log.info("Cached projectHierarchy for project {} in Redis", project.getId());
        } catch (Exception ex) {
          log.error("Failed to cache projectHierarchy for project {}: {}", project.getId(), ex.getMessage(), ex);
        }
      } else {
        log.warn("ProjectHierarchy is blank for project {}, not caching in Redis", project.getId());
      }
    }

    return projectRequest;
  }

    /**
     * Search for projects based on various criteria
     * @param isAncestorProjectId When true, treats the project IDs in the search criteria as ancestor project IDs
     * and returns all projects (including children) under these ancestors
     */
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
            Long createdTo,
            boolean isAncestorProjectId
    ) throws InvalidTenantIdException {
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
                createdTo,
                isAncestorProjectId
        );
        return projects;
    }

    public List<Project> searchProject(ProjectSearchRequest projectSearchRequest, @Valid ProjectSearchURLParams urlParams) throws InvalidTenantIdException {
        projectValidator.validateSearchV2ProjectRequest(projectSearchRequest, urlParams);
        return projectRepository.getProjects(projectSearchRequest.getProject(), urlParams);
    }

    public ProjectRequest updateProject(ProjectRequest request) throws InvalidTenantIdException {
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
            request.getProjects().get(0).getTenantId(), null, false, false, false, null, null, false
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

    private void processProjectUpdate(ProjectRequest request, Project project, List<Project> projectsFromDB) throws InvalidTenantIdException {
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
        String tenantId = project.getTenantId();
        producer.push(tenantId, projectConfiguration.getUpdateProjectTopic(), request);
    }

    private void handleUpdateProjectDates(ProjectRequest request, Project project, Project projectFromDB) throws InvalidTenantIdException {
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
        producer.push(project.getTenantId(), projectConfiguration.getUpdateProjectDateTopic(), request);
    }


    /**
     * Checks and enriches cascading project dates.
     *
     * @param request The project request containing projects and request information.
     */
    private void checkAndEnrichCascadingProjectDates(ProjectRequest request, Project project) throws InvalidTenantIdException {
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
            null,
            false
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
  private List<Project> getParentProjects(ProjectRequest projectRequest) throws InvalidTenantIdException{
    List<Project> parentProjects = new ArrayList<>();

    List<Project> projectsWithParent = projectRequest.getProjects().stream()
        .filter(p -> StringUtils.isNotBlank(p.getParent()))
        .collect(Collectors.toList());

    if (projectsWithParent.isEmpty()) {
      return Collections.emptyList();
    }

    List<String> missingParentIds = new ArrayList<>();

    for (Project project : projectsWithParent) {
      String parentId = project.getParent();
      String redisKey = projectConfiguration.getProjectCacheKey() + parentId;

      try {
        String cachedHierarchy = redisTemplate.opsForValue().get(redisKey);
        if (StringUtils.isNotBlank(cachedHierarchy)) {
          log.info("Parent project hierarchy for {} fetched from Redis", parentId);

          Project parent = new Project();
          parent.setId(parentId);
          parent.setProjectHierarchy(cachedHierarchy);
          parentProjects.add(parent);
        } else {
          log.info("No hierarchy found in Redis for parent {}", parentId);
          missingParentIds.add(parentId);
        }
      } catch (Exception ex) {
        log.error("Redis error while fetching hierarchy for parent {}: {}", parentId, ex.getMessage(), ex);
        missingParentIds.add(parentId); // fallback to DB or ignore
      }
    }

    if (!missingParentIds.isEmpty()) {
      List<Project> dbQueryProjects = missingParentIds.stream()
          .map(id -> Project.builder().id(id)
              .tenantId(projectRequest.getProjects().get(0).getTenantId()).build())
          .collect(Collectors.toList());

      ProjectRequest searchRequest = getSearchProjectRequest(dbQueryProjects,
          projectRequest.getRequestInfo(), false);

      List<Project> dbProjects = searchProject(
          searchRequest,
          projectConfiguration.getMaxLimit(),
          projectConfiguration.getDefaultOffset(),
          searchRequest.getProjects().get(0).getTenantId(),
          null, false, false, false, null, null, false
      );

      for (Project parent : dbProjects) {
        String redisKey = projectConfiguration.getProjectCacheKey() + parent.getId();
        try {
          redisTemplate.opsForValue().set(redisKey, parent.getProjectHierarchy(), Duration.ofDays(1));
        } catch (Exception ex) {
          log.error("Redis error while setting hierarchy for project {}: {}", parent.getId(), ex.getMessage(), ex);
        }
        parentProjects.add(parent);
      }

    }

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
    public Integer countAllProjects(ProjectRequest project, String tenantId, Long lastChangedSince, Boolean includeDeleted, Long createdFrom, Long createdTo, boolean isAncestorProjectId) throws InvalidTenantIdException {
        return projectRepository.getProjectCount(project, tenantId, lastChangedSince, includeDeleted, createdFrom, createdTo, isAncestorProjectId);
    }


    public Integer countAllProjects(ProjectSearchRequest projectSearchRequest, ProjectSearchURLParams urlParams) throws InvalidTenantIdException {
        return projectRepository.getProjectCount(projectSearchRequest.getProject(), urlParams);
    }
}
