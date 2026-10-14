package org.egov.excelingestion.web.controller;

import lombok.extern.slf4j.Slf4j;
import org.egov.excelingestion.constants.GenerationConstants;
import org.egov.excelingestion.service.ExcelWorkflowService;
import org.egov.excelingestion.service.ExcelProcessingService;
import org.egov.excelingestion.service.GenerationService;
import org.egov.excelingestion.service.ProcessingService;
import org.egov.excelingestion.service.SheetDataService;
import org.egov.excelingestion.util.RequestInfoConverter;
import org.egov.excelingestion.config.ProcessingConstants;
import org.egov.excelingestion.web.models.GenerateResource;
import org.egov.excelingestion.web.models.GenerateResourceRequest;
import org.egov.excelingestion.web.models.GenerateResourceResponse;
import org.egov.excelingestion.web.models.ProcessResource;
import org.egov.excelingestion.web.models.ProcessResourceRequest;
import org.egov.excelingestion.web.models.ProcessResourceResponse;
import org.egov.common.contract.request.RequestInfo;
import org.egov.excelingestion.web.models.GenerationSearchRequest;
import org.egov.excelingestion.web.models.GenerationSearchResponse;
import org.egov.excelingestion.web.models.ProcessingSearchRequest;
import org.egov.excelingestion.web.models.ProcessingSearchResponse;
import org.egov.excelingestion.web.models.SheetDataSearchRequest;
import org.egov.excelingestion.web.models.SheetDataDeleteRequest;
import org.egov.excelingestion.web.models.SheetDataSearchResponse;
import org.egov.excelingestion.web.models.SheetDataDeleteResponse;
import org.egov.excelingestion.web.models.DeleteDetails;
import org.egov.common.contract.response.ResponseInfo;
import org.egov.common.exception.InvalidTenantIdException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.HashMap;
import java.util.Map;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/v1/data")
@Validated
@Slf4j
@Tag(name = "Data Management (V1)", description = "Legacy data generation and processing endpoints")
public class IngestionController {

    private final ExcelWorkflowService excelWorkflowService;
    private final ExcelProcessingService excelProcessingService;
    private final GenerationService generationService;
    private final ProcessingService processingService;
    private final SheetDataService sheetDataService;
    private final RequestInfoConverter requestInfoConverter;

    public IngestionController(ExcelWorkflowService excelWorkflowService,
                              ExcelProcessingService excelProcessingService,
                              GenerationService generationService,
                              ProcessingService processingService,
                              SheetDataService sheetDataService,
                              RequestInfoConverter requestInfoConverter) {
        this.excelWorkflowService = excelWorkflowService;
        this.excelProcessingService = excelProcessingService;
        this.generationService = generationService;
        this.processingService = processingService;
        this.sheetDataService = sheetDataService;
        this.requestInfoConverter = requestInfoConverter;
    }

