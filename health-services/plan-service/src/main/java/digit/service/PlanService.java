package digit.service;

import digit.web.models.PlanRequest;
import digit.web.models.PlanResponse;
import org.egov.common.utils.ResponseInfoUtil;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Service
public class PlanService {

    private PlanValidator planValidator;

    private PlanEnricher planEnricher;

    public PlanService(PlanValidator planValidator, PlanEnricher planEnricher) {
        this.planValidator = planValidator;
        this.planEnricher = planEnricher;
    }

    public PlanResponse createPlan(PlanRequest body) {

        // Validate plan create request
        planValidator.validatePlanCreate(body);

        // Enrich plan create request
        planEnricher.enrichPlanCreate(body);

        return PlanResponse.builder()
                .responseInfo(ResponseInfoUtil.createResponseInfoFromRequestInfo(body.getRequestInfo(), Boolean.TRUE))
                .plan(Collections.singletonList(body.getPlan()))
                .build();
    }
}
