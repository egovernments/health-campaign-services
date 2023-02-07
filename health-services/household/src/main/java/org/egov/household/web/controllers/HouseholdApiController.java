package org.egov.household.web.controllers;


import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.annotations.ApiParam;
import org.egov.common.contract.response.ResponseInfo;
import org.egov.common.producer.Producer;
import org.egov.common.utils.CommonUtils;
import org.egov.common.utils.ResponseInfoFactory;
import org.egov.household.config.HouseholdConfiguration;
import org.egov.household.service.HouseholdMemberService;
import org.egov.household.service.HouseholdService;
import org.egov.household.web.models.Household;
import org.egov.household.web.models.HouseholdBulkRequest;
import org.egov.household.web.models.HouseholdBulkResponse;
import org.egov.household.web.models.HouseholdMember;
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
import java.util.List;

@javax.annotation.Generated(value = "org.egov.codegen.SpringBootCodegen", date = "2022-12-21T13:41:16.379+05:30")

@Controller
@RequestMapping("")
@Validated
public class HouseholdApiController {

    private final ObjectMapper objectMapper;

    private final HttpServletRequest httpServletRequest;

    private final HouseholdService householdService;

    private final HouseholdMemberService householdMemberService;

    private final Producer producer;

    private final HouseholdConfiguration householdConfiguration;


    @Autowired
    public HouseholdApiController(ObjectMapper objectMapper, HttpServletRequest request, HouseholdService householdService, HouseholdMemberService householdMemberService, Producer producer, HouseholdConfiguration householdConfiguration) {
        this.objectMapper = objectMapper;
        this.httpServletRequest = request;
        this.householdService = householdService;
        this.householdMemberService = householdMemberService;
        this.producer = producer;
        this.householdConfiguration = householdConfiguration;
    }

