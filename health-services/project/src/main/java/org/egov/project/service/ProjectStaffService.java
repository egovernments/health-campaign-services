package org.egov.project.service;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.ds.Tuple;
import org.egov.common.models.ErrorDetails;
import org.egov.common.service.IdGenService;
import org.egov.common.service.UserService;
import org.egov.common.utils.CommonUtils;
import org.egov.common.validator.Validator;
import org.egov.project.config.ProjectConfiguration;
import org.egov.project.repository.ProjectStaffRepository;
import org.egov.project.service.enrichment.ProjectStaffEnrichmentService;
import org.egov.project.validator.staff.PsIsDeletedValidator;
import org.egov.project.validator.staff.PsNonExistentEntityValidator;
import org.egov.project.validator.staff.PsNullIdValidator;
import org.egov.project.validator.staff.PsProjectIdValidator;
import org.egov.project.validator.staff.PsRowVersionValidator;
import org.egov.project.validator.staff.PsUniqueEntityValidator;
import org.egov.project.validator.staff.PsUserIdValidator;
import org.egov.project.web.models.ProjectStaff;
import org.egov.project.web.models.ProjectStaffBulkRequest;
import org.egov.project.web.models.ProjectStaffRequest;
import org.egov.project.web.models.ProjectStaffSearchRequest;
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
import static org.egov.project.Constants.SET_STAFF;
import static org.egov.project.Constants.SET_TASKS;
import static org.egov.project.Constants.VALIDATION_ERROR;

@Service
@Slf4j
public class ProjectStaffService {

    private final IdGenService idGenService;

    private final ProjectStaffRepository projectStaffRepository;

    private final ProjectService projectService;

    private final UserService userService;

    private final ProjectConfiguration projectConfiguration;

    private final ProjectStaffEnrichmentService enrichmentService;

    private final List<Validator<ProjectStaffBulkRequest, ProjectStaff>> validators;

    private final Predicate<Validator<ProjectStaffBulkRequest, ProjectStaff>> isApplicableForCreate = validator ->
            validator.getClass().equals(PsUserIdValidator.class)
                    || validator.getClass().equals(PsProjectIdValidator.class);

    private final Predicate<Validator<ProjectStaffBulkRequest, ProjectStaff>> isApplicableForUpdate = validator ->
            validator.getClass().equals(PsUserIdValidator.class)
                    || validator.getClass().equals(PsProjectIdValidator.class)
                    || validator.getClass().equals(PsNullIdValidator.class)
                    || validator.getClass().equals(PsIsDeletedValidator.class)
                    || validator.getClass().equals(PsRowVersionValidator.class)
                    || validator.getClass().equals(PsNonExistentEntityValidator.class)
                    || validator.getClass().equals(PsUniqueEntityValidator.class);

    private final Predicate<Validator<ProjectStaffBulkRequest, ProjectStaff>> isApplicableForDelete = validator ->
            validator.getClass().equals(PsNullIdValidator.class)
                    || validator.getClass().equals(PsNonExistentEntityValidator.class);

    @Autowired
    public ProjectStaffService(
            IdGenService idGenService,
            ProjectStaffRepository projectStaffRepository,
            ProjectService projectService,
            UserService userService,
            ProjectConfiguration projectConfiguration,
            ProjectStaffEnrichmentService enrichmentService, List<Validator<ProjectStaffBulkRequest, ProjectStaff>> validators) {
        this.idGenService = idGenService;
        this.projectStaffRepository = projectStaffRepository;
        this.projectService = projectService;
        this.userService = userService;
        this.projectConfiguration = projectConfiguration;
        this.enrichmentService = enrichmentService;
        this.validators = validators;
    }

    public ProjectStaff create(ProjectStaffRequest request) throws Exception {
        ProjectStaffBulkRequest bulkRequest = ProjectStaffBulkRequest.builder().requestInfo(request.getRequestInfo())
                .projectStaff(Collections.singletonList(request.getProjectStaff())).build();
        return create(bulkRequest, false).get(0);
    }


    public List<ProjectStaff> create(ProjectStaffBulkRequest request, boolean isBulk) throws Exception {
        Tuple<List<ProjectStaff>, Map<ProjectStaff, ErrorDetails>> tuple = validate(validators,
                isApplicableForCreate, request,
                isBulk);

        Map<ProjectStaff, ErrorDetails> errorDetailsMap = tuple.getY();
        List<ProjectStaff> validEntities = tuple.getX();
        try {
            if (!validEntities.isEmpty()) {
                enrichmentService.create(validEntities, request);
                projectStaffRepository.save(validEntities, projectConfiguration.getCreateProjectStaffTopic());
            }
        } catch (Exception exception) {
            log.error("error occurred", exception);
            populateErrorDetails(request, errorDetailsMap, validEntities, exception, SET_TASKS);
        }

        handleErrors(errorDetailsMap, isBulk, VALIDATION_ERROR);

        return validEntities;
    }


