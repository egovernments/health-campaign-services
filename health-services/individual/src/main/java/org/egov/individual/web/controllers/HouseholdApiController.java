package org.egov.individual.web.controllers;


import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.annotations.ApiParam;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.egov.common.contract.response.ResponseInfo;
import org.egov.common.models.core.SearchResponse;
import org.egov.common.models.core.URLParams;
import org.egov.common.models.household.Household;
import org.egov.common.models.household.HouseholdBulkRequest;
import org.egov.common.models.household.HouseholdBulkResponse;
import org.egov.common.models.household.HouseholdMember;
import org.egov.common.models.household.HouseholdMemberBulkRequest;
import org.egov.common.models.household.HouseholdMemberBulkResponse;
import org.egov.common.models.household.HouseholdMemberRequest;
import org.egov.common.models.household.HouseholdMemberResponse;
import org.egov.common.models.household.HouseholdMemberSearchRequest;
import org.egov.common.models.household.HouseholdRequest;
import org.egov.common.models.household.HouseholdResponse;
import org.egov.common.models.household.HouseholdSearchRequest;
import org.egov.common.producer.Producer;
import org.egov.common.utils.ResponseInfoFactory;
import org.egov.individual.config.IndividualProperties;
import org.egov.individual.service.HouseholdMemberService;
import org.egov.individual.service.HouseholdService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;



@Controller
@RequestMapping("")
@Validated
public class HouseholdApiController {

    private final ObjectMapper objectMapper;

    private final HttpServletRequest httpServletRequest;

    private final HouseholdService householdService;

    private final HouseholdMemberService householdMemberService;

    private final Producer producer;

    private final IndividualProperties properties;


    @Autowired
    public HouseholdApiController(ObjectMapper objectMapper, HttpServletRequest request,
                                  HouseholdService householdService,
                                  HouseholdMemberService householdMemberService,
                                  Producer producer,
                                  IndividualProperties properties) {
        this.objectMapper = objectMapper;
        this.httpServletRequest = request;
        this.householdService = householdService;
        this.householdMemberService = householdMemberService;
        this.producer = producer;
        this.properties = properties;
    }

