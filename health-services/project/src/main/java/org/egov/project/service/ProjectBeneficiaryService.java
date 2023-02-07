package org.egov.project.service;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.ds.Tuple;
import org.egov.common.models.ErrorDetails;
import org.egov.common.service.IdGenService;
import org.egov.common.utils.CommonUtils;
import org.egov.common.validator.Validator;
import org.egov.project.beneficiary.validators.BeneficiaryValidator;
import org.egov.project.beneficiary.validators.PbIsDeletedValidator;
import org.egov.project.beneficiary.validators.PbNonExistentEntityValidator;
import org.egov.project.beneficiary.validators.PbNullIdValidator;
import org.egov.project.beneficiary.validators.PbProjectIdValidator;
import org.egov.project.beneficiary.validators.PbRowVersionValidator;
import org.egov.project.beneficiary.validators.PbUniqueEntityValidator;
import org.egov.project.config.ProjectConfiguration;
import org.egov.project.repository.ProjectBeneficiaryRepository;
import org.egov.project.web.models.BeneficiaryBulkRequest;
import org.egov.project.web.models.BeneficiaryRequest;
import org.egov.project.web.models.BeneficiarySearchRequest;
import org.egov.project.web.models.ProjectBeneficiary;
import org.egov.tracer.model.CustomException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.egov.common.utils.CommonUtils.havingTenantId;
import static org.egov.common.utils.CommonUtils.includeDeleted;
import static org.egov.common.utils.CommonUtils.isSearchByIdOnly;
import static org.egov.common.utils.CommonUtils.lastChangedSince;
import static org.egov.common.utils.CommonUtils.notHavingErrors;
import static org.egov.common.utils.CommonUtils.populateErrorDetails;
import static org.egov.project.Constants.SET_PROJECT_BENEFICIARIES;
import static org.egov.project.Constants.VALIDATION_ERROR;

@Service
@Slf4j
public class ProjectBeneficiaryService {

    private final IdGenService idGenService;

    private final ProjectBeneficiaryRepository projectBeneficiaryRepository;

    private final ProjectService projectService;

    private final ProjectConfiguration projectConfiguration;

    private final ProjectBeneficiaryEnrichmentService projectBeneficiaryEnrichmentService;

    private final List<Validator<BeneficiaryBulkRequest, ProjectBeneficiary>> validators;

    private final Predicate<Validator<BeneficiaryBulkRequest, ProjectBeneficiary>> isApplicableForUpdate = validator ->
            validator.getClass().equals(PbNullIdValidator.class)
                    || validator.getClass().equals(PbNonExistentEntityValidator.class)
                    || validator.getClass().equals(PbIsDeletedValidator.class)
                    || validator.getClass().equals(PbProjectIdValidator.class)
                    || validator.getClass().equals(BeneficiaryValidator.class)
                    || validator.getClass().equals(PbRowVersionValidator.class)
                    || validator.getClass().equals(PbUniqueEntityValidator.class);

    private final Predicate<Validator<BeneficiaryBulkRequest, ProjectBeneficiary>> isApplicableForCreate = validator ->
            validator.getClass().equals(PbProjectIdValidator.class)
                    || validator.getClass().equals(BeneficiaryValidator.class);

    private final Predicate<Validator<BeneficiaryBulkRequest, ProjectBeneficiary>> isApplicableForDelete = validator ->
            validator.getClass().equals(PbNullIdValidator.class)
                    || validator.getClass().equals(PbNonExistentEntityValidator.class);

    @Autowired
    public ProjectBeneficiaryService(
            IdGenService idGenService,
            ProjectBeneficiaryRepository projectBeneficiaryRepository,
            ProjectService projectService,
            ProjectConfiguration projectConfiguration,
            List<Validator<BeneficiaryBulkRequest, ProjectBeneficiary>> validators,
            ProjectBeneficiaryEnrichmentService projectBeneficiaryEnrichmentService
    ) {
        this.idGenService = idGenService;
        this.projectBeneficiaryRepository = projectBeneficiaryRepository;
        this.projectService = projectService;
        this.projectConfiguration = projectConfiguration;
        this.validators = validators;
        this.projectBeneficiaryEnrichmentService = projectBeneficiaryEnrichmentService;
    }

    public List<ProjectBeneficiary> create(BeneficiaryRequest request) throws Exception {
        BeneficiaryBulkRequest bulkRequest = BeneficiaryBulkRequest.builder().requestInfo(request.getRequestInfo())
                .projectBeneficiaries(Collections.singletonList(request.getProjectBeneficiary())).build();
        return create(bulkRequest, false);
    }

    public List<ProjectBeneficiary> create(BeneficiaryBulkRequest beneficiaryRequest, boolean isBulk) throws Exception {
        Tuple<List<ProjectBeneficiary>, Map<ProjectBeneficiary, ErrorDetails>> tuple = validate(validators,
                isApplicableForCreate, beneficiaryRequest, isBulk);
        Map<ProjectBeneficiary, ErrorDetails> errorDetailsMap = tuple.getY();
        List<ProjectBeneficiary> validProjectBeneficiaries = tuple.getX();

        try {
            if (!validProjectBeneficiaries.isEmpty()) {
                projectBeneficiaryEnrichmentService.create(validProjectBeneficiaries, beneficiaryRequest);
                projectBeneficiaryRepository.save(validProjectBeneficiaries,
                        projectConfiguration.getCreateProjectBeneficiaryTopic());
            }
        } catch (Exception exception) {
            log.error("error occurred", exception);
            populateErrorDetails(beneficiaryRequest, errorDetailsMap, validProjectBeneficiaries,
                    exception, SET_PROJECT_BENEFICIARIES);
        }
        handleErrors(isBulk, errorDetailsMap);

        return validProjectBeneficiaries;
    }

