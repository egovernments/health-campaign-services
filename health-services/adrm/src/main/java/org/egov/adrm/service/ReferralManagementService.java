package org.egov.adrm.service;

import lombok.extern.slf4j.Slf4j;
import org.egov.adrm.Constants;
import org.egov.adrm.config.AdrmConfiguration;
import org.egov.adrm.repository.ReferralManagementRepository;
import org.egov.adrm.service.enrichment.ReferralManagementEnrichmentService;
import org.egov.adrm.validator.rm.RmFacilityEntitiesIdValidator;
import org.egov.adrm.validator.rm.RmIsDeletedValidator;
import org.egov.adrm.validator.rm.RmNonExistentEntityValidator;
import org.egov.adrm.validator.rm.RmNullIdValidator;
import org.egov.adrm.validator.rm.RmProjectEntitiesIdValidator;
import org.egov.adrm.validator.rm.RmUniqueEntityValidator;
import org.egov.common.ds.Tuple;
import org.egov.common.models.ErrorDetails;
import org.egov.common.models.adrm.referralmanagement.Referral;
import org.egov.common.models.adrm.referralmanagement.ReferralBulkRequest;
import org.egov.common.models.adrm.referralmanagement.ReferralRequest;
import org.egov.common.models.adrm.referralmanagement.ReferralSearchRequest;
import org.egov.common.service.IdGenService;
import org.egov.common.utils.CommonUtils;
import org.egov.common.validator.Validator;
import org.egov.tracer.model.CustomException;
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

@Service
@Slf4j
public class ReferralManagementService {
    private final IdGenService idGenService;

    private final ReferralManagementRepository referralManagementRepository;

    private final AdrmConfiguration adrmConfiguration;

    private final ReferralManagementEnrichmentService referralManagementEnrichmentService;

    private final List<Validator<ReferralBulkRequest, Referral>> validators;

    private final Predicate<Validator<ReferralBulkRequest, Referral>> isApplicableForCreate = validator ->
            validator.getClass().equals(RmProjectEntitiesIdValidator.class)
                || validator.getClass().equals(RmFacilityEntitiesIdValidator.class);

    private final Predicate<Validator<ReferralBulkRequest, Referral>> isApplicableForUpdate = validator ->
            validator.getClass().equals(RmProjectEntitiesIdValidator.class)
                || validator.getClass().equals(RmFacilityEntitiesIdValidator.class)
                || validator.getClass().equals(RmNullIdValidator.class)
                || validator.getClass().equals(RmIsDeletedValidator.class)
                || validator.getClass().equals(RmUniqueEntityValidator.class)
                || validator.getClass().equals(RmNonExistentEntityValidator.class);

    private final Predicate<Validator<ReferralBulkRequest, Referral>> isApplicableForDelete = validator ->
            validator.getClass().equals(RmNullIdValidator.class)
                    || validator.getClass().equals(RmNonExistentEntityValidator.class);


    public ReferralManagementService(IdGenService idGenService, ReferralManagementRepository referralManagementRepository, AdrmConfiguration adrmConfiguration, ReferralManagementEnrichmentService referralManagementEnrichmentService, List<Validator<ReferralBulkRequest, Referral>> validators) {
        this.idGenService = idGenService;
        this.referralManagementRepository = referralManagementRepository;
        this.adrmConfiguration = adrmConfiguration;
        this.referralManagementEnrichmentService = referralManagementEnrichmentService;
        this.validators = validators;
    }

    public Referral create(ReferralRequest request) {
        log.info("received request to create adverse events");
        ReferralBulkRequest bulkRequest = ReferralBulkRequest.builder().requestInfo(request.getRequestInfo())
                .referrals(Collections.singletonList(request.getReferral())).build();
        log.info("creating bulk request");
        return create(bulkRequest, false).get(0);
    }

    public List<Referral> create(ReferralBulkRequest referralRequest, boolean isBulk) {
        log.info("received request to create bulk adverse events");
        Tuple<List<Referral>, Map<Referral, ErrorDetails>> tuple = validate(validators,
                isApplicableForCreate, referralRequest, isBulk);
        Map<Referral, ErrorDetails> errorDetailsMap = tuple.getY();
        List<Referral> validReferrals = tuple.getX();

        try {
            if (!validReferrals.isEmpty()) {
                log.info("processing {} valid entities", validReferrals.size());
                referralManagementEnrichmentService.create(validReferrals, referralRequest);
                referralManagementRepository.save(validReferrals,
                        adrmConfiguration.getCreateReferralTopic());
                log.info("successfully created adverse events");
            }
        } catch (Exception exception) {
            log.error("error occurred while creating adverse events: {}", exception.getMessage());
            populateErrorDetails(referralRequest, errorDetailsMap, validReferrals,
                    exception, Constants.SET_ADVERSE_EVENTS);
        }
        handleErrors(errorDetailsMap, isBulk, Constants.VALIDATION_ERROR);

        return validReferrals;
    }

