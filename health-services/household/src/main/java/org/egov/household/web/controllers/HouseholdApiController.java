package org.egov.household.web.controllers;


import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.annotations.ApiParam;
import org.egov.common.utils.CommonUtils;
import org.egov.common.utils.ResponseInfoFactory;
import org.egov.household.service.HouseholdService;
import org.egov.household.web.models.Household;
import org.egov.household.web.models.HouseholdMemberRequest;
import org.egov.household.web.models.HouseholdMemberResponse;
import org.egov.household.web.models.HouseholdMemberSearchRequest;
import org.egov.household.web.models.HouseholdRequest;
import org.egov.household.web.models.HouseholdResponse;
import org.egov.household.web.models.HouseholdSearchRequest;
import org.egov.tracer.model.CustomException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import java.io.IOException;
import java.util.List;

@javax.annotation.Generated(value = "org.egov.codegen.SpringBootCodegen", date = "2022-12-21T13:41:16.379+05:30")

@Controller
@RequestMapping("")
public class HouseholdApiController {

    private final ObjectMapper objectMapper;

    private final HttpServletRequest request;

    private final HouseholdService householdService;
    @Autowired
    public HouseholdApiController(ObjectMapper objectMapper, HttpServletRequest request, HouseholdService householdService) {
        this.objectMapper = objectMapper;
        this.request = request;
        this.householdService = householdService;
    }

    @RequestMapping(value = "/member/v1/_create", method = RequestMethod.POST)
    public ResponseEntity<HouseholdMemberResponse> householdMemberV1CreatePost(@ApiParam(value = "Capture linkage of Household to Member.", required = true) @Valid @RequestBody HouseholdMemberRequest householdMember, @ApiParam(value = "Client can specify if the resource in request body needs to be sent back in the response. This is being used to limit amount of data that needs to flow back from the server to the client in low bandwidth scenarios. Server will always send the server generated id for validated requests.", defaultValue = "true") @Valid @RequestParam(value = "echoResource", required = false, defaultValue = "true") Boolean echoResource) {
        String accept = request.getHeader("Accept");
        if (accept != null && accept.contains("application/json")) {
            try {
                return new ResponseEntity<HouseholdMemberResponse>(objectMapper.readValue("{  \"ResponseInfo\" : {    \"ver\" : \"ver\",    \"resMsgId\" : \"resMsgId\",    \"msgId\" : \"msgId\",    \"apiId\" : \"apiId\",    \"ts\" : 0,    \"status\" : \"SUCCESSFUL\"  },  \"HouseholdMember\" : [ {    \"householdId\" : null,    \"individualClientReferenceId\" : null,    \"additionalFields\" : {      \"schema\" : \"HOUSEHOLD\",      \"fields\" : [ {        \"value\" : \"180\",        \"key\" : \"height\"      }, {        \"value\" : \"180\",        \"key\" : \"height\"      } ],      \"version\" : 2    },    \"isDeleted\" : { },    \"rowVersion\" : { },    \"auditDetails\" : {      \"lastModifiedTime\" : 2,      \"createdBy\" : \"createdBy\",      \"lastModifiedBy\" : \"lastModifiedBy\",      \"createdTime\" : 5    },    \"tenantId\" : \"tenantA\",    \"householdClientReferenceId\" : { },    \"id\" : { },    \"individualId\" : null,    \"isHeadOfHousehold\" : false  }, {    \"householdId\" : null,    \"individualClientReferenceId\" : null,    \"additionalFields\" : {      \"schema\" : \"HOUSEHOLD\",      \"fields\" : [ {        \"value\" : \"180\",        \"key\" : \"height\"      }, {        \"value\" : \"180\",        \"key\" : \"height\"      } ],      \"version\" : 2    },    \"isDeleted\" : { },    \"rowVersion\" : { },    \"auditDetails\" : {      \"lastModifiedTime\" : 2,      \"createdBy\" : \"createdBy\",      \"lastModifiedBy\" : \"lastModifiedBy\",      \"createdTime\" : 5    },    \"tenantId\" : \"tenantA\",    \"householdClientReferenceId\" : { },    \"id\" : { },    \"individualId\" : null,    \"isHeadOfHousehold\" : false  } ]}", HouseholdMemberResponse.class), HttpStatus.NOT_IMPLEMENTED);
            } catch (IOException e) {
                return new ResponseEntity<HouseholdMemberResponse>(HttpStatus.INTERNAL_SERVER_ERROR);
            }
        }

        return new ResponseEntity<HouseholdMemberResponse>(HttpStatus.NOT_IMPLEMENTED);
    }

