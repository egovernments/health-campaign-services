package org.egov.excelingestion.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.egov.common.contract.models.AuditDetails;
import org.egov.excelingestion.config.ErrorConstants;
import org.egov.excelingestion.config.ExcelIngestionConfig;
import org.egov.excelingestion.config.ValidationConstants;
import org.egov.excelingestion.exception.CustomExceptionHandler;
import org.egov.excelingestion.util.RequestInfoConverter;
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
    private final FileStoreService fileStoreService;
    private final LocalizationService localizationService;
    private final RequestInfoConverter requestInfoConverter;
    private final RestTemplate restTemplate;
    private final CustomExceptionHandler exceptionHandler;
    private final ExcelIngestionConfig config;
    
    public ExcelProcessingService(ValidationService validationService,
                                SchemaValidationService schemaValidationService,
                                FileStoreService fileStoreService,
                                LocalizationService localizationService,
                                RequestInfoConverter requestInfoConverter,
                                RestTemplate restTemplate,
                                CustomExceptionHandler exceptionHandler,
                                ExcelIngestionConfig config) {
        this.validationService = validationService;
        this.schemaValidationService = schemaValidationService;
        this.fileStoreService = fileStoreService;
        this.localizationService = localizationService;
        this.requestInfoConverter = requestInfoConverter;
        this.restTemplate = restTemplate;
        this.exceptionHandler = exceptionHandler;
        this.config = config;
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
            
            // Get localization for validation messages
            String validationModule = "hcm-admin-validation"; 
            Map<String, String> validationLocalizationMap = localizationService.getLocalizedMessages(
                    tenantId, validationModule, locale, request.getRequestInfo());
            
            // Get schema localization for field names
            String schemaModule = "hcm-admin-schemas";
            Map<String, String> schemaLocalizationMap = localizationService.getLocalizedMessages(
                    tenantId, schemaModule, locale, request.getRequestInfo());
            
            // Merge localization maps
            Map<String, String> mergedLocalizationMap = new HashMap<>();
            mergedLocalizationMap.putAll(validationLocalizationMap);
            mergedLocalizationMap.putAll(schemaLocalizationMap);
            
            // Download and validate the Excel file
            try (Workbook workbook = downloadExcelFromFileStore(resource.getFileStoreId(), resource.getTenantId())) {
                
                // Validate data and collect errors with localization
                List<ValidationError> validationErrors = validateExcelData(workbook, resource, request.getRequestInfo(), mergedLocalizationMap);
                
                // Process each sheet: add validation columns and errors
                Map<String, ValidationColumnInfo> columnInfoMap = new HashMap<>();
                for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                    Sheet sheet = workbook.getSheetAt(i);
                    String sheetName = sheet.getSheetName();
                    
                    // Add or find validation columns
                    ValidationColumnInfo columnInfo = validationService.addValidationColumns(sheet);
                    columnInfoMap.put(sheetName, columnInfo);
                    
                    // Get errors for this sheet and process them
                    List<ValidationError> sheetErrors = validationErrors.stream()
                            .filter(error -> sheetName.equals(error.getSheetName()))
                            .toList();
                    
                    validationService.processValidationErrors(sheet, sheetErrors, columnInfo);
                }
                
                // Upload the processed Excel file
                String processedFileStoreId = uploadProcessedExcel(workbook, resource);
                
                // Update resource with results
                return updateResourceWithResults(resource, validationErrors, processedFileStoreId);
            }
        } catch (IOException e) {
            log.error("Error processing Excel file: {}", e.getMessage(), e);
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
     * Validates data in all sheets of the workbook
     */
    private List<ValidationError> validateExcelData(Workbook workbook, ProcessResource resource, 
            org.egov.excelingestion.web.models.RequestInfo requestInfo, Map<String, String> localizationMap) {
        List<ValidationError> allErrors = new ArrayList<>();
        
        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
            Sheet sheet = workbook.getSheetAt(i);
            String sheetName = sheet.getSheetName();
            
            log.info("Validating sheet: {}", sheetName);
            
            // Convert sheet data to List<Map> format for schema validation
            List<Map<String, Object>> sheetData = convertSheetToMapList(sheet);
            
            // Perform schema validation with localization
            List<ValidationError> schemaErrors = schemaValidationService.validateDataWithSchema(
                    sheetData, sheetName, resource.getTenantId(), 
                    resource.getType(), "all", requestInfo, localizationMap);
            
            allErrors.addAll(schemaErrors);
        }
        
        return validationService.mergeErrors(allErrors);
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
                return cell.getNumericCellValue();
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
     */
    private ProcessResource updateResourceWithResults(ProcessResource resource, 
            List<ValidationError> errors, String processedFileStoreId) {
        
        // Count errors
        long errorCount = errors.stream()
                .filter(error -> !ValidationConstants.STATUS_VALID.equals(error.getStatus()))
                .count();
        
        String status = errorCount > 0 ? ValidationConstants.STATUS_INVALID : ValidationConstants.STATUS_VALID;
        
        // Update additional details
        Map<String, Object> additionalDetails = resource.getAdditionalDetails();
        if (additionalDetails == null) {
            additionalDetails = new HashMap<>();
        }
        
        additionalDetails.put("totalErrors", errorCount);
        additionalDetails.put("totalRecordsProcessed", errors.size());
        additionalDetails.put("hasValidationErrors", errorCount > 0);
        additionalDetails.put("processedTimestamp", System.currentTimeMillis());
        
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
                .status(status)
                .additionalDetails(additionalDetails)
                .auditDetails(auditDetails)
                .build();
    }
}