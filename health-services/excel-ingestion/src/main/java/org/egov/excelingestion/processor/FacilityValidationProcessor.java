package org.egov.excelingestion.processor;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.egov.excelingestion.config.ExcelIngestionConfig;
import org.egov.excelingestion.config.ValidationConstants;
import org.egov.excelingestion.service.ValidationService;
import org.egov.excelingestion.web.models.*;
import org.egov.excelingestion.util.LocalizationUtil;
import org.egov.excelingestion.util.EnrichmentUtil;
import org.egov.excelingestion.util.ExcelUtil;
import org.egov.excelingestion.service.CampaignService;
import org.egov.excelingestion.service.BoundaryService;
import org.egov.excelingestion.util.BoundaryUtil;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
@Slf4j
public class FacilityValidationProcessor implements IWorkbookProcessor {

    private final ValidationService validationService;
    private final ExcelIngestionConfig config;
    private final EnrichmentUtil enrichmentUtil;
    private final CampaignService campaignService;
    private final BoundaryService boundaryService;
    private final BoundaryUtil boundaryUtil;
    private final ExcelUtil excelUtil;

    public FacilityValidationProcessor(ValidationService validationService, 
                                     ExcelIngestionConfig config,
                                     EnrichmentUtil enrichmentUtil,
                                     CampaignService campaignService,
                                     BoundaryService boundaryService,
                                     BoundaryUtil boundaryUtil,
                                     ExcelUtil excelUtil) {
        this.validationService = validationService;
        this.config = config;
        this.enrichmentUtil = enrichmentUtil;
        this.campaignService = campaignService;
        this.boundaryService = boundaryService;
        this.boundaryUtil = boundaryUtil;
        this.excelUtil = excelUtil;
    }

    @Override
    public Workbook processWorkbook(Workbook workbook, 
                                  String sheetName,
                                  ProcessResource resource,
                                  RequestInfo requestInfo,
                                  Map<String, String> localizationMap) {
        try {
            // Find the facility sheet
            Sheet sheet = workbook.getSheet(sheetName);
            if (sheet == null) {
                log.warn("Sheet {} not found in workbook", sheetName);
                return workbook;
            }

            log.info("Starting facility validation for sheet: {}", sheetName);

            // Convert sheet data to map list - CACHED VERSION
            List<Map<String, Object>> sheetData = excelUtil.convertSheetToMapListCached(
                    resource.getFileStoreId(), sheetName, sheet);
            
            if (sheetData.isEmpty()) {
                log.info("No data found in sheet, skipping validation");
                return workbook;
            }

            List<ValidationError> errors = new ArrayList<>();
            
            // Validate boundary keys for active facilities
            validateBoundaryKeys(sheetData, errors, localizationMap);
            
            // Validate campaign boundaries
            validateCampaignBoundaries(sheetData, resource, requestInfo, errors, localizationMap);
            
            log.info("Facility validation completed with {} errors", errors.size());

            // Only add error columns if there are validation errors and enrich existing if present
            if (!errors.isEmpty()) {
                log.info("Found {} validation errors, adding/enriching error columns", errors.size());
                
                // Check if error columns already exist
                ValidationColumnInfo columnInfo = checkAndAddErrorColumns(sheet, localizationMap);
                
                // Process validation errors - enrich existing or add new error details
                processValidationErrors(sheet, errors, columnInfo, localizationMap);
            } else {
                log.info("No validation errors found, no error columns needed");
            }
            
            // Enrich resource additional details with error information
            enrichmentUtil.enrichErrorAndStatusInAdditionalDetails(resource, errors);
            
            return workbook;

        } catch (Exception e) {
            log.error("Error processing facility validation sheet: {}", e.getMessage(), e);
            return workbook;
        }
    }

