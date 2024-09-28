package digit.service;

import digit.repository.PlanFacilityRepository;
import digit.service.enrichment.PlanFacilityEnrichementService;
import digit.service.validator.PlanFacilityValidator;
import digit.util.ResponseInfoFactory;
import digit.web.models.PlanFacilityRequest;
import digit.web.models.PlanFacilityResponse;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Service
public class PlanFacilityService {

    private final PlanFacilityValidator planFacilityValidator;
    private final ResponseInfoFactory responseInfoFactory;
    private final PlanFacilityEnrichementService planFacilityEnricher;
    private final PlanFacilityRepository planFacilityRepository;

    public PlanFacilityService(PlanFacilityValidator planFacilityValidator, ResponseInfoFactory responseInfoFactory, PlanFacilityEnrichementService planFacilityEnricher, PlanFacilityRepository planFacilityRepository) {
        this.planFacilityValidator = planFacilityValidator;
        this.responseInfoFactory = responseInfoFactory;
        this.planFacilityEnricher = planFacilityEnricher;
        this.planFacilityRepository = planFacilityRepository;
    }

    /**
     * This method processes the requests that come for creating plans.
     *
     * @param planFacilityRequest
     * @return
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
}
