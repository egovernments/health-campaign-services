package org.egov.excelingestion.service;

import lombok.extern.slf4j.Slf4j;
import org.egov.excelingestion.config.ExcelIngestionConfig;
import org.egov.excelingestion.web.models.*;
import org.egov.common.http.client.ServiceRequestClient;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class BoundaryService {

    private final ServiceRequestClient serviceRequestClient;
    private final ExcelIngestionConfig config;

    public BoundaryService(ServiceRequestClient serviceRequestClient, ExcelIngestionConfig config) {
        this.serviceRequestClient = serviceRequestClient;
        this.config = config;
    }

    @Cacheable(value = "boundaryHierarchy", key = "#tenantId + '_' + #hierarchyType")
    public BoundaryHierarchyResponse fetchBoundaryHierarchy(String tenantId, String hierarchyType, RequestInfo requestInfo) {
        String hierarchyUrl = config.getHierarchySearchUrl();
        Map<String, Object> hierarchyPayload = createHierarchyPayload(requestInfo, tenantId, hierarchyType);
        
        log.info("Calling Boundary Hierarchy API: {} with tenantId: {}, hierarchyType: {}", hierarchyUrl, tenantId, hierarchyType);
        
        try {
            StringBuilder uri = new StringBuilder(hierarchyUrl);
            BoundaryHierarchyResponse result = serviceRequestClient.fetchResult(uri, hierarchyPayload, BoundaryHierarchyResponse.class);
            log.info("Successfully fetched boundary hierarchy data");
            return result;
        } catch (Exception e) {
            log.error("Error calling Boundary Hierarchy API: {}", e.getMessage(), e);
            throw new RuntimeException("Error calling Boundary Hierarchy API: " + hierarchyUrl, e);
        }
    }

    @Cacheable(value = "boundaryRelationship", key = "#tenantId + '_' + #hierarchyType")
    public BoundarySearchResponse fetchBoundaryRelationship(String tenantId, String hierarchyType, RequestInfo requestInfo) {
        StringBuilder url = new StringBuilder(config.getRelationshipSearchUrl());
        url.append("?includeChildren=true")
                .append("&tenantId=").append(URLEncoder.encode(tenantId, StandardCharsets.UTF_8))
                .append("&hierarchyType=").append(URLEncoder.encode(hierarchyType, StandardCharsets.UTF_8));

        Map<String, Object> relationshipPayload = createRelationshipPayload(requestInfo, tenantId, hierarchyType);
        
        log.info("Calling Boundary Relationship API: {} with tenantId: {}, hierarchyType: {}", url.toString(), tenantId, hierarchyType);
        
        try {
            BoundarySearchResponse result = serviceRequestClient.fetchResult(url, relationshipPayload, BoundarySearchResponse.class);
            log.info("Successfully fetched boundary relationship data");
            return result;
        } catch (Exception e) {
            log.error("Error calling Boundary Relationship API: {}", e.getMessage(), e);
            throw new RuntimeException("Error calling Boundary Relationship API: " + url.toString(), e);
        }
    }

    private Map<String, Object> createHierarchyPayload(RequestInfo requestInfo, String tenantId, String hierarchyType) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("RequestInfo", requestInfo);

        Map<String, Object> criteria = new HashMap<>();
        criteria.put("tenantId", tenantId);
        criteria.put("limit", 5);
        criteria.put("offset", 0);
        criteria.put("hierarchyType", hierarchyType);
        payload.put("BoundaryTypeHierarchySearchCriteria", criteria);
        return payload;
    }

    private Map<String, Object> createRelationshipPayload(RequestInfo requestInfo, String tenantId, String hierarchyType) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("RequestInfo", requestInfo);
        return payload;
    }
}