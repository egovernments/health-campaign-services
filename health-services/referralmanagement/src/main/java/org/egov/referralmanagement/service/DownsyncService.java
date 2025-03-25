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
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.http.client.ServiceRequestClient;
import org.egov.common.models.core.Pagination;
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
import org.egov.common.models.referralmanagement.beneficiarydownsync.Downsync;
import org.egov.common.models.referralmanagement.beneficiarydownsync.DownsyncCriteria;
import org.egov.common.models.referralmanagement.beneficiarydownsync.DownsyncRequest;
import org.egov.common.models.referralmanagement.sideeffect.SideEffect;
import org.egov.common.models.referralmanagement.sideeffect.SideEffectSearch;
import org.egov.common.models.referralmanagement.sideeffect.SideEffectSearchRequest;
import org.egov.common.models.service.ServiceCriteria;
import org.egov.common.models.service.ServiceResponse;
import org.egov.common.models.service.ServiceSearchRequest;
import org.egov.referralmanagement.config.ReferralManagementConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

@Service
@Slf4j
public class DownsyncService {

    private ServiceRequestClient restClient;

    private ReferralManagementConfiguration configs;

    private NamedParameterJdbcTemplate jdbcTemplate;

    private SideEffectService sideEffectService;

    private ReferralManagementService referralService;

    private MasterDataService masterDataService;

    @Autowired
    public DownsyncService( ServiceRequestClient serviceRequestClient,
                            ReferralManagementConfiguration referralManagementConfiguration,
                            NamedParameterJdbcTemplate jdbcTemplate,
                            SideEffectService sideEffectService,
                            ReferralManagementService referralService,
                            MasterDataService masterDataService ) {

        this.restClient = serviceRequestClient;
        this.configs = referralManagementConfiguration;
        this.jdbcTemplate = jdbcTemplate;
        this.sideEffectService=sideEffectService;
        this.referralService=referralService;
        this.masterDataService=masterDataService;

    }

    /**
     *
     * @param downsyncRequest
     * @return Downsync
     */
    public Downsync prepareDownsyncData(DownsyncRequest downsyncRequest) {

        Downsync downsync = new Downsync();
        DownsyncCriteria downsyncCriteria = downsyncRequest.getDownsyncCriteria();

        List<String> householdIds = null;
        Set<String> individualIds = null;
        List<String> individualClientRefIds = null;
        List<String> beneficiaryClientRefIds = null;
        List<String> taskClientRefIds = null;
        List<String> householdClientRefIds = null;


        downsync.setDownsyncCriteria(downsyncCriteria);
        boolean isSyncTimeAvailable = null != downsyncCriteria.getLastSyncedTime();

        //Project project = getProjectType(downsyncRequest);
        LinkedHashMap<String, Object> projectType = masterDataService.getProjectType(downsyncRequest);

        /* search household */
        householdIds = searchHouseholds(downsyncRequest, downsync);
        householdClientRefIds = downsync.getHouseholds().stream().map(Household::getClientReferenceId).collect(Collectors.toList());

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

        if("HOUSEHOLD".equalsIgnoreCase(beneficiaryType))
            beneficiaryClientRefIds = householdClientRefIds;

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

        searchServices(downsyncRequest, downsync, individualClientRefIds, householdClientRefIds);

        return downsync;
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

        StringBuilder url = new StringBuilder(configs.getIndividualHost())
                .append(configs.getIndividualSearchUrl());

        url = appendUrlParams(url, criteria, 0, individualIds.size(),true);

        IndividualSearch individualSearch = IndividualSearch.builder()
                .build();

        if(!CollectionUtils.isEmpty(individualIds))
            individualSearch.setId(new ArrayList<>(individualIds));

        IndividualSearchRequest searchRequest = IndividualSearchRequest.builder()
                .individual(individualSearch)
                .requestInfo(requestInfo)
                .build();

        List<Individual> individuals = restClient.fetchResult(url, searchRequest, IndividualBulkResponse.class).getIndividual();
        downsync.setIndividuals(individuals);

        return individuals.stream().map(Individual::getClientReferenceId).collect(Collectors.toList());
    }

