package org.egov.excelingestion.web.controller;

import lombok.extern.slf4j.Slf4j;
import org.egov.excelingestion.service.ExcelProcessingService;
import org.egov.excelingestion.util.RequestInfoConverter;
import org.egov.excelingestion.web.models.ProcessResource;
import org.egov.excelingestion.web.models.ProcessResourceRequest;
import org.egov.excelingestion.web.models.RequestInfo;
import org.egov.excelingestion.web.models.GenerateResourceResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/v1/data")
@Slf4j
public class ProcessController {

    @Autowired
    private ExcelProcessingService processingService;

    @PostMapping("/_process")
    public ResponseEntity<GenerateResourceResponse> processExcel(
            @Valid @RequestBody ProcessResourceRequest request) {
        
        log.info("Received process request for type: {} and tenantId: {}", 
                request.getResourceDetails().getType(), 
                request.getResourceDetails().getTenantId());
        
        try {
            // Convert RequestInfo if needed
            RequestInfo requestInfo = request.getRequestInfo();
            
            // Process the Excel file
            ProcessResource processedResource = processingService.processExcelFile(request);
            
            // Build response
            GenerateResourceResponse response = GenerateResourceResponse.builder()
                    .responseInfo(org.egov.common.contract.response.ResponseInfo.builder()
                            .apiId(requestInfo.getApiId())
                            .ver(requestInfo.getVer())
                            .ts(requestInfo.getTs())
                            .status("successful")
                            .build())
                    .generateResource(convertToGenerateResource(processedResource))
                    .build();
            
            return new ResponseEntity<>(response, HttpStatus.OK);
            
        } catch (Exception e) {
            log.error("Error processing Excel file: {}", e.getMessage(), e);
            
            // Build error response
            GenerateResourceResponse errorResponse = GenerateResourceResponse.builder()
                    .responseInfo(org.egov.common.contract.response.ResponseInfo.builder()
                            .apiId(request.getRequestInfo().getApiId())
                            .ver(request.getRequestInfo().getVer())
                            .ts(request.getRequestInfo().getTs())
                            .status("error")
                            .build())
                    .build();
            
            return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
    
    private org.egov.excelingestion.web.models.GenerateResource convertToGenerateResource(ProcessResource processResource) {
        return org.egov.excelingestion.web.models.GenerateResource.builder()
                .id(processResource.getId())
                .tenantId(processResource.getTenantId())
                .type(processResource.getType())
                .hierarchyType(processResource.getHierarchyType())
                .referenceId(processResource.getReferenceId())
                .status(processResource.getStatus())
                .fileStoreId(processResource.getProcessedFileStoreId())
                .additionalDetails(processResource.getAdditionalDetails())
                .auditDetails(processResource.getAuditDetails())
                .build();
    }
}