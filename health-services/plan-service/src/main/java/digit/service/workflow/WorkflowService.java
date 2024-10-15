package digit.service.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import digit.config.Configuration;
import digit.repository.ServiceRequestRepository;
import digit.util.CommonUtil;
import digit.web.models.Plan;
import digit.web.models.PlanConfiguration;
import digit.web.models.PlanConfigurationRequest;
import digit.web.models.PlanRequest;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.models.Workflow;
import org.egov.common.contract.request.User;
import org.egov.common.contract.workflow.*;
import org.egov.tracer.model.CustomException;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;

import java.util.*;

import static digit.config.ServiceConstants.*;

@Service
@Slf4j
public class WorkflowService {

	private ServiceRequestRepository serviceRequestRepository;

	private Configuration config;

	private ObjectMapper mapper;

	private CommonUtil commonUtil;

	public WorkflowService(ServiceRequestRepository serviceRequestRepository, Configuration config, ObjectMapper mapper, CommonUtil commonUtil) {
		this.serviceRequestRepository = serviceRequestRepository;
        this.config = config;
		this.mapper = mapper;
        this.commonUtil = commonUtil;
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

		enrichAssignesInWorkflow(processInstance, planConfig.getWorkflow());

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

		enrichAssignesInWorkflow(processInstance, plan.getWorkflow());

		return ProcessInstanceRequest.builder()
				.requestInfo(planRequest.getRequestInfo())
				.processInstances(Collections.singletonList(processInstance))
				.build();
	}

	/**
	 * Enriches the process instance with assignees from the given workflow.
	 *
	 * @param processInstance The process instance to enrich with assignees.
	 * @param workflow The workflow containing assignees to be added to the process instance.
	 */
	public void enrichAssignesInWorkflow(ProcessInstance processInstance, Workflow workflow) {
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

}