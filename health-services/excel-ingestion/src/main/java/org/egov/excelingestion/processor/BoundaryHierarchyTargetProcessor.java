package org.egov.excelingestion.processor;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.egov.excelingestion.config.ErrorConstants;
import org.egov.excelingestion.config.ProcessingConstants;
import org.egov.excelingestion.exception.CustomExceptionHandler;
import org.egov.excelingestion.service.CampaignService;
import org.egov.excelingestion.service.MDMSService;
import org.egov.excelingestion.service.SchemaValidationService;
import org.egov.excelingestion.service.ValidationService;
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

    public BoundaryHierarchyTargetProcessor(MDMSService mdmsService, 
                                          SchemaValidationService schemaValidationService,
                                          ValidationService validationService,
                                          EnrichmentUtil enrichmentUtil,
                                          CustomExceptionHandler exceptionHandler,
                                          ExcelUtil excelUtil,
                                          CampaignService campaignService) {
        this.mdmsService = mdmsService;
        this.schemaValidationService = schemaValidationService;
        this.validationService = validationService;
        this.enrichmentUtil = enrichmentUtil;
        this.exceptionHandler = exceptionHandler;
        this.excelUtil = excelUtil;
        this.campaignService = campaignService;
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
            
            List<ValidationError> validationErrors = schemaValidationService.validateDataWithPreFetchedSchema(
                    originalData, "HCM_CONSOLE_BOUNDARY_HIERARCHY", schemaProperties, localizationMap);

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

}