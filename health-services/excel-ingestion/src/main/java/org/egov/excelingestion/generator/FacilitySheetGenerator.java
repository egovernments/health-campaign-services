package org.egov.excelingestion.generator;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.egov.excelingestion.config.ErrorConstants;
import org.egov.excelingestion.config.ProcessingConstants;
import org.egov.excelingestion.exception.CustomExceptionHandler;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.egov.excelingestion.service.BoundaryService;
import org.egov.excelingestion.service.CampaignService;
import org.egov.excelingestion.service.MDMSService;
import org.egov.excelingestion.util.BoundaryUtil;
import org.egov.excelingestion.util.HierarchicalBoundaryUtil;
import org.egov.excelingestion.util.SchemaColumnDefUtil;
import org.egov.excelingestion.util.ExcelDataPopulator;
import org.egov.excelingestion.web.models.*;
import org.egov.excelingestion.web.models.excel.ColumnDef;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Generator for facility sheet with existing campaign data support
 */
@Component
@Slf4j
public class FacilitySheetGenerator implements ISheetGenerator {

    private final MDMSService mdmsService;
    private final CampaignService campaignService;
    private final CustomExceptionHandler exceptionHandler;
    private final SchemaColumnDefUtil schemaColumnDefUtil;
    private final ExcelDataPopulator excelDataPopulator;
    private final HierarchicalBoundaryUtil hierarchicalBoundaryUtil;

    public FacilitySheetGenerator(MDMSService mdmsService, CampaignService campaignService,
                                 BoundaryService boundaryService, BoundaryUtil boundaryUtil,
                                 CustomExceptionHandler exceptionHandler,
                                 SchemaColumnDefUtil schemaColumnDefUtil,
                                 ExcelDataPopulator excelDataPopulator,
                                 HierarchicalBoundaryUtil hierarchicalBoundaryUtil) {
        this.mdmsService = mdmsService;
        this.campaignService = campaignService;
        this.exceptionHandler = exceptionHandler;
        this.schemaColumnDefUtil = schemaColumnDefUtil;
        this.excelDataPopulator = excelDataPopulator;
        this.hierarchicalBoundaryUtil = hierarchicalBoundaryUtil;
    }

    @Override
    public XSSFWorkbook generateSheet(XSSFWorkbook workbook, 
                                     String sheetName, 
                                     SheetGenerationConfig config,
                                     GenerateResource generateResource, 
                                     RequestInfo requestInfo,
                                     Map<String, String> localizationMap) {
        
        log.info("Generating facility sheet: {} for schema: {}", sheetName, config.getSchemaName());
        
        try {
            // Fetch schema from MDMS
            Map<String, Object> filters = new HashMap<>();
            filters.put("title", config.getSchemaName());
            
            List<Map<String, Object>> mdmsList = mdmsService.searchMDMS(
                    requestInfo, generateResource.getTenantId(), ProcessingConstants.MDMS_SCHEMA_CODE, filters, 1, 0);
            
            String schemaJson = extractSchemaFromMDMSResponse(mdmsList, config.getSchemaName());
            
            if (schemaJson != null && !schemaJson.isEmpty()) {
                List<ColumnDef> columns = schemaColumnDefUtil.convertSchemaToColumnDefs(schemaJson);
                
                // Generate data with existing campaign data if reference ID is provided
                List<Map<String, Object>> data = fetchExistingFacilityData(generateResource, requestInfo);
                
                // Create sheet with schema columns and data using ExcelDataPopulator
                workbook = (XSSFWorkbook) excelDataPopulator.populateSheetWithData(workbook, sheetName, columns, data, localizationMap);
                
                // Add boundary dropdowns using HierarchicalBoundaryUtil
                if (shouldAddBoundaryDropdowns(generateResource)) {
                    hierarchicalBoundaryUtil.addHierarchicalBoundaryColumnWithData(
                            workbook, sheetName, localizationMap, generateResource.getBoundaries(),
                            generateResource.getHierarchyType(), generateResource.getTenantId(), requestInfo, data);
                }
            }
            
        } catch (Exception e) {
            log.error("Error generating facility sheet {}: {}", sheetName, e.getMessage(), e);
            throw new RuntimeException("Failed to generate facility sheet: " + sheetName, e);
        }
        
        return workbook;
    }
    
