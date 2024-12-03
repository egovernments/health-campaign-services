package org.egov.referralmanagement.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.ds.Tuple;
import org.egov.common.http.client.ServiceRequestClient;
import org.egov.common.models.household.Household;
import org.egov.common.models.household.HouseholdMember;
import org.egov.common.models.household.HouseholdMemberBulkResponse;
import org.egov.common.models.household.HouseholdMemberSearch;
import org.egov.common.models.household.HouseholdMemberSearchRequest;
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
import org.egov.referralmanagement.config.ReferralManagementConfiguration;
import org.egov.referralmanagement.repository.HouseholdRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class DownsyncService {
	
	private ServiceRequestClient restClient;
	
	private ReferralManagementConfiguration configs;
	
	private NamedParameterJdbcTemplate jdbcTemplate;
	
	private SideEffectService sideEffectService;
	
	private ReferralManagementService referralService;
	
	private HouseholdRepository householdRepository;

	private MasterDataService masterDataService;
	
	@Autowired
	RedisTemplate<String, Object> redisTemplate;

	private static final Integer SEARCH_MAX_COUNT = 1000;
	
	@Autowired
	public DownsyncService( ServiceRequestClient serviceRequestClient,
							ReferralManagementConfiguration referralManagementConfiguration,
							NamedParameterJdbcTemplate jdbcTemplate,
							SideEffectService sideEffectService,
							ReferralManagementService referralService,
							HouseholdRepository householdRepository,
							MasterDataService masterDataService) {

			this.restClient = serviceRequestClient;
			this.configs = referralManagementConfiguration;
			this.jdbcTemplate = jdbcTemplate;
			this.sideEffectService = sideEffectService;
			this.referralService = referralService;
			this.householdRepository = householdRepository;
			this.masterDataService = masterDataService;
		}

	/**
	 * @param downsyncRequest downsync request with isCommmunity flag true and optional householdId
	 * The function returns communal living household ids if isCommunity flag is true and householdId is null
	 * It returns all the household data for a particular household if id is passed; within given offset and limit
	 */
	public Downsync downsyncForCFL(DownsyncRequest downsyncRequest) {
		Downsync downsync = Downsync.builder().downsyncCriteria(downsyncRequest.getDownsyncCriteria()).build();
		DownsyncCriteria downsyncCriteria = downsyncRequest.getDownsyncCriteria();

		downsyncCriteria.setLastSyncedTime(null);

		String key = downsyncCriteria.getLocality() + downsyncCriteria.getOffset() + downsyncCriteria.getLimit() +
				downsyncCriteria.getIsCommunity() + downsyncCriteria.getHouseholdId();

		Object obj = getFromCache(key);
		if (null != obj) {
			return (Downsync) obj;
		}

		boolean isSyncTimeAvalable = null != downsyncCriteria.getLastSyncedTime();

		Long requestStartTime = System.currentTimeMillis();
		Long startTime = System.currentTimeMillis();

		// if household id is null then search for all the household and return the household ids
		if (downsyncCriteria.getHouseholdId() == null) {
			List<Household> households = null;
			log.info("The household search start time : " + startTime);
			households = searchHouseholds(downsyncRequest, downsync);
			log.info("The household call time : " + (System.currentTimeMillis()-startTime)/1000);
			downsync.setHouseholds(households);
			return  downsync;
		}

		List<Household> households = null;
		downsyncRequest.getDownsyncCriteria().getHouseholdId();
		List<String> householdClientRefIds = null;
		List<String> individualClientRefIds = null;
		List<String> beneficiaryClientRefIds = null;
		List<String> householdBeneficiaryClientRefIds = null;
		List<String> taskClientRefIds = null;

		// Fetch projectType from mdms
		log.info("The masterDataService start time : " + startTime);
		LinkedHashMap<String, Object> projectType = masterDataService.getProjectType(downsyncRequest);
		log.info("The masterDataService call time : " + (System.currentTimeMillis()-startTime)/1000);

		// Fetch households from household ids
		log.info("The household search start time : " + startTime);
		households = searchHouseholds(downsyncRequest, downsync);
		householdClientRefIds = households.stream().map(Household::getClientReferenceId).collect(Collectors.toList());
		log.info("The household call time : " + (System.currentTimeMillis()-startTime)/1000);
		downsync.setHouseholds(households);

		startTime = System.currentTimeMillis();
		log.info("The members start time : " + startTime);
		if (isSyncTimeAvalable || !CollectionUtils.isEmpty(householdClientRefIds))
			/* search household member using household ids */
			individualClientRefIds = searchMembers(downsyncRequest, downsync, householdClientRefIds);
		log.info("The members call time : " + (System.currentTimeMillis()-startTime)/1000);

		startTime = System.currentTimeMillis();
		log.info("The individualas start time : " + startTime);
		if (isSyncTimeAvalable || !CollectionUtils.isEmpty(individualClientRefIds)) {
			/* search individuals using individual ids */
			individualClientRefIds = searchIndividuals(downsyncRequest, downsync, individualClientRefIds);
		}
		log.info("The individual call time : " + (System.currentTimeMillis()-startTime)/1000);

		startTime = System.currentTimeMillis();
		log.info("The beneficiary start time : " + startTime);
		if (isSyncTimeAvalable || !CollectionUtils.isEmpty(individualClientRefIds)) {
			/* search beneficiary using individual ids */
			beneficiaryClientRefIds = searchBeneficiaries(downsyncRequest, downsync, individualClientRefIds);
		}
		log.info("The beneficiary call time : " + (System.currentTimeMillis()-startTime)/1000);


		startTime = System.currentTimeMillis();
		log.info("The task ref start time : " + startTime);
		if (isSyncTimeAvalable || !CollectionUtils.isEmpty(beneficiaryClientRefIds)) {

			/* search tasks using beneficiary uuids */
			taskClientRefIds = searchTasks(downsyncRequest, downsync, beneficiaryClientRefIds, projectType);

			/* ref search */
			referralSearch(downsyncRequest, downsync, beneficiaryClientRefIds);
		}
		log.info("The task ref call time : " + (System.currentTimeMillis()-startTime)/1000);

		startTime = System.currentTimeMillis();
		log.info("The side effect start time : " + startTime);
		if (isSyncTimeAvalable || !CollectionUtils.isEmpty(taskClientRefIds)) {
			/* search sideeffects using taskClientRefIds */
			searchSideEffect(downsyncRequest, downsync, taskClientRefIds);
		}
		log.info("The side effect call time : " + (System.currentTimeMillis()-startTime)/1000);

		log.info("The total call time -- : " + (System.currentTimeMillis()-requestStartTime)/1000);
		log.info("The end total call time -- : " + System.currentTimeMillis());

		cacheByKey(downsync, key);

		return downsync;
	}

		/**
		 * 
		 * @param downsyncRequest
		 * @return Downsync
		 */
		public Downsync prepareDownsyncData(DownsyncRequest downsyncRequest) {

			Downsync downsync = new Downsync();
			DownsyncCriteria downsyncCriteria = downsyncRequest.getDownsyncCriteria();
			/* FIXME SHOULD BE REMOVED for enabling lastsynced time issue*/
			downsyncCriteria.setLastSyncedTime(null);
			
			String key = downsyncCriteria.getLocality() + downsyncCriteria.getOffset() + downsyncCriteria.getLimit();

			Object obj = getFromCache(key);
			if (null != obj) {
				return (Downsync) obj;
			}

			List<Household> households = null;
			List<String> householdClientRefIds = null;
			List<String> individualClientRefIds = null;
			List<String> beneficiaryClientRefIds = null;
			List<String> householdBeneficiaryClientRefIds = null;
			List<String> taskClientRefIds = null;

			downsync.setDownsyncCriteria(downsyncCriteria);
			boolean isSyncTimeAvalable = null != downsyncCriteria.getLastSyncedTime();
			
			Long startTime0 = System.currentTimeMillis(); 
			Long startTime = System.currentTimeMillis(); 
			log.info("The masterDataService start time : " + startTime);
			
			LinkedHashMap<String, Object> projectType = masterDataService.getProjectType(downsyncRequest);

			log.info("The masterDataService call time : " + (System.currentTimeMillis()-startTime)/1000);
			
			/* search household */
			startTime = System.currentTimeMillis(); 
			log.info("The Household start time : " + startTime);
			households = searchHouseholds(downsyncRequest, downsync);
			householdClientRefIds = households.stream().map(Household::getClientReferenceId).collect(Collectors.toList());
			log.info("The household call time : " + (System.currentTimeMillis()-startTime)/1000);

			startTime = System.currentTimeMillis(); 
			log.info("The members start time : " + startTime);
			if (isSyncTimeAvalable || !CollectionUtils.isEmpty(householdClientRefIds))
				/* search household member using household ids */
				individualClientRefIds = searchMembers(downsyncRequest, downsync, householdClientRefIds);
			log.info("The members call time : " + (System.currentTimeMillis()-startTime)/1000);

			startTime = System.currentTimeMillis(); 
			log.info("The individualas start time : " + startTime);
			if (isSyncTimeAvalable || !CollectionUtils.isEmpty(individualClientRefIds)) {

				/* search individuals using individual ids */
				individualClientRefIds = searchIndividuals(downsyncRequest, downsync, individualClientRefIds);
			}
			log.info("The individual call time : " + (System.currentTimeMillis()-startTime)/1000);

			startTime = System.currentTimeMillis(); 
			log.info("The beneficiary start time : " + startTime);
			if (isSyncTimeAvalable || !CollectionUtils.isEmpty(individualClientRefIds)) {
				/* search beneficiary using individual ids */
				beneficiaryClientRefIds = searchBeneficiaries(downsyncRequest, downsync, individualClientRefIds);
			}
			log.info("The beneficiary call time : " + (System.currentTimeMillis()-startTime)/1000);


			startTime = System.currentTimeMillis(); 
			log.info("The task ref start time : " + startTime);
			if (isSyncTimeAvalable || !CollectionUtils.isEmpty(beneficiaryClientRefIds)) {

				/* search tasks using beneficiary uuids */
				taskClientRefIds = searchTasks(downsyncRequest, downsync, beneficiaryClientRefIds, projectType);

				/* ref search */
				referralSearch(downsyncRequest, downsync, beneficiaryClientRefIds);
			}
			log.info("The task ref call time : " + (System.currentTimeMillis()-startTime)/1000);

			startTime = System.currentTimeMillis(); 
			log.info("The side effect start time : " + startTime);
			if (isSyncTimeAvalable || !CollectionUtils.isEmpty(taskClientRefIds)) {

				searchSideEffect(downsyncRequest, downsync, taskClientRefIds);
			}
			log.info("The side effect call time : " + (System.currentTimeMillis()-startTime)/1000);
			
			log.info("The total call time -- : " + (System.currentTimeMillis()-startTime0)/1000);
			log.info("The end total call time -- : " + System.currentTimeMillis());
			
			cacheByKey(downsync, key);
			
			return downsync;
		}

		/**
		 * 
		 * @param downsyncRequest
		 * @param downsync
		 * @return
		 */
		private List<Household> searchHouseholds(DownsyncRequest downsyncRequest, Downsync downsync) {

			DownsyncCriteria  criteria = downsyncRequest.getDownsyncCriteria();
			
			//HouseholdBulkResponse res = restClient.fetchResult(householdUrl, searchRequest, HouseholdBulkResponse.class);
			Tuple<Long, List<Household>> res = null;
			if (downsyncRequest.getDownsyncCriteria().getIsCommunity()) {
				res = householdRepository.findByViewCFL(criteria.getLocality(), criteria.getLimit(), criteria.getOffset(), null, criteria.getLastSyncedTime() != null ? criteria.getLastSyncedTime() : 0L, criteria.getHouseholdId());
			} else {
				res = householdRepository.findByView(criteria.getLocality(), criteria.getLimit(), criteria.getOffset(), null, criteria.getLastSyncedTime() != null ? criteria.getLastSyncedTime() : 0L);
			}

			List<Household> households = res.getY();
			downsync.setHouseholds(households);
			downsync.getDownsyncCriteria().setTotalCount(res.getX());
					
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
			List<List<String>> subLists = splitList(individualClientRefIds, SEARCH_MAX_COUNT);

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
		 * @param householdIds
		 * @return
		 */
		private List<String> searchMembers(DownsyncRequest downsyncRequest, Downsync downsync,
				List<String> householdClientRefIds) {

			Long lastChangedSince = downsyncRequest.getDownsyncCriteria().getLastSyncedTime();

			List<String> memberids = getPrimaryIds(householdClientRefIds, "householdClientReferenceId","HOUSEHOLD_MEMBER",lastChangedSince);

			if (CollectionUtils.isEmpty(memberids))
				return Collections.emptyList();


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
	        
			return members.stream().map(HouseholdMember::getIndividualClientReferenceId).collect(Collectors.toList());
		}


		/**
		 * 
		 * @param downsyncRequest
		 * @param downsync
		 * @param individualClientRefIds
		 * @return clientreferenceid of beneficiary object
		 */
		private List<String> searchBeneficiaries(DownsyncRequest downsyncRequest, Downsync downsync,
				List<String> individualClientRefIds) {

			DownsyncCriteria criteria = downsyncRequest.getDownsyncCriteria();
			RequestInfo requestInfo = downsyncRequest.getRequestInfo();
			Long lastChangedSince =criteria.getLastSyncedTime();

			/* FIXME SHOULD BE REMOVED AND SEARCH SHOULD BE enhanced with list of beneficiary ids*/
			List<String> ids = getPrimaryIds(individualClientRefIds, "beneficiaryclientreferenceid","PROJECT_BENEFICIARY",lastChangedSince);

	        if(CollectionUtils.isEmpty(ids))
	        	return Collections.emptyList();

			List<List<String>> subLists = splitList(ids, SEARCH_MAX_COUNT);
			List<ProjectBeneficiary> beneficiaries = new ArrayList<>();

			for (List<String> list : subLists) {
				StringBuilder url = new StringBuilder(configs.getProjectHost())
						.append(configs.getProjectBeneficiarySearchUrl());
				url = appendUrlParams(url, criteria, 0, list.size(), false);

				ProjectBeneficiarySearch search = ProjectBeneficiarySearch.builder()
						.id(list)
						.projectId(downsyncRequest.getDownsyncCriteria().getProjectId())
						.build();

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
				List<String> beneficiaryClientRefIds, LinkedHashMap<String, Object> projectType) {

			DownsyncCriteria criteria = downsyncRequest.getDownsyncCriteria();
			RequestInfo requestInfo = downsyncRequest.getRequestInfo();

			List<String> taskIds;
			List<Integer> cycleIndicesForTaskDownload = masterDataService.getCycleIndicesForTask(projectType);
			if (!CollectionUtils.isEmpty(cycleIndicesForTaskDownload))
				taskIds = getPrimaryIds(beneficiaryClientRefIds, "projectBeneficiaryClientReferenceId", "PROJECT_TASK_CYCLE_INDEX_MATERIALIZED_VIEW",
						criteria.getLastSyncedTime(), cycleIndicesForTaskDownload);
			else
				taskIds = getPrimaryIds(beneficiaryClientRefIds, "projectBeneficiaryClientReferenceId", "PROJECT_TASK",
						criteria.getLastSyncedTime());

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
						.projectId(downsyncRequest.getDownsyncCriteria().getProjectId())
						.build();

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
		 * common method to fetch Ids with list of relation Ids like id of member with householdClientRefIds
		 * @param idList
		 * @param idListFeildName
		 * @param tableName
		 * @param lastChangedSince
		 * @param paramMap
		 * @return
		 */
		private List<String> getPrimaryIds(List<String> idList, String idListFieldName,String tableName, Long lastChangedSince) {

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
		 * common method to fetch Ids with list of relation Ids like id of member with householdClientRefIds
		 * @param idList
		 * @param idListFeildName
		 * @param tableName
		 * @param lastChangedSince
		 * @param paramMap
		 * @return
		 */
		private List<String> getPrimaryIds(List<String> idList, String idListFieldName,String tableName, Long lastChangedSince, List<Integer> cycleIndices) {

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
				isAndRequired = true;
				memberIdsquery.append(" lastModifiedTime >= (:lastChangedSince)");
				paramMap.put("lastChangedSince", lastChangedSince);
			}

			if(!CollectionUtils.isEmpty(cycleIndices)) {
				if(isAndRequired)
					memberIdsquery.append(" AND ");
				memberIdsquery.append(" cycleindex IN (:cycleIndices)");
				paramMap.put("cycleIndices", cycleIndices);
			}

			String finalQuery = String.format(memberIdsquery.toString(), tableName, idListFieldName, idListFieldName);
			/* FIXME SHOULD BE REMOVED AND SEARCH SHOULD BE enhanced with list of household ids*/
			List<String> memberids = jdbcTemplate.queryForList(finalQuery, paramMap, String.class);
			return memberids;
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
								criteria.getIncludeDeleted());
			
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
								criteria.getIncludeDeleted());
	
			downsync.setReferrals(referrals);
		}



	/**
	 * append url params
	 *
	 * @param url
	 * @param criteria
	 * @param includeLimitOffset
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
