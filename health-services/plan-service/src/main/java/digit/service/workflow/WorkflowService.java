package digit.service.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import digit.config.Configuration;
import digit.repository.ServiceRequestRepository;
import digit.web.models.PlanConfiguration;
import digit.web.models.PlanConfigurationRequest;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.models.Workflow;
import org.egov.common.contract.request.User;
import org.egov.common.contract.workflow.*;
import org.egov.tracer.model.CustomException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.*;

import static digit.config.ServiceConstants.*;
import static java.util.stream.Collectors.toList;

@Service
@Slf4j
public class WorkflowService {

	private ServiceRequestRepository serviceRequestRepository;

	private Configuration config;

	private ObjectMapper mapper;

	@Autowired
	public WorkflowService(ServiceRequestRepository serviceRequestRepository, Configuration config, ObjectMapper mapper) {
		this.serviceRequestRepository = serviceRequestRepository;
        this.config = config;
		this.mapper = mapper;
	}

	/**
	 * Integrates with the workflow for the given plan configuration request.
	 * If the action is null, it does not proceed with integration.
	 *
	 * @param planConfigurationRequest The request containing the plan configuration to integrate with the workflow.
	 */
	public void integrateWithWorkflow(PlanConfigurationRequest planConfigurationRequest) {
		if (planConfigurationRequest.getPlanConfiguration().getWorkflow().getAction() == null) {
			return;
		}

		ProcessInstanceRequest processInstanceRequest = createWorkflowRequest(planConfigurationRequest);
		ProcessInstanceResponse processInstanceResponse = callWorkflowTransition(processInstanceRequest);

		String businessId = planConfigurationRequest.getPlanConfiguration().getId();
		ProcessInstance processInstanceAfterTransition = processInstanceResponse.getProcessInstances().stream()
				.filter(processInstance -> businessId.equals(processInstance.getBusinessId()))
				.findFirst()
				.orElseThrow(() -> new CustomException(PROCESS_INSTANCE_NOT_FOUND_CODE, PROCESS_INSTANCE_NOT_FOUND_MESSAGE + businessId));

		// Setting the status back to the plan configuration object from workflow response
		planConfigurationRequest.getPlanConfiguration().setStatus(processInstanceAfterTransition.getState().getApplicationStatus());
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
			throw new CustomException(WORKFLOW_INTEGRATION_ERROR_CODE, WORKFLOW_INTEGRATION_ERROR_MESSAGE + e);
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
	 * Enriches the process instance with assignees from the given workflow.
	 *
	 * @param processInstance The process instance to enrich with assignees.
	 * @param workflow The workflow containing assignees to be added to the process instance.
	 */
	public void enrichAssignesInWorkflow(ProcessInstance processInstance, Workflow workflow) {
		List<User> userUuidList = CollectionUtils.isEmpty(workflow.getAssignes())
				? new LinkedList<>()
				: workflow.getAssignes().stream()
				.map(assignee -> User.builder().uuid(assignee).build())
				.collect(toList());

		processInstance.setAssignes(userUuidList);
	}

	/**
	 * Constructs the URI for the workflow service transition API.
	 *
	 * @return The StringBuilder containing the constructed workflow transition URI.
	 */
	private StringBuilder getWorkflowTransitionUri() {
		StringBuilder uri = new StringBuilder();
		return uri.append(config.getWfHost()).append(config.getWfTransitionPath());
	}


}