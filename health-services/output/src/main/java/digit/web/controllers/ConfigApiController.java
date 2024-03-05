package digit.web.controllers;


import digit.service.PlanConfigurationService;
import digit.web.models.PlanConfigurationRequest;
import digit.web.models.PlanConfigurationResponse;
import digit.web.models.PlanConfigurationSearchRequest;
import javax.validation.Valid;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.servlet.http.HttpServletRequest;
import org.egov.common.contract.response.ResponseInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestMapping;
import java.io.IOException;
import java.util.*;

@javax.annotation.Generated(value = "org.egov.codegen.SpringBootCodegen", date = "2024-03-04T09:55:29.782094600+05:30[Asia/Calcutta]")
@Controller
@RequestMapping("/plan")
public class ConfigApiController {

    private final ObjectMapper objectMapper;

    private final HttpServletRequest request;

    private final PlanConfigurationService planConfigurationService;

    @Autowired
    public ConfigApiController(ObjectMapper objectMapper, HttpServletRequest request, PlanConfigurationService planConfigurationService) {
        this.objectMapper = objectMapper;
        this.request = request;
        this.planConfigurationService = planConfigurationService;
    }

    @RequestMapping(value = "/config/_create", method = RequestMethod.POST)
    public ResponseEntity<PlanConfigurationResponse> configCreatePost(@Parameter(in = ParameterIn.DEFAULT, description = "", schema = @Schema()) @Valid @RequestBody PlanConfigurationRequest body) {
        String accept = request.getHeader("Accept");
        if (accept != null && accept.contains("application/json")) {
            PlanConfigurationRequest planConfigurationRequest = planConfigurationService.create(body);
            PlanConfigurationResponse response = PlanConfigurationResponse.builder()
                    .planConfigurationResponse(Collections.singletonList(planConfigurationRequest.getPlanConfiguration()))
                    .responseInfo(new ResponseInfo())
                    .build();

            return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);

        }

