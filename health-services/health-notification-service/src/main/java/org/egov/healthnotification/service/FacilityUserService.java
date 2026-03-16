package org.egov.healthnotification.service;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.http.client.ServiceRequestClient;
import org.egov.common.models.stock.SenderReceiverType;
import org.egov.healthnotification.config.HealthNotificationProperties;
import org.egov.healthnotification.util.RequestInfoUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Resolves stock senderId/receiverId to user UUIDs and facility names.
 *
 * - STAFF type: the staffId IS the user UUID (staff records map to individual → user)
 * - WAREHOUSE type: resolve facility → assigned user UUIDs via facility service
 */
@Service
@Slf4j
public class FacilityUserService {

    private final ServiceRequestClient serviceRequestClient;
    private final HealthNotificationProperties properties;

    @Autowired
    public FacilityUserService(ServiceRequestClient serviceRequestClient,
                                HealthNotificationProperties properties) {
        this.serviceRequestClient = serviceRequestClient;
        this.properties = properties;
    }

    /**
     * Resolves a facility/staff ID to user UUID(s).
     *
     * @param entityId   The sender or receiver ID
     * @param entityType STAFF or WAREHOUSE
     * @param tenantId   The tenant ID
     * @return List of user UUIDs
     */
    public List<String> resolveUserUuids(String entityId, SenderReceiverType entityType, String tenantId) {
        if (entityId == null || entityId.isBlank()) {
            log.warn("Entity ID is null/blank. Cannot resolve user UUIDs.");
            return List.of();
        }

        if (entityType == SenderReceiverType.STAFF) {
            // Staff ID maps directly to user UUID
            log.debug("STAFF type: using entityId {} as user UUID", entityId);
            return List.of(entityId);
        } else {
            // WAREHOUSE: resolve facility to assigned users
            log.debug("WAREHOUSE type: resolving facility {} to user UUIDs", entityId);
            return resolveFacilityUsers(entityId, tenantId);
        }
    }

    /**
     * Resolves a facility name for placeholder substitution.
     *
     * @param entityId   The sender or receiver ID
     * @param entityType STAFF or WAREHOUSE
     * @param tenantId   The tenant ID
     * @return The facility/staff name
     */
    public String resolveFacilityName(String entityId, SenderReceiverType entityType, String tenantId) {
        if (entityId == null || entityId.isBlank()) {
            return "";
        }

        try {
            if (entityType == SenderReceiverType.WAREHOUSE) {
                return fetchFacilityName(entityId, tenantId);
            } else {
                // For STAFF, return the entityId as name (could be enhanced to fetch from HRMS)
                return entityId;
            }
        } catch (Exception e) {
            log.error("Failed to resolve facility name for entityId={}, entityType={}: {}",
                    entityId, entityType, e.getMessage());
            return entityId;
        }
    }

    /**
     * Fetches facility name from the facility service.
     */
    private String fetchFacilityName(String facilityId, String tenantId) {
        try {
            Map<String, Object> request = new HashMap<>();
            request.put("RequestInfo", RequestInfoUtil.buildSystemRequestInfo());

            Map<String, Object> facility = new HashMap<>();
            facility.put("id", List.of(facilityId));
            request.put("Facility", facility);

            StringBuilder uri = new StringBuilder(properties.getFacilityServiceHost())
                    .append(properties.getFacilitySearchUrl())
                    .append("?tenantId=").append(tenantId)
                    .append("&limit=1&offset=0");

            JsonNode response = serviceRequestClient.fetchResult(uri, request, JsonNode.class);

            if (response != null && response.has("Facilities") && response.get("Facilities").isArray()
                    && response.get("Facilities").size() > 0) {
                JsonNode facilityNode = response.get("Facilities").get(0);
                if (facilityNode.has("name")) {
                    return facilityNode.get("name").asText();
                }
            }

            log.warn("Facility name not found for facilityId={}, tenantId={}", facilityId, tenantId);
            return facilityId;

        } catch (Exception e) {
            log.error("Error fetching facility name for facilityId={}: {}", facilityId, e.getMessage());
            return facilityId;
        }
    }

    /**
     * Resolves facility ID to assigned user UUIDs.
     * Calls facility service to get users assigned to a warehouse.
     */
    private List<String> resolveFacilityUsers(String facilityId, String tenantId) {
        try {
            Map<String, Object> request = new HashMap<>();
            request.put("RequestInfo", RequestInfoUtil.buildSystemRequestInfo());

            Map<String, Object> facility = new HashMap<>();
            facility.put("id", List.of(facilityId));
            request.put("Facility", facility);

            StringBuilder uri = new StringBuilder(properties.getFacilityServiceHost())
                    .append(properties.getFacilitySearchUrl())
                    .append("?tenantId=").append(tenantId)
                    .append("&limit=1&offset=0");

            JsonNode response = serviceRequestClient.fetchResult(uri, request, JsonNode.class);

            List<String> userUuids = new ArrayList<>();
            if (response != null && response.has("Facilities") && response.get("Facilities").isArray()
                    && response.get("Facilities").size() > 0) {
                JsonNode facilityNode = response.get("Facilities").get(0);
                // Extract user UUIDs from facility's auditDetails or associated staff
                if (facilityNode.has("auditDetails") && facilityNode.get("auditDetails").has("createdBy")) {
                    userUuids.add(facilityNode.get("auditDetails").get("createdBy").asText());
                }
            }

            if (userUuids.isEmpty()) {
                log.warn("No user UUIDs found for facilityId={}, tenantId={}", facilityId, tenantId);
            }

            return userUuids;

        } catch (Exception e) {
            log.error("Error resolving facility users for facilityId={}: {}", facilityId, e.getMessage());
            return List.of();
        }
    }
}
