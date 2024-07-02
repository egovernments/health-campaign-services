package org.egov.referralmanagement.service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.egov.common.ds.Tuple;
import org.egov.common.models.ErrorDetails;
import org.egov.common.models.core.SearchResponse;
import org.egov.common.models.referralmanagement.sideeffect.SideEffect;
import org.egov.common.models.referralmanagement.sideeffect.SideEffectBulkRequest;
import org.egov.common.models.referralmanagement.sideeffect.SideEffectRequest;
import org.egov.common.models.referralmanagement.sideeffect.SideEffectSearchRequest;
import org.egov.common.utils.CommonUtils;
import org.egov.common.validator.Validator;
import org.egov.referralmanagement.Constants;
import org.egov.referralmanagement.config.ReferralManagementConfiguration;
import org.egov.referralmanagement.repository.SideEffectRepository;
import org.egov.referralmanagement.service.enrichment.SideEffectEnrichmentService;
import org.egov.referralmanagement.validator.sideeffect.SeExistentEntityValidator;
import org.egov.referralmanagement.validator.sideeffect.SeIsDeletedValidator;
import org.egov.referralmanagement.validator.sideeffect.SeNonExistentEntityValidator;
import org.egov.referralmanagement.validator.sideeffect.SeNullIdValidator;
import org.egov.referralmanagement.validator.sideeffect.SeProjectBeneficiaryIdValidator;
import org.egov.referralmanagement.validator.sideeffect.SeProjectTaskIdValidator;
import org.egov.referralmanagement.validator.sideeffect.SeRowVersionValidator;
import org.egov.referralmanagement.validator.sideeffect.SeUniqueEntityValidator;
import org.egov.tracer.model.CustomException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.ReflectionUtils;

import static org.egov.common.utils.CommonUtils.getIdFieldName;
import static org.egov.common.utils.CommonUtils.getIdMethod;
import static org.egov.common.utils.CommonUtils.handleErrors;
import static org.egov.common.utils.CommonUtils.havingTenantId;
import static org.egov.common.utils.CommonUtils.includeDeleted;
import static org.egov.common.utils.CommonUtils.isSearchByIdOnly;
import static org.egov.common.utils.CommonUtils.lastChangedSince;
import static org.egov.common.utils.CommonUtils.notHavingErrors;
import static org.egov.common.utils.CommonUtils.populateErrorDetails;

/**
 * @author kanishq-egov
 * Service created to enrich, validate request and perform crud operations
 */
@Service
@Slf4j
public class SideEffectService {
    private final SideEffectRepository sideEffectRepository;

    private final ReferralManagementConfiguration referralManagementConfiguration;

    private final SideEffectEnrichmentService sideEffectEnrichmentService;

    private final List<Validator<SideEffectBulkRequest, SideEffect>> validators;

    private final Predicate<Validator<SideEffectBulkRequest, SideEffect>> isApplicableForCreate = validator ->
            validator.getClass().equals(SeProjectTaskIdValidator.class)
                || validator.getClass().equals(SeExistentEntityValidator.class)
                || validator.getClass().equals(SeProjectBeneficiaryIdValidator.class);

    private final Predicate<Validator<SideEffectBulkRequest, SideEffect>> isApplicableForUpdate = validator ->
            validator.getClass().equals(SeProjectTaskIdValidator.class)
                || validator.getClass().equals(SeNullIdValidator.class)
                || validator.getClass().equals(SeIsDeletedValidator.class)
                || validator.getClass().equals(SeUniqueEntityValidator.class)
                || validator.getClass().equals(SeNonExistentEntityValidator.class)
                || validator.getClass().equals(SeRowVersionValidator.class);

    private final Predicate<Validator<SideEffectBulkRequest, SideEffect>> isApplicableForDelete = validator ->
            validator.getClass().equals(SeNullIdValidator.class)
                || validator.getClass().equals(SeNonExistentEntityValidator.class)
                || validator.getClass().equals(SeRowVersionValidator.class);
    
    @Autowired
    public SideEffectService(
            SideEffectRepository sideEffectRepository,
            ReferralManagementConfiguration referralManagementConfiguration,
            SideEffectEnrichmentService sideEffectEnrichmentService,
            List<Validator<SideEffectBulkRequest, SideEffect>> validators
    ) {
        this.sideEffectRepository = sideEffectRepository;
        this.referralManagementConfiguration = referralManagementConfiguration;
        this.sideEffectEnrichmentService = sideEffectEnrichmentService;
        this.validators = validators;
    }

