package digit.web.controllers;

import digit.service.PlanFacilityService;
import digit.web.models.PlanFacilityRequest;
import digit.web.models.PlanFacilityResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import javax.validation.Valid;

@Controller
@RequestMapping("/planFacility")
public class PlanFacilityController {
    private PlanFacilityService planFacilityService;

    public PlanFacilityController(PlanFacilityService planFacilityService) {
        this.planFacilityService = planFacilityService;
    }

    /**
     * Request handler for serving plan facility update requests
     *
     * @param planFacilityRequest
     * @return
     */
    @RequestMapping(value = "/update", method = RequestMethod.POST)
    public ResponseEntity<PlanFacilityResponse> planFacilityUpdatePost(@Valid @RequestBody PlanFacilityRequest planFacilityRequest) {
        PlanFacilityResponse planFacilityResponse = planFacilityService.updatePlanFacility(planFacilityRequest);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(planFacilityResponse);
    }
}