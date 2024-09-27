package digit.service;

import digit.repository.PlanFacilityRepository;
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
     * This method processes the requests that come for searching plan facilities.
     *
     * @param planFacilitySearchRequest containing the search criteria and request information.
     * @return PlanFacilityResponse object containing the search results and response information.
     */
    public PlanFacilityResponse searchPlanFacility(PlanFacilitySearchRequest planFacilitySearchRequest) {

        // search validations
        if (planFacilitySearchRequest == null || planFacilitySearchRequest.getPlanFacilitySearchCriteria() == null) {
            throw new IllegalArgumentException("Search request or criteria cannot be null");
        }
        else if (planFacilitySearchRequest.getPlanFacilitySearchCriteria().getTenantId().isEmpty()) {
            throw new IllegalArgumentException("Tenant Id cannot be null");
        }
        else if (planFacilitySearchRequest.getPlanFacilitySearchCriteria().getPlanConfigurationId().isEmpty()) {
            throw new IllegalArgumentException("Plan Configuration ID cannot be null");
        }

        List<PlanFacility> planFacilityList = planFacilityRepository.search(planFacilitySearchRequest.getPlanFacilitySearchCriteria());

        // Build and return response back to controller
        return PlanFacilityResponse.builder()
                .responseInfo(ResponseInfoUtil.createResponseInfoFromRequestInfo(planFacilitySearchRequest.getRequestInfo(), Boolean.TRUE))
                .planFacility(planFacilityList)
                .build();
    }

}
