package org.egov.web.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.annotations.ApiParam;
import org.egov.service.AttendanceLogService;
import org.egov.web.models.AttendanceLogRequest;
import org.egov.web.models.AttendanceLogResponse;
import org.egov.web.models.AttendanceLogSearchCriteria;
import org.egov.web.models.RequestInfoWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;

@Controller
@RequestMapping("/log/v1")
public class AttendanceLogApiController {


    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private HttpServletRequest request;

    @Autowired
    private AttendanceLogService attendanceLogService;

    @RequestMapping(value = "/_create", method = RequestMethod.POST)
    public ResponseEntity<AttendanceLogResponse> attendanceLogV1CreatePOST(@ApiParam(value = "", allowableValues = "application/json") @RequestHeader(value = "Content-Type", required = false) String contentType, @ApiParam(value = "") @Valid @RequestBody AttendanceLogRequest attendanceLogRequest) {
        AttendanceLogResponse attendanceLogResponse = attendanceLogService.createAttendanceLog(attendanceLogRequest);
        return new ResponseEntity<AttendanceLogResponse>(attendanceLogResponse, HttpStatus.OK);
    }

    @RequestMapping(value = "/_search", method = RequestMethod.POST)
    public ResponseEntity<AttendanceLogResponse> attendanceLogV1SearchPOST(@Valid @ModelAttribute AttendanceLogSearchCriteria searchCriteria, @ApiParam(value = "") @Valid @RequestBody RequestInfoWrapper requestInfoWrapper) {
        AttendanceLogResponse attendanceLogResponse = attendanceLogService.searchAttendanceLog(requestInfoWrapper, searchCriteria);
        return new ResponseEntity<AttendanceLogResponse>(attendanceLogResponse, HttpStatus.OK);
    }

    @RequestMapping(value = "/_update", method = RequestMethod.POST)
    public ResponseEntity<AttendanceLogResponse> attendanceLogV1UpdatePOST(@ApiParam(value = "", allowableValues = "application/json") @RequestHeader(value = "Content-Type", required = false) String contentType, @ApiParam(value = "") @Valid @RequestBody AttendanceLogRequest attendanceLogRequest) {
        AttendanceLogResponse attendanceLogResponse = attendanceLogService.updateAttendanceLog(attendanceLogRequest);
        return new ResponseEntity<AttendanceLogResponse>(attendanceLogResponse, HttpStatus.OK);
    }

}
