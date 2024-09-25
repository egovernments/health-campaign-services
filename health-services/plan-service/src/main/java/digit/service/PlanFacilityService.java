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
     * This method processes the requests that come for updating plans facility
     *
     * @param body
     * @return The response containing the updated plan facility.
     */
    public PlanFacilityResponse updatePlanFacility(PlanFacilityRequest body) {
        //validate plan facility request
        planFacilityValidator.validatePlanFacilityUpdate(body);
        //enrich plan facilty request
        planFacilityEnricher.enrichPlanFacilityUpdate(body);
        //delegate update request to repository
        planFacilityRepository.update(body);
        //Build and return response back to controller
        return PlanFacilityResponse.builder()
                .responseInfo(ResponseInfoUtil.createResponseInfoFromRequestInfo(body.getRequestInfo(), Boolean.TRUE)).
                planFacility(Collections.singletonList(body.getPlanFacility()))
                .build();
    }
}

