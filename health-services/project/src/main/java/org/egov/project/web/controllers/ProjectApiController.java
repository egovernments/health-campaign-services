package org.egov.project.web.controllers;


import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.annotations.ApiParam;
import org.egov.common.contract.response.ResponseInfo;
import org.egov.common.models.project.BeneficiaryBulkRequest;
import org.egov.common.models.project.BeneficiaryBulkResponse;
import org.egov.common.models.project.BeneficiaryRequest;
import org.egov.common.models.project.BeneficiaryResponse;
import org.egov.common.models.project.Project;
import org.egov.common.models.project.ProjectBeneficiary;
import org.egov.common.models.project.ProjectFacility;
import org.egov.common.models.project.ProjectFacilityBulkRequest;
import org.egov.common.models.project.ProjectFacilityBulkResponse;
import org.egov.common.models.project.ProjectFacilityRequest;
import org.egov.common.models.project.ProjectFacilityResponse;
import org.egov.common.models.project.ProjectRequest;
import org.egov.common.models.project.ProjectResponse;
import org.egov.common.models.project.ProjectStaff;
import org.egov.common.models.project.ProjectStaffBulkRequest;
import org.egov.common.models.project.ProjectStaffBulkResponse;
import org.egov.common.models.project.ProjectStaffRequest;
import org.egov.common.models.project.ProjectStaffResponse;
import org.egov.common.models.project.Task;
import org.egov.common.models.project.TaskBulkRequest;
import org.egov.common.models.project.TaskBulkResponse;
import org.egov.common.models.project.TaskRequest;
import org.egov.common.models.project.TaskResponse;
import org.egov.common.models.project.TaskSearchRequest;
import org.egov.common.producer.Producer;
import org.egov.common.utils.ResponseInfoFactory;
import org.egov.project.config.ProjectConfiguration;
import org.egov.project.service.ProjectBeneficiaryService;
import org.egov.project.service.ProjectFacilityService;
import org.egov.project.service.ProjectService;
import org.egov.project.service.ProjectStaffService;
import org.egov.project.service.ProjectTaskService;
import org.egov.project.web.models.BeneficiarySearchRequest;
import org.egov.project.web.models.ProjectFacilitySearchRequest;
import org.egov.project.web.models.ProjectStaffSearchRequest;
import org.springframework.beans.factory.annotation.Autowired;
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

@javax.annotation.Generated(value = "org.egov.codegen.SpringBootCodegen", date = "2022-12-14T20:57:07.075+05:30")
@Controller
@RequestMapping("")
@Validated
public class ProjectApiController {

    private final ObjectMapper objectMapper;

    private final HttpServletRequest httpServletRequest;

    private final ProjectStaffService projectStaffService;

    private final ProjectBeneficiaryService projectBeneficiaryService;

    private final ProjectTaskService projectTaskService;

    private final ProjectFacilityService projectFacilityService;

    private final Producer producer;

    private final ProjectConfiguration projectConfiguration;

    private final ProjectService projectService;

    @Autowired
    public ProjectApiController(ObjectMapper objectMapper, HttpServletRequest httpServletRequest,
                                ProjectStaffService projectStaffService,
                                ProjectTaskService projectTaskService,
                                ProjectBeneficiaryService projectBeneficiaryService,
                                ProjectFacilityService projectFacilityService, Producer producer,
                                ProjectConfiguration projectConfiguration,
                                ProjectService projectService) {
        this.objectMapper = objectMapper;
        this.httpServletRequest = httpServletRequest;
        this.projectStaffService = projectStaffService;
        this.projectTaskService = projectTaskService;
        this.projectBeneficiaryService = projectBeneficiaryService;
        this.projectFacilityService = projectFacilityService;
        this.producer = producer;
        this.projectConfiguration = projectConfiguration;
        this.projectService = projectService;
    }

