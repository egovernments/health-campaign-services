package digit.util;

import digit.config.Configuration;
import digit.service.PlanEmployeeService;
import digit.web.models.*;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.models.Workflow;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.contract.request.User;
import org.egov.common.contract.workflow.ProcessInstance;
import org.egov.common.contract.workflow.ProcessInstanceRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;

import java.util.*;
import java.util.stream.Collectors;

import static digit.config.ServiceConstants.*;

@Slf4j
@Component
public class WorkflowUtil {

    private Configuration config;

    private PlanEmployeeService planEmployeeService;

    public WorkflowUtil(Configuration config, PlanEmployeeService planEmployeeService) {
        this.config = config;
        this.planEmployeeService = planEmployeeService;
    }

    /**
     * Creates a workflow request for processing bulk plans.
     *
     * @param bulkPlanRequest The request containing multiple plans and request information.
     * @return A {@link ProcessInstanceRequest} containing the process instances for workflow transition.
     */
    public ProcessInstanceRequest createWorkflowRequest(BulkPlanRequest bulkPlanRequest) {
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

        log.info("Process Instance assignes - " + processInstance.getAssignes());
        return ProcessInstanceRequest.builder()
                .requestInfo(planRequest.getRequestInfo())
                .processInstances(Collections.singletonList(processInstance))
                .build();
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
     * Enriches the process instance with assignees from the given workflow.
     *
     * @param processInstance The process instance to enrich with assignees.
     * @param workflow        The workflow containing assignees to be added to the process instance.
     */
    private void enrichAssignesInProcessInstance(ProcessInstance processInstance, Workflow workflow) {
        List<User> userList = CollectionUtils.isEmpty(workflow.getAssignes())
                ? new LinkedList<>()
                : workflow.getAssignes().stream()
                .map(assignee -> User.builder().uuid(assignee).build())
                .toList();

        processInstance.setAssignes(userList);
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
    private List<String> assignToHigherBoundaryLevel(String[] heirarchysBoundaryCodes, Plan plan, Map<String, List<String>> jurisdictionToEmployeeMap) {
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

}