    public List<ProjectBeneficiary> update(BeneficiaryRequest request) throws Exception {
        BeneficiaryBulkRequest bulkRequest = BeneficiaryBulkRequest.builder().requestInfo(request.getRequestInfo())
                .projectBeneficiaries(Collections.singletonList(request.getProjectBeneficiary())).build();
        return update(bulkRequest, false);
    }

    public List<ProjectBeneficiary> update(BeneficiaryBulkRequest beneficiaryRequest, boolean isBulk) throws Exception {
        Tuple<List<ProjectBeneficiary>, Map<ProjectBeneficiary, ErrorDetails>> tuple = validate(validators,
                isApplicableForUpdate, beneficiaryRequest, isBulk);
        Map<ProjectBeneficiary, ErrorDetails> errorDetailsMap = tuple.getY();
        List<ProjectBeneficiary> validProjectBeneficiaries = tuple.getX();

        try {
            if (!validProjectBeneficiaries.isEmpty()) {
                projectBeneficiaryEnrichmentService.update(validProjectBeneficiaries, beneficiaryRequest);
                projectBeneficiaryRepository.save(validProjectBeneficiaries,
                        projectConfiguration.getUpdateProjectBeneficiaryTopic());
            }
        } catch (Exception exception) {
            log.error("error occurred", exception);
            populateErrorDetails(beneficiaryRequest, errorDetailsMap, validProjectBeneficiaries,
                    exception, SET_PROJECT_BENEFICIARIES);
        }
        handleErrors(isBulk, errorDetailsMap);

        return validProjectBeneficiaries;
    }

    public List<ProjectBeneficiary> search(BeneficiarySearchRequest beneficiarySearchRequest,
                                     Integer limit,
                                     Integer offset,
                                     String tenantId,
                                     Long lastChangedSince,
                                     Boolean includeDeleted) throws Exception {

        if (isSearchByIdOnly(beneficiarySearchRequest.getProjectBeneficiary())) {
            List<String> ids = beneficiarySearchRequest.getProjectBeneficiary().getId();
            return projectBeneficiaryRepository.findById(ids, includeDeleted).stream()
                    .filter(lastChangedSince(lastChangedSince))
                    .filter(havingTenantId(tenantId))
                    .filter(includeDeleted(includeDeleted))
                    .collect(Collectors.toList());
        }
        return projectBeneficiaryRepository.find(beneficiarySearchRequest.getProjectBeneficiary(),
                limit, offset, tenantId, lastChangedSince, includeDeleted);
    }

    public List<ProjectBeneficiary> delete(BeneficiaryRequest beneficiaryRequest) {
        BeneficiaryBulkRequest bulkRequest = BeneficiaryBulkRequest.builder().requestInfo(beneficiaryRequest.getRequestInfo())
                .projectBeneficiaries(Collections.singletonList(beneficiaryRequest.getProjectBeneficiary())).build();
        return delete(bulkRequest, false);
    }

    public List<ProjectBeneficiary> delete(BeneficiaryBulkRequest beneficiaryRequest, boolean isBulk) {
        Tuple<List<ProjectBeneficiary>, Map<ProjectBeneficiary, ErrorDetails>> tuple = validate(validators,
                isApplicableForDelete, beneficiaryRequest, isBulk);
        Map<ProjectBeneficiary, ErrorDetails> errorDetailsMap = tuple.getY();
        List<ProjectBeneficiary> validProjectBeneficiaries = tuple.getX();

        try {
            if (!validProjectBeneficiaries.isEmpty()) {
                projectBeneficiaryEnrichmentService.delete(validProjectBeneficiaries, beneficiaryRequest);
                projectBeneficiaryRepository.save(validProjectBeneficiaries,
                        projectConfiguration.getDeleteProjectBeneficiaryTopic());
            }
        } catch (Exception exception) {
            log.error("error occurred", exception);
            populateErrorDetails(beneficiaryRequest, errorDetailsMap, validProjectBeneficiaries,
                    exception, SET_PROJECT_BENEFICIARIES);
        }
        handleErrors(isBulk, errorDetailsMap);

        return validProjectBeneficiaries;
    }

    private Tuple<List<ProjectBeneficiary>, Map<ProjectBeneficiary, ErrorDetails>> validate(List<Validator<BeneficiaryBulkRequest,
            ProjectBeneficiary>> validators,
            Predicate<Validator<BeneficiaryBulkRequest,
            ProjectBeneficiary>> isApplicable, BeneficiaryBulkRequest request, boolean isBulk) {
        log.info("validating request");
        Map<ProjectBeneficiary, ErrorDetails> errorDetailsMap = CommonUtils.validate(validators,
                isApplicable, request,
                SET_PROJECT_BENEFICIARIES);
        if (!errorDetailsMap.isEmpty() && !isBulk) {
            throw new CustomException(VALIDATION_ERROR, errorDetailsMap.values().toString());
        }
        List<ProjectBeneficiary> validProjectBeneficiaries = request.getProjectBeneficiaries().stream()
                .filter(notHavingErrors()).collect(Collectors.toList());
        return new Tuple<>(validProjectBeneficiaries, errorDetailsMap);
    }

    private static void handleErrors(boolean isBulk, Map<ProjectBeneficiary, ErrorDetails> errorDetailsMap) {
        if (!errorDetailsMap.isEmpty()) {
            log.error("{} errors collected", errorDetailsMap.size());
            if (isBulk) {
                log.info("call tracer.handleErrors(), {}", errorDetailsMap.values());
            } else {
                throw new CustomException(VALIDATION_ERROR, errorDetailsMap.values().toString());
            }
        }
    }
}
