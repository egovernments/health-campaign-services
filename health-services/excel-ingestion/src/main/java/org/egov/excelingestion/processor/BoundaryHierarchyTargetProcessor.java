package org.egov.excelingestion.processor;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.egov.excelingestion.config.ErrorConstants;
import org.egov.excelingestion.config.ProcessingConstants;
import org.egov.excelingestion.config.ValidationConstants;
import org.egov.excelingestion.exception.CustomExceptionHandler;
import org.egov.excelingestion.service.CampaignService;
import org.egov.excelingestion.service.MDMSService;
import org.egov.excelingestion.service.SchemaValidationService;
import org.egov.excelingestion.service.ValidationService;
import org.egov.excelingestion.util.BoundaryUtil;
import org.egov.excelingestion.util.EnrichmentUtil;
import org.egov.excelingestion.util.ExcelUtil;
import org.egov.excelingestion.web.models.ProcessResource;
import org.egov.excelingestion.web.models.RequestInfo;
import org.egov.excelingestion.web.models.ValidationError;
import org.egov.excelingestion.web.models.ValidationColumnInfo;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Processor for target sheet validation using dynamic schema based on projectType
 */
@Component
@Slf4j
public class BoundaryHierarchyTargetProcessor implements IWorkbookProcessor {

    private final MDMSService mdmsService;
    private final SchemaValidationService schemaValidationService;
    private final ValidationService validationService;
    private final EnrichmentUtil enrichmentUtil;
    private final CustomExceptionHandler exceptionHandler;
    private final ExcelUtil excelUtil;
    private final CampaignService campaignService;
    private final BoundaryUtil boundaryUtil;

    public BoundaryHierarchyTargetProcessor(MDMSService mdmsService, 
                                          SchemaValidationService schemaValidationService,
                                          ValidationService validationService,
                                          EnrichmentUtil enrichmentUtil,
                                          CustomExceptionHandler exceptionHandler,
                                          ExcelUtil excelUtil,
                                          CampaignService campaignService,
                                          BoundaryUtil boundaryUtil) {
        this.mdmsService = mdmsService;
        this.schemaValidationService = schemaValidationService;
        this.validationService = validationService;
        this.enrichmentUtil = enrichmentUtil;
        this.exceptionHandler = exceptionHandler;
        this.excelUtil = excelUtil;
        this.campaignService = campaignService;
        this.boundaryUtil = boundaryUtil;
    }

