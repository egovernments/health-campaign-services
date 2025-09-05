package org.egov.excelingestion.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.egov.common.contract.models.AuditDetails;
import org.egov.excelingestion.config.ErrorConstants;
import org.egov.excelingestion.config.ExcelIngestionConfig;
import org.egov.excelingestion.config.GeneratorConfigurationRegistry;
import org.egov.excelingestion.config.ProcessingConstants;
import org.egov.excelingestion.exception.CustomExceptionHandler;
import org.egov.excelingestion.util.RequestInfoConverter;
import org.egov.excelingestion.util.EnrichmentUtil;
import org.egov.excelingestion.web.models.ProcessResource;
import org.egov.excelingestion.web.models.ProcessResourceRequest;
import org.egov.excelingestion.web.models.ProcessorGenerationConfig;
import org.egov.excelingestion.web.models.SheetGenerationConfig;
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
    private final GeneratorConfigurationRegistry configRegistry;
    
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
                                GeneratorConfigurationRegistry configRegistry) {
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
    }

    /**
     * Processes the uploaded Excel file, validates data, and adds error columns
     */
    public ProcessResource processExcelFile(ProcessResourceRequest request) {
        log.info("Starting Excel file processing for type: {}", request.getResourceDetails().getType());

        ProcessResource resource = request.getResourceDetails();
        
        // Enrich resource with UUID and status
        enrichmentUtil.enrichProcessResource(resource);
        
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
            try (Workbook workbook = downloadExcelFromFileStore(resource.getFileStoreId(), resource.getTenantId())) {
                
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
                        // Add validation columns with localization
                        ValidationColumnInfo columnInfo = validationService.addValidationColumns(sheet, mergedLocalizationMap);
                        columnInfoMap.put(sheetName, columnInfo);
                        
                        // Process the validation errors
                        validationService.processValidationErrors(sheet, sheetErrors, columnInfo, mergedLocalizationMap);
                    }
                    
                    // Enrich resource additionalDetails with error count and status for this sheet
                    enrichmentUtil.enrichErrorAndStatusInAdditionalDetails(resource, sheetErrors);
                    
                    // Step 2: Check if there's a workbook processor configured for this sheet
                    configBasedProcessingService.processWorkbookWithProcessor(
                            sheetName, workbook, resource, request.getRequestInfo(), mergedLocalizationMap);
                }
                
                // Upload the processed Excel file
                String processedFileStoreId = uploadProcessedExcel(workbook, resource);
                
                // Update resource with results (error counts already enriched during processing)
                return updateResourceWithResults(resource, processedFileStoreId);
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
     * Downloads Excel file from file store
     */
    private Workbook downloadExcelFromFileStore(String fileStoreId, String tenantId) {
        String fileStoreUrl = config.getFilestoreHost() + config.getFilestoreUrlEndpoint();
        
        try {
            // Build URL with query parameters
            String url = String.format("%s?tenantId=%s&fileStoreIds=%s", fileStoreUrl, tenantId, fileStoreId);
            
            ResponseEntity<FileStoreResponse> response = restTemplate.exchange(
                    url, HttpMethod.GET, null, FileStoreResponse.class);
            
            FileStoreResponse responseBody = response.getBody();
            if (responseBody != null) {
                String fileUrl = null;
                
                // Try to get URL from fileStoreIds array first
                if (responseBody.getFiles() != null && !responseBody.getFiles().isEmpty()) {
                    fileUrl = responseBody.getFiles().get(0).getUrl();
                }
                
                // If not found in array, try the file ID to URL mapping
                if (fileUrl == null && responseBody.getFileIdToUrlMap() != null) {
                    fileUrl = responseBody.getFileIdToUrlMap().get(fileStoreId);
                }
                
                if (fileUrl != null) {
                    try (InputStream inputStream = new URL(fileUrl).openStream()) {
                        return new XSSFWorkbook(inputStream);
                    }
                }
            }
            
            exceptionHandler.throwCustomException(ErrorConstants.FILE_URL_RETRIEVAL_ERROR, 
                    ErrorConstants.FILE_URL_RETRIEVAL_ERROR_MESSAGE);
            
        } catch (Exception e) {
            log.error("Error downloading file from file store: {}", e.getMessage());
            exceptionHandler.throwCustomException(ErrorConstants.FILE_DOWNLOAD_ERROR, 
                    ErrorConstants.FILE_DOWNLOAD_ERROR_MESSAGE, e);
        }
        return null; // This should never be reached due to exceptions above
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
            
            // Convert sheet data to List<Map> format for schema validation
            List<Map<String, Object>> sheetData = convertSheetToMapList(sheet);
            
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
            ProcessorGenerationConfig config = configRegistry.getConfigByType(type);
            if (config == null) {
                return null;
            }
            
            // Find the schema name for this sheet
            for (SheetGenerationConfig sheetConfig : config.getSheets()) {
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
     * Converts sheet data to List of Maps for easier processing
     */
    private List<Map<String, Object>> convertSheetToMapList(Sheet sheet) {
        List<Map<String, Object>> data = new ArrayList<>();
        
        Row headerRow = sheet.getRow(0);
        if (headerRow == null) {
            return data;
        }
        
        // Get header names
        List<String> headers = new ArrayList<>();
        for (Cell cell : headerRow) {
            headers.add(getCellValueAsString(cell));
        }
        
        // Process data rows (skip row 1 as it's second header row, start from row 2)
        for (int rowNum = 2; rowNum <= sheet.getLastRowNum(); rowNum++) {
            Row row = sheet.getRow(rowNum);
            if (row == null) continue;
            
            Map<String, Object> rowData = new HashMap<>();
            boolean hasData = false;
            
            for (int colNum = 0; colNum < headers.size(); colNum++) {
                Cell cell = row.getCell(colNum);
                String header = headers.get(colNum);
                Object value = getCellValue(cell);
                
                if (value != null && !value.toString().trim().isEmpty()) {
                    hasData = true;
                }
                
                rowData.put(header, value);
            }
            
            if (hasData) {
                // Add actual Excel row number for error reporting (Excel rows are 1-indexed, +1 to match Excel display)
                rowData.put("__actualRowNumber__", rowNum + 1);
                data.add(rowData);
            }
        }
        
        return data;
    }

    /**
     * Gets cell value as appropriate type
     */
    private Object getCellValue(Cell cell) {
        if (cell == null) return null;
        
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getDateCellValue();
                }
                double numericValue = cell.getNumericCellValue();
                // If it's a whole number, return as integer to avoid .0 display
                if (numericValue == Math.floor(numericValue)) {
                    return (long) numericValue;
                }
                return numericValue;
            case BOOLEAN:
                return cell.getBooleanCellValue();
            case BLANK:
                return "";
            default:
                return null;
        }
    }

    /**
     * Gets cell value as string
     */
    private String getCellValueAsString(Cell cell) {
        Object value = getCellValue(cell);
        return value != null ? value.toString() : "";
    }

    /**
     * Uploads the processed Excel file to file store
     */
    private String uploadProcessedExcel(Workbook workbook, ProcessResource resource) throws IOException {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            workbook.write(outputStream);
            byte[] excelBytes = outputStream.toByteArray();
            
            String fileName = String.format("processed_%s_%s_%d.xlsx", 
                    resource.getType(), 
                    resource.getReferenceId(),
                    System.currentTimeMillis());
            
            return fileStoreService.uploadFile(excelBytes, resource.getTenantId(), fileName);
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