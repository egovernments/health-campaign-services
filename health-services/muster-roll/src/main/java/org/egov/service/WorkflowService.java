package org.egov.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.egov.common.contract.models.RequestInfoWrapper;
import org.egov.common.contract.models.Workflow;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.contract.request.User;
import org.egov.common.contract.workflow.*;
import org.egov.config.MusterRollServiceConfiguration;
import org.egov.repository.ServiceRequestRepository;
import org.egov.tracer.model.CustomException;
import org.egov.web.models.MusterRoll;
import org.egov.web.models.MusterRollRequest;
import org.egov.works.services.common.models.musterroll.Status;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class WorkflowService {

    private final MusterRollServiceConfiguration serviceConfiguration;

    private final ServiceRequestRepository repository;

    private final ObjectMapper mapper;

    @Autowired
    public WorkflowService(MusterRollServiceConfiguration serviceConfiguration, ServiceRequestRepository repository, ObjectMapper mapper) {
        this.serviceConfiguration = serviceConfiguration;
        this.repository = repository;
        this.mapper = mapper;
    }

    /* Call the workflow service with the given action and update the status
     * return the updated status of the application
     *
     */
    public String updateWorkflowStatus(MusterRollRequest musterRollRequest) {
        ProcessInstance processInstance = getProcessInstanceForMusterRoll(musterRollRequest);
        ProcessInstanceRequest workflowRequest = new ProcessInstanceRequest(musterRollRequest.getRequestInfo(), Collections.singletonList(processInstance));
        ProcessInstance processInstanceResponse = callWorkFlow(workflowRequest);
        musterRollRequest.getMusterRoll().setMusterRollStatus(processInstanceResponse.getState().getState());
        //musterRollStatus
        musterRollRequest.getMusterRoll().setStatus(Status.fromValue(processInstanceResponse.getState().getApplicationStatus()));
        // Fetch currentProcessInstance from workflow process search for inbox config
        musterRollRequest.getMusterRoll().setProcessInstance(processInstanceResponse);
        return processInstanceResponse.getState().getApplicationStatus();
    }

    private ProcessInstance getProcessInstanceForMusterRoll(MusterRollRequest request) {

        MusterRoll musterRoll = request.getMusterRoll();
        Workflow workflow = request.getWorkflow();

        ProcessInstance processInstance = new ProcessInstance();
        processInstance.setBusinessId(musterRoll.getMusterRollNumber());
        processInstance.setAction(request.getWorkflow().getAction());
        processInstance.setModuleName(serviceConfiguration.getMusterRollWFModuleName());
        processInstance.setTenantId(musterRoll.getTenantId());
        processInstance.setBusinessService(serviceConfiguration.getMusterRollWFBusinessService());
        processInstance.setComment(workflow.getComments());

        if (!CollectionUtils.isEmpty(workflow.getAssignes())) {
            List<User> users = new ArrayList<>();

            workflow.getAssignes().forEach(uuid -> {
                User user = new User();
                user.setUuid(uuid);
                users.add(user);
            });

            processInstance.setAssignes(users);
        }

        if (!CollectionUtils.isEmpty(workflow.getDocuments())) {
            processInstance.setDocuments(workflow.getDocuments());
        }

        return processInstance;
    }

    /**
     * Method to integrate with workflow
     * <p>
     * take the ProcessInstanceRequest as paramerter to call wf-service
     * <p>
     * and return wf-response to sets the resultant status
     */
    private ProcessInstance callWorkFlow(ProcessInstanceRequest workflowReq) {

        ProcessInstanceResponse response = null;
        StringBuilder url = new StringBuilder(serviceConfiguration.getWfHost().concat(serviceConfiguration.getWfTransitionPath()));
        Object optional = repository.fetchResult(url, workflowReq);
        response = mapper.convertValue(optional, ProcessInstanceResponse.class);
        return response.getProcessInstances().get(0);
    }

    /*
     * Should return the applicable BusinessService for the given request
     *
     */
    public BusinessService getBusinessService(MusterRollRequest musterRollRequest) {
        String tenantId = musterRollRequest.getMusterRoll().getTenantId();
        StringBuilder url = getSearchURLWithParams(tenantId, serviceConfiguration.getMusterRollWFBusinessService());
        RequestInfo requestInfo = musterRollRequest.getRequestInfo();
        Object result = repository.fetchResult(url, requestInfo);
        BusinessServiceResponse response = null;
        try {
            response = mapper.convertValue(result, BusinessServiceResponse.class);
        } catch (IllegalArgumentException e) {
            throw new CustomException("PARSING ERROR", "Failed to parse response of workflow business service search");
        }

        if (CollectionUtils.isEmpty(response.getBusinessServices()))
            throw new CustomException("BUSINESSSERVICE_DOESN'T_EXIST", "The businessService : " + serviceConfiguration.getMusterRollWFBusinessService() + " doesn't exist");

        return response.getBusinessServices().get(0);
    }

    /**
     * Creates url for search based on given tenantId and businessservices
     *
     * @param tenantId        The tenantId for which url is generated
     * @param businessService The businessService for which url is generated
     * @return The search url
     */

    private StringBuilder getSearchURLWithParams(String tenantId, String businessService) {

        StringBuilder url = new StringBuilder(serviceConfiguration.getWfHost());
        url.append(serviceConfiguration.getWfBusinessServiceSearchPath());
        url.append("?tenantId=");
        url.append(tenantId);
        url.append("&businessServices=");
        url.append(businessService);
        return url;
    }

    /*
     * Should return the applicable processInstance for the given request
     *
     */
    public ProcessInstance getProcessInstance(MusterRollRequest musterRollRequest) {
        String tenantId = musterRollRequest.getMusterRoll().getTenantId();
        String businessId = musterRollRequest.getMusterRoll().getMusterRollNumber();
        StringBuilder url = getProcessSearchURLWithParams(tenantId, businessId);
        RequestInfoWrapper requestInfoWrapper = RequestInfoWrapper.builder().requestInfo(musterRollRequest.getRequestInfo()).build();
        Object result = repository.fetchResult(url, requestInfoWrapper);
        ProcessInstanceResponse response = null;
        try {
            response = mapper.convertValue(result, ProcessInstanceResponse.class);
        } catch (IllegalArgumentException e) {
            throw new CustomException("PARSING ERROR", "Failed to parse response of workflow business service search");
        }

        if (CollectionUtils.isEmpty(response.getProcessInstances()))
            throw new CustomException("PROCESSINSTANCE_DOESN'T_EXIST", "The businessId : " + businessId + " doesn't exist");

        return response.getProcessInstances().get(0);
    }

    /**
     * Creates url for search based on given tenantId and businessId
     *
     * @param tenantId     The tenantId for which url is generated
     * @param businessId   The businessId for which url is generated
     * @return The search url
     */

    private StringBuilder getProcessSearchURLWithParams(String tenantId, String businessId) {

        StringBuilder url = new StringBuilder(serviceConfiguration.getWfHost());
        url.append(serviceConfiguration.getWfProcessInstanceSearchPath());
        url.append("?tenantId=");
        url.append(tenantId);
        url.append("&businessIds=");
        url.append(businessId);
        url.append("&history=false");
        return url;
    }

}
