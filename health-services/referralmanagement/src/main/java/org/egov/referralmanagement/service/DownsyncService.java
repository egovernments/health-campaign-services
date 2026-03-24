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
import org.egov.common.exception.InvalidTenantIdException;
import org.egov.common.http.client.ServiceRequestClient;
import org.egov.common.models.core.Pagination;
import org.egov.common.models.core.SearchResponse;
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
import org.egov.common.utils.MultiStateInstanceUtil;
import org.egov.referralmanagement.Constants;
import org.egov.referralmanagement.config.ReferralManagementConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import static org.egov.common.utils.MultiStateInstanceUtil.SCHEMA_REPLACE_STRING;
import static org.egov.referralmanagement.Constants.HOUSEHOLD;

@Service
@Slf4j
public class DownsyncService {

    private ServiceRequestClient restClient;

    private ReferralManagementConfiguration configs;

    private NamedParameterJdbcTemplate jdbcTemplate;

    private SideEffectService sideEffectService;

    private ReferralManagementService referralService;

    private MasterDataService masterDataService;

    private final MultiStateInstanceUtil multiStateInstanceUtil;

    @Autowired
    public DownsyncService( ServiceRequestClient serviceRequestClient,
                            ReferralManagementConfiguration referralManagementConfiguration,
                            NamedParameterJdbcTemplate jdbcTemplate,
                            SideEffectService sideEffectService,
                            ReferralManagementService referralService,
                            MasterDataService masterDataService, MultiStateInstanceUtil multiStateInstanceUtil) {

        this.restClient = serviceRequestClient;
        this.configs = referralManagementConfiguration;
        this.jdbcTemplate = jdbcTemplate;
        this.sideEffectService = sideEffectService;
        this.referralService = referralService;
        this.masterDataService = masterDataService;
        this.multiStateInstanceUtil = multiStateInstanceUtil;

    }

    /**
     *
     * @param downsyncRequest
     * @return Downsync
     */
    public Downsync prepareDownsyncData(DownsyncRequest downsyncRequest) throws InvalidTenantIdException {

        Downsync downsync = new Downsync();
        DownsyncCriteria downsyncCriteria = downsyncRequest.getDownsyncCriteria();

        List<String> individualClientRefIds = null;
        List<String> beneficiaryClientRefIds = null;
        List<String> taskClientRefIds = null;
        List<String> householdClientRefIds = null;


        downsync.setDownsyncCriteria(downsyncCriteria);
        boolean isSyncTimeAvailable = null != downsyncCriteria.getLastSyncedTime();

        //Project project = getProjectType(downsyncRequest);
        LinkedHashMap<String, Object> projectType = masterDataService.getProjectType(downsyncRequest);

        /* search household */
        householdClientRefIds = searchHouseholds(downsyncRequest, downsync);

        /* search household member using household client reference ids */
        if (isSyncTimeAvailable || !CollectionUtils.isEmpty(householdClientRefIds)) {
            individualClientRefIds = searchMembers(downsyncRequest, downsync, householdClientRefIds);
        }

        /* search individuals using individual client reference ids */
        if (isSyncTimeAvailable || !CollectionUtils.isEmpty(individualClientRefIds) ) {
            searchIndividuals(downsyncRequest, downsync, individualClientRefIds);
        }

        /* search beneficiary using individual ids OR household ids */

        String beneficiaryType = (String) projectType.get("beneficiaryType");

        beneficiaryClientRefIds = individualClientRefIds;

        if(HOUSEHOLD.equalsIgnoreCase(beneficiaryType))
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

        /* search service request services when enabled based on individual client reference ids
        *  and household client reference ids as reference ids for services */
        if (configs.getServiceRequestDownsyncEnabled()) {
            searchServices(downsyncRequest, downsync, individualClientRefIds, householdClientRefIds);
        }

        return downsync;
    }


