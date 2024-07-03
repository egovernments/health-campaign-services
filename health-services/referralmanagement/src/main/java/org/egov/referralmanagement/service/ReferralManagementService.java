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
import org.egov.common.models.referralmanagement.Referral;
import org.egov.common.models.referralmanagement.ReferralBulkRequest;
import org.egov.common.models.referralmanagement.ReferralRequest;
import org.egov.common.models.referralmanagement.ReferralSearchRequest;
import org.egov.common.service.IdGenService;
import org.egov.common.utils.CommonUtils;
import org.egov.common.validator.Validator;
import org.egov.referralmanagement.Constants;
import org.egov.referralmanagement.config.ReferralManagementConfiguration;
import org.egov.referralmanagement.repository.ReferralRepository;
import org.egov.referralmanagement.service.enrichment.ReferralManagementEnrichmentService;
import org.egov.referralmanagement.validator.RmExistentEntityValidator;
import org.egov.referralmanagement.validator.RmIsDeletedValidator;
import org.egov.referralmanagement.validator.RmNonExistentEntityValidator;
import org.egov.referralmanagement.validator.RmNullIdValidator;
import org.egov.referralmanagement.validator.RmProjectBeneficiaryIdValidator;
import org.egov.referralmanagement.validator.RmRecipientIdValidator;
import org.egov.referralmanagement.validator.RmReferrerIdValidator;
import org.egov.referralmanagement.validator.RmRowVersionValidator;
import org.egov.referralmanagement.validator.RmSideEffectIdValidator;
import org.egov.referralmanagement.validator.RmUniqueEntityValidator;
import org.egov.tracer.model.CustomException;
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

@Service
@Slf4j
public class ReferralManagementService {
	
    private final IdGenService idGenService;

    private final ReferralRepository referralRepository;

    private final ReferralManagementConfiguration referralManagementConfiguration;

    private final ReferralManagementEnrichmentService referralManagementEnrichmentService;

    private final List<Validator<ReferralBulkRequest, Referral>> validators;

    private final Predicate<Validator<ReferralBulkRequest, Referral>> isApplicableForCreate = validator ->
            validator.getClass().equals(RmProjectBeneficiaryIdValidator.class)
                || validator.getClass().equals(RmExistentEntityValidator.class)
                || validator.getClass().equals(RmReferrerIdValidator.class)
                || validator.getClass().equals(RmRecipientIdValidator.class)
                || validator.getClass().equals(RmSideEffectIdValidator.class)
                || validator.getClass().equals(RmRowVersionValidator.class);

    private final Predicate<Validator<ReferralBulkRequest, Referral>> isApplicableForUpdate = validator ->
            validator.getClass().equals(RmProjectBeneficiaryIdValidator.class)
                || validator.getClass().equals(RmReferrerIdValidator.class)
                || validator.getClass().equals(RmRecipientIdValidator.class)
                || validator.getClass().equals(RmSideEffectIdValidator.class)
                || validator.getClass().equals(RmNullIdValidator.class)
                || validator.getClass().equals(RmIsDeletedValidator.class)
                || validator.getClass().equals(RmUniqueEntityValidator.class)
                || validator.getClass().equals(RmNonExistentEntityValidator.class)
                || validator.getClass().equals(RmRowVersionValidator.class);

    private final Predicate<Validator<ReferralBulkRequest, Referral>> isApplicableForDelete = validator ->
            validator.getClass().equals(RmNullIdValidator.class)
                || validator.getClass().equals(RmNonExistentEntityValidator.class);


    public ReferralManagementService(IdGenService idGenService, ReferralRepository referralRepository, ReferralManagementConfiguration referralManagementConfiguration, ReferralManagementEnrichmentService referralManagementEnrichmentService, List<Validator<ReferralBulkRequest, Referral>> validators) {
        this.idGenService = idGenService;
        this.referralRepository = referralRepository;
        this.referralManagementConfiguration = referralManagementConfiguration;
        this.referralManagementEnrichmentService = referralManagementEnrichmentService;
        this.validators = validators;
    }

    public Referral create(ReferralRequest request) {
        log.info("received request to create referrals");
        ReferralBulkRequest bulkRequest = ReferralBulkRequest.builder().requestInfo(request.getRequestInfo())
                .referrals(Collections.singletonList(request.getReferral())).build();
        log.info("creating bulk request");
        return create(bulkRequest, false).get(0);
    }

    public List<Referral> create(ReferralBulkRequest referralRequest, boolean isBulk) {
        log.info("received request to create bulk referrals");
        Tuple<List<Referral>, Map<Referral, ErrorDetails>> tuple = validate(validators,
                isApplicableForCreate, referralRequest, isBulk);
        Map<Referral, ErrorDetails> errorDetailsMap = tuple.getY();
        List<Referral> validReferrals = tuple.getX();

        try {
            if (!validReferrals.isEmpty()) {
                log.info("processing {} valid entities", validReferrals.size());
                referralManagementEnrichmentService.create(validReferrals, referralRequest);
                referralRepository.save(validReferrals,
                        referralManagementConfiguration.getCreateReferralTopic());
                log.info("successfully created referrals");
            }
        } catch (Exception exception) {
            log.error("error occurred while creating referrals: {}", ExceptionUtils.getStackTrace(exception));
            populateErrorDetails(referralRequest, errorDetailsMap, validReferrals,
                    exception, Constants.SET_REFERRALS);
        }
        handleErrors(errorDetailsMap, isBulk, Constants.VALIDATION_ERROR);

        return validReferrals;
    }