    @RequestMapping(value = "/member/v1/_search", method = RequestMethod.POST)
    public ResponseEntity<HouseholdMemberResponse> householdMemberV1SearchPost(@ApiParam(value = "Details for existing household member.", required = true) @Valid @RequestBody HouseholdMemberSearchRequest householdMember, @NotNull
    @Min(0)
    @Max(1000) @ApiParam(value = "Pagination - limit records in response", required = true) @Valid @RequestParam(value = "limit", required = true) Integer limit, @NotNull
                                                                               @Min(0) @ApiParam(value = "Pagination - offset from which records should be returned in response", required = true) @Valid @RequestParam(value = "offset", required = true) Integer offset, @NotNull @ApiParam(value = "Unique id for a tenant.", required = true) @Valid @RequestParam(value = "tenantId", required = true) String tenantId, @ApiParam(value = "epoch of the time since when the changes on the object should be picked up. Search results from this parameter should include both newly created objects since this time as well as any modified objects since this time. This criterion is included to help polling clients to get the changes in system since a last time they synchronized with the platform. ") @Valid @RequestParam(value = "lastChangedSince", required = false) Long lastChangedSince, @ApiParam(value = "Used in search APIs to specify if (soft) deleted records should be included in search results.", defaultValue = "false") @Valid @RequestParam(value = "includeDeleted", required = false, defaultValue = "false") Boolean includeDeleted) {
        String accept = request.getHeader("Accept");
        if (accept != null && accept.contains("application/json")) {
            try {
                return new ResponseEntity<HouseholdMemberResponse>(objectMapper.readValue("{  \"ResponseInfo\" : {    \"ver\" : \"ver\",    \"resMsgId\" : \"resMsgId\",    \"msgId\" : \"msgId\",    \"apiId\" : \"apiId\",    \"ts\" : 0,    \"status\" : \"SUCCESSFUL\"  },  \"HouseholdMember\" : [ {    \"householdId\" : null,    \"individualClientReferenceId\" : null,    \"additionalFields\" : {      \"schema\" : \"HOUSEHOLD\",      \"fields\" : [ {        \"value\" : \"180\",        \"key\" : \"height\"      }, {        \"value\" : \"180\",        \"key\" : \"height\"      } ],      \"version\" : 2    },    \"isDeleted\" : { },    \"rowVersion\" : { },    \"auditDetails\" : {      \"lastModifiedTime\" : 2,      \"createdBy\" : \"createdBy\",      \"lastModifiedBy\" : \"lastModifiedBy\",      \"createdTime\" : 5    },    \"tenantId\" : \"tenantA\",    \"householdClientReferenceId\" : { },    \"id\" : { },    \"individualId\" : null,    \"isHeadOfHousehold\" : false  }, {    \"householdId\" : null,    \"individualClientReferenceId\" : null,    \"additionalFields\" : {      \"schema\" : \"HOUSEHOLD\",      \"fields\" : [ {        \"value\" : \"180\",        \"key\" : \"height\"      }, {        \"value\" : \"180\",        \"key\" : \"height\"      } ],      \"version\" : 2    },    \"isDeleted\" : { },    \"rowVersion\" : { },    \"auditDetails\" : {      \"lastModifiedTime\" : 2,      \"createdBy\" : \"createdBy\",      \"lastModifiedBy\" : \"lastModifiedBy\",      \"createdTime\" : 5    },    \"tenantId\" : \"tenantA\",    \"householdClientReferenceId\" : { },    \"id\" : { },    \"individualId\" : null,    \"isHeadOfHousehold\" : false  } ]}", HouseholdMemberResponse.class), HttpStatus.NOT_IMPLEMENTED);
            } catch (IOException e) {
                return new ResponseEntity<HouseholdMemberResponse>(HttpStatus.INTERNAL_SERVER_ERROR);
            }
        }

        return new ResponseEntity<HouseholdMemberResponse>(HttpStatus.NOT_IMPLEMENTED);
    }