    @RequestMapping(value = "/member/v1/bulk/_create", method = RequestMethod.POST)
    public ResponseEntity<ResponseInfo> householdMemberV1BulkCreatePost(@ApiParam(value = "Capture linkage of Household to Member.", required = true) @Valid @RequestBody HouseholdMemberBulkRequest householdMemberBulkRequest, @ApiParam(value = "Client can specify if the resource in request body needs to be sent back in the response. This is being used to limit amount of data that needs to flow back from the server to the client in low bandwidth scenarios. Server will always send the server generated id for validated requests.", defaultValue = "true") @Valid @RequestParam(value = "echoResource", required = false, defaultValue = "true") Boolean echoResource) {
        householdMemberBulkRequest.getRequestInfo().setApiId(httpServletRequest.getRequestURI());
        householdMemberService.putInCache(householdMemberBulkRequest.getHouseholdMembers());
        producer.push(properties.getCreateHouseholdBulkTopic(), householdMemberBulkRequest);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ResponseInfoFactory
                .createResponseInfo(householdMemberBulkRequest.getRequestInfo(), true));
    }

    @RequestMapping(value = "/member/v1/_create", method = RequestMethod.POST)
    public ResponseEntity<HouseholdMemberResponse> householdMemberV1CreatePost(@ApiParam(value = "Capture linkage of Household to Member.", required = true) @Valid @RequestBody HouseholdMemberRequest householdMemberRequest, @ApiParam(value = "Client can specify if the resource in request body needs to be sent back in the response. This is being used to limit amount of data that needs to flow back from the server to the client in low bandwidth scenarios. Server will always send the server generated id for validated requests.", defaultValue = "true") @Valid @RequestParam(value = "echoResource", required = false, defaultValue = "true") Boolean echoResource) {
        List<HouseholdMember> householdMembers = householdMemberService.create(householdMemberRequest);
        HouseholdMemberResponse response = HouseholdMemberResponse.builder().responseInfo(ResponseInfoFactory
                .createResponseInfo(householdMemberRequest.getRequestInfo(), true))
                .householdMember(householdMembers.get(0)).build();

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @RequestMapping(value = "/member/v1/_search", method = RequestMethod.POST)
    public ResponseEntity<HouseholdMemberBulkResponse> householdMemberV1SearchPost(
            @Valid @ModelAttribute URLParams urlParams,
            @ApiParam(value = "Details for existing household member.", required = true) @Valid @RequestBody HouseholdMemberSearchRequest householdMemberSearchRequest
    ) {
        SearchResponse<HouseholdMember> searchResponse = householdMemberService.search(
                householdMemberSearchRequest.getHouseholdMemberSearch(),
                urlParams.getLimit(),
                urlParams.getOffset(),
                urlParams.getTenantId(),
                urlParams.getLastChangedSince(),
                urlParams.getIncludeDeleted()
        );
        HouseholdMemberBulkResponse response = HouseholdMemberBulkResponse.builder().responseInfo(ResponseInfoFactory
                                                .createResponseInfo(householdMemberSearchRequest.getRequestInfo(), true))
                                                .householdMembers(searchResponse.getResponse())
                                                .totalCount(searchResponse.getTotalCount())
                                                .build();

        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @RequestMapping(value = "/member/v1/bulk/_update", method = RequestMethod.POST)
    public ResponseEntity<ResponseInfo> householdMemberV1BulkUpdatePost(@ApiParam(value = "Capture linkage of Household to Member.", required = true) @Valid @RequestBody HouseholdMemberBulkRequest householdMemberBulkRequest, @ApiParam(value = "Client can specify if the resource in request body needs to be sent back in the response. This is being used to limit amount of data that needs to flow back from the server to the client in low bandwidth scenarios. Server will always send the server generated id for validated requests.", defaultValue = "true") @Valid @RequestParam(value = "echoResource", required = false, defaultValue = "true") Boolean echoResource) {
        householdMemberBulkRequest.getRequestInfo().setApiId(httpServletRequest.getRequestURI());
        producer.push(properties.getUpdateHouseholdMemberBulkTopic(), householdMemberBulkRequest);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ResponseInfoFactory
                .createResponseInfo(householdMemberBulkRequest.getRequestInfo(), true));
    }

    @RequestMapping(value = "/member/v1/_update", method = RequestMethod.POST)
    public ResponseEntity<HouseholdMemberResponse> householdMemberV1UpdatePost(@ApiParam(value = "Linkage details for existing household member.", required = true) @Valid @RequestBody HouseholdMemberRequest householdMemberRequest, @ApiParam(value = "Client can specify if the resource in request body needs to be sent back in the response. This is being used to limit amount of data that needs to flow back from the server to the client in low bandwidth scenarios. Server will always send the server generated id for validated requests.", defaultValue = "true") @Valid @RequestParam(value = "echoResource", required = false, defaultValue = "true") Boolean echoResource) {
        List<HouseholdMember> householdMembers = householdMemberService.update(householdMemberRequest);
        HouseholdMemberResponse response = HouseholdMemberResponse.builder()
                .householdMember(householdMembers.get(0))
                .responseInfo(ResponseInfoFactory
                        .createResponseInfo(householdMemberRequest.getRequestInfo(), true))
                .build();

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @RequestMapping(value = "/member/v1/bulk/_delete", method = RequestMethod.POST)
    public ResponseEntity<ResponseInfo> householdMemberV1BulkDeletePost(@ApiParam(value = "Capture linkage of Household to Member.", required = true) @Valid @RequestBody HouseholdMemberBulkRequest householdMemberBulkRequest, @ApiParam(value = "Client can specify if the resource in request body needs to be sent back in the response. This is being used to limit amount of data that needs to flow back from the server to the client in low bandwidth scenarios. Server will always send the server generated id for validated requests.", defaultValue = "true") @Valid @RequestParam(value = "echoResource", required = false, defaultValue = "true") Boolean echoResource) {
        householdMemberBulkRequest.getRequestInfo().setApiId(httpServletRequest.getRequestURI());
        producer.push(properties.getDeleteHouseholdMemberBulkTopic(), householdMemberBulkRequest);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ResponseInfoFactory
                .createResponseInfo(householdMemberBulkRequest.getRequestInfo(), true));
    }

    @RequestMapping(value = "/member/v1/_delete", method = RequestMethod.POST)
    public ResponseEntity<HouseholdMemberResponse> householdMemberV1DeletePost(@ApiParam(value = "Linkage details for existing household member.", required = true) @Valid @RequestBody HouseholdMemberRequest householdMemberRequest, @ApiParam(value = "Client can specify if the resource in request body needs to be sent back in the response. This is being used to limit amount of data that needs to flow back from the server to the client in low bandwidth scenarios. Server will always send the server generated id for validated requests.", defaultValue = "true") @Valid @RequestParam(value = "echoResource", required = false, defaultValue = "true") Boolean echoResource) {
        List<HouseholdMember> householdMembers = householdMemberService.delete(householdMemberRequest);
        HouseholdMemberResponse response = HouseholdMemberResponse.builder()
                .householdMember(householdMembers.get(0))
                .responseInfo(ResponseInfoFactory
                        .createResponseInfo(householdMemberRequest.getRequestInfo(), true))
                .build();

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @RequestMapping(value = "/v1/_create", method = RequestMethod.POST)
    public ResponseEntity<HouseholdResponse> householdV1CreatePost(@ApiParam(value = "Capture details of Household.", required = true) @Valid @RequestBody HouseholdRequest request,
                                                                   @ApiParam(value = "Client can specify if the resource in request body needs to be sent back in the response. This is being used to limit amount of data that needs to flow back from the server to the client in low bandwidth scenarios. Server will always send the server generated id for validated requests.", defaultValue = "true") @Valid @RequestParam(value = "echoResource", required = false, defaultValue = "true") Boolean echoResource) {


        Household household = householdService.create(request);
        HouseholdResponse response = HouseholdResponse.builder().responseInfo(ResponseInfoFactory
                .createResponseInfo(request.getRequestInfo(), true)).household(household).build();

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @RequestMapping(value = "/v1/bulk/_create", method = RequestMethod.POST)
    public ResponseEntity<ResponseInfo> householdV1CreatePost(@ApiParam(value = "Capture details of Household.", required = true) @Valid @RequestBody HouseholdBulkRequest request,
                                                              @ApiParam(value = "Client can specify if the resource in request body needs to be sent back in the response. This is being used to limit amount of data that needs to flow back from the server to the client in low bandwidth scenarios. Server will always send the server generated id for validated requests.", defaultValue = "true") @Valid @RequestParam(value = "echoResource", required = false, defaultValue = "true") Boolean echoResource) {
        request.getRequestInfo().setApiId(httpServletRequest.getRequestURI());
        householdService.putInCache(request.getHouseholds());
        producer.push(properties.getCreateHouseholdBulkTopic(), request);

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ResponseInfoFactory
                .createResponseInfo(request.getRequestInfo(), true));
    }

    @RequestMapping(value = "/v1/_delete", method = RequestMethod.POST)
    public ResponseEntity<HouseholdResponse> householdV1DeletePost(@ApiParam(value = "Capture details of Household.", required = true) @Valid @RequestBody HouseholdRequest request,
                                                                   @ApiParam(value = "Client can specify if the resource in request body needs to be sent back in the response. This is being used to limit amount of data that needs to flow back from the server to the client in low bandwidth scenarios. Server will always send the server generated id for validated requests.", defaultValue = "true") @Valid @RequestParam(value = "echoResource", required = false, defaultValue = "true") Boolean echoResource) {


        Household household = householdService.delete(request);
        HouseholdResponse response = HouseholdResponse.builder().responseInfo(ResponseInfoFactory
                .createResponseInfo(request.getRequestInfo(), true)).household(household).build();

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @RequestMapping(value = "/v1/bulk/_delete", method = RequestMethod.POST)
    public ResponseEntity<ResponseInfo> householdV1DeletePost(@ApiParam(value = "Capture details of Household.", required = true) @Valid @RequestBody HouseholdBulkRequest request,
                                                              @ApiParam(value = "Client can specify if the resource in request body needs to be sent back in the response. This is being used to limit amount of data that needs to flow back from the server to the client in low bandwidth scenarios. Server will always send the server generated id for validated requests.", defaultValue = "true") @Valid @RequestParam(value = "echoResource", required = false, defaultValue = "true") Boolean echoResource) {
        request.getRequestInfo().setApiId(httpServletRequest.getRequestURI());
        producer.push(properties.getDeleteHouseholdBulkTopic(), request);

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ResponseInfoFactory
                .createResponseInfo(request.getRequestInfo(), true));
    }

    @RequestMapping(value = "/v1/_search", method = RequestMethod.POST)
    public ResponseEntity<HouseholdBulkResponse> householdV1SearchPost(
            @Valid @ModelAttribute URLParams urlParams,
            @ApiParam(value = "Details for existing household.", required = true) @Valid @RequestBody HouseholdSearchRequest request
    ) {
        SearchResponse<Household> searchResponse = householdService.search(
                request.getHousehold(),
                urlParams.getLimit(),
                urlParams.getOffset(),
                urlParams.getTenantId(),
                urlParams.getLastChangedSince(),
                urlParams.getIncludeDeleted()
        );
        HouseholdBulkResponse response = HouseholdBulkResponse.builder()
                .responseInfo(
                        ResponseInfoFactory.createResponseInfo(
                                request.getRequestInfo(), true
                        )
                ).totalCount(searchResponse.getTotalCount())
                .households(searchResponse.getResponse()).build();

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
        producer.push(properties.getUpdateHouseholdBulkTopic(), request);

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ResponseInfoFactory
                .createResponseInfo(request.getRequestInfo(), true));
    }

}
