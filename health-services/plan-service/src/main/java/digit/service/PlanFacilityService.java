package digit.service;

import digit.repository.PlanFacilityRepository;
import digit.service.enrichment.PlanFacilityEnricher;
import digit.service.validator.PlanFacilityValidator;
import digit.web.models.PlanFacilityRequest;
import digit.web.models.PlanFacilityResponse;
import org.egov.common.utils.ResponseInfoUtil;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Service
public class PlanFacilityService {
    private PlanFacilityValidator planFacilityValidator;
    private PlanFacilityEnricher planFacilityEnricher;
    private PlanFacilityRepository planFacilityRepository;

    public PlanFacilityService(PlanFacilityValidator planFacilityValidator, PlanFacilityEnricher planFacilityEnricher, PlanFacilityRepository planFacilityRepository) {
        this.planFacilityValidator = planFacilityValidator;
        this.planFacilityEnricher = planFacilityEnricher;
        this.planFacilityRepository = planFacilityRepository;
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
        //enrich plan facilty request
        planFacilityEnricher.enrichPlanFacilityUpdate(planFacilityRequest);
        //delegate update request to repository
        planFacilityRepository.update(planFacilityRequest);
        //Build and return response back to controller
        return PlanFacilityResponse.builder()
                .responseInfo(ResponseInfoUtil.createResponseInfoFromRequestInfo(planFacilityRequest.getRequestInfo(), Boolean.TRUE)).
                planFacility(Collections.singletonList(planFacilityRequest.getPlanFacility()))
                .build();
    }
}

