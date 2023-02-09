package org.egov.household.service;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.data.query.exception.QueryBuilderException;
import org.egov.common.ds.Tuple;
import org.egov.common.http.client.ServiceRequestClient;
import org.egov.common.models.ErrorDetails;
import org.egov.common.utils.CommonUtils;
import org.egov.common.validator.Validator;
import org.egov.household.config.HouseholdMemberConfiguration;
import org.egov.household.household.member.validators.HmHouseholdHeadValidator;
import org.egov.household.household.member.validators.HmHouseholdValidator;
import org.egov.household.household.member.validators.HmIndividualValidator;
import org.egov.household.household.member.validators.HmIsDeletedValidator;
import org.egov.household.household.member.validators.HmNonExistentEntityValidator;
import org.egov.household.household.member.validators.HmNullIdValidator;
import org.egov.household.household.member.validators.HmRowVersionValidator;
import org.egov.household.household.member.validators.HmUniqueEntityValidator;
import org.egov.household.household.member.validators.HmUniqueIndividualValidator;
import org.egov.household.repository.HouseholdMemberRepository;
import org.egov.household.web.models.HouseholdMember;
import org.egov.household.web.models.HouseholdMemberBulkRequest;
import org.egov.household.web.models.HouseholdMemberRequest;
import org.egov.household.web.models.HouseholdMemberSearch;
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
import static org.egov.common.utils.CommonUtils.havingTenantId;
import static org.egov.common.utils.CommonUtils.includeDeleted;
import static org.egov.common.utils.CommonUtils.isSearchByIdOnly;
import static org.egov.common.utils.CommonUtils.lastChangedSince;
import static org.egov.common.utils.CommonUtils.notHavingErrors;
import static org.egov.common.utils.CommonUtils.populateErrorDetails;
import static org.egov.household.Constants.SET_HOUSEHOLD_MEMBERS;
import static org.egov.household.Constants.VALIDATION_ERROR;

@Service
@Slf4j
public class HouseholdMemberService {

    private final HouseholdMemberRepository householdMemberRepository;

    private final HouseholdService householdService;

    private final HouseholdMemberConfiguration householdMemberConfiguration;

    private final ServiceRequestClient serviceRequestClient;
    
    private final HouseholdMemberEnrichmentService householdMemberEnrichmentService;

    private final List<Validator<HouseholdMemberBulkRequest, HouseholdMember>> validators;

    private final Predicate<Validator<HouseholdMemberBulkRequest, HouseholdMember>> isApplicableForUpdate = validator ->
            validator.getClass().equals(HmNullIdValidator.class)
                    || validator.getClass().equals(HmNonExistentEntityValidator.class)
                    || validator.getClass().equals(HmIsDeletedValidator.class)
                    || validator.getClass().equals(HmRowVersionValidator.class)
                    || validator.getClass().equals(HmUniqueEntityValidator.class)
                    || validator.getClass().equals(HmHouseholdValidator.class)
                    || validator.getClass().equals(HmIndividualValidator.class)
                    || validator.getClass().equals(HmHouseholdHeadValidator.class);

    private final Predicate<Validator<HouseholdMemberBulkRequest, HouseholdMember>> isApplicableForCreate = validator ->
            validator.getClass().equals(HmHouseholdValidator.class)
                    || validator.getClass().equals(HmUniqueIndividualValidator.class)
                    || validator.getClass().equals(HmHouseholdHeadValidator.class);

    private final Predicate<Validator<HouseholdMemberBulkRequest, HouseholdMember>> isApplicableForDelete = validator ->
            validator.getClass().equals(HmNullIdValidator.class)
                    || validator.getClass().equals(HmNonExistentEntityValidator.class);

    @Autowired
    public HouseholdMemberService(HouseholdMemberRepository householdMemberRepository,
                                  HouseholdMemberConfiguration householdMemberConfiguration,
                                  ServiceRequestClient serviceRequestClient,
                                  HouseholdService householdService,
                                  HouseholdMemberEnrichmentService householdMemberEnrichmentService,
                                  List<Validator<HouseholdMemberBulkRequest, HouseholdMember>> validators) {
        this.householdMemberRepository = householdMemberRepository;
        this.householdMemberConfiguration = householdMemberConfiguration;
        this.serviceRequestClient = serviceRequestClient;
        this.householdService = householdService;
        this.householdMemberEnrichmentService = householdMemberEnrichmentService;
        this.validators = validators;
    }

    public List<HouseholdMember> create(HouseholdMemberRequest householdMemberRequest) 
            throws Exception {
        HouseholdMemberBulkRequest householdMemberBulkRequest = HouseholdMemberBulkRequest.builder()
                .requestInfo(householdMemberRequest.getRequestInfo())
                .householdMembers(Collections.singletonList(householdMemberRequest.getHouseholdMember()))
                .build();
        return create(householdMemberBulkRequest, false);
    }

    public List<HouseholdMember> create(HouseholdMemberBulkRequest householdMemberBulkRequest, boolean isBulk) {
        Tuple<List<HouseholdMember>, Map<HouseholdMember, ErrorDetails>> tuple = validate(validators,
                isApplicableForCreate, householdMemberBulkRequest, isBulk);
        Map<HouseholdMember, ErrorDetails> errorDetailsMap = tuple.getY();
        List<HouseholdMember> validHouseholdMembers = tuple.getX();

        try {
            if (!validHouseholdMembers.isEmpty()) {
                householdMemberEnrichmentService.create(validHouseholdMembers, householdMemberBulkRequest);
                householdMemberRepository.save(validHouseholdMembers,
                        householdMemberConfiguration.getCreateTopic());
            }
        } catch (Exception exception) {
            log.error("error occurred", exception);
            populateErrorDetails(householdMemberBulkRequest, errorDetailsMap, validHouseholdMembers,
                    exception, SET_HOUSEHOLD_MEMBERS);
        }
        handleErrors(isBulk, errorDetailsMap);

        return validHouseholdMembers;
    }


