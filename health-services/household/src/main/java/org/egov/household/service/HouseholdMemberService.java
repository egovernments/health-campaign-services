package org.egov.household.service;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.data.query.exception.QueryBuilderException;
import org.egov.common.http.client.ServiceRequestClient;
import org.egov.common.utils.CommonUtils;
import org.egov.household.config.HouseholdMemberConfiguration;
import org.egov.household.repository.HouseholdMemberRepository;
import org.egov.household.web.models.Household;
import org.egov.household.web.models.HouseholdMember;
import org.egov.household.web.models.HouseholdMemberRequest;
import org.egov.household.web.models.HouseholdMemberSearch;
import org.egov.household.web.models.HouseholdMemberSearchRequest;
import org.egov.household.web.models.Individual;
import org.egov.household.web.models.IndividualResponse;
import org.egov.household.web.models.IndividualSearch;
import org.egov.household.web.models.IndividualSearchRequest;
import org.egov.tracer.model.CustomException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.egov.common.utils.CommonUtils.checkRowVersion;
import static org.egov.common.utils.CommonUtils.enrichForCreate;
import static org.egov.common.utils.CommonUtils.enrichForUpdate;
import static org.egov.common.utils.CommonUtils.getIdFieldName;
import static org.egov.common.utils.CommonUtils.getIdList;
import static org.egov.common.utils.CommonUtils.getIdMethod;
import static org.egov.common.utils.CommonUtils.getIdToObjMap;
import static org.egov.common.utils.CommonUtils.getSet;
import static org.egov.common.utils.CommonUtils.getTenantId;
import static org.egov.common.utils.CommonUtils.havingTenantId;
import static org.egov.common.utils.CommonUtils.includeDeleted;
import static org.egov.common.utils.CommonUtils.isSearchByIdOnly;
import static org.egov.common.utils.CommonUtils.lastChangedSince;
import static org.egov.common.utils.CommonUtils.validateEntities;

@Service
@Slf4j
public class HouseholdMemberService {

    private final HouseholdMemberRepository householdMemberRepository;

    private final HouseholdService householdService;

    private final HouseholdMemberConfiguration householdMemberConfiguration;

    private final ServiceRequestClient serviceRequestClient;

    @Value("${egov.individual.host}")
    private String individualServiceHost;

    @Value("${egov.individual.search.url}")
    private String individualServiceSearchUrl;

    @Autowired
    public HouseholdMemberService(HouseholdMemberRepository householdMemberRepository,
                                  HouseholdMemberConfiguration householdMemberConfiguration,
                                  ServiceRequestClient serviceRequestClient,
                                  HouseholdService householdService) {
        this.householdMemberRepository = householdMemberRepository;
        this.householdMemberConfiguration = householdMemberConfiguration;
        this.serviceRequestClient = serviceRequestClient;
        this.householdService = householdService;
    }

    public List<HouseholdMember> create(HouseholdMemberRequest householdMemberRequest) throws Exception {
        List<HouseholdMember> householdMembers = householdMemberRequest.getHouseholdMember();
        RequestInfo requestInfo = householdMemberRequest.getRequestInfo();
        String tenantId = getTenantId(householdMembers);

        Method idMethod = getIdMethod(householdMembers, "householdId", "householdClientReferenceId");
        String columnName = getColumnName(idMethod);

        List<Household> householdList = validateHouseholdIds(householdMembers, idMethod,  columnName);
        Method householdMethod = getIdMethod(householdList, columnName);
        Map<String, Household> householdMap = getIdToObjMap(householdList, householdMethod);

        for (HouseholdMember householdMember : householdMembers) {
            IndividualResponse searchResponse = searchIndividualBeneficiary(
                    householdMember,
                    requestInfo,
                    tenantId
            );

            List<HouseholdMember> individualSearchResult = validateIndividualMapping(householdMember, searchResponse);
            if(!individualSearchResult.isEmpty()) {
                throw new CustomException("INDIVIDUAL_ALREADY_MEMBER_OF_HOUSEHOLD", householdMember.getIndividualId());
            }
            enrichWithHouseholdId(householdMap, householdMember);
            validateHeadOfHousehold(householdMember);
        }

        List<String> uuidList = Stream.generate(UUID::randomUUID)
                .limit(householdMembers.size())
                .map(UUID::toString)
                .collect(Collectors.toList());

        enrichForCreate(householdMembers, uuidList, requestInfo);

        householdMemberRepository.save(householdMembers, householdMemberConfiguration.getCreateTopic());

        return householdMembers;
    }

    private void enrichWithHouseholdId(Map<String, Household> householdMap, HouseholdMember householdMember) {
        Household household = householdMap.get(getHouseholdId(householdMember));
        householdMember.setHouseholdId(household.getId());
        householdMember.setHouseholdClientReferenceId(household.getClientReferenceId());
    }

    private String getColumnName(Method idMethod) {
        String columnName = "id";
        if ("getHouseholdClientReferenceId".equals(idMethod.getName())) {
            columnName = "clientReferenceId";
        }
        return columnName;
    }

    private String getHouseholdId(HouseholdMember householdMember) {
        return householdMember.getHouseholdId()!=null
                ? householdMember.getHouseholdId() :
                householdMember.getHouseholdClientReferenceId();
    }

