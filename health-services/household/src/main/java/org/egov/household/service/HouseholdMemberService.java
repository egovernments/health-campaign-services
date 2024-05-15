package org.egov.household.service;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.data.query.exception.QueryBuilderException;
import org.egov.common.ds.Tuple;
import org.egov.common.http.client.ServiceRequestClient;
import org.egov.common.models.ErrorDetails;
import org.egov.common.models.household.HouseholdMember;
import org.egov.common.models.household.HouseholdMemberBulkRequest;
import org.egov.common.models.household.HouseholdMemberRequest;
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
import static org.egov.common.utils.CommonUtils.handleErrors;
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

    public List<HouseholdMember> create(HouseholdMemberRequest householdMemberRequest) {
        log.info("received request to create household member");
        HouseholdMemberBulkRequest householdMemberBulkRequest = HouseholdMemberBulkRequest.builder()
                .requestInfo(householdMemberRequest.getRequestInfo())
                .householdMembers(Collections.singletonList(householdMemberRequest.getHouseholdMember()))
                .build();
        log.info("creating bulk request for household member creation");
        List<HouseholdMember> householdMembers = create(householdMemberBulkRequest, false);
        log.info("household member created successfully");
        return householdMembers;
    }

    public List<HouseholdMember> create(HouseholdMemberBulkRequest householdMemberBulkRequest, boolean isBulk) {
        Tuple<List<HouseholdMember>, Map<HouseholdMember, ErrorDetails>> tuple = validate(validators,
                isApplicableForCreate, householdMemberBulkRequest, isBulk);
        Map<HouseholdMember, ErrorDetails> errorDetailsMap = tuple.getY();
        List<HouseholdMember> validHouseholdMembers = tuple.getX();

        try {
            if (!validHouseholdMembers.isEmpty()) {
                log.info("enriching valid household members for creation");
                householdMemberEnrichmentService.create(validHouseholdMembers, householdMemberBulkRequest);

                log.info("saving valid household members to the repository");
                householdMemberRepository.save(validHouseholdMembers,
                        householdMemberConfiguration.getCreateTopic());
                log.info("household members data saved successfully");
            }
        } catch (Exception exception) {
            log.error("error occurred while creating household members: ", exception);
            populateErrorDetails(householdMemberBulkRequest, errorDetailsMap, validHouseholdMembers,
                    exception, SET_HOUSEHOLD_MEMBERS);
        }
        handleErrors(errorDetailsMap, isBulk, VALIDATION_ERROR);

        return validHouseholdMembers;
    }


    public List<HouseholdMember> search(HouseholdMemberSearch householdMemberSearch, Integer limit, Integer offset, String tenantId,
                                        Long lastChangedSince, Boolean includeDeleted) {

        String idFieldName = getIdFieldName(householdMemberSearch);
        if (isSearchByIdOnly(householdMemberSearch, idFieldName)) {
            List<String> ids = (List<String>) ReflectionUtils.invokeMethod(getIdMethod(Collections
                            .singletonList(householdMemberSearch)),
                    householdMemberSearch);
            List<HouseholdMember> householdMembers = householdMemberRepository.findById(ids,
                            idFieldName, includeDeleted).stream()
                                .filter(lastChangedSince(lastChangedSince))
                                .filter(havingTenantId(tenantId))
                                .filter(includeDeleted(includeDeleted))
                                .collect(Collectors.toList());
            log.info("found {} household members for search by id", householdMembers.size());
            return householdMembers;
        }
        try {
            return householdMemberRepository.find(householdMemberSearch, limit, offset,
                    tenantId, lastChangedSince, includeDeleted);
        } catch (QueryBuilderException e) {
            log.error("error in building query for household member search", e);
            throw new CustomException("ERROR_IN_QUERY", e.getMessage());
        }
    }

    public List<HouseholdMember> update(HouseholdMemberRequest householdMemberRequest) {
        log.info("starting update of household member for single request");
        HouseholdMemberBulkRequest householdMemberBulkRequest = HouseholdMemberBulkRequest.builder()
                .requestInfo(householdMemberRequest.getRequestInfo())
                .householdMembers(Collections.singletonList(householdMemberRequest.getHouseholdMember()))
                .build();
        log.info("finished update of household member for single request");
        return update(householdMemberBulkRequest, false);
    }

    public List<HouseholdMember> update(HouseholdMemberBulkRequest householdMemberBulkRequest, boolean isBulk) {
        Tuple<List<HouseholdMember>, Map<HouseholdMember, ErrorDetails>> tuple = validate(validators,
                isApplicableForUpdate, householdMemberBulkRequest, isBulk);
        Map<HouseholdMember, ErrorDetails> errorDetailsMap = tuple.getY();
        List<HouseholdMember> validHouseholdMembers = tuple.getX();

        log.info("number of valid household members to be updated: {}", validHouseholdMembers.size());

        try {
            if (!validHouseholdMembers.isEmpty()) {
                householdMemberEnrichmentService.update(validHouseholdMembers, householdMemberBulkRequest);
                householdMemberRepository.save(validHouseholdMembers,
                        householdMemberConfiguration.getUpdateTopic());
                log.info("household member data updated successfully");
            }
        } catch (Exception exception) {
            log.error("error occurred", exception);
            populateErrorDetails(householdMemberBulkRequest, errorDetailsMap, validHouseholdMembers,
                    exception, SET_HOUSEHOLD_MEMBERS);
        }
        handleErrors(errorDetailsMap, isBulk, VALIDATION_ERROR);

        return validHouseholdMembers;
    }

    public List<HouseholdMember> delete(HouseholdMemberRequest householdMemberRequest) {
        HouseholdMemberBulkRequest bulkRequest = HouseholdMemberBulkRequest.builder().requestInfo(householdMemberRequest.getRequestInfo())
                .householdMembers(Collections.singletonList(householdMemberRequest.getHouseholdMember())).build();
        log.info("delete household member bulk request: {}", bulkRequest);
        return delete(bulkRequest, false);
    }

    public List<HouseholdMember> delete(HouseholdMemberBulkRequest householdMemberBulkRequest, boolean isBulk) {
        Tuple<List<HouseholdMember>, Map<HouseholdMember, ErrorDetails>> tuple = validate(validators,
                isApplicableForDelete, householdMemberBulkRequest, isBulk);
        Map<HouseholdMember, ErrorDetails> errorDetailsMap = tuple.getY();
        List<HouseholdMember> validHouseholdMembers = tuple.getX();

        log.info("valid Household Members for delete operation: {}", validHouseholdMembers);

        try {
            if (!validHouseholdMembers.isEmpty()) {
                householdMemberEnrichmentService.delete(validHouseholdMembers, householdMemberBulkRequest);
                householdMemberRepository.save(validHouseholdMembers,
                        householdMemberConfiguration.getDeleteTopic());
                log.info("deleted Household Members: {}", validHouseholdMembers);
            }
        } catch (Exception exception) {
            log.error("error occurred while deleting household members", exception);
            populateErrorDetails(householdMemberBulkRequest, errorDetailsMap, validHouseholdMembers,
                    exception, SET_HOUSEHOLD_MEMBERS);
        }
        handleErrors(errorDetailsMap, isBulk, VALIDATION_ERROR);

        return validHouseholdMembers;
    }

    public void putInCache(List<HouseholdMember> householdMembers) {
        log.info("putting {} household members in cache", householdMembers.size());
        householdMemberRepository.putInCache(householdMembers);
        log.info("successfully put household members in cache");
    }

    private Tuple<List<HouseholdMember>, Map<HouseholdMember, ErrorDetails>> validate(List<Validator<HouseholdMemberBulkRequest,
            HouseholdMember>> validators, Predicate<Validator<HouseholdMemberBulkRequest,
            HouseholdMember>> isApplicable, HouseholdMemberBulkRequest request, boolean isBulk) {
        log.info("validating request for household members");
        Map<HouseholdMember, ErrorDetails> errorDetailsMap = CommonUtils.validate(validators,
                isApplicable, request,
                SET_HOUSEHOLD_MEMBERS);
        if (!errorDetailsMap.isEmpty() && !isBulk) {
            log.info("errors found in the request for household members");
            throw new CustomException(VALIDATION_ERROR, errorDetailsMap.values().toString());
        }
        List<HouseholdMember> householdMembers = request.getHouseholdMembers().stream()
                .filter(notHavingErrors()).collect(Collectors.toList());
        log.info("validating request for household members completed");
        return new Tuple<>(householdMembers, errorDetailsMap);
    }

}
