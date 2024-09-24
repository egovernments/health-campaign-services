package digit.service;

import digit.repository.PlanFacilityRepository;
import digit.repository.PlanRepository;
import digit.web.models.*;
import org.egov.common.utils.ResponseInfoUtil;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PlanFacilityService {

    private PlanFacilityRepository planFacilityRepository;

    public PlanFacilityService(PlanFacilityRepository planFacilityRepository) {
        this.planFacilityRepository = planFacilityRepository;
    }

    /**
     * This method processes the requests that come for searching plans.
     * @param body
     * @return
     */
    public PlanFacilityResponse searchPlanFacility(PlanFacilitySearchRequest body) {
        // Delegate search request to repository
        List<PlanFacility> planFacilityList = planFacilityRepository.search(body.getPlanFacilitySearchCriteria());

        // Build and return response back to controller
        return PlanFacilityResponse.builder()
                .responseInfo(ResponseInfoUtil.createResponseInfoFromRequestInfo(body.getRequestInfo(), Boolean.TRUE))
                .planFacility(planFacilityList)
                .build();
    }
}