    public Referral update(ReferralRequest request) {
        log.info("received request to update referral");
        ReferralBulkRequest bulkRequest = ReferralBulkRequest.builder().requestInfo(request.getRequestInfo())
                .referrals(Collections.singletonList(request.getReferral())).build();
        log.info("creating bulk request");
        return update(bulkRequest, false).get(0);
    }

    public List<Referral> update(ReferralBulkRequest referralRequest, boolean isBulk) {
        log.info("received request to update bulk referral");
        Tuple<List<Referral>, Map<Referral, ErrorDetails>> tuple = validate(validators,
                isApplicableForUpdate, referralRequest, isBulk);
        Map<Referral, ErrorDetails> errorDetailsMap = tuple.getY();
        List<Referral> validReferrals = tuple.getX();

        try {
            if (!validReferrals.isEmpty()) {
                log.info("processing {} valid entities", validReferrals.size());
                referralManagementEnrichmentService.update(validReferrals, referralRequest);
                referralRepository.save(validReferrals,
                        referralManagementConfiguration.getUpdateReferralTopic());
                log.info("successfully updated bulk referrals");
            }
        } catch (Exception exception) {
            log.error("error occurred while updating referrals: {}", ExceptionUtils.getStackTrace(exception));
            populateErrorDetails(referralRequest, errorDetailsMap, validReferrals,
                    exception, Constants.SET_REFERRALS);
        }
        handleErrors(errorDetailsMap, isBulk, Constants.VALIDATION_ERROR);

        return validReferrals;
    }

    public SearchResponse<Referral> search(ReferralSearchRequest referralSearchRequest,
                                           Integer limit,
                                           Integer offset,
                                           String tenantId,
                                           Long lastChangedSince,
                                           Boolean includeDeleted) {
        log.info("received request to search referrals");
        String idFieldName = getIdFieldName(referralSearchRequest.getReferral());
        if (isSearchByIdOnly(referralSearchRequest.getReferral(), idFieldName)) {
            log.info("searching referrals by id");
            List<String> ids = (List<String>) ReflectionUtils.invokeMethod(getIdMethod(Collections
                            .singletonList(referralSearchRequest.getReferral())),
                    referralSearchRequest.getReferral());
            log.info("fetching referrals with ids: {}", ids);
            List<Referral> referrals = referralRepository.findById(ids, idFieldName, includeDeleted).getResponse().stream()
                    .filter(lastChangedSince(lastChangedSince))
                    .filter(havingTenantId(tenantId))
                    .filter(includeDeleted(includeDeleted))
                    .collect(Collectors.toList());
            return SearchResponse.<Referral>builder().response(referrals).build();
        }
        log.info("searching referrals using criteria");
        return referralRepository.find(referralSearchRequest.getReferral(),
                limit, offset, tenantId, lastChangedSince, includeDeleted);
    }

    public Referral delete(ReferralRequest referralRequest) {
        log.info("received request to delete a referral");
        ReferralBulkRequest bulkRequest = ReferralBulkRequest.builder().requestInfo(referralRequest.getRequestInfo())
                .referrals(Collections.singletonList(referralRequest.getReferral())).build();
        log.info("creating bulk request");
        return delete(bulkRequest, false).get(0);
    }

    public List<Referral> delete(ReferralBulkRequest referralRequest, boolean isBulk) {
        Tuple<List<Referral>, Map<Referral, ErrorDetails>> tuple = validate(validators,
                isApplicableForDelete, referralRequest, isBulk);
        Map<Referral, ErrorDetails> errorDetailsMap = tuple.getY();
        List<Referral> validReferrals = tuple.getX();

        try {
            if (!validReferrals.isEmpty()) {
                log.info("processing {} valid entities", validReferrals.size());
                List<String> referralIds = validReferrals.stream().map(entity -> entity.getId()).collect(Collectors.toSet()).stream().collect(Collectors.toList());
                List<Referral> existingReferrals = referralRepository
                        .findById(referralIds, false);
                referralManagementEnrichmentService.delete(existingReferrals, referralRequest);
                referralRepository.save(existingReferrals,
                        referralManagementConfiguration.getDeleteReferralTopic());
                log.info("successfully deleted entities");
            }
        } catch (Exception exception) {
            log.error("error occurred while deleting entities: {}", ExceptionUtils.getStackTrace(exception));
            populateErrorDetails(referralRequest, errorDetailsMap, validReferrals,
                    exception, Constants.SET_REFERRALS);
        }
        handleErrors(errorDetailsMap, isBulk, Constants.VALIDATION_ERROR);

        return validReferrals;
    }

    public void putInCache(List<Referral> referrals) {
        log.info("putting {} referrals in cache", referrals.size());
        referralRepository.putInCache(referrals);
        log.info("successfully put referrals in cache");
    }

    private Tuple<List<Referral>, Map<Referral, ErrorDetails>> validate(
            List<Validator<ReferralBulkRequest, Referral>> validators,
            Predicate<Validator<ReferralBulkRequest, Referral>> isApplicable,
            ReferralBulkRequest request,
            boolean isBulk
    ) {
        log.info("validating request");
        Map<Referral, ErrorDetails> errorDetailsMap = CommonUtils.validate(validators,
                isApplicable, request,
                Constants.SET_REFERRALS);
        if (!errorDetailsMap.isEmpty() && !isBulk) {
            log.error("validation error occurred. error details: {}", errorDetailsMap.values().toString());
            throw new CustomException(Constants.VALIDATION_ERROR, errorDetailsMap.values().toString());
        }
        List<Referral> validReferrals = request.getReferrals().stream()
                .filter(notHavingErrors()).collect(Collectors.toList());
        log.info("validation successful, found valid referrals");
        return new Tuple<>(validReferrals, errorDetailsMap);
    }
}
