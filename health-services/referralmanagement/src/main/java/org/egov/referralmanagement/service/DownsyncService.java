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
import org.egov.common.models.referralmanagement.Referral;
import org.egov.common.models.referralmanagement.beneficiarydownsync.Downsync;
import org.egov.common.models.referralmanagement.beneficiarydownsync.DownsyncCriteria;
import org.egov.common.models.referralmanagement.beneficiarydownsync.DownsyncRequest;
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
	
	@Autowired
	public DownsyncService(ServiceRequestClient serviceRequestClient,
			ReferralManagementConfiguration referralManagementConfiguration,
			NamedParameterJdbcTemplate jdbcTemplate) {

		this.restClient = serviceRequestClient;
		this.configs = referralManagementConfiguration;
		this.jdbcTemplate = jdbcTemplate;
	}
	
	public Downsync prepareDownsyncData(DownsyncRequest downsyncRequest) {
		
		
		Downsync downsync = new Downsync();
		
		List<String> householdIds = null;
		Set<String> individualIds = null;
		List<String> beneficiaryUuids = null;
		List<String> taskIds = null;
		
		downsync.setDownsyncCriteria(downsyncRequest.getDownsyncCriteria());
		/* search household */
		 householdIds = searchHouseholds(downsyncRequest, downsync);
		
		/* search household member using household ids */
		individualIds = searchMembers(downsyncRequest, downsync, householdIds);
		
		
		if(!CollectionUtils.isEmpty(individualIds)) {
			
			/* search individuals using individual ids */
			searchIndividuals(downsyncRequest, downsync, individualIds);
			
			/* search beneficiary using individual ids */
			beneficiaryUuids = searchBeneficiaries(downsyncRequest, downsync, individualIds);
		}
		
		/* search tasks using  benegiciary uuids */
		if (!CollectionUtils.isEmpty(beneficiaryUuids)) {

			List<Task> tasks = new ArrayList<>();
			taskIds = tasks.stream().map(Task::getId).collect(Collectors.toList());
		}
		
		// search side effect FIXME - tasks id array search not available
		
		// referral search - project beneficiary id
		List<Referral> referrals = null;
		
		
		
		return downsync;
	}


	private List<String> searchHouseholds(DownsyncRequest downsyncRequest, Downsync downsync) {
		
		DownsyncCriteria criteria = downsyncRequest.getDownsyncCriteria();
		RequestInfo requestInfo = downsyncRequest.getRequestInfo();
		
		StringBuilder householdUrl = new StringBuilder(configs.getHouseholdHost())
				.append(configs.getHouseholdSearchUrl());
		householdUrl = appendUrlParams(householdUrl, criteria);
				
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
	
	private void searchIndividuals(DownsyncRequest downsyncRequest, Downsync downsync, Set<String> individualIds) {
		
		DownsyncCriteria criteria = downsyncRequest.getDownsyncCriteria();
		RequestInfo requestInfo = downsyncRequest.getRequestInfo();
		
		StringBuilder url = new StringBuilder(configs.getIndividualHost())
				.append(configs.getIndividualSearchUrl());
		url = appendUrlParams(url, criteria);
				
		IndividualSearch individualSearch = IndividualSearch.builder()
				.id(new ArrayList<>(individualIds))
				.build();
		
		IndividualSearchRequest searchRequest = IndividualSearchRequest.builder()
				.individual(individualSearch)
				.requestInfo(requestInfo)
				.build();
		
		List<Individual> individuals = restClient.fetchResult(url, searchRequest, IndividualBulkResponse.class).getIndividual();
		downsync.setIndividuals(individuals);
	}
	
	/**
	 * 
	 * @param downsyncRequest
	 * @param householdIds
	 * @return
	 */
	private Set<String> searchMembers(DownsyncRequest downsyncRequest, Downsync downsync, List<String> householdIds) {
		
		StringBuilder memberUrl = new StringBuilder(configs.getHouseholdHost())
									.append(configs.getHouseholdMemberSearchUrl());
		appendUrlParams(memberUrl, downsyncRequest.getDownsyncCriteria());
		
		String memberIdsquery = "SELECT id from HOUSEHOLD_MEMBER where householdId IN (:householdIds)";
		
		Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("householdIds", householdIds);
        
        /* FIXME SHOULD BE REMOVED AND SEARCH SHOULD BE enhanced with list of household ids*/
        List<String> memeberids = jdbcTemplate.queryForList(memberIdsquery, paramMap, String.class);
		
        
        HouseholdMemberSearch memberSearch = HouseholdMemberSearch.builder()
		.id(memeberids)
		.build();
		
        HouseholdMemberSearchRequest searchRequest = HouseholdMemberSearchRequest.builder()
		.householdMemberSearch(memberSearch)
		.requestInfo(downsyncRequest.getRequestInfo())
		.build();
        
        List<HouseholdMember> members = restClient.fetchResult(memberUrl, searchRequest, HouseholdMemberBulkResponse.class).getHouseholdMembers();
        downsync.setHouseholdMembers(members);
        
		return members.stream().map(HouseholdMember::getIndividualId).collect(Collectors.toSet());
	}

	
	private List<String> searchBeneficiaries(DownsyncRequest downsyncRequest, Downsync downsync, Set<String> individualIds) {
		
		DownsyncCriteria criteria = downsyncRequest.getDownsyncCriteria();
		RequestInfo requestInfo = downsyncRequest.getRequestInfo();
		
		StringBuilder url = new StringBuilder(configs.getProjectHost())
				.append(configs.getProjectBeneficiarySearchUrl());
		url = appendUrlParams(url, criteria);
		
		String beneficiaryIdQuery = "SELECT id from PROJECT_BENEFICIARY where beneficiaryId IN (:beneficiaryIds)";
		
		Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("beneficiaryIds", individualIds);
        
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
		
		return ids;
	}

	/**
	 * append url params 
	 * 
	 * @param url
	 * @param criteria
	 * @return
	 */
	private StringBuilder appendUrlParams(StringBuilder url, DownsyncCriteria criteria) {

		return url.append("?tenantId=")
				  .append(criteria.getTenantId())
				  .append("&offset=")
				  .append(criteria.getOffset())
				  .append("&limit=")
				  .append(criteria.getLimit());
	}
}
