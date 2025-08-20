package org.egov.excelingestion.web.controller;

import lombok.extern.slf4j.Slf4j;
import org.egov.excelingestion.config.ErrorConstants;
import org.egov.excelingestion.service.ExcelGenerationService;
import org.egov.excelingestion.service.ExcelProcessingService;
import org.egov.excelingestion.web.models.GenerateResource;
import org.egov.excelingestion.web.models.GenerateResourceRequest;
import org.egov.excelingestion.web.models.GenerateResourceResponse;
import org.egov.excelingestion.web.models.ProcessResource;
import org.egov.excelingestion.web.models.ProcessResourceRequest;
import org.egov.excelingestion.web.models.ProcessResourceResponse;
import org.egov.excelingestion.web.models.RequestInfo;
import org.egov.common.contract.response.ResponseInfo;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.validation.annotation.Validated;

import java.io.IOException;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/v1/data")
@Validated
@Slf4j
public class IngestionController {

    private final ExcelGenerationService excelGenerationService;
    private final ExcelProcessingService processingService;

    public IngestionController(ExcelGenerationService excelGenerationService,
                              ExcelProcessingService processingService) {
        this.excelGenerationService = excelGenerationService;
        this.processingService = processingService;
    }

        @PostMapping("/_generate")
    public ResponseEntity<GenerateResourceResponse> generate( @RequestBody @Valid GenerateResourceRequest request) throws IOException {
        GenerateResource processedResource = excelGenerationService.generateAndUploadExcel(request);

        ResponseInfo responseInfo = ResponseInfo.builder()
                .apiId("egov-bff")
                .ver("0.0.1")
                .ts(System.currentTimeMillis())
                .status("successful")
                .build();

        GenerateResourceResponse response = GenerateResourceResponse.builder()
                .responseInfo(responseInfo)
                .generateResource(processedResource)
                .build();

        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @PostMapping("/_process")
    public ResponseEntity<ProcessResourceResponse> processExcel(
            @Valid @RequestBody ProcessResourceRequest request) {
        
        log.info("Received process request for type: {} and tenantId: {}", 
                request.getResourceDetails().getType(), 
                request.getResourceDetails().getTenantId());
        
        RequestInfo requestInfo = request.getRequestInfo();
        ProcessResource processedResource = processingService.processExcelFile(request);
        
        ProcessResourceResponse response = ProcessResourceResponse.builder()
                .responseInfo(org.egov.common.contract.response.ResponseInfo.builder()
                        .apiId(requestInfo.getApiId())
                        .ver(requestInfo.getVer())
                        .ts(requestInfo.getTs())
                        .status("successful")
                        .build())
                .processResource(processedResource)
                .build();
        
        return new ResponseEntity<>(response, HttpStatus.OK);
    }
}