    @RequestMapping(value = "/beneficiary/v1/bulk/_create", method = RequestMethod.POST)
    public ResponseEntity<ResponseInfo> projectBeneficiaryV1BulkCreatePost(@ApiParam(value = "Capture details of benificiary type.", required = true) @Valid @RequestBody BeneficiaryBulkRequest beneficiaryRequest) {
        beneficiaryRequest.getRequestInfo().setApiId(httpServletRequest.getRequestURI());
        projectBeneficiaryService.putInCache(beneficiaryRequest.getProjectBeneficiaries());
        producer.push(projectConfiguration.getBulkCreateProjectBeneficiaryTopic(), beneficiaryRequest);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ResponseInfoFactory
                .createResponseInfo(beneficiaryRequest.getRequestInfo(), true));
    }

    @RequestMapping(value = "/beneficiary/v1/_create", method = RequestMethod.POST)
    public ResponseEntity<BeneficiaryResponse> projectBeneficiaryV1CreatePost(@ApiParam(value = "Capture details of benificiary type.", required = true) @Valid @RequestBody BeneficiaryRequest beneficiaryRequest) {


        List<ProjectBeneficiary> projectBeneficiaries = projectBeneficiaryService.create(beneficiaryRequest);
        BeneficiaryResponse response = BeneficiaryResponse.builder()
                .projectBeneficiary(projectBeneficiaries.get(0))
                .responseInfo(ResponseInfoFactory
                        .createResponseInfo(beneficiaryRequest.getRequestInfo(), true))
                .build();
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @RequestMapping(value = "/beneficiary/v1/_search", method = RequestMethod.POST)
    public ResponseEntity<BeneficiaryBulkResponse> projectBeneficiaryV1SearchPost(@ApiParam(value = "Project Beneficiary Search.", required = true) @Valid @RequestBody BeneficiarySearchRequest beneficiarySearchRequest, @NotNull
    @Min(0)
    @Max(1000) @ApiParam(value = "Pagination - limit records in response", required = true) @Valid @RequestParam(value = "limit", required = true) Integer limit, @NotNull
                                                                              @Min(0) @ApiParam(value = "Pagination - offset from which records should be returned in response", required = true) @Valid @RequestParam(value = "offset", required = true) Integer offset, @NotNull @ApiParam(value = "Unique id for a tenant.", required = true) @Valid @RequestParam(value = "tenantId", required = true) String tenantId, @ApiParam(value = "epoch of the time since when the changes on the object should be picked up. Search results from this parameter should include both newly created objects since this time as well as any modified objects since this time. This criterion is included to help polling clients to get the changes in system since a last time they synchronized with the platform. ") @Valid @RequestParam(value = "lastChangedSince", required = false) Long lastChangedSince, @ApiParam(value = "Used in search APIs to specify if (soft) deleted records should be included in search results.", defaultValue = "false") @Valid @RequestParam(value = "includeDeleted", required = false, defaultValue = "false") Boolean includeDeleted) throws Exception {
        List<ProjectBeneficiary> projectBeneficiaries = projectBeneficiaryService.search(
                beneficiarySearchRequest,
                limit,
                offset,
                tenantId,
                lastChangedSince,
                includeDeleted
        );
        BeneficiaryBulkResponse beneficiaryResponse = BeneficiaryBulkResponse.builder()
                .projectBeneficiaries(projectBeneficiaries)
                .responseInfo(ResponseInfoFactory
                        .createResponseInfo(beneficiarySearchRequest.getRequestInfo(), true))
                .build();

        return ResponseEntity.status(HttpStatus.OK).body(beneficiaryResponse);
    }

    @RequestMapping(value = "/beneficiary/v1/_update", method = RequestMethod.POST)
    public ResponseEntity<BeneficiaryResponse> projectBeneficiaryV1UpdatePost(@ApiParam(value = "Project Beneficiary Registration.", required = true) @Valid @RequestBody BeneficiaryRequest beneficiaryRequest, @ApiParam(value = "Client can specify if the resource in request body needs to be sent back in the response. This is being used to limit amount of data that needs to flow back from the server to the client in low bandwidth scenarios. Server will always send the server generated id for validated requests.", defaultValue = "true") @Valid @RequestParam(value = "echoResource", required = false, defaultValue = "true") Boolean echoResource) {

        List<ProjectBeneficiary> projectBeneficiaries = projectBeneficiaryService.update(beneficiaryRequest);
        BeneficiaryResponse response = BeneficiaryResponse.builder()
                .projectBeneficiary(projectBeneficiaries.get(0))
                .responseInfo(ResponseInfoFactory
                        .createResponseInfo(beneficiaryRequest.getRequestInfo(), true))
                .build();

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @RequestMapping(value = "/beneficiary/v1/bulk/_update", method = RequestMethod.POST)
    public ResponseEntity<ResponseInfo> projectBeneficiaryV1BulkUpdatePost(@ApiParam(value = "Project Beneficiary Registration.", required = true) @Valid @RequestBody BeneficiaryBulkRequest beneficiaryRequest, @ApiParam(value = "Client can specify if the resource in request body needs to be sent back in the response. This is being used to limit amount of data that needs to flow back from the server to the client in low bandwidth scenarios. Server will always send the server generated id for validated requests.", defaultValue = "true") @Valid @RequestParam(value = "echoResource", required = false, defaultValue = "true") Boolean echoResource) {

        beneficiaryRequest.getRequestInfo().setApiId(httpServletRequest.getRequestURI());
        producer.push(projectConfiguration.getBulkUpdateProjectBeneficiaryTopic(), beneficiaryRequest);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ResponseInfoFactory
                .createResponseInfo(beneficiaryRequest.getRequestInfo(), true));
    }

    @RequestMapping(value = "/beneficiary/v1/bulk/_delete", method = RequestMethod.POST)
    public ResponseEntity<ResponseInfo> projectBeneficiaryV1BulkDeletePost(@ApiParam(value = "Capture details of benificiary type.", required = true) @Valid @RequestBody BeneficiaryBulkRequest beneficiaryRequest) {
        beneficiaryRequest.getRequestInfo().setApiId(httpServletRequest.getRequestURI());
        producer.push(projectConfiguration.getBulkDeleteProjectBeneficiaryTopic(), beneficiaryRequest);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ResponseInfoFactory
                .createResponseInfo(beneficiaryRequest.getRequestInfo(), true));
    }

    @RequestMapping(value = "/beneficiary/v1/_delete", method = RequestMethod.POST)
    public ResponseEntity<BeneficiaryResponse> projectBeneficiaryV1DeletePost(@ApiParam(value = "Capture details of benificiary type.", required = true) @Valid @RequestBody BeneficiaryRequest beneficiaryRequest) {


        List<ProjectBeneficiary> projectBeneficiaries = projectBeneficiaryService.delete(beneficiaryRequest);
        BeneficiaryResponse response = BeneficiaryResponse.builder()
                .projectBeneficiary(projectBeneficiaries.get(0))
                .responseInfo(ResponseInfoFactory
                        .createResponseInfo(beneficiaryRequest.getRequestInfo(), true))
                .build();
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @RequestMapping(value = "/facility/v1/_create", method = RequestMethod.POST)
    public ResponseEntity<ProjectFacilityResponse> projectFacilityV1CreatePost(@ApiParam(value = "Capture linkage of Project and facility.", required = true) @Valid @RequestBody ProjectFacilityRequest request) {

        ProjectFacility projectFacility = projectFacilityService.create(request);
        ProjectFacilityResponse response = ProjectFacilityResponse.builder()
                .projectFacility(projectFacility)
                .responseInfo(ResponseInfoFactory
                        .createResponseInfo(request.getRequestInfo(), true))
                .build();
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @RequestMapping(value = "/facility/v1/bulk/_create", method = RequestMethod.POST)
    public ResponseEntity<ResponseInfo> projectFacilityV1BulkCreatePost(@ApiParam(value = "Capture linkage of Project and facility.", required = true) @Valid @RequestBody ProjectFacilityBulkRequest request) {
        request.getRequestInfo().setApiId(httpServletRequest.getRequestURI());
        producer.push(projectConfiguration.getBulkCreateProjectFacilityTopic(), request);

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ResponseInfoFactory
                .createResponseInfo(request.getRequestInfo(), true));
    }

    @RequestMapping(value = "/facility/v1/_search", method = RequestMethod.POST)
    public ResponseEntity<ProjectFacilityBulkResponse> projectFacilityV1SearchPost(@ApiParam(value = "Capture details of Project facility.", required = true) @Valid @RequestBody ProjectFacilitySearchRequest projectFacilitySearchRequest,
                                                                                   @NotNull @Min(0) @Max(1000) @ApiParam(value = "Pagination - limit records in response", required = true) @Valid @RequestParam(value = "limit", required = true) Integer limit,
                                                                                   @NotNull @Min(0) @ApiParam(value = "Pagination - offset from which records should be returned in response", required = true) @Valid @RequestParam(value = "offset", required = true) Integer offset,
                                                                                   @NotNull @ApiParam(value = "Unique id for a tenant.", required = true) @Valid @RequestParam(value = "tenantId", required = true) String tenantId,
                                                                                   @ApiParam(value = "epoch of the time since when the changes on the object should be picked up. Search results from this parameter should include both newly created objects since this time as well as any modified objects since this time. This criterion is included to help polling clients to get the changes in system since a last time they synchronized with the platform. ") @Valid @RequestParam(value = "lastChangedSince", required = false) Long lastChangedSince,
                                                                                   @ApiParam(value = "Used in search APIs to specify if (soft) deleted records should be included in search results.", defaultValue = "false") @Valid @RequestParam(value = "includeDeleted", required = false, defaultValue = "false") Boolean includeDeleted) throws Exception {
        List<ProjectFacility> projectFacilities = projectFacilityService.search(
                projectFacilitySearchRequest,
                limit,
                offset,
                tenantId,
                lastChangedSince,
                includeDeleted
        );
        ProjectFacilityBulkResponse response = ProjectFacilityBulkResponse.builder()
                .projectFacilities(projectFacilities)
                .responseInfo(ResponseInfoFactory
                        .createResponseInfo(projectFacilitySearchRequest.getRequestInfo(), true))
                .build();

        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @RequestMapping(value = "/facility/v1/_update", method = RequestMethod.POST)
    public ResponseEntity<ProjectFacilityResponse> projectFacilityV1UpdatePost(@ApiParam(value = "Capture linkage of Project and facility.", required = true) @Valid @RequestBody ProjectFacilityRequest projectFacilityUpdateRequest) {

        ProjectFacility projectFacility = projectFacilityService.update(projectFacilityUpdateRequest);
        ProjectFacilityResponse response = ProjectFacilityResponse.builder()
                .projectFacility(projectFacility)
                .responseInfo(ResponseInfoFactory
                        .createResponseInfo(projectFacilityUpdateRequest.getRequestInfo(), true))
                .build();

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @RequestMapping(value = "/facility/v1/bulk/_update", method = RequestMethod.POST)
    public ResponseEntity<ResponseInfo> projectFacilityV1BulkUpdatePost(@ApiParam(value = "Capture linkage of Project and facility.", required = true) @Valid @RequestBody ProjectFacilityBulkRequest request) {
        request.getRequestInfo().setApiId(httpServletRequest.getRequestURI());
        producer.push(projectConfiguration.getBulkUpdateProjectFacilityTopic(), request);

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ResponseInfoFactory
                .createResponseInfo(request.getRequestInfo(), true));
    }

    @RequestMapping(value = "/facility/v1/_delete", method = RequestMethod.POST)
    public ResponseEntity<ProjectFacilityResponse> projectFacilityV1DeletePost(@ApiParam(value = "Capture linkage of Project and facility.", required = true) @Valid @RequestBody ProjectFacilityRequest projectFacilityUpdateRequest) {

        ProjectFacility projectFacilities = projectFacilityService.delete(projectFacilityUpdateRequest);
        ProjectFacilityResponse response = ProjectFacilityResponse.builder()
                .projectFacility(projectFacilities)
                .responseInfo(ResponseInfoFactory
                        .createResponseInfo(projectFacilityUpdateRequest.getRequestInfo(), true))
                .build();

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @RequestMapping(value = "/facility/v1/bulk/_delete", method = RequestMethod.POST)
    public ResponseEntity<ResponseInfo> projectFacilityV1BulkDeletePost(@ApiParam(value = "Capture linkage of Project and facility.", required = true) @Valid @RequestBody ProjectFacilityBulkRequest request) {

        request.getRequestInfo().setApiId(httpServletRequest.getRequestURI());
        producer.push(projectConfiguration.getBulkDeleteProjectFacilityTopic(), request);

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ResponseInfoFactory
                .createResponseInfo(request.getRequestInfo(), true));
    }

    @RequestMapping(value = "/staff/v1/_create", method = RequestMethod.POST)
    public ResponseEntity<ProjectStaffResponse> projectStaffV1CreatePost(@ApiParam(value = "Capture linkage of Project and staff user.", required = true) @Valid @RequestBody ProjectStaffRequest request) {

        ProjectStaff staff = projectStaffService.create(request);
        ProjectStaffResponse response = ProjectStaffResponse.builder()
                .projectStaff(staff)
                .responseInfo(ResponseInfoFactory
                        .createResponseInfo(request.getRequestInfo(), true))
                .build();
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @RequestMapping(value = "/staff/v1/bulk/_create", method = RequestMethod.POST)
    public ResponseEntity<ResponseInfo> projectStaffV1CreatePost(@ApiParam(value = "Capture linkage of Project and staff user.", required = true) @Valid @RequestBody ProjectStaffBulkRequest request) {

        request.getRequestInfo().setApiId(httpServletRequest.getRequestURI());
        producer.push(projectConfiguration.getBulkCreateProjectStaffTopic(), request);

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ResponseInfoFactory
                .createResponseInfo(request.getRequestInfo(), true));
    }

    @RequestMapping(value = "/staff/v1/_search", method = RequestMethod.POST)
    public ResponseEntity<ProjectStaffBulkResponse> projectStaffV1SearchPost(@ApiParam(value = "Capture details of Project staff.", required = true) @Valid @RequestBody ProjectStaffSearchRequest projectStaffSearchRequest,
                                                                             @NotNull @Min(0) @Max(1000) @ApiParam(value = "Pagination - limit records in response", required = true) @Valid @RequestParam(value = "limit", required = true) Integer limit,
                                                                             @NotNull @Min(0) @ApiParam(value = "Pagination - offset from which records should be returned in response", required = true) @Valid @RequestParam(value = "offset", required = true) Integer offset,
                                                                             @NotNull @ApiParam(value = "Unique id for a tenant.", required = true) @Valid @RequestParam(value = "tenantId", required = true) String tenantId,
                                                                             @ApiParam(value = "epoch of the time since when the changes on the object should be picked up. Search results from this parameter should include both newly created objects since this time as well as any modified objects since this time. This criterion is included to help polling clients to get the changes in system since a last time they synchronized with the platform. ") @Valid @RequestParam(value = "lastChangedSince", required = false) Long lastChangedSince,
                                                                             @ApiParam(value = "Used in search APIs to specify if (soft) deleted records should be included in search results.", defaultValue = "false") @Valid @RequestParam(value = "includeDeleted", required = false, defaultValue = "false") Boolean includeDeleted) throws Exception {
        List<ProjectStaff> projectStaffList = projectStaffService.search(
                projectStaffSearchRequest,
                limit,
                offset,
                tenantId,
                lastChangedSince,
                includeDeleted
        );
        ProjectStaffBulkResponse response = ProjectStaffBulkResponse.builder()
                .projectStaff(projectStaffList)
                .responseInfo(ResponseInfoFactory
                        .createResponseInfo(projectStaffSearchRequest.getRequestInfo(), true))
                .build();

        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @RequestMapping(value = "/staff/v1/_update", method = RequestMethod.POST)
    public ResponseEntity<ProjectStaffResponse> projectStaffV1UpdatePost(@ApiParam(value = "Capture linkage of Project and staff user.", required = true) @Valid @RequestBody ProjectStaffRequest projectStaffUpdateRequest) {

        ProjectStaff staff = projectStaffService.update(projectStaffUpdateRequest);
        ProjectStaffResponse response = ProjectStaffResponse.builder()
                .projectStaff(staff)
                .responseInfo(ResponseInfoFactory
                        .createResponseInfo(projectStaffUpdateRequest.getRequestInfo(), true))
                .build();

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @RequestMapping(value = "/staff/v1/bulk/_update", method = RequestMethod.POST)
    public ResponseEntity<ResponseInfo> projectStaffV1UpdatePost(@ApiParam(value = "Capture linkage of Project and staff user.", required = true) @Valid @RequestBody ProjectStaffBulkRequest request) {

        request.getRequestInfo().setApiId(httpServletRequest.getRequestURI());
        producer.push(projectConfiguration.getBulkUpdateProjectStaffTopic(), request);

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ResponseInfoFactory
                .createResponseInfo(request.getRequestInfo(), true));
    }

    @RequestMapping(value = "/staff/v1/_delete", method = RequestMethod.POST)
    public ResponseEntity<ProjectStaffResponse> projectStaffV1DeletePost(@ApiParam(value = "Capture linkage of Project and staff user.", required = true) @Valid @RequestBody ProjectStaffRequest projectStaffUpdateRequest) {

        ProjectStaff staff = projectStaffService.delete(projectStaffUpdateRequest);
        ProjectStaffResponse response = ProjectStaffResponse.builder()
                .projectStaff(staff)
                .responseInfo(ResponseInfoFactory
                        .createResponseInfo(projectStaffUpdateRequest.getRequestInfo(), true))
                .build();

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @RequestMapping(value = "/staff/v1/bulk/_delete", method = RequestMethod.POST)
    public ResponseEntity<ResponseInfo> projectStaffV1DeletePost(@ApiParam(value = "Capture linkage of Project and staff user.", required = true) @Valid @RequestBody ProjectStaffBulkRequest request) {

        request.getRequestInfo().setApiId(httpServletRequest.getRequestURI());
        producer.push(projectConfiguration.getBulkDeleteProjectStaffTopic(), request);

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ResponseInfoFactory
                .createResponseInfo(request.getRequestInfo(), true));
    }

    @RequestMapping(value = "/task/v1/_create", method = RequestMethod.POST)
    public ResponseEntity<TaskResponse> projectTaskV1CreatePost(@ApiParam(value = "Capture details of Task", required = true) @Valid @RequestBody TaskRequest request) {

        Task task = projectTaskService.create(request);
        TaskResponse response = TaskResponse.builder()
                .task(task)
                .responseInfo(ResponseInfoFactory
                        .createResponseInfo(request.getRequestInfo(), true))
                .build();

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }



    @RequestMapping(value = "/task/v1/bulk/_create", method = RequestMethod.POST)
    public ResponseEntity<ResponseInfo> projectTaskBulkV1CreatePost(@ApiParam(value = "Capture details of Task", required = true) @Valid @RequestBody TaskBulkRequest request) {
        request.getRequestInfo().setApiId(httpServletRequest.getRequestURI());
        projectTaskService.putInCache(request.getTasks());
        producer.push(projectConfiguration.getCreateProjectTaskBulkTopic(), request);

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ResponseInfoFactory
                .createResponseInfo(request.getRequestInfo(), true));
    }

    @RequestMapping(value = "/task/v1/_search", method = RequestMethod.POST)
    public ResponseEntity<TaskBulkResponse> projectTaskV1SearchPost(@ApiParam(value = "Project Task Search.", required = true) @Valid @RequestBody TaskSearchRequest request,
                                                                    @NotNull @Min(0) @Max(1000) @ApiParam(value = "Pagination - limit records in response", required = true) @Valid @RequestParam(value = "limit", required = true) Integer limit,
                                                                    @NotNull @Min(0) @ApiParam(value = "Pagination - offset from which records should be returned in response", required = true) @Valid @RequestParam(value = "offset", required = true) Integer offset,
                                                                    @NotNull @ApiParam(value = "Unique id for a tenant.", required = true) @Valid @RequestParam(value = "tenantId", required = true) String tenantId,
                                                                    @ApiParam(value = "epoch of the time since when the changes on the object should be picked up. Search results from this parameter should include both newly created objects since this time as well as any modified objects since this time. This criterion is included to help polling clients to get the changes in system since a last time they synchronized with the platform. ") @Valid @RequestParam(value = "lastChangedSince", required = false) Long lastChangedSince,
                                                                    @ApiParam(value = "Used in search APIs to specify if (soft) deleted records should be included in search results.", defaultValue = "false") @Valid @RequestParam(value = "includeDeleted", required = false, defaultValue = "false") Boolean includeDeleted) {

        List<Task> households = projectTaskService.search(request.getTask(), limit, offset, tenantId, lastChangedSince, includeDeleted);
        TaskBulkResponse response = TaskBulkResponse.builder().responseInfo(ResponseInfoFactory
                .createResponseInfo(request.getRequestInfo(), true)).tasks(households).build();

        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @RequestMapping(value = "/task/v1/_update", method = RequestMethod.POST)
    public ResponseEntity<TaskResponse> projectTaskV1UpdatePost(@ApiParam(value = "Capture details of Existing task", required = true) @Valid @RequestBody TaskRequest request) {
       Task task = projectTaskService.update(request);

        TaskResponse response = TaskResponse.builder()
                .task(task)
                .responseInfo(ResponseInfoFactory
                        .createResponseInfo(request.getRequestInfo(), true))
                .build();

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);

    }

    @RequestMapping(value = "/task/v1/bulk/_update", method = RequestMethod.POST)
    public ResponseEntity<ResponseInfo> projectTaskV1BulkUpdatePost(@ApiParam(value = "Capture details of Existing task", required = true) @Valid @RequestBody TaskBulkRequest request) {
        request.getRequestInfo().setApiId(httpServletRequest.getRequestURI());
        producer.push(projectConfiguration.getUpdateProjectTaskBulkTopic(), request);

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ResponseInfoFactory
                .createResponseInfo(request.getRequestInfo(), true));
    }

    @RequestMapping(value = "/task/v1/_delete", method = RequestMethod.POST)
    public ResponseEntity<TaskResponse> projectTaskV1DeletePost(@ApiParam(value = "Capture details of Existing task", required = true) @Valid @RequestBody TaskRequest request) {
        Task task = projectTaskService.delete(request);

        TaskResponse response = TaskResponse.builder()
                .task(task)
                .responseInfo(ResponseInfoFactory
                        .createResponseInfo(request.getRequestInfo(), true))
                .build();

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);

    }

    @RequestMapping(value = "/task/v1/bulk/_delete", method = RequestMethod.POST)
    public ResponseEntity<ResponseInfo> projectTaskV1BulkDeletePost(@ApiParam(value = "Capture details of Existing task", required = true) @Valid @RequestBody TaskBulkRequest request) {
        request.getRequestInfo().setApiId(httpServletRequest.getRequestURI());
        producer.push(projectConfiguration.getDeleteProjectTaskBulkTopic(), request);

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ResponseInfoFactory
                .createResponseInfo(request.getRequestInfo(), true));
    }

    @RequestMapping(value = "/v1/_create", method = RequestMethod.POST)
    public ResponseEntity<ProjectResponse> createProject(@ApiParam(value = "Details for the new Project.", required = true) @Valid @RequestBody ProjectRequest project) {
        ProjectRequest enrichedProjectRequest = projectService.createProject(project);
        ResponseInfo responseInfo = ResponseInfoFactory.createResponseInfo(project.getRequestInfo(), true);
        ProjectResponse projectResponse = ProjectResponse.builder().responseInfo(responseInfo).project(enrichedProjectRequest.getProjects()).build();
        return new ResponseEntity<ProjectResponse>(projectResponse,HttpStatus.OK);
    }

    @RequestMapping(value = "/v1/_search", method = RequestMethod.POST)
    public ResponseEntity<ProjectResponse> searchProject(@ApiParam(value = "Details for the project.", required = true) @Valid @RequestBody ProjectRequest project, @NotNull @Min(0) @Max(1000) @ApiParam(value = "Pagination - limit records in response", required = true) @Valid @RequestParam(value = "limit", required = true) Integer limit, @NotNull @Min(0) @ApiParam(value = "Pagination - offset from which records should be returned in response", required = true) @Valid @RequestParam(value = "offset", required = true) Integer offset, @NotNull @ApiParam(value = "Unique id for a tenant.", required = true) @Valid @RequestParam(value = "tenantId", required = true) String tenantId, @ApiParam(value = "epoch of the time since when the changes on the object should be picked up. Search results from this parameter should include both newly created objects since this time as well as any modified objects since this time. This criterion is included to help polling clients to get the changes in system since a last time they synchronized with the platform. ") @Valid @RequestParam(value = "lastChangedSince", required = false) Long lastChangedSince, @ApiParam(value = "Used in search APIs to specify if (soft) deleted records should be included in search results.", defaultValue = "false") @Valid @RequestParam(value = "includeDeleted", required = false, defaultValue = "false") Boolean includeDeleted , @ApiParam(value = "Used in project search API to specify if response should include project elements that are in the preceding hierarchy of matched projects.", defaultValue = "false") @Valid @RequestParam(value = "includeAncestors", required = false, defaultValue = "false") Boolean includeAncestors,  @ApiParam(value = "Used in project search API to specify if response should include project elements that are in the following hierarchy of matched projects.", defaultValue = "false") @Valid @RequestParam(value = "includeDescendants", required = false, defaultValue = "false") Boolean includeDescendants, @ApiParam(value = "Used in project search API to limit the search results to only those projects whose creation date is after the specified 'createdFrom' date", defaultValue = "false") @Valid @RequestParam(value = "createdFrom", required = false) Long createdFrom, @ApiParam(value = "Used in project search API to limit the search results to only those projects whose creation date is before the specified 'createdTo' date", defaultValue = "false") @Valid @RequestParam(value = "createdTo", required = false) Long createdTo) {
        List<Project> projects = projectService.searchProject(project, limit, offset, tenantId, lastChangedSince, includeDeleted, includeAncestors, includeDescendants, createdFrom, createdTo);
        ResponseInfo responseInfo = ResponseInfoFactory.createResponseInfo(project.getRequestInfo(), true);
        Integer count = projectService.countAllProjects(project, tenantId, lastChangedSince, includeDeleted, createdFrom, createdTo);
        ProjectResponse projectResponse = ProjectResponse.builder().responseInfo(responseInfo).project(projects).totalCount(count).build();
        return new ResponseEntity<ProjectResponse>(projectResponse, HttpStatus.OK);
    }

    @RequestMapping(value = "/v1/_update", method = RequestMethod.POST)
    public ResponseEntity<ProjectResponse> updateProject(@ApiParam(value = "Details for the updated Project.", required = true) @Valid @RequestBody ProjectRequest project) {
        ProjectRequest enrichedProjectRequest = projectService.updateProject(project);

        ResponseInfo responseInfo = ResponseInfoFactory.createResponseInfo(project.getRequestInfo(), true);
        ProjectResponse projectResponse = ProjectResponse.builder().responseInfo(responseInfo).project(enrichedProjectRequest.getProjects()).build();
        return new ResponseEntity<ProjectResponse>(projectResponse, HttpStatus.OK);
    }

}
