package digit.web.controllers;


import digit.service.PlanConfigurationService;
import digit.web.models.PlanConfigurationRequest;
import digit.web.models.PlanConfigurationResponse;
import digit.web.models.PlanConfigurationSearchRequest;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class PlanConfigController {

    private PlanConfigurationService planConfigurationService;

    public PlanConfigController(PlanConfigurationService planConfigurationService) {
        this.planConfigurationService = planConfigurationService;
    }

    /**
     * Request handler for serving plan configuration create requests
     * @param body
     * @return
     */
    @RequestMapping(value = "/config/_create", method = RequestMethod.POST)
    public ResponseEntity<PlanConfigurationResponse> create(@Parameter(in = ParameterIn.DEFAULT, description = "", schema = @Schema()) @Valid @RequestBody PlanConfigurationRequest body) {

        PlanConfigurationResponse response = planConfigurationService.create(body);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);

    }

    /**
     * Request handler for serving plan configuration search requests
     * @param body
     * @return
     */
    @RequestMapping(value = "/config/_search", method = RequestMethod.POST)
    public ResponseEntity<PlanConfigurationResponse> search(@Parameter(in = ParameterIn.DEFAULT, description = "", schema = @Schema()) @Valid @RequestBody PlanConfigurationSearchRequest body) {
        PlanConfigurationResponse response = planConfigurationService.search(body);
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    /**
     * Request handler for serving plan configuration update requests
     * @param body
     * @return
     */
    @RequestMapping(value = "/config/_update", method = RequestMethod.POST)
    public ResponseEntity<PlanConfigurationResponse> update(@Parameter(in = ParameterIn.DEFAULT, description = "", schema = @Schema()) @Valid @RequestBody PlanConfigurationRequest body) {
        PlanConfigurationResponse response = planConfigurationService.update(body);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }
}