    /**
     * converting SideEffectRequest to SideEffectBulkRequest
     * @param request
     * @return
     */
    public SideEffect create(SideEffectRequest request) {
        log.info("received request to create side effects");
        SideEffectBulkRequest bulkRequest = SideEffectBulkRequest.builder().requestInfo(request.getRequestInfo())
                .sideEffects(Collections.singletonList(request.getSideEffect())).build();
        log.info("creating bulk request");
        return create(bulkRequest, false).get(0);
    }

    /**
     * validate the request, for valid objects after enriching them, sending it to kafka, and
     * throwing error for the invalid objects.
     * @param sideEffectRequest
     * @param isBulk
     * @return
     */
    public List<SideEffect> create(SideEffectBulkRequest sideEffectRequest, boolean isBulk) {
        log.info("received request to create bulk side effects");
        Tuple<List<SideEffect>, Map<SideEffect, ErrorDetails>> tuple = validate(validators,
                isApplicableForCreate, sideEffectRequest, isBulk);
        Map<SideEffect, ErrorDetails> errorDetailsMap = tuple.getY();
        List<SideEffect> validSideEffects = tuple.getX();

        try {
            if (!validSideEffects.isEmpty()) {
                log.info("processing {} valid entities", validSideEffects.size());
                sideEffectEnrichmentService.create(validSideEffects, sideEffectRequest);
                sideEffectRepository.save(validSideEffects,
                        referralManagementConfiguration.getCreateSideEffectTopic());
                log.info("successfully created side effects");
            }
        } catch (Exception exception) {
            log.error("error occurred while creating side effects: {}", ExceptionUtils.getStackTrace(exception));
            populateErrorDetails(sideEffectRequest, errorDetailsMap, validSideEffects,
                    exception, Constants.SET_SIDE_EFFECTS);
        }
        handleErrors(errorDetailsMap, isBulk, Constants.VALIDATION_ERROR);

        return validSideEffects;
    }

    /**
     * converting SideEffectRequest to SideEffectBulkRequest
     * @param request
     * @return
     */
    public SideEffect update(SideEffectRequest request) {
        log.info("received request to update side effect");
        SideEffectBulkRequest bulkRequest = SideEffectBulkRequest.builder().requestInfo(request.getRequestInfo())
                .sideEffects(Collections.singletonList(request.getSideEffect())).build();
        log.info("creating bulk request");
        return update(bulkRequest, false).get(0);
    }

    /**
     * validate the request, for valid objects after enriching them, sending it to kafka, and
     * throwing error for the invalid objects.
     * @param sideEffectRequest
     * @param isBulk
     * @return
     */
    public List<SideEffect> update(SideEffectBulkRequest sideEffectRequest, boolean isBulk) {
        log.info("received request to update bulk side effect");
        Tuple<List<SideEffect>, Map<SideEffect, ErrorDetails>> tuple = validate(validators,
                isApplicableForUpdate, sideEffectRequest, isBulk);
        Map<SideEffect, ErrorDetails> errorDetailsMap = tuple.getY();
        List<SideEffect> validSideEffects = tuple.getX();

        try {
            if (!validSideEffects.isEmpty()) {
                log.info("processing {} valid entities", validSideEffects.size());
                sideEffectEnrichmentService.update(validSideEffects, sideEffectRequest);
                sideEffectRepository.save(validSideEffects,
                        referralManagementConfiguration.getUpdateSideEffectTopic());
                log.info("successfully updated bulk side effects");
            }
        } catch (Exception exception) {
            log.error("error occurred while updating side effects: {}", ExceptionUtils.getStackTrace(exception));
            populateErrorDetails(sideEffectRequest, errorDetailsMap, validSideEffects,
                    exception, Constants.SET_SIDE_EFFECTS);
        }
        handleErrors(errorDetailsMap, isBulk, Constants.VALIDATION_ERROR);

        return validSideEffects;
    }

