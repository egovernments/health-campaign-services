package digit.service.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import digit.config.Configuration;
import digit.repository.ServiceRequestRepository;
import digit.service.PlanEmployeeService;
import digit.util.CommonUtil;
import digit.web.models.*;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.models.Workflow;
import org.egov.common.contract.request.User;
import org.egov.common.contract.workflow.*;
import org.egov.tracer.model.CustomException;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;

import java.util.*;
import java.util.stream.Collectors;

import static digit.config.ServiceConstants.*;

@Service
@Slf4j
public class WorkflowService {

	private ServiceRequestRepository serviceRequestRepository;

	private Configuration config;

	private ObjectMapper mapper;

	private CommonUtil commonUtil;

    private PlanEmployeeService planEmployeeService;

    public WorkflowService(ServiceRequestRepository serviceRequestRepository, Configuration config, ObjectMapper mapper, CommonUtil commonUtil, PlanEmployeeService planEmployeeService) {
        this.serviceRequestRepository = serviceRequestRepository;
        this.config = config;
        this.mapper = mapper;
        this.commonUtil = commonUtil;
        this.planEmployeeService = planEmployeeService;
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

        autoAssignAssignee(plan.getWorkflow(), planRequest);
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
     * Automatically assigns an assignee based on the workflow action and jurisdiction hierarchy.
     * Retrieves jurisdiction boundaries from the plan request and searches for matching employee assignments.
     *
     * For INITIATE actions, assigns the employee from the lowest boundary.
     * For INTERMEDIATE actions (non-ROOT_APPROVER), assigns an employee from a higher-level boundary.
     * For SEND_BACK actions, assigns the last modified user.
     *
     * The assignee is set in both the workflow and the plan request.
     *
     * @param workflow the workflow object to set the assignee
     * @param planRequest the plan request containing workflow and jurisdiction details
     */
    private void autoAssignAssignee(Workflow workflow, PlanRequest planRequest) {
        String[] allheirarchysBoundaryCodes = planRequest.getPlan().getBoundaryAncestralPath().split("//|");
        String[] heirarchysBoundaryCodes = Arrays.copyOf(allheirarchysBoundaryCodes, allheirarchysBoundaryCodes.length - 1);

        PlanEmployeeAssignmentSearchCriteria planEmployeeAssignmentSearchCriteria =
                PlanEmployeeAssignmentSearchCriteria.builder()
                        .tenantId(planRequest.getPlan().getTenantId())
                        .jurisdiction(Arrays.stream(heirarchysBoundaryCodes).toList())
                        .planConfigurationId(planRequest.getPlan().getPlanConfigurationId())
                        .role(config.getPlanEstimationApproverRoles())
                        .build();

        //search for plan-employee assignments for the ancestral heirarchy codes.
        PlanEmployeeAssignmentResponse planEmployeeAssignmentResponse = planEmployeeService.search(PlanEmployeeAssignmentSearchRequest.builder()
                .planEmployeeAssignmentSearchCriteria(planEmployeeAssignmentSearchCriteria)
                .requestInfo(planRequest.getRequestInfo()).build());

        // Create a map of jurisdiction to employeeId
        Map<String, String> jurisdictionToEmployeeMap = planEmployeeAssignmentResponse.getPlanEmployeeAssignment().stream()
                .filter(assignment -> assignment.getJurisdiction() != null && !assignment.getJurisdiction().isEmpty())
                .flatMap(assignment -> {
                    String employeeId = assignment.getEmployeeId();
                    return assignment.getJurisdiction().stream()
                            .filter(jurisdiction -> Arrays.asList(heirarchysBoundaryCodes).contains(jurisdiction))
                            .map(jurisdiction -> new AbstractMap.SimpleEntry<>(jurisdiction, employeeId));
                })
                .collect(Collectors.toMap(
                        Map.Entry::getKey, // jurisdiction as the key
                        Map.Entry::getValue, // employeeId as the value
                        (existing, replacement) -> existing, // Keep the first employeeId for duplicates
                        LinkedHashMap::new // Ensure insertion order is preserved
                ));

        String assignee = null; //assignee will remain null in case terminate actions are being taken
        int length = heirarchysBoundaryCodes.length;

        String action = planRequest.getPlan().getWorkflow().getAction();
        if (config.getWfInitiateActions().contains(action)) {
            if (length - 1 >= 0)
                assignee = jurisdictionToEmployeeMap.get(heirarchysBoundaryCodes[length - 1]);
        } else if (config.getWfIntermediateActions().contains(action)) {
            if (!planRequest.getRequestInfo().getUserInfo().getRoles().contains("ROOT_ESTIMATION_APPROVER"))
                assignee = assignToHigherBoundaryLevel(heirarchysBoundaryCodes, planRequest, jurisdictionToEmployeeMap);
        } else if (config.getWfSendBackActions().contains(action)) {
            assignee = planRequest.getPlan().getAuditDetails().getLastModifiedBy();
        }

        workflow.setAssignes(Collections.singletonList(assignee));
        planRequest.getPlan().setAssignee(assignee);
    }

    public String assignToHigherBoundaryLevel(String[] heirarchysBoundaryCodes, PlanRequest planRequest, Map<String, String> jurisdictionToEmployeeMap) {
        for (int i = heirarchysBoundaryCodes.length - 2; i >= 0; i--) {
            String boundaryCode = heirarchysBoundaryCodes[i];

            // Check if this boundary code is present in assigneeJurisdiction
            if (planRequest.getPlan().getAssigneeJurisdiction().contains(boundaryCode)) {

                if (i - 1 >= 0) {
                    // Check the next higher level in the hierarchy (one index above the match)
                    String higherBoundaryCode = heirarchysBoundaryCodes[i - 1];

                    // Fetch the employeeId from the map for the higher boundary code
                    String employeeId = jurisdictionToEmployeeMap.get(higherBoundaryCode);

                    // If an employee is found, set them as the assignee and break the loop
                    if (employeeId != null) {
                        return employeeId;
                    }
                }
            }
        }
        return null;
    }

}