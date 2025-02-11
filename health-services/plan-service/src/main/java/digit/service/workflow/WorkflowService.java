package digit.service.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import digit.config.Configuration;
import digit.repository.ServiceRequestRepository;
import digit.service.PlanEmployeeService;
import digit.service.validator.PlanConfigurationValidator;
import digit.util.CommonUtil;
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

	private CommonUtil commonUtil;

    private PlanEmployeeService planEmployeeService;

    private PlanConfigurationValidator planConfigurationValidator;

    private RestTemplate restTemplate;

    public WorkflowService(ServiceRequestRepository serviceRequestRepository, Configuration config, ObjectMapper mapper, CommonUtil commonUtil, PlanEmployeeService planEmployeeService, PlanConfigurationValidator planConfigurationValidator, RestTemplate restTemplate) {
        this.serviceRequestRepository = serviceRequestRepository;
        this.config = config;
        this.mapper = mapper;
        this.commonUtil = commonUtil;
        this.planEmployeeService = planEmployeeService;
        this.planConfigurationValidator = planConfigurationValidator;
        this.restTemplate = restTemplate;
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

        ProcessInstanceRequest processInstanceRequest = createWorkflowRequest(planConfigurationRequest);
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

        ProcessInstanceRequest processInstanceRequest = createWorkflowRequest(planRequest);
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
        } catch (Exception e) {
            throw new CustomException(WORKFLOW_INTEGRATION_ERROR_CODE, WORKFLOW_INTEGRATION_ERROR_MESSAGE + e.getMessage());
        }

        return processInstanceResponse;
    }

    /**
     * Creates a workflow request from the given plan configuration request.
     *
     * @param planConfigurationRequest The request containing the plan configuration to create a workflow request.
     * @return The constructed process instance request for the workflow.
     */
    public ProcessInstanceRequest createWorkflowRequest(PlanConfigurationRequest planConfigurationRequest) {
        PlanConfiguration planConfig = planConfigurationRequest.getPlanConfiguration();
        ProcessInstance processInstance = ProcessInstance.builder()
                .businessId(planConfig.getId())
                .tenantId(planConfig.getTenantId())
                .businessService(PLAN_CONFIGURATION_BUSINESS_SERVICE)
                .moduleName(MODULE_NAME_VALUE)
                .action(planConfig.getWorkflow().getAction())
                .comment(planConfig.getWorkflow().getComments())
                .documents(planConfig.getWorkflow().getDocuments())
                .build();

        enrichAssignesInProcessInstance(processInstance, planConfig.getWorkflow());

        return ProcessInstanceRequest.builder()
                .requestInfo(planConfigurationRequest.getRequestInfo())
                .processInstances(Collections.singletonList(processInstance))
                .build();
    }

    /**
     * Creates a workflow request from the given plan configuration request.
     *
     * @param planRequest The request containing the plan to create a workflow request.
     * @return The constructed process instance request for the workflow.
     */
    public ProcessInstanceRequest createWorkflowRequest(PlanRequest planRequest) {
        Plan plan = planRequest.getPlan();
        ProcessInstance processInstance = ProcessInstance.builder()
                .businessId(plan.getId())
                .tenantId(plan.getTenantId())
                .businessService(PLAN_ESTIMATION_BUSINESS_SERVICE)
                .moduleName(MODULE_NAME_VALUE)
                .action(plan.getWorkflow().getAction())
                .comment(plan.getWorkflow().getComments())
                .documents(plan.getWorkflow().getDocuments())
                .build();

        List<String> assignee = getAssigneeForAutoAssignment(plan, planRequest.getRequestInfo());

        // Set assignees for send back actions
        if (config.getWfSendBackActions().contains(plan.getWorkflow().getAction())) {
            assignee = Collections.singletonList(plan.getAuditDetails().getLastModifiedBy());
        }

        // Set Assignee
        if(!ObjectUtils.isEmpty(assignee))
            plan.getWorkflow().setAssignes(assignee);

        plan.setAssignee(assignee);

        enrichAssignesInProcessInstance(processInstance, plan.getWorkflow());

        return ProcessInstanceRequest.builder()
                .requestInfo(planRequest.getRequestInfo())
                .processInstances(Collections.singletonList(processInstance))
                .build();
    }

    /**
     * Enriches the process instance with assignees from the given workflow.
     *
     * @param processInstance The process instance to enrich with assignees.
     * @param workflow        The workflow containing assignees to be added to the process instance.
     */
    public void enrichAssignesInProcessInstance(ProcessInstance processInstance, Workflow workflow) {
        List<User> userList = CollectionUtils.isEmpty(workflow.getAssignes())
                ? new LinkedList<>()
                : workflow.getAssignes().stream()
                .map(assignee -> User.builder().uuid(assignee).build())
                .toList();

        processInstance.setAssignes(userList);
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
     * Automatically assigns a list of assignee based on the workflow action and jurisdiction hierarchy.
     * Retrieves jurisdiction boundaries from the plan request and searches for matching employee assignments.
     *
     * For INITIATE actions, assigns the employee from the lowest boundary.
     * For INTERMEDIATE actions (non-ROOT_APPROVER), assigns an employee from a higher-level boundary.
     * For SEND_BACK actions, assigns the last modified user.
     *
     * The assignee is set in both the workflow and the plan request.
     *
     * @param requestInfo auth details for making internal calls
     * @param plan the plan object containing workflow and jurisdiction details
     */
    private List<String> getAssigneeForAutoAssignment(Plan plan, RequestInfo requestInfo) {
        String[] allheirarchysBoundaryCodes = plan.getBoundaryAncestralPath().split(PIPE_REGEX);
        String[] heirarchysBoundaryCodes = Arrays.copyOf(allheirarchysBoundaryCodes, allheirarchysBoundaryCodes.length - 1);

        PlanEmployeeAssignmentSearchCriteria planEmployeeAssignmentSearchCriteria =
                PlanEmployeeAssignmentSearchCriteria.builder()
                        .tenantId(plan.getTenantId())
                        .jurisdiction(Arrays.stream(heirarchysBoundaryCodes).toList())
                        .planConfigurationId(plan.getPlanConfigurationId())
                        .role(config.getPlanEstimationApproverRoles())
                        .build();

        //search for plan-employee assignments for the ancestral heirarchy codes.
        PlanEmployeeAssignmentResponse planEmployeeAssignmentResponse = planEmployeeService.search(PlanEmployeeAssignmentSearchRequest.builder()
                .planEmployeeAssignmentSearchCriteria(planEmployeeAssignmentSearchCriteria)
                .requestInfo(requestInfo).build());

        // Create a map of jurisdiction to list of employeeIds
        Map<String, List<String>> jurisdictionToEmployeeMap = planEmployeeAssignmentResponse.getPlanEmployeeAssignment().stream()
                .filter(assignment -> assignment.getJurisdiction() != null && !assignment.getJurisdiction().isEmpty())
                .flatMap(assignment -> {
                    String employeeId = assignment.getEmployeeId();
                    return assignment.getJurisdiction().stream()
                            .filter(jurisdiction -> Arrays.asList(heirarchysBoundaryCodes).contains(jurisdiction))
                            .map(jurisdiction -> new AbstractMap.SimpleEntry<>(jurisdiction, employeeId));
                })
                .collect(Collectors.groupingBy(
                        Map.Entry::getKey, // jurisdiction as the key
                        LinkedHashMap::new, // Preserve insertion order
                        Collectors.mapping(
                                Map.Entry::getValue, // employee IDs as values
                                Collectors.toList() // Collect employee IDs into a List
                        )
                ));

        List<String> assignee = null; //assignee will remain null in case terminate actions are being taken

        String action = plan.getWorkflow().getAction();
        if (config.getWfInitiateActions().contains(action)) {
            for (int i = heirarchysBoundaryCodes.length - 1; i >= 0; i--) {
                assignee = jurisdictionToEmployeeMap.get(heirarchysBoundaryCodes[i]);
                if (assignee != null)
                    break; // Stop iterating once an assignee is found
            }
        } else if (config.getWfIntermediateActions().contains(action)) {
            assignee = assignToHigherBoundaryLevel(heirarchysBoundaryCodes, plan, jurisdictionToEmployeeMap);
        }

        return assignee;
    }

    /**
     * Assigns a list of employees from a higher-level jurisdiction in the hierarchy.
     * Iterates through boundary codes, checking if they match the assignee's jurisdiction.
     * If a higher-level boundary has an assigned employee, returns that employee's ID.
     *
     * @param heirarchysBoundaryCodes boundary codes representing the hierarchy
     * @param plan the object with plan and jurisdiction details
     * @param jurisdictionToEmployeeMap map of jurisdiction codes to employee IDs
     * @return the employee ID from the higher boundary, or null if
     */
    public List<String> assignToHigherBoundaryLevel(String[] heirarchysBoundaryCodes, Plan plan, Map<String, List<String>> jurisdictionToEmployeeMap) {
        for (int i = heirarchysBoundaryCodes.length - 1; i >= 0; i--) {
            String boundaryCode = heirarchysBoundaryCodes[i];

            // Check if this boundary code is present in assigneeJurisdiction
            if (plan.getAssigneeJurisdiction().contains(boundaryCode)) {

                for (int j = i - 1; j >= 0; j--) {
                    // Check the next higher level in the hierarchy (one index above the match)
                    String higherBoundaryCode = heirarchysBoundaryCodes[j];

                    // Fetch the employeeId from the map for the higher boundary code
                    List<String> employeeId = jurisdictionToEmployeeMap.get(higherBoundaryCode);

                    // If an employee is found, set them as the assignee and break the loop
                    if (employeeId != null) {
                        return employeeId;
                    }
                }
            }
        }
        return null;
    }

    public void invokeWorkflowForStatusUpdate(BulkPlanRequest bulkPlanRequest) {
        ProcessInstanceRequest processInstanceRequest = createWorkflowRequest(bulkPlanRequest);
        ProcessInstanceResponse processInstanceResponse = callWorkflowTransition(processInstanceRequest);

        enrichPlansPostTransition(processInstanceResponse, bulkPlanRequest);
    }

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

    private ProcessInstanceRequest createWorkflowRequest(BulkPlanRequest bulkPlanRequest) {
        List<ProcessInstance> processInstanceList = new ArrayList<>();

        // Perform auto assignment
        List<String> assignee = getAssigneeForAutoAssignment(bulkPlanRequest.getPlans().get(0),
                bulkPlanRequest.getRequestInfo());

        for(Plan plan: bulkPlanRequest.getPlans()) {

            // Setting assignee for send back actions
            if (config.getWfSendBackActions().contains(plan.getWorkflow().getAction())) {
                assignee = Collections.singletonList(plan.getAuditDetails().getLastModifiedBy());
            }

            // Set assignee
            if(!ObjectUtils.isEmpty(assignee))
                plan.getWorkflow().setAssignes(assignee);

            plan.setAssignee(assignee);

            // Create process instance object from plan
            ProcessInstance processInstance = ProcessInstance.builder()
                    .businessId(plan.getId())
                    .tenantId(plan.getTenantId())
                    .businessService(PLAN_ESTIMATION_BUSINESS_SERVICE)
                    .moduleName(MODULE_NAME_VALUE)
                    .action(plan.getWorkflow().getAction())
                    .comment(plan.getWorkflow().getComments())
                    .documents(plan.getWorkflow().getDocuments())
                    .build();

            // Enrich user list for process instance
            enrichAssignesInProcessInstance(processInstance, plan.getWorkflow());

            // Add entry for bulk transition
            processInstanceList.add(processInstance);
        }

        return ProcessInstanceRequest.builder()
                .requestInfo(bulkPlanRequest.getRequestInfo())
                .processInstances(processInstanceList)
                .build();
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