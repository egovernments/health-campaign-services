package org.egov.project.web.controllers;

import io.swagger.annotations.ApiParam;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.egov.common.contract.response.ResponseInfo;
import org.egov.common.models.core.SearchResponse;
import org.egov.common.models.core.URLParams;
import org.egov.common.models.project.irs.UserAction;
import org.egov.common.models.project.irs.UserActionBulkRequest;
import org.egov.common.models.project.irs.UserActionBulkResponse;
import org.egov.common.models.project.irs.UserActionSearchRequest;
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

@Controller
@RequestMapping("/user-action")
@Validated
public class UserActionController {

    private final HttpServletRequest httpServletRequest;

    private final UserActionService userActionService;
    
    private final Producer producer;

    private final ProjectConfiguration projectConfiguration;

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

    @RequestMapping(value = "/v1/_create", method = RequestMethod.POST)
    public ResponseEntity<ResponseInfo> userActionV1BulkCreatePost(
            @ApiParam(value = "Capture linkage of Project and User Action UserAction.", required = true) @Valid @RequestBody UserActionBulkRequest request
    ) {
        request.getRequestInfo().setApiId(httpServletRequest.getRequestURI());
        producer.push(projectConfiguration.getBulkCreateUserActionTaskTopic(), request);

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ResponseInfoFactory
                .createResponseInfo(request.getRequestInfo(), true));
    }


    @RequestMapping(value = "/v1/_search", method = RequestMethod.POST)
    public ResponseEntity<UserActionBulkResponse> userActionV2SearchPost(
            @Valid @ModelAttribute URLParams urlParams,
            @ApiParam(value = "Capture details of Project User Action UserAction.", required = true) @Valid @RequestBody UserActionSearchRequest request
    ) {
        SearchResponse<UserAction> userActions = userActionService.search(request, urlParams);
        UserActionBulkResponse response = UserActionBulkResponse.builder()
                .userActions(userActions.getResponse())
                .totalCount(userActions.getTotalCount())
                .responseInfo(ResponseInfoFactory
                        .createResponseInfo(request.getRequestInfo(), true))
                .build();

        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @RequestMapping(value = "/v1/_update", method = RequestMethod.POST)
    public ResponseEntity<ResponseInfo> userActionV1BulkUpdatePost(
            @ApiParam(value = "Capture linkage of Project and User Action UserAction.", required = true) @Valid @RequestBody UserActionBulkRequest request
    ) {
        request.getRequestInfo().setApiId(httpServletRequest.getRequestURI());
        producer.push(projectConfiguration.getBulkUpdateUserActionTaskTopic(), request);

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ResponseInfoFactory
                .createResponseInfo(request.getRequestInfo(), true));
    }

}
