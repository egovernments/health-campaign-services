package org.egov.project.web.controllers;

import io.swagger.annotations.ApiParam;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.egov.common.contract.response.ResponseInfo;
import org.egov.common.models.core.SearchResponse;
import org.egov.common.models.core.URLParams;
import org.egov.common.models.project.irs.LocationCapture;
import org.egov.common.models.project.irs.LocationCaptureBulkRequest;
import org.egov.common.models.project.irs.LocationCaptureBulkResponse;
import org.egov.common.models.project.irs.LocationCaptureSearchRequest;
import org.egov.common.producer.Producer;
import org.egov.common.utils.ResponseInfoFactory;
import org.egov.project.config.ProjectConfiguration;
import org.egov.project.service.LocationCaptureTaskService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

@Controller
@RequestMapping("/user-location")
@Validated
public class LocationCaptureController {

    private final HttpServletRequest httpServletRequest;

    private final LocationCaptureTaskService locationCaptureTaskService;

    private final Producer producer;

    private final ProjectConfiguration projectConfiguration;

    @Autowired
    public LocationCaptureController(
            HttpServletRequest httpServletRequest,
            LocationCaptureTaskService locationCaptureTaskService,
            Producer producer,
            ProjectConfiguration projectConfiguration
    ) {
        this.httpServletRequest = httpServletRequest;
        this.locationCaptureTaskService = locationCaptureTaskService;
        this.producer = producer;
        this.projectConfiguration = projectConfiguration;
    }

    @RequestMapping(value = "/v1/_create", method = RequestMethod.POST)
    public ResponseEntity<ResponseInfo> locationCaptureTaskV1BulkCreatePost(@ApiParam(value = "Create Location Capture LocationCapture.", required = true) @Valid @RequestBody LocationCaptureBulkRequest request) {
        request.getRequestInfo().setApiId(httpServletRequest.getRequestURI());
        producer.push(projectConfiguration.getBulkCreateLocationCaptureTaskTopic(), request);

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ResponseInfoFactory
                .createResponseInfo(request.getRequestInfo(), true));
    }


    @RequestMapping(value = "/v1/_search", method = RequestMethod.POST)
    public ResponseEntity<LocationCaptureBulkResponse> locationCaptureTaskV2SearchPost(
            @Valid @ModelAttribute URLParams urlParams,
            @ApiParam(value = "Search details of Location Capture.", required = true) @Valid @RequestBody LocationCaptureSearchRequest locationCaptureSearchRequest
            ) throws Exception {
        SearchResponse<LocationCapture> tasks = locationCaptureTaskService.search(
                locationCaptureSearchRequest,
                urlParams
        );
        LocationCaptureBulkResponse response = LocationCaptureBulkResponse.builder()
                .locationCaptures(tasks.getResponse())
                .totalCount(tasks.getTotalCount())
                .responseInfo(ResponseInfoFactory
                        .createResponseInfo(locationCaptureSearchRequest.getRequestInfo(), true))
                .build();

        return ResponseEntity.status(HttpStatus.OK).body(response);
    }
}
