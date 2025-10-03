package org.egov.referralmanagement.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.ds.Tuple;
import org.egov.common.http.client.ServiceRequestClient;
import org.egov.common.models.household.*;
import org.egov.common.models.individual.Individual;
import org.egov.common.models.individual.IndividualBulkResponse;
import org.egov.common.models.individual.IndividualSearch;
import org.egov.common.models.individual.IndividualSearchRequest;
import org.egov.common.models.project.*;
import org.egov.common.models.referralmanagement.Referral;
import org.egov.common.models.referralmanagement.ReferralSearch;
import org.egov.common.models.referralmanagement.ReferralSearchRequest;
import org.egov.common.models.referralmanagement.beneficiarydownsync.Downsync;
import org.egov.common.models.referralmanagement.beneficiarydownsync.DownsyncCriteria;
import org.egov.common.models.referralmanagement.beneficiarydownsync.DownsyncRequest;
import org.egov.common.models.referralmanagement.sideeffect.SideEffect;
import org.egov.common.models.referralmanagement.sideeffect.SideEffectSearch;
import org.egov.common.models.referralmanagement.sideeffect.SideEffectSearchRequest;
import org.egov.referralmanagement.config.ReferralManagementConfiguration;
import org.egov.referralmanagement.repository.HouseholdRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

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
    RedisTemplate<String, Object> redisTemplate;

    @Autowired
    public DownsyncService( ServiceRequestClient serviceRequestClient,
                            ReferralManagementConfiguration referralManagementConfiguration,
                            NamedParameterJdbcTemplate jdbcTemplate,
                            SideEffectService sideEffectService,
                            ReferralManagementService referralService,
                            MasterDataService masterDataService,
                            HouseholdRepository householdRepository) {

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
    public Downsync prepareDownsyncData(DownsyncRequest downsyncRequest, boolean useProjectId) {

        Downsync downsync = new Downsync();
        DownsyncCriteria downsyncCriteria = downsyncRequest.getDownsyncCriteria();
        downsyncCriteria.setLastSyncedTime(null);
        String syncedTimeKey = Optional.ofNullable(downsyncCriteria.getLastSyncedTime()).orElse(0L).toString();
        long startTime = System.currentTimeMillis();

        String key = downsyncCriteria.getLocality() + downsyncCriteria.getOffset() + downsyncCriteria.getLimit() + syncedTimeKey;

        Object obj = getFromCache(key);
        if (null != obj) {
            return (Downsync) obj;
        }

        List<Household> households = null;
        List<String> householdClientRefIds = new ArrayList<>();
        List<String> individualClientRefIds = new ArrayList<>();
        List<String> beneficiaryClientRefIds = new ArrayList<>();
        List<String> taskClientRefIds = new ArrayList<>();


        downsync.setDownsyncCriteria(downsyncCriteria);
        // removing incremental downsync for matview flow
        if (configs.isEnableMatviewSearch()) {
            downsyncCriteria.setLastSyncedTime(null);
        }
        boolean isSyncTimeAvailable = null != downsyncCriteria.getLastSyncedTime();

        long t0 = System.currentTimeMillis();

        //Project project = getProjectType(downsyncRequest);
        // TODO: Remove this if campaign type is not individual
//        String beneficiaryType = masterDataService.getProjectType(downsyncRequest);
        String beneficiaryType = "INDIVIDUAL";
        long t1 = System.currentTimeMillis();

        householdClientRefIds = getHouseholdIds(downsyncCriteria.getLocality(), downsyncCriteria.getOffset(), downsyncCriteria.getLimit());

        /* search household */
        households = searchHouseholds(downsyncRequest, downsync, householdClientRefIds);
//        householdClientRefIds = households.stream().map(Household::getClientReferenceId).collect(Collectors.toList());

        long t2 = System.currentTimeMillis();

        if (!CollectionUtils.isEmpty(householdClientRefIds))
            /* search household member using household client ref ids */
            individualClientRefIds = searchMembers(downsyncRequest, downsync, householdClientRefIds);

        long t3 = System.currentTimeMillis();

        /* search individuals using individual ids */
        if (isSyncTimeAvailable || !CollectionUtils.isEmpty(individualClientRefIds) ) {
            individualClientRefIds = searchIndividuals(downsyncRequest, downsync, individualClientRefIds);
        }

        long t4 = System.currentTimeMillis();

        /* search beneficiary using individual ids OR household ids */

        beneficiaryClientRefIds = individualClientRefIds;

        if("HOUSEHOLD".equalsIgnoreCase(beneficiaryType))
            beneficiaryClientRefIds = downsync.getHouseholds().stream().map(Household::getClientReferenceId).collect(Collectors.toList());

        long t5 = System.currentTimeMillis();

        //fetch beneficiary in the db
        if (isSyncTimeAvailable || !CollectionUtils.isEmpty(beneficiaryClientRefIds)) {
            beneficiaryClientRefIds = searchBeneficiaries(downsyncRequest, downsync, beneficiaryClientRefIds, useProjectId);
        }

        long t6 = System.currentTimeMillis();

        long t7 = System.currentTimeMillis();
        long t8 = System.currentTimeMillis();

        /* search tasks using beneficiary uuids */
        if (isSyncTimeAvailable || !CollectionUtils.isEmpty(beneficiaryClientRefIds)) {

            taskClientRefIds = searchTasks(downsyncRequest, downsync, beneficiaryClientRefIds, useProjectId);

            t7 = System.currentTimeMillis();

            /* ref search */
            referralSearch(downsyncRequest, downsync, beneficiaryClientRefIds);

            t8 = System.currentTimeMillis();
        }


        if (isSyncTimeAvailable || !CollectionUtils.isEmpty(taskClientRefIds)) {
            searchSideEffect(downsyncRequest, downsync, taskClientRefIds);
        }

        long t9 = System.currentTimeMillis();

        cacheByKey(downsync, key);

        long t10 = System.currentTimeMillis();

        log.info("Downsync process finished in {} ms, {} ms, {} ms, {} ms, {} ms, {} ms, {} ms, {} ms, {} ms, {} ms, {} ms",
                startTime - t0, t1 - t0, t2 - t1,  t3 - t2, t4 - t3, t5 - t4, t6 - t5, t7 - t6,  t8 - t7, t9 - t8, t10 - t9);


        return downsync;
    }


    /**
     *
     * @param downsyncRequest
     * @param downsync
     * @return
     */
    private List<Household> searchHouseholds(DownsyncRequest downsyncRequest, Downsync downsync, List<String> householdClientRefIds) {

        DownsyncCriteria criteria = downsyncRequest.getDownsyncCriteria();
        List<Household> households = null;

        if (configs.isEnableMatviewSearch()) {
            Tuple<Long, List<Household>> res = householdRepository.findByView(criteria.getLocality(), criteria.getLimit(), criteria.getOffset(), null);
            households = res.getY();
            downsync.getDownsyncCriteria().setTotalCount(res.getX());
        } else {
            RequestInfo requestInfo = downsyncRequest.getRequestInfo();

            StringBuilder householdUrl = new StringBuilder(configs.getHouseholdHost())
                    .append(configs.getHouseholdSearchUrl());
            householdUrl = appendUrlParams(householdUrl, criteria, null, null, true);

            HouseholdSearch householdSearch = HouseholdSearch.builder()
                    .clientReferenceId(householdClientRefIds)
                    .build();

            HouseholdSearchRequest searchRequest = HouseholdSearchRequest.builder()
                    .household(householdSearch)
                    .requestInfo(requestInfo)
                    .build();

            HouseholdBulkResponse res = restClient.fetchResult(householdUrl, searchRequest, HouseholdBulkResponse.class);
            households = res.getHouseholds();
            downsync.getDownsyncCriteria().setTotalCount(res.getTotalCount());
        }
        downsync.setHouseholds(households);

        if(CollectionUtils.isEmpty(households))
            return Collections.emptyList();

        return households;
    }

    /**
     *
     * @param downsyncRequest
     * @param downsync
     * @param individualClientRefIds
     * @return individual ClientReferenceIds
     */
    private List<String> searchIndividuals(DownsyncRequest downsyncRequest, Downsync downsync,
                                           List<String> individualClientRefIds) {

        DownsyncCriteria criteria = downsyncRequest.getDownsyncCriteria();
        RequestInfo requestInfo = downsyncRequest.getRequestInfo();

        List<Individual> individuals = new ArrayList<>();
        if(CollectionUtils.isEmpty(individualClientRefIds)) return  Collections.emptyList();

        Integer searchLimit = configs.getDownsyncSearchLimits().getOrDefault("individual", SEARCH_MAX_COUNT);
        List<List<String>> subLists = splitList(individualClientRefIds, searchLimit);

        for (List<String> list : subLists) {
            StringBuilder url = new StringBuilder(configs.getIndividualHost())
                    .append(configs.getIndividualSearchUrl());

            url = appendUrlParams(url, criteria, 0, list.size(), true);

            IndividualSearch individualSearch = IndividualSearch.builder()
                    .clientReferenceId(list)
                    .build();

            IndividualSearchRequest searchRequest = IndividualSearchRequest.builder()
                    .individual(individualSearch)
                    .requestInfo(requestInfo)
                    .build();

            List<Individual> individualsSublist = restClient.fetchResult(url, searchRequest, IndividualBulkResponse.class).getIndividual();
            individuals.addAll(individualsSublist);
        }

        downsync.setIndividuals(individuals);

        return individuals.stream().map(Individual::getClientReferenceId).collect(Collectors.toList());
    }

    /**
     *
     * @param downsyncRequest
     * @param householdClientRefIds
     * @return
     */
    private List<String> searchMembers(DownsyncRequest downsyncRequest, Downsync downsync,
                                      List<String> householdClientRefIds) {

        Long lastChangedSince = downsyncRequest.getDownsyncCriteria().getLastSyncedTime();

        List<String> memberids = getPrimaryIds(householdClientRefIds, "householdClientReferenceId","HOUSEHOLD_MEMBER",lastChangedSince);

        if (CollectionUtils.isEmpty(memberids))
            return Collections.emptyList();

        Integer searchLimit = configs.getDownsyncSearchLimits().getOrDefault("member", SEARCH_MAX_COUNT);
        List<List<String>> subLists = splitList(memberids, searchLimit);
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

        return members.stream().map(HouseholdMember::getIndividualClientReferenceId).collect(Collectors.toList());
    }

    /**
     *
     * @param downsyncRequest
     * @param downsync
     * @param beneficiaryClientRefIds
     * @return clientreferenceid of beneficiary object
     */
    private List<String> searchBeneficiaries(DownsyncRequest downsyncRequest, Downsync downsync,
                                             List<String> beneficiaryClientRefIds, boolean useProjectId) {

        DownsyncCriteria criteria = downsyncRequest.getDownsyncCriteria();
        RequestInfo requestInfo = downsyncRequest.getRequestInfo();
        Long lastChangedSince =criteria.getLastSyncedTime();

        List<String> beneficiaryIds = getPrimaryIds(
                beneficiaryClientRefIds,
                "beneficiaryclientreferenceid",
                "PROJECT_BENEFICIARY",
                lastChangedSince
        );

        if(CollectionUtils.isEmpty(beneficiaryIds))
            return Collections.emptyList();

        Integer searchLimit = configs.getDownsyncSearchLimits().getOrDefault("beneficiary", SEARCH_MAX_COUNT);
        List<List<String>> subLists = splitList(beneficiaryIds, searchLimit);
        List<ProjectBeneficiary> beneficiaries = new ArrayList<>();

        for (List<String> list : subLists) {
            StringBuilder url = new StringBuilder(configs.getProjectHost())
                    .append(configs.getProjectBeneficiarySearchUrl());

            url = appendUrlParams(url, criteria, 0, list.size(),false);

            ProjectBeneficiarySearch search = ProjectBeneficiarySearch.builder()
                    .id(list).build();

            if (useProjectId) {
                search.setProjectId(Collections.singletonList(downsyncRequest.getDownsyncCriteria().getProjectId()));
            }

            BeneficiarySearchRequest searchRequest = BeneficiarySearchRequest.builder()
                    .projectBeneficiary(search)
                    .requestInfo(requestInfo)
                    .build();


            List<ProjectBeneficiary> beneficiariesSublist = restClient.fetchResult(url, searchRequest, BeneficiaryBulkResponse.class).getProjectBeneficiaries();

            beneficiaries.addAll(beneficiariesSublist);
        }


        downsync.setProjectBeneficiaries(beneficiaries);

        return beneficiaries.stream().map(ProjectBeneficiary::getClientReferenceId).collect(Collectors.toList());
    }



    /**
     *
     * @param downsyncRequest
     * @param downsync
     * @param beneficiaryClientRefIds
     * @return
     */
    private List<String> searchTasks(DownsyncRequest downsyncRequest, Downsync downsync,
                                     List<String> beneficiaryClientRefIds, boolean useProjectId) {

        DownsyncCriteria criteria = downsyncRequest.getDownsyncCriteria();
        RequestInfo requestInfo = downsyncRequest.getRequestInfo();
        List<String> taskIds = getPrimaryIds(beneficiaryClientRefIds, "projectBeneficiaryClientReferenceId", "PROJECT_TASK",
                criteria.getLastSyncedTime());

        if(CollectionUtils.isEmpty(taskIds))
            return Collections.emptyList();

        Integer searchLimit = configs.getDownsyncSearchLimits().getOrDefault("task", SEARCH_MAX_COUNT);
        List<List<String>> subLists = splitList(taskIds, searchLimit);
        List<Task> tasks = new ArrayList<>();

        for (List<String> list : subLists) {
            StringBuilder url = new StringBuilder(configs.getProjectHost())
                     .append(configs.getProjectTaskSearchUrl());

            url = appendUrlParams(url, criteria, 0, list.size(), false);

            TaskSearch search = TaskSearch.builder()
                    .id(list)
                    .build();

            if(useProjectId) {
                search.setProjectId(
                        Collections.singletonList(downsyncRequest.getDownsyncCriteria().getProjectId()));
            }

            TaskSearchRequest searchRequest = TaskSearchRequest.builder()
                .task(search)
                .requestInfo(requestInfo)
                .build();

            List<Task> tasksSublist = restClient.fetchResult(url, searchRequest, TaskBulkResponse.class).getTasks();
            tasks.addAll(tasksSublist);
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
        List<String> SEIds = getPrimaryIds(taskClientRefIds, "taskClientReferenceId", "SIDE_EFFECT", criteria.getLastSyncedTime());

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


    /**
     * common method to fetch Ids with list of relation Ids like id of member with householdIds
     * @param idList
     * @param idListFieldName
     * @param tableName
     * @param lastChangedSince
     * @return
     */
    private List<String> getPrimaryIds(List<String> idList, String idListFieldName, String tableName, Long lastChangedSince) {

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

        String finalQuery = String.format(memberIdsquery.toString(), tableName, idListFieldName, idListFieldName);
        /* FIXME SHOULD BE REMOVED AND SEARCH SHOULD BE enhanced with list of household ids*/
        List<String> memberids = jdbcTemplate.queryForList(finalQuery, paramMap, String.class);
        return memberids;
    }

    private List<String> getHouseholdIds(String localityCode, Integer limit, Integer offset) {
        String query = "select distinct hhm.householdclientreferenceid " +
                "from household_member hhm " +
                "join project_beneficiary pb " +
                "  on pb.beneficiaryclientreferenceid = hhm.individualclientreferenceid " +
                "join project_task pt " +
                "  on pt.projectbeneficiaryclientreferenceid = pb.clientreferenceid " +
                "join address a " +
                "  on pt.addressid = a.id " +
                "where pt.status in ('BENEFICIARY_ABSENT', 'ADMINISTRATION_FAILED') " +
                "  and a.localitycode = :localityCode " +
                "order by hhm.householdclientreferenceid  " +
                "limit :limit " +
                "offset :offset";
        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("localityCode", localityCode);
        paramMap.put("limit", limit);
        paramMap.put("offset", offset);
        return jdbcTemplate.queryForList(query, paramMap, String.class);
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
        if(CollectionUtils.isEmpty(list)) return subLists;
        for (int i = 0; i < list.size(); i += size) {
            subLists.add(list.subList(i, Math.min(i + size, list.size())));
        }
        return subLists;
    }

    private void cacheByKey(Downsync downsync, String key) {
        try {
            redisTemplate.opsForValue().set(key, downsync);
            redisTemplate.expire(key, 600l, TimeUnit.SECONDS);
        } catch (Exception exception) {
            log.warn("Error while saving to cache: {}", ExceptionUtils.getStackTrace(exception));
        }
    }

    private Object getFromCache(String key) {

        Object res = null;

        try {
            res = redisTemplate.opsForValue().get(key);
        } catch (Exception exception) {
            log.warn("Error while retrieving from cache: {}", ExceptionUtils.getStackTrace(exception));
        }
        return res;
    }

}
