package digit.service.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import digit.config.Configuration;
import digit.repository.ServiceRequestRepository;
import digit.util.PlanEmployeeAssignmentUtil;
import digit.web.models.BulkCensusRequest;
import digit.web.models.Census;
import digit.web.models.CensusRequest;
import digit.web.models.plan.PlanEmployeeAssignmentResponse;
import digit.web.models.plan.PlanEmployeeAssignmentSearchCriteria;
import digit.web.models.plan.PlanEmployeeAssignmentSearchRequest;
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

import java.util.*;
import java.util.stream.Collectors;

import static digit.config.ServiceConstants.*;

@Service
@Slf4j
public class WorkflowService {

    private ServiceRequestRepository serviceRequestRepository;

    private Configuration config;

    private ObjectMapper mapper;

    private PlanEmployeeAssignmentUtil planEmployeeAssignmentUtil;

    public WorkflowService(ServiceRequestRepository serviceRequestRepository, Configuration config, ObjectMapper mapper, PlanEmployeeAssignmentUtil planEmployeeAssignmentUtil) {
        this.serviceRequestRepository = serviceRequestRepository;
        this.config = config;
        this.mapper = mapper;
        this.planEmployeeAssignmentUtil = planEmployeeAssignmentUtil;
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
                .prepareAuditDetails(censusRequest.getCensus().getAuditDetails(), censusRequest.getRequestInfo(), Boolean.FALSE));

    }

    /**
     * Integrates with the workflow for the given bulk census request.
     *
     * @param request The request containing the list of census objects to integrate with the workflow.
     */
    public void invokeWorkflowForStatusUpdate(BulkCensusRequest request) {

        ProcessInstanceRequest processInstanceRequest = createWorkflowRequest(request);
        ProcessInstanceResponse processInstanceResponse = callWorkflowTransition(processInstanceRequest);

        enrichCensusPostTransition(processInstanceResponse, request);
    }

    /**
     * Enriches the census records in bulk update with audit details and workflow status.
     *
     * @param processInstanceResponse process instance response containing the current workflow status
     * @param request                 the bulk census request
     */
    private void enrichCensusPostTransition(ProcessInstanceResponse processInstanceResponse, BulkCensusRequest request) {
        // Update status and audit information post transition
        request.getCensus().forEach(census -> {
            // Update status of Census
            census.setStatus(processInstanceResponse.getProcessInstances().get(0).getState().getState());

            // Update audit information of census
            census.setAuditDetails(AuditDetailsEnrichmentUtil
                    .prepareAuditDetails(census.getAuditDetails(), request.getRequestInfo(), Boolean.FALSE));
        });
    }

    /**
     * Creates a workflow request from the given list of census records in bulk request.
     *
     * @param request The request containing the list of census to create a workflow request.
     * @return The constructed process instance request for the workflow.
     */
    private ProcessInstanceRequest createWorkflowRequest(BulkCensusRequest request) {
        List<ProcessInstance> processInstanceList = new ArrayList<>();

        // Perform auto assignment
        List<String> assignee = getAssigneeForAutoAssignment(request.getCensus().get(0), request.getRequestInfo());

        for (Census census : request.getCensus()) {
            if (config.getWfSendBackActions().contains(census.getWorkflow().getAction())) {
                assignee = Collections.singletonList(census.getAuditDetails().getLastModifiedBy());
            }

            // Set assignee
            if (!ObjectUtils.isEmpty(assignee))
                census.getWorkflow().setAssignes(assignee);

            census.setAssignee(assignee != null ? new HashSet<>(assignee) : new HashSet<>());

            // Create process instance object from census
            ProcessInstance processInstance = ProcessInstance.builder()
                    .businessId(census.getId())
                    .tenantId(census.getTenantId())
                    .businessService(CENSUS_BUSINESS_SERVICE)
                    .moduleName(MODULE_NAME_VALUE)
                    .action(census.getWorkflow().getAction())
                    .comment(census.getWorkflow().getComments())
                    .documents(census.getWorkflow().getDocuments())
                    .build();

            // Enrich user list for process instance
            enrichAssigneesInProcessInstance(processInstance, census.getWorkflow());

            // Add entry for bulk transition
            processInstanceList.add(processInstance);
        }

        return ProcessInstanceRequest.builder()
                .requestInfo(request.getRequestInfo())
                .processInstances(processInstanceList)
                .build();
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

        // Create process instance object from census
        ProcessInstance processInstance = ProcessInstance.builder()
                .businessId(census.getId())
                .tenantId(census.getTenantId())
                .businessService(CENSUS_BUSINESS_SERVICE)
                .moduleName(MODULE_NAME_VALUE)
                .action(census.getWorkflow().getAction())
                .comment(census.getWorkflow().getComments())
                .documents(census.getWorkflow().getDocuments())
                .build();

        // Perform auto assignment
        List<String> assignee = getAssigneeForAutoAssignment(census, censusRequest.getRequestInfo());

        if (config.getWfSendBackActions().contains(census.getWorkflow().getAction())) {
            assignee = Collections.singletonList(census.getAuditDetails().getLastModifiedBy());
        }

        // Set Assignee
        if (!ObjectUtils.isEmpty(assignee))
            census.getWorkflow().setAssignes(assignee);

        census.setAssignee(assignee != null ? new HashSet<>(assignee) : new HashSet<>());

        // Enrich user for process instance
        enrichAssigneesInProcessInstance(processInstance, census.getWorkflow());

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
    public void enrichAssigneesInProcessInstance(ProcessInstance processInstance, Workflow workflow) {
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
     * Returns a list of assignee based on the workflow action and jurisdiction hierarchy.
     * Retrieves jurisdiction boundaries from the census request and searches for matching employee assignments.
     *
     * For INITIATE actions, assigns the employee from the lowest boundary.
     * For INTERMEDIATE actions (non-ROOT_APPROVER), assigns an employee from a higher-level boundary.
     * For SEND_BACK actions, assigns the last modified user.
     *
     * @param census      the census object containing workflow and jurisdiction details
     * @param requestInfo the requestInfo
     */
    private List<String> getAssigneeForAutoAssignment(Census census, RequestInfo requestInfo) {
        String[] allHierarchiesBoundaryCodes = census.getBoundaryAncestralPath().get(0).split(PIPE_REGEX);
        String[] hierarchiesBoundaryCodes = Arrays.copyOf(allHierarchiesBoundaryCodes, allHierarchiesBoundaryCodes.length - 1);

        PlanEmployeeAssignmentSearchRequest planEmployeeAssignmentSearchRequest = planEmployeeAssignmentUtil.getPlanEmployeeSearchRequest(census,
                new ArrayList<>(), Arrays.stream(hierarchiesBoundaryCodes).toList(), config.getAllowedCensusRoles(), requestInfo);

        //search for plan-employee assignments for the ancestral hierarchy codes.
        PlanEmployeeAssignmentResponse planEmployeeAssignmentResponse = planEmployeeAssignmentUtil.fetchPlanEmployeeAssignment(planEmployeeAssignmentSearchRequest);

        // Create a map of jurisdiction to list of employeeIds
        Map<String, List<String>> jurisdictionToEmployeeMap = getJurisdictionToEmployeesMap(planEmployeeAssignmentResponse, hierarchiesBoundaryCodes);

        List<String> assignee = null; //assignee will remain null in case terminate actions are being taken

        String action = census.getWorkflow().getAction();
        if (config.getWfInitiateActions().contains(action)) {
            for (int i = hierarchiesBoundaryCodes.length - 1; i >= 0; i--) {
                assignee = jurisdictionToEmployeeMap.get(hierarchiesBoundaryCodes[i]);
                if (assignee != null)
                    break; // Stop iterating once an assignee is found
            }
        } else if (config.getWfIntermediateActions().contains(action)) {
            assignee = assignToHigherBoundaryLevel(hierarchiesBoundaryCodes, census, jurisdictionToEmployeeMap);
        }

        return assignee;
    }

    private Map<String, List<String>> getJurisdictionToEmployeesMap(PlanEmployeeAssignmentResponse planEmployeeAssignmentResponse, String[] hierarchiesBoundaryCodes) {

        return planEmployeeAssignmentResponse.getPlanEmployeeAssignment().stream()
                .filter(assignment -> !CollectionUtils.isEmpty(assignment.getJurisdiction()))
                .flatMap(assignment -> {
                    String employeeId = assignment.getEmployeeId();
                    return assignment.getJurisdiction().stream()
                            .filter(jurisdiction -> Arrays.asList(hierarchiesBoundaryCodes).contains(jurisdiction))
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
    }
    /**
     * Assigns a list of employees from a higher-level jurisdiction in the hierarchy.
     * Iterates through boundary codes, checking if they match the assignee's jurisdiction.
     * If a higher-level boundary has an assigned employee, returns that employee's ID.
     *
     * @param hierarchyBoundaryCodes    boundary codes representing the hierarchy
     * @param census                    the census object with jurisdiction details
     * @param jurisdictionToEmployeeMap map of jurisdiction codes to employee IDs
     * @return the list of employee IDs from the higher boundary, or null if
     */
    public List<String> assignToHigherBoundaryLevel(String[] hierarchyBoundaryCodes, Census census, Map<String, List<String>> jurisdictionToEmployeeMap) {
        for (int i = hierarchyBoundaryCodes.length - 1; i >= 0; i--) {
            String boundaryCode = hierarchyBoundaryCodes[i];

            // Check if this boundary code is present in assigneeJurisdiction
            if (census.getAssigneeJurisdiction().contains(boundaryCode)) {

                for (int j = i - 1; j >= 0; j--) {
                    // Check the next higher level in the hierarchy (one index above the match)
                    String higherBoundaryCode = hierarchyBoundaryCodes[j];

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

}