package org.egov.project.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.egov.common.ds.Tuple;
import org.egov.common.models.ErrorDetails;
import org.egov.common.models.core.SearchResponse;
import org.egov.common.models.project.ProjectFacility;
import org.egov.common.models.project.Project;
import org.egov.common.models.project.ProjectFacilityBulkRequest;
import org.egov.common.models.project.ProjectFacilityRequest;
import org.egov.common.models.project.ProjectFacilitySearch;
import org.egov.common.models.project.ProjectFacilitySearchRequest;
import org.egov.common.models.project.ProjectRequest;
import org.egov.common.service.IdGenService;
import org.egov.common.service.UserService;
import org.egov.common.utils.CommonUtils;
import org.egov.common.validator.Validator;
import org.egov.project.config.ProjectConfiguration;
import org.egov.project.repository.ProjectFacilityRepository;
import org.egov.project.service.enrichment.ProjectFacilityEnrichmentService;
import org.egov.project.validator.facility.PfFacilityIdValidator;
import org.egov.project.validator.facility.PfIsDeletedValidator;
import org.egov.project.validator.facility.PfNonExistentEntityValidator;
import org.egov.project.validator.facility.PfNullIdValidator;
import org.egov.project.validator.facility.PfProjectIdValidator;
import org.egov.project.validator.facility.PfRowVersionValidator;
import org.egov.project.validator.facility.PfUniqueCombinationValidator;
import org.egov.project.validator.facility.PfUniqueEntityValidator;
import org.egov.tracer.model.CustomException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import static org.egov.common.utils.CommonUtils.handleErrors;
import static org.egov.common.utils.CommonUtils.havingTenantId;
import static org.egov.common.utils.CommonUtils.includeDeleted;
import static org.egov.common.utils.CommonUtils.isSearchByIdOnly;
import static org.egov.common.utils.CommonUtils.lastChangedSince;
import static org.egov.common.utils.CommonUtils.notHavingErrors;
import static org.egov.common.utils.CommonUtils.populateErrorDetails;
import static org.egov.project.Constants.SET_PROJECT_FACILITIES;
import static org.egov.project.Constants.VALIDATION_ERROR;

@Service
@Slf4j
public class ProjectFacilityService {

    private final IdGenService idGenService;

    private final ProjectFacilityRepository projectFacilityRepository;

    private final ProjectService projectService;

    private final UserService userService;

    private final ProjectConfiguration projectConfiguration;

    private final ProjectFacilityEnrichmentService enrichmentService;

    private final List<Validator<ProjectFacilityBulkRequest, ProjectFacility>> validators;

    private final Predicate<Validator<ProjectFacilityBulkRequest, ProjectFacility>> isApplicableForCreate = validator ->
            validator.getClass().equals(PfFacilityIdValidator.class)
                    || validator.getClass().equals(PfProjectIdValidator.class)
                    || validator.getClass().equals(PfUniqueCombinationValidator.class);

    private final Predicate<Validator<ProjectFacilityBulkRequest, ProjectFacility>> isApplicableForUpdate = validator ->
            validator.getClass().equals(PfFacilityIdValidator.class)
                    || validator.getClass().equals(PfProjectIdValidator.class)
                    || validator.getClass().equals(PfNullIdValidator.class)
                    || validator.getClass().equals(PfIsDeletedValidator.class)
                    || validator.getClass().equals(PfRowVersionValidator.class)
                    || validator.getClass().equals(PfNonExistentEntityValidator.class)
                    || validator.getClass().equals(PfUniqueEntityValidator.class)
                    || validator.getClass().equals(PfUniqueCombinationValidator.class);

    private final Predicate<Validator<ProjectFacilityBulkRequest, ProjectFacility>> isApplicableForDelete = validator ->
            validator.getClass().equals(PfNullIdValidator.class)
                    || validator.getClass().equals(PfNonExistentEntityValidator.class);

    @Autowired
    public ProjectFacilityService(
            IdGenService idGenService,
            ProjectFacilityRepository projectFacilityRepository,
            ProjectService projectService,
            UserService userService,
            ProjectConfiguration projectConfiguration,
            ProjectFacilityEnrichmentService enrichmentService, List<Validator<ProjectFacilityBulkRequest, ProjectFacility>> validators) {
        this.idGenService = idGenService;
        this.projectFacilityRepository = projectFacilityRepository;
        this.projectService = projectService;
        this.userService = userService;
        this.projectConfiguration = projectConfiguration;
        this.enrichmentService = enrichmentService;
        this.validators = validators;
    }

    public ProjectFacility create(ProjectFacilityRequest request) {
        log.info("received request to create project facility");
        ProjectFacilityBulkRequest bulkRequest = ProjectFacilityBulkRequest.builder().requestInfo(request.getRequestInfo())
                .projectFacilities(Collections.singletonList(request.getProjectFacility())).build();
        log.info("creating bulk request");
        return create(bulkRequest, false).get(0);
    }


