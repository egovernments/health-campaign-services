package digit.web.controllers;

import digit.service.PlanEmployeeService;
import digit.web.models.*;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

@Controller
public class PlanEmployeeController {

    PlanEmployeeService planEmployeeService;

    public PlanEmployeeController(PlanEmployeeService planEmployeeService) {
        this.planEmployeeService = planEmployeeService;
    }

    /**
     * Request handler for serving plan employee assignment create requests
     * @param body
     * @return
     */
    @RequestMapping(value = "/employee/_create", method = RequestMethod.POST)
    public ResponseEntity<PlanEmployeeAssignmentResponse> create(@Parameter(in = ParameterIn.DEFAULT, description = "", schema = @Schema()) @Valid @RequestBody PlanEmployeeAssignmentRequest body) {

        PlanEmployeeAssignmentResponse response = planEmployeeService.create(body);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);

    }

    /**
     * Request handler for serving plan employee assignment search requests
     * @param body
     * @return
     */
    @RequestMapping(value = "/employee/_search", method = RequestMethod.POST)
    public ResponseEntity<PlanEmployeeAssignmentResponse> search(@Parameter(in = ParameterIn.DEFAULT, description = "", schema = @Schema()) @Valid @RequestBody PlanEmployeeAssignmentSearchRequest body) {

        PlanEmployeeAssignmentResponse response = planEmployeeService.search(body);
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    /**
     * Request handler for serving plan employee assignment update requests
     * @param body
     * @return
     */
    @RequestMapping(value = "/employee/_update", method = RequestMethod.POST)
    public ResponseEntity<PlanEmployeeAssignmentResponse> update(@Parameter(in = ParameterIn.DEFAULT, description = "", schema = @Schema()) @Valid @RequestBody PlanEmployeeAssignmentRequest body) {

        PlanEmployeeAssignmentResponse response = planEmployeeService.update(body);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }
}
