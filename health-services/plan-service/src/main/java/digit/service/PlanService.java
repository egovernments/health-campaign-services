package digit.service;

import digit.repository.PlanRepository;
import digit.service.workflow.WorkflowService;
import digit.web.models.Plan;
import digit.web.models.PlanRequest;
import digit.web.models.PlanResponse;
import digit.web.models.PlanSearchRequest;
import org.egov.common.utils.ResponseInfoUtil;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
        // Delegate search request to repository
        List<Plan> planList = planRepository.search(body.getPlanSearchCriteria());

        // Build and return response back to controller
        return PlanResponse.builder()
                .responseInfo(ResponseInfoUtil.createResponseInfoFromRequestInfo(body.getRequestInfo(), Boolean.TRUE))
                .plan(planList)
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
}
