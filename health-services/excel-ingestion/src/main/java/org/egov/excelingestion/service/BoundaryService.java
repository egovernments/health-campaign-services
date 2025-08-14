package org.egov.excelingestion.service;

import lombok.extern.slf4j.Slf4j;
import org.egov.excelingestion.config.ErrorConstants;
import org.egov.excelingestion.config.ExcelIngestionConfig;
import org.egov.excelingestion.web.models.*;
import org.egov.common.http.client.ServiceRequestClient;
import org.egov.excelingestion.exception.CustomExceptionHandler;
import org.springframework.beans.factory.annotation.Autowired;
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
    private final ApiPayloadBuilder apiPayloadBuilder;
    
    @Autowired
    private CustomExceptionHandler exceptionHandler;

    public BoundaryService(ServiceRequestClient serviceRequestClient, ExcelIngestionConfig config,
            ApiPayloadBuilder apiPayloadBuilder) {
        this.serviceRequestClient = serviceRequestClient;
        this.config = config;
        this.apiPayloadBuilder = apiPayloadBuilder;
    }

    @Cacheable(value = "boundaryHierarchy", key = "#tenantId + '_' + #hierarchyType")
    public BoundaryHierarchyResponse fetchBoundaryHierarchy(String tenantId, String hierarchyType, RequestInfo requestInfo) {
        String hierarchyUrl = config.getHierarchySearchUrl();
        Map<String, Object> hierarchyPayload = apiPayloadBuilder.createHierarchyPayload(requestInfo, tenantId, hierarchyType);
        
        log.info("Calling Boundary Hierarchy API: {} with tenantId: {}, hierarchyType: {}", hierarchyUrl, tenantId, hierarchyType);
        
        try {
            StringBuilder uri = new StringBuilder(hierarchyUrl);
            BoundaryHierarchyResponse result = serviceRequestClient.fetchResult(uri, hierarchyPayload, BoundaryHierarchyResponse.class);
            log.info("Successfully fetched boundary hierarchy data");
            return result;
        } catch (Exception e) {
            log.error("Error calling Boundary Hierarchy API: {}", e.getMessage(), e);
            exceptionHandler.throwCustomException(ErrorConstants.BOUNDARY_SERVICE_ERROR, 
                    ErrorConstants.BOUNDARY_SERVICE_ERROR_MESSAGE, e);
            return null; // This will never be reached due to exception throwing above
        }
    }

    @Cacheable(value = "boundaryRelationship", key = "#tenantId + '_' + #hierarchyType")
    public BoundarySearchResponse fetchBoundaryRelationship(String tenantId, String hierarchyType, RequestInfo requestInfo) {
        StringBuilder url = new StringBuilder(config.getRelationshipSearchUrl());
        url.append("?includeChildren=true")
                .append("&tenantId=").append(URLEncoder.encode(tenantId, StandardCharsets.UTF_8))
                .append("&hierarchyType=").append(URLEncoder.encode(hierarchyType, StandardCharsets.UTF_8));

        Map<String, Object> relationshipPayload = apiPayloadBuilder.createRelationshipPayload(requestInfo);
        
        log.info("Calling Boundary Relationship API: {} with tenantId: {}, hierarchyType: {}", url.toString(), tenantId, hierarchyType);
        
        try {
            BoundarySearchResponse result = serviceRequestClient.fetchResult(url, relationshipPayload, BoundarySearchResponse.class);
            log.info("Successfully fetched boundary relationship data");
            return result;
        } catch (Exception e) {
            log.error("Error calling Boundary Relationship API: {}", e.getMessage(), e);
            exceptionHandler.throwCustomException(ErrorConstants.BOUNDARY_SERVICE_ERROR, 
                    ErrorConstants.BOUNDARY_SERVICE_ERROR_MESSAGE, e);
            return null; // This will never be reached due to exception throwing above
        }
    }
}