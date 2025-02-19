package digit.web.controllers;


import digit.service.PlanService;
import digit.web.models.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestMapping;


@Validated
@Controller
@RequestMapping("/plan")
public class PlanController {

    private PlanService planService;

    public PlanController(PlanService planService) {
        this.planService = planService;
    }

    /**
     * Request handler for serving plan create requests
     * @param body
     * @return
     */
    @RequestMapping(value = "/_create", method = RequestMethod.POST)
    public ResponseEntity<PlanResponse> createPost(@Valid @RequestBody PlanRequest body) {
        PlanResponse planResponse = planService.createPlan(body);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(planResponse);
    }

    /**
     * Request handler for serving plan search requests
     * @param body
     * @return
     */
    @RequestMapping(value = "/_search", method = RequestMethod.POST)
    public ResponseEntity<PlanResponse> searchPost(@Valid @RequestBody PlanSearchRequest body) {
        PlanResponse planResponse = planService.searchPlan(body);
        return ResponseEntity.status(HttpStatus.OK).body(planResponse);
    }

    /**
     * Request handler for serving plan update requests
     * @param body
     * @return
     */
    @RequestMapping(value = "/_update", method = RequestMethod.POST)
    public ResponseEntity<PlanResponse> updatePost(@Valid @RequestBody PlanRequest body) {
        PlanResponse planResponse = planService.updatePlan(body);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(planResponse);
    }

}
