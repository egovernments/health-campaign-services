package digit.service.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import digit.config.Configuration;
import digit.repository.ServiceRequestRepository;
import digit.util.PlanEmployeeAssignmnetUtil;
import digit.web.models.Census;
import digit.web.models.CensusRequest;
import digit.web.models.plan.PlanEmployeeAssignmentResponse;
import digit.web.models.plan.PlanEmployeeAssignmentSearchCriteria;
import digit.web.models.plan.PlanEmployeeAssignmentSearchRequest;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.models.Workflow;
import org.egov.common.contract.request.User;
import org.egov.common.contract.workflow.*;
import org.egov.common.utils.AuditDetailsEnrichmentUtil;
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

    private PlanEmployeeAssignmnetUtil planEmployeeAssignmnetUtil;

    public WorkflowService(ServiceRequestRepository serviceRequestRepository, Configuration config, ObjectMapper mapper, PlanEmployeeAssignmnetUtil planEmployeeAssignmnetUtil) {
        this.serviceRequestRepository = serviceRequestRepository;
        this.config = config;
        this.mapper = mapper;
        this.planEmployeeAssignmnetUtil = planEmployeeAssignmnetUtil;
    }

    /**
     * Integrates with the workflow for the given census request.
     * If the action is null, it does not proceed with integration.
     *
     * @param censusRequest The request containing the census object to integrate with the workflow.
     */
    public void invokeWorkflowForStatusUpdate(CensusRequest censusRequest) {
        if (ObjectUtils.isEmpty(censusRequest.getCensus().getWorkflow()))
            return;

        ProcessInstanceRequest processInstanceRequest = createWorkflowRequest(censusRequest);
        ProcessInstanceResponse processInstanceResponse = callWorkflowTransition(processInstanceRequest);

        // Setting the status back to the census object from workflow response
        censusRequest.getCensus().setStatus(processInstanceResponse.getProcessInstances().get(0).getState().getState());

        // Enrich audit details after auto assignment is complete
        censusRequest.getCensus().setAuditDetails(AuditDetailsEnrichmentUtil
                .prepareAuditDetails( censusRequest.getCensus().getAuditDetails(),  censusRequest.getRequestInfo(), Boolean.FALSE));

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
     * Creates a workflow request from the given census request.
     *
     * @param censusRequest The request containing the census to create a workflow request.
     * @return The constructed process instance request for the workflow.
     */
    public ProcessInstanceRequest createWorkflowRequest(CensusRequest censusRequest) {
        Census census = censusRequest.getCensus();
        ProcessInstance processInstance = ProcessInstance.builder()
                .businessId(census.getId())
                .tenantId(census.getTenantId())
                .businessService(CENSUS_BUSINESS_SERVICE)
                .moduleName(MODULE_NAME_VALUE)
                .action(census.getWorkflow().getAction())
                .comment(census.getWorkflow().getComments())
                .documents(census.getWorkflow().getDocuments())
                .build();

        autoAssignAssignee(census.getWorkflow(), censusRequest);
        enrichAssignesInProcessInstance(processInstance, census.getWorkflow());

        log.info("Process Instance assignes - " + processInstance.getAssignes());
        return ProcessInstanceRequest.builder()
                .requestInfo(censusRequest.getRequestInfo())
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
     * Retrieves jurisdiction boundaries from the census request and searches for matching employee assignments.
     *
     * For INITIATE actions, assigns the employee from the lowest boundary.
     * For INTERMEDIATE actions (non-ROOT_APPROVER), assigns an employee from a higher-level boundary.
     * For SEND_BACK actions, assigns the last modified user.
     *
     * The assignee is set in both the workflow and the census request.
     *
     * @param workflow the workflow object to set the assignee
     * @param censusRequest the census request containing workflow and jurisdiction details
     */
    private void autoAssignAssignee(Workflow workflow, CensusRequest censusRequest) {
        String[] allheirarchysBoundaryCodes = censusRequest.getCensus().getBoundaryAncestralPath().get(0).split(PIPE_REGEX);
        String[] heirarchysBoundaryCodes = Arrays.copyOf(allheirarchysBoundaryCodes, allheirarchysBoundaryCodes.length - 1);

        PlanEmployeeAssignmentSearchCriteria planEmployeeAssignmentSearchCriteria =
                PlanEmployeeAssignmentSearchCriteria.builder()
                        .tenantId(censusRequest.getCensus().getTenantId())
                        .jurisdiction(Arrays.stream(heirarchysBoundaryCodes).toList())
                        .planConfigurationId(censusRequest.getCensus().getSource())
                        .role(config.getAllowedCensusRoles())
                        .build();

        //search for plan-employee assignments for the ancestral heirarchy codes.
        PlanEmployeeAssignmentResponse planEmployeeAssignmentResponse = planEmployeeAssignmnetUtil.fetchPlanEmployeeAssignment(PlanEmployeeAssignmentSearchRequest.builder()
                .planEmployeeAssignmentSearchCriteria(planEmployeeAssignmentSearchCriteria)
                .requestInfo(censusRequest.getRequestInfo()).build());

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

        String action = censusRequest.getCensus().getWorkflow().getAction();
        if (config.getWfInitiateActions().contains(action)) {
            for (int i = heirarchysBoundaryCodes.length - 1; i >= 0; i--) {
                assignee = jurisdictionToEmployeeMap.get(heirarchysBoundaryCodes[i]);
                if (assignee != null)
                    break; // Stop iterating once an assignee is found
            }
        } else if (config.getWfIntermediateActions().contains(action)) {
            assignee = assignToHigherBoundaryLevel(heirarchysBoundaryCodes, censusRequest, jurisdictionToEmployeeMap);
        } else if (config.getWfSendBackActions().contains(action)) {
            assignee = censusRequest.getCensus().getAuditDetails().getLastModifiedBy();
        }

        if(!ObjectUtils.isEmpty(assignee))
            workflow.setAssignes(Collections.singletonList(assignee));

        censusRequest.getCensus().setAssignee(assignee);
    }

    /**
     * Assigns an employee from a higher-level jurisdiction in the hierarchy.
     * Iterates through boundary codes, checking if they match the assignee's jurisdiction.
     * If a higher-level boundary has an assigned employee, returns that employee's ID.
     *
     * @param heirarchysBoundaryCodes boundary codes representing the hierarchy
     * @param censusRequest the request with census and jurisdiction details
     * @param jurisdictionToEmployeeMap map of jurisdiction codes to employee IDs
     * @return the employee ID from the higher boundary, or null if
     */
    public String assignToHigherBoundaryLevel(String[] heirarchysBoundaryCodes, CensusRequest censusRequest, Map<String, String> jurisdictionToEmployeeMap) {
        for (int i = heirarchysBoundaryCodes.length - 1; i >= 0; i--) {
            String boundaryCode = heirarchysBoundaryCodes[i];

            // Check if this boundary code is present in assigneeJurisdiction
            if (censusRequest.getCensus().getAssigneeJurisdiction().contains(boundaryCode)) {

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