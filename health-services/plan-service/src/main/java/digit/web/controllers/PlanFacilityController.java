package digit.web.controllers;

import digit.service.PlanFacilityService;
import digit.web.models.PlanFacilityRequest;
import digit.web.models.PlanFacilityResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

@Validated
@Controller
@RequestMapping("/plan/facility")
public class PlanFacilityController {

    private final PlanFacilityService planFacilityService;

    public PlanFacilityController(PlanFacilityService planFacilityService) {
        this.planFacilityService = planFacilityService;
    }

    /**
     * Request handler for serving plan facility create requests
     *
     * @param planFacilityRequest
     * @return
     */
    @RequestMapping(value = "/_create", method = RequestMethod.POST)
    public ResponseEntity<PlanFacilityResponse> createPlanFacility(@Valid @RequestBody PlanFacilityRequest planFacilityRequest) {
        PlanFacilityResponse planFacilityResponse = planFacilityService.createPlanFacility(planFacilityRequest);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(planFacilityResponse);
    }
}