    /**
     * Process workbook - validate and add error columns directly to workbook
     * Implementation of IWorkbookProcessor interface
     */
    @Override
    public Workbook processWorkbook(Workbook workbook, 
                                  String sheetName,
                                  ProcessResource resource,
                                  RequestInfo requestInfo,
                                  Map<String, String> localizationMap) {
        try {
            // Find the target sheet
            Sheet sheet = workbook.getSheet(sheetName);
            if (sheet == null) {
                log.warn("Sheet {} not found in workbook", sheetName);
                return workbook;
            }

            // Convert sheet data to map list - CACHED VERSION
            List<Map<String, Object>> originalData = excelUtil.convertSheetToMapListCached(
                    resource.getFileStoreId(), sheetName, sheet);
            
            // Get projectType from campaign service instead of additionalDetails
            String projectType = campaignService.getProjectTypeFromCampaign(
                resource.getReferenceId(), resource.getTenantId(), requestInfo);
            if (projectType == null || projectType.isEmpty()) {
                log.info("No projectType found in campaign, skipping additional validation");
                return workbook;
            }

            // Fetch target schema from MDMS
            String schemaName = "target-" + projectType;
            Map<String, Object> schema = fetchTargetSchema(schemaName, resource.getTenantId(), requestInfo);
            if (schema == null) {
                log.error("Target schema '{}' not found in MDMS for tenant: {}", schemaName, resource.getTenantId());
                exceptionHandler.throwCustomException(ErrorConstants.SCHEMA_NOT_FOUND_IN_MDMS,
                        ErrorConstants.SCHEMA_NOT_FOUND_IN_MDMS_MESSAGE
                                .replace("{0}", schemaName)
                                .replace("{1}", resource.getTenantId()));
            }

            log.info("Validating target sheet data with schema: {}", schemaName);

            // Perform validation - extract properties from schema
            Map<String, Object> schemaProperties = (Map<String, Object>) schema.get("properties");
            if (schemaProperties == null) {
                log.warn("No properties found in schema {}, skipping validation", schemaName);
                return workbook;
            }
            
            // First validate boundary codes against campaign enriched boundaries
            List<ValidationError> boundaryValidationErrors = validateCampaignBoundaries(originalData, resource, requestInfo, localizationMap);
            
            // Then perform schema validation
            List<ValidationError> schemaValidationErrors = schemaValidationService.validateDataWithPreFetchedSchema(
                    originalData, "HCM_CONSOLE_BOUNDARY_HIERARCHY", schemaProperties, localizationMap);
            
            // Combine all validation errors
            List<ValidationError> validationErrors = new ArrayList<>();
            validationErrors.addAll(boundaryValidationErrors);
            validationErrors.addAll(schemaValidationErrors);

            // Only add error columns if there are validation errors
            if (!validationErrors.isEmpty()) {
                log.info("Found {} validation errors, adding error columns", validationErrors.size());
                
                // Clean up validation formatting from template and lock sheet (processed files should only show error columns)
                validationService.removeValidationFormatting(sheet);
                
                // Use ValidationService to add error columns (same styling as facility/user sheets)
                ValidationColumnInfo columnInfo = validationService.addValidationColumns(sheet, localizationMap);
                
                // Process validation errors using ValidationService
                validationService.processValidationErrors(sheet, validationErrors, columnInfo, localizationMap);
                
                // Enrich resource additionalDetails with error count and status
                enrichmentUtil.enrichErrorAndStatusInAdditionalDetails(resource, validationErrors);
            } else {
                log.info("No validation errors found, no error columns needed");
                
                // Still enrich with zero errors
                enrichmentUtil.enrichErrorAndStatusInAdditionalDetails(resource, validationErrors);
            }
            
            return workbook;

        } catch (org.egov.tracer.model.CustomException e) {
            // Re-throw CustomExceptions (like schema not found) to stop processing
            log.error("Custom exception in target sheet processing: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Error processing target sheet: {}", e.getMessage(), e);
            return workbook;
        }
    }


    /**
     * Fetch target schema from MDMS
     */
    private Map<String, Object> fetchTargetSchema(String schemaName, String tenantId, RequestInfo requestInfo) {
        try {
            Map<String, Object> filters = new HashMap<>();
            filters.put("title", schemaName);

            List<Map<String, Object>> mdmsList = mdmsService.searchMDMS(
                    requestInfo, tenantId, ProcessingConstants.MDMS_SCHEMA_CODE, filters, 1, 0);

            if (!mdmsList.isEmpty()) {
                Map<String, Object> mdmsData = mdmsList.get(0);
                return (Map<String, Object>) mdmsData.get("data");
            }
        } catch (Exception e) {
            log.error("Error fetching schema {}: {}", schemaName, e.getMessage());
        }
        return null;
    }