    private boolean shouldAddBoundaryDropdowns(GenerateResource generateResource) {
        return generateResource.getBoundaries() != null && !generateResource.getBoundaries().isEmpty() 
               && generateResource.getHierarchyType() != null && !generateResource.getHierarchyType().isEmpty();
    }
    
    private String extractSchemaFromMDMSResponse(List<Map<String, Object>> mdmsList, String title) {
        try {
            if (!mdmsList.isEmpty()) {
                Map<String, Object> mdmsData = mdmsList.get(0);
                Map<String, Object> data = (Map<String, Object>) mdmsData.get("data");
                
                Map<String, Object> properties = (Map<String, Object>) data.get("properties");
                if (properties != null) {
                    ObjectMapper mapper = new ObjectMapper();
                    log.info("Successfully extracted MDMS schema for: {}", title);
                    return mapper.writeValueAsString(properties);
                }
            }
            log.warn("No MDMS data found for schema: {}", title);
        } catch (Exception e) {
            log.error("Error extracting MDMS schema {}: {}", title, e.getMessage(), e);
            exceptionHandler.throwCustomException(ErrorConstants.MDMS_SERVICE_ERROR,
                    ErrorConstants.MDMS_SERVICE_ERROR_MESSAGE, e);
        }
        
        exceptionHandler.throwCustomException(ErrorConstants.MDMS_DATA_NOT_FOUND,
                ErrorConstants.MDMS_DATA_NOT_FOUND_MESSAGE.replace("{0}", title),
                new RuntimeException("Schema '" + title + "' not found in MDMS configuration"));
        return null;
    }
    
    private List<Map<String, Object>> fetchExistingFacilityData(GenerateResource generateResource, 
                                                                RequestInfo requestInfo) {
        String referenceId = generateResource.getReferenceId();
        if (referenceId == null || referenceId.isEmpty()) {
            log.info("No reference ID provided for facility sheet");
            return null; // Headers-only sheet
        }
        
        try {
            // Get campaign number from reference ID
            String campaignNumber = getCampaignNumberFromReferenceId(referenceId, 
                    generateResource.getTenantId(), requestInfo);
            
            if (campaignNumber == null || campaignNumber.isEmpty()) {
                log.info("No campaign found for reference ID: {}", referenceId);
                return null; // Headers-only sheet
            }
            
            // Search for all facility data for this campaign
            List<Map<String, Object>> campaignDataResponse = campaignService.searchCampaignDataByUniqueIdentifiers(
                    new ArrayList<>(), "facility", null, campaignNumber, generateResource.getTenantId(), requestInfo);
            
            if (campaignDataResponse != null && !campaignDataResponse.isEmpty()) {
                // Extract the actual data object from each campaign data record
                List<Map<String, Object>> existingData = new ArrayList<>();
                for (Map<String, Object> record : campaignDataResponse) {
                    if (record.get("data") != null) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> dataObject = (Map<String, Object>) record.get("data");
                        if (dataObject != null) {
                            existingData.add(dataObject);
                        }
                    }
                }
                
                if (!existingData.isEmpty()) {
                    log.info("Found {} existing facility records for campaign: {}", 
                            existingData.size(), campaignNumber);
                    return existingData;
                }
            }
            
            log.info("No existing facility data found for campaign: {}", campaignNumber);
            return null; // Headers-only sheet
            
        } catch (Exception e) {
            log.error("Error fetching existing facility data: {}", e.getMessage());
            return null; // Headers-only sheet on error
        }
    }
    
    private String getCampaignNumberFromReferenceId(String referenceId, String tenantId, RequestInfo requestInfo) {
        try {
            log.info("Searching campaign by reference ID: {}", referenceId);
            CampaignSearchResponse.CampaignDetail campaign = campaignService.searchCampaignById(referenceId, tenantId, requestInfo);
            
            if (campaign != null) {
                String campaignNumber = campaign.getCampaignNumber();
                log.info("Found campaign number: {} for reference ID: {}", campaignNumber, referenceId);
                return campaignNumber;
            } else {
                log.warn("No campaign found for reference ID: {}", referenceId);
                return null;
            }
        } catch (Exception e) {
            log.error("Error fetching campaign for reference ID {}: {}", referenceId, e.getMessage());
            return null;
        }
    }
}