        return new ResponseEntity<PlanConfigurationResponse>(HttpStatus.NOT_IMPLEMENTED);
    }

    @RequestMapping(value = "/config/_search", method = RequestMethod.POST)
    public ResponseEntity<PlanConfigurationResponse> configSearchPost(@Parameter(in = ParameterIn.DEFAULT, description = "", schema = @Schema()) @Valid @RequestBody PlanConfigurationSearchRequest body) {
        String accept = request.getHeader("Accept");
        if (accept != null && accept.contains("application/json")) {
            try {
                return new ResponseEntity<PlanConfigurationResponse>(objectMapper.readValue("{  \"ResponseInfo\" : {    \"ver\" : \"ver\",    \"resMsgId\" : \"resMsgId\",    \"msgId\" : \"msgId\",    \"apiId\" : \"apiId\",    \"ts\" : 0,    \"status\" : \"SUCCESSFUL\"  },  \"PlanConfigurationResponse\" : [ {    \"executionPlanId\" : \"executionPlanId\",    \"operations\" : [ {      \"output\" : \"output\",      \"input\" : \"input\",      \"assumptionValue\" : \"assumptionValue\",      \"id\" : \"046b6c7f-0b8a-43b9-b35d-6489e6daee91\",      \"operator\" : \"+\"    }, {      \"output\" : \"output\",      \"input\" : \"input\",      \"assumptionValue\" : \"assumptionValue\",      \"id\" : \"046b6c7f-0b8a-43b9-b35d-6489e6daee91\",      \"operator\" : \"+\"    } ],    \"resourceMapping\" : [ {      \"mappedFrom\" : \"mappedFrom\",      \"mappedTo\" : \"mappedTo\"    }, {      \"mappedFrom\" : \"mappedFrom\",      \"mappedTo\" : \"mappedTo\"    } ],    \"tenantId\" : \"tenantId\",    \"name\" : \"name\",    \"files\" : [ {      \"filestoreId\" : \"filestoreId\",      \"inputFileType\" : \"Excel\",      \"id\" : \"046b6c7f-0b8a-43b9-b35d-6489e6daee91\"    }, {      \"filestoreId\" : \"filestoreId\",      \"inputFileType\" : \"Excel\",      \"id\" : \"046b6c7f-0b8a-43b9-b35d-6489e6daee91\"    } ],    \"id\" : \"046b6c7f-0b8a-43b9-b35d-6489e6daee91\",    \"assumptions\" : [ {      \"id\" : \"046b6c7f-0b8a-43b9-b35d-6489e6daee91\",      \"value\" : 6.027456183070403,      \"key\" : \"key\"    }, {      \"id\" : \"046b6c7f-0b8a-43b9-b35d-6489e6daee91\",      \"value\" : 6.027456183070403,      \"key\" : \"key\"    } ]  }, {    \"executionPlanId\" : \"executionPlanId\",    \"operations\" : [ {      \"output\" : \"output\",      \"input\" : \"input\",      \"assumptionValue\" : \"assumptionValue\",      \"id\" : \"046b6c7f-0b8a-43b9-b35d-6489e6daee91\",      \"operator\" : \"+\"    }, {      \"output\" : \"output\",      \"input\" : \"input\",      \"assumptionValue\" : \"assumptionValue\",      \"id\" : \"046b6c7f-0b8a-43b9-b35d-6489e6daee91\",      \"operator\" : \"+\"    } ],    \"resourceMapping\" : [ {      \"mappedFrom\" : \"mappedFrom\",      \"mappedTo\" : \"mappedTo\"    }, {      \"mappedFrom\" : \"mappedFrom\",      \"mappedTo\" : \"mappedTo\"    } ],    \"tenantId\" : \"tenantId\",    \"name\" : \"name\",    \"files\" : [ {      \"filestoreId\" : \"filestoreId\",      \"inputFileType\" : \"Excel\",      \"id\" : \"046b6c7f-0b8a-43b9-b35d-6489e6daee91\"    }, {      \"filestoreId\" : \"filestoreId\",      \"inputFileType\" : \"Excel\",      \"id\" : \"046b6c7f-0b8a-43b9-b35d-6489e6daee91\"    } ],    \"id\" : \"046b6c7f-0b8a-43b9-b35d-6489e6daee91\",    \"assumptions\" : [ {      \"id\" : \"046b6c7f-0b8a-43b9-b35d-6489e6daee91\",      \"value\" : 6.027456183070403,      \"key\" : \"key\"    }, {      \"id\" : \"046b6c7f-0b8a-43b9-b35d-6489e6daee91\",      \"value\" : 6.027456183070403,      \"key\" : \"key\"    } ]  } ]}", PlanConfigurationResponse.class), HttpStatus.NOT_IMPLEMENTED);
            } catch (IOException e) {
                return new ResponseEntity<PlanConfigurationResponse>(HttpStatus.INTERNAL_SERVER_ERROR);
            }
        }

        return new ResponseEntity<PlanConfigurationResponse>(HttpStatus.NOT_IMPLEMENTED);
    }

    @RequestMapping(value = "/config/_update", method = RequestMethod.POST)
    public ResponseEntity<PlanConfigurationResponse> configUpdatePost(@Parameter(in = ParameterIn.DEFAULT, description = "", schema = @Schema()) @Valid @RequestBody PlanConfigurationRequest body) {
        String accept = request.getHeader("Accept");
        if (accept != null && accept.contains("application/json")) {
            try {
                return new ResponseEntity<PlanConfigurationResponse>(objectMapper.readValue("{  \"ResponseInfo\" : {    \"ver\" : \"ver\",    \"resMsgId\" : \"resMsgId\",    \"msgId\" : \"msgId\",    \"apiId\" : \"apiId\",    \"ts\" : 0,    \"status\" : \"SUCCESSFUL\"  },  \"PlanConfigurationResponse\" : [ {    \"executionPlanId\" : \"executionPlanId\",    \"operations\" : [ {      \"output\" : \"output\",      \"input\" : \"input\",      \"assumptionValue\" : \"assumptionValue\",      \"id\" : \"046b6c7f-0b8a-43b9-b35d-6489e6daee91\",      \"operator\" : \"+\"    }, {      \"output\" : \"output\",      \"input\" : \"input\",      \"assumptionValue\" : \"assumptionValue\",      \"id\" : \"046b6c7f-0b8a-43b9-b35d-6489e6daee91\",      \"operator\" : \"+\"    } ],    \"resourceMapping\" : [ {      \"mappedFrom\" : \"mappedFrom\",      \"mappedTo\" : \"mappedTo\"    }, {      \"mappedFrom\" : \"mappedFrom\",      \"mappedTo\" : \"mappedTo\"    } ],    \"tenantId\" : \"tenantId\",    \"name\" : \"name\",    \"files\" : [ {      \"filestoreId\" : \"filestoreId\",      \"inputFileType\" : \"Excel\",      \"id\" : \"046b6c7f-0b8a-43b9-b35d-6489e6daee91\"    }, {      \"filestoreId\" : \"filestoreId\",      \"inputFileType\" : \"Excel\",      \"id\" : \"046b6c7f-0b8a-43b9-b35d-6489e6daee91\"    } ],    \"id\" : \"046b6c7f-0b8a-43b9-b35d-6489e6daee91\",    \"assumptions\" : [ {      \"id\" : \"046b6c7f-0b8a-43b9-b35d-6489e6daee91\",      \"value\" : 6.027456183070403,      \"key\" : \"key\"    }, {      \"id\" : \"046b6c7f-0b8a-43b9-b35d-6489e6daee91\",      \"value\" : 6.027456183070403,      \"key\" : \"key\"    } ]  }, {    \"executionPlanId\" : \"executionPlanId\",    \"operations\" : [ {      \"output\" : \"output\",      \"input\" : \"input\",      \"assumptionValue\" : \"assumptionValue\",      \"id\" : \"046b6c7f-0b8a-43b9-b35d-6489e6daee91\",      \"operator\" : \"+\"    }, {      \"output\" : \"output\",      \"input\" : \"input\",      \"assumptionValue\" : \"assumptionValue\",      \"id\" : \"046b6c7f-0b8a-43b9-b35d-6489e6daee91\",      \"operator\" : \"+\"    } ],    \"resourceMapping\" : [ {      \"mappedFrom\" : \"mappedFrom\",      \"mappedTo\" : \"mappedTo\"    }, {      \"mappedFrom\" : \"mappedFrom\",      \"mappedTo\" : \"mappedTo\"    } ],    \"tenantId\" : \"tenantId\",    \"name\" : \"name\",    \"files\" : [ {      \"filestoreId\" : \"filestoreId\",      \"inputFileType\" : \"Excel\",      \"id\" : \"046b6c7f-0b8a-43b9-b35d-6489e6daee91\"    }, {      \"filestoreId\" : \"filestoreId\",      \"inputFileType\" : \"Excel\",      \"id\" : \"046b6c7f-0b8a-43b9-b35d-6489e6daee91\"    } ],    \"id\" : \"046b6c7f-0b8a-43b9-b35d-6489e6daee91\",    \"assumptions\" : [ {      \"id\" : \"046b6c7f-0b8a-43b9-b35d-6489e6daee91\",      \"value\" : 6.027456183070403,      \"key\" : \"key\"    }, {      \"id\" : \"046b6c7f-0b8a-43b9-b35d-6489e6daee91\",      \"value\" : 6.027456183070403,      \"key\" : \"key\"    } ]  } ]}", PlanConfigurationResponse.class), HttpStatus.NOT_IMPLEMENTED);
            } catch (IOException e) {
                return new ResponseEntity<PlanConfigurationResponse>(HttpStatus.INTERNAL_SERVER_ERROR);
            }
        }

        return new ResponseEntity<PlanConfigurationResponse>(HttpStatus.NOT_IMPLEMENTED);
    }
}
