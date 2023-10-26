package org.egov.referralmanagement.web.controllers;

import javax.validation.Valid;

import org.egov.common.models.referralmanagement.beneficiarydownsync.Downsync;
import org.egov.common.models.referralmanagement.beneficiarydownsync.DownsyncRequest;
import org.egov.common.models.referralmanagement.beneficiarydownsync.DownsyncResponse;
import org.egov.common.utils.ResponseInfoFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import io.swagger.annotations.ApiParam;

@Controller
@RequestMapping("/beneficiary-downsync")
@Validated
public class BeneficiaryDownsyncController {


    @RequestMapping(value = "/v1/_create", method = RequestMethod.POST)
    public ResponseEntity<DownsyncResponse> getBeneficaryData (@ApiParam(value = "Capture details of Side Effect", required = true) @Valid @RequestBody DownsyncRequest request) {

    	Downsync.builder().
    	downsyncCriteria(request.getDownsyncCriteria())
    	.build();
        DownsyncResponse response = DownsyncResponse.builder()
                .downsync(new Downsync())
                .responseInfo(ResponseInfoFactory
                        .createResponseInfo(request.getRequestInfo(), true))
                .build();

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }
}
