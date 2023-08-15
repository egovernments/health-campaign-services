package org.egov.project.web.controllers;

import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.validation.Valid;

import org.egov.common.utils.ResponseInfoFactory;
import org.egov.project.web.models.BandwidthCheckRequest;
import org.egov.project.web.models.BandwidthCheckResponse;
import org.egov.project.web.models.DumpRequest;
import org.egov.tracer.ExceptionAdvise;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import io.swagger.annotations.ApiParam;
import lombok.extern.slf4j.Slf4j;

@Controller
@RequestMapping("")
@Validated
@Slf4j
public class DataTransferController {

	@Autowired
	private ExceptionAdvise exceptionAdvise;

	@RequestMapping(value = "/check/bandwidth", method = RequestMethod.POST)
	public ResponseEntity<BandwidthCheckResponse> checkBandwidth(
			@ApiParam(value = "Captures dummy json data", required = true) @Valid @RequestBody BandwidthCheckRequest request) {
		log.info("Request received: {}", request);
		return ResponseEntity.status(HttpStatus.OK)
				.body(BandwidthCheckResponse.builder()
						.responseInfo(ResponseInfoFactory.createResponseInfo(request.getRequestInfo(), true))
						.additionalFields(request.getAdditionalFields()).build());
	}

	@RequestMapping(value = "/data/errordump", method = RequestMethod.POST)
	public ResponseEntity<String> indexRecoveryData(
			@ApiParam(value = "Captures failed data from offline client to index", required = true) @Valid @RequestBody DumpRequest request) {
		log.info("Request received: {}", request);

		if (null == request.getErrorDetail().getApiDetails().getId()) {
			request.getErrorDetail().getApiDetails().setId(UUID.randomUUID().toString());
		}
		exceptionAdvise.exceptionHandler(Stream.of(request.getErrorDetail()).collect(Collectors.toList()));
		return ResponseEntity.status(HttpStatus.OK)
				.body("{\"Success\":\"True\"}");
	}
}
