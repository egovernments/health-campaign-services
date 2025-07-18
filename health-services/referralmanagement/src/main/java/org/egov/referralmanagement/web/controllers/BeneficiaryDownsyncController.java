package org.egov.referralmanagement.web.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.annotations.ApiParam;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.egov.common.exception.InvalidTenantIdException;
import org.egov.common.models.referralmanagement.beneficiarydownsync.Downsync;
import org.egov.common.models.referralmanagement.beneficiarydownsync.DownsyncRequest;
import org.egov.common.models.referralmanagement.beneficiarydownsync.DownsyncResponse;
import org.egov.common.utils.ResponseInfoFactory;
import org.egov.referralmanagement.service.DownsyncService;
import org.egov.tracer.model.CustomException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@Slf4j
@Controller
@RequestMapping("/beneficiary-downsync")
@Validated
public class BeneficiaryDownsyncController {
	
	private DownsyncService downsyncService;

	private ObjectMapper mapper;

	@Autowired
	BeneficiaryDownsyncController (DownsyncService downsyncService, @Qualifier("objectMapper") ObjectMapper objectMapper){
		this.downsyncService = downsyncService;
		this.mapper = objectMapper;
	}
	
    @PostMapping(value = "/v1/_get")
    public ResponseEntity<DownsyncResponse> getBeneficiaryData(@ApiParam(value = "Capture details of Side Effect", required = true) @Valid @RequestBody DownsyncRequest request) {
		log.info("UserUUID: {}", request.getRequestInfo().getUserInfo().getUuid());
		log.info("Downsync RequestBody: {}", mapper.valueToTree(request).toString());
        Downsync downsync = null;
        try {
            downsync = downsyncService.prepareDownsyncData(request);
        } catch (InvalidTenantIdException e) {
            log.error("Invalid tenantId: {}", ExceptionUtils.getStackTrace(e));
            throw new CustomException("INVALID_TENANT_ID", e.getMessage());
        }
        DownsyncResponse response = DownsyncResponse.builder()
                .downsync(downsync)
                .responseInfo(ResponseInfoFactory
                        .createResponseInfo(request.getRequestInfo(), true))
                .build();
        
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }
}