    /**
     * Validate boundary codes against campaign enriched boundaries
     */
    private List<ValidationError> validateCampaignBoundaries(List<Map<String, Object>> sheetData, 
                                                           ProcessResource resource, RequestInfo requestInfo,
                                                           Map<String, String> localizationMap) {
        List<ValidationError> errors = new ArrayList<>();
        
        try {
            // Get enriched boundary codes from campaign using cached function
            Set<String> validBoundaryCodes = boundaryUtil.getEnrichedBoundaryCodesFromCampaign(
                resource.getId(), resource.getReferenceId(), resource.getTenantId(), 
                resource.getHierarchyType(), requestInfo);
            
            log.info("Found {} valid boundary codes from campaign enriched boundaries", validBoundaryCodes.size());
            
            // Get lowest level boundaries from campaign
            Set<String> lowestLevelBoundaries = boundaryUtil.getLowestLevelBoundaryCodesFromCampaign(
                resource.getId(), resource.getReferenceId(), resource.getTenantId(), 
                resource.getHierarchyType(), requestInfo);
            
            log.info("Found {} lowest level boundary codes from campaign", lowestLevelBoundaries.size());
            
            // Extract boundary codes from target sheet
            Set<String> targetBoundaryCodes = new HashSet<>();
            for (Map<String, Object> rowData : sheetData) {
                String boundaryCode = ExcelUtil.getValueAsString(rowData.get("HCM_ADMIN_CONSOLE_BOUNDARY_CODE"));
                if (boundaryCode != null && !boundaryCode.trim().isEmpty()) {
                    targetBoundaryCodes.add(boundaryCode.trim());
                }
            }
            
            // Check if all lowest level boundaries are present in target sheet
            Set<String> missingBoundaries = new HashSet<>(lowestLevelBoundaries);
            missingBoundaries.removeAll(targetBoundaryCodes);
            
            if (!missingBoundaries.isEmpty()) {
                log.info("Found {} missing lowest level boundaries in target sheet", missingBoundaries.size());
                
                // Create error message with max 3 boundary names
                List<String> missingList = new ArrayList<>(missingBoundaries);
                String messagePrefix = localizationMap.getOrDefault(
                    "HCM_TARGET_LOWEST_LEVEL_BOUNDARIES_MISSING", 
                    "Lowest level boundaries missing in this sheet are: ");
                StringBuilder errorMessage = new StringBuilder(messagePrefix);
                
                int displayCount = Math.min(3, missingList.size());
                for (int i = 0; i < displayCount; i++) {
                    errorMessage.append(localizationMap.getOrDefault(missingList.get(i), missingList.get(i)));
                    if (i < displayCount - 1) {
                        errorMessage.append(", ");
                    }
                }
                
                if (missingList.size() > 3) {
                    errorMessage.append("...");
                }
                
                // Add error to first data row (after headers)
                Integer firstDataRowNumber = null;
                if (!sheetData.isEmpty()) {
                    firstDataRowNumber = (Integer) sheetData.get(0).get("__actualRowNumber__");
                }
                
                ValidationError error = ValidationError.builder()
                    .rowNumber(firstDataRowNumber != null ? firstDataRowNumber : 3)
                    .columnName("HCM_ADMIN_CONSOLE_BOUNDARY_CODE")
                    .status(ValidationConstants.STATUS_INVALID)
                    .errorDetails(errorMessage.toString())
                    .build();
                errors.add(error);
            }
            
            // Validate each row's boundary code
            for (Map<String, Object> rowData : sheetData) {
                String boundaryCode = ExcelUtil.getValueAsString(rowData.get("HCM_ADMIN_CONSOLE_BOUNDARY_CODE"));
                Integer rowNumber = (Integer) rowData.get("__actualRowNumber__");
                
                if (boundaryCode == null || boundaryCode.trim().isEmpty()) {
                    continue; // Skip empty boundary codes - schema validation will handle required field validation
                }
                
                if (!validBoundaryCodes.contains(boundaryCode.trim())) {
                    String errorMessage = localizationMap.getOrDefault(
                        "HCM_BOUNDARY_CODE_NOT_IN_CAMPAIGN_BOUNDARIES", 
                        "This boundary does not exist in the campaign's boundary.");
                    
                    ValidationError error = ValidationError.builder()
                        .rowNumber(rowNumber != null ? rowNumber : 0)
                        .columnName("HCM_ADMIN_CONSOLE_BOUNDARY_CODE")
                        .status(ValidationConstants.STATUS_INVALID)
                        .errorDetails(errorMessage)
                        .build();
                    errors.add(error);
                    
                    log.debug("Boundary code '{}' at row {} not found in campaign enriched boundaries", 
                             boundaryCode, rowNumber);
                }
            }
            
            if (!errors.isEmpty()) {
                log.info("Found {} total boundary validation errors in target sheet", errors.size());
            }
            
        } catch (Exception e) {
            log.error("Error validating campaign boundaries for target sheet: {}", e.getMessage(), e);
            // Don't add errors for technical failures - just log and continue
        }
        
        return errors;
    }

}