    /**
     * searching based on parameters
     * @param sideEffectSearchRequest
     * @param limit
     * @param offset
     * @param tenantId
     * @param lastChangedSince
     * @param includeDeleted
     * @return
     * @throws Exception
     */
    public SearchResponse<SideEffect> search(SideEffectSearchRequest sideEffectSearchRequest,
                                             Integer limit,
                                             Integer offset,
                                             String tenantId,
                                             Long lastChangedSince,
                                             Boolean includeDeleted) {
        log.info("received request to search side effects");
        String idFieldName = getIdFieldName(sideEffectSearchRequest.getSideEffect());
        if (isSearchByIdOnly(sideEffectSearchRequest.getSideEffect(), idFieldName)) {
            log.info("searching side effects by id");
            List<String> ids = (List<String>) ReflectionUtils.invokeMethod(getIdMethod(Collections
                            .singletonList(sideEffectSearchRequest.getSideEffect())),
                    sideEffectSearchRequest.getSideEffect());
            log.info("fetching side effects with ids: {}", ids);
            List<SideEffect> sideEffectList = sideEffectRepository.findById(ids, includeDeleted, idFieldName);
            return SearchResponse.<SideEffect>builder().response(sideEffectList.stream()
                    .filter(lastChangedSince(lastChangedSince))
                    .filter(havingTenantId(tenantId))
                    .filter(includeDeleted(includeDeleted))
                    .collect(Collectors.toList())).build();
        }
        log.info("searching side effects using criteria");
        return sideEffectRepository.find(sideEffectSearchRequest.getSideEffect(),
                limit, offset, tenantId, lastChangedSince, includeDeleted);
    }

    /**
     * converting SideEffectRequest to SideEffectBulkRequest
     * @param sideEffectRequest
     * @return
     */
    public SideEffect delete(SideEffectRequest sideEffectRequest) {
        log.info("received request to delete a side effect");
        SideEffectBulkRequest bulkRequest = SideEffectBulkRequest.builder().requestInfo(sideEffectRequest.getRequestInfo())
                .sideEffects(Collections.singletonList(sideEffectRequest.getSideEffect())).build();
        log.info("creating bulk request");
        return delete(bulkRequest, false).get(0);
    }

    /**
     * validating the request, enriching the valid objects and sending them to kafka
     * throwing error on invalid objects
     * @param sideEffectRequest
     * @param isBulk
     * @return
     */
    public List<SideEffect> delete(SideEffectBulkRequest sideEffectRequest, boolean isBulk) {
        Tuple<List<SideEffect>, Map<SideEffect, ErrorDetails>> tuple = validate(validators,
                isApplicableForDelete, sideEffectRequest, isBulk);
        Map<SideEffect, ErrorDetails> errorDetailsMap = tuple.getY();
        List<SideEffect> validSideEffects = tuple.getX();

        try {
            if (!validSideEffects.isEmpty()) {
                log.info("processing {} valid entities", validSideEffects.size());
                List<String> sideEffectIds = validSideEffects.stream().map(entity -> entity.getId()).collect(Collectors.toSet()).stream().collect(Collectors.toList());
                List<SideEffect> existingSideEffects = sideEffectRepository
                        .findById(sideEffectIds, false);
                sideEffectEnrichmentService.delete(existingSideEffects, sideEffectRequest);
                sideEffectRepository.save(existingSideEffects,
                        referralManagementConfiguration.getDeleteSideEffectTopic());
                log.info("successfully deleted entities");
            }
        } catch (Exception exception) {
            log.error("error occurred while deleting entities: {}", ExceptionUtils.getStackTrace(exception));
            populateErrorDetails(sideEffectRequest, errorDetailsMap, validSideEffects,
                    exception, Constants.SET_SIDE_EFFECTS);
        }
        handleErrors(errorDetailsMap, isBulk, Constants.VALIDATION_ERROR);

        return validSideEffects;
    }

    public void putInCache(List<SideEffect> sideEffects) {
        log.info("putting {} side effects in cache", sideEffects.size());
        sideEffectRepository.putInCache(sideEffects);
        log.info("successfully put side effects in cache");
    }

    /**
     * method use to valid request using parameters objects
     * @param validators
     * @param isApplicable
     * @param request
     * @param isBulk
     * @return
     */
    private Tuple<List<SideEffect>, Map<SideEffect, ErrorDetails>> validate(
            List<Validator<SideEffectBulkRequest, SideEffect>> validators,
            Predicate<Validator<SideEffectBulkRequest, SideEffect>> isApplicable,
            SideEffectBulkRequest request,
            boolean isBulk
    ) {
        log.info("validating request");
        Map<SideEffect, ErrorDetails> errorDetailsMap = CommonUtils.validate(validators,
                isApplicable, request,
                Constants.SET_SIDE_EFFECTS);
        if (!errorDetailsMap.isEmpty() && !isBulk) {
            log.error("validation error occurred. error details: {}", errorDetailsMap.values().toString());
            throw new CustomException(Constants.VALIDATION_ERROR, errorDetailsMap.values().toString());
        }
        List<SideEffect> validSideEffects = request.getSideEffects().stream()
                .filter(notHavingErrors()).collect(Collectors.toList());
        log.info("validation successful, found valid side effects");
        return new Tuple<>(validSideEffects, errorDetailsMap);
    }

}
