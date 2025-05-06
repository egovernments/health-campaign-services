package org.egov.referralmanagement.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.ds.Tuple;
import org.egov.common.http.client.ServiceRequestClient;
import org.egov.common.models.household.Household;
import org.egov.common.models.household.HouseholdBulkResponse;
import org.egov.common.models.household.HouseholdMember;
import org.egov.common.models.household.HouseholdMemberBulkResponse;
import org.egov.common.models.household.HouseholdMemberSearch;
import org.egov.common.models.household.HouseholdMemberSearchRequest;
import org.egov.common.models.household.HouseholdSearch;
import org.egov.common.models.household.HouseholdSearchRequest;
import org.egov.common.models.individual.Individual;
import org.egov.common.models.individual.IndividualBulkResponse;
import org.egov.common.models.individual.IndividualSearch;
import org.egov.common.models.individual.IndividualSearchRequest;
import org.egov.common.models.project.BeneficiaryBulkResponse;
import org.egov.common.models.project.BeneficiarySearchRequest;
import org.egov.common.models.project.ProjectBeneficiary;
import org.egov.common.models.project.ProjectBeneficiarySearch;
import org.egov.common.models.project.Task;
import org.egov.common.models.project.TaskBulkResponse;
import org.egov.common.models.project.TaskSearch;
import org.egov.common.models.project.TaskSearchRequest;
import org.egov.common.models.referralmanagement.Referral;
import org.egov.common.models.referralmanagement.ReferralSearch;
import org.egov.common.models.referralmanagement.ReferralSearchRequest;
import org.egov.common.models.referralmanagement.beneficiarydownsync.*;
import org.egov.common.models.referralmanagement.sideeffect.SideEffect;
import org.egov.common.models.referralmanagement.sideeffect.SideEffectSearch;
import org.egov.common.models.referralmanagement.sideeffect.SideEffectSearchRequest;
import org.egov.referralmanagement.config.ReferralManagementConfiguration;
import org.egov.referralmanagement.repository.HouseholdRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

@Service
@Slf4j
public class DownsyncService {

    private ServiceRequestClient restClient;

    private ReferralManagementConfiguration configs;

    private NamedParameterJdbcTemplate jdbcTemplate;

    private SideEffectService sideEffectService;

    private ReferralManagementService referralService;

    private MasterDataService masterDataService;
    private HouseholdRepository householdRepository;

    private static final Integer SEARCH_MAX_COUNT = 1000;

    @Autowired
    public DownsyncService(ServiceRequestClient serviceRequestClient,
                           ReferralManagementConfiguration referralManagementConfiguration,
                           NamedParameterJdbcTemplate jdbcTemplate,
                           SideEffectService sideEffectService,
                           ReferralManagementService referralService,
                           MasterDataService masterDataService, HouseholdRepository householdRepository) {

        this.restClient = serviceRequestClient;
        this.configs = referralManagementConfiguration;
        this.jdbcTemplate = jdbcTemplate;
        this.sideEffectService=sideEffectService;
        this.referralService=referralService;
        this.masterDataService=masterDataService;
        this.householdRepository = householdRepository;
    }