    private void validateHeadOfHousehold(HouseholdMember householdMember) {
        if(householdMember.getIsHeadOfHousehold()){
            List<HouseholdMember> householdMembersHeadCheck = householdMemberRepository.findIndividualByHousehold(householdMember.getHouseholdId()).stream().filter(
                    householdMember1 -> householdMember1.getIsHeadOfHousehold())
                    .collect(Collectors.toList());

            if(!householdMembersHeadCheck.isEmpty()){
                throw new CustomException("HOUSEHOLD_ALREADY_HAS_HEAD", householdMember.getIndividualId());
            }
        }
    }

    private List<HouseholdMember> validateIndividualMapping(HouseholdMember householdMember, IndividualResponse searchResponse) {
        List<Individual> individuals = searchResponse.getIndividual();
        if(individuals.isEmpty()){
            throw new CustomException("INDIVIDUAL_NOT_FOUND", householdMember.getIndividualId());
        }

        Individual individual = individuals.get(0);
        householdMember.setIndividualId(individual.getId());
        householdMember.setIndividualClientReferenceId(individual.getClientReferenceId());

        List<HouseholdMember> individualSearchResult = householdMemberRepository.findIndividual(individual.getId());
        return individualSearchResult;
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

    public List<HouseholdMember> update(HouseholdMemberRequest householdMemberRequest) throws Exception {
        List<HouseholdMember> householdMembers = householdMemberRequest.getHouseholdMember();
        RequestInfo requestInfo = householdMemberRequest.getRequestInfo();
        String tenantId = getTenantId(householdMembers);

        Method idMethod = getIdMethod(householdMembers, "householdId", "householdClientReferenceId");
        String columnName = getColumnName(idMethod);

        List<Household> householdList = validateHouseholdIds(householdMembers, idMethod,  columnName);
        Method householdMethod = getIdMethod(householdList, columnName);
        Map<String, Household> householdMap = getIdToObjMap(householdList, householdMethod);

        for (HouseholdMember householdMember : householdMembers) {
            IndividualResponse searchResponse = searchIndividualBeneficiary(
                    householdMember,
                    requestInfo,
                    tenantId
            );
            List<HouseholdMember> individualSearchResult = validateIndividualMapping(householdMember, searchResponse);
            if(individualSearchResult.isEmpty()) {
                throw new CustomException("INDIVIDUAL_NOT_MEMBER_OF_HOUSEHOLD", householdMember.getIndividualId());
            }
            enrichWithHouseholdId(householdMap, householdMember);
        }

        log.info("Checking if already exists");
        Method idMethodForUpdate = getIdMethod(householdMembers, "id");

        Map<String, HouseholdMember> hMap = getIdToObjMap(householdMembers, idMethodForUpdate);
        List<String> householdIds = new ArrayList<>(hMap.keySet());
        List<HouseholdMember> existingHouseholds = householdMemberRepository.findById(householdIds, "id", false);
        validateEntities(hMap, existingHouseholds, idMethodForUpdate);
        checkRowVersion(hMap, existingHouseholds, idMethodForUpdate);


        log.info("Updating lastModifiedTime and lastModifiedBy");
        enrichForUpdate(hMap, existingHouseholds, householdMemberRequest, idMethodForUpdate);

        householdMemberRepository.save(householdMembers, householdMemberConfiguration.getUpdateTopic());
        return householdMemberRequest.getHouseholdMember();
    }

    private IndividualResponse searchIndividualBeneficiary(
            HouseholdMember householdMember,
            RequestInfo requestInfo,
            String tenantId
    ) throws Exception {
        IndividualSearch individualSearch = null;

        if (householdMember.getIndividualId() != null) {
            individualSearch = IndividualSearch
                    .builder()
                    .id(householdMember.getIndividualId())
                    .build();
        } else if (householdMember.getIndividualClientReferenceId() != null) {
            individualSearch = IndividualSearch
                    .builder()
                    .clientReferenceId(householdMember.getIndividualClientReferenceId())
                    .build();
        }

        IndividualSearchRequest individualSearchRequest = IndividualSearchRequest.builder()
                .requestInfo(requestInfo)
                .individual(individualSearch)
                .build();

        return getIndividualResponse(tenantId, individualSearchRequest);
    }

    private IndividualResponse getIndividualResponse(String tenantId, IndividualSearchRequest individualSearchRequest) throws Exception {
        return serviceRequestClient.fetchResult(
                new StringBuilder(individualServiceHost + individualServiceSearchUrl + "?limit=10&offset=0&tenantId=" + tenantId),
                individualSearchRequest,
                IndividualResponse.class);
    }

    private List<Household> validateHouseholdIds(List<HouseholdMember> householdMembers, Method idMethod, String columnName) {
        List<String> houseHoldIds = getIdList(householdMembers, idMethod);
        List<Household> validHouseHoldIds = householdService.findById(houseHoldIds, columnName, false);
        Set<String> uniqueHoldIds = getSet(validHouseHoldIds, columnName == "id" ? "getId": "getClientReferenceId");

        List<String> invalidHouseholds = CommonUtils.getDifference(
                houseHoldIds,
                new ArrayList<>(uniqueHoldIds)
        );

        if(!invalidHouseholds.isEmpty()){
            log.error("Invalid Household Ids {}", invalidHouseholds);
            throw new CustomException("INVALID_HOUSEHOLD_ID", invalidHouseholds.toString());
        }

        return validHouseHoldIds.stream().distinct().collect(Collectors.toList());
    }

}
