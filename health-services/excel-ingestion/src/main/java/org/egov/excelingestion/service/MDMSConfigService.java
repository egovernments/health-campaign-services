package org.egov.excelingestion.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.egov.common.contract.request.RequestInfo;
import org.egov.excelingestion.config.ProcessingConstants;
import org.egov.excelingestion.web.models.mdms.ExcelIngestionProcessData;
import org.egov.excelingestion.web.models.mdms.ExcelIngestionGenerateData;
import org.egov.excelingestion.web.models.mdms.ReadMeConfigData;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for caching MDMS configuration data with 5-minute TTL
 */
@Service
@Slf4j
public class MDMSConfigService {

    private final MDMSService mdmsService;
    private final ObjectMapper objectMapper;

    public MDMSConfigService(MDMSService mdmsService, ObjectMapper objectMapper) {
        this.mdmsService = mdmsService;
        this.objectMapper = objectMapper;
    }

    /**
     * Get excel ingestion process configuration from MDMS with caching
     * 
     * @param requestInfo Request info
     * @param tenantId Tenant ID
     * @param uniqueIdentifier Unique identifier for the config (e.g., "unified-console-parse")
     * @return MDMS config data or null if not found
     */
    @Cacheable(value = "mdmsExcelIngestionProcess", key = "#tenantId + '_' + #uniqueIdentifier", 
               cacheManager = "cacheManager")
    public ExcelIngestionProcessData getExcelIngestionProcessConfig(RequestInfo requestInfo, String tenantId, 
                                                                  String uniqueIdentifier) {
        log.info("Fetching excel ingestion process config from MDMS for: {}", uniqueIdentifier);
        
        Map<String, Object> filters = new HashMap<>();
        filters.put("excelIngestionProcessName", uniqueIdentifier);
        
        List<Map<String, Object>> results = mdmsService.searchMDMS(
            requestInfo,
            tenantId,
            "HCM-ADMIN-CONSOLE.excelIngestionProcess",
            filters,
            1,
            0
        );
        
        if (!results.isEmpty()) {
            try {
                Map<String, Object> result = results.get(0);
                Map<String, Object> data = (Map<String, Object>) result.get("data");
                if (data != null) {
                    ExcelIngestionProcessData processData = objectMapper.convertValue(data, ExcelIngestionProcessData.class);
                    log.info("Found excel ingestion process config for: {}", uniqueIdentifier);
                    return processData;
                }
            } catch (Exception e) {
                log.error("Error converting MDMS data to ExcelIngestionProcessData for: {}", uniqueIdentifier, e);
            }
        }
        
        log.warn("No excel ingestion process config found for: {}", uniqueIdentifier);
        return null;
    }

    /**
     * Get excel ingestion generate configuration from MDMS with caching
     * 
     * @param requestInfo Request info
     * @param tenantId Tenant ID
     * @param uniqueIdentifier Unique identifier for the config (e.g., "unified-console")
     * @return MDMS config data or null if not found
     */
    @Cacheable(value = "mdmsExcelIngestionGenerate", key = "#tenantId + '_' + #uniqueIdentifier", 
               cacheManager = "cacheManager")
    public ExcelIngestionGenerateData getExcelIngestionGenerateConfig(RequestInfo requestInfo, String tenantId, 
                                                                    String uniqueIdentifier) {
        log.info("Fetching excel ingestion generate config from MDMS for: {}", uniqueIdentifier);
        
        Map<String, Object> filters = new HashMap<>();
        filters.put("excelIngestionGenerateName", uniqueIdentifier);
        
        List<Map<String, Object>> results = mdmsService.searchMDMS(
            requestInfo,
            tenantId,
            "HCM-ADMIN-CONSOLE.excelIngestionGenerate",
            filters,
            1,
            0
        );
        
        if (!results.isEmpty()) {
            try {
                Map<String, Object> result = results.get(0);
                Map<String, Object> data = (Map<String, Object>) result.get("data");
                if (data != null) {
                    ExcelIngestionGenerateData generateData = objectMapper.convertValue(data, ExcelIngestionGenerateData.class);
                    log.info("Found excel ingestion generate config for: {}", uniqueIdentifier);
                    return generateData;
                }
            } catch (Exception e) {
                log.error("Error converting MDMS data to ExcelIngestionGenerateData for: {}", uniqueIdentifier, e);
            }
        }
        
        log.warn("No excel ingestion generate config found for: {}", uniqueIdentifier);
        return null;
    }

    /**
     * Get the ReadMe (instructions) config for a single resource type from MDMS, cached per
     * (tenant, type). Returns null when the type has no ReadMe config authored — the README sheet
     * simply skips that block rather than failing the whole generation.
     *
     * @param type resource type, e.g. "user", "facility", "boundary"
     */
    @Cacheable(value = "mdmsReadMeConfig", key = "#tenantId + '_' + #type", cacheManager = "cacheManager",
               unless = "#result == null")
    public ReadMeConfigData getReadMeConfig(RequestInfo requestInfo, String tenantId, String type) {
        log.info("Fetching ReadMe config from MDMS for type: {}", type);

        Map<String, Object> filters = new HashMap<>();
        filters.put(ProcessingConstants.MDMS_README_CONFIG_TYPE_FILTER, type);

        List<Map<String, Object>> results = mdmsService.searchMDMS(
            requestInfo,
            tenantId,
            ProcessingConstants.MDMS_README_CONFIG_SCHEMA,
            filters,
            1,
            0
        );

        if (!results.isEmpty()) {
            try {
                Map<String, Object> data = (Map<String, Object>) results.get(0).get("data");
                if (data != null) {
                    ReadMeConfigData readMeConfig = objectMapper.convertValue(data, ReadMeConfigData.class);
                    log.info("Found ReadMe config for type: {}", type);
                    return readMeConfig;
                }
            } catch (Exception e) {
                log.error("Error converting MDMS data to ReadMeConfigData for type: {}", type, e);
            }
        }

        log.warn("No ReadMe config found for type: {}", type);
        return null;
    }
}