    /**
     *
     * @param downsyncRequest
     * @param downsync
     * @return household client reference ids list
     */
    private List<String> searchHouseholds(DownsyncRequest downsyncRequest, Downsync downsync) {

        DownsyncCriteria criteria = downsyncRequest.getDownsyncCriteria();
        RequestInfo requestInfo = downsyncRequest.getRequestInfo();

        StringBuilder householdUrl = new StringBuilder(configs.getHouseholdHost())
                .append(configs.getHouseholdSearchUrl());
        appendUrlParams(householdUrl, criteria, null, null, true);

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

        return households.stream().map(Household::getClientReferenceId).collect(Collectors.toList());
    }

    /** Fetches individuals based on individual client reference ids and sets in downsync object
     *
     * @param downsyncRequest
     * @param downsync
     * @param individualClientRefIds individual client reference ids
     * @return individual ClientReferenceIds
     */
    private List<String> searchIndividuals(DownsyncRequest downsyncRequest, Downsync downsync,
                                           List<String> individualClientRefIds) throws InvalidTenantIdException {

        DownsyncCriteria criteria = downsyncRequest.getDownsyncCriteria();
        RequestInfo requestInfo = downsyncRequest.getRequestInfo();
        String tenantId = criteria.getTenantId();

        List<String> individualIds = getPrimaryIds(tenantId, individualClientRefIds, "clientReferenceId", "INDIVIDUAL", criteria.getLastSyncedTime());

        if (CollectionUtils.isEmpty(individualIds))
            return Collections.emptyList();

        /* builds url for individual search */
        StringBuilder url = new StringBuilder(configs.getIndividualHost())
                .append(configs.getIndividualSearchUrl());

        List<Individual> allIndividuals = new ArrayList<>();

        /* get batch size to fetch individuals from environment */
        int batchSize = configs.getIndividualSearchBatchSize();

        appendUrlParams(url, criteria, 0, batchSize, true);

        /* fetches the data in the batches of batch size */
        for (int i = 0; i < individualIds.size(); i += batchSize) {
            List<String> batch = getIdsForBatch(batchSize, i, individualIds);

            IndividualSearch individualSearch = IndividualSearch.builder()
                    .id(batch)
                    .build();

            IndividualSearchRequest searchRequest = IndividualSearchRequest.builder()
                    .individual(individualSearch)
                    .requestInfo(requestInfo)
                    .build();

            List<Individual> individuals = restClient.fetchResult(url, searchRequest, IndividualBulkResponse.class).getIndividual();
            allIndividuals.addAll(individuals);
        }
        downsync.setIndividuals(allIndividuals);

        return allIndividuals.stream().map(Individual::getClientReferenceId).collect(Collectors.toList());
    }

    /** Fetches service request services in batch of configured size based on household and individual
     * client reference ids and sets in downsync object
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

        /* builds url for service request service search */
        StringBuilder url = new StringBuilder(configs.getServiceRequestHost())
                .append(configs.getServiceRequestServiceSearchUrl());

        List<String> referenceIds = new ArrayList<>();
        if (!CollectionUtils.isEmpty(householdIds)) referenceIds.addAll(householdIds);
        if (!CollectionUtils.isEmpty(individualIds)) referenceIds.addAll(individualIds);

        List<org.egov.common.models.service.Service> allServices = new ArrayList<>();

        /* get batch size to fetch services from environment */
        int batchSize = configs.getServiceRequestSearchBatchSize();

        /* fetches the data in the batches of batch size */
        for (int i = 0; i < referenceIds.size(); i += batchSize) {
            List<String> batch = getIdsForBatch(batchSize, i, referenceIds);

            ServiceCriteria serviceCriteria = ServiceCriteria.builder()
                    .tenantId(criteria.getTenantId())
                    .referenceIds(batch)
                    .build();

            Pagination pagination = Pagination.builder()
                    .offset(0)
                    .limit(batch.size())
                    .build();

            ServiceSearchRequest searchRequest = ServiceSearchRequest.builder()
                    .serviceCriteria(serviceCriteria)
                    .pagination(pagination)
                    .requestInfo(requestInfo)
                    .build();

            List<org.egov.common.models.service.Service> services =
                    restClient.fetchResult(url, searchRequest, ServiceResponse.class).getServices();

            if (services != null) {
                allServices.addAll(services);
            }
        }