    /**
     *
     * @param downsyncRequest
     * @return Downsync
     */
    public Downsync prepareDownsyncData(DownsyncRequest downsyncRequest, boolean isCLF) {

        Downsync downsync = new Downsync();
        DownsyncCriteria downsyncCriteria = downsyncRequest.getDownsyncCriteria();

        List<String> householdIds = null;
        Set<String> individualIds = null;
        List<String> individualClientRefIds = null;
        List<String> beneficiaryClientRefIds = null;
        List<String> taskClientRefIds = null;


        downsync.setDownsyncCriteria(downsyncCriteria);
        boolean isSyncTimeAvailable = null != downsyncCriteria.getLastSyncedTime();

        //Project project = getProjectType(downsyncRequest);
        LinkedHashMap<String, Object> projectType = masterDataService.getProjectType(downsyncRequest);

        /* search household */
        List<Household> households = null;
        if (isCLF) {
            households  = searchHouseholdsCLF(downsyncRequest);
        }else {
            householdIds = searchHouseholds(downsyncRequest, downsync);
        }
        if (CollectionUtils.isEmpty(householdIds)) {
            householdIds = (households == null)
                    ? Collections.emptyList()
                    : households.stream()
                    .map(Household::getId)
                    .collect(Collectors.toList());
        }

        /* search household member using household ids */
        if (isSyncTimeAvailable || !CollectionUtils.isEmpty(householdIds)) {
            individualIds = searchMembers(downsyncRequest, downsync, householdIds);
        }

        /* search individuals using individual ids */
        if (isSyncTimeAvailable || !CollectionUtils.isEmpty(individualIds) ) {
            individualClientRefIds = searchIndividuals(downsyncRequest, downsync, individualIds);
        }

        /* search beneficiary using individual ids OR household ids */

        String beneficiaryType = (String) projectType.get("beneficiaryType");

        beneficiaryClientRefIds = individualClientRefIds;

        if("HOUSEHOLD".equalsIgnoreCase(beneficiaryType)) {
            if (households == null)
                beneficiaryClientRefIds = downsync.getHouseholds().stream().map(Household::getClientReferenceId).collect(Collectors.toList());
            else
                beneficiaryClientRefIds = households.stream().map(Household::getClientReferenceId).collect(Collectors.toList());
        }


        //fetch beneficiary in the db
        if (isSyncTimeAvailable || !CollectionUtils.isEmpty(beneficiaryClientRefIds)) {
            beneficiaryClientRefIds = searchBeneficiaries(downsyncRequest, downsync, beneficiaryClientRefIds);
        }

        /* search tasks using beneficiary uuids */
        if (isSyncTimeAvailable || !CollectionUtils.isEmpty(beneficiaryClientRefIds)) {

            taskClientRefIds = searchTasks(downsyncRequest, downsync, beneficiaryClientRefIds, projectType);

            /* ref search */
            referralSearch(downsyncRequest, downsync, beneficiaryClientRefIds);
        }


        if (isSyncTimeAvailable || !CollectionUtils.isEmpty(taskClientRefIds)) {
            searchSideEffect(downsyncRequest, downsync, taskClientRefIds);
        }

        return downsync;
    }

    public DownsyncCLFHousehold prepareDownsyncCLFDataHousehold(DownsyncRequest downsyncRequest) {
        DownsyncCLFHousehold downsyncCLFHousehold = new DownsyncCLFHousehold();
        downsyncCLFHousehold.setDownsyncCriteria(downsyncRequest.getDownsyncCriteria());

        long startTime = System.currentTimeMillis();
        log.info("The household search start time : " + startTime);
        List<Household> householdList = searchHouseholdsCLF(downsyncRequest);

        Map<String, Integer> householdIdMemberCountMap = gethouseholdMemberCountMap(householdList.stream()
                .map(Household::getId).collect(Collectors.toList()));
        List<HouseholdMemberMap> householdMemberCountMap = new ArrayList<>();

       for (Household household : householdList) {
           Integer memberCount = householdIdMemberCountMap.get(household.getId()) == null ? 0 : householdIdMemberCountMap.get(household.getId());
           HouseholdMemberMap householdMemberMap = HouseholdMemberMap.builder().household(household).numberOfMembers(memberCount).build();
           householdMemberCountMap.add(householdMemberMap);
       }

        downsyncCLFHousehold.setHouseholdMemberCountMap(householdMemberCountMap);
        log.info("The search time : " + (System.currentTimeMillis() - startTime)/1000);
        return downsyncCLFHousehold;
    }

