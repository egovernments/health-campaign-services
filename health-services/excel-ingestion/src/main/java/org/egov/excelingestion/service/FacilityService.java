package org.egov.excelingestion.service;

import lombok.extern.slf4j.Slf4j;
import org.egov.excelingestion.config.ExcelIngestionConfig;
import org.egov.excelingestion.repository.ServiceRequestRepository;
import org.egov.common.contract.request.RequestInfo;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Service for facility API calls only - no transformations
 */
@Service
@Slf4j
public class FacilityService {

    private static final int ALL_FACILITIES_PAGE_SIZE = 50;

    private final ServiceRequestRepository serviceRequestRepository;
    private final ExcelIngestionConfig config;

    public FacilityService(ServiceRequestRepository serviceRequestRepository,
                          ExcelIngestionConfig config) {
        this.serviceRequestRepository = serviceRequestRepository;
        this.config = config;
    }

    /**
     * Fetch all permanent facilities of one usage (facility type, e.g. "CLF") from facility API.
     * @param tenantId Tenant ID
     * @param usage Facility usage to filter by; null for every usage
     * @param requestInfo Request context
     * @return List of raw permanent facilities from API
     */
    public List<Map<String, Object>> fetchAllPermanentFacilities(String tenantId, String usage, RequestInfo requestInfo) {
        log.info("Fetching all permanent facilities for tenant: {}", tenantId);

        try {
            List<Map<String, Object>> allFacilities = searchPermanentFacilities(tenantId, usage, null, null,
                    ALL_FACILITIES_PAGE_SIZE, requestInfo);
            log.info("Successfully fetched {} permanent facilities for tenant: {}", allFacilities.size(), tenantId);
            return allFacilities;

        } catch (Exception e) {
            log.error("Error fetching permanent facilities for tenant {}: {}", tenantId, e.getMessage(), e);
            return new ArrayList<>(); // Return empty list on error
        }
    }

    /**
     * Permanent facilities (of one usage, or every usage when null) on the root boundary and every boundary below it,
     * read with the facility search's includeDescendants instead of every permanent facility of the tenant. Without a
     * root boundary, or when that search fails, falls back to {@link #fetchAllPermanentFacilities(String, String, RequestInfo)};
     * callers still keep only the facilities on their own boundaries.
     */
    public List<Map<String, Object>> fetchPermanentFacilitiesAtAndBelow(String tenantId, String usage, String rootBoundaryCode,
                                                                        String hierarchyType, RequestInfo requestInfo) {
        if (rootBoundaryCode == null || rootBoundaryCode.isBlank()) {
            return fetchAllPermanentFacilities(tenantId, usage, requestInfo);
        }
        try {
            List<Map<String, Object>> facilities = searchPermanentFacilities(tenantId, usage, rootBoundaryCode, hierarchyType,
                    config.getFacilitySearchDescendantsPageSize(), requestInfo);
            log.info("Fetched {} permanent facilities at and below boundary {} for tenant: {}", facilities.size(), rootBoundaryCode, tenantId);
            return facilities;
        } catch (Exception e) {
            log.warn("Could not search facilities at and below boundary {} for tenant {}; reading every permanent facility instead: {}",
                    rootBoundaryCode, tenantId, e.getMessage());
            return fetchAllPermanentFacilities(tenantId, usage, requestInfo);
        }
    }

    private List<Map<String, Object>> searchPermanentFacilities(String tenantId, String usage, String rootBoundaryCode,
                                                                String hierarchyType, int limit, RequestInfo requestInfo) {
        List<Map<String, Object>> allFacilities = new ArrayList<>();
        int offset = 0;
        boolean searchAgain = true;

        while (searchAgain) {
            // Create request body following project-factory pattern
            Map<String, Object> facilitySearchBody = new HashMap<>();
            facilitySearchBody.put("RequestInfo", requestInfo);

            Map<String, Object> facilityFilter = new HashMap<>();
            facilityFilter.put("isPermanent", true);  // Only permanent facilities
            if (usage != null) {
                facilityFilter.put("usage", usage);
            }
            if (rootBoundaryCode != null) {
                facilityFilter.put("boundaryCode", rootBoundaryCode);
            }
            facilitySearchBody.put("Facility", facilityFilter);

            // Make API call
            String facilitySearchUrl = config.getFacilitySearchUrl();
            StringBuilder facilitySearchUri = new StringBuilder(facilitySearchUrl);

            // Add query parameters to URL
            // Values are encoded here and the URI is sent as it is, so a hierarchy type with &, # or + reaches the search whole
            facilitySearchUri.append("?limit=").append(limit)
                    .append("&offset=").append(offset)
                    .append("&tenantId=").append(URLEncoder.encode(tenantId.split("\\.")[0], StandardCharsets.UTF_8));
            if (rootBoundaryCode != null) {
                facilitySearchUri.append("&includeDescendants=true");
                if (hierarchyType != null && !hierarchyType.isBlank()) {
                    facilitySearchUri.append("&hierarchyType=").append(URLEncoder.encode(hierarchyType, StandardCharsets.UTF_8));
                }
            }

            Object response = serviceRequestRepository.fetchResultFromUri(URI.create(facilitySearchUri.toString()), facilitySearchBody);

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
        return allFacilities;
    }
}
