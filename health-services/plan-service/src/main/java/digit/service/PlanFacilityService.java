package digit.service;

import digit.repository.PlanFacilityRepository;
import digit.service.enrichment.PlanFacilityEnricher;
import digit.service.validator.PlanFacilityValidator;
import digit.util.ResponseInfoFactory;
import digit.web.models.PlanFacility;
import digit.web.models.PlanFacilityRequest;
import digit.web.models.PlanFacilityResponse;
import org.egov.common.utils.ResponseInfoUtil;
import digit.web.models.PlanFacilitySearchRequest;
import org.egov.common.utils.ResponseInfoUtil;
import org.springframework.stereotype.Service;
import java.util.Collections;
import java.util.List;

@Service
public class PlanFacilityService {

    private final PlanFacilityValidator planFacilityValidator;
    private final ResponseInfoFactory responseInfoFactory;
    private final PlanFacilityEnricher planFacilityEnricher;
    private final PlanFacilityRepository planFacilityRepository;

    public PlanFacilityService(PlanFacilityValidator planFacilityValidator, ResponseInfoFactory responseInfoFactory, PlanFacilityEnricher planFacilityEnricher, PlanFacilityRepository planFacilityRepository) {
        this.planFacilityValidator = planFacilityValidator;
        this.responseInfoFactory = responseInfoFactory;
        this.planFacilityEnricher = planFacilityEnricher;
        this.planFacilityRepository = planFacilityRepository;
    }

    /**
     * This method processes the requests that come for creating plan facilities.
     *
     * @param planFacilityRequest The PlanFacilityRequest containing the plan facility details for creation.
     * @return PlanFacilityResponse containing the created plan facility and response information.
     */
    public PlanFacilityResponse createPlanFacility(PlanFacilityRequest planFacilityRequest) {
        // Validate plan facility create request
        planFacilityValidator.validatePlanFacilityCreate(planFacilityRequest);

        // Enrich plan facility create request
        planFacilityEnricher.enrichPlanFacilityCreate(planFacilityRequest);

        // Delegate creation request to repository
        planFacilityRepository.create(planFacilityRequest);

        // Build and return response back to controller
        PlanFacilityResponse response = PlanFacilityResponse.builder().planFacility(Collections.singletonList(planFacilityRequest.getPlanFacility())).responseInfo(responseInfoFactory.createResponseInfoFromRequestInfo(planFacilityRequest.getRequestInfo(), true)).build();
        return response;
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

    /**
     * Processes requests for updating plan facilities.
     *
     * @param planFacilityRequest The PlanFacilityRequest containing the update information.
     * @return PlanFacilityResponse containing the updated plan facility and response information.
     */
    public PlanFacilityResponse updatePlanFacility(PlanFacilityRequest planFacilityRequest) {
        //validate plan facility request
        planFacilityValidator.validatePlanFacilityUpdate(planFacilityRequest);

        //enrich plan facility request
        planFacilityEnricher.enrichPlanFacilityUpdate(planFacilityRequest);

        //delegate update request to repository
        planFacilityRepository.update(planFacilityRequest);

        //Build and return response back to controller
        return PlanFacilityResponse.builder().
                responseInfo(ResponseInfoUtil.createResponseInfoFromRequestInfo(planFacilityRequest.getRequestInfo(), Boolean.TRUE)).
                planFacility(Collections.singletonList(planFacilityRequest.getPlanFacility())).
                build();
    }

}
