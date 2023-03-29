package org.egov.project.service;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.ds.Tuple;
import org.egov.common.models.ErrorDetails;
import org.egov.common.models.project.ProjectFacility;
import org.egov.common.models.project.ProjectFacilityBulkRequest;
import org.egov.common.models.project.ProjectFacilityRequest;
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
import org.egov.project.web.models.ProjectFacilitySearchRequest;
import org.egov.tracer.model.CustomException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

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
            log.error("error occurred while creating project facility: {}", exception.getMessage());
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
            log.error("error occurred while updating project facility", exception);
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
            log.error("error occurred while deleting entities: {}", exception);
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

    public List<ProjectFacility> search(ProjectFacilitySearchRequest projectFacilitySearchRequest,
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
            return projectFacilityRepository.findById(ids, includeDeleted).stream()
                    .filter(lastChangedSince(lastChangedSince))
                    .filter(havingTenantId(tenantId))
                    .filter(includeDeleted(includeDeleted))
                    .collect(Collectors.toList());
        }
        log.info("searching project facility using criteria");
        return projectFacilityRepository.find(projectFacilitySearchRequest.getProjectFacility(),
                limit, offset, tenantId, lastChangedSince, includeDeleted);
    }

}