    /**
     *
     * @param downsyncRequest
     * @param downsync
     * @return
     */
    private List<String> searchHouseholds(DownsyncRequest downsyncRequest, Downsync downsync) {

        DownsyncCriteria criteria = downsyncRequest.getDownsyncCriteria();
        RequestInfo requestInfo = downsyncRequest.getRequestInfo();

        StringBuilder householdUrl = new StringBuilder(configs.getHouseholdHost())
                .append(configs.getHouseholdSearchUrl());
        householdUrl = 	appendUrlParams(householdUrl, criteria, null, null, true);

        HouseholdSearch householdSearch = HouseholdSearch.builder()
                .localityCode(criteria.getLocality())
                .build();

        if (StringUtils.hasLength(criteria.getHouseholdId())) {
            householdSearch.setId(Collections.singletonList(criteria.getHouseholdId()));
        }
        HouseholdSearchRequest searchRequest = HouseholdSearchRequest.builder()
                .household(householdSearch)
                .requestInfo(requestInfo)
                .build();

        HouseholdBulkResponse res = restClient.fetchResult(householdUrl, searchRequest, HouseholdBulkResponse.class);
        List<Household> households = res.getHouseholds();
        downsync.setHouseholds(households);
        downsync.getDownsyncCriteria().setTotalCount(res.getTotalCount());

        if(CollectionUtils.isEmpty(households))
            return Collections.emptyList();

        return households.stream().map(Household::getId).collect(Collectors.toList());
    }

    private List<Household> searchHouseholdsCLF(DownsyncRequest downsyncRequest) {

        DownsyncCriteria  criteria = downsyncRequest.getDownsyncCriteria();

        //HouseholdBulkResponse res = restClient.fetchResult(householdUrl, searchRequest, HouseholdBulkResponse.class);
        Tuple<Long, List<Household>> res = null;

        res = householdRepository.findByViewCLF(criteria.getLocality(), criteria.getLimit(), criteria.getOffset(), null, criteria.getLastSyncedTime() != null ? criteria.getLastSyncedTime() : 0L, criteria.getHouseholdId());

        downsyncRequest.getDownsyncCriteria().setTotalCount(res.getX());

        List<Household> households = res.getY();
        return households;
    }

    /**
     *
     * @param downsyncRequest
     * @param downsync
     * @param individualIds
     * @return individual ClientReferenceIds
     */
    private List<String> searchIndividuals(DownsyncRequest downsyncRequest, Downsync downsync,
                                           Set<String> individualIds) {

        DownsyncCriteria criteria = downsyncRequest.getDownsyncCriteria();
        RequestInfo requestInfo = downsyncRequest.getRequestInfo();

        List<Individual> individuals = new ArrayList<>();
        List<List<String>> subLists = splitList(individualIds.stream().toList(), SEARCH_MAX_COUNT);

        for (List<String> list : subLists) {
            StringBuilder url = new StringBuilder(configs.getIndividualHost())
                    .append(configs.getIndividualSearchUrl());
            url = appendUrlParams(url, criteria, 0, list.size(), true);

            IndividualSearch individualSearch = IndividualSearch.builder()
                    .build();

            if(!CollectionUtils.isEmpty(individualIds))
                individualSearch.setId(new ArrayList<>(individualIds));

            IndividualSearchRequest searchRequest = IndividualSearchRequest.builder()
                    .individual(individualSearch)
                    .requestInfo(requestInfo)
                    .build();

            IndividualBulkResponse res = restClient.fetchResult(url, searchRequest, IndividualBulkResponse.class);
            individuals.addAll(res.getIndividual());
        }
        downsync.setIndividuals(individuals);

        return individuals.stream().map(Individual::getClientReferenceId).collect(Collectors.toList());
    }

