package org.egov.excelingestion.service;

import lombok.extern.slf4j.Slf4j;
import org.egov.excelingestion.config.ExcelIngestionConfig;
import org.egov.excelingestion.repository.ServiceRequestRepository;
import org.egov.excelingestion.web.models.RequestInfo;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Service for facility API calls only - no transformations
 */
@Service
@Slf4j
public class FacilityService {

    private final ServiceRequestRepository serviceRequestRepository;
    private final ExcelIngestionConfig config;

    public FacilityService(ServiceRequestRepository serviceRequestRepository,
                          ExcelIngestionConfig config) {
        this.serviceRequestRepository = serviceRequestRepository;
        this.config = config;
    }

    /**
     * Fetch all permanent facilities from facility API 
     * @param tenantId Tenant ID
     * @param requestInfo Request context
     * @return List of raw permanent facilities from API
     */
    public List<Map<String, Object>> fetchAllPermanentFacilities(String tenantId, RequestInfo requestInfo) {
        log.info("Fetching all permanent facilities for tenant: {}", tenantId);
        
        try {
            List<Map<String, Object>> allFacilities = new ArrayList<>();
            int offset = 0;
            int limit = 50;
            boolean searchAgain = true;
            
            while (searchAgain) {
                // Create request body following project-factory pattern
                Map<String, Object> facilitySearchBody = new HashMap<>();
                facilitySearchBody.put("RequestInfo", requestInfo);
                
                Map<String, Object> facilityFilter = new HashMap<>();
                facilityFilter.put("isPermanent", true);  // Only permanent facilities
                facilitySearchBody.put("Facility", facilityFilter);
                
                // Make API call
                String facilitySearchUrl = config.getFacilitySearchUrl();
                StringBuilder facilitySearchUri = new StringBuilder(facilitySearchUrl);
                
                // Add query parameters to URL
                facilitySearchUri.append("?limit=").append(limit)
                        .append("&offset=").append(offset)
                        .append("&tenantId=").append(tenantId.split("\\.")[0]);
                
                Object response = serviceRequestRepository.fetchResult(facilitySearchUri, facilitySearchBody);
                
                if (response != null) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> responseMap = (Map<String, Object>) response;
                    
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> facilities = (List<Map<String, Object>>) responseMap.get("Facilities");
                    
                    if (facilities != null && !facilities.isEmpty()) {
                        allFacilities.addAll(facilities);
                        
                        searchAgain = facilities.size() >= limit;
                        offset += limit;
                        
                        log.debug("Fetched {} facilities in batch", facilities.size());
                    } else {
                        searchAgain = false;
                    }
                } else {
                    searchAgain = false;
                }
            }
            
            log.info("Successfully fetched {} permanent facilities for tenant: {}", allFacilities.size(), tenantId);
            return allFacilities;
            
        } catch (Exception e) {
            log.error("Error fetching permanent facilities for tenant {}: {}", tenantId, e.getMessage(), e);
            return new ArrayList<>(); // Return empty list on error
        }
    }
}