    /**
     * Check if error columns exist and add them if needed
     */
    private ValidationColumnInfo checkAndAddErrorColumns(Sheet sheet, Map<String, String> localizationMap) {
        Row headerRow = sheet.getRow(0);
        if (headerRow == null) {
            return validationService.addValidationColumns(sheet, localizationMap);
        }
        
        // Check if error columns already exist
        boolean hasErrorColumn = false;
        boolean hasStatusColumn = false;
        int errorColumnIndex = -1;
        int statusColumnIndex = -1;
        
        for (Cell cell : headerRow) {
            String headerValue = ExcelUtil.getCellValueAsString(cell);
            if (ValidationConstants.ERROR_DETAILS_COLUMN_NAME.equals(headerValue)) {
                hasErrorColumn = true;
                errorColumnIndex = cell.getColumnIndex();
            } else if (ValidationConstants.STATUS_COLUMN_NAME.equals(headerValue)) {
                hasStatusColumn = true;
                statusColumnIndex = cell.getColumnIndex();
            }
        }
        
        // If both columns exist, return their positions
        if (hasErrorColumn && hasStatusColumn) {
            ValidationColumnInfo columnInfo = new ValidationColumnInfo();
            columnInfo.setErrorColumnIndex(errorColumnIndex);
            columnInfo.setStatusColumnIndex(statusColumnIndex);
            return columnInfo;
        }
        
        // If columns don't exist, add them
        return validationService.addValidationColumns(sheet, localizationMap);
    }
    
