package org.egov.web.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.annotations.ApiParam;
import org.egov.common.contract.response.ResponseInfo;
import org.egov.service.AttendeeService;
import org.egov.util.ResponseInfoFactory;
import org.egov.web.models.AttendeeCreateRequest;
import org.egov.web.models.AttendeeCreateResponse;
import org.egov.web.models.AttendeeDeleteRequest;
import org.egov.web.models.AttendeeDeleteResponse;
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
@RequestMapping("/attendee/v1")
public class AttendeeApiController {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private HttpServletRequest request;

    @Autowired
    private AttendeeService attendeeService;

    @Autowired
    private ResponseInfoFactory responseInfoFactory;

    @RequestMapping(value = "/_create", method = RequestMethod.POST)
    public ResponseEntity<AttendeeCreateResponse> createAttendee(@ApiParam(value = "", allowableValues = "application/json") @RequestHeader(value = "Content-Type", required = false) String contentType, @ApiParam(value = "") @Valid @RequestBody AttendeeCreateRequest attendeeCreateRequest) {
        AttendeeCreateRequest enrichedRequest = attendeeService.createAttendee(attendeeCreateRequest);
        ResponseInfo responseInfo = responseInfoFactory.createResponseInfoFromRequestInfo(attendeeCreateRequest.getRequestInfo(), true);
        AttendeeCreateResponse attendeeCreateResponse = AttendeeCreateResponse.builder().responseInfo(responseInfo).attendees(enrichedRequest.getAttendees()).build();
        return new ResponseEntity<AttendeeCreateResponse>(attendeeCreateResponse, HttpStatus.OK);
    }


    @RequestMapping(value = "/_delete", method = RequestMethod.POST)
    public ResponseEntity<AttendeeDeleteResponse> deleteAttendee(@ApiParam(value = "", allowableValues = "application/json") @RequestHeader(value = "Content-Type", required = false) String contentType, @ApiParam(value = "") @Valid @RequestBody AttendeeDeleteRequest attendeeDeleteRequest) {
        AttendeeDeleteRequest enrichedRequest = attendeeService.deleteAttendee(attendeeDeleteRequest);
        ResponseInfo responseInfo = responseInfoFactory.createResponseInfoFromRequestInfo(attendeeDeleteRequest.getRequestInfo(), true);
        AttendeeDeleteResponse attendeeDeleteResponse = AttendeeDeleteResponse.builder().responseInfo(responseInfo).attendees(enrichedRequest.getAttendees()).build();
        return new ResponseEntity<AttendeeDeleteResponse>(attendeeDeleteResponse, HttpStatus.OK);
    }

}
