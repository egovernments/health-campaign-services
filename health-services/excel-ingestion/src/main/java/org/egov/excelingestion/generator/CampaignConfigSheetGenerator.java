package org.egov.excelingestion.generator;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.egov.excelingestion.config.ErrorConstants;
import org.egov.excelingestion.exception.CustomExceptionHandler;
import org.egov.excelingestion.service.MDMSService;
import org.egov.excelingestion.util.CampaignConfigSheetCreator;
import org.egov.excelingestion.web.models.GenerateResource;
import org.egov.excelingestion.web.models.RequestInfo;
import org.egov.excelingestion.web.models.SheetGenerationConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Generator for campaign configuration sheet - uses direct workbook approach
 */
@Component
@Slf4j
public class CampaignConfigSheetGenerator implements ISheetGenerator {

    private final MDMSService mdmsService;
    private final CampaignConfigSheetCreator campaignConfigSheetCreator;
    private final CustomExceptionHandler exceptionHandler;

    public CampaignConfigSheetGenerator(MDMSService mdmsService,
                                      CampaignConfigSheetCreator campaignConfigSheetCreator,
                                      CustomExceptionHandler exceptionHandler) {
        this.mdmsService = mdmsService;
        this.campaignConfigSheetCreator = campaignConfigSheetCreator;
        this.exceptionHandler = exceptionHandler;
    }

    @Override
    public XSSFWorkbook generateSheet(XSSFWorkbook workbook, String sheetName, SheetGenerationConfig config,
                                    GenerateResource generateResource, RequestInfo requestInfo,
                                    Map<String, String> localizationMap) {
        
        log.info("Generating campaign configuration sheet: {}", sheetName);
        
        try {
            // Fetch campaign config data from MDMS
            Map<String, Object> configFilters = new HashMap<>();
            configFilters.put("sheetName", config.getSheetNameKey());
            
            List<Map<String, Object>> configMdmsList = mdmsService.searchMDMS(
                    requestInfo, generateResource.getTenantId(), "HCM-ADMIN-CONSOLE.configsheet", configFilters, 1, 0);
            
            String campaignConfigData = extractCampaignConfigFromMDMSResponse(configMdmsList, config.getSheetNameKey());
            
            if (campaignConfigData != null && !campaignConfigData.isEmpty()) {
                ObjectMapper mapper = new ObjectMapper();
                Map<String, Object> configData = mapper.readValue(campaignConfigData, Map.class);
                
                workbook = (XSSFWorkbook) campaignConfigSheetCreator.createCampaignConfigSheet(
                        workbook, sheetName, configData, localizationMap,
                        generateResource.getTenantId(), generateResource.getHierarchyType(), requestInfo);
                
                log.info("Campaign configuration sheet created successfully: {}", sheetName);
            }
            
        } catch (Exception e) {
            log.error("Error creating campaign configuration sheet {}: {}", sheetName, e.getMessage(), e);
            
            // If it's already a CustomException with specific error code, preserve it
            if (e instanceof org.egov.tracer.model.CustomException) {
                throw (org.egov.tracer.model.CustomException) e;
            }
            
            // Otherwise, treat as campaign config creation error
            exceptionHandler.throwCustomException(ErrorConstants.CAMPAIGN_CONFIG_CREATION_ERROR,
                    ErrorConstants.CAMPAIGN_CONFIG_CREATION_ERROR_MESSAGE, e);
        }
        
        return workbook;
    }
    
    private String extractCampaignConfigFromMDMSResponse(List<Map<String, Object>> mdmsList, String sheetName) {
        try {
            if (!mdmsList.isEmpty()) {
                Map<String, Object> mdmsData = mdmsList.get(0);
                Map<String, Object> data = (Map<String, Object>) mdmsData.get("data");
                
                if (data != null) {
                    ObjectMapper mapper = new ObjectMapper();
                    log.info("Successfully extracted MDMS campaign config for: {}", sheetName);
                    return mapper.writeValueAsString(data);
                }
            }
            log.warn("No MDMS data found for campaign config: {}", sheetName);
        } catch (Exception e) {
            log.error("Error extracting MDMS campaign config {}: {}", sheetName, e.getMessage(), e);
            exceptionHandler.throwCustomException(ErrorConstants.MDMS_SERVICE_ERROR,
                    ErrorConstants.MDMS_SERVICE_ERROR_MESSAGE, e);
        }
        
        exceptionHandler.throwCustomException(ErrorConstants.MDMS_DATA_NOT_FOUND,
                ErrorConstants.MDMS_DATA_NOT_FOUND_MESSAGE.replace("{0}", sheetName),
                new RuntimeException("Campaign config sheet '" + sheetName + "' not found in MDMS configuration"));
        return null;
    }
}