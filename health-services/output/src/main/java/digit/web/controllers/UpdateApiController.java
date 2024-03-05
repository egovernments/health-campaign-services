package digit.web.controllers;


import digit.web.models.ErrorRes;
import digit.web.models.PlanEditRequest;
import digit.web.models.PlanSearchResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.RequestMapping;
import java.io.IOException;
import java.util.*;

import javax.validation.constraints.*;
import javax.validation.Valid;
import javax.servlet.http.HttpServletRequest;
import java.util.Optional;

@javax.annotation.Generated(value = "org.egov.codegen.SpringBootCodegen", date = "2024-03-04T09:55:29.782094600+05:30[Asia/Calcutta]")
@Controller
@RequestMapping("/plan")
public class UpdateApiController {

    private final ObjectMapper objectMapper;

    private final HttpServletRequest request;

    @Autowired
    public UpdateApiController(ObjectMapper objectMapper, HttpServletRequest request) {
        this.objectMapper = objectMapper;
        this.request = request;
    }

    @RequestMapping(value = "/_update", method = RequestMethod.POST)
    public ResponseEntity<PlanSearchResponse> updatePost(@Parameter(in = ParameterIn.DEFAULT, description = "", schema = @Schema()) @Valid @RequestBody PlanEditRequest body) {
        String accept = request.getHeader("Accept");
        if (accept != null && accept.contains("application/json")) {
            try {
                return new ResponseEntity<PlanSearchResponse>(objectMapper.readValue("{  \"ResponseInfo\" : {    \"ver\" : \"ver\",    \"resMsgId\" : \"resMsgId\",    \"msgId\" : \"msgId\",    \"apiId\" : \"apiId\",    \"ts\" : 0,    \"status\" : \"SUCCESSFUL\"  },  \"Plan\" : [ {    \"executionPlanId\" : \"executionPlanId\",    \"planConfigurationId\" : \"planConfigurationId\",    \"activities\" : [ {      \"resourceId\" : \"resourceId\",      \"plannedStartDate\" : 0,      \"tenantId\" : \"tenantId\",      \"description\" : \"description\",      \"id\" : \"id\",      \"conditions\" : [ {        \"expression\" : \"3 <= age <= 11\",        \"entityProperty\" : \"age\",        \"entity\" : \"PERSON\"      }, {        \"expression\" : \"3 <= age <= 11\",        \"entityProperty\" : \"age\",        \"entity\" : \"PERSON\"      } ],      \"plannedEndDate\" : 6,      \"dependencies\" : [ \"dependencies\", \"dependencies\" ]    }, {      \"resourceId\" : \"resourceId\",      \"plannedStartDate\" : 0,      \"tenantId\" : \"tenantId\",      \"description\" : \"description\",      \"id\" : \"id\",      \"conditions\" : [ {        \"expression\" : \"3 <= age <= 11\",        \"entityProperty\" : \"age\",        \"entity\" : \"PERSON\"      }, {        \"expression\" : \"3 <= age <= 11\",        \"entityProperty\" : \"age\",        \"entity\" : \"PERSON\"      } ],      \"plannedEndDate\" : 6,      \"dependencies\" : [ \"dependencies\", \"dependencies\" ]    } ],    \"tenantId\" : \"tenantId\",    \"locality\" : \"locality\",    \"resources\" : [ {      \"id\" : \"046b6c7f-0b8a-43b9-b35d-6489e6daee91\",      \"estimatedNumber\" : 1.4658129805029452,      \"resourceType\" : \"STAFF\"    }, {      \"id\" : \"046b6c7f-0b8a-43b9-b35d-6489e6daee91\",      \"estimatedNumber\" : 1.4658129805029452,      \"resourceType\" : \"STAFF\"    } ],    \"id\" : \"046b6c7f-0b8a-43b9-b35d-6489e6daee91\",    \"additionalDetails\" : { },    \"targets\" : [ {      \"metricDetail\" : {        \"comparator\" : \">\",        \"unit\" : \"PERCENT\",        \"value\" : 90      },      \"metric\" : \"VACCINATION_COVERAGE\",      \"id\" : \"046b6c7f-0b8a-43b9-b35d-6489e6daee91\",      \"taskId\" : \"046b6c7f-0b8a-43b9-b35d-6489e6daee91\"    }, {      \"metricDetail\" : {        \"comparator\" : \">\",        \"unit\" : \"PERCENT\",        \"value\" : 90      },      \"metric\" : \"VACCINATION_COVERAGE\",      \"id\" : \"046b6c7f-0b8a-43b9-b35d-6489e6daee91\",      \"taskId\" : \"046b6c7f-0b8a-43b9-b35d-6489e6daee91\"    } ]  }, {    \"executionPlanId\" : \"executionPlanId\",    \"planConfigurationId\" : \"planConfigurationId\",    \"activities\" : [ {      \"resourceId\" : \"resourceId\",      \"plannedStartDate\" : 0,      \"tenantId\" : \"tenantId\",      \"description\" : \"description\",      \"id\" : \"id\",      \"conditions\" : [ {        \"expression\" : \"3 <= age <= 11\",        \"entityProperty\" : \"age\",        \"entity\" : \"PERSON\"      }, {        \"expression\" : \"3 <= age <= 11\",        \"entityProperty\" : \"age\",        \"entity\" : \"PERSON\"      } ],      \"plannedEndDate\" : 6,      \"dependencies\" : [ \"dependencies\", \"dependencies\" ]    }, {      \"resourceId\" : \"resourceId\",      \"plannedStartDate\" : 0,      \"tenantId\" : \"tenantId\",      \"description\" : \"description\",      \"id\" : \"id\",      \"conditions\" : [ {        \"expression\" : \"3 <= age <= 11\",        \"entityProperty\" : \"age\",        \"entity\" : \"PERSON\"      }, {        \"expression\" : \"3 <= age <= 11\",        \"entityProperty\" : \"age\",        \"entity\" : \"PERSON\"      } ],      \"plannedEndDate\" : 6,      \"dependencies\" : [ \"dependencies\", \"dependencies\" ]    } ],    \"tenantId\" : \"tenantId\",    \"locality\" : \"locality\",    \"resources\" : [ {      \"id\" : \"046b6c7f-0b8a-43b9-b35d-6489e6daee91\",      \"estimatedNumber\" : 1.4658129805029452,      \"resourceType\" : \"STAFF\"    }, {      \"id\" : \"046b6c7f-0b8a-43b9-b35d-6489e6daee91\",      \"estimatedNumber\" : 1.4658129805029452,      \"resourceType\" : \"STAFF\"    } ],    \"id\" : \"046b6c7f-0b8a-43b9-b35d-6489e6daee91\",    \"additionalDetails\" : { },    \"targets\" : [ {      \"metricDetail\" : {        \"comparator\" : \">\",        \"unit\" : \"PERCENT\",        \"value\" : 90      },      \"metric\" : \"VACCINATION_COVERAGE\",      \"id\" : \"046b6c7f-0b8a-43b9-b35d-6489e6daee91\",      \"taskId\" : \"046b6c7f-0b8a-43b9-b35d-6489e6daee91\"    }, {      \"metricDetail\" : {        \"comparator\" : \">\",        \"unit\" : \"PERCENT\",        \"value\" : 90      },      \"metric\" : \"VACCINATION_COVERAGE\",      \"id\" : \"046b6c7f-0b8a-43b9-b35d-6489e6daee91\",      \"taskId\" : \"046b6c7f-0b8a-43b9-b35d-6489e6daee91\"    } ]  } ]}", PlanSearchResponse.class), HttpStatus.NOT_IMPLEMENTED);
            } catch (IOException e) {
                return new ResponseEntity<PlanSearchResponse>(HttpStatus.INTERNAL_SERVER_ERROR);
            }
        }

        return new ResponseEntity<PlanSearchResponse>(HttpStatus.NOT_IMPLEMENTED);
    }

}
