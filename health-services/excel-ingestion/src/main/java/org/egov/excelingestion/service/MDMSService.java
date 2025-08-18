package org.egov.excelingestion.service;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.http.client.ServiceRequestClient;
import org.egov.excelingestion.config.ErrorConstants;
import org.egov.excelingestion.config.ExcelIngestionConfig;
import org.egov.excelingestion.web.models.RequestInfo;
import org.egov.excelingestion.exception.CustomExceptionHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for MDMS search operations.
 */
@Service
@Slf4j
public class MDMSService {

    private final ServiceRequestClient serviceRequestClient;
    private final ExcelIngestionConfig config;
    
    @Autowired
    private CustomExceptionHandler exceptionHandler;

    @Autowired
    public MDMSService(ServiceRequestClient serviceRequestClient, ExcelIngestionConfig config) {
        this.serviceRequestClient = serviceRequestClient;
        this.config = config;
    }

    /**
     * Generic MDMS search method.
     *
     * @param requestInfo Request info object
     * @param tenantId Tenant ID
     * @param schemaCode Schema code (e.g., "HCM-ADMIN-CONSOLE.schemas")
     * @param filters Map of filters to apply
     * @param limit Limit for results (default: 1)
     * @param offset Offset for pagination (default: 0)
     * @return List of MDMS data maps or empty list if error/no data
     */
    public List<Map<String, Object>> searchMDMS(RequestInfo requestInfo, 
                                               String tenantId,
                                               String schemaCode,
                                               Map<String, Object> filters,
                                               Integer limit,
                                               Integer offset) {
        
        String url = config.getMdmsSearchUrl();
        
        try {
            // Build MDMS criteria
            Map<String, Object> mdmsCriteria = new HashMap<>();
            mdmsCriteria.put("tenantId", tenantId);
            mdmsCriteria.put("schemaCode", schemaCode);
            
            if (filters != null && !filters.isEmpty()) {
                mdmsCriteria.put("filters", filters);
            }
            
            mdmsCriteria.put("limit", limit != null ? limit : 1);
            mdmsCriteria.put("offset", offset != null ? offset : 0);
            
            // Build request payload
            Map<String, Object> payload = new HashMap<>();
            payload.put("RequestInfo", requestInfo);
            payload.put("MdmsCriteria", mdmsCriteria);
            
            log.info("Calling MDMS API: {} with schemaCode: {}, tenantId: {}, filters: {}", 
                    url, schemaCode, tenantId, filters);
            
            // Make API call
            StringBuilder uri = new StringBuilder(url);
            Map<String, Object> responseBody = serviceRequestClient.fetchResult(uri, payload, Map.class);
            
            if (responseBody != null && responseBody.get("mdms") != null) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> mdmsList = (List<Map<String, Object>>) responseBody.get("mdms");
                log.info("Successfully fetched {} MDMS records", mdmsList.size());
                return mdmsList;
            }
            
            log.warn("No MDMS data found for schemaCode: {}, filters: {}", schemaCode, filters);
        } catch (Exception e) {
            log.error("Error calling MDMS API: {}", e.getMessage(), e);
            exceptionHandler.throwCustomException(ErrorConstants.MDMS_SERVICE_ERROR, 
                    ErrorConstants.MDMS_SERVICE_ERROR_MESSAGE, e);
        }
        
        return new ArrayList<>();
    }

    /**
     * Generic MDMS search method with MdmsCriteria
     * 
     * @param requestInfo Request info object
     * @param mdmsCriteria MDMS search criteria
     * @return List of MDMS data maps
     */
    public List<Map<String, Object>> searchMDMSData(RequestInfo requestInfo, Map<String, Object> mdmsCriteria) {
        String url = config.getMdmsSearchUrl();
        
        try {
            // Build request payload
            Map<String, Object> payload = new HashMap<>();
            payload.put("RequestInfo", requestInfo);
            payload.put("MdmsCriteria", mdmsCriteria);
            
            log.info("Calling MDMS API with criteria: {}", mdmsCriteria);
            
            // Make API call
            StringBuilder uri = new StringBuilder(url);
            Map<String, Object> responseBody = serviceRequestClient.fetchResult(uri, payload, Map.class);
            
            if (responseBody != null && responseBody.get("mdms") != null) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> mdmsList = (List<Map<String, Object>>) responseBody.get("mdms");
                log.info("Successfully fetched {} MDMS records", mdmsList.size());
                return mdmsList;
            }
            
            log.warn("No MDMS data found");
        } catch (Exception e) {
            log.error("Error calling MDMS API: {}", e.getMessage(), e);
            exceptionHandler.throwCustomException(ErrorConstants.MDMS_SERVICE_ERROR, 
                    ErrorConstants.MDMS_SERVICE_ERROR_MESSAGE, e);
        }
        
        return new ArrayList<>();
    }
}