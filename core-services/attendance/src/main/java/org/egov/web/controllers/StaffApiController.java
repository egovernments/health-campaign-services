package org.egov.web.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.annotations.ApiParam;
import org.egov.common.contract.response.ResponseInfo;
import org.egov.service.StaffService;
import org.egov.util.ResponseInfoFactory;
import org.egov.web.models.StaffPermissionRequest;
import org.egov.web.models.StaffPermissionResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;

@Controller
@RequestMapping("/staff/v1")
public class StaffApiController {


    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private HttpServletRequest request;

    @Autowired
    private StaffService staffService;

    @Autowired
    private ResponseInfoFactory responseInfoFactory;

    @RequestMapping(value = "/_create", method = RequestMethod.POST)
    public ResponseEntity<StaffPermissionResponse> createStaff(@ApiParam(value = "", allowableValues = "application/json") @RequestHeader(value = "Content-Type", required = false) String contentType, @ApiParam(value = "") @Valid @RequestBody StaffPermissionRequest staffPermissionRequest) {
        StaffPermissionRequest enrichedRequest = staffService.createAttendanceStaff(staffPermissionRequest, false);
        ResponseInfo responseInfo = responseInfoFactory.createResponseInfoFromRequestInfo(staffPermissionRequest.getRequestInfo(), true);
        StaffPermissionResponse staffPermissionResponse = StaffPermissionResponse.builder().responseInfo(responseInfo)
                .staff(enrichedRequest.getStaff()).build();
        return new ResponseEntity<StaffPermissionResponse>(staffPermissionResponse, HttpStatus.OK);
    }

    @RequestMapping(value = "/_delete", method = RequestMethod.POST)
    public ResponseEntity<StaffPermissionResponse> deleteStaff(@ApiParam(value = "", allowableValues = "application/json") @RequestHeader(value = "Content-Type", required = false) String contentType, @ApiParam(value = "") @Valid @RequestBody StaffPermissionRequest staffPermissionRequest) {
        StaffPermissionRequest enrichedRequest = staffService.deleteAttendanceStaff(staffPermissionRequest);
        ResponseInfo responseInfo = responseInfoFactory.createResponseInfoFromRequestInfo(staffPermissionRequest.getRequestInfo(), true);
        StaffPermissionResponse staffPermissionResponse = StaffPermissionResponse.builder().responseInfo(responseInfo)
                .staff(enrichedRequest.getStaff()).build();
        return new ResponseEntity<StaffPermissionResponse>(staffPermissionResponse, HttpStatus.OK);
    }

}