    public List<HouseholdMember> search(HouseholdMemberSearch householdMemberSearch, Integer limit, Integer offset, String tenantId,
                                        Long lastChangedSince, Boolean includeDeleted) {

        String idFieldName = getIdFieldName(householdMemberSearch);
        if (isSearchByIdOnly(householdMemberSearch, idFieldName)) {
            List<String> ids = (List<String>) ReflectionUtils.invokeMethod(getIdMethod(Collections
                            .singletonList(householdMemberSearch)),
                    householdMemberSearch);
            return householdMemberRepository.findById(ids,
                    idFieldName, includeDeleted).stream()
                    .filter(lastChangedSince(lastChangedSince))
                    .filter(havingTenantId(tenantId))
                    .filter(includeDeleted(includeDeleted))
                    .collect(Collectors.toList());
        }
        try {
            return householdMemberRepository.find(householdMemberSearch, limit, offset,
                    tenantId, lastChangedSince, includeDeleted);
        } catch (QueryBuilderException e) {
            throw new CustomException("ERROR_IN_QUERY", e.getMessage());
        }
    }

    public List<HouseholdMember> update(HouseholdMemberRequest householdMemberRequest) {
        HouseholdMemberBulkRequest householdMemberBulkRequest = HouseholdMemberBulkRequest.builder()
                .requestInfo(householdMemberRequest.getRequestInfo())
                .householdMembers(Collections.singletonList(householdMemberRequest.getHouseholdMember()))
                .build();
        return update(householdMemberBulkRequest, false);
    }

    public List<HouseholdMember> update(HouseholdMemberBulkRequest householdMemberBulkRequest, boolean isBulk) {
        Tuple<List<HouseholdMember>, Map<HouseholdMember, ErrorDetails>> tuple = validate(validators,
                isApplicableForUpdate, householdMemberBulkRequest, isBulk);
        Map<HouseholdMember, ErrorDetails> errorDetailsMap = tuple.getY();
        List<HouseholdMember> validHouseholdMembers = tuple.getX();

        try {
            if (!validHouseholdMembers.isEmpty()) {
                householdMemberEnrichmentService.update(validHouseholdMembers, householdMemberBulkRequest);
                householdMemberRepository.save(validHouseholdMembers,
                        householdMemberConfiguration.getUpdateTopic());
            }
        } catch (Exception exception) {
            log.error("error occurred", exception);
            populateErrorDetails(householdMemberBulkRequest, errorDetailsMap, validHouseholdMembers,
                    exception, SET_HOUSEHOLD_MEMBERS);
        }
        handleErrors(isBulk, errorDetailsMap);

        return validHouseholdMembers;
    }

    public List<HouseholdMember> delete(HouseholdMemberRequest householdMemberRequest) {
        HouseholdMemberBulkRequest bulkRequest = HouseholdMemberBulkRequest.builder().requestInfo(householdMemberRequest.getRequestInfo())
                .householdMembers(Collections.singletonList(householdMemberRequest.getHouseholdMember())).build();
        return delete(bulkRequest, false);
    }

    public List<HouseholdMember> delete(HouseholdMemberBulkRequest householdMemberBulkRequest, boolean isBulk) {
        Tuple<List<HouseholdMember>, Map<HouseholdMember, ErrorDetails>> tuple = validate(validators,
                isApplicableForDelete, householdMemberBulkRequest, isBulk);
        Map<HouseholdMember, ErrorDetails> errorDetailsMap = tuple.getY();
        List<HouseholdMember> validHouseholdMembers = tuple.getX();

        try {
            if (!validHouseholdMembers.isEmpty()) {
                householdMemberEnrichmentService.delete(validHouseholdMembers, householdMemberBulkRequest);
                householdMemberRepository.save(validHouseholdMembers,
                        householdMemberConfiguration.getDeleteTopic());
            }
        } catch (Exception exception) {
            log.error("error occurred", exception);
            populateErrorDetails(householdMemberBulkRequest, errorDetailsMap, validHouseholdMembers,
                    exception, SET_HOUSEHOLD_MEMBERS);
        }
        handleErrors(isBulk, errorDetailsMap);

        return validHouseholdMembers;
    }

    private Tuple<List<HouseholdMember>, Map<HouseholdMember, ErrorDetails>> validate(List<Validator<HouseholdMemberBulkRequest,
            HouseholdMember>> validators, Predicate<Validator<HouseholdMemberBulkRequest,
            HouseholdMember>> isApplicable, HouseholdMemberBulkRequest request, boolean isBulk) {
        log.info("validating request");
        Map<HouseholdMember, ErrorDetails> errorDetailsMap = CommonUtils.validate(validators,
                isApplicable, request,
                SET_HOUSEHOLD_MEMBERS);
        if (!errorDetailsMap.isEmpty() && !isBulk) {
            throw new CustomException(VALIDATION_ERROR, errorDetailsMap.values().toString());
        }
        List<HouseholdMember> householdMembers = request.getHouseholdMembers().stream()
                .filter(notHavingErrors()).collect(Collectors.toList());
        return new Tuple<>(householdMembers, errorDetailsMap);
    }

    private static void handleErrors(boolean isBulk, Map<HouseholdMember, ErrorDetails> errorDetailsMap) {
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