    @RequestMapping(value = "/member/v1/_update", method = RequestMethod.POST)
    public ResponseEntity<HouseholdResponse> householdMemberV1UpdatePost(@ApiParam(value = "Linkage details for existing household member.", required = true) @Valid @RequestBody HouseholdMemberRequest householdMember, @ApiParam(value = "Client can specify if the resource in request body needs to be sent back in the response. This is being used to limit amount of data that needs to flow back from the server to the client in low bandwidth scenarios. Server will always send the server generated id for validated requests.", defaultValue = "true") @Valid @RequestParam(value = "echoResource", required = false, defaultValue = "true") Boolean echoResource) {
        String accept = request.getHeader("Accept");
        if (accept != null && accept.contains("application/json")) {
            try {
                return new ResponseEntity<HouseholdResponse>(objectMapper.readValue("{  \"ResponseInfo\" : {    \"ver\" : \"ver\",    \"resMsgId\" : \"resMsgId\",    \"msgId\" : \"msgId\",    \"apiId\" : \"apiId\",    \"ts\" : 0,    \"status\" : \"SUCCESSFUL\"  },  \"Household\" : [ {    \"address\" : {      \"locationAccuracy\" : 5962.133916683182,      \"pincode\" : \"pincode\",      \"city\" : \"city\",      \"latitude\" : 18.494211295267263,      \"locality\" : {        \"code\" : \"code\",        \"materializedPath\" : \"materializedPath\",        \"children\" : [ null, null ],        \"latitude\" : \"latitude\",        \"name\" : \"name\",        \"label\" : \"label\",        \"longitude\" : \"longitude\"      },      \"type\" : \"type\",      \"buildingName\" : \"buildingName\",      \"street\" : \"street\",      \"tenantId\" : \"tenantA\",      \"addressLine1\" : \"addressLine1\",      \"addressLine2\" : \"addressLine2\",      \"id\" : \"id\",      \"doorNo\" : \"doorNo\",      \"landmark\" : \"landmark\",      \"longitude\" : -127.23073270189397    },    \"additionalFields\" : {      \"schema\" : \"HOUSEHOLD\",      \"fields\" : [ {        \"value\" : \"180\",        \"key\" : \"height\"      }, {        \"value\" : \"180\",        \"key\" : \"height\"      } ],      \"version\" : 2    },    \"isDeleted\" : { },    \"rowVersion\" : { },    \"memberCount\" : \"4\",    \"auditDetails\" : {      \"lastModifiedTime\" : 2,      \"createdBy\" : \"createdBy\",      \"lastModifiedBy\" : \"lastModifiedBy\",      \"createdTime\" : 5    },    \"tenantId\" : \"tenantA\",    \"id\" : { },    \"clientReferenceId\" : { }  }, {    \"address\" : {      \"locationAccuracy\" : 5962.133916683182,      \"pincode\" : \"pincode\",      \"city\" : \"city\",      \"latitude\" : 18.494211295267263,      \"locality\" : {        \"code\" : \"code\",        \"materializedPath\" : \"materializedPath\",        \"children\" : [ null, null ],        \"latitude\" : \"latitude\",        \"name\" : \"name\",        \"label\" : \"label\",        \"longitude\" : \"longitude\"      },      \"type\" : \"type\",      \"buildingName\" : \"buildingName\",      \"street\" : \"street\",      \"tenantId\" : \"tenantA\",      \"addressLine1\" : \"addressLine1\",      \"addressLine2\" : \"addressLine2\",      \"id\" : \"id\",      \"doorNo\" : \"doorNo\",      \"landmark\" : \"landmark\",      \"longitude\" : -127.23073270189397    },    \"additionalFields\" : {      \"schema\" : \"HOUSEHOLD\",      \"fields\" : [ {        \"value\" : \"180\",        \"key\" : \"height\"      }, {        \"value\" : \"180\",        \"key\" : \"height\"      } ],      \"version\" : 2    },    \"isDeleted\" : { },    \"rowVersion\" : { },    \"memberCount\" : \"4\",    \"auditDetails\" : {      \"lastModifiedTime\" : 2,      \"createdBy\" : \"createdBy\",      \"lastModifiedBy\" : \"lastModifiedBy\",      \"createdTime\" : 5    },    \"tenantId\" : \"tenantA\",    \"id\" : { },    \"clientReferenceId\" : { }  } ]}", HouseholdResponse.class), HttpStatus.NOT_IMPLEMENTED);
            } catch (IOException e) {
                return new ResponseEntity<HouseholdResponse>(HttpStatus.INTERNAL_SERVER_ERROR);
            }
        }

        return new ResponseEntity<HouseholdResponse>(HttpStatus.NOT_IMPLEMENTED);
    }