    /**
     *
     * @param downsyncRequest
     * @param householdIds
     * @return
     */
    private Set<String> searchMembers(DownsyncRequest downsyncRequest, Downsync downsync,
                                      List<String> householdIds) {

        Long lastChangedSince = downsyncRequest.getDownsyncCriteria().getLastSyncedTime();

        List<String> memberids = getPrimaryIds(householdIds, "householdId","HOUSEHOLD_MEMBER",
                lastChangedSince, downsyncRequest.getDownsyncCriteria().getLimit(), downsyncRequest.getDownsyncCriteria().getOffset());

        if (CollectionUtils.isEmpty(memberids))
            return Collections.emptySet();

        List<List<String>> subLists = splitList(memberids, SEARCH_MAX_COUNT);
        List<HouseholdMember> members = new ArrayList<>();
        for (List<String> list : subLists) {
            StringBuilder memberUrl = new StringBuilder(configs.getHouseholdHost())
                    .append(configs.getHouseholdMemberSearchUrl());
            appendUrlParams(memberUrl, downsyncRequest.getDownsyncCriteria(), 0, list.size(), false);
            HouseholdMemberSearch memberSearch = HouseholdMemberSearch.builder()
                    .id(list)
                    .build();

            HouseholdMemberSearchRequest searchRequest = HouseholdMemberSearchRequest.builder()
                    .householdMemberSearch(memberSearch)
                    .requestInfo(downsyncRequest.getRequestInfo())
                    .build();

            List<HouseholdMember> membersSublist = restClient.fetchResult(memberUrl, searchRequest, HouseholdMemberBulkResponse.class).getHouseholdMembers();
            members.addAll(membersSublist);
        }
        downsync.setHouseholdMembers(members);

        return members.stream().map(HouseholdMember::getIndividualId).collect(Collectors.toSet());
    }

    /**
     *
     * @param downsyncRequest
     * @param downsync
     * @param beneficiaryClientRefIds
     * @return clientreferenceid of beneficiary object
     */
    private List<String> searchBeneficiaries(DownsyncRequest downsyncRequest, Downsync downsync,
                                             List<String> beneficiaryClientRefIds) {

        DownsyncCriteria criteria = downsyncRequest.getDownsyncCriteria();
        RequestInfo requestInfo = downsyncRequest.getRequestInfo();
        Long lastChangedSince =criteria.getLastSyncedTime();

        List<String> beneficiaryIds = getPrimaryIds(
                beneficiaryClientRefIds,
                "beneficiaryclientreferenceid",
                "PROJECT_BENEFICIARY",
                lastChangedSince, null, null
        );

        if(CollectionUtils.isEmpty(beneficiaryIds))
            return Collections.emptyList();

        List<List<String>> subLists = splitList(beneficiaryIds, SEARCH_MAX_COUNT);
        List<ProjectBeneficiary> beneficiaries = new ArrayList<>();

        for (List<String> list : subLists) {
            StringBuilder url = new StringBuilder(configs.getProjectHost())
                    .append(configs.getProjectBeneficiarySearchUrl());
            url = appendUrlParams(url, criteria, 0, list.size(), false);

            ProjectBeneficiarySearch search = ProjectBeneficiarySearch.builder()
                    .id(list)
                    .projectId(Collections.singletonList(downsyncRequest.getDownsyncCriteria().getProjectId()))
                    .build();

            BeneficiarySearchRequest searchRequest = BeneficiarySearchRequest.builder()
                    .projectBeneficiary(search)
                    .requestInfo(requestInfo)
                    .build();

            List<ProjectBeneficiary> beneficiariesSubList = restClient.fetchResult(url, searchRequest, BeneficiaryBulkResponse.class).getProjectBeneficiaries();
            beneficiaries.addAll(beneficiariesSubList);
        }
        downsync.setProjectBeneficiaries(beneficiaries);

        return beneficiaries.stream().map(ProjectBeneficiary::getClientReferenceId).collect(Collectors.toList());
    }



