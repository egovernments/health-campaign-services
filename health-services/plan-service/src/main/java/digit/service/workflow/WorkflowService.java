package digit.service.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import digit.config.Configuration;
import digit.repository.ServiceRequestRepository;
import digit.service.PlanEmployeeService;
import digit.service.validator.PlanConfigurationValidator;
import digit.util.CommonUtil;
import digit.util.WorkflowUtil;
import digit.web.models.*;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.models.Workflow;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.contract.request.User;
import org.egov.common.contract.workflow.*;
import org.egov.common.utils.AuditDetailsEnrichmentUtil;
import org.egov.tracer.model.CustomException;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.stream.Collectors;

import static digit.config.ServiceConstants.*;
import static digit.config.ServiceConstants.NO_BUSINESS_SERVICE_DATA_FOUND_MESSAGE;

@Service
@Slf4j
public class WorkflowService {

	private ServiceRequestRepository serviceRequestRepository;

	private Configuration config;

	private ObjectMapper mapper;

    private RestTemplate restTemplate;

    private WorkflowUtil workflowUtil;

    public WorkflowService(ServiceRequestRepository serviceRequestRepository, Configuration config, ObjectMapper mapper, RestTemplate restTemplate, WorkflowUtil workflowUtil) {
        this.serviceRequestRepository = serviceRequestRepository;
        this.config = config;
        this.mapper = mapper;
        this.restTemplate = restTemplate;
        this.workflowUtil = workflowUtil;
    }

    /**
     * Integrates with the workflow for the given plan configuration request.
     * If the action is null, it does not proceed with integration.
     *
     * @param planConfigurationRequest The request containing the plan configuration to integrate with the workflow.
     */
    public void invokeWorkflowForStatusUpdate(PlanConfigurationRequest planConfigurationRequest) {
        if (ObjectUtils.isEmpty(planConfigurationRequest.getPlanConfiguration().getWorkflow()))
            return;

        ProcessInstanceRequest processInstanceRequest = workflowUtil.createWorkflowRequest(planConfigurationRequest);
        ProcessInstanceResponse processInstanceResponse = callWorkflowTransition(processInstanceRequest);

        // Setting the status back to the plan configuration object from workflow response
        planConfigurationRequest.getPlanConfiguration().setStatus(processInstanceResponse.getProcessInstances().get(0).getState().getState());
    }

	/**
	 * Integrates with the workflow for the given plan request.
	 * If the action is null, it does not proceed with integration.
	 *
	 * @param planRequest The request containing the plan estimate to integrate with the workflow.
	 */
	public void invokeWorkflowForStatusUpdate(PlanRequest planRequest) {
		if (ObjectUtils.isEmpty(planRequest.getPlan().getWorkflow()))
			return;

        ProcessInstanceRequest processInstanceRequest = workflowUtil.createWorkflowRequest(planRequest);
        ProcessInstanceResponse processInstanceResponse = callWorkflowTransition(processInstanceRequest);

        // Setting the status back to the plan configuration object from workflow response
        planRequest.getPlan().setStatus(processInstanceResponse.getProcessInstances().get(0).getState().getState());

        // Enrich audit details after auto assignment is complete
        planRequest.getPlan().setAuditDetails(AuditDetailsEnrichmentUtil
                .prepareAuditDetails( planRequest.getPlan().getAuditDetails(),  planRequest.getRequestInfo(), Boolean.FALSE));

    }

    /**
     * Calls the workflow transition service and retrieves the process instance response.
     *
     * @param processInstanceRequest The request containing process instance details for the workflow transition.
     * @return The response containing details of the process instances after the transition.
     * @throws CustomException if there is an error during the workflow integration.
     */
    public ProcessInstanceResponse callWorkflowTransition(ProcessInstanceRequest processInstanceRequest) {
        ProcessInstanceResponse processInstanceResponse;
        try {
            Object response = serviceRequestRepository.fetchResult(getWorkflowTransitionUri(), processInstanceRequest);
            processInstanceResponse = mapper.convertValue(response, ProcessInstanceResponse.class);
            return processInstanceResponse;
        } catch (Exception e) {
            throw new CustomException(WORKFLOW_INTEGRATION_ERROR_CODE, WORKFLOW_INTEGRATION_ERROR_MESSAGE + e.getMessage());
        }
    }

