package org.egov.excelingestion.service;

import org.egov.common.contract.request.RequestInfo;
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
     * Creates campaign search API payload.
     */
    public Map<String, Object> createCampaignSearchPayload(RequestInfo requestInfo,
                                                           String tenantId,
                                                           String[] ids,
                                                           Boolean isActive,
                                                           Integer limit,
                                                           Integer offset) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("RequestInfo", requestInfo);

        return createCampaignSearchPayload(requestInfo, tenantId, ids, null, isActive, limit, offset);
    }

    /**
     * Campaign search payload that can also select by campaignNumber.
     *
     * <p>Prefer a campaignNumber-keyed search when resolving a parent campaign: project-factory only
     * returns the boundaries array when the caller searches a single id, searches by campaignNumber, or
     * explicitly opts in. A filter-shaped search silently comes back with no boundaries, which is far
     * worse than an error because it is indistinguishable from a campaign that genuinely has none.
     */
    public Map<String, Object> createCampaignSearchPayload(RequestInfo requestInfo,
                                                           String tenantId,
                                                           String[] ids,
                                                           String campaignNumber,
                                                           Boolean isActive,
                                                           Integer limit,
                                                           Integer offset) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("RequestInfo", requestInfo);

        Map<String, Object> searchCriteria = new HashMap<>();
        searchCriteria.put("tenantId", tenantId);
        if (ids != null) searchCriteria.put("ids", ids);
        if (campaignNumber != null) searchCriteria.put("campaignNumber", campaignNumber);
        if (isActive != null) searchCriteria.put("isActive", isActive);

        Map<String, Object> pagination = new HashMap<>();
        if (limit != null) pagination.put("limit", limit);
        if (offset != null) pagination.put("offset", offset);
        if (!pagination.isEmpty()) searchCriteria.put("pagination", pagination);

        payload.put("CampaignDetails", searchCriteria);
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