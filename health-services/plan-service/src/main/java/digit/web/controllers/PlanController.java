package digit.web.controllers;


import digit.service.PlanService;
import digit.web.models.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.validation.Valid;

@javax.annotation.Generated(value = "org.egov.codegen.SpringBootCodegen", date = "2024-03-04T09:55:29.782094600+05:30[Asia/Calcutta]")
@Controller
@RequestMapping("/plan")
public class PlanController {

    private PlanService planService;

    public PlanController(PlanService planService) {
        this.planService = planService;
    }

    @RequestMapping(value = "/_create", method = RequestMethod.POST)
    public ResponseEntity<PlanResponse> createPost(@Valid @RequestBody PlanCreateRequest body) {
        PlanResponse planResponse = planService.createPlan(body);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(new PlanResponse());
    }

    @RequestMapping(value = "/_search", method = RequestMethod.POST)
    public ResponseEntity<PlanResponse> searchPost(@Valid @RequestBody PlanSearchRequest body) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(new PlanResponse());
    }

    @RequestMapping(value = "/_update", method = RequestMethod.POST)
    public ResponseEntity<PlanResponse> updatePost(@Valid @RequestBody PlanEditRequest body) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(new PlanResponse());
    }

}
