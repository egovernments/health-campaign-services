package org.egov.referralmanagement.web.controllers;

import javax.validation.Valid;

import org.egov.common.models.referralmanagement.beneficiarydownsync.Downsync;
import org.egov.common.models.referralmanagement.beneficiarydownsync.DownsyncRequest;
import org.egov.common.models.referralmanagement.beneficiarydownsync.DownsyncResponse;
import org.egov.common.utils.ResponseInfoFactory;
import org.egov.referralmanagement.service.DownsyncService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import io.swagger.annotations.ApiParam;

@Controller
@RequestMapping("/beneficiary-downsync")
@Validated
public class BeneficiaryDownsyncController {
	
	private DownsyncService downsyncService;

	@Autowired
	BeneficiaryDownsyncController (DownsyncService downsyncService){
		this.downsyncService = downsyncService;
	}
	
    @PostMapping(value = "/v1/_get")
    public ResponseEntity<DownsyncResponse> getBeneficaryData (@ApiParam(value = "Capture details of Side Effect", required = true) @Valid @RequestBody DownsyncRequest request) {

    	Downsync.builder().
    	downsyncCriteria(request.getDownsyncCriteria())
    	.build();
    	Downsync downsync = downsyncService.prepareDownsyncData(request);
        DownsyncResponse response = DownsyncResponse.builder()
                .downsync(downsync)
                .responseInfo(ResponseInfoFactory
                        .createResponseInfo(request.getRequestInfo(), true))
                .build();
        
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }
}
