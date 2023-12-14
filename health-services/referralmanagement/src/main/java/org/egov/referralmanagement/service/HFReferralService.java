package org.egov.referralmanagement.service;


import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.ds.Tuple;
import org.egov.common.models.ErrorDetails;
import org.egov.common.models.referralmanagement.hfreferral.HFReferral;
import org.egov.common.models.referralmanagement.hfreferral.HFReferralBulkRequest;
import org.egov.common.models.referralmanagement.hfreferral.HFReferralRequest;
import org.egov.common.models.referralmanagement.hfreferral.HFReferralSearchRequest;
import org.egov.common.service.IdGenService;
import org.egov.common.utils.CommonUtils;
import org.egov.common.validator.Validator;
import org.egov.referralmanagement.Constants;
import org.egov.referralmanagement.config.ReferralManagementConfiguration;
import org.egov.referralmanagement.repository.HFReferralRepository;
import org.egov.referralmanagement.service.enrichment.HFReferralEnrichmentService;
import org.egov.referralmanagement.validator.hfreferral.HfrIsDeletedValidator;
import org.egov.referralmanagement.validator.hfreferral.HfrNonExistentEntityValidator;
import org.egov.referralmanagement.validator.hfreferral.HfrNullIdValidator;
import org.egov.referralmanagement.validator.hfreferral.HfrProjectIdValidator;
import org.egov.referralmanagement.validator.hfreferral.HfrRowVersionValidator;
import org.egov.referralmanagement.validator.hfreferral.HfrUniqueEntityValidator;
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
public class HFReferralService {

    private final IdGenService idGenService;

    private final HFReferralRepository hfReferralRepository;

    private final ReferralManagementConfiguration referralManagementConfiguration;

    private final HFReferralEnrichmentService hfReferralEnrichmentService;

    private final List<Validator<HFReferralBulkRequest, HFReferral>> validators;

    private final Predicate<Validator<HFReferralBulkRequest, HFReferral>> isApplicableForCreate = validator ->
            validator.getClass().equals(HfrProjectIdValidator.class)
                || validator.getClass().equals(HfrRowVersionValidator.class);

    private final Predicate<Validator<HFReferralBulkRequest, HFReferral>> isApplicableForUpdate = validator ->
            validator.getClass().equals(HfrProjectIdValidator.class)
                || validator.getClass().equals(HfrNullIdValidator.class)
                || validator.getClass().equals(HfrIsDeletedValidator.class)
                || validator.getClass().equals(HfrUniqueEntityValidator.class)
                || validator.getClass().equals(HfrNonExistentEntityValidator.class)
                || validator.getClass().equals(HfrRowVersionValidator.class);

    private final Predicate<Validator<HFReferralBulkRequest, HFReferral>> isApplicableForDelete = validator ->
            validator.getClass().equals(HfrNullIdValidator.class)
                || validator.getClass().equals(HfrNonExistentEntityValidator.class)
                || validator.getClass().equals(HfrRowVersionValidator.class);


    public HFReferralService(IdGenService idGenService, HFReferralRepository hfReferralRepository, ReferralManagementConfiguration referralManagementConfiguration, HFReferralEnrichmentService referralManagementEnrichmentService, List<Validator<HFReferralBulkRequest, HFReferral>> validators) {
        this.idGenService = idGenService;
        this.hfReferralRepository = hfReferralRepository;
        this.referralManagementConfiguration = referralManagementConfiguration;
        this.hfReferralEnrichmentService = referralManagementEnrichmentService;
        this.validators = validators;
    }

    public HFReferral create(HFReferralRequest request) {
        log.info("received request to create referrals");
        HFReferralBulkRequest bulkRequest = HFReferralBulkRequest.builder().requestInfo(request.getRequestInfo())
                .hfReferrals(Collections.singletonList(request.getHfReferral())).build();
        log.info("creating bulk request");
        return create(bulkRequest, false).get(0);
    }

    public List<HFReferral> create(HFReferralBulkRequest hfReferralRequest, boolean isBulk) {
        log.info("received request to create bulk referrals");
        Tuple<List<HFReferral>, Map<HFReferral, ErrorDetails>> tuple = validate(validators,
                isApplicableForCreate, hfReferralRequest, isBulk);
        Map<HFReferral, ErrorDetails> errorDetailsMap = tuple.getY();
        List<HFReferral> validReferrals = tuple.getX();

        try {
            if (!validReferrals.isEmpty()) {
                log.info("processing {} valid entities", validReferrals.size());
                hfReferralEnrichmentService.create(validReferrals, hfReferralRequest);
                hfReferralRepository.save(validReferrals,
                        referralManagementConfiguration.getCreateReferralTopic());
                log.info("successfully created referrals");
            }
        } catch (Exception exception) {
            log.error("error occurred while creating referrals: {}", exception.getMessage());
            populateErrorDetails(hfReferralRequest, errorDetailsMap, validReferrals,
                    exception, Constants.SET_REFERRALS);
        }
        handleErrors(errorDetailsMap, isBulk, Constants.VALIDATION_ERROR);

        return validReferrals;
    }

    public HFReferral update(HFReferralRequest request) {
        log.info("received request to update referral");
        HFReferralBulkRequest bulkRequest = HFReferralBulkRequest.builder().requestInfo(request.getRequestInfo())
                .hfReferrals(Collections.singletonList(request.getHfReferral())).build();
        log.info("creating bulk request");
        return update(bulkRequest, false).get(0);
    }

