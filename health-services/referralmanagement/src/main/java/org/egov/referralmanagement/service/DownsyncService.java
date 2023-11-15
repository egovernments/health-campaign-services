package org.egov.referralmanagement.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.egov.common.contract.request.RequestInfo;
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
import org.egov.common.models.referralmanagement.beneficiarydownsync.Downsync;
import org.egov.common.models.referralmanagement.beneficiarydownsync.DownsyncCriteria;
import org.egov.common.models.referralmanagement.beneficiarydownsync.DownsyncRequest;
import org.egov.common.models.referralmanagement.sideeffect.SideEffect;
import org.egov.common.models.referralmanagement.sideeffect.SideEffectSearch;
import org.egov.common.models.referralmanagement.sideeffect.SideEffectSearchRequest;
import org.egov.referralmanagement.config.ReferralManagementConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

@Service
public class DownsyncService {
	
	private ServiceRequestClient restClient;
	
	private ReferralManagementConfiguration configs;
	
	private NamedParameterJdbcTemplate jdbcTemplate;
	
	private SideEffectService sideEffectService;
	
	private ReferralManagementService referralService;
	
	@Autowired
	public DownsyncService( ServiceRequestClient serviceRequestClient,
							ReferralManagementConfiguration referralManagementConfiguration,
							NamedParameterJdbcTemplate jdbcTemplate,
							SideEffectService sideEffectService,
							ReferralManagementService referralService) {

		this.restClient = serviceRequestClient;
		this.configs = referralManagementConfiguration;
		this.jdbcTemplate = jdbcTemplate;
		this.sideEffectService=sideEffectService;
		this.referralService=referralService;
		
		}

		/**
		 * 
		 * @param downsyncRequest
		 * @return Downsync
		 */
		public Downsync prepareDownsyncData(DownsyncRequest downsyncRequest) {

			Downsync downsync = new Downsync();

			List<String> householdIds = null;
			Set<String> individualIds = null;
			List<String> individualClientRefIds = null;
			List<String> beneficiaryClientRefIds = null;
			List<String> taskClientRefIds = null;

			downsync.setDownsyncCriteria(downsyncRequest.getDownsyncCriteria());
			/* search household */
			householdIds = searchHouseholds(downsyncRequest, downsync);

			if (!CollectionUtils.isEmpty(householdIds))
				/* search household member using household ids */
				individualIds = searchMembers(downsyncRequest, downsync, householdIds);

			if (!CollectionUtils.isEmpty(individualIds)) {

				/* search individuals using individual ids */
				individualClientRefIds = searchIndividuals(downsyncRequest, downsync, individualIds);
			}

			if (!CollectionUtils.isEmpty(individualClientRefIds)) {
				/* search beneficiary using individual ids */
				beneficiaryClientRefIds = searchBeneficiaries(downsyncRequest, downsync, individualClientRefIds);
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
		private List<String> searchHouseholds(DownsyncRequest downsyncRequest, Downsync downsync) {

			DownsyncCriteria criteria = downsyncRequest.getDownsyncCriteria();
			RequestInfo requestInfo = downsyncRequest.getRequestInfo();
	
			StringBuilder householdUrl = new StringBuilder(configs.getHouseholdHost())
					.append(configs.getHouseholdSearchUrl());
			householdUrl = 	appendUrlParams(householdUrl, criteria, null, null);
					
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
			url = appendUrlParams(url, criteria, 0, individualIds.size());
					
			IndividualSearch individualSearch = IndividualSearch.builder()
					.id(new ArrayList<>(individualIds))
					.build();
			
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
		 * @param householdIds
		 * @return
		 */
		private Set<String> searchMembers(DownsyncRequest downsyncRequest, Downsync downsync,
				List<String> householdIds) {

			StringBuilder memberUrl = new StringBuilder(configs.getHouseholdHost())
					.append(configs.getHouseholdMemberSearchUrl());
			
			String memberIdsquery = "SELECT id from HOUSEHOLD_MEMBER where householdId IN (:householdIds)";
			
			Map<String, Object> paramMap = new HashMap<>();
	        paramMap.put("householdIds", householdIds);
			appendUrlParams(memberUrl, downsyncRequest.getDownsyncCriteria(), 0, householdIds.size());

	        /* FIXME SHOULD BE REMOVED AND SEARCH SHOULD BE enhanced with list of household ids*/
	        List<String> memberids = jdbcTemplate.queryForList(memberIdsquery, paramMap, String.class);
			
			if (CollectionUtils.isEmpty(memberids))
				return Collections.emptySet();
			
	
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
		 * @param individualClientRefIds
		 * @return clientreferenceid of beneficiary object
		 */
		private List<String> searchBeneficiaries(DownsyncRequest downsyncRequest, Downsync downsync,
				List<String> individualClientRefIds) {

			DownsyncCriteria criteria = downsyncRequest.getDownsyncCriteria();
			RequestInfo requestInfo = downsyncRequest.getRequestInfo();

			StringBuilder url = new StringBuilder(configs.getProjectHost())
					.append(configs.getProjectBeneficiarySearchUrl());
			url = appendUrlParams(url, criteria, 0, individualClientRefIds.size());
			
			String beneficiaryIdQuery = "SELECT id from PROJECT_BENEFICIARY where beneficiaryclientreferenceid IN (:beneficiaryIds)";
			
			Map<String, Object> paramMap = new HashMap<>();
	        paramMap.put("beneficiaryIds", individualClientRefIds);
	        
	        /* FIXME SHOULD BE REMOVED AND SEARCH SHOULD BE enhanced with list of beneficiary ids*/
	        List<String> ids = jdbcTemplate.queryForList(beneficiaryIdQuery, paramMap, String.class);
					
	        if(CollectionUtils.isEmpty(ids))
	        	return Collections.emptyList();
	        		
	        ProjectBeneficiarySearch search = ProjectBeneficiarySearch.builder()
					.id(ids)
					.projectId(downsyncRequest.getDownsyncCriteria().getProjectId())
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
		 * @return
		 */
		private List<String> searchTasks(DownsyncRequest downsyncRequest, Downsync downsync,
				List<String> beneficiaryClientRefIds) {

			DownsyncCriteria criteria = downsyncRequest.getDownsyncCriteria();
			RequestInfo requestInfo = downsyncRequest.getRequestInfo();
	
			StringBuilder url = new StringBuilder(configs.getProjectHost())
					.append(configs.getProjectTaskSearchUrl());
			
			String taskIdQuery = "SELECT id from PROJECT_TASK where projectBeneficiaryClientReferenceId IN (:beneficiaryClientRefIds)";
			
			Map<String, Object> paramMap = new HashMap<>();
	        paramMap.put("beneficiaryClientRefIds", beneficiaryClientRefIds);
	        
	        /* FIXME SHOULD BE REMOVED AND TASK SEARCH SHOULD BE enhanced with list of client-ref-beneficiary ids*/
	        List<String> taskIds = jdbcTemplate.queryForList(taskIdQuery, paramMap, String.class);
	        url = appendUrlParams(url, criteria, 0, taskIds.size());
	        
	        if(CollectionUtils.isEmpty(taskIds))
	        	return Collections.emptyList();
	        		
	        TaskSearch search = TaskSearch.builder()
					.id(taskIds)
					.projectId(downsyncRequest.getDownsyncCriteria().getProjectId())
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
}