    @RequestMapping(value = "/v1/_create", method = RequestMethod.POST)
    public ResponseEntity<HouseholdResponse> householdV1CreatePost(@ApiParam(value = "Capture details of Household.", required = true) @Valid @RequestBody HouseholdRequest request,
                                                                   @ApiParam(value = "Client can specify if the resource in request body needs to be sent back in the response. This is being used to limit amount of data that needs to flow back from the server to the client in low bandwidth scenarios. Server will always send the server generated id for validated requests.", defaultValue = "true") @Valid @RequestParam(value = "echoResource", required = false, defaultValue = "true") Boolean echoResource) throws Exception {

        if (!CommonUtils.isForCreate(request)) {
            throw new CustomException("INVALID_API_OPERATION",
                    String.format("API Operation %s not valid for create request", request.getApiOperation()));
        }

        List<Household> households = householdService.create(request);
        HouseholdResponse response = HouseholdResponse.builder().responseInfo(ResponseInfoFactory
                .createResponseInfo(request.getRequestInfo(), true)).household(households).build();

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @RequestMapping(value = "/v1/_search", method = RequestMethod.POST)
    public ResponseEntity<HouseholdResponse> householdV1SearchPost(@ApiParam(value = "Details for existing household.", required = true) @Valid @RequestBody HouseholdSearchRequest request,
                                                                   @NotNull @Min(0) @Max(1000) @ApiParam(value = "Pagination - limit records in response", required = true) @Valid @RequestParam(value = "limit", required = true) Integer limit,
                                                                   @NotNull @Min(0) @ApiParam(value = "Pagination - offset from which records should be returned in response", required = true) @Valid @RequestParam(value = "offset", required = true) Integer offset,
                                                                   @NotNull @ApiParam(value = "Unique id for a tenant.", required = true) @Valid @RequestParam(value = "tenantId", required = true) String tenantId,
                                                                   @ApiParam(value = "epoch of the time since when the changes on the object should be picked up. Search results from this parameter should include both newly created objects since this time as well as any modified objects since this time. This criterion is included to help polling clients to get the changes in system since a last time they synchronized with the platform. ") @Valid @RequestParam(value = "lastChangedSince", required = false) Long lastChangedSince,
                                                                   @ApiParam(value = "Used in search APIs to specify if (soft) deleted records should be included in search results.", defaultValue = "false") @Valid @RequestParam(value = "includeDeleted", required = false, defaultValue = "false") Boolean includeDeleted) {

        List<Household> households = householdService.search(request.getHousehold(), limit, offset, tenantId, lastChangedSince, includeDeleted);
        HouseholdResponse response = HouseholdResponse.builder().responseInfo(ResponseInfoFactory
                .createResponseInfo(request.getRequestInfo(), true)).household(households).build();

        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @RequestMapping(value = "/v1/_update", method = RequestMethod.POST)
    public ResponseEntity<HouseholdResponse> householdV1UpdatePost(@ApiParam(value = "Details for existing household.", required = true) @Valid @RequestBody HouseholdRequest request, @ApiParam(value = "Client can specify if the resource in request body needs to be sent back in the response. This is being used to limit amount of data that needs to flow back from the server to the client in low bandwidth scenarios. Server will always send the server generated id for validated requests.", defaultValue = "true") @Valid @RequestParam(value = "echoResource", required = false, defaultValue = "true") Boolean echoResource) {
        if (!CommonUtils.isForUpdate(request)
                && !CommonUtils.isForDelete(request)) {
            throw new CustomException("INVALID_API_OPERATION", String.format("API Operation %s not valid for update request",
                    request.getApiOperation()));
        }

        List<Household> households = householdService.update(request);
        HouseholdResponse response = HouseholdResponse.builder()
                .household(households)
                .responseInfo(ResponseInfoFactory
                        .createResponseInfo(request.getRequestInfo(), true))
                .build();

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

}
