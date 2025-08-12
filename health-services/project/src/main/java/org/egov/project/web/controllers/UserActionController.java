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
import org.egov.project.service.UserActionService;
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
 * Controller for handling user action-related requests.
 * Provides endpoints for creating, updating, and searching user actions.
 */
@Controller
@RequestMapping("/user-action")
@Validated
@Slf4j
public class UserActionController {

    private final HttpServletRequest httpServletRequest;
    private final UserActionService userActionService;
    private final Producer producer;
    private final ProjectConfiguration projectConfiguration;

    /**
     * Constructor for injecting dependencies into the UserActionController.
     *
     * @param httpServletRequest       The HttpServletRequest to capture request details.
     * @param userActionService        The service for handling user action logic.
     * @param producer                 The producer for sending messages to Kafka topics.
     * @param projectConfiguration     Configuration properties related to the project.
     */
    @Autowired
    public UserActionController(
            HttpServletRequest httpServletRequest,
            UserActionService userActionService,
            Producer producer,
            ProjectConfiguration projectConfiguration
    ) {
        this.httpServletRequest = httpServletRequest;
        this.userActionService = userActionService;
        this.producer = producer;
        this.projectConfiguration = projectConfiguration;
    }

    /**
     * Endpoint for creating user actions in bulk.
     * Receives a UserActionBulkRequest object, processes it, and sends it to the appropriate Kafka topic.
     *
     * @param request The bulk request containing user actions to be created.
     * @return A ResponseEntity containing the response info with HTTP status ACCEPTED.
     */
    @RequestMapping(value = "/v1/_create", method = RequestMethod.POST)
    public ResponseEntity<ResponseInfo> userActionV1BulkCreatePost(
            @ApiParam(value = "Capture linkage of Project and User Action UserAction.", required = true) @Valid @RequestBody UserActionBulkRequest request
    ) {
        // Set the API ID in the request info using the current request URI.
        request.getRequestInfo().setApiId(httpServletRequest.getRequestURI());

        try {
            log.debug("Pushing user action bulk create request to Kafka topic: {}", projectConfiguration.getBulkCreateUserActionTopic());
            // Send the request to the Kafka topic for bulk creation.
            producer.push(projectConfiguration.getBulkCreateUserActionTopic(), request);
            log.info("Successfully pushed user action bulk create request to Kafka");
        } catch (Exception e) {
            log.error("Failed to push user action bulk create request to Kafka", e);
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
     * Endpoint for searching user actions based on given search criteria.
     * Receives a UserActionSearchRequest object and returns the search results.
     *
     * @param urlParams                  URL parameters for the search.
     * @param request                    The request containing search criteria for user actions.
     * @return A ResponseEntity containing the search results with HTTP status OK.
     */
    @RequestMapping(value = "/v1/_search", method = RequestMethod.POST)
    public ResponseEntity<UserActionBulkResponse> userActionV2SearchPost(
            @Valid @ModelAttribute URLParams urlParams,
            @ApiParam(value = "Capture details of Project User Action UserAction.", required = true) @Valid @RequestBody UserActionSearchRequest request
    ) {
        log.debug("Executing search with URLParams: {} and request: {}", urlParams, request);

        // Perform the search using the userActionService.
        SearchResponse<UserAction> userActions;
        try {
            // Perform the search using the userActionService.
            userActions = userActionService.search(request, urlParams);
            log.info("Successfully searched for user actions: {}", userActions.getResponse().size());
        } catch (Exception e) {
            log.error("Failed to search for user actions", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }

        // Build the response object with the search results and response info.
        UserActionBulkResponse response = UserActionBulkResponse.builder()
                .userActions(userActions.getResponse())
                .totalCount(userActions.getTotalCount())
                .responseInfo(ResponseInfoFactory.createResponseInfo(request.getRequestInfo(), true))
                .build();

        // Return the response with HTTP status OK.
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    /**
     * Endpoint for updating user actions in bulk.
     * Receives a UserActionBulkRequest object, processes it, and sends it to the appropriate Kafka topic.
     *
     * @param request The bulk request containing user actions to be updated.
     * @return A ResponseEntity containing the response info with HTTP status ACCEPTED.
     */
    @RequestMapping(value = "/v1/_update", method = RequestMethod.POST)
    public ResponseEntity<ResponseInfo> userActionV1BulkUpdatePost(
            @ApiParam(value = "Capture linkage of Project and User Action UserAction.", required = true) @Valid @RequestBody UserActionBulkRequest request
    ) {
        // Set the API ID in the request info using the current request URI.
        request.getRequestInfo().setApiId(httpServletRequest.getRequestURI());

        try {
            // Send the request to the Kafka topic for bulk update.
            producer.push(projectConfiguration.getBulkUpdateUserActionTopic(), request);
        } catch (Exception e) {
            log.error("Failed to push user action bulk update request to Kafka", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ResponseInfoFactory.createResponseInfo(request.getRequestInfo(), false)
            );
        }

        // Create and return a ResponseInfo object with HTTP status ACCEPTED.
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(
                ResponseInfoFactory.createResponseInfo(request.getRequestInfo(), true)
        );
    }
}
