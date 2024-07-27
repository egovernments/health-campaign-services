package org.egov.facility.web.controllers;


import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.annotations.ApiParam;
import org.egov.common.contract.response.ResponseInfo;
import org.egov.common.models.facility.*;
import org.egov.common.producer.Producer;
import org.egov.common.utils.ResponseInfoFactory;
import org.egov.facility.config.FacilityConfiguration;
import org.egov.facility.service.FacilityService;
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
import java.util.List;

@javax.annotation.Generated(value = "org.egov.codegen.SpringBootCodegen", date = "2023-02-21T14:37:54.683+05:30")

@Controller
@RequestMapping("")
public class FacilityApiController {

    private final ObjectMapper objectMapper;

    private final HttpServletRequest httpServletRequest;

    private final FacilityService facilityService;

    private final Producer producer;

    private final FacilityConfiguration facilityConfiguration;

    @Autowired
    public FacilityApiController(ObjectMapper objectMapper,
                                 HttpServletRequest httpServletRequest,
                                 FacilityService facilityService,
                                 Producer producer,
                                 FacilityConfiguration facilityConfiguration) {
        this.objectMapper = objectMapper;
        this.httpServletRequest = httpServletRequest;
        this.facilityService = facilityService;
        this.producer = producer;
        this.facilityConfiguration = facilityConfiguration;
    }

    @RequestMapping(value = "/v1/bulk/_create", method = RequestMethod.POST)
    public ResponseEntity<ResponseInfo> facilityV1BulkCreatePost(@ApiParam(value = "Capture details of Facility.", required = true) @Valid @RequestBody FacilityBulkRequest request) {
        request.getRequestInfo().setApiId(httpServletRequest.getRequestURI());
        producer.push(facilityConfiguration.getBulkCreateFacilityTopic(), request);

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ResponseInfoFactory
                .createResponseInfo(request.getRequestInfo(), true));
    }

    @RequestMapping(value = "/v1/bulk/_delete", method = RequestMethod.POST)
    public ResponseEntity<ResponseInfo> facilityV1BulkDeletePost(@ApiParam(value = "Details for existing facility.", required = true) @Valid @RequestBody FacilityBulkRequest request) {
        request.getRequestInfo().setApiId(httpServletRequest.getRequestURI());
        producer.push(facilityConfiguration.getBulkDeleteFacilityTopic(), request);

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ResponseInfoFactory
                .createResponseInfo(request.getRequestInfo(), true));
    }

    @RequestMapping(value = "/v1/bulk/_update", method = RequestMethod.POST)
    public ResponseEntity<ResponseInfo> facilityV1BulkUpdatePost(@ApiParam(value = "Details for existing facility.", required = true) @Valid @RequestBody FacilityBulkRequest request) {
        request.getRequestInfo().setApiId(httpServletRequest.getRequestURI());
        producer.push(facilityConfiguration.getBulkUpdateFacilityTopic(), request);

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ResponseInfoFactory
                .createResponseInfo(request.getRequestInfo(), true));
    }

    @RequestMapping(value = "/v1/_create", method = RequestMethod.POST)
    public ResponseEntity<FacilityResponse> facilityV1CreatePost(@ApiParam(value = "Capture details of Facility.", required = true) @Valid @RequestBody FacilityRequest request) {
        Facility facility = facilityService.create(request);
        FacilityResponse response = FacilityResponse.builder()
                .facility(facility)
                .responseInfo(ResponseInfoFactory
                        .createResponseInfo(request.getRequestInfo(), true))
                .build();

        return ResponseEntity.accepted().body(response);
    }

    @RequestMapping(value = "/v1/_delete", method = RequestMethod.POST)
    public ResponseEntity<FacilityResponse> facilityV1DeletePost(@ApiParam(value = "Details for existing facility.", required = true) @Valid @RequestBody FacilityRequest request) {
        Facility facility = facilityService.delete(request);
        FacilityResponse response = FacilityResponse.builder()
                .facility(facility)
                .responseInfo(ResponseInfoFactory
                        .createResponseInfo(request.getRequestInfo(), true))
                .build();

        return ResponseEntity.accepted().body(response);
    }

    @RequestMapping(value = "/v1/_search", method = RequestMethod.POST)
    public ResponseEntity<FacilityBulkResponse> facilityV1SearchPost(@ApiParam(value = "Details for existing facility.", required = true) @Valid @RequestBody FacilitySearchRequest request, @NotNull
    @Min(0)
    @Max(2000) @ApiParam(value = "Pagination - limit records in response", required = true) @Valid @RequestParam(value = "limit", required = true) Integer limit, @NotNull
                                                                 @Min(0) @ApiParam(value = "Pagination - offset from which records should be returned in response", required = true) @Valid @RequestParam(value = "offset", required = true) Integer offset, @NotNull @ApiParam(value = "Unique id for a tenant.", required = true) @Valid @RequestParam(value = "tenantId", required = true) String tenantId, @ApiParam(value = "epoch of the time since when the changes on the object should be picked up. Search results from this parameter should include both newly created objects since this time as well as any modified objects since this time. This criterion is included to help polling clients to get the changes in system since a last time they synchronized with the platform. ") @Valid @RequestParam(value = "lastChangedSince", required = false) Long lastChangedSince, @ApiParam(value = "Used in search APIs to specify if (soft) deleted records should be included in search results.", defaultValue = "false") @Valid @RequestParam(value = "includeDeleted", required = false, defaultValue = "false") Boolean includeDeleted) throws Exception {
        limit = 2000;
        List<Facility> facilities = facilityService.search(request, limit, offset, tenantId, lastChangedSince, includeDeleted);
        FacilityBulkResponse response = FacilityBulkResponse.builder().responseInfo(ResponseInfoFactory
                .createResponseInfo(request.getRequestInfo(), true)).facilities(facilities).build();

        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @RequestMapping(value = "/v1/_update", method = RequestMethod.POST)
    public ResponseEntity<FacilityResponse> facilityV1UpdatePost(@ApiParam(value = "Details for existing facility.", required = true) @Valid @RequestBody FacilityRequest request) {
        Facility facility = facilityService.update(request);
        FacilityResponse response = FacilityResponse.builder()
                .facility(facility)
                .responseInfo(ResponseInfoFactory
                        .createResponseInfo(request.getRequestInfo(), true))
                .build();

        return ResponseEntity.accepted().body(response);
    }

}
