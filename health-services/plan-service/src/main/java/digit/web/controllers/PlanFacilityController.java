package digit.web.controllers;

import digit.service.PlanFacilityService;
import digit.web.models.*;
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
@RequestMapping("plan")
public class PlanFacilityController {

    private PlanFacilityService planFacilityService;

    public PlanFacilityController(PlanFacilityService planFacilityService) {
        this.planFacilityService = planFacilityService;
    }

    /**
     * Request handler for serving plan facility create requests
     *
     * @param planFacilityRequest
     * @return
     */
    @RequestMapping(value = "/facility/_create", method = RequestMethod.POST)
    public ResponseEntity<PlanFacilityResponse> createPlanFacility(@Valid @RequestBody PlanFacilityRequest planFacilityRequest) {
        PlanFacilityResponse planFacilityResponse = planFacilityService.createPlanFacility(planFacilityRequest);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(planFacilityResponse);
    }

    /**
     * Request handler for serving plan facility update requests
     *
     * @param planFacilityRequest
     * @return
     */
    @RequestMapping(value = "/facility/_update", method = RequestMethod.POST)
    public ResponseEntity<PlanFacilityResponse> updatePlanFacility(@Valid @RequestBody PlanFacilityRequest planFacilityRequest) {
        PlanFacilityResponse planFacilityResponse = planFacilityService.updatePlanFacility(planFacilityRequest);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(planFacilityResponse);
    }

    /**
     * Request handler for serving plan facility search requests
     *
     * @param planFacilityRequest
     * @return
     */
    @RequestMapping(value = "/facility/_search", method = RequestMethod.POST)
    public ResponseEntity<PlanFacilityResponse> searchPlanFacility(@Valid @RequestBody PlanFacilitySearchRequest planFacilityRequest) {
        PlanFacilityResponse planFacilityResponse = planFacilityService.searchPlanFacility(planFacilityRequest);
        return ResponseEntity.status(HttpStatus.OK).body(planFacilityResponse);
    }
}
