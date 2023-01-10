package org.egov.project.web.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.annotations.ApiParam;
import org.egov.common.utils.ResponseInfoFactory;
import org.egov.project.service.ProjectTaskService;
import org.egov.project.web.models.Task;
import org.egov.project.web.models.TaskRequest;
import org.egov.project.web.models.TaskResource;
import org.egov.project.web.models.TaskResourceRequest;
import org.egov.project.web.models.TaskResourceResponse;
import org.egov.project.web.models.TaskResponse;
import org.egov.project.web.models.TaskSearchRequest;
import org.egov.tracer.model.CustomException;
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
import java.util.List;

import static org.egov.common.utils.CommonUtils.isForCreate;

@Controller
@RequestMapping("")
@Validated
public class ProjectTaskApiController {

    private final ObjectMapper objectMapper;

    private final HttpServletRequest request;

    private final ProjectTaskService projectTaskService;

    public ProjectTaskApiController(ObjectMapper objectMapper, HttpServletRequest request, ProjectTaskService projectTaskService) {
        this.objectMapper = objectMapper;
        this.request = request;
        this.projectTaskService = projectTaskService;
    }

    @RequestMapping(value = "/task/v1/_create", method = RequestMethod.POST)
    public ResponseEntity<TaskResponse> projectTaskV1CreatePost(@ApiParam(value = "Capture details of Task", required = true) @Valid @RequestBody TaskRequest request) throws Exception {
        if (!isForCreate(request)) {
            throw new CustomException("INVALID_API_OPERATION",
                    String.format("API Operation %s not valid for create request", request.getApiOperation()));
        }

        List<Task> tasks = projectTaskService.create(request);
        TaskResponse response = TaskResponse.builder()
                .task(tasks)
                .responseInfo(ResponseInfoFactory
                        .createResponseInfo(request.getRequestInfo(), true))
                .build();

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @RequestMapping(value = "/task/v1/_search", method = RequestMethod.POST)
    public ResponseEntity<TaskResponse> projectTaskV1SearchPost(@ApiParam(value = "Project Task Search.", required = true) @Valid @RequestBody TaskSearchRequest request,
                                                                @NotNull @Min(0) @Max(1000) @ApiParam(value = "Pagination - limit records in response", required = true) @Valid @RequestParam(value = "limit", required = true) Integer limit,
                                                                @NotNull @Min(0) @ApiParam(value = "Pagination - offset from which records should be returned in response", required = true) @Valid @RequestParam(value = "offset", required = true) Integer offset,
                                                                @NotNull @ApiParam(value = "Unique id for a tenant.", required = true) @Valid @RequestParam(value = "tenantId", required = true) String tenantId,
                                                                @ApiParam(value = "epoch of the time since when the changes on the object should be picked up. Search results from this parameter should include both newly created objects since this time as well as any modified objects since this time. This criterion is included to help polling clients to get the changes in system since a last time they synchronized with the platform. ") @Valid @RequestParam(value = "lastChangedSince", required = false) Long lastChangedSince,
                                                                @ApiParam(value = "Used in search APIs to specify if (soft) deleted records should be included in search results.", defaultValue = "false") @Valid @RequestParam(value = "includeDeleted", required = false, defaultValue = "false") Boolean includeDeleted) {

        List<Task> households = projectTaskService.search(request.getTask(), limit, offset, tenantId, lastChangedSince, includeDeleted);
        TaskResponse response = TaskResponse.builder().responseInfo(ResponseInfoFactory
                .createResponseInfo(request.getRequestInfo(), true)).task(households).build();

        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @RequestMapping(value = "/task/v1/_update", method = RequestMethod.POST)
    public ResponseEntity<TaskResponse> projectTaskV1UpdatePost(@ApiParam(value = "Capture details of Existing task", required = true) @Valid @RequestBody TaskRequest task) {
        return new ResponseEntity<TaskResponse>(HttpStatus.NOT_IMPLEMENTED);
    }

    @RequestMapping(value = "/task/resource/v1/_create", method = RequestMethod.POST)
    public ResponseEntity<TaskResourceResponse> projectTaskResourceV1Create(@ApiParam(value = "Capture details of Task", required = true) @Valid @RequestBody TaskResourceRequest request) {

        if (!isForCreate(request)) {
            throw new CustomException("INVALID_API_OPERATION",
                    String.format("API Operation %s not valid for create request", request.getApiOperation()));
        }

        List<TaskResource> resources = projectTaskService.createResource(request);

        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }

    @RequestMapping(value = "/task/resource/v1/_update", method = RequestMethod.POST)
    public ResponseEntity<TaskResourceResponse> projectTaskResourceV1Update(@ApiParam(value = "Capture details of Task", required = true) @Valid @RequestBody TaskResourceRequest request) {
        return new ResponseEntity<TaskResourceResponse>(HttpStatus.NOT_IMPLEMENTED);
    }
}
