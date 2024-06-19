package org.egov.referralmanagement.service;

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
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class DownsyncService {
	
	private ServiceRequestClient restClient;
	
	private ReferralManagementConfiguration configs;
	
	private NamedParameterJdbcTemplate jdbcTemplate;
	
	private SideEffectService sideEffectService;
	
	private ReferralManagementService referralService;
	
	private HouseholdRepository householdRepository;

	private static final Integer SEARCH_MAX_COUNT = 1000;
	
	@Autowired
	public DownsyncService( ServiceRequestClient serviceRequestClient,
							ReferralManagementConfiguration referralManagementConfiguration,
							NamedParameterJdbcTemplate jdbcTemplate,
							SideEffectService sideEffectService,
							ReferralManagementService referralService,
							HouseholdRepository householdRepository) {

		this.restClient = serviceRequestClient;
		this.configs = referralManagementConfiguration;
		this.jdbcTemplate = jdbcTemplate;
		this.sideEffectService=sideEffectService;
		this.referralService=referralService;
		this.householdRepository = householdRepository;
		
		}

		/**
		 * 
		 * @param downsyncRequest
		 * @return Downsync
		 */
		public Downsync prepareDownsyncData(DownsyncRequest downsyncRequest) {

			Downsync downsync = new Downsync();

			List<Household> households = null;
			List<String> householdClientRefIds = null;
			List<String> individualClientRefIds = null;
			List<String> beneficiaryClientRefIds = null;
			List<String> householdBeneficiaryClientRefIds = null;
			List<String> taskClientRefIds = null;

			downsync.setDownsyncCriteria(downsyncRequest.getDownsyncCriteria());
			/* search household */
			households = searchHouseholds(downsyncRequest, downsync);
			householdClientRefIds = households.stream().map(Household::getClientReferenceId).collect(Collectors.toList());

			if (!CollectionUtils.isEmpty(householdClientRefIds))
				/* search household member using household ids */
				individualClientRefIds = searchMembers(downsyncRequest, downsync, householdClientRefIds);

			if (!CollectionUtils.isEmpty(individualClientRefIds)) {

				/* search individuals using individual ids */
				individualClientRefIds = searchIndividuals(downsyncRequest, downsync, individualClientRefIds);
			}

			if (!CollectionUtils.isEmpty(individualClientRefIds)) {
				/* search beneficiary using individual ids */
				beneficiaryClientRefIds = searchBeneficiaries(downsyncRequest, downsync, individualClientRefIds);
			}

			if (!CollectionUtils.isEmpty(householdClientRefIds)) {
				/* search beneficiary using household ids */
				householdBeneficiaryClientRefIds = searchBeneficiaries(downsyncRequest, downsync, householdClientRefIds);
				if (CollectionUtils.isEmpty(beneficiaryClientRefIds)) {
					beneficiaryClientRefIds = householdBeneficiaryClientRefIds;
				} else if(!CollectionUtils.isEmpty(householdBeneficiaryClientRefIds)) {
					beneficiaryClientRefIds.addAll(householdBeneficiaryClientRefIds);
				}
			}

			if (!CollectionUtils.isEmpty(beneficiaryClientRefIds)) {

				/* search tasks using beneficiary uuids */
				taskClientRefIds = searchTasks(downsyncRequest, downsync, beneficiaryClientRefIds);

				/* ref search */
				referralSearch(downsyncRequest, downsync, beneficiaryClientRefIds);
			}

			if (!CollectionUtils.isEmpty(taskClientRefIds)) {

				searchSideEffect(downsyncRequest, downsync, taskClientRefIds);
			}

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
			Tuple<Long, List<Household>> res = householdRepository.findByView(criteria.getLocality(), criteria.getLimit(), criteria.getOffset(), null);
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
				url = appendUrlParams(url, criteria, 0, list.size());

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
			
			String memberIdsquery = "SELECT id from HOUSEHOLD_MEMBER where householdClientReferenceId IN (:householdClientRefIds)";
			
			Map<String, Object> paramMap = new HashMap<>();
	        paramMap.put("householdClientRefIds", householdClientRefIds);

	        /* FIXME SHOULD BE REMOVED AND SEARCH SHOULD BE enhanced with list of household ids*/
	        List<String> memberids = jdbcTemplate.queryForList(memberIdsquery, paramMap, String.class);
			
			if (CollectionUtils.isEmpty(memberids))
				return Collections.emptyList();

			List<List<String>> subLists = splitList(memberids, SEARCH_MAX_COUNT);
			List<HouseholdMember> members = new ArrayList<>();
			for (List<String> list : subLists) {
				StringBuilder memberUrl = new StringBuilder(configs.getHouseholdHost())
						.append(configs.getHouseholdMemberSearchUrl());
				appendUrlParams(memberUrl, downsyncRequest.getDownsyncCriteria(), 0, list.size());

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
			
			String beneficiaryIdQuery = "SELECT id from PROJECT_BENEFICIARY where beneficiaryclientreferenceid IN (:beneficiaryIds)";
			
			Map<String, Object> paramMap = new HashMap<>();
	        paramMap.put("beneficiaryIds", individualClientRefIds);
	        
	        /* FIXME SHOULD BE REMOVED AND SEARCH SHOULD BE enhanced with list of beneficiary ids*/
	        List<String> ids = jdbcTemplate.queryForList(beneficiaryIdQuery, paramMap, String.class);
					
	        if(CollectionUtils.isEmpty(ids))
	        	return Collections.emptyList();

			List<List<String>> subLists = splitList(ids, SEARCH_MAX_COUNT);
			List<ProjectBeneficiary> beneficiaries = new ArrayList<>();

			for (List<String> list : subLists) {
				StringBuilder url = new StringBuilder(configs.getProjectHost())
						.append(configs.getProjectBeneficiarySearchUrl());
				url = appendUrlParams(url, criteria, 0, list.size());

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
				List<String> beneficiaryClientRefIds) {

			DownsyncCriteria criteria = downsyncRequest.getDownsyncCriteria();
			RequestInfo requestInfo = downsyncRequest.getRequestInfo();
			
			String taskIdQuery = "SELECT id from PROJECT_TASK where projectBeneficiaryClientReferenceId IN (:beneficiaryClientRefIds)";
			
			Map<String, Object> paramMap = new HashMap<>();
	        paramMap.put("beneficiaryClientRefIds", beneficiaryClientRefIds);
	        
	        /* FIXME SHOULD BE REMOVED AND TASK SEARCH SHOULD BE enhanced with list of client-ref-beneficiary ids*/
	        List<String> taskIds = jdbcTemplate.queryForList(taskIdQuery, paramMap, String.class);
	        
	        if(CollectionUtils.isEmpty(taskIds))
	        	return Collections.emptyList();

			List<List<String>> subLists = splitList(taskIds, SEARCH_MAX_COUNT);
			List<Task> tasks = new ArrayList<>();

			for (List<String> list : subLists) {
				StringBuilder url = new StringBuilder(configs.getProjectHost())
						.append(configs.getProjectTaskSearchUrl());
				url = appendUrlParams(url, criteria, 0, list.size());

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
		 * 
		 * @param downsyncRequest
		 * @param downsync
		 * @param taskClientRefIds
		 */
		private void searchSideEffect(DownsyncRequest downsyncRequest, Downsync downsync,
				List<String> taskClientRefIds) {

			DownsyncCriteria criteria = downsyncRequest.getDownsyncCriteria();
			RequestInfo requestInfo = downsyncRequest.getRequestInfo();

			// search side effect FIXME - tasks id array search not available
			String sEIdQuery = "SELECT id from SIDE_EFFECT where taskClientReferenceId IN (:taskClientRefIds)";
			
			Map<String, Object> paramMap = new HashMap<>();
	        paramMap.put("taskClientRefIds", taskClientRefIds);
	        
	        /* FIXME SHOULD BE REMOVED AND TASK SEARCH SHOULD BE enhanced with list of client-ref-beneficiary ids*/
	        List<String> SEIds = jdbcTemplate.queryForList(sEIdQuery, paramMap, String.class);
					
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
        		
			ReferralSearch search = ReferralSearch.builder()
			.projectBeneficiaryClientReferenceId(beneficiaryClientRefIds)
			.build();
	
			ReferralSearchRequest searchRequest = ReferralSearchRequest.builder()
					.referral(search)
					.requestInfo(requestInfo)
					.build();
	
			List<Referral> referrals = referralService.search(
								searchRequest,
								beneficiaryClientRefIds.size(),
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
	private StringBuilder appendUrlParams(StringBuilder url, DownsyncCriteria criteria, Integer offset, Integer limit) {
		
		url.append("?tenantId=")
			.append(criteria.getTenantId())
			.append("&includeDeleted=")
			.append(criteria.getIncludeDeleted())
			.append("&limit=");

		if (null != limit)
			url.append(limit);
		else
			url.append(criteria.getLimit());
			
		url.append("&offset=");
		
		if(null != offset) 
			url.append(offset);
		else 
			url.append(criteria.getOffset());
		
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