    @RequestMapping(value = "/member/v1/_create", method = RequestMethod.POST)
    public ResponseEntity<HouseholdMemberResponse> householdMemberV1CreatePost(@ApiParam(value = "Capture linkage of Household to Member.", required = true) @Valid @RequestBody HouseholdMemberRequest householdMemberRequest, @ApiParam(value = "Client can specify if the resource in request body needs to be sent back in the response. This is being used to limit amount of data that needs to flow back from the server to the client in low bandwidth scenarios. Server will always send the server generated id for validated requests.", defaultValue = "true") @Valid @RequestParam(value = "echoResource", required = false, defaultValue = "true") Boolean echoResource) throws Exception {
        if (!CommonUtils.isForCreate(householdMemberRequest)) {
            throw new CustomException("INVALID_API_OPERATION",
                    String.format("API Operation %s not valid for create request", householdMemberRequest.getApiOperation()));
        }

        List<HouseholdMember> householdMembers = householdMemberService.create(householdMemberRequest);
        HouseholdMemberResponse response = HouseholdMemberResponse.builder().responseInfo(ResponseInfoFactory
                .createResponseInfo(householdMemberRequest.getRequestInfo(), true)).householdMember(householdMembers).build();

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @RequestMapping(value = "/member/v1/_search", method = RequestMethod.POST)
    public ResponseEntity<HouseholdMemberResponse> householdMemberV1SearchPost(@ApiParam(value = "Details for existing household member.", required = true) @Valid @RequestBody HouseholdMemberSearchRequest householdMemberSearchRequest, @NotNull
    @Min(0)
    @Max(1000) @ApiParam(value = "Pagination - limit records in response", required = true) @Valid @RequestParam(value = "limit", required = true) Integer limit, @NotNull
                                                                               @Min(0) @ApiParam(value = "Pagination - offset from which records should be returned in response", required = true) @Valid @RequestParam(value = "offset", required = true) Integer offset, @NotNull @ApiParam(value = "Unique id for a tenant.", required = true) @Valid @RequestParam(value = "tenantId", required = true) String tenantId, @ApiParam(value = "epoch of the time since when the changes on the object should be picked up. Search results from this parameter should include both newly created objects since this time as well as any modified objects since this time. This criterion is included to help polling clients to get the changes in system since a last time they synchronized with the platform. ") @Valid @RequestParam(value = "lastChangedSince", required = false) Long lastChangedSince, @ApiParam(value = "Used in search APIs to specify if (soft) deleted records should be included in search results.", defaultValue = "false") @Valid @RequestParam(value = "includeDeleted", required = false, defaultValue = "false") Boolean includeDeleted) {
        List<HouseholdMember> households = householdMemberService.search(householdMemberSearchRequest.getHouseholdMemberSearch(), limit, offset, tenantId, lastChangedSince, includeDeleted);
        HouseholdMemberResponse response = HouseholdMemberResponse.builder().responseInfo(ResponseInfoFactory
                                                .createResponseInfo(householdMemberSearchRequest.getRequestInfo(), true))
                                                .householdMember(households)
                                                .build();

        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @RequestMapping(value = "/member/v1/_update", method = RequestMethod.POST)
    public ResponseEntity<HouseholdMemberResponse> householdMemberV1UpdatePost(@ApiParam(value = "Linkage details for existing household member.", required = true) @Valid @RequestBody HouseholdMemberRequest householdMemberRequest, @ApiParam(value = "Client can specify if the resource in request body needs to be sent back in the response. This is being used to limit amount of data that needs to flow back from the server to the client in low bandwidth scenarios. Server will always send the server generated id for validated requests.", defaultValue = "true") @Valid @RequestParam(value = "echoResource", required = false, defaultValue = "true") Boolean echoResource) throws Exception {
        if (!CommonUtils.isForUpdate(householdMemberRequest)
                && !CommonUtils.isForDelete(householdMemberRequest)) {
            throw new CustomException("INVALID_API_OPERATION", String.format("API Operation %s not valid for update request",
                    householdMemberRequest.getApiOperation()));
        }

        List<HouseholdMember> householdMembers = householdMemberService.update(householdMemberRequest);
        HouseholdMemberResponse response = HouseholdMemberResponse.builder()
                .householdMember(householdMembers)
                .responseInfo(ResponseInfoFactory
                        .createResponseInfo(householdMemberRequest.getRequestInfo(), true))
                .build();

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @RequestMapping(value = "/v1/_create", method = RequestMethod.POST)
    public ResponseEntity<HouseholdResponse> householdV1CreatePost(@ApiParam(value = "Capture details of Household.", required = true) @Valid @RequestBody HouseholdRequest request,
                                                                   @ApiParam(value = "Client can specify if the resource in request body needs to be sent back in the response. This is being used to limit amount of data that needs to flow back from the server to the client in low bandwidth scenarios. Server will always send the server generated id for validated requests.", defaultValue = "true") @Valid @RequestParam(value = "echoResource", required = false, defaultValue = "true") Boolean echoResource) throws Exception {


        Household household = householdService.create(request);
        HouseholdResponse response = HouseholdResponse.builder().responseInfo(ResponseInfoFactory
                .createResponseInfo(request.getRequestInfo(), true)).household(household).build();

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @RequestMapping(value = "/v1/bulk/_create", method = RequestMethod.POST)
    public ResponseEntity<ResponseInfo> householdV1CreatePost(@ApiParam(value = "Capture details of Household.", required = true) @Valid @RequestBody HouseholdBulkRequest request,
                                                              @ApiParam(value = "Client can specify if the resource in request body needs to be sent back in the response. This is being used to limit amount of data that needs to flow back from the server to the client in low bandwidth scenarios. Server will always send the server generated id for validated requests.", defaultValue = "true") @Valid @RequestParam(value = "echoResource", required = false, defaultValue = "true") Boolean echoResource) throws Exception {
        request.getRequestInfo().setApiId(httpServletRequest.getRequestURI());
        producer.push(householdConfiguration.getConsumerCreateTopic(), request);

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ResponseInfoFactory
                .createResponseInfo(request.getRequestInfo(), true));
    }

    @RequestMapping(value = "/v1/_delete", method = RequestMethod.POST)
    public ResponseEntity<HouseholdResponse> householdV1DeletePost(@ApiParam(value = "Capture details of Household.", required = true) @Valid @RequestBody HouseholdRequest request,
                                                                   @ApiParam(value = "Client can specify if the resource in request body needs to be sent back in the response. This is being used to limit amount of data that needs to flow back from the server to the client in low bandwidth scenarios. Server will always send the server generated id for validated requests.", defaultValue = "true") @Valid @RequestParam(value = "echoResource", required = false, defaultValue = "true") Boolean echoResource) throws Exception {


        Household household = householdService.delete(request);
        HouseholdResponse response = HouseholdResponse.builder().responseInfo(ResponseInfoFactory
                .createResponseInfo(request.getRequestInfo(), true)).household(household).build();

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @RequestMapping(value = "/v1/bulk/_delete", method = RequestMethod.POST)
    public ResponseEntity<ResponseInfo> householdV1DeletePost(@ApiParam(value = "Capture details of Household.", required = true) @Valid @RequestBody HouseholdBulkRequest request,
                                                              @ApiParam(value = "Client can specify if the resource in request body needs to be sent back in the response. This is being used to limit amount of data that needs to flow back from the server to the client in low bandwidth scenarios. Server will always send the server generated id for validated requests.", defaultValue = "true") @Valid @RequestParam(value = "echoResource", required = false, defaultValue = "true") Boolean echoResource) throws Exception {
        request.getRequestInfo().setApiId(httpServletRequest.getRequestURI());
        producer.push(householdConfiguration.getConsumerCreateTopic(), request);

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ResponseInfoFactory
                .createResponseInfo(request.getRequestInfo(), true));
    }

    @RequestMapping(value = "/v1/_search", method = RequestMethod.POST)
    public ResponseEntity<HouseholdBulkResponse> householdV1SearchPost(@ApiParam(value = "Details for existing household.", required = true) @Valid @RequestBody HouseholdSearchRequest request,
                                                                   @NotNull @Min(0) @Max(1000) @ApiParam(value = "Pagination - limit records in response", required = true) @Valid @RequestParam(value = "limit", required = true) Integer limit,
                                                                   @NotNull @Min(0) @ApiParam(value = "Pagination - offset from which records should be returned in response", required = true) @Valid @RequestParam(value = "offset", required = true) Integer offset,
                                                                   @NotNull @Size(min = 2, max = 1000) @ApiParam(value = "Unique id for a tenant.", required = true) @Valid @RequestParam(value = "tenantId", required = true) String tenantId,
                                                                   @ApiParam(value = "epoch of the time since when the changes on the object should be picked up. Search results from this parameter should include both newly created objects since this time as well as any modified objects since this time. This criterion is included to help polling clients to get the changes in system since a last time they synchronized with the platform. ") @Valid @RequestParam(value = "lastChangedSince", required = false) Long lastChangedSince,
                                                                   @ApiParam(value = "Used in search APIs to specify if (soft) deleted records should be included in search results.", defaultValue = "false") @Valid @RequestParam(value = "includeDeleted", required = false, defaultValue = "false") Boolean includeDeleted) {

        List<Household> households = householdService.search(request.getHousehold(), limit, offset, tenantId, lastChangedSince, includeDeleted);
        HouseholdBulkResponse response = HouseholdBulkResponse.builder().responseInfo(ResponseInfoFactory
                .createResponseInfo(request.getRequestInfo(), true)).households(households).build();

        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @RequestMapping(value = "/v1/_update", method = RequestMethod.POST)
    public ResponseEntity<HouseholdResponse> householdV1UpdatePost(@ApiParam(value = "Details for existing household.", required = true) @Valid @RequestBody HouseholdRequest request, @ApiParam(value = "Client can specify if the resource in request body needs to be sent back in the response. This is being used to limit amount of data that needs to flow back from the server to the client in low bandwidth scenarios. Server will always send the server generated id for validated requests.", defaultValue = "true") @Valid @RequestParam(value = "echoResource", required = false, defaultValue = "true") Boolean echoResource) {

        Household household = householdService.update(request);
        HouseholdResponse response = HouseholdResponse.builder()
                .household(household)
                .responseInfo(ResponseInfoFactory
                        .createResponseInfo(request.getRequestInfo(), true))
                .build();

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @RequestMapping(value = "/v1/bulk/_update", method = RequestMethod.POST)
    public ResponseEntity<ResponseInfo> householdV1BulkUpdatePost(@ApiParam(value = "Details for existing household.", required = true) @Valid @RequestBody HouseholdBulkRequest request,
                                                                  @ApiParam(value = "Client can specify if the resource in request body needs to be sent back in the response. This is being used to limit amount of data that needs to flow back from the server to the client in low bandwidth scenarios. Server will always send the server generated id for validated requests.", defaultValue = "true") @Valid @RequestParam(value = "echoResource", required = false, defaultValue = "true") Boolean echoResource) {
        request.getRequestInfo().setApiId(httpServletRequest.getRequestURI());
        producer.push(householdConfiguration.getConsumerUpdateTopic(), request);

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ResponseInfoFactory
                .createResponseInfo(request.getRequestInfo(), true));
    }

}
