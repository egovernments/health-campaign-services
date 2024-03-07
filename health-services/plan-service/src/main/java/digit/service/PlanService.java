package digit.service;

import digit.repository.PlanRepository;
import digit.web.models.PlanRequest;
import digit.web.models.PlanResponse;
import org.egov.common.utils.ResponseInfoUtil;
import org.springframework.stereotype.Service;

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

        planRepository.create(body);

        return PlanResponse.builder()
                .responseInfo(ResponseInfoUtil.createResponseInfoFromRequestInfo(body.getRequestInfo(), Boolean.TRUE))
                .plan(Collections.singletonList(body.getPlan()))
                .build();
    }
}
