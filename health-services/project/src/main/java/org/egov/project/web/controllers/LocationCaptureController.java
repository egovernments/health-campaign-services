package org.egov.project.web.controllers;

import io.swagger.annotations.ApiParam;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.response.ResponseInfo;
import org.egov.common.models.core.SearchResponse;
import org.egov.common.models.core.URLParams;
import org.egov.common.models.project.useraction.UserAction;
import org.egov.common.models.project.useraction.UserActionBulkRequest;
import org.egov.common.models.project.useraction.UserActionBulkResponse;
import org.egov.common.models.project.useraction.UserActionSearchRequest;
import org.egov.common.producer.Producer;
import org.egov.common.utils.ResponseInfoFactory;
import org.egov.project.config.ProjectConfiguration;
import org.egov.project.service.LocationCaptureService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

/**
 * Controller for handling requests related to location capture tasks.
 * Provides endpoints for creating and searching location capture tasks.
 */
@Controller
@RequestMapping("/user-location")
@Validated
@Slf4j
public class LocationCaptureController {

    private final HttpServletRequest httpServletRequest;
    private final LocationCaptureService locationCaptureService;
    private final Producer producer;
    private final ProjectConfiguration projectConfiguration;

    /**
     * Constructor for injecting dependencies into the LocationCaptureController.
     *
     * @param httpServletRequest       The HttpServletRequest to capture request details.
     * @param locationCaptureService   The service for handling location capture logic.
     * @param producer                 The producer for sending messages to Kafka topics.
     * @param projectConfiguration     Configuration properties related to the project.
     */
    @Autowired
    public LocationCaptureController(
            HttpServletRequest httpServletRequest,
            LocationCaptureService locationCaptureService,
            Producer producer,
            ProjectConfiguration projectConfiguration
    ) {
        this.httpServletRequest = httpServletRequest;
        this.locationCaptureService = locationCaptureService;
        this.producer = producer;
        this.projectConfiguration = projectConfiguration;
    }

    /**
     * Endpoint for creating location capture tasks in bulk.
     * Receives a UserActionBulkRequest object, processes it, and sends it to the appropriate Kafka topic.
     *
     * @param request The bulk request containing user actions to be created.
     * @return A ResponseEntity containing the response info with HTTP status ACCEPTED.
     */
    @RequestMapping(value = "/v1/_create", method = RequestMethod.POST)
    public ResponseEntity<ResponseInfo> locationCaptureTaskV1BulkCreatePost(
            @ApiParam(value = "Create Location Capture LocationCapture.", required = true) @Valid @RequestBody UserActionBulkRequest request) {
        // Set the API ID in the request info using the current request URI.
        request.getRequestInfo().setApiId(httpServletRequest.getRequestURI());

        try {
            // Send the request to the Kafka topic for bulk creation.
            producer.push(projectConfiguration.getBulkCreateLocationCaptureTopic(), request);
        } catch (Exception e) {
            log.error("Error sending message to Kafka", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ResponseInfoFactory.createResponseInfo(request.getRequestInfo(), false)
            );
        }

        // Create and return a ResponseInfo object with HTTP status ACCEPTED.
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(
                ResponseInfoFactory.createResponseInfo(request.getRequestInfo(), true)
        );
    }

    /**
     * Endpoint for searching location capture tasks based on given search criteria.
     * Receives a UserActionSearchRequest object and returns the search results.
     *
     * @param urlParams                  URL parameters for the search.
     * @param locationCaptureSearchRequest The request containing search criteria for location capture tasks.
     * @return A ResponseEntity containing the search results with HTTP status OK.
     * @throws Exception if there is an error during the search operation.
     */
    @RequestMapping(value = "/v1/_search", method = RequestMethod.POST)
    public ResponseEntity<UserActionBulkResponse> locationCaptureTaskV2SearchPost(
            @Valid @ModelAttribute URLParams urlParams,
            @ApiParam(value = "Search details of Location Capture.", required = true) @Valid @RequestBody UserActionSearchRequest locationCaptureSearchRequest
    ) throws Exception {

        try {
            // Perform the search using the locationCaptureService.
            SearchResponse<UserAction> locationCaptureSearchResponse = locationCaptureService.search(locationCaptureSearchRequest, urlParams);

            // Build the response object with the search results and response info.
            UserActionBulkResponse response = UserActionBulkResponse.builder()
                    .userActions(locationCaptureSearchResponse.getResponse())
                    .totalCount(locationCaptureSearchResponse.getTotalCount())
                    .responseInfo(ResponseInfoFactory.createResponseInfo(locationCaptureSearchRequest.getRequestInfo(), true))
                    .build();

            // Return the response with HTTP status OK.
            return ResponseEntity.status(HttpStatus.OK).body(response);
        } catch (Exception e) {
            log.error("Error during search operation", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    UserActionBulkResponse.builder()
                            .responseInfo(ResponseInfoFactory.createResponseInfo(locationCaptureSearchRequest.getRequestInfo(), false))
                            .build()
            );
        }
    }
}