    /**
     * Process validation errors and enrich existing error data with semicolon separation
     */
    private void processValidationErrors(Sheet sheet, List<ValidationError> errors, 
                                       ValidationColumnInfo columnInfo, Map<String, String> localizationMap) {
        // Create a map of row number to errors for quick lookup
        Map<Integer, List<ValidationError>> errorsByRow = new HashMap<>();
        for (ValidationError error : errors) {
            errorsByRow.computeIfAbsent(error.getRowNumber(), k -> new ArrayList<>()).add(error);
        }
        
        // Process each data row (skip header row)
        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;
            
            int actualRowNumber = i + 1; // Convert to 1-based row number
            List<ValidationError> rowErrors = errorsByRow.get(actualRowNumber);
            
            if (rowErrors != null && !rowErrors.isEmpty()) {
                // Get existing error and status values
                Cell errorCell = row.getCell(columnInfo.getErrorColumnIndex());
                Cell statusCell = row.getCell(columnInfo.getStatusColumnIndex());
                
                String existingErrors = errorCell != null ? ExcelUtil.getCellValueAsString(errorCell) : "";
                String existingStatus = statusCell != null ? ExcelUtil.getCellValueAsString(statusCell) : "";
                
                // Build new error message
                StringBuilder errorMessages = new StringBuilder();
                if (existingErrors != null && !existingErrors.trim().isEmpty()) {
                    errorMessages.append(existingErrors);
                }
                
                String status = existingStatus != null && !existingStatus.isEmpty() ? 
                    existingStatus : ValidationConstants.STATUS_VALID;
                
                for (ValidationError error : rowErrors) {
                    if (errorMessages.length() > 0) {
                        errorMessages.append("; ");
                    }
                    errorMessages.append(error.getErrorDetails());
                    
                    // Set status to the most severe error
                    if (ValidationConstants.STATUS_ERROR.equals(error.getStatus())) {
                        status = ValidationConstants.STATUS_ERROR;
                    } else if (ValidationConstants.STATUS_INVALID.equals(error.getStatus()) && 
                              !ValidationConstants.STATUS_ERROR.equals(status)) {
                        status = ValidationConstants.STATUS_INVALID;
                    }
                }
                
                // Create cells if they don't exist
                if (errorCell == null) {
                    errorCell = row.createCell(columnInfo.getErrorColumnIndex());
                }
                if (statusCell == null) {
                    statusCell = row.createCell(columnInfo.getStatusColumnIndex());
                }
                
                // Set the enriched values
                errorCell.setCellValue(errorMessages.toString());
                statusCell.setCellValue(status);
            }
        }
    }
    
    /**
     * Validate boundary keys for active facilities
     */
    private void validateBoundaryKeys(List<Map<String, Object>> sheetData, List<ValidationError> errors,
                                    Map<String, String> localizationMap) {
        log.info("Validating boundary keys for {} records", sheetData.size());
        
        for (Map<String, Object> rowData : sheetData) {
            String usage = ExcelUtil.getValueAsString(rowData.get("HCM_ADMIN_CONSOLE_FACILITY_USAGE"));
            String boundaryCode = ExcelUtil.getValueAsString(rowData.get("HCM_ADMIN_CONSOLE_BOUNDARY_CODE"));
            Integer rowNumber = (Integer) rowData.get("__actualRowNumber__");
            
            // Only validate boundary key if usage is "active" 
            if ("Active".equals(usage)) {
                if (boundaryCode == null || boundaryCode.trim().isEmpty()) {
                    ValidationError error = new ValidationError();
                    error.setRowNumber(rowNumber);
                    error.setErrorDetails(LocalizationUtil.getLocalizedMessage(localizationMap, 
                        "HCM_FACILITY_BOUNDARY_CODE_REQUIRED_FOR_ACTIVE_USAGE", 
                        "Boundary selection is required if usage is active"));
                    error.setStatus(ValidationConstants.STATUS_INVALID);
                    errors.add(error);
                }
            }
        }
        
        log.info("Boundary key validation completed");
    }
    
    /**
     * Validate that boundary codes exist within campaign boundaries
     */
    private void validateCampaignBoundaries(List<Map<String, Object>> sheetData, ProcessResource resource,
                                          RequestInfo requestInfo, List<ValidationError> errors,
                                          Map<String, String> localizationMap) {
        log.info("Validating campaign boundaries for {} records", sheetData.size());
        
        try {
            // Get campaign boundaries from campaign service
            List<CampaignSearchResponse.BoundaryDetail> campaignBoundaries = 
                campaignService.getBoundariesFromCampaign(resource.getReferenceId(), resource.getTenantId(), requestInfo);
            
            if (campaignBoundaries == null || campaignBoundaries.isEmpty()) {
                log.warn("No campaign boundaries found for campaign: {}", resource.getReferenceId());
                return;
            }
            
            // Get enriched boundary codes using BoundaryUtil with caching
            Set<String> validBoundaryCodes = boundaryUtil.getEnrichedBoundaryCodesFromCampaign(
                resource.getId(), resource.getReferenceId(), resource.getTenantId(), resource.getHierarchyType(), requestInfo);
            
            log.info("Found {} valid boundary codes from campaign boundaries", validBoundaryCodes.size());
            
            // Validate each row's boundary code
            for (Map<String, Object> rowData : sheetData) {
                String boundaryCode = ExcelUtil.getValueAsString(rowData.get("HCM_ADMIN_CONSOLE_BOUNDARY_CODE"));
                Integer rowNumber = (Integer) rowData.get("__actualRowNumber__");
                
                // Skip validation if boundary code is null or empty
                if (boundaryCode == null || boundaryCode.trim().isEmpty()) {
                    continue;
                }
                
                // Check if boundary code exists in valid campaign boundaries
                if (!validBoundaryCodes.contains(boundaryCode.trim())) {
                    ValidationError error = new ValidationError();
                    error.setRowNumber(rowNumber);
                    error.setErrorDetails(LocalizationUtil.getLocalizedMessage(localizationMap, 
                        "HCM_BOUNDARY_CODE_NOT_IN_CAMPAIGN", 
                        "Boundary code is not part of campaign boundaries"));
                    error.setStatus(ValidationConstants.STATUS_INVALID);
                    errors.add(error);
                }
            }
            
            log.info("Campaign boundary validation completed");
            
        } catch (Exception e) {
            log.error("Error during campaign boundary validation: {}", e.getMessage(), e);
            // Add a general error for all rows if boundary validation fails
            for (Map<String, Object> rowData : sheetData) {
                Integer rowNumber = (Integer) rowData.get("__actualRowNumber__");
                ValidationError error = new ValidationError();
                error.setRowNumber(rowNumber);
                error.setErrorDetails(LocalizationUtil.getLocalizedMessage(localizationMap, 
                    "HCM_BOUNDARY_VALIDATION_FAILED", 
                    "Boundary validation failed due to system error"));
                error.setStatus(ValidationConstants.STATUS_ERROR);
                errors.add(error);
            }
        }
    }
}