    public Referral update(ReferralRequest request) {
        log.info("received request to update adverse event");
        ReferralBulkRequest bulkRequest = ReferralBulkRequest.builder().requestInfo(request.getRequestInfo())
                .referrals(Collections.singletonList(request.getReferral())).build();
        log.info("creating bulk request");
        return update(bulkRequest, false).get(0);
    }

    public List<Referral> update(ReferralBulkRequest referralRequest, boolean isBulk) {
        log.info("received request to update bulk adverse event");
        Tuple<List<Referral>, Map<Referral, ErrorDetails>> tuple = validate(validators,
                isApplicableForUpdate, referralRequest, isBulk);
        Map<Referral, ErrorDetails> errorDetailsMap = tuple.getY();
        List<Referral> validReferrals = tuple.getX();

        try {
            if (!validReferrals.isEmpty()) {
                log.info("processing {} valid entities", validReferrals.size());
                referralManagementEnrichmentService.update(validReferrals, referralRequest);
                referralManagementRepository.save(validReferrals,
                        adrmConfiguration.getUpdateReferralTopic());
                log.info("successfully updated bulk adverse events");
            }
        } catch (Exception exception) {
            log.error("error occurred while updating adverse events", exception);
            populateErrorDetails(referralRequest, errorDetailsMap, validReferrals,
                    exception, Constants.SET_ADVERSE_EVENTS);
        }
        handleErrors(errorDetailsMap, isBulk, Constants.VALIDATION_ERROR);

        return validReferrals;
    }

    public List<Referral> search(ReferralSearchRequest referralSearchRequest,
                                 Integer limit,
                                 Integer offset,
                                 String tenantId,
                                 Long lastChangedSince,
                                 Boolean includeDeleted) throws Exception {
        log.info("received request to search adverse events");
        String idFieldName = getIdFieldName(referralSearchRequest.getReferral());
        if (isSearchByIdOnly(referralSearchRequest.getReferral(), idFieldName)) {
            log.info("searching adverse events by id");
            List<String> ids = (List<String>) ReflectionUtils.invokeMethod(getIdMethod(Collections
                            .singletonList(referralSearchRequest.getReferral())),
                    referralSearchRequest.getReferral());
            log.info("fetching adverse events with ids: {}", ids);
            return referralManagementRepository.findById(ids, includeDeleted, idFieldName).stream()
                    .filter(lastChangedSince(lastChangedSince))
                    .filter(havingTenantId(tenantId))
                    .filter(includeDeleted(includeDeleted))
                    .collect(Collectors.toList());
        }
        log.info("searching adverse events using criteria");
        return referralManagementRepository.find(referralSearchRequest.getReferral(),
                limit, offset, tenantId, lastChangedSince, includeDeleted);
    }

    public Referral delete(ReferralRequest referralRequest) {
        log.info("received request to delete a adverse event");
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
                List<Referral> existingReferrals = referralManagementRepository
                        .findById(referralIds, false);
                referralManagementEnrichmentService.delete(existingReferrals, referralRequest);
                referralManagementRepository.save(existingReferrals,
                        adrmConfiguration.getDeleteReferralTopic());
                log.info("successfully deleted entities");
            }
        } catch (Exception exception) {
            log.error("error occurred while deleting entities: {}", exception);
            populateErrorDetails(referralRequest, errorDetailsMap, validReferrals,
                    exception, Constants.SET_ADVERSE_EVENTS);
        }
        handleErrors(errorDetailsMap, isBulk, Constants.VALIDATION_ERROR);

        return validReferrals;
    }

    public void putInCache(List<Referral> referrals) {
        log.info("putting {} adverse events in cache", referrals.size());
        referralManagementRepository.putInCache(referrals);
        log.info("successfully put adverse events in cache");
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
                Constants.SET_ADVERSE_EVENTS);
        if (!errorDetailsMap.isEmpty() && !isBulk) {
            log.error("validation error occurred. error details: {}", errorDetailsMap.values().toString());
            throw new CustomException(Constants.VALIDATION_ERROR, errorDetailsMap.values().toString());
        }
        List<Referral> validReferrals = request.getReferrals().stream()
                .filter(notHavingErrors()).collect(Collectors.toList());
        log.info("validation successful, found valid adverse events");
        return new Tuple<>(validReferrals, errorDetailsMap);
    }
}
