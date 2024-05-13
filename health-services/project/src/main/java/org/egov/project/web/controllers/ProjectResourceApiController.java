package org.egov.project.web.controllers;

import io.swagger.annotations.ApiParam;
import org.egov.common.contract.response.ResponseInfo;
import org.egov.common.data.query.exception.QueryBuilderException;
import org.egov.common.models.core.URLParams;
import org.egov.common.models.project.ProjectResource;
import org.egov.common.models.project.ProjectResourceBulkRequest;
import org.egov.common.models.project.ProjectResourceBulkResponse;
import org.egov.common.models.project.ProjectResourceRequest;
import org.egov.common.models.project.ProjectResourceResponse;
import org.egov.common.producer.Producer;
import org.egov.common.utils.ResponseInfoFactory;
import org.egov.project.config.ProjectConfiguration;
import org.egov.project.service.ProjectResourceService;
import org.egov.project.web.models.ProjectResourceSearchRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.util.List;

@javax.annotation.Generated(value = "org.egov.codegen.SpringBootCodegen", date = "2022-12-14T20:57:07.075+05:30")
@Controller
@RequestMapping("")
@Validated
public class ProjectResourceApiController {

    private final HttpServletRequest httpServletRequest;

    private final Producer producer;

    private final ProjectConfiguration projectConfiguration;

    @Autowired
    ProjectResourceService projectResourceService;

    public ProjectResourceApiController(HttpServletRequest httpServletRequest, Producer producer, ProjectConfiguration projectConfiguration) {
        this.httpServletRequest = httpServletRequest;
        this.producer = producer;
        this.projectConfiguration = projectConfiguration;
    }

    @RequestMapping(value = "/resource/v1/_create", method = RequestMethod.POST)
    public ResponseEntity<ProjectResourceResponse> resourceV1CreatePost(@ApiParam(value = "Capture linkage of Project and resources.", required = true) @Valid @RequestBody ProjectResourceRequest request) {
        ProjectResource projectResourceResponse = projectResourceService.create(request);
        ProjectResourceResponse response = ProjectResourceResponse.builder()
                .projectResource(projectResourceResponse)
                .responseInfo(ResponseInfoFactory
                        .createResponseInfo(request.getRequestInfo(), true))
                .build();
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @RequestMapping(value = "/resource/v1/bulk/_create", method = RequestMethod.POST)
    public ResponseEntity<ResponseInfo> resourceV1BulkCreatePost(@ApiParam(value = "Capture linkage of Project and resources.", required = true) @Valid @RequestBody ProjectResourceBulkRequest request) {
        request.getRequestInfo().setApiId(httpServletRequest.getRequestURI());
        producer.push(projectConfiguration.getCreateProjectResourceBulkTopic(), request);

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ResponseInfoFactory
                .createResponseInfo(request.getRequestInfo(), true));
    }

    @RequestMapping(value = "/resource/v1/_search", method = RequestMethod.POST)
    public ResponseEntity<ProjectResourceBulkResponse> resourceV1SearchPost(@ApiParam(
            value = "Search linkage of Project and resource.", required = true) @Valid @RequestBody
                                                                                ProjectResourceSearchRequest request,
            ) throws QueryBuilderException {

        List<ProjectResource> projectResource = projectResourceService.search(request, searchCriteria.getLimit(), searchCriteria.getOffset(), searchCriteria.getTenantId(), searchCriteria.getLastChangedSince(), searchCriteria.getIncludeDeleted());
        ProjectResourceBulkResponse response = ProjectResourceBulkResponse.builder().responseInfo(ResponseInfoFactory
                .createResponseInfo(request.getRequestInfo(), true)).projectResource(projectResource).build();

        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @RequestMapping(value = "/resource/v1/_update", method = RequestMethod.POST)
    public ResponseEntity<ProjectResourceResponse> resourceV1UpdatePost(@ApiParam(value = "Capture linkage of Project and Resource.", required = true) @Valid @RequestBody ProjectResourceRequest request) {

        ProjectResource projectResourceResponse = projectResourceService.update(request);
        ProjectResourceResponse response = ProjectResourceResponse.builder()
                .projectResource(projectResourceResponse)
                .responseInfo(ResponseInfoFactory
                        .createResponseInfo(request.getRequestInfo(), true))
                .build();
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @RequestMapping(value = "/resource/v1/bulk/_update", method = RequestMethod.POST)
    public ResponseEntity<ResponseInfo> resourceV1BulkUpdatePost(@ApiParam(value = "Capture linkage of Project and Resource.", required = true) @Valid @RequestBody ProjectResourceBulkRequest request) {

        request.getRequestInfo().setApiId(httpServletRequest.getRequestURI());
        producer.push(projectConfiguration.getUpdateProjectResourceBulkTopic(), request);

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ResponseInfoFactory
                .createResponseInfo(request.getRequestInfo(), true));
    }

    @RequestMapping(value = "/resource/v1/_delete", method = RequestMethod.POST)
    public ResponseEntity<ProjectResourceResponse> resourceV1DeletePost(@ApiParam(value = "Capture linkage of Project and Resource.", required = true) @Valid @RequestBody ProjectResourceRequest request) {
        ProjectResource projectResourceResponse = projectResourceService.delete(request);
        ProjectResourceResponse response = ProjectResourceResponse.builder()
                .projectResource(projectResourceResponse)
                .responseInfo(ResponseInfoFactory
                        .createResponseInfo(request.getRequestInfo(), true))
                .build();
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @RequestMapping(value = "/resource/v1/bulk/_delete", method = RequestMethod.POST)
    public ResponseEntity<ResponseInfo> resourceV1BulkDeletePost(@ApiParam(value = "Capture linkage of Project and Resource.", required = true) @Valid @RequestBody ProjectResourceBulkRequest request) {
        request.getRequestInfo().setApiId(httpServletRequest.getRequestURI());
        producer.push(projectConfiguration.getDeleteProjectResourceBulkTopic(), request);

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ResponseInfoFactory
                .createResponseInfo(request.getRequestInfo(), true));    }
}
