package org.egov.excelingestion.web.controller;

import lombok.extern.slf4j.Slf4j;
import org.egov.excelingestion.constants.GenerationConstants;
import org.egov.excelingestion.service.ExcelWorkflowService;
import org.egov.excelingestion.service.ExcelProcessingService;
import org.egov.excelingestion.service.GenerationService;
import org.egov.excelingestion.web.models.GenerateResource;
import org.egov.excelingestion.web.models.GenerateResourceRequest;
import org.egov.excelingestion.web.models.GenerateResourceResponse;
import org.egov.excelingestion.web.models.ProcessResource;
import org.egov.excelingestion.web.models.ProcessResourceRequest;
import org.egov.excelingestion.web.models.ProcessResourceResponse;
import org.egov.excelingestion.web.models.RequestInfo;
import org.egov.excelingestion.web.models.GenerationSearchRequest;
import org.egov.excelingestion.web.models.GenerationSearchResponse;
import org.egov.common.contract.response.ResponseInfo;
import org.egov.common.exception.InvalidTenantIdException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/v1/data")
@Validated
@Slf4j
public class IngestionController {

    private final ExcelWorkflowService excelWorkflowService;
    private final ExcelProcessingService processingService;
    private final GenerationService generationService;

    public IngestionController(ExcelWorkflowService excelWorkflowService,
                              ExcelProcessingService processingService,
                              GenerationService generationService) {
        this.excelWorkflowService = excelWorkflowService;
        this.processingService = processingService;
        this.generationService = generationService;
    }

    @PostMapping("/_generate")
    public ResponseEntity<GenerateResourceResponse> generate(@RequestBody @Valid GenerateResourceRequest request) {
        log.info("Received async generation request for type: {} and tenantId: {}", 
                request.getGenerateResource().getType(), 
                request.getGenerateResource().getTenantId());
        
        String generationId = generationService.initiateGeneration(request);

        // Return complete resource details with generated ID and PENDING status
        GenerateResource inputResource = request.getGenerateResource();
        GenerateResource responseResource = GenerateResource.builder()
                .id(generationId)
                .tenantId(inputResource.getTenantId())
                .type(inputResource.getType())
                .hierarchyType(inputResource.getHierarchyType())
                .referenceId(inputResource.getReferenceId())
                .status(GenerationConstants.STATUS_PENDING)
                .additionalDetails(inputResource.getAdditionalDetails())
                .createdTime(System.currentTimeMillis())
                .lastModifiedTime(System.currentTimeMillis())
                .build();

        ResponseInfo responseInfo = ResponseInfo.builder()
                .apiId(request.getRequestInfo().getApiId())
                .ver(request.getRequestInfo().getVer())
                .ts(request.getRequestInfo().getTs())
                .status("successful")
                .build();

        GenerateResourceResponse response = GenerateResourceResponse.builder()
                .responseInfo(responseInfo)
                .generateResource(responseResource)
                .build();

        return new ResponseEntity<>(response, HttpStatus.ACCEPTED);
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

    @PostMapping("/_search")
    public ResponseEntity<GenerationSearchResponse> searchGenerations(
            @Valid @RequestBody GenerationSearchRequest request) throws InvalidTenantIdException {
        
        log.info("Received generation search request for tenantId: {}", 
                request.getGenerationSearchCriteria().getTenantId());
        
        GenerationSearchResponse response = generationService.searchGenerations(request);
        
        return new ResponseEntity<>(response, HttpStatus.OK);
    }
}
