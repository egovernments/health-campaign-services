package org.egov.excelingestion.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.egov.common.contract.models.AuditDetails;
import org.egov.excelingestion.config.ErrorConstants;
import org.egov.excelingestion.config.ExcelIngestionConfig;
import org.egov.excelingestion.config.ProcessingConstants;
import org.egov.excelingestion.config.ProcessorConfigurationRegistry;
import org.egov.excelingestion.config.ProcessorConfigurationRegistry.ProcessorSheetConfig;
import org.egov.excelingestion.exception.CustomExceptionHandler;
import org.egov.excelingestion.util.RequestInfoConverter;
import org.egov.excelingestion.util.EnrichmentUtil;
import org.egov.excelingestion.util.ExcelUtil;
import org.egov.excelingestion.web.models.ProcessResource;
import org.egov.excelingestion.web.models.ProcessResourceRequest;
import org.egov.excelingestion.web.models.ValidationError;
import org.egov.excelingestion.web.models.ValidationColumnInfo;
import org.egov.excelingestion.web.models.filestore.FileStoreResponse;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.*;

@Service
@Slf4j
public class ExcelProcessingService {

    private final ValidationService validationService;
    private final SchemaValidationService schemaValidationService;
    private final ConfigBasedProcessingService configBasedProcessingService;
    private final FileStoreService fileStoreService;
    private final LocalizationService localizationService;
    private final RequestInfoConverter requestInfoConverter;
    private final RestTemplate restTemplate;
    private final CustomExceptionHandler exceptionHandler;
    private final ExcelIngestionConfig config;
    private final EnrichmentUtil enrichmentUtil;
    private final ProcessorConfigurationRegistry configRegistry;
    private final ExcelUtil excelUtil;
    
    public ExcelProcessingService(ValidationService validationService,
                                SchemaValidationService schemaValidationService,
                                ConfigBasedProcessingService configBasedProcessingService,
                                FileStoreService fileStoreService,
                                LocalizationService localizationService,
                                RequestInfoConverter requestInfoConverter,
                                RestTemplate restTemplate,
                                CustomExceptionHandler exceptionHandler,
                                ExcelIngestionConfig config,
                                EnrichmentUtil enrichmentUtil,
                                ProcessorConfigurationRegistry configRegistry,
                                ExcelUtil excelUtil) {
        this.validationService = validationService;
        this.schemaValidationService = schemaValidationService;
        this.configBasedProcessingService = configBasedProcessingService;
        this.fileStoreService = fileStoreService;
        this.localizationService = localizationService;
        this.requestInfoConverter = requestInfoConverter;
        this.restTemplate = restTemplate;
        this.exceptionHandler = exceptionHandler;
        this.config = config;
        this.enrichmentUtil = enrichmentUtil;
        this.configRegistry = configRegistry;
        this.excelUtil = excelUtil;
    }

