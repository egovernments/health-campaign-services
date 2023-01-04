package org.egov.individual.web.controllers;


import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.annotations.ApiParam;
import org.egov.common.utils.CommonUtils;
import org.egov.common.utils.ResponseInfoFactory;
import org.egov.individual.service.IndividualService;
import org.egov.individual.web.models.Individual;
import org.egov.individual.web.models.IndividualRequest;
import org.egov.individual.web.models.IndividualResponse;
import org.egov.individual.web.models.IndividualSearchRequest;
import org.egov.tracer.model.CustomException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.io.IOException;
import java.util.List;

@javax.annotation.Generated(value = "org.egov.codegen.SpringBootCodegen", date = "2022-12-27T11:47:19.561+05:30")

@Controller
@Validated
    public class IndividualApiController {

    private final IndividualService individualService;

        private final ObjectMapper objectMapper;

        private final HttpServletRequest servletRequest;

        @Autowired
        public IndividualApiController(IndividualService individualService,
                                       ObjectMapper objectMapper,
                                       HttpServletRequest servletRequest) {
            this.individualService = individualService;
            this.objectMapper = objectMapper;
        this.servletRequest = servletRequest;
        }

                @RequestMapping(value="/v1/_create", method = RequestMethod.POST)
                public ResponseEntity<IndividualResponse> individualV1CreatePost(@ApiParam(value = "Capture details of Individual." ,required=true )  @Valid @RequestBody IndividualRequest request,@ApiParam(value = "Client can specify if the resource in request body needs to be sent back in the response. This is being used to limit amount of data that needs to flow back from the server to the client in low bandwidth scenarios. Server will always send the server generated id for validated requests.", defaultValue = "true") @Valid @RequestParam(value = "echoResource", required = false, defaultValue="true") Boolean echoResource) throws Exception {
                    if (CommonUtils.isForCreate(request)) {
                        List<Individual> individuals = individualService.create(request);
                        IndividualResponse response = IndividualResponse.builder()
                                .individual(individuals)
                                .responseInfo(ResponseInfoFactory
                                        .createResponseInfo(request.getRequestInfo(), true))
                                .build();

                        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
                    } else {
                        throw new CustomException("INVALID_API_OPERATION",
                                String.format("API Operation %s not valid for create request",
                                        request.getApiOperation()));
                    }
                }

                @RequestMapping(value="/v1/_search", method = RequestMethod.POST)
                public ResponseEntity<IndividualResponse> individualV1SearchPost(@ApiParam(value = "Individual details." ,required=true )  @Valid @RequestBody IndividualSearchRequest request, @NotNull
    @Min(0)
    @Max(1000) @ApiParam(value = "Pagination - limit records in response", required = true) @Valid @RequestParam(value = "limit", required = true) Integer limit, @NotNull
    @Min(0)@ApiParam(value = "Pagination - offset from which records should be returned in response", required = true) @Valid @RequestParam(value = "offset", required = true) Integer offset, @NotNull @ApiParam(value = "Unique id for a tenant.", required = true) @Valid @Size(min = 2, max = 1000) @RequestParam(value = "tenantId", required = true) String tenantId, @ApiParam(value = "epoch of the time since when the changes on the object should be picked up. Search results from this parameter should include both newly created objects since this time as well as any modified objects since this time. This criterion is included to help polling clients to get the changes in system since a last time they synchronized with the platform. ") @Valid @RequestParam(value = "lastChangedSince", required = false) Long lastChangedSince, @ApiParam(value = "Used in search APIs to specify if (soft) deleted records should be included in search results.", defaultValue = "false") @Valid @RequestParam(value = "includeDeleted", required = false, defaultValue="false") Boolean includeDeleted) {
                    List<Individual> individuals = individualService.search(request.getIndividual(), limit, offset, tenantId,
                            lastChangedSince, includeDeleted);
                    IndividualResponse response = IndividualResponse.builder()
                            .individual(individuals)
                            .responseInfo(ResponseInfoFactory.createResponseInfo(request.getRequestInfo(), true))
                            .build();
                    return ResponseEntity.status(HttpStatus.OK).body(response);
                }

                @RequestMapping(value="/v1/_update", method = RequestMethod.POST)
                public ResponseEntity<IndividualResponse> individualV1UpdatePost(@ApiParam(value = "Details for the Individual." ,required=true )  @Valid @RequestBody IndividualRequest individual,@ApiParam(value = "Client can specify if the resource in request body needs to be sent back in the response. This is being used to limit amount of data that needs to flow back from the server to the client in low bandwidth scenarios. Server will always send the server generated id for validated requests.", defaultValue = "true") @Valid @RequestParam(value = "echoResource", required = false, defaultValue="true") Boolean echoResource) {
                        String accept = servletRequest.getHeader("Accept");
                            if (accept != null && accept.contains("application/json")) {
                            try {
                            return new ResponseEntity<IndividualResponse>(objectMapper.readValue("{  \"ResponseInfo\" : {    \"ver\" : \"ver\",    \"resMsgId\" : \"resMsgId\",    \"msgId\" : \"msgId\",    \"apiId\" : \"apiId\",    \"ts\" : 0,    \"status\" : \"SUCCESSFUL\"  },  \"Individual\" : [ {    \"fatherName\" : \"fatherName\",    \"address\" : [ {      \"locationAccuracy\" : 5962.133916683182,      \"pincode\" : \"pincode\",      \"city\" : \"city\",      \"latitude\" : 18.494211295267263,      \"locality\" : {        \"code\" : \"code\",        \"materializedPath\" : \"materializedPath\",        \"children\" : [ null, null ],        \"latitude\" : \"latitude\",        \"name\" : \"name\",        \"label\" : \"label\",        \"longitude\" : \"longitude\"      },      \"type\" : \"type\",      \"buildingName\" : \"buildingName\",      \"street\" : \"street\",      \"tenantId\" : \"tenantA\",      \"addressLine1\" : \"addressLine1\",      \"addressLine2\" : \"addressLine2\",      \"id\" : \"id\",      \"doorNo\" : \"doorNo\",      \"landmark\" : \"landmark\",      \"longitude\" : -127.23073270189397    }, {      \"locationAccuracy\" : 5962.133916683182,      \"pincode\" : \"pincode\",      \"city\" : \"city\",      \"latitude\" : 18.494211295267263,      \"locality\" : {        \"code\" : \"code\",        \"materializedPath\" : \"materializedPath\",        \"children\" : [ null, null ],        \"latitude\" : \"latitude\",        \"name\" : \"name\",        \"label\" : \"label\",        \"longitude\" : \"longitude\"      },      \"type\" : \"type\",      \"buildingName\" : \"buildingName\",      \"street\" : \"street\",      \"tenantId\" : \"tenantA\",      \"addressLine1\" : \"addressLine1\",      \"addressLine2\" : \"addressLine2\",      \"id\" : \"id\",      \"doorNo\" : \"doorNo\",      \"landmark\" : \"landmark\",      \"longitude\" : -127.23073270189397    } ],    \"gender\" : \"MALE\",    \"husbandName\" : \"husbandName\",    \"additionalFields\" : {      \"schema\" : \"HOUSEHOLD\",      \"fields\" : [ {        \"value\" : \"180\",        \"key\" : \"height\"      }, {        \"value\" : \"180\",        \"key\" : \"height\"      } ],      \"version\" : 2    },    \"rowVersion\" : { },    \"altContactNumber\" : \"altContactNumber\",    \"mobileNumber\" : \"mobileNumber\",    \"identifiers\" : [ {      \"id\" : \"ABCD-1212\",      \"type\" : \"SYSTEM_GENERATED\"    }, {      \"id\" : \"ABCD-1212\",      \"type\" : \"SYSTEM_GENERATED\"    } ],    \"photo\" : \"photo\",    \"dateOfBirth\" : \"YYYYMMDD\",    \"clientReferenceId\" : { },    \"userId\" : null,    \"bloodGroup\" : \"bloodGroup\",    \"isDeleted\" : { },    \"auditDetails\" : {      \"lastModifiedTime\" : 2,      \"createdBy\" : \"createdBy\",      \"lastModifiedBy\" : \"lastModifiedBy\",      \"createdTime\" : 5    },    \"tenantId\" : \"tenantA\",    \"name\" : {      \"otherNames\" : \"otherNames\",      \"givenName\" : \"givenName\",      \"familyName\" : \"familyName\"    },    \"id\" : { },    \"email\" : \"email\"  }, {    \"fatherName\" : \"fatherName\",    \"address\" : [ {      \"locationAccuracy\" : 5962.133916683182,      \"pincode\" : \"pincode\",      \"city\" : \"city\",      \"latitude\" : 18.494211295267263,      \"locality\" : {        \"code\" : \"code\",        \"materializedPath\" : \"materializedPath\",        \"children\" : [ null, null ],        \"latitude\" : \"latitude\",        \"name\" : \"name\",        \"label\" : \"label\",        \"longitude\" : \"longitude\"      },      \"type\" : \"type\",      \"buildingName\" : \"buildingName\",      \"street\" : \"street\",      \"tenantId\" : \"tenantA\",      \"addressLine1\" : \"addressLine1\",      \"addressLine2\" : \"addressLine2\",      \"id\" : \"id\",      \"doorNo\" : \"doorNo\",      \"landmark\" : \"landmark\",      \"longitude\" : -127.23073270189397    }, {      \"locationAccuracy\" : 5962.133916683182,      \"pincode\" : \"pincode\",      \"city\" : \"city\",      \"latitude\" : 18.494211295267263,      \"locality\" : {        \"code\" : \"code\",        \"materializedPath\" : \"materializedPath\",        \"children\" : [ null, null ],        \"latitude\" : \"latitude\",        \"name\" : \"name\",        \"label\" : \"label\",        \"longitude\" : \"longitude\"      },      \"type\" : \"type\",      \"buildingName\" : \"buildingName\",      \"street\" : \"street\",      \"tenantId\" : \"tenantA\",      \"addressLine1\" : \"addressLine1\",      \"addressLine2\" : \"addressLine2\",      \"id\" : \"id\",      \"doorNo\" : \"doorNo\",      \"landmark\" : \"landmark\",      \"longitude\" : -127.23073270189397    } ],    \"gender\" : \"MALE\",    \"husbandName\" : \"husbandName\",    \"additionalFields\" : {      \"schema\" : \"HOUSEHOLD\",      \"fields\" : [ {        \"value\" : \"180\",        \"key\" : \"height\"      }, {        \"value\" : \"180\",        \"key\" : \"height\"      } ],      \"version\" : 2    },    \"rowVersion\" : { },    \"altContactNumber\" : \"altContactNumber\",    \"mobileNumber\" : \"mobileNumber\",    \"identifiers\" : [ {      \"id\" : \"ABCD-1212\",      \"type\" : \"SYSTEM_GENERATED\"    }, {      \"id\" : \"ABCD-1212\",      \"type\" : \"SYSTEM_GENERATED\"    } ],    \"photo\" : \"photo\",    \"dateOfBirth\" : \"YYYYMMDD\",    \"clientReferenceId\" : { },    \"userId\" : null,    \"bloodGroup\" : \"bloodGroup\",    \"isDeleted\" : { },    \"auditDetails\" : {      \"lastModifiedTime\" : 2,      \"createdBy\" : \"createdBy\",      \"lastModifiedBy\" : \"lastModifiedBy\",      \"createdTime\" : 5    },    \"tenantId\" : \"tenantA\",    \"name\" : {      \"otherNames\" : \"otherNames\",      \"givenName\" : \"givenName\",      \"familyName\" : \"familyName\"    },    \"id\" : { },    \"email\" : \"email\"  } ]}", IndividualResponse.class), HttpStatus.NOT_IMPLEMENTED);
                            } catch (IOException e) {
                            return new ResponseEntity<IndividualResponse>(HttpStatus.INTERNAL_SERVER_ERROR);
                            }
                            }

                        return new ResponseEntity<IndividualResponse>(HttpStatus.NOT_IMPLEMENTED);
                }

        }
