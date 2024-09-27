package digit.web.controllers;


import digit.service.PlanFacilityService;
import digit.web.models.PlanFacilityResponse;
import digit.web.models.PlanFacilitySearchRequest;
import digit.web.models.PlanResponse;
import digit.web.models.PlanSearchRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

@Controller
@RequestMapping("plan")
public class PlanFacilityController {

    private PlanFacilityService planFacilityService;

    public PlanFacilityController(PlanFacilityService planFacilityService) {
        this.planFacilityService = planFacilityService;
    }

    @RequestMapping(value = "/facility/_search", method = RequestMethod.POST)
    public ResponseEntity<PlanFacilityResponse> searchPost(@Valid @RequestBody PlanFacilitySearchRequest body) {
        PlanFacilityResponse planFacilityResponse = planFacilityService.searchPlanFacility(body);
        return ResponseEntity.status(HttpStatus.OK).body(planFacilityResponse);
    }
}
