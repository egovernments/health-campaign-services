package digit.web.controllers;


import digit.service.PlanService;
import digit.web.models.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestMapping;


@Controller
@RequestMapping("/plan")
public class PlanController {

    private PlanService planService;

    public PlanController(PlanService planService) {
        this.planService = planService;
    }

    @RequestMapping(value = "/_create", method = RequestMethod.POST)
    public ResponseEntity<PlanResponse> createPost(@Valid @RequestBody PlanRequest body) {
        PlanResponse planResponse = planService.createPlan(body);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(planResponse);
    }

    @RequestMapping(value = "/_search", method = RequestMethod.POST)
    public ResponseEntity<PlanResponse> searchPost(@Valid @RequestBody PlanSearchRequest body) {
        return ResponseEntity.status(HttpStatus.OK).body(new PlanResponse());
    }

    @RequestMapping(value = "/_update", method = RequestMethod.POST)
    public ResponseEntity<PlanResponse> updatePost(@Valid @RequestBody PlanEditRequest body) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(new PlanResponse());
    }

}