    /**
     *
     * @param downsyncRequest
     * @param downsync
     * @param beneficiaryClientRefIds
     * @param projectType
     * @return
     */
    private List<String> searchTasks(DownsyncRequest downsyncRequest, Downsync downsync,
                                     List<String> beneficiaryClientRefIds, LinkedHashMap<String, Object> projectType) {

        DownsyncCriteria criteria = downsyncRequest.getDownsyncCriteria();
        RequestInfo requestInfo = downsyncRequest.getRequestInfo();
        List<String> taskIds = getPrimaryIds(beneficiaryClientRefIds, "projectBeneficiaryClientReferenceId", "PROJECT_TASK",
                criteria.getLastSyncedTime(), null, null);

        if(CollectionUtils.isEmpty(taskIds))
            return Collections.emptyList();

        List<List<String>> subLists = splitList(taskIds, SEARCH_MAX_COUNT);
        List<Task> tasks = new ArrayList<>();

        for (List<String> list : subLists) {
            StringBuilder url = new StringBuilder(configs.getProjectHost())
                    .append(configs.getProjectTaskSearchUrl());

            url = appendUrlParams(url, criteria, 0, list.size(), false);

            TaskSearch search = TaskSearch.builder()
                    .id(list)
                    .projectId(Collections.singletonList(downsyncRequest.getDownsyncCriteria().getProjectId()))
                    .build();

            TaskSearchRequest searchRequest = TaskSearchRequest.builder()
                    .task(search)
                    .requestInfo(requestInfo)
                    .build();
            List<Task> tasksSubList = restClient.fetchResult(url, searchRequest, TaskBulkResponse.class).getTasks();
            tasks.addAll(tasksSubList);
        }
        downsync.setTasks(tasks);

        return tasks.stream().map(Task::getClientReferenceId).collect(Collectors.toList());
    }

    /**
     *
     * @param downsyncRequest
     * @param downsync
     * @param taskClientRefIds
     */
    private void searchSideEffect(DownsyncRequest downsyncRequest, Downsync downsync,
                                  List<String> taskClientRefIds) {

        DownsyncCriteria criteria = downsyncRequest.getDownsyncCriteria();
        RequestInfo requestInfo = downsyncRequest.getRequestInfo();

        /* FIXME SHOULD BE REMOVED AND TASK SEARCH SHOULD BE enhanced with list of client-ref-beneficiary ids*/
        List<String> SEIds = getPrimaryIds(taskClientRefIds, "taskClientReferenceId",
                "SIDE_EFFECT", criteria.getLastSyncedTime(), null, null);

        if(CollectionUtils.isEmpty(SEIds))
            return;

        SideEffectSearch search = SideEffectSearch.builder()
                .id(SEIds)
                .build();

        SideEffectSearchRequest effectSearchRequest = SideEffectSearchRequest.builder()
                .sideEffect(search)
                .requestInfo(requestInfo)
                .build();

        List<SideEffect> effects = sideEffectService.search(
            effectSearchRequest,
            SEIds.size(),
            0,
            criteria.getTenantId(),
            criteria.getLastSyncedTime(),
            criteria.getIncludeDeleted()
        ).getResponse();

        downsync.setSideEffects(effects);
    }

    private void referralSearch(DownsyncRequest downsyncRequest, Downsync downsync,
                                List<String> beneficiaryClientRefIds) {

        DownsyncCriteria criteria = downsyncRequest.getDownsyncCriteria();
        RequestInfo requestInfo = downsyncRequest.getRequestInfo();
        Integer limit = beneficiaryClientRefIds.size();

        ReferralSearch search = ReferralSearch.builder()
                .build();

        if(!CollectionUtils.isEmpty(beneficiaryClientRefIds)) {
            search.setProjectBeneficiaryClientReferenceId(beneficiaryClientRefIds);
            limit = null;
        }

        ReferralSearchRequest searchRequest = ReferralSearchRequest.builder()
                .referral(search)
                .requestInfo(requestInfo)
                .build();

        List<Referral> referrals = referralService.search(
            searchRequest,
            limit,
            0,
            criteria.getTenantId(),
            criteria.getLastSyncedTime(),
            criteria.getIncludeDeleted()
        ).getResponse();

        downsync.setReferrals(referrals);
    }

