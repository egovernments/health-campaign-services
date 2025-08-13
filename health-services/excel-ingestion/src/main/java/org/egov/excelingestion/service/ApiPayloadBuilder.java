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

    /**
     * Creates a generic API payload with RequestInfo.
     *
     * @param requestInfo The RequestInfo object
     * @param criteriaKey The key for the criteria object
     * @param criteria The criteria map
     * @return The payload map
     */
    public Map<String, Object> createPayloadWithCriteria(RequestInfo requestInfo, String criteriaKey, Map<String, Object> criteria) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("RequestInfo", requestInfo);
        if (criteriaKey != null && criteria != null) {
            payload.put(criteriaKey, criteria);
        }
        return payload;
    }

    /**
     * Creates MDMS search API payload.
     *
     * @param requestInfo The RequestInfo object
     * @param tenantId The tenant ID
     * @param title The schema title
     * @param schemaCode The schema code
     * @return The payload map
     */
    public Map<String, Object> createMdmsPayload(RequestInfo requestInfo, String tenantId, String title, String schemaCode) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("RequestInfo", requestInfo);

        Map<String, Object> mdmsCriteria = new HashMap<>();
        mdmsCriteria.put("tenantId", tenantId);
        
        Map<String, Object> filters = new HashMap<>();
        filters.put("title", title);
        mdmsCriteria.put("filters", filters);
        
        mdmsCriteria.put("schemaCode", schemaCode);
        mdmsCriteria.put("limit", 1);
        mdmsCriteria.put("offset", 0);
        mdmsCriteria.put("sortBy", "uniqueIdentifier");
        mdmsCriteria.put("order", "ASC");
        
        payload.put("MdmsCriteria", mdmsCriteria);
        return payload;
    }
}