    public List<HFReferral> update(HFReferralBulkRequest hfReferralRequest, boolean isBulk) {
        log.info("received request to update bulk referral");
        Tuple<List<HFReferral>, Map<HFReferral, ErrorDetails>> tuple = validate(validators,
                isApplicableForUpdate, hfReferralRequest, isBulk);
        Map<HFReferral, ErrorDetails> errorDetailsMap = tuple.getY();
        List<HFReferral> validReferrals = tuple.getX();

        try {
            if (!validReferrals.isEmpty()) {
                log.info("processing {} valid entities", validReferrals.size());
                hfReferralEnrichmentService.update(validReferrals, hfReferralRequest);
                hfReferralRepository.save(validReferrals,
                        referralManagementConfiguration.getUpdateReferralTopic());
                log.info("successfully updated bulk referrals");
            }
        } catch (Exception exception) {
            log.error("error occurred while updating referrals", exception);
            populateErrorDetails(hfReferralRequest, errorDetailsMap, validReferrals,
                    exception, Constants.SET_REFERRALS);
        }
        handleErrors(errorDetailsMap, isBulk, Constants.VALIDATION_ERROR);

        return validReferrals;
    }

    public List<HFReferral> search(HFReferralSearchRequest referralSearchRequest,
                                   Integer limit,
                                   Integer offset,
                                   String tenantId,
                                   Long lastChangedSince,
                                   Boolean includeDeleted) {
        log.info("received request to search referrals");
        String idFieldName = getIdFieldName(referralSearchRequest.getHfReferral());
        if (isSearchByIdOnly(referralSearchRequest.getHfReferral(), idFieldName)) {
            log.info("searching referrals by id");
            List<String> ids = (List<String>) ReflectionUtils.invokeMethod(getIdMethod(Collections
                            .singletonList(referralSearchRequest.getHfReferral())),
                    referralSearchRequest.getHfReferral());
            log.info("fetching referrals with ids: {}", ids);
            return hfReferralRepository.findById(ids, includeDeleted, idFieldName).stream()
                    .filter(lastChangedSince(lastChangedSince))
                    .filter(havingTenantId(tenantId))
                    .filter(includeDeleted(includeDeleted))
                    .collect(Collectors.toList());
        }
        log.info("searching referrals using criteria");
        return hfReferralRepository.find(referralSearchRequest.getHfReferral(),
                limit, offset, tenantId, lastChangedSince, includeDeleted);
    }

    public HFReferral delete(HFReferralRequest hfReferralRequest) {
        log.info("received request to delete a referral");
        HFReferralBulkRequest bulkRequest = HFReferralBulkRequest.builder().requestInfo(hfReferralRequest.getRequestInfo())
                .hfReferrals(Collections.singletonList(hfReferralRequest.getHfReferral())).build();
        log.info("creating bulk request");
        return delete(bulkRequest, false).get(0);
    }

    public List<HFReferral> delete(HFReferralBulkRequest hfReferralRequest, boolean isBulk) {
        Tuple<List<HFReferral>, Map<HFReferral, ErrorDetails>> tuple = validate(validators,
                isApplicableForDelete, hfReferralRequest, isBulk);
        Map<HFReferral, ErrorDetails> errorDetailsMap = tuple.getY();
        List<HFReferral> validReferrals = tuple.getX();

        try {
            if (!validReferrals.isEmpty()) {
                log.info("processing {} valid entities", validReferrals.size());
                List<String> referralIds = validReferrals.stream().map(entity -> entity.getId()).collect(Collectors.toSet()).stream().collect(Collectors.toList());
                List<HFReferral> existingReferrals = hfReferralRepository
                        .findById(referralIds, false);
                hfReferralEnrichmentService.delete(existingReferrals, hfReferralRequest);
                hfReferralRepository.save(existingReferrals,
                        referralManagementConfiguration.getDeleteReferralTopic());
                log.info("successfully deleted entities");
            }
        } catch (Exception exception) {
            log.error("error occurred while deleting entities: {}", exception);
            populateErrorDetails(hfReferralRequest, errorDetailsMap, validReferrals,
                    exception, Constants.SET_REFERRALS);
        }
        handleErrors(errorDetailsMap, isBulk, Constants.VALIDATION_ERROR);

        return validReferrals;
    }

    public void putInCache(List<HFReferral> hfReferrals) {
        log.info("putting {} hfReferrals in cache", hfReferrals.size());
        hfReferralRepository.putInCache(hfReferrals);
        log.info("successfully put hfReferrals in cache");
    }

    private Tuple<List<HFReferral>, Map<HFReferral, ErrorDetails>> validate(
            List<Validator<HFReferralBulkRequest, HFReferral>> validators,
            Predicate<Validator<HFReferralBulkRequest, HFReferral>> isApplicable,
            HFReferralBulkRequest request,
            boolean isBulk
    ) {
        log.info("validating request");
        Map<HFReferral, ErrorDetails> errorDetailsMap = CommonUtils.validate(validators,
                isApplicable, request,
                Constants.SET_REFERRALS);
        if (!errorDetailsMap.isEmpty() && !isBulk) {
            log.error("validation error occurred. error details: {}", errorDetailsMap.values().toString());
            throw new CustomException(Constants.VALIDATION_ERROR, errorDetailsMap.values().toString());
        }
        List<HFReferral> validReferrals = request.getHfReferrals().stream()
                .filter(notHavingErrors()).collect(Collectors.toList());
        log.info("validation successful, found valid referrals");
        return new Tuple<>(validReferrals, errorDetailsMap);
    }
}