    public List<ProjectFacility> create(ProjectFacilityBulkRequest request, boolean isBulk) {
        log.info("received request to create bulk project facility");
        Tuple<List<ProjectFacility>, Map<ProjectFacility, ErrorDetails>> tuple = validate(validators,
                isApplicableForCreate, request,
                isBulk);

        Map<ProjectFacility, ErrorDetails> errorDetailsMap = tuple.getY();
        List<ProjectFacility> validEntities = tuple.getX();
        try {
            if (!validEntities.isEmpty()) {
                log.info("processing {} valid entities", validEntities.size());
                enrichmentService.create(validEntities, request);
                projectFacilityRepository.save(validEntities, projectConfiguration.getCreateProjectFacilityTopic());
                log.info("successfully created project facility");
            }
        } catch (Exception exception) {
            log.error("error occurred while creating project facility: {}", ExceptionUtils.getStackTrace(exception));
            populateErrorDetails(request, errorDetailsMap, validEntities, exception, SET_PROJECT_FACILITIES);
        }

        handleErrors(errorDetailsMap, isBulk, VALIDATION_ERROR);

        return validEntities;
    }


    public ProjectFacility update(ProjectFacilityRequest request) {
        log.debug("received request to update project facility");
        ProjectFacilityBulkRequest bulkRequest = ProjectFacilityBulkRequest.builder().requestInfo(request.getRequestInfo())
                .projectFacilities(Collections.singletonList(request.getProjectFacility())).build();
        log.info("creating bulk request");
        return update(bulkRequest, false).get(0);
    }

    public List<ProjectFacility> update(ProjectFacilityBulkRequest request, boolean isBulk) {
        log.info("received request to update bulk project facility");
        Tuple<List<ProjectFacility>, Map<ProjectFacility, ErrorDetails>> tuple = validate(validators,
                isApplicableForUpdate, request,
                isBulk);

        Map<ProjectFacility, ErrorDetails> errorDetailsMap = tuple.getY();
        List<ProjectFacility> validEntities = tuple.getX();
        try {
            if (!validEntities.isEmpty()) {
                log.info("processing {} valid entities", validEntities.size());
                enrichmentService.update(validEntities, request);
                projectFacilityRepository.save(validEntities, projectConfiguration.getUpdateProjectFacilityTopic());
                log.info("successfully updated bulk project facility");
            }
        } catch (Exception exception) {
            log.error("error occurred while updating project facility", ExceptionUtils.getStackTrace(exception));
            populateErrorDetails(request, errorDetailsMap, validEntities, exception, SET_PROJECT_FACILITIES);
        }

        handleErrors(errorDetailsMap, isBulk, VALIDATION_ERROR);

        return validEntities;
    }

    public ProjectFacility delete(ProjectFacilityRequest request) {
        log.info("received request to delete a project facility");
        ProjectFacilityBulkRequest bulkRequest = ProjectFacilityBulkRequest.builder().requestInfo(request.getRequestInfo())
                .projectFacilities(Collections.singletonList(request.getProjectFacility())).build();
        log.info("creating bulk request");
        return delete(bulkRequest, false).get(0);
    }

    public List<ProjectFacility> delete(ProjectFacilityBulkRequest request, boolean isBulk) {
        Tuple<List<ProjectFacility>, Map<ProjectFacility, ErrorDetails>> tuple = validate(validators,
                isApplicableForDelete, request,
                isBulk);

        Map<ProjectFacility, ErrorDetails> errorDetailsMap = tuple.getY();
        List<ProjectFacility> validEntities = tuple.getX();
        try {
            if (!validEntities.isEmpty()) {
                log.info("processing {} valid entities", validEntities.size());
                enrichmentService.delete(validEntities, request);
                projectFacilityRepository.save(validEntities, projectConfiguration.getDeleteProjectFacilityTopic());
                log.info("successfully deleted entities");
            }
        } catch (Exception exception) {
            log.error("error occurred while deleting entities: {}", ExceptionUtils.getStackTrace(exception));
            populateErrorDetails(request, errorDetailsMap, validEntities, exception, SET_PROJECT_FACILITIES);
        }

        handleErrors(errorDetailsMap, isBulk, VALIDATION_ERROR);

        return validEntities;
    }

    private Tuple<List<ProjectFacility>, Map<ProjectFacility, ErrorDetails>> validate(List<Validator<ProjectFacilityBulkRequest, ProjectFacility>> validators,
                                                                Predicate<Validator<ProjectFacilityBulkRequest, ProjectFacility>> applicableValidators,
                                                                ProjectFacilityBulkRequest request, boolean isBulk) {
        log.info("validating request");
        Map<ProjectFacility, ErrorDetails> errorDetailsMap = CommonUtils.validate(validators,
                applicableValidators, request,
                SET_PROJECT_FACILITIES);
        if (!errorDetailsMap.isEmpty() && !isBulk) {
            log.error("validation error occurred. error details: {}", errorDetailsMap.values().toString());
            throw new CustomException(VALIDATION_ERROR, errorDetailsMap.values().toString());
        }
        List<ProjectFacility> validEntities = request.getProjectFacilities().stream()
                .filter(notHavingErrors()).collect(Collectors.toList());
        log.info("validation successful, found valid project facility");
        return new Tuple<>(validEntities, errorDetailsMap);
    }

