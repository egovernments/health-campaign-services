package org.egov.project.web.controllers;


import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.annotations.ApiParam;
import org.egov.common.utils.CommonUtils;
import org.egov.common.utils.ResponseInfoFactory;
import org.egov.project.service.ProjectBeneficiaryService;
import org.egov.project.service.ProjectStaffService;
import org.egov.project.web.models.BeneficiaryRequest;
import org.egov.project.web.models.BeneficiaryResponse;
import org.egov.project.web.models.BeneficiarySearchRequest;
import org.egov.project.web.models.ProjectBeneficiary;
import org.egov.project.web.models.ProjectFacilityRequest;
import org.egov.project.web.models.ProjectFacilityResponse;
import org.egov.project.web.models.ProjectFacilitySearchRequest;
import org.egov.project.web.models.ProjectRequest;
import org.egov.project.web.models.ProjectResourceRequest;
import org.egov.project.web.models.ProjectResourceResponse;
import org.egov.project.web.models.ProjectResourceSearchRequest;
import org.egov.project.web.models.ProjectResponse;
import org.egov.project.web.models.ProjectSearchRequest;
import org.egov.project.web.models.ProjectStaff;
import org.egov.project.web.models.ProjectStaffRequest;
import org.egov.project.web.models.ProjectStaffResponse;
import org.egov.project.web.models.ProjectStaffSearchRequest;
import org.egov.project.web.models.TaskRequest;
import org.egov.project.web.models.TaskResponse;
import org.egov.project.web.models.TaskSearchRequest;
import org.egov.tracer.model.CustomException;
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
import java.io.IOException;
import java.util.List;

@javax.annotation.Generated(value = "org.egov.codegen.SpringBootCodegen", date = "2022-12-14T20:57:07.075+05:30")

@Controller
@RequestMapping("")
public class ProjectApiController {

    private final ObjectMapper objectMapper;

    private final HttpServletRequest request;

    private final ProjectStaffService projectStaffService;

    private final ProjectBeneficiaryService projectBeneficiaryService;

    @Autowired
    public ProjectApiController(ObjectMapper objectMapper, HttpServletRequest request, ProjectStaffService projectStaffService, ProjectBeneficiaryService projectBeneficiaryService) {
        this.objectMapper = objectMapper;
        this.request = request;
        this.projectStaffService = projectStaffService;
        this.projectBeneficiaryService = projectBeneficiaryService;
    }