        downsync.setServices(allServices);
    }

    /** Searches for household members of the given household client reference ids
     * updating in the downsync object
     *
     * @param downsyncRequest
     * @param householdClientReferenceIds
     * @return household member's individual client reference ids list
     */
    private List<String> searchMembers(DownsyncRequest downsyncRequest, Downsync downsync,
                                                      List<String> householdClientReferenceIds) throws InvalidTenantIdException {

        Long lastChangedSince = downsyncRequest.getDownsyncCriteria().getLastSyncedTime();
        String tenantId = downsyncRequest.getDownsyncCriteria().getTenantId();

        List<String> memberIds = getPrimaryIds(tenantId, householdClientReferenceIds, "householdClientReferenceId","HOUSEHOLD_MEMBER",lastChangedSince);

        if (CollectionUtils.isEmpty(memberIds))
            return Collections.emptyList();

        /* builds url for household member search */
        StringBuilder memberUrl = new StringBuilder(configs.getHouseholdHost())
                .append(configs.getHouseholdMemberSearchUrl());

        List<HouseholdMember> allMembers = new ArrayList<>();

        /* get batch size to fetch household members from environment */
        int batchSize = configs.getHouseholdMemberSearchBatchSize();

        appendUrlParams(memberUrl, downsyncRequest.getDownsyncCriteria(), 0, batchSize, false);

        /* fetches the data in the batches of batch size */
        for (int i = 0; i < memberIds.size(); i += batchSize) {
            List<String> batch = getIdsForBatch(batchSize, i, memberIds);

            HouseholdMemberSearch memberSearch = HouseholdMemberSearch.builder()
                    .id(batch)
                    .build();

            HouseholdMemberSearchRequest searchRequest = HouseholdMemberSearchRequest.builder()
                    .householdMemberSearch(memberSearch)
                    .requestInfo(downsyncRequest.getRequestInfo())
                    .build();

            List<HouseholdMember> members = restClient.fetchResult(memberUrl, searchRequest, HouseholdMemberBulkResponse.class).getHouseholdMembers();
            allMembers.addAll(members);
        }
        downsync.setHouseholdMembers(allMembers);

        return allMembers.stream().map(HouseholdMember::getIndividualClientReferenceId).collect(Collectors.toList());
    }

    /**
     *
     * @param downsyncRequest
     * @param downsync
     * @param beneficiaryClientRefIds
     * @return clientreferenceid of beneficiary object
     */
    private List<String> searchBeneficiaries(DownsyncRequest downsyncRequest, Downsync downsync,
                                             List<String> beneficiaryClientRefIds) throws InvalidTenantIdException {

        DownsyncCriteria criteria = downsyncRequest.getDownsyncCriteria();
        RequestInfo requestInfo = downsyncRequest.getRequestInfo();
        Long lastChangedSince =criteria.getLastSyncedTime();
        String tenantId = criteria.getTenantId();

        List<String> beneficiaryIds = getPrimaryIds(
                tenantId,
                beneficiaryClientRefIds,
                "beneficiaryclientreferenceid",
                "PROJECT_BENEFICIARY",
                lastChangedSince
        );

        if(CollectionUtils.isEmpty(beneficiaryIds))
            return Collections.emptyList();

        StringBuilder url = new StringBuilder(configs.getProjectHost())
                .append(configs.getProjectBeneficiarySearchUrl());

        List<ProjectBeneficiary> allBeneficiaries = new ArrayList<>();

        /* get batch size to fetch project beneficiaries from environment */
        int batchSize = configs.getProjectBeneficiarySearchBatchSize();

        appendUrlParams(url, criteria, 0, batchSize, false);

        /* fetches the data in the batches of batch size */
        for (int i = 0; i < beneficiaryIds.size(); i += batchSize) {
            List<String> batch = getIdsForBatch(batchSize, i, beneficiaryIds);

            ProjectBeneficiarySearch search = ProjectBeneficiarySearch.builder()
                    .id(batch)
                    .projectId(Collections.singletonList(downsyncRequest.getDownsyncCriteria().getProjectId()))
                    .build();

            BeneficiarySearchRequest searchRequest = BeneficiarySearchRequest.builder()
                    .projectBeneficiary(search)
                    .requestInfo(requestInfo)
                    .build();

            List<ProjectBeneficiary> beneficiaries = restClient.fetchResult(url, searchRequest, BeneficiaryBulkResponse.class).getProjectBeneficiaries();
            allBeneficiaries.addAll(beneficiaries);
        }
        downsync.setProjectBeneficiaries(allBeneficiaries);

        return allBeneficiaries.stream().map(ProjectBeneficiary::getClientReferenceId).collect(Collectors.toList());
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
                                     List<String> beneficiaryClientRefIds, LinkedHashMap<String, Object> projectType) throws InvalidTenantIdException {

        DownsyncCriteria criteria = downsyncRequest.getDownsyncCriteria();
        RequestInfo requestInfo = downsyncRequest.getRequestInfo();
        String tenantId = criteria.getTenantId();

        List<String> taskIds = getPrimaryIds(tenantId, beneficiaryClientRefIds, "projectBeneficiaryClientReferenceId", "PROJECT_TASK",
                criteria.getLastSyncedTime());

        if(CollectionUtils.isEmpty(taskIds))
            return Collections.emptyList();

        StringBuilder url = new StringBuilder(configs.getProjectHost())
                 .append(configs.getProjectTaskSearchUrl());

        List<Task> allTasks = new ArrayList<>();

        /* get batch size to fetch project tasks from environment */
        int batchSize = configs.getProjectTaskSearchBatchSize();

        appendUrlParams(url, criteria, 0, batchSize, false);

        /* fetches the data in the batches of batch size */
        for (int i = 0; i < taskIds.size(); i += batchSize) {
            List<String> batch = getIdsForBatch(batchSize, i, taskIds);

            TaskSearch search = TaskSearch.builder()
                    .id(batch)
                    .projectId(Collections.singletonList(downsyncRequest.getDownsyncCriteria().getProjectId()))
                    .build();

            TaskSearchRequest searchRequest = TaskSearchRequest.builder()
                    .task(search)
                    .requestInfo(requestInfo)
                    .build();

            List<Task> tasks = restClient.fetchResult(url, searchRequest, TaskBulkResponse.class).getTasks();
            allTasks.addAll(tasks);
        }
        downsync.setTasks(allTasks);

        return allTasks.stream().map(Task::getClientReferenceId).collect(Collectors.toList());
    }

    /**
     *
     * @param downsyncRequest
     * @param downsync
     * @param taskClientRefIds
     */
    private void searchSideEffect(DownsyncRequest downsyncRequest, Downsync downsync,
                                  List<String> taskClientRefIds) throws InvalidTenantIdException {

        DownsyncCriteria criteria = downsyncRequest.getDownsyncCriteria();
        RequestInfo requestInfo = downsyncRequest.getRequestInfo();
        String tenantId = criteria.getTenantId();

        /* FIXME SHOULD BE REMOVED AND TASK SEARCH SHOULD BE enhanced with list of client-ref-beneficiary ids*/
        List<String> SEIds = getPrimaryIds(tenantId, taskClientRefIds, "taskClientReferenceId", "SIDE_EFFECT", criteria.getLastSyncedTime());

        if(CollectionUtils.isEmpty(SEIds))
            return;

        List<SideEffect> allSideEffects = new ArrayList<>();

        /* get batch size to fetch side effects from environment */
        int batchSize = configs.getSideEffectSearchBatchSize();

        /* fetches the data in the batches of batch size */
        for (int i = 0; i < SEIds.size(); i += batchSize) {
            List<String> batch = getIdsForBatch(batchSize, i, SEIds);

            SideEffectSearch search = SideEffectSearch.builder()
                    .id(batch)
                    .build();

            SideEffectSearchRequest effectSearchRequest = SideEffectSearchRequest.builder()
                    .sideEffect(search)
                    .requestInfo(requestInfo)
                    .build();

            List<SideEffect> effects = sideEffectService.search(
                    effectSearchRequest,
                    batchSize,
                    0,
                    criteria.getTenantId(),
                    criteria.getLastSyncedTime(),
                    criteria.getIncludeDeleted()
            ).getResponse();
            allSideEffects.addAll(effects);
        }

        downsync.setSideEffects(allSideEffects);
    }

    private void referralSearch(DownsyncRequest downsyncRequest, Downsync downsync,
                                List<String> beneficiaryClientRefIds) throws InvalidTenantIdException {

        DownsyncCriteria criteria = downsyncRequest.getDownsyncCriteria();
        RequestInfo requestInfo = downsyncRequest.getRequestInfo();

        if(CollectionUtils.isEmpty(beneficiaryClientRefIds)) {
            return;
        }

        List<Referral> allReferrals = new ArrayList<>();

        /* get batch size to fetch project tasks from environment */
        int batchSize = configs.getReferralSearchBatchSize();

        int fetched = 0;
        Long totalCount;

        do {
            ReferralSearch search = ReferralSearch.builder()
                    .projectBeneficiaryClientReferenceId(beneficiaryClientRefIds)
                    .build();

            ReferralSearchRequest searchRequest = ReferralSearchRequest.builder()
                    .referral(search)
                    .requestInfo(requestInfo)
                    .build();

            SearchResponse<Referral> searchResponse = referralService.search(
                    searchRequest,
                    batchSize,
                    fetched,
                    criteria.getTenantId(),
                    criteria.getLastSyncedTime(),
                    criteria.getIncludeDeleted()
            );

            totalCount = searchResponse.getTotalCount();
            List<Referral> referrals = searchResponse.getResponse();
            allReferrals.addAll(referrals);

            fetched += batchSize;
        } while (fetched < totalCount);

        downsync.setReferrals(allReferrals);
    }


    /**
     * common method to fetch Ids with list of relation Ids like id of member with householdIds
     * @param idList
     * @param idListFieldName
     * @param tableName
     * @param lastChangedSince
     * @return
     */
    private List<String> getPrimaryIds(String tenantId, List<String> idList, String idListFieldName, String tableName, Long lastChangedSince) throws InvalidTenantIdException {

        /**
         * Adding lastShangedSince to id query to avoid load on API search for members
         */
        boolean isAndRequired = false;
        Map<String, Object> paramMap = new HashMap<>();

        if (CollectionUtils.isEmpty(idList))
            return Collections.emptyList();

        StringBuilder memberIdsquery = new StringBuilder("SELECT id from %s.%s WHERE ");


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

        String finalQuery = String.format(memberIdsquery.toString(), SCHEMA_REPLACE_STRING, tableName, idListFieldName, idListFieldName);
        finalQuery = multiStateInstanceUtil.replaceSchemaPlaceholder(finalQuery, tenantId);
        List<String> memberids = jdbcTemplate.queryForList(finalQuery, paramMap, String.class);
        return memberids;
    }

    private List<String> getIdsForBatch(int batchSize, int offset, List<String> idList) {
        return idList.subList(offset, Math.min(offset + batchSize, idList.size()));
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
