package org.egov.web.controllers;


import com.fasterxml.jackson.databind.ObjectMapper;
import org.egov.common.contract.models.RequestInfoWrapper;
import io.swagger.annotations.ApiParam;
import org.egov.common.contract.response.ResponseInfo;
import org.egov.service.MusterRollService;
import org.egov.util.ResponseInfoCreator;
import org.egov.web.models.MusterRollRequest;
import org.egov.web.models.MusterRollResponse;
import org.egov.web.models.MusterRollSearchCriteria;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Collections;

@jakarta.annotation.Generated(value = "org.egov.codegen.SpringBootCodegen", date = "2022-11-14T19:58:09.415+05:30")

@Controller
@RequestMapping("/v1")
public class MusterRollApiController {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private HttpServletRequest request;

    @Autowired
    private MusterRollService musterRollService;

    @Autowired
    private ResponseInfoCreator responseInfoCreator;

    @RequestMapping(value = "/_estimate", method = RequestMethod.POST)
    public ResponseEntity<MusterRollResponse> musterRollV1EstimatePost(@ApiParam(value = "Request object to provide the estimate of the muster roll", required = true) @Valid @RequestBody MusterRollRequest body) {
        MusterRollRequest musterRollRequest = musterRollService.estimateMusterRoll(body);
        ResponseInfo responseInfo = responseInfoCreator.createResponseInfoFromRequestInfo(body.getRequestInfo(), true);
        MusterRollResponse musterRollResponse = MusterRollResponse.builder().responseInfo(responseInfo).musterRolls(Collections.singletonList(musterRollRequest.getMusterRoll())).build();
        return new ResponseEntity<MusterRollResponse>(musterRollResponse, HttpStatus.OK);
    }

    @RequestMapping(value = "/_create", method = RequestMethod.POST)
    public ResponseEntity<MusterRollResponse> musterRollV1CreatePost(@ApiParam(value = "Request object to create the muster roll", required = true) @Valid @RequestBody MusterRollRequest body) {
        MusterRollRequest musterRollRequest = musterRollService.createMusterRoll(body);
        ResponseInfo responseInfo = responseInfoCreator.createResponseInfoFromRequestInfo(body.getRequestInfo(), true);
        MusterRollResponse musterRollResponse = MusterRollResponse.builder().responseInfo(responseInfo).musterRolls(Collections.singletonList(musterRollRequest.getMusterRoll())).build();
        return new ResponseEntity<MusterRollResponse>(musterRollResponse, HttpStatus.OK);
    }

    @RequestMapping(value = "/_search", method = RequestMethod.POST)
    public ResponseEntity<MusterRollResponse> attendanceV1SearchPOST(@Valid @RequestBody RequestInfoWrapper requestInfoWrapper, @Valid @ModelAttribute MusterRollSearchCriteria searchCriteria) {
        MusterRollResponse musterRollResponse = musterRollService.searchMusterRolls(requestInfoWrapper, searchCriteria);
        return new ResponseEntity<MusterRollResponse>(musterRollResponse, HttpStatus.OK);
    }

    @RequestMapping(value = "/_update", method = RequestMethod.POST)
    public ResponseEntity<MusterRollResponse> musterRollV1UpdatePost(@ApiParam(value = "Request object to update the muster roll", required = true) @Valid @RequestBody MusterRollRequest body) {
        MusterRollRequest musterRollRequest = musterRollService.updateMusterRoll(body);
        ResponseInfo responseInfo = responseInfoCreator.createResponseInfoFromRequestInfo(body.getRequestInfo(), true);
        MusterRollResponse musterRollResponse = MusterRollResponse.builder().responseInfo(responseInfo).musterRolls(Collections.singletonList(musterRollRequest.getMusterRoll())).build();
        return new ResponseEntity<MusterRollResponse>(musterRollResponse, HttpStatus.OK);
    }

}