    @RequestMapping(value = "/beneficiary/v1/_create", method = RequestMethod.POST)
    public ResponseEntity<BeneficiaryResponse> projectBeneficiaryV1CreatePost(@ApiParam(value = "Capture details of benificiary type.", required = true) @Valid @RequestBody BeneficiaryRequest beneficiaryRequest) throws Exception {
        if (!CommonUtils.isForCreate(beneficiaryRequest)){
            throw new CustomException("INVALID_API_OPERATION", String.format("API Operation %s not valid for create request", beneficiaryRequest.getApiOperation()));
        }

        List<ProjectBeneficiary> projectBeneficiaries = projectBeneficiaryService.create(beneficiaryRequest);
        BeneficiaryResponse response = BeneficiaryResponse.builder()
                .projectBeneficiary(projectBeneficiaries)
                .responseInfo(ResponseInfoFactory
                        .createResponseInfo(beneficiaryRequest.getRequestInfo(), true))
                .build();
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @RequestMapping(value = "/beneficiary/v1/_search", method = RequestMethod.POST)
    public ResponseEntity<BeneficiaryResponse> projectBeneficiaryV1SearchPost(@ApiParam(value = "Project Beneficiary Search.", required = true) @Valid @RequestBody BeneficiarySearchRequest beneficiarySearchRequest, @NotNull
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
        BeneficiaryResponse beneficiaryResponse = BeneficiaryResponse.builder()
                .projectBeneficiary(projectBeneficiaries)
                .responseInfo(ResponseInfoFactory
                        .createResponseInfo(beneficiarySearchRequest.getRequestInfo(), true))
                .build();

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(beneficiaryResponse);
    }

    @RequestMapping(value = "/beneficiary/v1/_update", method = RequestMethod.POST)
    public ResponseEntity<BeneficiaryResponse> projectBeneficiaryV1UpdatePost(@ApiParam(value = "Project Beneficiary Registration.", required = true) @Valid @RequestBody BeneficiaryRequest beneficiaryRequest, @ApiParam(value = "Client can specify if the resource in request body needs to be sent back in the response. This is being used to limit amount of data that needs to flow back from the server to the client in low bandwidth scenarios. Server will always send the server generated id for validated requests.", defaultValue = "true") @Valid @RequestParam(value = "echoResource", required = false, defaultValue = "true") Boolean echoResource) throws Exception {
        if (!CommonUtils.isForUpdate(beneficiaryRequest)
                && !CommonUtils.isForDelete(beneficiaryRequest)) {
            throw new CustomException("INVALID_API_OPERATION", String.format("API Operation %s not valid for update request",
                    beneficiaryRequest.getApiOperation()));
        }

        List<ProjectBeneficiary> projectBeneficiaries = projectBeneficiaryService.update(beneficiaryRequest);
        BeneficiaryResponse response = BeneficiaryResponse.builder()
                .projectBeneficiary(projectBeneficiaries)
                .responseInfo(ResponseInfoFactory
                        .createResponseInfo(beneficiaryRequest.getRequestInfo(), true))
                .build();

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @RequestMapping(value = "/facility/v1/_create", method = RequestMethod.POST)
    public ResponseEntity<ProjectFacilityResponse> projectFacilityV1CreatePost(@ApiParam(value = "Capture linkage of Project and facility.", required = true) @Valid @RequestBody ProjectFacilityRequest projectFacility) {
        String accept = request.getHeader("Accept");
        if (accept != null && accept.contains("application/json")) {
            try {
                return new ResponseEntity<ProjectFacilityResponse>(objectMapper.readValue("{  \"ResponseInfo\" : {    \"ver\" : \"ver\",    \"resMsgId\" : \"resMsgId\",    \"msgId\" : \"msgId\",    \"apiId\" : \"apiId\",    \"ts\" : 0,    \"status\" : \"SUCCESSFUL\"  },  \"ProjectFacility\" : [ {    \"facilityId\" : \"facilityId\",    \"isDeleted\" : { },    \"rowVersion\" : { },    \"auditDetails\" : {      \"lastModifiedTime\" : 2,      \"createdBy\" : \"createdBy\",      \"lastModifiedBy\" : \"lastModifiedBy\",      \"createdTime\" : 3    },    \"tenantId\" : \"tenantA\",    \"id\" : { },    \"projectId\" : \"projectId\"  }, {    \"facilityId\" : \"facilityId\",    \"isDeleted\" : { },    \"rowVersion\" : { },    \"auditDetails\" : {      \"lastModifiedTime\" : 2,      \"createdBy\" : \"createdBy\",      \"lastModifiedBy\" : \"lastModifiedBy\",      \"createdTime\" : 3    },    \"tenantId\" : \"tenantA\",    \"id\" : { },    \"projectId\" : \"projectId\"  } ]}", ProjectFacilityResponse.class), HttpStatus.NOT_IMPLEMENTED);
            } catch (IOException e) {
                return new ResponseEntity<ProjectFacilityResponse>(HttpStatus.INTERNAL_SERVER_ERROR);
            }
        }

        return new ResponseEntity<ProjectFacilityResponse>(HttpStatus.NOT_IMPLEMENTED);
    }

    @RequestMapping(value = "/facility/v1/_search", method = RequestMethod.POST)
    public ResponseEntity<ProjectFacilityResponse> projectFacilityV1SearchPost(@ApiParam(value = "Capture linkage of Project and facility.", required = true) @Valid @RequestBody ProjectFacilitySearchRequest projectFacility, @NotNull
    @Min(0)
    @Max(1000) @ApiParam(value = "Pagination - limit records in response", required = true) @Valid @RequestParam(value = "limit", required = true) Integer limit, @NotNull
                                                                               @Min(0) @ApiParam(value = "Pagination - offset from which records should be returned in response", required = true) @Valid @RequestParam(value = "offset", required = true) Integer offset, @NotNull @ApiParam(value = "Unique id for a tenant.", required = true) @Valid @RequestParam(value = "tenantId", required = true) String tenantId, @ApiParam(value = "epoch of the time since when the changes on the object should be picked up. Search results from this parameter should include both newly created objects since this time as well as any modified objects since this time. This criterion is included to help polling clients to get the changes in system since a last time they synchronized with the platform. ") @Valid @RequestParam(value = "lastChangedSince", required = false) Long lastChangedSince, @ApiParam(value = "Used in search APIs to specify if (soft) deleted records should be included in search results.", defaultValue = "false") @Valid @RequestParam(value = "includeDeleted", required = false, defaultValue = "false") Boolean includeDeleted) {
        String accept = request.getHeader("Accept");
        if (accept != null && accept.contains("application/json")) {
            try {
                return new ResponseEntity<ProjectFacilityResponse>(objectMapper.readValue("{  \"ResponseInfo\" : {    \"ver\" : \"ver\",    \"resMsgId\" : \"resMsgId\",    \"msgId\" : \"msgId\",    \"apiId\" : \"apiId\",    \"ts\" : 0,    \"status\" : \"SUCCESSFUL\"  },  \"ProjectFacility\" : [ {    \"facilityId\" : \"facilityId\",    \"isDeleted\" : { },    \"rowVersion\" : { },    \"auditDetails\" : {      \"lastModifiedTime\" : 2,      \"createdBy\" : \"createdBy\",      \"lastModifiedBy\" : \"lastModifiedBy\",      \"createdTime\" : 3    },    \"tenantId\" : \"tenantA\",    \"id\" : { },    \"projectId\" : \"projectId\"  }, {    \"facilityId\" : \"facilityId\",    \"isDeleted\" : { },    \"rowVersion\" : { },    \"auditDetails\" : {      \"lastModifiedTime\" : 2,      \"createdBy\" : \"createdBy\",      \"lastModifiedBy\" : \"lastModifiedBy\",      \"createdTime\" : 3    },    \"tenantId\" : \"tenantA\",    \"id\" : { },    \"projectId\" : \"projectId\"  } ]}", ProjectFacilityResponse.class), HttpStatus.NOT_IMPLEMENTED);
            } catch (IOException e) {
                return new ResponseEntity<ProjectFacilityResponse>(HttpStatus.INTERNAL_SERVER_ERROR);
            }
        }

        return new ResponseEntity<ProjectFacilityResponse>(HttpStatus.NOT_IMPLEMENTED);
    }

    @RequestMapping(value = "/facility/v1/_update", method = RequestMethod.POST)
    public ResponseEntity<ProjectFacilityResponse> projectFacilityV1UpdatePost(@ApiParam(value = "Capture linkage of Project and facility.", required = true) @Valid @RequestBody ProjectFacilityRequest projectFacility) {
        String accept = request.getHeader("Accept");
        if (accept != null && accept.contains("application/json")) {
            try {
                return new ResponseEntity<ProjectFacilityResponse>(objectMapper.readValue("{  \"ResponseInfo\" : {    \"ver\" : \"ver\",    \"resMsgId\" : \"resMsgId\",    \"msgId\" : \"msgId\",    \"apiId\" : \"apiId\",    \"ts\" : 0,    \"status\" : \"SUCCESSFUL\"  },  \"ProjectFacility\" : [ {    \"facilityId\" : \"facilityId\",    \"isDeleted\" : { },    \"rowVersion\" : { },    \"auditDetails\" : {      \"lastModifiedTime\" : 2,      \"createdBy\" : \"createdBy\",      \"lastModifiedBy\" : \"lastModifiedBy\",      \"createdTime\" : 3    },    \"tenantId\" : \"tenantA\",    \"id\" : { },    \"projectId\" : \"projectId\"  }, {    \"facilityId\" : \"facilityId\",    \"isDeleted\" : { },    \"rowVersion\" : { },    \"auditDetails\" : {      \"lastModifiedTime\" : 2,      \"createdBy\" : \"createdBy\",      \"lastModifiedBy\" : \"lastModifiedBy\",      \"createdTime\" : 3    },    \"tenantId\" : \"tenantA\",    \"id\" : { },    \"projectId\" : \"projectId\"  } ]}", ProjectFacilityResponse.class), HttpStatus.NOT_IMPLEMENTED);
            } catch (IOException e) {
                return new ResponseEntity<ProjectFacilityResponse>(HttpStatus.INTERNAL_SERVER_ERROR);
            }
        }

        return new ResponseEntity<ProjectFacilityResponse>(HttpStatus.NOT_IMPLEMENTED);
    }

    @RequestMapping(value = "/resource/v1/_create", method = RequestMethod.POST)
    public ResponseEntity<ProjectResourceResponse> projectResourceV1CreatePost(@ApiParam(value = "Capture linkage of Project and resources.", required = true) @Valid @RequestBody ProjectResourceRequest projectResource) {
        String accept = request.getHeader("Accept");
        if (accept != null && accept.contains("application/json")) {
            try {
                return new ResponseEntity<ProjectResourceResponse>(objectMapper.readValue("{  \"ResponseInfo\" : {    \"ver\" : \"ver\",    \"resMsgId\" : \"resMsgId\",    \"msgId\" : \"msgId\",    \"apiId\" : \"apiId\",    \"ts\" : 0,    \"status\" : \"SUCCESSFUL\"  },  \"ProjectResource\" : [ {    \"isDeleted\" : { },    \"rowVersion\" : { },    \"auditDetails\" : {      \"lastModifiedTime\" : 2,      \"createdBy\" : \"createdBy\",      \"lastModifiedBy\" : \"lastModifiedBy\",      \"createdTime\" : 3    },    \"tenantId\" : \"tenantA\",    \"resources\" : [ {      \"productVariantId\" : \"productVariantId\",      \"isBaseUnitVariant\" : true,      \"type\" : \"type\"    }, {      \"productVariantId\" : \"productVariantId\",      \"isBaseUnitVariant\" : true,      \"type\" : \"type\"    } ],    \"id\" : { },    \"projectId\" : \"projectId\"  }, {    \"isDeleted\" : { },    \"rowVersion\" : { },    \"auditDetails\" : {      \"lastModifiedTime\" : 2,      \"createdBy\" : \"createdBy\",      \"lastModifiedBy\" : \"lastModifiedBy\",      \"createdTime\" : 3    },    \"tenantId\" : \"tenantA\",    \"resources\" : [ {      \"productVariantId\" : \"productVariantId\",      \"isBaseUnitVariant\" : true,      \"type\" : \"type\"    }, {      \"productVariantId\" : \"productVariantId\",      \"isBaseUnitVariant\" : true,      \"type\" : \"type\"    } ],    \"id\" : { },    \"projectId\" : \"projectId\"  } ]}", ProjectResourceResponse.class), HttpStatus.NOT_IMPLEMENTED);
            } catch (IOException e) {
                return new ResponseEntity<ProjectResourceResponse>(HttpStatus.INTERNAL_SERVER_ERROR);
            }
        }

        return new ResponseEntity<ProjectResourceResponse>(HttpStatus.NOT_IMPLEMENTED);
    }

    @RequestMapping(value = "/resource/v1/_search", method = RequestMethod.POST)
    public ResponseEntity<ProjectResourceResponse> projectResourceV1SearchPost(@ApiParam(value = "Search linkage of Project and resource.", required = true) @Valid @RequestBody ProjectResourceSearchRequest projectResource, @NotNull
    @Min(0)
    @Max(1000) @ApiParam(value = "Pagination - limit records in response", required = true) @Valid @RequestParam(value = "limit", required = true) Integer limit, @NotNull
                                                                               @Min(0) @ApiParam(value = "Pagination - offset from which records should be returned in response", required = true) @Valid @RequestParam(value = "offset", required = true) Integer offset, @NotNull @ApiParam(value = "Unique id for a tenant.", required = true) @Valid @RequestParam(value = "tenantId", required = true) String tenantId, @ApiParam(value = "epoch of the time since when the changes on the object should be picked up. Search results from this parameter should include both newly created objects since this time as well as any modified objects since this time. This criterion is included to help polling clients to get the changes in system since a last time they synchronized with the platform. ") @Valid @RequestParam(value = "lastChangedSince", required = false) Long lastChangedSince, @ApiParam(value = "Used in search APIs to specify if (soft) deleted records should be included in search results.", defaultValue = "false") @Valid @RequestParam(value = "includeDeleted", required = false, defaultValue = "false") Boolean includeDeleted) {
        String accept = request.getHeader("Accept");
        if (accept != null && accept.contains("application/json")) {
            try {
                return new ResponseEntity<ProjectResourceResponse>(objectMapper.readValue("{  \"ResponseInfo\" : {    \"ver\" : \"ver\",    \"resMsgId\" : \"resMsgId\",    \"msgId\" : \"msgId\",    \"apiId\" : \"apiId\",    \"ts\" : 0,    \"status\" : \"SUCCESSFUL\"  },  \"ProjectResource\" : [ {    \"isDeleted\" : { },    \"rowVersion\" : { },    \"auditDetails\" : {      \"lastModifiedTime\" : 2,      \"createdBy\" : \"createdBy\",      \"lastModifiedBy\" : \"lastModifiedBy\",      \"createdTime\" : 3    },    \"tenantId\" : \"tenantA\",    \"resources\" : [ {      \"productVariantId\" : \"productVariantId\",      \"isBaseUnitVariant\" : true,      \"type\" : \"type\"    }, {      \"productVariantId\" : \"productVariantId\",      \"isBaseUnitVariant\" : true,      \"type\" : \"type\"    } ],    \"id\" : { },    \"projectId\" : \"projectId\"  }, {    \"isDeleted\" : { },    \"rowVersion\" : { },    \"auditDetails\" : {      \"lastModifiedTime\" : 2,      \"createdBy\" : \"createdBy\",      \"lastModifiedBy\" : \"lastModifiedBy\",      \"createdTime\" : 3    },    \"tenantId\" : \"tenantA\",    \"resources\" : [ {      \"productVariantId\" : \"productVariantId\",      \"isBaseUnitVariant\" : true,      \"type\" : \"type\"    }, {      \"productVariantId\" : \"productVariantId\",      \"isBaseUnitVariant\" : true,      \"type\" : \"type\"    } ],    \"id\" : { },    \"projectId\" : \"projectId\"  } ]}", ProjectResourceResponse.class), HttpStatus.NOT_IMPLEMENTED);
            } catch (IOException e) {
                return new ResponseEntity<ProjectResourceResponse>(HttpStatus.INTERNAL_SERVER_ERROR);
            }
        }

        return new ResponseEntity<ProjectResourceResponse>(HttpStatus.NOT_IMPLEMENTED);
    }

    @RequestMapping(value = "/resource/v1/_update", method = RequestMethod.POST)
    public ResponseEntity<ProjectResourceResponse> projectResourceV1UpdatePost(@ApiParam(value = "Capture linkage of Project and Resource.", required = true) @Valid @RequestBody ProjectResourceRequest projectResource) {
        String accept = request.getHeader("Accept");
        if (accept != null && accept.contains("application/json")) {
            try {
                return new ResponseEntity<ProjectResourceResponse>(objectMapper.readValue("{  \"ResponseInfo\" : {    \"ver\" : \"ver\",    \"resMsgId\" : \"resMsgId\",    \"msgId\" : \"msgId\",    \"apiId\" : \"apiId\",    \"ts\" : 0,    \"status\" : \"SUCCESSFUL\"  },  \"ProjectResource\" : [ {    \"isDeleted\" : { },    \"rowVersion\" : { },    \"auditDetails\" : {      \"lastModifiedTime\" : 2,      \"createdBy\" : \"createdBy\",      \"lastModifiedBy\" : \"lastModifiedBy\",      \"createdTime\" : 3    },    \"tenantId\" : \"tenantA\",    \"resources\" : [ {      \"productVariantId\" : \"productVariantId\",      \"isBaseUnitVariant\" : true,      \"type\" : \"type\"    }, {      \"productVariantId\" : \"productVariantId\",      \"isBaseUnitVariant\" : true,      \"type\" : \"type\"    } ],    \"id\" : { },    \"projectId\" : \"projectId\"  }, {    \"isDeleted\" : { },    \"rowVersion\" : { },    \"auditDetails\" : {      \"lastModifiedTime\" : 2,      \"createdBy\" : \"createdBy\",      \"lastModifiedBy\" : \"lastModifiedBy\",      \"createdTime\" : 3    },    \"tenantId\" : \"tenantA\",    \"resources\" : [ {      \"productVariantId\" : \"productVariantId\",      \"isBaseUnitVariant\" : true,      \"type\" : \"type\"    }, {      \"productVariantId\" : \"productVariantId\",      \"isBaseUnitVariant\" : true,      \"type\" : \"type\"    } ],    \"id\" : { },    \"projectId\" : \"projectId\"  } ]}", ProjectResourceResponse.class), HttpStatus.NOT_IMPLEMENTED);
            } catch (IOException e) {
                return new ResponseEntity<ProjectResourceResponse>(HttpStatus.INTERNAL_SERVER_ERROR);
            }
        }

        return new ResponseEntity<ProjectResourceResponse>(HttpStatus.NOT_IMPLEMENTED);
    }

    @RequestMapping(value = "/staff/v1/_create", method = RequestMethod.POST)
    public ResponseEntity<ProjectStaffResponse> projectStaffV1CreatePost(@ApiParam(value = "Capture linkage of Project and staff user.", required = true) @Valid @RequestBody ProjectStaffRequest projectStaffRequest) throws Exception {
        if (!CommonUtils.isForCreate(projectStaffRequest)){
            throw new CustomException("INVALID_API_OPERATION", String.format("API Operation %s not valid for create request", projectStaffRequest.getApiOperation()));
        }

        List<ProjectStaff> projectStaffs = projectStaffService.create(projectStaffRequest);
        ProjectStaffResponse response = ProjectStaffResponse.builder()
                .projectStaff(projectStaffs)
                .responseInfo(ResponseInfoFactory
                        .createResponseInfo(projectStaffRequest.getRequestInfo(), true))
                .build();
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @RequestMapping(value = "/staff/v1/_search", method = RequestMethod.POST)
    public ResponseEntity<ProjectStaffResponse> projectStaffV1SearchPost(@ApiParam(value = "Capture details of Project staff.", required = true) @Valid @RequestBody ProjectStaffSearchRequest projectStaffSearchRequest,
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
        ProjectStaffResponse projectStaffResponse = ProjectStaffResponse.builder()
                .projectStaff(projectStaffList)
                .responseInfo(ResponseInfoFactory
                        .createResponseInfo(projectStaffSearchRequest.getRequestInfo(), true))
                .build();

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(projectStaffResponse);
    }

    @RequestMapping(value = "/staff/v1/_update", method = RequestMethod.POST)
    public ResponseEntity<ProjectStaffResponse> projectStaffV1UpdatePost(@ApiParam(value = "Capture linkage of Project and staff user.", required = true) @Valid @RequestBody ProjectStaffRequest projectStaffUpdateRequest) throws Exception {
        if (!CommonUtils.isForUpdate(projectStaffUpdateRequest)
                && !CommonUtils.isForDelete(projectStaffUpdateRequest)) {
            throw new CustomException("INVALID_API_OPERATION", String.format("API Operation %s not valid for update request",
                    projectStaffUpdateRequest.getApiOperation()));
        }

        List<ProjectStaff> projectStaffs = projectStaffService.update(projectStaffUpdateRequest);
        ProjectStaffResponse response = ProjectStaffResponse.builder()
                .projectStaff(projectStaffs)
                .responseInfo(ResponseInfoFactory
                        .createResponseInfo(projectStaffUpdateRequest.getRequestInfo(), true))
                .build();

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);    }

    @RequestMapping(value = "/task/v1/_create", method = RequestMethod.POST)
    public ResponseEntity<TaskResponse> projectTaskV1CreatePost(@ApiParam(value = "Capture details of Task", required = true) @Valid @RequestBody TaskRequest task) {
        String accept = request.getHeader("Accept");
        if (accept != null && accept.contains("application/json")) {
            try {
                return new ResponseEntity<TaskResponse>(objectMapper.readValue("{  \"ResponseInfo\" : {    \"ver\" : \"ver\",    \"resMsgId\" : \"resMsgId\",    \"msgId\" : \"msgId\",    \"apiId\" : \"apiId\",    \"ts\" : 0,    \"status\" : \"SUCCESSFUL\"  },  \"Task\" : [ {    \"actualEndDate\" : 5,    \"address\" : {      \"locationAccuracy\" : 5962.133916683182,      \"pincode\" : \"pincode\",      \"city\" : \"city\",      \"latitude\" : 18.494211295267263,      \"locality\" : {        \"code\" : \"code\",        \"materializedPath\" : \"materializedPath\",        \"children\" : [ null, null ],        \"latitude\" : \"latitude\",        \"name\" : \"name\",        \"label\" : \"label\",        \"longitude\" : \"longitude\"      },      \"type\" : \"type\",      \"buildingName\" : \"buildingName\",      \"street\" : \"street\",      \"tenantId\" : \"tenantA\",      \"addressLine1\" : \"addressLine1\",      \"addressLine2\" : \"addressLine2\",      \"id\" : \"id\",      \"doorNo\" : \"doorNo\",      \"landmark\" : \"landmark\",      \"longitude\" : -127.23073270189397    },    \"additionalFields\" : {      \"schema\" : \"HOUSEHOLD\",      \"fields\" : [ {        \"value\" : \"180\",        \"key\" : \"height\"      }, {        \"value\" : \"180\",        \"key\" : \"height\"      } ],      \"version\" : 2    },    \"rowVersion\" : { },    \"resources\" : [ {      \"isDelivered\" : true,      \"quantity\" : \"quantity\",      \"productVariantId\" : \"ID-1\",      \"isDeleted\" : { },      \"auditDetails\" : {        \"lastModifiedTime\" : 2,        \"createdBy\" : \"createdBy\",        \"lastModifiedBy\" : \"lastModifiedBy\",        \"createdTime\" : 3      },      \"tenantId\" : \"tenantA\",      \"deliveryComment\" : \"deliveryComment\",      \"id\" : \"id\"    }, {      \"isDelivered\" : true,      \"quantity\" : \"quantity\",      \"productVariantId\" : \"ID-1\",      \"isDeleted\" : { },      \"auditDetails\" : {        \"lastModifiedTime\" : 2,        \"createdBy\" : \"createdBy\",        \"lastModifiedBy\" : \"lastModifiedBy\",        \"createdTime\" : 3      },      \"tenantId\" : \"tenantA\",      \"deliveryComment\" : \"deliveryComment\",      \"id\" : \"id\"    } ],    \"plannedEndDate\" : 6,    \"projectBeneficiaryId\" : \"R-ID-1\",    \"createdDate\" : 1663218161,    \"plannedStartDate\" : 0,    \"isDeleted\" : null,    \"createdBy\" : \"UUID\",    \"auditDetails\" : {      \"lastModifiedTime\" : 2,      \"createdBy\" : \"createdBy\",      \"lastModifiedBy\" : \"lastModifiedBy\",      \"createdTime\" : 3    },    \"tenantId\" : \"tenantA\",    \"actualStartDate\" : 1,    \"id\" : { },    \"projectId\" : \"projectId\",    \"status\" : \"DELIVERED\"  }, {    \"actualEndDate\" : 5,    \"address\" : {      \"locationAccuracy\" : 5962.133916683182,      \"pincode\" : \"pincode\",      \"city\" : \"city\",      \"latitude\" : 18.494211295267263,      \"locality\" : {        \"code\" : \"code\",        \"materializedPath\" : \"materializedPath\",        \"children\" : [ null, null ],        \"latitude\" : \"latitude\",        \"name\" : \"name\",        \"label\" : \"label\",        \"longitude\" : \"longitude\"      },      \"type\" : \"type\",      \"buildingName\" : \"buildingName\",      \"street\" : \"street\",      \"tenantId\" : \"tenantA\",      \"addressLine1\" : \"addressLine1\",      \"addressLine2\" : \"addressLine2\",      \"id\" : \"id\",      \"doorNo\" : \"doorNo\",      \"landmark\" : \"landmark\",      \"longitude\" : -127.23073270189397    },    \"additionalFields\" : {      \"schema\" : \"HOUSEHOLD\",      \"fields\" : [ {        \"value\" : \"180\",        \"key\" : \"height\"      }, {        \"value\" : \"180\",        \"key\" : \"height\"      } ],      \"version\" : 2    },    \"rowVersion\" : { },    \"resources\" : [ {      \"isDelivered\" : true,      \"quantity\" : \"quantity\",      \"productVariantId\" : \"ID-1\",      \"isDeleted\" : { },      \"auditDetails\" : {        \"lastModifiedTime\" : 2,        \"createdBy\" : \"createdBy\",        \"lastModifiedBy\" : \"lastModifiedBy\",        \"createdTime\" : 3      },      \"tenantId\" : \"tenantA\",      \"deliveryComment\" : \"deliveryComment\",      \"id\" : \"id\"    }, {      \"isDelivered\" : true,      \"quantity\" : \"quantity\",      \"productVariantId\" : \"ID-1\",      \"isDeleted\" : { },      \"auditDetails\" : {        \"lastModifiedTime\" : 2,        \"createdBy\" : \"createdBy\",        \"lastModifiedBy\" : \"lastModifiedBy\",        \"createdTime\" : 3      },      \"tenantId\" : \"tenantA\",      \"deliveryComment\" : \"deliveryComment\",      \"id\" : \"id\"    } ],    \"plannedEndDate\" : 6,    \"projectBeneficiaryId\" : \"R-ID-1\",    \"createdDate\" : 1663218161,    \"plannedStartDate\" : 0,    \"isDeleted\" : null,    \"createdBy\" : \"UUID\",    \"auditDetails\" : {      \"lastModifiedTime\" : 2,      \"createdBy\" : \"createdBy\",      \"lastModifiedBy\" : \"lastModifiedBy\",      \"createdTime\" : 3    },    \"tenantId\" : \"tenantA\",    \"actualStartDate\" : 1,    \"id\" : { },    \"projectId\" : \"projectId\",    \"status\" : \"DELIVERED\"  } ]}", TaskResponse.class), HttpStatus.NOT_IMPLEMENTED);
            } catch (IOException e) {
                return new ResponseEntity<TaskResponse>(HttpStatus.INTERNAL_SERVER_ERROR);
            }
        }

        return new ResponseEntity<TaskResponse>(HttpStatus.NOT_IMPLEMENTED);
    }

    @RequestMapping(value = "/task/v1/_search", method = RequestMethod.POST)
    public ResponseEntity<TaskResponse> projectTaskV1SearchPost(@ApiParam(value = "Project Task Search.", required = true) @Valid @RequestBody TaskSearchRequest task, @NotNull
    @Min(0)
    @Max(1000) @ApiParam(value = "Pagination - limit records in response", required = true) @Valid @RequestParam(value = "limit", required = true) Integer limit, @NotNull
                                                                @Min(0) @ApiParam(value = "Pagination - offset from which records should be returned in response", required = true) @Valid @RequestParam(value = "offset", required = true) Integer offset, @NotNull @ApiParam(value = "Unique id for a tenant.", required = true) @Valid @RequestParam(value = "tenantId", required = true) String tenantId, @ApiParam(value = "epoch of the time since when the changes on the object should be picked up. Search results from this parameter should include both newly created objects since this time as well as any modified objects since this time. This criterion is included to help polling clients to get the changes in system since a last time they synchronized with the platform. ") @Valid @RequestParam(value = "lastChangedSince", required = false) Long lastChangedSince, @ApiParam(value = "Used in search APIs to specify if (soft) deleted records should be included in search results.", defaultValue = "false") @Valid @RequestParam(value = "includeDeleted", required = false, defaultValue = "false") Boolean includeDeleted) {
        String accept = request.getHeader("Accept");
        if (accept != null && accept.contains("application/json")) {
            try {
                return new ResponseEntity<TaskResponse>(objectMapper.readValue("{  \"ResponseInfo\" : {    \"ver\" : \"ver\",    \"resMsgId\" : \"resMsgId\",    \"msgId\" : \"msgId\",    \"apiId\" : \"apiId\",    \"ts\" : 0,    \"status\" : \"SUCCESSFUL\"  },  \"Task\" : [ {    \"actualEndDate\" : 5,    \"address\" : {      \"locationAccuracy\" : 5962.133916683182,      \"pincode\" : \"pincode\",      \"city\" : \"city\",      \"latitude\" : 18.494211295267263,      \"locality\" : {        \"code\" : \"code\",        \"materializedPath\" : \"materializedPath\",        \"children\" : [ null, null ],        \"latitude\" : \"latitude\",        \"name\" : \"name\",        \"label\" : \"label\",        \"longitude\" : \"longitude\"      },      \"type\" : \"type\",      \"buildingName\" : \"buildingName\",      \"street\" : \"street\",      \"tenantId\" : \"tenantA\",      \"addressLine1\" : \"addressLine1\",      \"addressLine2\" : \"addressLine2\",      \"id\" : \"id\",      \"doorNo\" : \"doorNo\",      \"landmark\" : \"landmark\",      \"longitude\" : -127.23073270189397    },    \"additionalFields\" : {      \"schema\" : \"HOUSEHOLD\",      \"fields\" : [ {        \"value\" : \"180\",        \"key\" : \"height\"      }, {        \"value\" : \"180\",        \"key\" : \"height\"      } ],      \"version\" : 2    },    \"rowVersion\" : { },    \"resources\" : [ {      \"isDelivered\" : true,      \"quantity\" : \"quantity\",      \"productVariantId\" : \"ID-1\",      \"isDeleted\" : { },      \"auditDetails\" : {        \"lastModifiedTime\" : 2,        \"createdBy\" : \"createdBy\",        \"lastModifiedBy\" : \"lastModifiedBy\",        \"createdTime\" : 3      },      \"tenantId\" : \"tenantA\",      \"deliveryComment\" : \"deliveryComment\",      \"id\" : \"id\"    }, {      \"isDelivered\" : true,      \"quantity\" : \"quantity\",      \"productVariantId\" : \"ID-1\",      \"isDeleted\" : { },      \"auditDetails\" : {        \"lastModifiedTime\" : 2,        \"createdBy\" : \"createdBy\",        \"lastModifiedBy\" : \"lastModifiedBy\",        \"createdTime\" : 3      },      \"tenantId\" : \"tenantA\",      \"deliveryComment\" : \"deliveryComment\",      \"id\" : \"id\"    } ],    \"plannedEndDate\" : 6,    \"projectBeneficiaryId\" : \"R-ID-1\",    \"createdDate\" : 1663218161,    \"plannedStartDate\" : 0,    \"isDeleted\" : null,    \"createdBy\" : \"UUID\",    \"auditDetails\" : {      \"lastModifiedTime\" : 2,      \"createdBy\" : \"createdBy\",      \"lastModifiedBy\" : \"lastModifiedBy\",      \"createdTime\" : 3    },    \"tenantId\" : \"tenantA\",    \"actualStartDate\" : 1,    \"id\" : { },    \"projectId\" : \"projectId\",    \"status\" : \"DELIVERED\"  }, {    \"actualEndDate\" : 5,    \"address\" : {      \"locationAccuracy\" : 5962.133916683182,      \"pincode\" : \"pincode\",      \"city\" : \"city\",      \"latitude\" : 18.494211295267263,      \"locality\" : {        \"code\" : \"code\",        \"materializedPath\" : \"materializedPath\",        \"children\" : [ null, null ],        \"latitude\" : \"latitude\",        \"name\" : \"name\",        \"label\" : \"label\",        \"longitude\" : \"longitude\"      },      \"type\" : \"type\",      \"buildingName\" : \"buildingName\",      \"street\" : \"street\",      \"tenantId\" : \"tenantA\",      \"addressLine1\" : \"addressLine1\",      \"addressLine2\" : \"addressLine2\",      \"id\" : \"id\",      \"doorNo\" : \"doorNo\",      \"landmark\" : \"landmark\",      \"longitude\" : -127.23073270189397    },    \"additionalFields\" : {      \"schema\" : \"HOUSEHOLD\",      \"fields\" : [ {        \"value\" : \"180\",        \"key\" : \"height\"      }, {        \"value\" : \"180\",        \"key\" : \"height\"      } ],      \"version\" : 2    },    \"rowVersion\" : { },    \"resources\" : [ {      \"isDelivered\" : true,      \"quantity\" : \"quantity\",      \"productVariantId\" : \"ID-1\",      \"isDeleted\" : { },      \"auditDetails\" : {        \"lastModifiedTime\" : 2,        \"createdBy\" : \"createdBy\",        \"lastModifiedBy\" : \"lastModifiedBy\",        \"createdTime\" : 3      },      \"tenantId\" : \"tenantA\",      \"deliveryComment\" : \"deliveryComment\",      \"id\" : \"id\"    }, {      \"isDelivered\" : true,      \"quantity\" : \"quantity\",      \"productVariantId\" : \"ID-1\",      \"isDeleted\" : { },      \"auditDetails\" : {        \"lastModifiedTime\" : 2,        \"createdBy\" : \"createdBy\",        \"lastModifiedBy\" : \"lastModifiedBy\",        \"createdTime\" : 3      },      \"tenantId\" : \"tenantA\",      \"deliveryComment\" : \"deliveryComment\",      \"id\" : \"id\"    } ],    \"plannedEndDate\" : 6,    \"projectBeneficiaryId\" : \"R-ID-1\",    \"createdDate\" : 1663218161,    \"plannedStartDate\" : 0,    \"isDeleted\" : null,    \"createdBy\" : \"UUID\",    \"auditDetails\" : {      \"lastModifiedTime\" : 2,      \"createdBy\" : \"createdBy\",      \"lastModifiedBy\" : \"lastModifiedBy\",      \"createdTime\" : 3    },    \"tenantId\" : \"tenantA\",    \"actualStartDate\" : 1,    \"id\" : { },    \"projectId\" : \"projectId\",    \"status\" : \"DELIVERED\"  } ]}", TaskResponse.class), HttpStatus.NOT_IMPLEMENTED);
            } catch (IOException e) {
                return new ResponseEntity<TaskResponse>(HttpStatus.INTERNAL_SERVER_ERROR);
            }
        }

        return new ResponseEntity<TaskResponse>(HttpStatus.NOT_IMPLEMENTED);
    }

    @RequestMapping(value = "/task/v1/_update", method = RequestMethod.POST)
    public ResponseEntity<TaskResponse> projectTaskV1UpdatePost(@ApiParam(value = "Capture details of Existing task", required = true) @Valid @RequestBody TaskRequest task) {
        String accept = request.getHeader("Accept");
        if (accept != null && accept.contains("application/json")) {
            try {
                return new ResponseEntity<TaskResponse>(objectMapper.readValue("{  \"ResponseInfo\" : {    \"ver\" : \"ver\",    \"resMsgId\" : \"resMsgId\",    \"msgId\" : \"msgId\",    \"apiId\" : \"apiId\",    \"ts\" : 0,    \"status\" : \"SUCCESSFUL\"  },  \"Task\" : [ {    \"actualEndDate\" : 5,    \"address\" : {      \"locationAccuracy\" : 5962.133916683182,      \"pincode\" : \"pincode\",      \"city\" : \"city\",      \"latitude\" : 18.494211295267263,      \"locality\" : {        \"code\" : \"code\",        \"materializedPath\" : \"materializedPath\",        \"children\" : [ null, null ],        \"latitude\" : \"latitude\",        \"name\" : \"name\",        \"label\" : \"label\",        \"longitude\" : \"longitude\"      },      \"type\" : \"type\",      \"buildingName\" : \"buildingName\",      \"street\" : \"street\",      \"tenantId\" : \"tenantA\",      \"addressLine1\" : \"addressLine1\",      \"addressLine2\" : \"addressLine2\",      \"id\" : \"id\",      \"doorNo\" : \"doorNo\",      \"landmark\" : \"landmark\",      \"longitude\" : -127.23073270189397    },    \"additionalFields\" : {      \"schema\" : \"HOUSEHOLD\",      \"fields\" : [ {        \"value\" : \"180\",        \"key\" : \"height\"      }, {        \"value\" : \"180\",        \"key\" : \"height\"      } ],      \"version\" : 2    },    \"rowVersion\" : { },    \"resources\" : [ {      \"isDelivered\" : true,      \"quantity\" : \"quantity\",      \"productVariantId\" : \"ID-1\",      \"isDeleted\" : { },      \"auditDetails\" : {        \"lastModifiedTime\" : 2,        \"createdBy\" : \"createdBy\",        \"lastModifiedBy\" : \"lastModifiedBy\",        \"createdTime\" : 3      },      \"tenantId\" : \"tenantA\",      \"deliveryComment\" : \"deliveryComment\",      \"id\" : \"id\"    }, {      \"isDelivered\" : true,      \"quantity\" : \"quantity\",      \"productVariantId\" : \"ID-1\",      \"isDeleted\" : { },      \"auditDetails\" : {        \"lastModifiedTime\" : 2,        \"createdBy\" : \"createdBy\",        \"lastModifiedBy\" : \"lastModifiedBy\",        \"createdTime\" : 3      },      \"tenantId\" : \"tenantA\",      \"deliveryComment\" : \"deliveryComment\",      \"id\" : \"id\"    } ],    \"plannedEndDate\" : 6,    \"projectBeneficiaryId\" : \"R-ID-1\",    \"createdDate\" : 1663218161,    \"plannedStartDate\" : 0,    \"isDeleted\" : null,    \"createdBy\" : \"UUID\",    \"auditDetails\" : {      \"lastModifiedTime\" : 2,      \"createdBy\" : \"createdBy\",      \"lastModifiedBy\" : \"lastModifiedBy\",      \"createdTime\" : 3    },    \"tenantId\" : \"tenantA\",    \"actualStartDate\" : 1,    \"id\" : { },    \"projectId\" : \"projectId\",    \"status\" : \"DELIVERED\"  }, {    \"actualEndDate\" : 5,    \"address\" : {      \"locationAccuracy\" : 5962.133916683182,      \"pincode\" : \"pincode\",      \"city\" : \"city\",      \"latitude\" : 18.494211295267263,      \"locality\" : {        \"code\" : \"code\",        \"materializedPath\" : \"materializedPath\",        \"children\" : [ null, null ],        \"latitude\" : \"latitude\",        \"name\" : \"name\",        \"label\" : \"label\",        \"longitude\" : \"longitude\"      },      \"type\" : \"type\",      \"buildingName\" : \"buildingName\",      \"street\" : \"street\",      \"tenantId\" : \"tenantA\",      \"addressLine1\" : \"addressLine1\",      \"addressLine2\" : \"addressLine2\",      \"id\" : \"id\",      \"doorNo\" : \"doorNo\",      \"landmark\" : \"landmark\",      \"longitude\" : -127.23073270189397    },    \"additionalFields\" : {      \"schema\" : \"HOUSEHOLD\",      \"fields\" : [ {        \"value\" : \"180\",        \"key\" : \"height\"      }, {        \"value\" : \"180\",        \"key\" : \"height\"      } ],      \"version\" : 2    },    \"rowVersion\" : { },    \"resources\" : [ {      \"isDelivered\" : true,      \"quantity\" : \"quantity\",      \"productVariantId\" : \"ID-1\",      \"isDeleted\" : { },      \"auditDetails\" : {        \"lastModifiedTime\" : 2,        \"createdBy\" : \"createdBy\",        \"lastModifiedBy\" : \"lastModifiedBy\",        \"createdTime\" : 3      },      \"tenantId\" : \"tenantA\",      \"deliveryComment\" : \"deliveryComment\",      \"id\" : \"id\"    }, {      \"isDelivered\" : true,      \"quantity\" : \"quantity\",      \"productVariantId\" : \"ID-1\",      \"isDeleted\" : { },      \"auditDetails\" : {        \"lastModifiedTime\" : 2,        \"createdBy\" : \"createdBy\",        \"lastModifiedBy\" : \"lastModifiedBy\",        \"createdTime\" : 3      },      \"tenantId\" : \"tenantA\",      \"deliveryComment\" : \"deliveryComment\",      \"id\" : \"id\"    } ],    \"plannedEndDate\" : 6,    \"projectBeneficiaryId\" : \"R-ID-1\",    \"createdDate\" : 1663218161,    \"plannedStartDate\" : 0,    \"isDeleted\" : null,    \"createdBy\" : \"UUID\",    \"auditDetails\" : {      \"lastModifiedTime\" : 2,      \"createdBy\" : \"createdBy\",      \"lastModifiedBy\" : \"lastModifiedBy\",      \"createdTime\" : 3    },    \"tenantId\" : \"tenantA\",    \"actualStartDate\" : 1,    \"id\" : { },    \"projectId\" : \"projectId\",    \"status\" : \"DELIVERED\"  } ]}", TaskResponse.class), HttpStatus.NOT_IMPLEMENTED);
            } catch (IOException e) {
                return new ResponseEntity<TaskResponse>(HttpStatus.INTERNAL_SERVER_ERROR);
            }
        }

        return new ResponseEntity<TaskResponse>(HttpStatus.NOT_IMPLEMENTED);
    }

    @RequestMapping(value = "/v1/_create", method = RequestMethod.POST)
    public ResponseEntity<ProjectResponse> projectV1CreatePost(@ApiParam(value = "Details for the new Project.", required = true) @Valid @RequestBody ProjectRequest project) {
        String accept = request.getHeader("Accept");
        if (accept != null && accept.contains("application/json")) {
            try {
                return new ResponseEntity<ProjectResponse>(objectMapper.readValue("{  \"ResponseInfo\" : {    \"ver\" : \"ver\",    \"resMsgId\" : \"resMsgId\",    \"msgId\" : \"msgId\",    \"apiId\" : \"apiId\",    \"ts\" : 0,    \"status\" : \"SUCCESSFUL\"  },  \"Project\" : [ {    \"parent\" : \"parent\",    \"address\" : {      \"locationAccuracy\" : 5962.133916683182,      \"pincode\" : \"pincode\",      \"city\" : \"city\",      \"latitude\" : 18.494211295267263,      \"locality\" : {        \"code\" : \"code\",        \"materializedPath\" : \"materializedPath\",        \"children\" : [ null, null ],        \"latitude\" : \"latitude\",        \"name\" : \"name\",        \"label\" : \"label\",        \"longitude\" : \"longitude\"      },      \"type\" : \"type\",      \"buildingName\" : \"buildingName\",      \"street\" : \"street\",      \"tenantId\" : \"tenantA\",      \"addressLine1\" : \"addressLine1\",      \"addressLine2\" : \"addressLine2\",      \"id\" : \"id\",      \"doorNo\" : \"doorNo\",      \"landmark\" : \"landmark\",      \"longitude\" : -127.23073270189397    },    \"additionalFields\" : {      \"schema\" : \"HOUSEHOLD\",      \"fields\" : [ {        \"value\" : \"180\",        \"key\" : \"height\"      }, {        \"value\" : \"180\",        \"key\" : \"height\"      } ],      \"version\" : 2    },    \"rowVersion\" : { },    \"endDate\" : 2,    \"documents\" : [ {      \"documentType\" : \"documentType\",      \"documentUid\" : \"documentUid\",      \"auditDetails\" : {        \"lastModifiedTime\" : 7,        \"createdBy\" : \"createdBy\",        \"lastModifiedBy\" : \"lastModifiedBy\",        \"createdTime\" : 4      },      \"tenantId\" : \"tenantId\",      \"id\" : \"id\",      \"fileStoreId\" : \"fileStoreId\"    }, {      \"documentType\" : \"documentType\",      \"documentUid\" : \"documentUid\",      \"auditDetails\" : {        \"lastModifiedTime\" : 7,        \"createdBy\" : \"createdBy\",        \"lastModifiedBy\" : \"lastModifiedBy\",        \"createdTime\" : 4      },      \"tenantId\" : \"tenantId\",      \"id\" : \"id\",      \"fileStoreId\" : \"fileStoreId\"    } ],    \"description\" : \"description\",    \"targets\" : [ {      \"isDeleted\" : { },      \"beneficiaryType\" : \"ID-1\",      \"auditDetails\" : {        \"lastModifiedTime\" : 2,        \"createdBy\" : \"createdBy\",        \"lastModifiedBy\" : \"lastModifiedBy\",        \"createdTime\" : 3      },      \"id\" : \"id\",      \"baseline\" : 7,      \"target\" : 9    }, {      \"isDeleted\" : { },      \"beneficiaryType\" : \"ID-1\",      \"auditDetails\" : {        \"lastModifiedTime\" : 2,        \"createdBy\" : \"createdBy\",        \"lastModifiedBy\" : \"lastModifiedBy\",        \"createdTime\" : 3      },      \"id\" : \"id\",      \"baseline\" : 7,      \"target\" : 9    } ],    \"referenceId\" : \"referenceId\",    \"projectTypeId\" : { },    \"isDeleted\" : null,    \"auditDetails\" : {      \"lastModifiedTime\" : 2,      \"createdBy\" : \"createdBy\",      \"lastModifiedBy\" : \"lastModifiedBy\",      \"createdTime\" : 3    },    \"tenantId\" : \"tenantA\",    \"projectHierarchy\" : \"projectHierarchy\",    \"id\" : { },    \"department\" : \"department\",    \"startDate\" : 5,    \"subProjectTypeId\" : { },    \"isTaskEnabled\" : false  }, {    \"parent\" : \"parent\",    \"address\" : {      \"locationAccuracy\" : 5962.133916683182,      \"pincode\" : \"pincode\",      \"city\" : \"city\",      \"latitude\" : 18.494211295267263,      \"locality\" : {        \"code\" : \"code\",        \"materializedPath\" : \"materializedPath\",        \"children\" : [ null, null ],        \"latitude\" : \"latitude\",        \"name\" : \"name\",        \"label\" : \"label\",        \"longitude\" : \"longitude\"      },      \"type\" : \"type\",      \"buildingName\" : \"buildingName\",      \"street\" : \"street\",      \"tenantId\" : \"tenantA\",      \"addressLine1\" : \"addressLine1\",      \"addressLine2\" : \"addressLine2\",      \"id\" : \"id\",      \"doorNo\" : \"doorNo\",      \"landmark\" : \"landmark\",      \"longitude\" : -127.23073270189397    },    \"additionalFields\" : {      \"schema\" : \"HOUSEHOLD\",      \"fields\" : [ {        \"value\" : \"180\",        \"key\" : \"height\"      }, {        \"value\" : \"180\",        \"key\" : \"height\"      } ],      \"version\" : 2    },    \"rowVersion\" : { },    \"endDate\" : 2,    \"documents\" : [ {      \"documentType\" : \"documentType\",      \"documentUid\" : \"documentUid\",      \"auditDetails\" : {        \"lastModifiedTime\" : 7,        \"createdBy\" : \"createdBy\",        \"lastModifiedBy\" : \"lastModifiedBy\",        \"createdTime\" : 4      },      \"tenantId\" : \"tenantId\",      \"id\" : \"id\",      \"fileStoreId\" : \"fileStoreId\"    }, {      \"documentType\" : \"documentType\",      \"documentUid\" : \"documentUid\",      \"auditDetails\" : {        \"lastModifiedTime\" : 7,        \"createdBy\" : \"createdBy\",        \"lastModifiedBy\" : \"lastModifiedBy\",        \"createdTime\" : 4      },      \"tenantId\" : \"tenantId\",      \"id\" : \"id\",      \"fileStoreId\" : \"fileStoreId\"    } ],    \"description\" : \"description\",    \"targets\" : [ {      \"isDeleted\" : { },      \"beneficiaryType\" : \"ID-1\",      \"auditDetails\" : {        \"lastModifiedTime\" : 2,        \"createdBy\" : \"createdBy\",        \"lastModifiedBy\" : \"lastModifiedBy\",        \"createdTime\" : 3      },      \"id\" : \"id\",      \"baseline\" : 7,      \"target\" : 9    }, {      \"isDeleted\" : { },      \"beneficiaryType\" : \"ID-1\",      \"auditDetails\" : {        \"lastModifiedTime\" : 2,        \"createdBy\" : \"createdBy\",        \"lastModifiedBy\" : \"lastModifiedBy\",        \"createdTime\" : 3      },      \"id\" : \"id\",      \"baseline\" : 7,      \"target\" : 9    } ],    \"referenceId\" : \"referenceId\",    \"projectTypeId\" : { },    \"isDeleted\" : null,    \"auditDetails\" : {      \"lastModifiedTime\" : 2,      \"createdBy\" : \"createdBy\",      \"lastModifiedBy\" : \"lastModifiedBy\",      \"createdTime\" : 3    },    \"tenantId\" : \"tenantA\",    \"projectHierarchy\" : \"projectHierarchy\",    \"id\" : { },    \"department\" : \"department\",    \"startDate\" : 5,    \"subProjectTypeId\" : { },    \"isTaskEnabled\" : false  } ]}", ProjectResponse.class), HttpStatus.NOT_IMPLEMENTED);
            } catch (IOException e) {
                return new ResponseEntity<ProjectResponse>(HttpStatus.INTERNAL_SERVER_ERROR);
            }
        }

        return new ResponseEntity<ProjectResponse>(HttpStatus.NOT_IMPLEMENTED);
    }

    @RequestMapping(value = "/v1/_search", method = RequestMethod.POST)
    public ResponseEntity<ProjectResponse> projectV1SearchPost(@ApiParam(value = "Details for the project.", required = true) @Valid @RequestBody ProjectSearchRequest project, @NotNull
    @Min(0)
    @Max(1000) @ApiParam(value = "Pagination - limit records in response", required = true) @Valid @RequestParam(value = "limit", required = true) Integer limit, @NotNull
                                                               @Min(0) @ApiParam(value = "Pagination - offset from which records should be returned in response", required = true) @Valid @RequestParam(value = "offset", required = true) Integer offset, @NotNull @ApiParam(value = "Unique id for a tenant.", required = true) @Valid @RequestParam(value = "tenantId", required = true) String tenantId, @ApiParam(value = "epoch of the time since when the changes on the object should be picked up. Search results from this parameter should include both newly created objects since this time as well as any modified objects since this time. This criterion is included to help polling clients to get the changes in system since a last time they synchronized with the platform. ") @Valid @RequestParam(value = "lastChangedSince", required = false) Long lastChangedSince, @ApiParam(value = "Used in search APIs to specify if (soft) deleted records should be included in search results.", defaultValue = "false") @Valid @RequestParam(value = "includeDeleted", required = false, defaultValue = "false") Boolean includeDeleted, @ApiParam(value = "Used in project search API to specify if records past end date should be included in search results.", defaultValue = "false") @Valid @RequestParam(value = "includeEnded", required = false, defaultValue = "false") Boolean includeEnded, @ApiParam(value = "Used in project search API to specify if response should include project elements that are in the preceding hierarchy of matched projects.", defaultValue = "false") @Valid @RequestParam(value = "includeAncestors", required = false, defaultValue = "false") Boolean includeAncestors, @ApiParam(value = "Used in project search API to specify if response should include project elements that are in the following hierarchy of matched projects.", defaultValue = "false") @Valid @RequestParam(value = "includeDescendants", required = false, defaultValue = "false") Boolean includeDescendants) {
        String accept = request.getHeader("Accept");
        if (accept != null && accept.contains("application/json")) {
            try {
                return new ResponseEntity<ProjectResponse>(objectMapper.readValue("{  \"ResponseInfo\" : {    \"ver\" : \"ver\",    \"resMsgId\" : \"resMsgId\",    \"msgId\" : \"msgId\",    \"apiId\" : \"apiId\",    \"ts\" : 0,    \"status\" : \"SUCCESSFUL\"  },  \"Project\" : [ {    \"parent\" : \"parent\",    \"address\" : {      \"locationAccuracy\" : 5962.133916683182,      \"pincode\" : \"pincode\",      \"city\" : \"city\",      \"latitude\" : 18.494211295267263,      \"locality\" : {        \"code\" : \"code\",        \"materializedPath\" : \"materializedPath\",        \"children\" : [ null, null ],        \"latitude\" : \"latitude\",        \"name\" : \"name\",        \"label\" : \"label\",        \"longitude\" : \"longitude\"      },      \"type\" : \"type\",      \"buildingName\" : \"buildingName\",      \"street\" : \"street\",      \"tenantId\" : \"tenantA\",      \"addressLine1\" : \"addressLine1\",      \"addressLine2\" : \"addressLine2\",      \"id\" : \"id\",      \"doorNo\" : \"doorNo\",      \"landmark\" : \"landmark\",      \"longitude\" : -127.23073270189397    },    \"additionalFields\" : {      \"schema\" : \"HOUSEHOLD\",      \"fields\" : [ {        \"value\" : \"180\",        \"key\" : \"height\"      }, {        \"value\" : \"180\",        \"key\" : \"height\"      } ],      \"version\" : 2    },    \"rowVersion\" : { },    \"endDate\" : 2,    \"documents\" : [ {      \"documentType\" : \"documentType\",      \"documentUid\" : \"documentUid\",      \"auditDetails\" : {        \"lastModifiedTime\" : 7,        \"createdBy\" : \"createdBy\",        \"lastModifiedBy\" : \"lastModifiedBy\",        \"createdTime\" : 4      },      \"tenantId\" : \"tenantId\",      \"id\" : \"id\",      \"fileStoreId\" : \"fileStoreId\"    }, {      \"documentType\" : \"documentType\",      \"documentUid\" : \"documentUid\",      \"auditDetails\" : {        \"lastModifiedTime\" : 7,        \"createdBy\" : \"createdBy\",        \"lastModifiedBy\" : \"lastModifiedBy\",        \"createdTime\" : 4      },      \"tenantId\" : \"tenantId\",      \"id\" : \"id\",      \"fileStoreId\" : \"fileStoreId\"    } ],    \"description\" : \"description\",    \"targets\" : [ {      \"isDeleted\" : { },      \"beneficiaryType\" : \"ID-1\",      \"auditDetails\" : {        \"lastModifiedTime\" : 2,        \"createdBy\" : \"createdBy\",        \"lastModifiedBy\" : \"lastModifiedBy\",        \"createdTime\" : 3      },      \"id\" : \"id\",      \"baseline\" : 7,      \"target\" : 9    }, {      \"isDeleted\" : { },      \"beneficiaryType\" : \"ID-1\",      \"auditDetails\" : {        \"lastModifiedTime\" : 2,        \"createdBy\" : \"createdBy\",        \"lastModifiedBy\" : \"lastModifiedBy\",        \"createdTime\" : 3      },      \"id\" : \"id\",      \"baseline\" : 7,      \"target\" : 9    } ],    \"referenceId\" : \"referenceId\",    \"projectTypeId\" : { },    \"isDeleted\" : null,    \"auditDetails\" : {      \"lastModifiedTime\" : 2,      \"createdBy\" : \"createdBy\",      \"lastModifiedBy\" : \"lastModifiedBy\",      \"createdTime\" : 3    },    \"tenantId\" : \"tenantA\",    \"projectHierarchy\" : \"projectHierarchy\",    \"id\" : { },    \"department\" : \"department\",    \"startDate\" : 5,    \"subProjectTypeId\" : { },    \"isTaskEnabled\" : false  }, {    \"parent\" : \"parent\",    \"address\" : {      \"locationAccuracy\" : 5962.133916683182,      \"pincode\" : \"pincode\",      \"city\" : \"city\",      \"latitude\" : 18.494211295267263,      \"locality\" : {        \"code\" : \"code\",        \"materializedPath\" : \"materializedPath\",        \"children\" : [ null, null ],        \"latitude\" : \"latitude\",        \"name\" : \"name\",        \"label\" : \"label\",        \"longitude\" : \"longitude\"      },      \"type\" : \"type\",      \"buildingName\" : \"buildingName\",      \"street\" : \"street\",      \"tenantId\" : \"tenantA\",      \"addressLine1\" : \"addressLine1\",      \"addressLine2\" : \"addressLine2\",      \"id\" : \"id\",      \"doorNo\" : \"doorNo\",      \"landmark\" : \"landmark\",      \"longitude\" : -127.23073270189397    },    \"additionalFields\" : {      \"schema\" : \"HOUSEHOLD\",      \"fields\" : [ {        \"value\" : \"180\",        \"key\" : \"height\"      }, {        \"value\" : \"180\",        \"key\" : \"height\"      } ],      \"version\" : 2    },    \"rowVersion\" : { },    \"endDate\" : 2,    \"documents\" : [ {      \"documentType\" : \"documentType\",      \"documentUid\" : \"documentUid\",      \"auditDetails\" : {        \"lastModifiedTime\" : 7,        \"createdBy\" : \"createdBy\",        \"lastModifiedBy\" : \"lastModifiedBy\",        \"createdTime\" : 4      },      \"tenantId\" : \"tenantId\",      \"id\" : \"id\",      \"fileStoreId\" : \"fileStoreId\"    }, {      \"documentType\" : \"documentType\",      \"documentUid\" : \"documentUid\",      \"auditDetails\" : {        \"lastModifiedTime\" : 7,        \"createdBy\" : \"createdBy\",        \"lastModifiedBy\" : \"lastModifiedBy\",        \"createdTime\" : 4      },      \"tenantId\" : \"tenantId\",      \"id\" : \"id\",      \"fileStoreId\" : \"fileStoreId\"    } ],    \"description\" : \"description\",    \"targets\" : [ {      \"isDeleted\" : { },      \"beneficiaryType\" : \"ID-1\",      \"auditDetails\" : {        \"lastModifiedTime\" : 2,        \"createdBy\" : \"createdBy\",        \"lastModifiedBy\" : \"lastModifiedBy\",        \"createdTime\" : 3      },      \"id\" : \"id\",      \"baseline\" : 7,      \"target\" : 9    }, {      \"isDeleted\" : { },      \"beneficiaryType\" : \"ID-1\",      \"auditDetails\" : {        \"lastModifiedTime\" : 2,        \"createdBy\" : \"createdBy\",        \"lastModifiedBy\" : \"lastModifiedBy\",        \"createdTime\" : 3      },      \"id\" : \"id\",      \"baseline\" : 7,      \"target\" : 9    } ],    \"referenceId\" : \"referenceId\",    \"projectTypeId\" : { },    \"isDeleted\" : null,    \"auditDetails\" : {      \"lastModifiedTime\" : 2,      \"createdBy\" : \"createdBy\",      \"lastModifiedBy\" : \"lastModifiedBy\",      \"createdTime\" : 3    },    \"tenantId\" : \"tenantA\",    \"projectHierarchy\" : \"projectHierarchy\",    \"id\" : { },    \"department\" : \"department\",    \"startDate\" : 5,    \"subProjectTypeId\" : { },    \"isTaskEnabled\" : false  } ]}", ProjectResponse.class), HttpStatus.NOT_IMPLEMENTED);
            } catch (IOException e) {
                return new ResponseEntity<ProjectResponse>(HttpStatus.INTERNAL_SERVER_ERROR);
            }
        }

        return new ResponseEntity<ProjectResponse>(HttpStatus.NOT_IMPLEMENTED);
    }

    @RequestMapping(value = "/v1/_update", method = RequestMethod.POST)
    public ResponseEntity<ProjectResponse> projectV1UpdatePost(@ApiParam(value = "Details for the new Project.", required = true) @Valid @RequestBody ProjectRequest project) {
        String accept = request.getHeader("Accept");
        if (accept != null && accept.contains("application/json")) {
            try {
                return new ResponseEntity<ProjectResponse>(objectMapper.readValue("{  \"ResponseInfo\" : {    \"ver\" : \"ver\",    \"resMsgId\" : \"resMsgId\",    \"msgId\" : \"msgId\",    \"apiId\" : \"apiId\",    \"ts\" : 0,    \"status\" : \"SUCCESSFUL\"  },  \"Project\" : [ {    \"parent\" : \"parent\",    \"address\" : {      \"locationAccuracy\" : 5962.133916683182,      \"pincode\" : \"pincode\",      \"city\" : \"city\",      \"latitude\" : 18.494211295267263,      \"locality\" : {        \"code\" : \"code\",        \"materializedPath\" : \"materializedPath\",        \"children\" : [ null, null ],        \"latitude\" : \"latitude\",        \"name\" : \"name\",        \"label\" : \"label\",        \"longitude\" : \"longitude\"      },      \"type\" : \"type\",      \"buildingName\" : \"buildingName\",      \"street\" : \"street\",      \"tenantId\" : \"tenantA\",      \"addressLine1\" : \"addressLine1\",      \"addressLine2\" : \"addressLine2\",      \"id\" : \"id\",      \"doorNo\" : \"doorNo\",      \"landmark\" : \"landmark\",      \"longitude\" : -127.23073270189397    },    \"additionalFields\" : {      \"schema\" : \"HOUSEHOLD\",      \"fields\" : [ {        \"value\" : \"180\",        \"key\" : \"height\"      }, {        \"value\" : \"180\",        \"key\" : \"height\"      } ],      \"version\" : 2    },    \"rowVersion\" : { },    \"endDate\" : 2,    \"documents\" : [ {      \"documentType\" : \"documentType\",      \"documentUid\" : \"documentUid\",      \"auditDetails\" : {        \"lastModifiedTime\" : 7,        \"createdBy\" : \"createdBy\",        \"lastModifiedBy\" : \"lastModifiedBy\",        \"createdTime\" : 4      },      \"tenantId\" : \"tenantId\",      \"id\" : \"id\",      \"fileStoreId\" : \"fileStoreId\"    }, {      \"documentType\" : \"documentType\",      \"documentUid\" : \"documentUid\",      \"auditDetails\" : {        \"lastModifiedTime\" : 7,        \"createdBy\" : \"createdBy\",        \"lastModifiedBy\" : \"lastModifiedBy\",        \"createdTime\" : 4      },      \"tenantId\" : \"tenantId\",      \"id\" : \"id\",      \"fileStoreId\" : \"fileStoreId\"    } ],    \"description\" : \"description\",    \"targets\" : [ {      \"isDeleted\" : { },      \"beneficiaryType\" : \"ID-1\",      \"auditDetails\" : {        \"lastModifiedTime\" : 2,        \"createdBy\" : \"createdBy\",        \"lastModifiedBy\" : \"lastModifiedBy\",        \"createdTime\" : 3      },      \"id\" : \"id\",      \"baseline\" : 7,      \"target\" : 9    }, {      \"isDeleted\" : { },      \"beneficiaryType\" : \"ID-1\",      \"auditDetails\" : {        \"lastModifiedTime\" : 2,        \"createdBy\" : \"createdBy\",        \"lastModifiedBy\" : \"lastModifiedBy\",        \"createdTime\" : 3      },      \"id\" : \"id\",      \"baseline\" : 7,      \"target\" : 9    } ],    \"referenceId\" : \"referenceId\",    \"projectTypeId\" : { },    \"isDeleted\" : null,    \"auditDetails\" : {      \"lastModifiedTime\" : 2,      \"createdBy\" : \"createdBy\",      \"lastModifiedBy\" : \"lastModifiedBy\",      \"createdTime\" : 3    },    \"tenantId\" : \"tenantA\",    \"projectHierarchy\" : \"projectHierarchy\",    \"id\" : { },    \"department\" : \"department\",    \"startDate\" : 5,    \"subProjectTypeId\" : { },    \"isTaskEnabled\" : false  }, {    \"parent\" : \"parent\",    \"address\" : {      \"locationAccuracy\" : 5962.133916683182,      \"pincode\" : \"pincode\",      \"city\" : \"city\",      \"latitude\" : 18.494211295267263,      \"locality\" : {        \"code\" : \"code\",        \"materializedPath\" : \"materializedPath\",        \"children\" : [ null, null ],        \"latitude\" : \"latitude\",        \"name\" : \"name\",        \"label\" : \"label\",        \"longitude\" : \"longitude\"      },      \"type\" : \"type\",      \"buildingName\" : \"buildingName\",      \"street\" : \"street\",      \"tenantId\" : \"tenantA\",      \"addressLine1\" : \"addressLine1\",      \"addressLine2\" : \"addressLine2\",      \"id\" : \"id\",      \"doorNo\" : \"doorNo\",      \"landmark\" : \"landmark\",      \"longitude\" : -127.23073270189397    },    \"additionalFields\" : {      \"schema\" : \"HOUSEHOLD\",      \"fields\" : [ {        \"value\" : \"180\",        \"key\" : \"height\"      }, {        \"value\" : \"180\",        \"key\" : \"height\"      } ],      \"version\" : 2    },    \"rowVersion\" : { },    \"endDate\" : 2,    \"documents\" : [ {      \"documentType\" : \"documentType\",      \"documentUid\" : \"documentUid\",      \"auditDetails\" : {        \"lastModifiedTime\" : 7,        \"createdBy\" : \"createdBy\",        \"lastModifiedBy\" : \"lastModifiedBy\",        \"createdTime\" : 4      },      \"tenantId\" : \"tenantId\",      \"id\" : \"id\",      \"fileStoreId\" : \"fileStoreId\"    }, {      \"documentType\" : \"documentType\",      \"documentUid\" : \"documentUid\",      \"auditDetails\" : {        \"lastModifiedTime\" : 7,        \"createdBy\" : \"createdBy\",        \"lastModifiedBy\" : \"lastModifiedBy\",        \"createdTime\" : 4      },      \"tenantId\" : \"tenantId\",      \"id\" : \"id\",      \"fileStoreId\" : \"fileStoreId\"    } ],    \"description\" : \"description\",    \"targets\" : [ {      \"isDeleted\" : { },      \"beneficiaryType\" : \"ID-1\",      \"auditDetails\" : {        \"lastModifiedTime\" : 2,        \"createdBy\" : \"createdBy\",        \"lastModifiedBy\" : \"lastModifiedBy\",        \"createdTime\" : 3      },      \"id\" : \"id\",      \"baseline\" : 7,      \"target\" : 9    }, {      \"isDeleted\" : { },      \"beneficiaryType\" : \"ID-1\",      \"auditDetails\" : {        \"lastModifiedTime\" : 2,        \"createdBy\" : \"createdBy\",        \"lastModifiedBy\" : \"lastModifiedBy\",        \"createdTime\" : 3      },      \"id\" : \"id\",      \"baseline\" : 7,      \"target\" : 9    } ],    \"referenceId\" : \"referenceId\",    \"projectTypeId\" : { },    \"isDeleted\" : null,    \"auditDetails\" : {      \"lastModifiedTime\" : 2,      \"createdBy\" : \"createdBy\",      \"lastModifiedBy\" : \"lastModifiedBy\",      \"createdTime\" : 3    },    \"tenantId\" : \"tenantA\",    \"projectHierarchy\" : \"projectHierarchy\",    \"id\" : { },    \"department\" : \"department\",    \"startDate\" : 5,    \"subProjectTypeId\" : { },    \"isTaskEnabled\" : false  } ]}", ProjectResponse.class), HttpStatus.NOT_IMPLEMENTED);
            } catch (IOException e) {
                return new ResponseEntity<ProjectResponse>(HttpStatus.INTERNAL_SERVER_ERROR);
            }
        }

        return new ResponseEntity<ProjectResponse>(HttpStatus.NOT_IMPLEMENTED);
    }

}
