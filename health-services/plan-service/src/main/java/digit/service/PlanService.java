package digit.service;

import digit.repository.PlanRepository;
import digit.web.models.PlanRequest;
import digit.web.models.PlanResponse;
import digit.web.models.PlanSearchRequest;
import org.egov.common.utils.ResponseInfoUtil;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;

@Service
public class PlanService {

    private PlanValidator planValidator;

    private PlanEnricher planEnricher;

    private PlanRepository planRepository;

    public PlanService(PlanValidator planValidator, PlanEnricher planEnricher, PlanRepository planRepository) {
        this.planValidator = planValidator;
        this.planEnricher = planEnricher;
        this.planRepository = planRepository;
    }

    public PlanResponse createPlan(PlanRequest body) {
        // Validate plan create request
        planValidator.validatePlanCreate(body);

        // Enrich plan create request
        planEnricher.enrichPlanCreate(body);

        // Delegate creation request to repository
        planRepository.create(body);

        // Build and return response back to controller
        return PlanResponse.builder()
                .responseInfo(ResponseInfoUtil.createResponseInfoFromRequestInfo(body.getRequestInfo(), Boolean.TRUE))
                .plan(Collections.singletonList(body.getPlan()))
                .build();
    }

    public PlanResponse searchPlan(PlanSearchRequest body) {
        

        return PlanResponse.builder()
                .responseInfo(ResponseInfoUtil.createResponseInfoFromRequestInfo(body.getRequestInfo(), Boolean.TRUE))
                .plan(new ArrayList<>())
                .build();
    }
}