    /**
     *
     * @param downsyncRequest
     * @param downsync
     * @param individualIds individual client reference ids
     * @param householdIds household client reference ids
     */
    private void searchServices(DownsyncRequest downsyncRequest, Downsync downsync,
                                           List<String> individualIds, List<String> householdIds) {
        if (CollectionUtils.isEmpty(householdIds) && CollectionUtils.isEmpty(individualIds)) {
            return;
        }

        DownsyncCriteria criteria = downsyncRequest.getDownsyncCriteria();
        RequestInfo requestInfo = downsyncRequest.getRequestInfo();

        StringBuilder url = new StringBuilder(configs.getServiceRequestHost())
                .append(configs.getServiceRequestServiceSearchUrl());

        List<String> referenceIds = new ArrayList<>();
        if (!CollectionUtils.isEmpty(householdIds)) referenceIds.addAll(householdIds);
        if (!CollectionUtils.isEmpty(individualIds)) referenceIds.addAll(individualIds);

        ServiceCriteria serviceCriteria = ServiceCriteria.builder()
                .tenantId(criteria.getTenantId())
                .referenceIds(referenceIds)
                .build();

        Pagination pagination = Pagination.builder()
                .offset(0)
                .limit(referenceIds.size())
                .build();

        ServiceSearchRequest searchRequest = ServiceSearchRequest.builder()
                .serviceCriteria(serviceCriteria)
                .pagination(pagination)
                .requestInfo(requestInfo)
                .build();

        List<org.egov.common.models.service.Service> services
                = restClient.fetchResult(url, searchRequest, ServiceResponse.class).getService();

        downsync.setServices(services);
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

        List<String> memberids = getPrimaryIds(householdIds, "householdId","HOUSEHOLD_MEMBER",lastChangedSince);

        if (CollectionUtils.isEmpty(memberids))
            return Collections.emptySet();

        StringBuilder memberUrl = new StringBuilder(configs.getHouseholdHost())
                .append(configs.getHouseholdMemberSearchUrl());

        appendUrlParams(memberUrl, downsyncRequest.getDownsyncCriteria(), 0, householdIds.size(), false);

        HouseholdMemberSearch memberSearch = HouseholdMemberSearch.builder()
                .id(memberids)
                .build();

        HouseholdMemberSearchRequest searchRequest = HouseholdMemberSearchRequest.builder()
                .householdMemberSearch(memberSearch)
                .requestInfo(downsyncRequest.getRequestInfo())
                .build();

        List<HouseholdMember> members = restClient.fetchResult(memberUrl, searchRequest, HouseholdMemberBulkResponse.class).getHouseholdMembers();
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
                lastChangedSince
        );

        if(CollectionUtils.isEmpty(beneficiaryIds))
            return Collections.emptyList();

        StringBuilder url = new StringBuilder(configs.getProjectHost())
                .append(configs.getProjectBeneficiarySearchUrl());

        url = appendUrlParams(url, criteria, 0, beneficiaryClientRefIds.size(),false);

        ProjectBeneficiarySearch search = ProjectBeneficiarySearch.builder()
                .id(beneficiaryIds)
                .projectId(Collections.singletonList(downsyncRequest.getDownsyncCriteria().getProjectId()))
                .build();

        BeneficiarySearchRequest searchRequest = BeneficiarySearchRequest.builder()
                .projectBeneficiary(search)
                .requestInfo(requestInfo)
                .build();

        List<ProjectBeneficiary> beneficiaries = restClient.fetchResult(url, searchRequest, BeneficiaryBulkResponse.class).getProjectBeneficiaries();
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
                criteria.getLastSyncedTime());

        if(CollectionUtils.isEmpty(taskIds))
            return Collections.emptyList();

        StringBuilder url = new StringBuilder(configs.getProjectHost())
                 .append(configs.getProjectTaskSearchUrl());

        url = appendUrlParams(url, criteria, 0, taskIds.size(), false);

        TaskSearch search = TaskSearch.builder()
                .id(taskIds)
                .projectId(Collections.singletonList(downsyncRequest.getDownsyncCriteria().getProjectId()))
                .build();

        TaskSearchRequest searchRequest = TaskSearchRequest.builder()
                .task(search)
                .requestInfo(requestInfo)
                .build();

        List<Task> tasks = restClient.fetchResult(url, searchRequest, TaskBulkResponse.class).getTasks();
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
}