    private Map<String, Integer> gethouseholdMemberCountMap(List<String> idList) {

        Map<String, Integer> memberCountMap = new HashMap<>();

        if (!CollectionUtils.isEmpty(idList)) {
            StringBuilder memberIdsquery = new StringBuilder("SELECT householdId, COUNT(*) AS memberCount " +
                    "FROM HOUSEHOLD_MEMBER WHERE householdId IN (:householdId) GROUP BY householdId");

            Map<String, Object> paramMap = new HashMap<>();
            paramMap.put("householdId", idList);
            List<Map<String, Object>> memberCountList = jdbcTemplate.queryForList(memberIdsquery.toString(), paramMap);
            return memberCountList.stream()
                    .collect(Collectors.toMap(
                            row -> (String) row.get("householdId"),
                            row -> ((Number) row.get("memberCount")).intValue()
                    ));

        }
        return memberCountMap;
    }


    /**
     * common method to fetch Ids with list of relation Ids like id of member with householdIds
     * @param idList
     * @param idListFieldName
     * @param tableName
     * @param lastChangedSince
     * @return
     */
    private List<String> getPrimaryIds(List<String> idList, String idListFieldName, String tableName,
                                       Long lastChangedSince, Integer limit, Integer offset) {

        /**
         * Adding lastShangedSince to id query to avoid load on API search for members
         */
        boolean isAndRequired = false;
        Map<String, Object> paramMap = new HashMap<>();
        StringBuilder memberIdsquery = new StringBuilder("SELECT id from %s WHERE ");


        if (!CollectionUtils.isEmpty(idList)) {

            memberIdsquery.append("%s IN (:%s)");
            paramMap.put(idListFieldName, idList);
            isAndRequired = true;
        }

        if (null != lastChangedSince) {
            if(isAndRequired)
                memberIdsquery.append(" AND ");
            memberIdsquery.append(" lastModifiedTime >= (:lastChangedSince)");
            paramMap.put("lastChangedSince", lastChangedSince);
        }

        if (tableName.equalsIgnoreCase("HOUSEHOLD_MEMBER")) {
            memberIdsquery.append(" ORDER BY lastModifiedTime ASC LIMIT :limit OFFSET :offset");
            paramMap.put("limit", limit);
            paramMap.put("offset", offset);
        }

        String finalQuery = String.format(memberIdsquery.toString(), tableName, idListFieldName, idListFieldName);
        /* FIXME SHOULD BE REMOVED AND SEARCH SHOULD BE enhanced with list of household ids*/
        List<String> memberids = jdbcTemplate.queryForList(finalQuery, paramMap, String.class);
        return memberids;
    }

    /**
     * append url params
     *
     * @param url
     * @param criteria
     * @param offset
     * @param limit
     * @param sendPrevSyncTime
     * @return
     */
    private StringBuilder appendUrlParams(StringBuilder url, DownsyncCriteria criteria, Integer offset, Integer limit, boolean sendPrevSyncTime) {

        url.append("?tenantId=")
                .append(criteria.getTenantId())
                .append("&includeDeleted=")
                .append(criteria.getIncludeDeleted())
                .append("&limit=");

        if (null != limit && limit != 0)
            url.append(limit);
        else
            url.append(criteria.getLimit());

        url.append("&offset=");

        if(null != offset)
            url.append(offset);
        else
            url.append(criteria.getOffset());

        if(sendPrevSyncTime && null != criteria.getLastSyncedTime())
            url.append("&lastChangedSince=").append(criteria.getLastSyncedTime());

        return url;
    }

    private <T> List<List<T>> splitList(List<T> list, int size) {
        List<List<T>> subLists = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            subLists.add(list.subList(i, Math.min(i + size, list.size())));
        }
        return subLists;
    }
}