    public ProjectStaff update(ProjectStaffRequest request) throws Exception {
        ProjectStaffBulkRequest bulkRequest = ProjectStaffBulkRequest.builder().requestInfo(request.getRequestInfo())
                .projectStaff(Collections.singletonList(request.getProjectStaff())).build();
        return update(bulkRequest, false).get(0);
    }

    public List<ProjectStaff> update(ProjectStaffBulkRequest request, boolean isBulk) {
        Tuple<List<ProjectStaff>, Map<ProjectStaff, ErrorDetails>> tuple = validate(validators,
                isApplicableForUpdate, request,
                isBulk);

        Map<ProjectStaff, ErrorDetails> errorDetailsMap = tuple.getY();
        List<ProjectStaff> validEntities = tuple.getX();
        try {
            if (!validEntities.isEmpty()) {
                enrichmentService.update(validEntities, request);
                projectStaffRepository.save(validEntities, projectConfiguration.getUpdateProjectStaffTopic());
            }
        } catch (Exception exception) {
            log.error("error occurred", exception);
            populateErrorDetails(request, errorDetailsMap, validEntities, exception, SET_TASKS);
        }

        handleErrors(errorDetailsMap, isBulk, VALIDATION_ERROR);

        return validEntities;
    }

    public ProjectStaff delete(ProjectStaffRequest request) throws Exception {
        ProjectStaffBulkRequest bulkRequest = ProjectStaffBulkRequest.builder().requestInfo(request.getRequestInfo())
                .projectStaff(Collections.singletonList(request.getProjectStaff())).build();
        return delete(bulkRequest, false).get(0);
    }

    public List<ProjectStaff> delete(ProjectStaffBulkRequest request, boolean isBulk) {
        Tuple<List<ProjectStaff>, Map<ProjectStaff, ErrorDetails>> tuple = validate(validators,
                isApplicableForDelete, request,
                isBulk);

        Map<ProjectStaff, ErrorDetails> errorDetailsMap = tuple.getY();
        List<ProjectStaff> validEntities = tuple.getX();
        try {
            if (!validEntities.isEmpty()) {
                enrichmentService.delete(validEntities, request);
                projectStaffRepository.save(validEntities, projectConfiguration.getDeleteProjectStaffTopic());
            }
        } catch (Exception exception) {
            log.error("error occurred", exception);
            populateErrorDetails(request, errorDetailsMap, validEntities, exception, SET_TASKS);
        }

        handleErrors(errorDetailsMap, isBulk, VALIDATION_ERROR);

        return validEntities;
    }

    private Tuple<List<ProjectStaff>, Map<ProjectStaff, ErrorDetails>> validate(List<Validator<ProjectStaffBulkRequest, ProjectStaff>> validators,
                                                                Predicate<Validator<ProjectStaffBulkRequest, ProjectStaff>> applicableValidators,
                                                                ProjectStaffBulkRequest request, boolean isBulk) {
        log.info("validating request");
        Map<ProjectStaff, ErrorDetails> errorDetailsMap = CommonUtils.validate(validators,
                applicableValidators, request,
                SET_STAFF);
        if (!errorDetailsMap.isEmpty() && !isBulk) {
            throw new CustomException(VALIDATION_ERROR, errorDetailsMap.values().toString());
        }
        List<ProjectStaff> validTasks = request.getProjectStaff().stream()
                .filter(notHavingErrors()).collect(Collectors.toList());
        return new Tuple<>(validTasks, errorDetailsMap);
    }

    public List<ProjectStaff> search(ProjectStaffSearchRequest projectStaffSearchRequest,
                                     Integer limit,
                                     Integer offset,
                                     String tenantId,
                                     Long lastChangedSince,
                                     Boolean includeDeleted) throws Exception {

        if (isSearchByIdOnly(projectStaffSearchRequest.getProjectStaff())) {
            List<String> ids = projectStaffSearchRequest.getProjectStaff().getId();
            return projectStaffRepository.findById(ids, includeDeleted).stream()
                    .filter(lastChangedSince(lastChangedSince))
                    .filter(havingTenantId(tenantId))
                    .filter(includeDeleted(includeDeleted))
                    .collect(Collectors.toList());
        }
        return projectStaffRepository.find(projectStaffSearchRequest.getProjectStaff(),
                limit, offset, tenantId, lastChangedSince, includeDeleted);
    }

}
