package digit.service;

import digit.repository.PlanRepository;
import digit.service.workflow.WorkflowService;
import digit.web.models.*;
import org.egov.common.utils.ResponseInfoUtil;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
public class PlanService {

    private PlanValidator planValidator;

    private PlanEnricher planEnricher;

    private PlanRepository planRepository;

    private WorkflowService workflowService;

    public PlanService(PlanValidator planValidator, PlanEnricher planEnricher, PlanRepository planRepository, WorkflowService workflowService) {
        this.planValidator = planValidator;
        this.planEnricher = planEnricher;
        this.planRepository = planRepository;
        this.workflowService = workflowService;
    }

    /**
     * This method processes the requests that come for creating plans.
     * @param body
     * @return
     */
    public PlanResponse createPlan(PlanRequest body) {
        // Validate plan create request
        planValidator.validatePlanCreate(body);

        // Enrich plan create request
        planEnricher.enrichPlanCreate(body);

        // Call workflow transition API for status update
        workflowService.invokeWorkflowForStatusUpdate(body);

        // Delegate creation request to repository
        planRepository.create(body);

        // Build and return response back to controller
        return PlanResponse.builder()
                .responseInfo(ResponseInfoUtil.createResponseInfoFromRequestInfo(body.getRequestInfo(), Boolean.TRUE))
                .plan(Collections.singletonList(body.getPlan()))
                .build();
    }

    /**
     * This method processes the requests that come for searching plans.
     * @param body
     * @return
     */
    public PlanResponse searchPlan(PlanSearchRequest body) {
        // Enrich search request
        planEnricher.enrichSearchRequest(body);

        // Delegate search request to repository
        List<Plan> planList = planRepository.search(body.getPlanSearchCriteria());

        // Get the total count of plans for given search criteria
        Integer count = planRepository.count(body.getPlanSearchCriteria());

        // Get the status count of plans for given search criteria
        Map<String, Integer> statusCountMap = planRepository.statusCount(body);

        // Build and return response back to controller
        return PlanResponse.builder()
                .responseInfo(ResponseInfoUtil.createResponseInfoFromRequestInfo(body.getRequestInfo(), Boolean.TRUE))
                .plan(planList)
                .totalCount(count)
                .statusCount(statusCountMap)
                .build();
    }

    /**
     * This method processes the requests that come for updating plans.
     * @param body
     * @return
     */
    public PlanResponse updatePlan(PlanRequest body) {
        // Validate plan update request
        planValidator.validatePlanUpdate(body);

        // Enrich plan update request
        planEnricher.enrichPlanUpdate(body);

        // Call workflow transition API for status update
        workflowService.invokeWorkflowForStatusUpdate(body);

        // Delegate update request to repository
        planRepository.update(body);

        // Build and return response back to controller
        return PlanResponse.builder()
                .responseInfo(ResponseInfoUtil.createResponseInfoFromRequestInfo(body.getRequestInfo(), Boolean.TRUE))
                .plan(Collections.singletonList(body.getPlan()))
                .build();
    }

    /**
     * This method processes bulk update requests for plan.
     * @param bulkPlanRequest
     * @return
     */
    public PlanResponse bulkUpdate(BulkPlanRequest bulkPlanRequest) {
        // Validate bulk plan update request
        planValidator.validateBulkPlanUpdate(bulkPlanRequest);

        // Call workflow transition for updating status and assignee
        workflowService.invokeWorkflowForStatusUpdate(bulkPlanRequest);

        // Delegate bulk update request to repository
        planRepository.bulkUpdate(bulkPlanRequest);

        // Build and return response back to controller
        return PlanResponse.builder().responseInfo(ResponseInfoUtil.createResponseInfoFromRequestInfo(bulkPlanRequest.getRequestInfo(), Boolean.TRUE))
                .plan(bulkPlanRequest.getPlans())
                .build();
    }
}
