package org.egov.facility.service;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.http.client.ServiceRequestClient;
import org.egov.facility.config.FacilityConfiguration;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Site types of the outside household feature, read from the MDMS master HCM.OUTSIDE_HOUSEHOLD_TYPES
 * (active rows, state tenant). A facility whose usage is one of these codes is a site, not a normal
 * facility. The list is cached per state tenant; if MDMS cannot be read, the last list read is used
 * (an empty list until one has been read, which leaves sites visible rather than failing the search).
 */
@Component
@Slf4j
public class OutsideHouseholdTypeService {

    private static final String SCHEMA_CODE = "HCM.OUTSIDE_HOUSEHOLD_TYPES";
    private static final long CACHE_MILLIS = 5 * 60 * 1000L;
    // After a failed read, try MDMS again after this long instead of on every search
    private static final long RETRY_AFTER_FAILURE_MILLIS = 30 * 1000L;

    private final ServiceRequestClient serviceRequestClient;
    private final FacilityConfiguration configuration;
    private final Map<String, CachedTypes> cache = new ConcurrentHashMap<>();

    // codes in master order; categoriesByType maps each upper-case code to its upper-case "categories" (empty = any)
    private record CachedTypes(List<String> codes, Map<String, List<String>> categoriesByType, long expiresAt) {
    }

    public OutsideHouseholdTypeService(ServiceRequestClient serviceRequestClient, FacilityConfiguration configuration) {
        this.serviceRequestClient = serviceRequestClient;
        this.configuration = configuration;
    }

    /** Active site type codes for the tenant's state tenant; never null. */
    public List<String> getTypes(String tenantId) {
        return read(tenantId).codes();
    }

    /** Upper-case category codes allowed per upper-case active site type code; an empty list means any category. */
    public Map<String, List<String>> getCategoriesByType(String tenantId) {
        return read(tenantId).categoriesByType();
    }

    private CachedTypes read(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            return new CachedTypes(List.of(), Map.of(), 0);
        }
        String stateTenantId = tenantId.split("\\.")[0];
        CachedTypes cached = cache.get(stateTenantId);
        long now = System.currentTimeMillis();
        if (cached != null && now < cached.expiresAt()) {
            return cached;
        }
        try {
            CachedTypes fresh = fetch(stateTenantId, now + CACHE_MILLIS);
            cache.put(stateTenantId, fresh);
            return fresh;
        } catch (Exception e) {
            log.error("Could not read {} for tenant {}; using the last list read", SCHEMA_CODE, stateTenantId, e);
            CachedTypes lastKnown = new CachedTypes(cached != null ? cached.codes() : List.of(),
                    cached != null ? cached.categoriesByType() : Map.of(), now + RETRY_AFTER_FAILURE_MILLIS);
            cache.put(stateTenantId, lastKnown);
            return lastKnown;
        }
    }

    @SuppressWarnings("unchecked")
    private CachedTypes fetch(String stateTenantId, long expiresAt) {
        Map<String, Object> request = Map.of(
                "RequestInfo", RequestInfo.builder().build(),
                "MdmsCriteria", Map.of("tenantId", stateTenantId, "schemaCode", SCHEMA_CODE, "limit", 100));
        String host = configuration.getMdmsHost();
        String path = configuration.getMdmsV2SearchPath();
        String url = (host.endsWith("/") ? host : host + "/") + (path.startsWith("/") ? path.substring(1) : path);
        Map<String, Object> response = serviceRequestClient.fetchResult(new StringBuilder(url), request, Map.class);
        List<String> codes = new ArrayList<>();
        Map<String, List<String>> categoriesByType = new LinkedHashMap<>();
        Object rows = response == null ? null : response.get("mdms");
        if (rows instanceof List) {
            for (Object row : (List<Object>) rows) {
                Object data = row instanceof Map ? ((Map<String, Object>) row).get("data") : null;
                if (!(data instanceof Map) || Boolean.FALSE.equals(((Map<String, Object>) row).get("isActive"))) {
                    continue;
                }
                Object code = ((Map<String, Object>) data).get("code");
                if (Boolean.TRUE.equals(((Map<String, Object>) data).get("active")) && code instanceof String
                        && !((String) code).isBlank()) {
                    codes.add(((String) code).trim());
                    List<String> categories = new ArrayList<>();
                    Object listed = ((Map<String, Object>) data).get("categories");
                    if (listed instanceof List) {
                        for (Object category : (List<Object>) listed) {
                            if (category instanceof String && !((String) category).isBlank()) {
                                categories.add(((String) category).trim().toUpperCase(Locale.ROOT));
                            }
                        }
                    }
                    categoriesByType.put(((String) code).trim().toUpperCase(Locale.ROOT), List.copyOf(categories));
                }
            }
        }
        return new CachedTypes(List.copyOf(codes), Map.copyOf(categoriesByType), expiresAt);
    }
}
