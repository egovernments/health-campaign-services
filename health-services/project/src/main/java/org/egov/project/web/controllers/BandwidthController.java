package org.egov.project.web.controllers;

import io.swagger.annotations.ApiParam;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.utils.ResponseInfoFactory;
import org.egov.project.web.models.BandwidthCheckRequest;
import org.egov.project.web.models.BandwidthCheckResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import javax.validation.Valid;

@Controller
@RequestMapping("")
@Validated
@Slf4j
public class BandwidthController {


    @RequestMapping(value = "/check/bandwidth", method = RequestMethod.POST)
    public ResponseEntity<BandwidthCheckResponse> checkBandwidth(@ApiParam(value = "Captures dummy json data", required = true) @Valid @RequestBody BandwidthCheckRequest request) {
        log.info("Request received: {}", request);
        return ResponseEntity.status(HttpStatus.OK).body(BandwidthCheckResponse.builder()
                .responseInfo(ResponseInfoFactory
                .createResponseInfo(request.getRequestInfo(), true))
                .additionalFields(request.getAdditionalFields())
                .build());
    }
}
