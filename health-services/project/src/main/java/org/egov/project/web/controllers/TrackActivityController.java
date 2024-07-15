package org.egov.project.web.controllers;

import io.swagger.annotations.ApiParam;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.egov.common.contract.response.ResponseInfo;
import org.egov.common.models.core.SearchResponse;
import org.egov.common.models.core.URLParams;
import org.egov.common.models.project.Task;
import org.egov.common.models.project.TaskBulkRequest;
import org.egov.common.models.project.TaskBulkResponse;
import org.egov.common.models.project.TaskRequest;
import org.egov.common.models.project.TaskResponse;
import org.egov.common.models.project.TaskSearchRequest;
import org.egov.common.models.project.irs.LocationPointBulkRequest;
import org.egov.common.producer.Producer;
import org.egov.common.utils.ResponseInfoFactory;
import org.egov.project.config.ProjectConfiguration;
import org.egov.project.service.TrackActivityTaskService;
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
@RequestMapping("/staff/track-activity")
@Validated
public class TrackActivityController {

    private final HttpServletRequest httpServletRequest;

    private final TrackActivityTaskService trackActivityTaskService;

    private final Producer producer;

    private final ProjectConfiguration projectConfiguration;

    @Autowired
    public TrackActivityController(
            HttpServletRequest httpServletRequest,
            TrackActivityTaskService trackActivityTaskService,
            Producer producer,
            ProjectConfiguration projectConfiguration
    ) {
        this.httpServletRequest = httpServletRequest;
        this.trackActivityTaskService = trackActivityTaskService;
        this.producer = producer;
        this.projectConfiguration = projectConfiguration;
    }

    @RequestMapping(value = "/v1/_create", method = RequestMethod.POST)
    public ResponseEntity<TaskResponse> trackActivityTaskV1CreatePost(@ApiParam(value = "Capture linkage of Project and Track Activity Task.", required = true) @Valid @RequestBody TaskRequest request) {

        Task task = trackActivityTaskService.create(request);
        TaskResponse response = TaskResponse.builder()
                .task(task)
                .responseInfo(ResponseInfoFactory
                        .createResponseInfo(request.getRequestInfo(), true))
                .build();
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @RequestMapping(value = "/v1/bulk/_create", method = RequestMethod.POST)
    public ResponseEntity<ResponseInfo> trackActivityTaskV1BulkCreatePost(@ApiParam(value = "Capture linkage of Project and Track Activity Task.", required = true) @Valid @RequestBody TaskBulkRequest request) {
        request.getRequestInfo().setApiId(httpServletRequest.getRequestURI());
        producer.push(projectConfiguration.getBulkCreateTrackActivityTaskTopic(), request);

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ResponseInfoFactory
                .createResponseInfo(request.getRequestInfo(), true));
    }


    @RequestMapping(value = "/v1/_search", method = RequestMethod.POST)
    public ResponseEntity<TaskBulkResponse> trackActivityTaskV2SearchPost(
            @Valid @ModelAttribute URLParams urlParams,
            @ApiParam(value = "Capture details of Project Track Activity Task.", required = true) @Valid @RequestBody TaskSearchRequest taskSearchRequest
    ) throws Exception {
        SearchResponse<Task> tasks = trackActivityTaskService.search(
                taskSearchRequest.getTask(),
                urlParams.getLimit(),
                urlParams.getOffset(),
                urlParams.getTenantId(),
                urlParams.getLastChangedSince(),
                urlParams.getIncludeDeleted()
        );
        TaskBulkResponse response = TaskBulkResponse.builder()
                .tasks(tasks.getResponse())
                .totalCount(tasks.getTotalCount())
                .responseInfo(ResponseInfoFactory
                        .createResponseInfo(taskSearchRequest.getRequestInfo(), true))
                .build();

        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @RequestMapping(value = "/v1/_update", method = RequestMethod.POST)
    public ResponseEntity<TaskResponse> trackActivityTaskV1UpdatePost(@ApiParam(value = "Capture linkage of Project and Track Activity Task.", required = true) @Valid @RequestBody TaskRequest trackActivityTaskUpdateRequest) {

        Task task = trackActivityTaskService.update(trackActivityTaskUpdateRequest);
        TaskResponse response = TaskResponse.builder()
                .task(task)
                .responseInfo(ResponseInfoFactory
                        .createResponseInfo(trackActivityTaskUpdateRequest.getRequestInfo(), true))
                .build();

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @RequestMapping(value = "/v1/bulk/_update", method = RequestMethod.POST)
    public ResponseEntity<ResponseInfo> trackActivityTaskV1BulkUpdatePost(@ApiParam(value = "Capture linkage of Project and Track Activity Task.", required = true) @Valid @RequestBody TaskBulkRequest request) {
        request.getRequestInfo().setApiId(httpServletRequest.getRequestURI());
        producer.push(projectConfiguration.getBulkUpdateTrackActivityTaskTopic(), request);

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ResponseInfoFactory
                .createResponseInfo(request.getRequestInfo(), true));
    }

    @RequestMapping(value = "/v1/_delete", method = RequestMethod.POST)
    public ResponseEntity<TaskResponse> trackActivityTaskV1DeletePost(@ApiParam(value = "Capture linkage of Project and Track Activity Task.", required = true) @Valid @RequestBody TaskRequest trackActivityTaskUpdateRequest) {

        Task tasks = trackActivityTaskService.delete(trackActivityTaskUpdateRequest);
        TaskResponse response = TaskResponse.builder()
                .task(tasks)
                .responseInfo(ResponseInfoFactory
                        .createResponseInfo(trackActivityTaskUpdateRequest.getRequestInfo(), true))
                .build();

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @RequestMapping(value = "/v1/bulk/_delete", method = RequestMethod.POST)
    public ResponseEntity<ResponseInfo> trackActivityTaskV1BulkDeletePost(@ApiParam(value = "Capture linkage of Project and Track Activity Task.", required = true) @Valid @RequestBody TaskBulkRequest request) {

        request.getRequestInfo().setApiId(httpServletRequest.getRequestURI());
        producer.push(projectConfiguration.getBulkDeleteTrackActivityTaskTopic(), request);

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ResponseInfoFactory
                .createResponseInfo(request.getRequestInfo(), true));
    }

    @RequestMapping(value = "/location-point/v1/bulk/_create", method = RequestMethod.POST)
    public ResponseEntity<ResponseInfo> trackActivityLocationPointV1BulkCreatePost(
            @ApiParam(value = "Capture linkage of Project and Track Activity Task.", required = true) @Valid @RequestBody LocationPointBulkRequest request
    ) {
        request.getRequestInfo().setApiId(httpServletRequest.getRequestURI());
        producer.push(projectConfiguration.getBulkCreateTrackActivityTaskLocationPointTopic(), request);

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ResponseInfoFactory
                .createResponseInfo(request.getRequestInfo(), true));
    }
}
