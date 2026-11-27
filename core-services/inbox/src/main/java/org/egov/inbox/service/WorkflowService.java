package org.egov.inbox.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.egov.common.contract.request.RequestInfo;
import org.egov.common.contract.request.Role;
import org.egov.inbox.config.InboxConfiguration;
import org.egov.inbox.repository.ServiceRequestRepository;
import org.egov.inbox.util.BpaConstants;
import org.egov.inbox.util.ErrorConstants;
import org.egov.inbox.util.FSMConstants;
import org.egov.inbox.web.model.InboxRequest;
import org.egov.inbox.web.model.RequestInfoWrapper;
import org.egov.inbox.web.model.workflow.BusinessService;
import org.egov.inbox.web.model.workflow.BusinessServiceResponse;
import org.egov.inbox.web.model.workflow.ProcessInstanceResponse;
import org.egov.inbox.web.model.workflow.ProcessInstanceSearchCriteria;
import org.egov.tracer.model.CustomException;
import org.egov.inbox.web.model.workflow.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class WorkflowService {

	private InboxConfiguration config;

	private ServiceRequestRepository serviceRequestRepository;

	private ObjectMapper mapper;

	@Autowired
	public WorkflowService(InboxConfiguration config, ServiceRequestRepository serviceRequestRepository,
			ObjectMapper mapper) {
		this.config = config;
		this.serviceRequestRepository = serviceRequestRepository;
		this.mapper = mapper;
	}

	public Integer getProcessCount(String tenantId, RequestInfo requestInfo, ProcessInstanceSearchCriteria criteria) {
		List<String> listOfBusinessServices = new ArrayList<>(criteria.getBusinessService());
		Integer processCount = 0;
		for(String businessSrv : listOfBusinessServices) {
			criteria.setBusinessService(Collections.singletonList(businessSrv));
			StringBuilder url = new StringBuilder(config.getWorkflowHost());
			url.append(config.getProcessCountPath());
			criteria.setIsProcessCountCall(true);
			url = this.buildWorkflowUrl(criteria, url, Boolean.FALSE);

			RequestInfoWrapper requestInfoWrapper = RequestInfoWrapper.builder().requestInfo(requestInfo).build();
			Object result = serviceRequestRepository.fetchIntResult(url, requestInfoWrapper);
			Integer response = null;
			try {
				response = mapper.convertValue(result, Integer.class);
			} catch (IllegalArgumentException e) {
				throw new CustomException(ErrorConstants.PARSING_ERROR, "Failed to parse response of ProcessInstance Count");
			}
			processCount += response;
		}
		criteria.setBusinessService(listOfBusinessServices);
		return processCount;
	}
	
	public Integer getNearingSlaProcessCount(String tenantId, RequestInfo requestInfo, ProcessInstanceSearchCriteria criteria) {
		List<String> listOfBusinessServices = new ArrayList<>(criteria.getBusinessService());
		Integer processCount = 0;
		for(String businessSrv : listOfBusinessServices) {
			criteria.setBusinessService(Collections.singletonList(businessSrv));
			StringBuilder url = new StringBuilder(config.getWorkflowHost());
			url.append(config.getNearingSlaProcessCountPath());
			criteria.setIsProcessCountCall(true);
			url = this.buildWorkflowUrl(criteria, url, Boolean.FALSE);

			RequestInfoWrapper requestInfoWrapper = RequestInfoWrapper.builder().requestInfo(requestInfo).build();
			Object result = serviceRequestRepository.fetchIntResult(url, requestInfoWrapper);
			Integer response = null;
			try {
				response = mapper.convertValue(result, Integer.class);
			} catch (IllegalArgumentException e) {
				throw new CustomException(ErrorConstants.PARSING_ERROR, "Failed to parse response of ProcessInstance Count");
			}
			processCount += response;
		}
		criteria.setBusinessService(listOfBusinessServices);
		return processCount;
	}
	
        public List<HashMap<String, Object>> getProcessStatusCount(RequestInfo requestInfo,
                ProcessInstanceSearchCriteria criteria) {
            List<String> listOfBusinessServices = new ArrayList<>(criteria.getBusinessService());
            List<HashMap<String, Object>> finalResponse = null;
            for (String businessSrv : listOfBusinessServices) {
                criteria.setBusinessService(Collections.singletonList(businessSrv));
                StringBuilder url = new StringBuilder(config.getWorkflowHost());
                url.append(config.getProcessStatusCountPath());
                criteria.setIsProcessCountCall(true);
                // For BPA having large request, so that it was sending from the body
                List<String> roles = requestInfo.getUserInfo().getRoles().stream().map(Role::getCode).collect(Collectors.toList());
                if ((!ObjectUtils.isEmpty(criteria.getModuleName()) && !criteria.getModuleName().equalsIgnoreCase(BpaConstants.BPA)) 
                        || (!ObjectUtils.isEmpty(criteria.getModuleName()) && 
                        		criteria.getModuleName().equalsIgnoreCase(BpaConstants.BPA) && !roles.contains(BpaConstants.CITIZEN)))
                    url = this.buildWorkflowUrl(criteria, url, Boolean.FALSE);
                if (requestInfo.getUserInfo().getRoles().get(0).getCode().equals(FSMConstants.FSM_DSO)) {
                    url.append("&assignee=").append(requestInfo.getUserInfo().getUuid());
                }
                
                if (criteria != null && !ObjectUtils.isEmpty(criteria.getModuleName()) && criteria.getModuleName().equalsIgnoreCase(BpaConstants.BPA)
                        && roles.contains(BpaConstants.CITIZEN)) {
                    List<String> inputBusinessSrvs = new ArrayList<>(criteria.getBusinessService());
                    criteria.setBusinessService(null);
                    Map<String, Object> statusRequest = new HashMap<>();
                    statusRequest.put("RequestInfo", requestInfo);
                    statusRequest.put("ProcessInstanceSearchCriteria", criteria);
		    url.append("?tenantId=").append(criteria.getTenantId());
                    finalResponse = (List<HashMap<String, Object>>) serviceRequestRepository.fetchListResult(url, statusRequest);
                    criteria.setBusinessService(inputBusinessSrvs);
                } else {
                    RequestInfoWrapper requestInfoWrapper = RequestInfoWrapper.builder().requestInfo(requestInfo).build();
                    if (finalResponse == null) {
                        finalResponse = (List<HashMap<String, Object>>) serviceRequestRepository.fetchListResult(url,
                                requestInfoWrapper);
                    } else {
                        finalResponse.addAll(
                                (List<HashMap<String, Object>>) serviceRequestRepository.fetchListResult(url, requestInfoWrapper));
                    }
                }
            }
            criteria.setBusinessService(listOfBusinessServices);
            return finalResponse;
        }
	
	public ProcessInstanceResponse getProcessInstance(ProcessInstanceSearchCriteria criteria, RequestInfo requestInfo) {
		StringBuilder url = new StringBuilder(config.getWorkflowHost());
		url.append( config.getProcessSearchPath());
		url = this.buildWorkflowUrl(criteria, url, Boolean.FALSE);
		 if(requestInfo.getUserInfo().getRoles().get(0).getCode().equals(FSMConstants.FSM_DSO)) {
         	url.append("&assignee=").append( requestInfo.getUserInfo().getUuid());
         }
		RequestInfoWrapper requestInfoWrapper = RequestInfoWrapper.builder().requestInfo(requestInfo).build();
		Object result = serviceRequestRepository.fetchResult(url, requestInfoWrapper);
		ProcessInstanceResponse resposne =null;
		try {
			resposne = mapper.convertValue(result, ProcessInstanceResponse.class);
		} catch (IllegalArgumentException e) {
			throw new CustomException(ErrorConstants.PARSING_ERROR, "Failed to parse response of ProcessInstance");
		}
		return resposne;
	}
	/**
	 * Get the workflow config for the given tenant
	 * 
	 * @param tenantId
	 *           id of the tenant
	 * @param requestInfo
	 *            The RequestInfo object of the request
	 * @param businessServiceName
	 * 				businessService code
	 * @return BusinessService for the the given tenantId
	 */
	public BusinessService getBusinessService(String tenantId, RequestInfo requestInfo, String businessServceName) {
		StringBuilder url = getSearchURLWithParams(tenantId, businessServceName);
		RequestInfoWrapper requestInfoWrapper = RequestInfoWrapper.builder().requestInfo(requestInfo).build();
		Object result = serviceRequestRepository.fetchResult(url, requestInfoWrapper);
		BusinessServiceResponse response = null;
		try {
			response = mapper.convertValue(result, BusinessServiceResponse.class);
		} catch (IllegalArgumentException e) {
			throw new CustomException(ErrorConstants.PARSING_ERROR, "Failed to parse response of Workflow");
		}
		return response.getBusinessServices().get(0);
	}


	@Cacheable(value="businessServices")
	public List<BusinessService> getBusinessServices(InboxRequest request) {
		String tenantId = request.getInbox().getTenantId();
		RequestInfo requestInfo = request.getRequestInfo() ;
		List<String> businessServicesCodes = request.getInbox().getProcessSearchCriteria().getBusinessService();
		String businessServiceList = String.join(",",businessServicesCodes);
		StringBuilder url = getSearchURLWithParams(tenantId, businessServiceList);
		RequestInfoWrapper requestInfoWrapper = RequestInfoWrapper.builder().requestInfo(requestInfo).build();
		Object result = serviceRequestRepository.fetchResult(url, requestInfoWrapper);
		BusinessServiceResponse response = null;
		try {
			response = mapper.convertValue(result, BusinessServiceResponse.class);
		} catch (IllegalArgumentException e) {
			throw new CustomException(ErrorConstants.PARSING_ERROR, "Failed to parse response of Workflow");
		}
		return response.getBusinessServices();
	}


	public Map<String,String> getStatusIdToBusinessServiceMap(List<BusinessService> businessServices){

		Map<String,String> statusIdToBusinessServiceMap = new HashMap<>();

		businessServices.forEach(businessService -> {
			businessService.getStates().forEach(state -> {
				statusIdToBusinessServiceMap.put(state.getUuid(), businessService.getBusinessService());
			 }
			);
		});

		return statusIdToBusinessServiceMap;
	}

	public Map<String,String> getApplicationStatusIdToStatusMap(List<BusinessService> businessServices){

		Map<String,String> statusIdToApplicationStatusMap = new HashMap<>();

		businessServices.forEach(businessService -> {
			businessService.getStates().forEach(state -> {
				statusIdToApplicationStatusMap.put(state.getUuid(), state.getApplicationStatus());
					}
			);
		});

		return statusIdToApplicationStatusMap;
	}

	public Map<String,String> getApplicationStatusIdToStateMap(List<BusinessService> businessServices){

		Map<String,String> statusIdToApplicationStateMap = new HashMap<>();

		businessServices.forEach(businessService -> {
			businessService.getStates().forEach(state -> {
				statusIdToApplicationStateMap.put(state.getUuid(), state.getState());
					}
			);
		});

		return statusIdToApplicationStateMap;
	}

	
	private StringBuilder buildWorkflowUrl(ProcessInstanceSearchCriteria criteria, StringBuilder url,boolean noStatus) {
		url.append("?tenantId=").append(criteria.getTenantId());
		if(!CollectionUtils.isEmpty(criteria.getStatus()) && noStatus == Boolean.FALSE) {
			url.append("&status=").append(StringUtils.arrayToDelimitedString(criteria.getStatus().toArray(),","));
		}
		
		if(!CollectionUtils.isEmpty(criteria.getBusinessIds())) {
			url.append("&businessIds=").append(StringUtils.arrayToDelimitedString(criteria.getBusinessIds().toArray(),","));
		}
		
		if(!CollectionUtils.isEmpty(criteria.getIds())) {
			url.append("&ids=").append(StringUtils.arrayToDelimitedString(criteria.getIds().toArray(),","));
		}
		if(!StringUtils.isEmpty(criteria.getAssignee())) {
			url.append("&assignee=").append( criteria.getAssignee());
		}
		if(criteria.getHistory() != null) {
			url.append("&history=").append( criteria.getHistory());
		}
		if(criteria.getFromDate() != null) {
			url.append("&fromDate=").append( criteria.getFromDate());
		}
		if(criteria.getToDate() != null) {
			url.append("&toDate=").append( criteria.getToDate());
		}

		if(!StringUtils.isEmpty(criteria.getModuleName())) {
			url.append("&moduleName=").append( criteria.getModuleName());
		}
		if(criteria.getIsProcessCountCall() || ObjectUtils.isEmpty(criteria.getModuleName()) && !StringUtils.isEmpty(criteria.getBusinessService())) {
			url.append("&businessService=").append( StringUtils.arrayToDelimitedString(criteria.getBusinessService().toArray(),","));
		}
		if(!StringUtils.isEmpty(criteria.getLimit())) {
			url.append("&limit=").append( criteria.getLimit());
		}
		if(!StringUtils.isEmpty(criteria.getOffset())) {
			url.append("&offset=").append( criteria.getOffset());
		}
		
		return url;
	}

	/**
	 * Creates url for search based on given tenantId
	 *
	 * @param tenantId
	 *            The tenantId for which url is generated
	 * @return The search url
	 */
	private StringBuilder getSearchURLWithParams(String tenantId, String businessService) {
		StringBuilder url = new StringBuilder(config.getWorkflowHost());
		
		
		
		if (businessService != null) {
			url.append(config.getBusinessServiceSearchPath());
			url.append("?businessServices=");
			url.append(businessService);
		} 
		
		url.append("&tenantId=");
		url.append(tenantId);
		
		return url;
	}
	
	/**
     * Gets the list of status on which user from requestInfo can take action upon
     * @param requestInfo The RequestInfo Object of the request
     * @param businessServices List of all businessServices
     * @return List of status on which user from requestInfo can take action upon
     */

    public HashMap<String,String> getActionableStatusesForRole(RequestInfo requestInfo, List<BusinessService> businessServices,ProcessInstanceSearchCriteria criteria){
        log.info("========== GET ACTIONABLE STATUSES FOR ROLE - START ==========");

        String tenantId;
        List<String> userRoleCodes;
        Map<String,List<String>> tenantIdToUserRolesMap = getTenantIdToUserRolesMap(requestInfo);
        log.info("TenantId to User Roles Map: {}", tenantIdToUserRolesMap);

        Map<String,List<BusinessService>> tenantIdToBuisnessSevicesMap =  getTenantIdToBuisnessSevicesMap(businessServices);
        log.info("TenantId to BusinessServices Map: {}", tenantIdToBuisnessSevicesMap.keySet());
        tenantIdToBuisnessSevicesMap.forEach((key, value) ->
            log.info("  TenantId: {}, BusinessServices: {}", key, value.stream().map(BusinessService::getBusinessService).collect(java.util.stream.Collectors.toList())));

        Map<String,Set<String>> stateToRoleMap = getStateToRoleMap(businessServices);
        log.info("State to Role Map (size: {})", stateToRoleMap.size());

        HashMap<String,String> actionableStatuses = new HashMap<>();
        log.info("Criteria TenantId: {}", criteria.getTenantId());
        log.info("includeTerminateStates config: {}", config.getIncludeTerminateStates());

        for(Map.Entry<String,List<String>> entry : tenantIdToUserRolesMap.entrySet()){
            log.info("--- Processing User Roles Entry ---");
            log.info("  Entry TenantId: {}, Roles: {}", entry.getKey(), entry.getValue());

        	String statelevelTenantId=entry.getKey().split("\\.")[0];
            log.info("  State level TenantId: {}", statelevelTenantId);

            boolean tenantIdMatches = entry.getKey().equals(criteria.getTenantId());
            boolean isDSORole = entry.getValue().contains(FSMConstants.FSM_DSO) && entry.getKey().equals(statelevelTenantId);
            log.info("  TenantId matches criteria? {}", tenantIdMatches);
            log.info("  Is DSO role? {}", isDSORole);

            if(tenantIdMatches || isDSORole){
                log.info("  MATCHED! Processing business services for this tenant");

                List<BusinessService> businessServicesByTenantId = new ArrayList();
                if(entry.getKey().split("\\.").length==1){
                    businessServicesByTenantId = tenantIdToBuisnessSevicesMap.get(criteria.getTenantId());
                    log.info("  Single level tenant, using criteria tenantId: {}", criteria.getTenantId());
              }else{
                    businessServicesByTenantId = tenantIdToBuisnessSevicesMap.get(entry.getKey());
                    log.info("  Multi-level tenant, using entry key: {}", entry.getKey());
              }

                log.info("  BusinessServices found: {}", businessServicesByTenantId != null ? businessServicesByTenantId.size() : 0);

                if(businessServicesByTenantId != null ) {
                	 businessServicesByTenantId.forEach(service -> {
                         log.info("    Processing BusinessService: {}", service.getBusinessService());
                         List<State> states = service.getStates();
                         log.info("      States count: {}", states != null ? states.size() : 0);

                         states.forEach(state -> {
                             log.info("      --- Processing State ---");
                             log.info("        State: {}, UUID: {}, IsTerminate: {}", state.getState(), state.getUuid(), state.getIsTerminateState());

                             Set<String> stateRoles = stateToRoleMap.get(state.getUuid());
                             log.info("        Roles for this state: {}", stateRoles);

                             boolean hasMatchingRoles = !CollectionUtils.isEmpty(stateRoles) && !Collections.disjoint(stateRoles,entry.getValue());
                             log.info("        User roles: {}", entry.getValue());
                             log.info("        Has matching roles? {}", hasMatchingRoles);

                             // Add if user has matching roles
                             if(hasMatchingRoles){
                                 actionableStatuses.put(state.getUuid(), state.getApplicationStatus());
                                 log.info("        ADDED as actionable (role match): {} -> {}", state.getUuid(), state.getApplicationStatus());
                             }

                             // Add terminate states if configured
                             if(config.getIncludeTerminateStates() != null
                                 && config.getIncludeTerminateStates()
                                 && state.getIsTerminateState() != null
                                 && state.getIsTerminateState()){
                                 actionableStatuses.put(state.getUuid(), state.getApplicationStatus());
                                 log.info("        ADDED as actionable (terminate state): {} -> {}", state.getUuid(), state.getApplicationStatus());
                             }

                         });
                     });
                } else {
                    log.warn("  BusinessServices is NULL for tenantId!");
                }

            } else {
                log.info("  NOT MATCHED, skipping this tenant");
            }
        }

        log.info("Final Actionable Statuses: {}", actionableStatuses);
        log.info("========== GET ACTIONABLE STATUSES FOR ROLE - END ==========");
        return actionableStatuses;
    }
    
    /**
     * Gets the map of tenantId to roles the user is assigned
     * @param requestInfo RequestInfo of the request
     * @return Map of tenantId to roles for user in the requestInfo
     */
    public Map<String,List<String>> getTenantIdToUserRolesMap(RequestInfo requestInfo){
        Map<String,List<String>> tenantIdToUserRoles = new HashMap<>();
        requestInfo.getUserInfo().getRoles().forEach(role -> {
            if(tenantIdToUserRoles.containsKey(role.getTenantId())){
                tenantIdToUserRoles.get(role.getTenantId()).add(role.getCode());
            }
            else {
                List<String> roleCodes = new LinkedList<>();
                roleCodes.add(role.getCode());
                tenantIdToUserRoles.put(role.getTenantId(),roleCodes);
            }

        });
        return tenantIdToUserRoles;
    }
    
    public Map<String,List<BusinessService>> getTenantIdToBuisnessSevicesMap(List<BusinessService> businessServices){
        Map<String,List<BusinessService>> tenantIdToBuisnessSevicesMap = new HashMap<>();
        businessServices.forEach(businessService -> {
            if(tenantIdToBuisnessSevicesMap.containsKey(businessService.getTenantId())){
                tenantIdToBuisnessSevicesMap.get(businessService.getTenantId()).add(businessService);
            }
            else {
                List<BusinessService> businessServiceList = new LinkedList<>();
                businessServiceList.add(businessService);
                tenantIdToBuisnessSevicesMap.put(businessService.getTenantId(),businessServiceList);
            }
        });
        return tenantIdToBuisnessSevicesMap;
    }
    
    /**
     * Creates a map of status to roles who can take actions on it for all businessService
     * @param businessServices The list of businessServices
     * @return Map of status to roles which can take action on it for all businessService
     */
    public Map<String,Set<String>> getStateToRoleMap(List<BusinessService> businessServices){
        Map<String,Set<String>> stateToRolesMap = new HashMap<>();
        businessServices.forEach(businessService -> {
            for(State state : businessService.getStates()){
                HashSet<String> roles = new HashSet<>();
                if(!CollectionUtils.isEmpty(state.getActions())){
                    state.getActions().forEach(action -> {
                        roles.addAll(action.getRoles());
                    });
                }
                stateToRolesMap.put(state.getUuid(),roles);
            }
        });
        return stateToRolesMap;
    }

	
}
