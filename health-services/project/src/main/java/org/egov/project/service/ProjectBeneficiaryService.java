package org.egov.project.service;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.ds.Tuple;
import org.egov.common.models.ErrorDetails;
import org.egov.common.models.project.BeneficiaryBulkRequest;
import org.egov.common.models.project.BeneficiaryRequest;
import org.egov.common.models.project.ProjectBeneficiary;
import org.egov.common.service.IdGenService;
import org.egov.common.utils.CommonUtils;
import org.egov.common.validator.Validator;
import org.egov.project.config.ProjectConfiguration;
import org.egov.project.repository.ProjectBeneficiaryRepository;
import org.egov.project.service.enrichment.ProjectBeneficiaryEnrichmentService;
import org.egov.project.validator.beneficiary.BeneficiaryValidator;
import org.egov.project.validator.beneficiary.PbIsDeletedValidator;
import org.egov.project.validator.beneficiary.PbNonExistentEntityValidator;
import org.egov.project.validator.beneficiary.PbNullIdValidator;
import org.egov.project.validator.beneficiary.PbProjectIdValidator;
import org.egov.project.validator.beneficiary.PbRowVersionValidator;
import org.egov.project.validator.beneficiary.PbUniqueEntityValidator;
import org.egov.project.validator.beneficiary.PbUniqueTagsValidator;
import org.egov.project.validator.beneficiary.PbVoucherTagUniqueForCreateValidator;
import org.egov.project.validator.beneficiary.PbVoucherTagUniqueForUpdateValidator;
import org.egov.project.web.models.BeneficiarySearchRequest;
import org.egov.tracer.model.CustomException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.ReflectionUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.egov.common.utils.CommonUtils.getIdFieldName;
import static org.egov.common.utils.CommonUtils.getIdMethod;
import static org.egov.common.utils.CommonUtils.handleErrors;
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
                    || validator.getClass().equals(PbUniqueTagsValidator.class)
                    || validator.getClass().equals(PbVoucherTagUniqueForUpdateValidator.class)
                    || validator.getClass().equals(PbIsDeletedValidator.class)
                    || validator.getClass().equals(PbProjectIdValidator.class)
                    || validator.getClass().equals(BeneficiaryValidator.class)
                    || validator.getClass().equals(PbRowVersionValidator.class)
                    || validator.getClass().equals(PbUniqueEntityValidator.class);

    private final Predicate<Validator<BeneficiaryBulkRequest, ProjectBeneficiary>> isApplicableForCreate = validator ->
            validator.getClass().equals(PbProjectIdValidator.class)
                    || validator.getClass().equals(BeneficiaryValidator.class)
                    || validator.getClass().equals(PbUniqueTagsValidator.class)
                    || validator.getClass().equals(PbVoucherTagUniqueForCreateValidator.class);

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

    public List<ProjectBeneficiary> create(BeneficiaryRequest request) {
        log.info("received request to create project beneficiaries");
        BeneficiaryBulkRequest bulkRequest = BeneficiaryBulkRequest.builder().requestInfo(request.getRequestInfo())
                .projectBeneficiaries(Collections.singletonList(request.getProjectBeneficiary())).build();
        log.info("creating bulk request");
        return create(bulkRequest, false);
    }

    public List<ProjectBeneficiary> create(BeneficiaryBulkRequest beneficiaryRequest, boolean isBulk) {
        log.info("received request to create bulk project beneficiaries");
        Tuple<List<ProjectBeneficiary>, Map<ProjectBeneficiary, ErrorDetails>> tuple = validate(validators,
                isApplicableForCreate, beneficiaryRequest, isBulk);
        Map<ProjectBeneficiary, ErrorDetails> errorDetailsMap = tuple.getY();
        List<ProjectBeneficiary> validProjectBeneficiaries = tuple.getX();

        try {
            if (!validProjectBeneficiaries.isEmpty()) {
                log.info("processing {} valid entities", validProjectBeneficiaries.size());
                projectBeneficiaryEnrichmentService.create(validProjectBeneficiaries, beneficiaryRequest);
                projectBeneficiaryRepository.save(validProjectBeneficiaries,
                        projectConfiguration.getCreateProjectBeneficiaryTopic());
                log.info("successfully created project beneficiaries");
            }
        } catch (Exception exception) {
            log.error("error occurred while creating project beneficiaries: {}", exception.getMessage());
            populateErrorDetails(beneficiaryRequest, errorDetailsMap, validProjectBeneficiaries,
                    exception, SET_PROJECT_BENEFICIARIES);
        }
        handleErrors(errorDetailsMap, isBulk, VALIDATION_ERROR);

        return validProjectBeneficiaries;
    }

    public List<ProjectBeneficiary> update(BeneficiaryRequest request) {
        log.info("received request to update project beneficiary");
        BeneficiaryBulkRequest bulkRequest = BeneficiaryBulkRequest.builder().requestInfo(request.getRequestInfo())
                .projectBeneficiaries(Collections.singletonList(request.getProjectBeneficiary())).build();
        log.info("creating bulk request");
        return update(bulkRequest, false);
    }

    public List<ProjectBeneficiary> update(BeneficiaryBulkRequest beneficiaryRequest, boolean isBulk) {
        log.info("received request to update bulk project beneficiary");
        Tuple<List<ProjectBeneficiary>, Map<ProjectBeneficiary, ErrorDetails>> tuple = validate(validators,
                isApplicableForUpdate, beneficiaryRequest, isBulk);
        Map<ProjectBeneficiary, ErrorDetails> errorDetailsMap = tuple.getY();
        List<ProjectBeneficiary> validProjectBeneficiaries = tuple.getX();

        try {
            if (!validProjectBeneficiaries.isEmpty()) {
                log.info("processing {} valid entities", validProjectBeneficiaries.size());
                projectBeneficiaryEnrichmentService.update(validProjectBeneficiaries, beneficiaryRequest);
                projectBeneficiaryRepository.save(validProjectBeneficiaries,
                        projectConfiguration.getUpdateProjectBeneficiaryTopic());
                log.info("successfully updated bulk project beneficiaries");
            }
        } catch (Exception exception) {
            log.error("error occurred while updating project beneficiaries", exception);
            populateErrorDetails(beneficiaryRequest, errorDetailsMap, validProjectBeneficiaries,
                    exception, SET_PROJECT_BENEFICIARIES);
        }
        handleErrors(errorDetailsMap, isBulk, VALIDATION_ERROR);

        return validProjectBeneficiaries;
    }

    public List<ProjectBeneficiary> search(BeneficiarySearchRequest beneficiarySearchRequest,
                                           Integer limit,
                                           Integer offset,
                                           String tenantId,
                                           Long lastChangedSince,
                                           Boolean includeDeleted) throws Exception {
        log.info("received request to search project beneficiaries");
        String idFieldName = getIdFieldName(beneficiarySearchRequest.getProjectBeneficiary());
        if (isSearchByIdOnly(beneficiarySearchRequest.getProjectBeneficiary(), idFieldName)) {
            log.info("searching project beneficiaries by id");
            List<String> ids = (List<String>) ReflectionUtils.invokeMethod(getIdMethod(Collections
                            .singletonList(beneficiarySearchRequest.getProjectBeneficiary())),
                    beneficiarySearchRequest.getProjectBeneficiary());
            log.info("fetching project beneficiaries with ids: {}", ids);
            return projectBeneficiaryRepository.findById(ids, includeDeleted, idFieldName).stream()
                    .filter(lastChangedSince(lastChangedSince))
                    .filter(havingTenantId(tenantId))
                    .filter(includeDeleted(includeDeleted))
                    .collect(Collectors.toList());
        }
        log.info("searching project beneficiaries using criteria");
        return projectBeneficiaryRepository.find(beneficiarySearchRequest.getProjectBeneficiary(),
                limit, offset, tenantId, lastChangedSince, includeDeleted);
    }

    public List<ProjectBeneficiary> delete(BeneficiaryRequest beneficiaryRequest) {
        log.info("received request to delete a project beneficiary");
        BeneficiaryBulkRequest bulkRequest = BeneficiaryBulkRequest.builder().requestInfo(beneficiaryRequest.getRequestInfo())
                .projectBeneficiaries(Collections.singletonList(beneficiaryRequest.getProjectBeneficiary())).build();
        log.info("creating bulk request");
        return delete(bulkRequest, false);
    }

    public List<ProjectBeneficiary> delete(BeneficiaryBulkRequest beneficiaryRequest, boolean isBulk) {
        Tuple<List<ProjectBeneficiary>, Map<ProjectBeneficiary, ErrorDetails>> tuple = validate(validators,
                isApplicableForDelete, beneficiaryRequest, isBulk);
        Map<ProjectBeneficiary, ErrorDetails> errorDetailsMap = tuple.getY();
        List<ProjectBeneficiary> validProjectBeneficiaries = tuple.getX();

        try {
            if (!validProjectBeneficiaries.isEmpty()) {
                log.info("processing {} valid entities", validProjectBeneficiaries.size());
                projectBeneficiaryEnrichmentService.delete(validProjectBeneficiaries, beneficiaryRequest);
                projectBeneficiaryRepository.save(validProjectBeneficiaries,
                        projectConfiguration.getDeleteProjectBeneficiaryTopic());
                log.info("successfully deleted entities");
            }
        } catch (Exception exception) {
            log.error("error occurred while deleting entities: {}", exception);
            populateErrorDetails(beneficiaryRequest, errorDetailsMap, validProjectBeneficiaries,
                    exception, SET_PROJECT_BENEFICIARIES);
        }
        handleErrors(errorDetailsMap, isBulk, VALIDATION_ERROR);

        return validProjectBeneficiaries;
    }

    public void putInCache(List<ProjectBeneficiary> projectBeneficiaries) {
        log.info("putting {} project beneficiaries in cache", projectBeneficiaries.size());
        projectBeneficiaryRepository.putInCache(projectBeneficiaries);
        log.info("successfully put project beneficiaries in cache");
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
            log.error("validation error occurred. error details: {}", errorDetailsMap.values().toString());
            throw new CustomException(VALIDATION_ERROR, errorDetailsMap.values().toString());
        }
        List<ProjectBeneficiary> validProjectBeneficiaries = request.getProjectBeneficiaries().stream()
                .filter(notHavingErrors()).collect(Collectors.toList());
        log.info("validation successful, found valid project beneficiaries");
        return new Tuple<>(validProjectBeneficiaries, errorDetailsMap);
    }
}