    public SearchResponse<ProjectFacility> search(ProjectFacilitySearchRequest projectFacilitySearchRequest,
                                                  Integer limit,
                                                  Integer offset,
                                                  String tenantId,
                                                  Long lastChangedSince,
                                                  Boolean includeDeleted) throws Exception {
        log.info("received request to search project facility");

        if (isSearchByIdOnly(projectFacilitySearchRequest.getProjectFacility())) {
            log.info("searching project facility by id");
            List<String> ids = projectFacilitySearchRequest.getProjectFacility().getId();
            log.info("fetching project facility with ids: {}", ids);
            List<ProjectFacility> projectfacilities = projectFacilityRepository.findById(tenantId, ids, includeDeleted).stream()
                    .filter(lastChangedSince(lastChangedSince))
                    .filter(havingTenantId(tenantId))
                    .filter(includeDeleted(includeDeleted))
                    .collect(Collectors.toList());
            return SearchResponse.<ProjectFacility>builder().response(projectfacilities).build();
        }
        log.info("searching project facility using criteria");
        return projectFacilityRepository.findWithCount(projectFacilitySearchRequest.getProjectFacility(),
                limit, offset, tenantId, lastChangedSince, includeDeleted);
    }

    /**
     * Searches for facility IDs grouped by boundary type within the project hierarchy tree.
     *
     * Given a project ID and a list of boundary types, this method:
     * 1. Fetches the root project to get its projectHierarchy (for ancestor IDs)
     * 2. Queries descendants directly via SQL join (PROJECT + PROJECT_ADDRESS + PROJECT_FACILITY)
     * 3. Queries ancestors directly via SQL join using IDs parsed from projectHierarchy
     * 4. Returns a merged map of boundaryType → list of facilityIds
     */
    public Map<String, List<String>> searchByHierarchy(
            ProjectFacilitySearchRequest request,
            String tenantId) {
        log.info("received request to search project facility by hierarchy");
        ProjectFacilitySearch searchCriteria = request.getProjectFacility();
        List<String> boundaryTypes = searchCriteria.getBoundaryTypes();

        if (searchCriteria.getProjectId() == null || searchCriteria.getProjectId().isEmpty()) {
            throw new CustomException("INVALID_PROJECT_ID", "projectId is required for hierarchy search");
        }
        String projectId = searchCriteria.getProjectId().get(0);

        // Step 1: Fetch only the root project to get its projectHierarchy
        Project searchProject = Project.builder().id(projectId).tenantId(tenantId).build();
        ProjectRequest projectRequest = ProjectRequest.builder()
                .requestInfo(request.getRequestInfo())
                .projects(Collections.singletonList(searchProject))
                .build();

        List<Project> projects;
        try {
            projects = projectService.searchProject(
                    projectRequest,
                    1, 0, tenantId, null, false,
                    false,   // no ancestors
                    false,   // no descendants
                    null, null, false
            );
        } catch (Exception e) {
            log.error("error searching root project: {}", e.getMessage());
            throw new CustomException("PROJECT_SEARCH_ERROR",
                    "Error searching root project: " + e.getMessage());
        }

        if (projects == null || projects.isEmpty()) {
            log.info("no project found for id: {}", projectId);
            return Collections.emptyMap();
        }

        Project mainProject = projects.get(0);
        Map<String, List<String>> facilityMap = new HashMap<>();

        try {
            // Step 2: Query descendants — projects whose hierarchy starts with this projectId
            Map<String, List<String>> descendantFacilities =
                    projectFacilityRepository.findFacilitiesByDescendants(projectId, boundaryTypes, tenantId);
            descendantFacilities.forEach((bt, ids) ->
                    facilityMap.computeIfAbsent(bt, k -> new ArrayList<>()).addAll(ids));

            // Step 3: Query ancestors — parse IDs from projectHierarchy and include the project itself
            List<String> ancestorIds = new ArrayList<>();
            ancestorIds.add(projectId);
            if (StringUtils.isNotBlank(mainProject.getProjectHierarchy())) {
                String[] hierarchyParts = mainProject.getProjectHierarchy().split("\\.");
                for (String part : hierarchyParts) {
                    if (!part.equals(projectId)) {
                        ancestorIds.add(part);
                    }
                }
            }

            Map<String, List<String>> ancestorFacilities =
                    projectFacilityRepository.findFacilitiesByAncestors(ancestorIds, boundaryTypes, tenantId);
            ancestorFacilities.forEach((bt, ids) ->
                    facilityMap.computeIfAbsent(bt, k -> new ArrayList<>()).addAll(ids));

        } catch (Exception e) {
            log.error("error searching facilities by hierarchy: {}", e.getMessage());
            throw new CustomException("FACILITY_HIERARCHY_SEARCH_ERROR",
                    "Error searching facilities by hierarchy: " + e.getMessage());
        }

        log.info("hierarchy search complete. boundary types found: {}", facilityMap.keySet());
        return facilityMap;
    }
}
