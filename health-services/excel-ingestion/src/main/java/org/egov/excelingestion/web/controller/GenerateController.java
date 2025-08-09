package org.egov.excelingestion.web.controller;

import org.egov.excelingestion.service.ExcelGenerationService;
import org.egov.excelingestion.web.models.GenerateResource;
import org.egov.excelingestion.web.models.GenerateResourceRequest;
import org.egov.excelingestion.web.models.GenerateResourceResponse;
import org.egov.common.contract.response.ResponseInfo;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

import javax.validation.Valid;

@RestController
@RequestMapping("/v1/data")
public class GenerateController {

    private final ExcelGenerationService excelGenerationService;

    public GenerateController(ExcelGenerationService excelGenerationService) {
        this.excelGenerationService = excelGenerationService;
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
}
