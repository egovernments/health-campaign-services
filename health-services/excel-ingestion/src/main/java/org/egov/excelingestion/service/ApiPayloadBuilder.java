package org.egov.excelingestion.service;

import org.egov.excelingestion.web.models.RequestInfo;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Service to centralize API payload creation logic.
 * This eliminates duplication across multiple processors and services.
 */
@Service
public class ApiPayloadBuilder {

    /**
     * Creates hierarchy search API payload.
     *
     * @param requestInfo The RequestInfo object
     * @param tenantId The tenant ID
     * @param hierarchyType The hierarchy type
     * @return The payload map
     */
    public Map<String, Object> createHierarchyPayload(RequestInfo requestInfo, String tenantId, String hierarchyType) {
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

    /**
     * Creates relationship search API payload.
     *
     * @param requestInfo The RequestInfo object
     * @return The payload map
     */
    public Map<String, Object> createRelationshipPayload(RequestInfo requestInfo) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("RequestInfo", requestInfo);
        return payload;
    }

}