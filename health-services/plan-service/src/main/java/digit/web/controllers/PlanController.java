package digit.web.controllers;


import digit.web.models.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.validation.Valid;

@javax.annotation.Generated(value = "org.egov.codegen.SpringBootCodegen", date = "2024-03-04T09:55:29.782094600+05:30[Asia/Calcutta]")
@Controller
public class PlanController {

    private final ObjectMapper objectMapper;

    public PlanController(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @RequestMapping(value = "/_create", method = RequestMethod.POST)
    public ResponseEntity<PlanSearchResponse> createPost(@Parameter(in = ParameterIn.DEFAULT, description = "", schema = @Schema()) @Valid @RequestBody PlanCreateRequest body) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(new PlanSearchResponse());
    }

    @RequestMapping(value = "/_search", method = RequestMethod.POST)
    public ResponseEntity<PlanSearchResponse> searchPost(@Parameter(in = ParameterIn.DEFAULT, description = "", schema = @Schema()) @Valid @RequestBody PlanSearchRequest body) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(new PlanSearchResponse());
    }

    @RequestMapping(value = "/_update", method = RequestMethod.POST)
    public ResponseEntity<PlanSearchResponse> updatePost(@Parameter(in = ParameterIn.DEFAULT, description = "", schema = @Schema()) @Valid @RequestBody PlanEditRequest body) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(new PlanSearchResponse());
    }

}
