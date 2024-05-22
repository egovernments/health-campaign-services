package org.egov.facility.web.controllers;


import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.annotations.ApiParam;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.egov.common.contract.response.ResponseInfo;
import org.egov.common.models.core.URLParams;
import org.egov.common.models.facility.Facility;
import org.egov.common.models.facility.FacilityBulkRequest;
import org.egov.common.models.facility.FacilityBulkResponse;
import org.egov.common.models.facility.FacilityRequest;
import org.egov.common.models.facility.FacilityResponse;
import org.egov.common.models.facility.FacilitySearchRequest;
import org.egov.common.producer.Producer;
import org.egov.common.utils.ResponseInfoFactory;
import org.egov.facility.config.FacilityConfiguration;
import org.egov.facility.service.FacilityService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

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
    public ResponseEntity<FacilityBulkResponse> facilityV1SearchPost(
            @Valid @ModelAttribute URLParams urlParams,
            @ApiParam(value = "Details for existing facility.", required = true) @Valid @RequestBody FacilitySearchRequest request
    ) throws Exception {
        List<Facility> facilities = facilityService.search(
                request,
                urlParams.getLimit(),
                urlParams.getOffset(),
                urlParams.getTenantId(),
                urlParams.getLastChangedSince(),
                urlParams.getIncludeDeleted());
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
