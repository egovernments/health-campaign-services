package digit.web.controllers;


import digit.service.PlanConfigurationService;
import digit.util.ResponseInfoFactory;
import digit.web.models.PlanConfiguration;
import digit.web.models.PlanConfigurationRequest;
import digit.web.models.PlanConfigurationResponse;
import digit.web.models.PlanConfigurationSearchRequest;
import jakarta.validation.Valid;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestMapping;
import java.util.*;

@Controller
public class PlanConfigController {

    private ObjectMapper objectMapper;

    private PlanConfigurationService planConfigurationService;

    private ResponseInfoFactory responseInfoFactory;

    @Autowired
    public PlanConfigController(ObjectMapper objectMapper, PlanConfigurationService planConfigurationService, ResponseInfoFactory responseInfoFactory) {
        this.objectMapper = objectMapper;
        this.planConfigurationService = planConfigurationService;
        this.responseInfoFactory = responseInfoFactory;
    }

    /**
     * Request handler for serving plan configuration create requests
     * @param body
     * @return
     */
    @RequestMapping(value = "/config/_create", method = RequestMethod.POST)
    public ResponseEntity<PlanConfigurationResponse> configCreatePost(@Parameter(in = ParameterIn.DEFAULT, description = "", schema = @Schema()) @Valid @RequestBody PlanConfigurationRequest body) {

        PlanConfigurationRequest planConfigurationRequest = planConfigurationService.create(body);
        PlanConfigurationResponse response = PlanConfigurationResponse.builder()
                .planConfiguration(Collections.singletonList(planConfigurationRequest.getPlanConfiguration()))
                .responseInfo(responseInfoFactory
                        .createResponseInfoFromRequestInfo(body.getRequestInfo(), true))
                .build();

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);

    }

    /**
     * Request handler for serving plan configuration search requests
     * @param body
     * @return
     */
    @RequestMapping(value = "/config/_search", method = RequestMethod.POST)
    public ResponseEntity<PlanConfigurationResponse> configSearchPost(@Parameter(in = ParameterIn.DEFAULT, description = "", schema = @Schema()) @Valid @RequestBody PlanConfigurationSearchRequest body) {
        PlanConfigurationResponse response = planConfigurationService.search(body);
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    /**
     * Request handler for serving plan configuration update requests
     * @param body
     * @return
     */
    @RequestMapping(value = "/config/_update", method = RequestMethod.POST)
    public ResponseEntity<PlanConfigurationResponse> configUpdatePost(@Parameter(in = ParameterIn.DEFAULT, description = "", schema = @Schema()) @Valid @RequestBody PlanConfigurationRequest body) {
        PlanConfigurationResponse response = planConfigurationService.update(body);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }
}