    /**
     * Constructs the URI for the workflow service transition API.
     *
     * @return The StringBuilder containing the constructed workflow transition URI.
     */
    private StringBuilder getWorkflowTransitionUri() {
        return new StringBuilder().append(config.getWfHost()).append(config.getWfTransitionPath());
    }

    /**
     * Invokes the workflow transition for updating the status of plans in a bulk request.
     *
     * @param bulkPlanRequest The request containing plans that require workflow status updates.
     */
    public void invokeWorkflowForStatusUpdate(BulkPlanRequest bulkPlanRequest) {
        ProcessInstanceRequest processInstanceRequest = workflowUtil.createWorkflowRequest(bulkPlanRequest);
        ProcessInstanceResponse processInstanceResponse = callWorkflowTransition(processInstanceRequest);

        enrichPlansPostTransition(processInstanceResponse, bulkPlanRequest);
    }

    /**
     * Updates the status and audit details of plans after a workflow transition.
     *
     * @param processInstanceResponse The response containing updated process instance details.
     * @param bulkPlanRequest         The bulk plan request containing plans to be updated.
     */
    private void enrichPlansPostTransition(ProcessInstanceResponse processInstanceResponse, BulkPlanRequest bulkPlanRequest) {
        // Update status and audit information post transition
        bulkPlanRequest.getPlans().forEach(plan -> {
            // Update status of plan
            plan.setStatus(processInstanceResponse.getProcessInstances().get(0).getState().getState());

            // Update audit information of plan
            plan.setAuditDetails(AuditDetailsEnrichmentUtil
                    .prepareAuditDetails(plan.getAuditDetails(), bulkPlanRequest.getRequestInfo(), Boolean.FALSE));
        });
    }

    /**
     * Creates a list of all the workflow states for the provided business service.
     * @param requestInfo
     * @param businessService
     * @param tenantId
     * @return
     */
    public List<String> getStatusFromBusinessService(RequestInfo requestInfo, String businessService, String tenantId) {
        BusinessService businessServices = fetchBusinessService(requestInfo, businessService, tenantId);

        return businessServices.getStates().stream()
                .map(State::getState)
                .filter(state -> !ObjectUtils.isEmpty(state))
                .toList();
    }

    /**
     * This method fetches business service details for the given tenant id and business service.
     *
     * @param requestInfo     the request info from request.
     * @param businessService businessService whose details are to be searched.
     * @param tenantId        tenantId from request.
     * @return returns the business service response for the given tenant id and business service.
     */
    public BusinessService fetchBusinessService(RequestInfo requestInfo, String businessService, String tenantId) {

        // Get business service uri
        Map<String, String> uriParameters = new HashMap<>();
        String uri = getBusinessServiceUri(businessService, tenantId, uriParameters);

        // Create request body
        RequestInfoWrapper requestInfoWrapper = RequestInfoWrapper.builder().requestInfo(requestInfo).build();
        BusinessServiceResponse businessServiceResponse = new BusinessServiceResponse();

        try {
            businessServiceResponse = restTemplate.postForObject(uri, requestInfoWrapper, BusinessServiceResponse.class, uriParameters);
        } catch (Exception e) {
            log.error(ERROR_WHILE_FETCHING_BUSINESS_SERVICE_DETAILS, e);
        }

        if (CollectionUtils.isEmpty(businessServiceResponse.getBusinessServices())) {
            throw new CustomException(NO_BUSINESS_SERVICE_DATA_FOUND_CODE, NO_BUSINESS_SERVICE_DATA_FOUND_MESSAGE);
        }

        return businessServiceResponse.getBusinessServices().get(0);
    }

    /**
     * This method creates business service uri with query parameters
     *
     * @param businessService businessService whose details are to be searched.
     * @param tenantId        tenant id from the request.
     * @param uriParameters   map that stores values corresponding to the placeholder in uri
     * @return
     */
    private String getBusinessServiceUri(String businessService, String tenantId, Map<String, String> uriParameters) {

        StringBuilder uri = new StringBuilder();
        uri.append(config.getWfHost()).append(config.getBusinessServiceSearchEndpoint()).append(URI_BUSINESS_SERVICE_QUERY_TEMPLATE);

        uriParameters.put(URI_TENANT_ID_PARAM, tenantId);
        uriParameters.put(URI_BUSINESS_SERVICE_PARAM, businessService);

        return uri.toString();
    }

}