    /**
     * Processes the uploaded Excel file, validates data, and adds error columns
     */
    public ProcessResource processExcelFile(ProcessResourceRequest request) {
        log.info("Starting Excel file processing for type: {}", request.getResourceDetails().getType());

        ProcessResource resource = request.getResourceDetails();
        
        
        try {
            // Extract locale and create localization maps
            String locale = requestInfoConverter.extractLocale(request.getRequestInfo());
            String tenantId = resource.getTenantId();
            String hierarchyType = resource.getHierarchyType();
            
            Map<String, String> mergedLocalizationMap = new HashMap<>();
            
            // Get boundary hierarchy localization if hierarchyType is provided
            if (hierarchyType != null && !hierarchyType.trim().isEmpty()) {
                String boundaryModule = "hcm-boundary-" + hierarchyType.toLowerCase();
                Map<String, String> boundaryLocalizationMap = localizationService.getLocalizedMessages(
                        tenantId, boundaryModule, locale, request.getRequestInfo());
                mergedLocalizationMap.putAll(boundaryLocalizationMap);
            }
            
            // Get schema localization for field names
            String schemaModule = "hcm-admin-schemas";
            Map<String, String> schemaLocalizationMap = localizationService.getLocalizedMessages(
                    tenantId, schemaModule, locale, request.getRequestInfo());
            mergedLocalizationMap.putAll(schemaLocalizationMap);
            
            // Download and validate the Excel file
            try (Workbook workbook = fileStoreService.downloadExcelFromFileStore(resource.getFileStoreId(), resource.getTenantId())) {
                
                // Pre-validate schemas and fetch them before data validation using config-based approach
                Map<String, Map<String, Object>> preValidatedSchemas = configBasedProcessingService.preValidateAndFetchSchemas(
                        workbook, resource, request.getRequestInfo(), mergedLocalizationMap);
                
                // Validate data and collect errors with localization
                List<ValidationError> validationErrors = validateExcelData(workbook, resource, 
                        request.getRequestInfo(), mergedLocalizationMap, preValidatedSchemas);
                
                // Process each sheet: only add validation columns to sheets with errors
                Map<String, ValidationColumnInfo> columnInfoMap = new HashMap<>();
                for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                    Sheet sheet = workbook.getSheetAt(i);
                    String sheetName = sheet.getSheetName();
                    
                    // Skip hidden sheets (wrapped in _h_ prefix and suffix)
                    if (sheetName != null && sheetName.startsWith("_h_") && sheetName.endsWith("_h_")) {
                        log.debug("Skipping hidden sheet in validation column processing: {}", sheetName);
                        continue;
                    }
                    
                    // Step 1: Regular MDMS schema validation for all sheets (if errors exist)
                    List<ValidationError> sheetErrors = validationErrors.stream()
                            .filter(error -> sheetName.equals(error.getSheetName()))
                            .toList();
                    
                    // Only add validation columns if there are errors for this sheet
                    if (!sheetErrors.isEmpty()) {
                        // Clean up validation formatting from template (processed files should only show error columns)
                        validationService.removeValidationFormatting(sheet);
                        
                        // Add validation columns with localization
                        ValidationColumnInfo columnInfo = validationService.addValidationColumns(sheet, mergedLocalizationMap);
                        columnInfoMap.put(sheetName, columnInfo);
                        
                        // Process the validation errors
                        validationService.processValidationErrors(sheet, sheetErrors, columnInfo, mergedLocalizationMap);
                    }
                    
                    // Convert sheet data to get row count - CACHED VERSION
                    List<Map<String, Object>> sheetData = excelUtil.convertSheetToMapListCached(
                            resource.getFileStoreId(), sheetName, sheet);
                    
                    // Enrich resource additionalDetails with error count and status for this sheet
                    enrichmentUtil.enrichErrorAndStatusInAdditionalDetails(resource, sheetErrors);
                    
                    // Enrich resource additionalDetails with row count for this sheet
                    enrichmentUtil.enrichRowCountInAdditionalDetails(resource, sheetData.size());
                }
                
                // Step 2: Process workbook with configured processors (once per workbook, not per sheet)
                configBasedProcessingService.processWorkbookWithProcessor(
                        workbook, resource, request.getRequestInfo(), mergedLocalizationMap);
                
                // Step 3: Handle post-processing (persistence and event publishing) - MOVED AFTER PROCESSORS
                for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                    Sheet sheet = workbook.getSheetAt(i);
                    String sheetName = sheet.getSheetName();
                    
                    if (configBasedProcessingService.isHiddenSheet(sheetName)) {
                        continue; // Skip hidden sheets
                    }
                    
                    // Convert sheet data again for post-processing (after processors have run) - CACHED VERSION
                    List<Map<String, Object>> sheetData = excelUtil.convertSheetToMapListCached(
                            resource.getFileStoreId(), sheetName, sheet);
                    
                    configBasedProcessingService.handlePostProcessing(
                            sheetName, sheetData.size(), resource, mergedLocalizationMap, sheetData, request.getRequestInfo());
                }
                
                // Upload the processed Excel file
                String processedFileStoreId = uploadProcessedExcel(workbook, resource);
                
                // Update resource with results (error counts already enriched during processing)
                ProcessResource updatedResource = updateResourceWithResults(resource, processedFileStoreId);
                
                return updatedResource;
            }
        } catch (IOException e) {
            log.error("Error processing Excel file for ID: {}", resource.getId(), e);
            resource.setStatus(ProcessingConstants.STATUS_FAILED);
            exceptionHandler.throwCustomException(ErrorConstants.EXCEL_PROCESSING_ERROR, 
                    ErrorConstants.EXCEL_PROCESSING_ERROR_MESSAGE, e);
        }
        return null; // This should never be reached due to exception above
    }


    /**
     * Validates data in all sheets of the workbook using pre-fetched schemas
     */
    private List<ValidationError> validateExcelData(Workbook workbook, ProcessResource resource, 
            org.egov.excelingestion.web.models.RequestInfo requestInfo, Map<String, String> localizationMap,
            Map<String, Map<String, Object>> preValidatedSchemas) {
        List<ValidationError> allErrors = new ArrayList<>();
        
        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
            Sheet sheet = workbook.getSheetAt(i);
            String sheetName = sheet.getSheetName();
            
            // Skip hidden sheets (wrapped in _h_ prefix and suffix)
            if (sheetName != null && sheetName.startsWith("_h_") && sheetName.endsWith("_h_")) {
                log.info("Skipping validation for hidden sheet: {}", sheetName);
                continue;
            }
            
            log.info("Validating sheet: {}", sheetName);
            
            // Convert sheet data to List<Map> format for schema validation - CACHED VERSION
            List<Map<String, Object>> sheetData = excelUtil.convertSheetToMapListCached(
                    resource.getFileStoreId(), sheetName, sheet);
            
            // Get schema for this sheet from pre-validated schemas
            Map<String, Object> schema = getSchemaForSheet(sheetName, resource.getType(), localizationMap, preValidatedSchemas);
            
            // Perform schema validation with pre-fetched schema
            List<ValidationError> schemaErrors = schemaValidationService.validateDataWithPreFetchedSchema(
                    sheetData, sheetName, schema, localizationMap);
            
            allErrors.addAll(schemaErrors);
        }
        
        return validationService.mergeErrors(allErrors);
    }

    /**
     * Helper method to get schema for a sheet from pre-validated schemas
     */
    private Map<String, Object> getSchemaForSheet(String sheetName, String type, 
                                                 Map<String, String> localizationMap,
                                                 Map<String, Map<String, Object>> preValidatedSchemas) {
        try {
            // Get processor configuration
            List<ProcessorSheetConfig> configs = configRegistry.getConfigByType(type);
            if (configs == null) {
                return null;
            }
            
            // Find the schema name for this sheet
            for (ProcessorSheetConfig sheetConfig : configs) {
                String localizedName = getLocalizedSheetName(sheetConfig.getSheetNameKey(), localizationMap);
                if (localizedName.equals(sheetName)) {
                    String schemaName = sheetConfig.getSchemaName();
                    if (schemaName != null && preValidatedSchemas.containsKey(schemaName)) {
                        return preValidatedSchemas.get(schemaName);
                    }
                    break;
                }
            }
        } catch (Exception e) {
            log.error("Error getting schema for sheet {}: {}", sheetName, e.getMessage(), e);
        }
        
        return null;
    }
    
    /**
     * Get localized sheet name with 31-char limit handling
     */
    private String getLocalizedSheetName(String sheetKey, Map<String, String> localizationMap) {
        String localizedName = sheetKey;
        
        if (localizationMap != null && localizationMap.containsKey(sheetKey)) {
            localizedName = localizationMap.get(sheetKey);
        }
        
        // Handle Excel's 31 character limit
        if (localizedName.length() > 31) {
            localizedName = localizedName.substring(0, 31);
        }
        
        return localizedName;
    }
    

    /**
     * Uploads the processed Excel file to file store
     */
    private String uploadProcessedExcel(Workbook workbook, ProcessResource resource) throws IOException {
        log.info("Starting optimized workbook write for resource: {}", resource.getId());
        long startTime = System.currentTimeMillis();
        
        // Check if we should use streaming for large workbooks
        SXSSFWorkbook streamingWorkbook = null;
        Workbook workbookToWrite = workbook;
        
        try {
            // Convert to streaming workbook if it's an XSSFWorkbook (for better performance with large files)
            if (workbook instanceof XSSFWorkbook) {
                log.info("Converting to streaming workbook for better performance");
                // Use SXSSFWorkbook with a window of 100 rows in memory
                streamingWorkbook = new SXSSFWorkbook((XSSFWorkbook) workbook, 100);
                streamingWorkbook.setCompressTempFiles(true); // Compress temporary files
                workbookToWrite = streamingWorkbook;
            }
            
            // Use streaming approach with larger buffer for big files
            try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream(8 * 1024 * 1024)) { // Pre-allocate 8MB for large Excel files
                
                // Optimize workbook before writing
                optimizeWorkbookForWriting(workbookToWrite);
                
                log.info("Writing workbook to stream for resource: {}", resource.getId());
                workbookToWrite.write(outputStream);
                
                long writeTime = System.currentTimeMillis() - startTime;
                log.info("Workbook write completed in {}ms for resource: {}", writeTime, resource.getId());
                
                byte[] excelBytes = outputStream.toByteArray();
                log.info("Generated Excel file size: {}KB for resource: {}", excelBytes.length / 1024, resource.getId());
                
                String fileName = String.format("processed_%s_%s_%d.xlsx", 
                        resource.getType(), 
                        resource.getReferenceId(),
                        System.currentTimeMillis());
                
                long totalTime = System.currentTimeMillis() - startTime;
                log.info("Total upload preparation time: {}ms for resource: {}", totalTime, resource.getId());
                
                return fileStoreService.uploadFile(excelBytes, resource.getTenantId(), fileName);
            }
        } finally {
            // Dispose of the streaming workbook to free temporary files
            if (streamingWorkbook != null) {
                streamingWorkbook.dispose();
                log.debug("Disposed streaming workbook temporary files");
            }
        }
    }

    /**
     * Optimize workbook settings for faster write operations
     */
    private void optimizeWorkbookForWriting(Workbook workbook) {
        try {
            // Force calculation mode to manual to speed up write
            if (workbook instanceof org.apache.poi.xssf.usermodel.XSSFWorkbook) {
                org.apache.poi.xssf.usermodel.XSSFWorkbook xssfWorkbook = (org.apache.poi.xssf.usermodel.XSSFWorkbook) workbook;
                
                // Safe approach: Only set if CalcPr exists
                if (xssfWorkbook.getCTWorkbook().getCalcPr() != null) {
                    xssfWorkbook.getCTWorkbook().getCalcPr().setCalcMode(org.openxmlformats.schemas.spreadsheetml.x2006.main.STCalcMode.MANUAL);
                    log.debug("Set workbook calculation mode to manual");
                }
            } else if (workbook instanceof SXSSFWorkbook) {
                // For streaming workbook, get the underlying XSSFWorkbook
                SXSSFWorkbook sxssfWorkbook = (SXSSFWorkbook) workbook;
                XSSFWorkbook xssfWorkbook = sxssfWorkbook.getXSSFWorkbook();
                
                if (xssfWorkbook.getCTWorkbook().getCalcPr() != null) {
                    xssfWorkbook.getCTWorkbook().getCalcPr().setCalcMode(org.openxmlformats.schemas.spreadsheetml.x2006.main.STCalcMode.MANUAL);
                    log.debug("Set streaming workbook calculation mode to manual");
                }
            }
            
            // Simplified optimization - just force recalculation off where possible
            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                org.apache.poi.ss.usermodel.Sheet sheet = workbook.getSheetAt(i);
                try {
                    sheet.setForceFormulaRecalculation(false);
                } catch (Exception e) {
                    // Ignore individual sheet optimization failures
                }
            }
            
            log.debug("Workbook optimized for writing with {} sheets", workbook.getNumberOfSheets());
        } catch (Exception e) {
            log.warn("Failed to optimize workbook for writing, continuing with default settings: {}", e.getMessage());
        }
    }
    
    /**
     * Updates resource with processing results
     * Error counts and validation status are already enriched during processing
     */
    private ProcessResource updateResourceWithResults(ProcessResource resource, String processedFileStoreId) {
        
        // Processing is complete, so status is PROCESSED regardless of validation errors
        String processStatus = ProcessingConstants.STATUS_PROCESSED;
        
        // Ensure additionalDetails exists (should already be populated by enrichment utility)
        Map<String, Object> additionalDetails = resource.getAdditionalDetails();
        if (additionalDetails == null) {
            additionalDetails = new HashMap<>();
        }
        
        // Update audit details
        AuditDetails auditDetails = resource.getAuditDetails();
        if (auditDetails == null) {
            auditDetails = new AuditDetails();
            auditDetails.setCreatedTime(System.currentTimeMillis());
        }
        auditDetails.setLastModifiedTime(System.currentTimeMillis());
        
        return ProcessResource.builder()
                .id(resource.getId())
                .tenantId(resource.getTenantId())
                .type(resource.getType())
                .hierarchyType(resource.getHierarchyType())
                .referenceId(resource.getReferenceId())
                .fileStoreId(resource.getFileStoreId())
                .processedFileStoreId(processedFileStoreId)
                .status(processStatus)
                .additionalDetails(additionalDetails)
                .auditDetails(auditDetails)
                .build();
    }

}