package digit.service;

import digit.web.models.PlanCreateRequest;
import digit.web.models.PlanResponse;
import org.egov.common.utils.ResponseInfoUtil;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Service
public class PlanService {

    private ResponseInfoUtil responseInfoUtil;

    public PlanService(ResponseInfoUtil responseInfoUtil) {
        this.responseInfoUtil = responseInfoUtil;
    }

    public PlanResponse createPlan(PlanCreateRequest body) {

        // Validate plan create request


        return PlanResponse.builder()
                .responseInfo(responseInfoUtil.createResponseInfoFromRequestInfo(body.getRequestInfo(), Boolean.TRUE))
                .plan(Collections.singletonList(body.getPlan()))
                .build();
    }
}