    @PostMapping("/generate/_init")
    @Operation(
            summary = "Initialize template generation",
            description = "Kicks off asynchronous template generation and returns a queued generation resource."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "202", description = "Generation request accepted",
                    content = @Content(schema = @Schema(implementation = GenerateResourceResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request payload")
    })
    public ResponseEntity<GenerateResourceResponse> generate(@RequestBody @Valid GenerateResourceRequest request) {
        log.info("Received async generation request for type: {} and tenantId: {}", 
                request.getGenerateResource().getType(), 
                request.getGenerateResource().getTenantId());
        
        String generationId = generationService.initiateGeneration(request);

        // Return complete resource details with generated ID and PENDING status
        GenerateResource inputResource = request.getGenerateResource();
        
        // Extract locale from RequestInfo using RequestInfoConverter
        String locale = inputResource.getLocale() != null ? inputResource.getLocale()
                : requestInfoConverter.extractLocale(request.getRequestInfo());


        GenerateResource responseResource = GenerateResource.builder()
                .id(generationId)
                .tenantId(inputResource.getTenantId())
                .type(inputResource.getType())
                .hierarchyType(inputResource.getHierarchyType())
                .referenceId(inputResource.getReferenceId())
                .referenceType(inputResource.getReferenceType())
                .status(GenerationConstants.STATUS_QUEUED)
                .locale(locale)
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

    @PostMapping("/process/_validation")
    @Operation(
            summary = "Validate uploaded excel",
            description = "Performs validation-only processing and returns queued processing status."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "202", description = "Validation request accepted",
                    content = @Content(schema = @Schema(implementation = ProcessResourceResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request payload")
    })
    public ResponseEntity<ProcessResourceResponse> processExcelValidation(
            @Valid @RequestBody ProcessResourceRequest request) {

        log.info("Received process validation request for type: {} and tenantId: {}",
                request.getResourceDetails().getType(),
                request.getResourceDetails().getTenantId());

        RequestInfo requestInfo = request.getRequestInfo();
        String processingId = processingService.initiateProcessing(request);

        // Extract locale from RequestInfo using RequestInfoConverter
        String locale = request.getResourceDetails().getLocale() != null
                ? request.getResourceDetails().getLocale()
                : requestInfoConverter.extractLocale(requestInfo);

        // Return the resource with pending status and processing ID
        ProcessResource responseResource = ProcessResource.builder()
                .id(processingId)
                .tenantId(request.getResourceDetails().getTenantId())
                .type(request.getResourceDetails().getType())
                .hierarchyType(request.getResourceDetails().getHierarchyType())
                .referenceId(request.getResourceDetails().getReferenceId())
                .referenceType(request.getResourceDetails().getReferenceType())
                .fileStoreId(request.getResourceDetails().getFileStoreId())
                .status(ProcessingConstants.STATUS_PENDING)
                .locale(locale)
                .additionalDetails(request.getResourceDetails().getAdditionalDetails())
                .build();
        
        ProcessResourceResponse response = ProcessResourceResponse.builder()
                .responseInfo(org.egov.common.contract.response.ResponseInfo.builder()
                        .apiId(requestInfo.getApiId())
                        .ver(requestInfo.getVer())
                        .ts(requestInfo.getTs())
                        .status("successful")
                        .build())
                .processResource(responseResource)
                .build();
        
        return new ResponseEntity<>(response, HttpStatus.ACCEPTED);
    }

    @PostMapping("/process/_create")
    @Operation(
            summary = "Create processing job",
            description = "Validates and starts asynchronous parse/create flow for uploaded excel."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "202", description = "Processing request accepted",
                    content = @Content(schema = @Schema(implementation = ProcessResourceResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request payload")
    })
    public ResponseEntity<ProcessResourceResponse> processExcelParse(
            @Valid @RequestBody ProcessResourceRequest request) {

        log.info("Received process request for type: {} and tenantId: {}",
                request.getResourceDetails().getType(),
                request.getResourceDetails().getTenantId());

        RequestInfo requestInfo = request.getRequestInfo();
        String processingId = processingService.initiateProcessing(request);

        // Extract locale from RequestInfo using RequestInfoConverter
        String locale = request.getResourceDetails().getLocale() != null
                ? request.getResourceDetails().getLocale()
                : requestInfoConverter.extractLocale(requestInfo);

        // Return the resource with pending status and processing ID
        ProcessResource responseResource = ProcessResource.builder()
                .id(processingId)
                .tenantId(request.getResourceDetails().getTenantId())
                .type(request.getResourceDetails().getType())
                .hierarchyType(request.getResourceDetails().getHierarchyType())
                .referenceId(request.getResourceDetails().getReferenceId())
                .referenceType(request.getResourceDetails().getReferenceType())
                .fileStoreId(request.getResourceDetails().getFileStoreId())
                .status(ProcessingConstants.STATUS_PENDING)
                .locale(locale)
                .additionalDetails(request.getResourceDetails().getAdditionalDetails())
                .build();

        ProcessResourceResponse response = ProcessResourceResponse.builder()
                .responseInfo(org.egov.common.contract.response.ResponseInfo.builder()
                        .apiId(requestInfo.getApiId())
                        .ver(requestInfo.getVer())
                        .ts(requestInfo.getTs())
                        .status("successful")
                        .build())
                .processResource(responseResource)
                .build();

        return new ResponseEntity<>(response, HttpStatus.ACCEPTED);
    }

    @PostMapping("/generate/_search")
    @Operation(
            summary = "Search generation jobs",
            description = "Searches generation records by criteria and returns current status/details."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Generation search results",
                    content = @Content(schema = @Schema(implementation = GenerationSearchResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request payload")
    })
    public ResponseEntity<GenerationSearchResponse> searchGenerations(
            @Valid @RequestBody GenerationSearchRequest request) throws InvalidTenantIdException {
        
        log.info("Received generation search request for tenantId: {}", 
                request.getGenerationSearchCriteria().getTenantId());
        
        GenerationSearchResponse response = generationService.searchGenerations(request);
        
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @PostMapping("/process/_search")
    @Operation(
            summary = "Search processing jobs",
            description = "Searches processing records by criteria and returns current status/details."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Processing search results",
                    content = @Content(schema = @Schema(implementation = ProcessingSearchResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request payload")
    })
    public ResponseEntity<ProcessingSearchResponse> searchProcessing(
            @Valid @RequestBody ProcessingSearchRequest request) throws InvalidTenantIdException {
        
        log.info("Received processing search request for tenantId: {}", 
                request.getProcessingSearchCriteria().getTenantId());
        
        ProcessingSearchResponse response = processingService.searchProcessing(request);
        
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    /**
     * Search sheet data temp records
     */
    @PostMapping("/sheet/_search")
    @Operation(
            summary = "Search temporary sheet rows",
            description = "Fetches staged sheet row data using the provided search criteria."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Sheet data search results",
                    content = @Content(schema = @Schema(implementation = SheetDataSearchResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request payload")
    })
    public ResponseEntity<SheetDataSearchResponse> searchSheetData(@Valid @RequestBody SheetDataSearchRequest request) {
        log.info("Received sheet data search request for tenant: {}", 
                request.getSheetDataSearchCriteria().getTenantId());
        
        SheetDataSearchResponse result = sheetDataService.searchSheetData(request);
        
        return new ResponseEntity<>(result, HttpStatus.OK);
    }

    /**
     * Delete sheet data using URL parameters
     */
    @PostMapping("/sheet/_delete")
    @Operation(
            summary = "Delete temporary sheet rows",
            description = "Deletes staged sheet data for the given tenantId, referenceId and fileStoreId."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "202", description = "Sheet data delete accepted",
                    content = @Content(schema = @Schema(implementation = SheetDataDeleteResponse.class))),
            @ApiResponse(responseCode = "400", description = "Missing or invalid query parameters")
    })
    public ResponseEntity<SheetDataDeleteResponse> deleteSheetData(
            @RequestParam @NotBlank(message = "SHEET_DATA_INVALID_TENANT") String tenantId,
            @RequestParam @NotBlank(message = "SHEET_DATA_DELETE_MISSING_PARAMS") String referenceId,
            @RequestParam @NotBlank(message = "SHEET_DATA_DELETE_MISSING_PARAMS") String fileStoreId,
            @RequestBody @Valid RequestInfo requestInfo) {
        
        log.info("Received sheet data delete request for tenant: {}, referenceId: {}, fileStoreId: {}", 
                tenantId, referenceId, fileStoreId);
        
        // Create delete request object for service
        SheetDataDeleteRequest deleteRequest = SheetDataDeleteRequest.builder()
                .requestInfo(requestInfo)
                .tenantId(tenantId)
                .referenceId(referenceId)
                .fileStoreId(fileStoreId)
                .build();
        
        sheetDataService.deleteSheetData(deleteRequest);
        
        // Create response info
        ResponseInfo responseInfo = ResponseInfo.builder()
                .apiId(requestInfo.getApiId())
                .ver(requestInfo.getVer())
                .ts(requestInfo.getTs())
                .status("successful")
                .build();
        
        // Create delete details
        DeleteDetails deleteDetails = DeleteDetails.builder()
                .message(org.egov.excelingestion.config.ErrorConstants.SHEET_DATA_DELETE_SUCCESS)
                .tenantId(tenantId)
                .referenceId(referenceId)
                .fileStoreId(fileStoreId)
                .build();
        
        // Create final response
        SheetDataDeleteResponse response = SheetDataDeleteResponse.builder()
                .responseInfo(responseInfo)
                .deleteDetails(deleteDetails)
                .build();
        
        return new ResponseEntity<>(response, HttpStatus.ACCEPTED);
    }
}
