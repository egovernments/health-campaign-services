package org.egov.facility.service;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.http.client.ServiceRequestClient;
import org.egov.facility.config.FacilityConfiguration;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The boundary codes at and below a boundary, for a facility search with includeDescendants=true. Read from
 * boundary-service (relationship search with includeChildren; the hierarchy type is not needed) and cached per
 * tenant and code. If boundary-service cannot be read, only the code itself is returned, which is how the search
 * behaves without includeDescendants.
 */
@Component
@Slf4j
public class BoundaryDescendantService {

    private static final long CACHE_MILLIS = 5 * 60 * 1000L;
    // After a failed read, try boundary-service again after this long instead of on every search
    private static final long RETRY_AFTER_FAILURE_MILLIS = 30 * 1000L;

    private final ServiceRequestClient serviceRequestClient;
    private final FacilityConfiguration configuration;
    private final Map<String, CachedCodes> cache = new ConcurrentHashMap<>();

    private record CachedCodes(List<String> codes, long expiresAt) {
    }

    public BoundaryDescendantService(ServiceRequestClient serviceRequestClient, FacilityConfiguration configuration) {
        this.serviceRequestClient = serviceRequestClient;
        this.configuration = configuration;
    }

    /** The boundary code itself followed by every code below it; never empty for a non-blank code. */
    public List<String> getCodesAtAndBelow(String tenantId, String boundaryCode, RequestInfo requestInfo) {
        String key = tenantId + "|" + boundaryCode;
        CachedCodes cached = cache.get(key);
        long now = System.currentTimeMillis();
        if (cached != null && now < cached.expiresAt()) {
            return cached.codes();
        }
        try {
            List<String> codes = fetch(tenantId, boundaryCode, requestInfo);
            cache.put(key, new CachedCodes(codes, now + CACHE_MILLIS));
            return codes;
        } catch (Exception e) {
            log.warn("Could not read the boundaries below {} for tenant {}; searching that boundary only: {}",
                    boundaryCode, tenantId, e.getMessage());
            List<String> onlyItself = List.of(boundaryCode);
            cache.put(key, new CachedCodes(onlyItself, now + RETRY_AFTER_FAILURE_MILLIS));
            return onlyItself;
        }
    }

    private List<String> fetch(String tenantId, String boundaryCode, RequestInfo requestInfo) {
        String url = configuration.getBoundaryServiceHost() + configuration.getBoundaryRelationshipSearchUrl()
                + "?tenantId=" + URLEncoder.encode(tenantId, StandardCharsets.UTF_8)
                + "&codes=" + URLEncoder.encode(boundaryCode, StandardCharsets.UTF_8)
                + "&includeChildren=true";
        Map<String, Object> request = new HashMap<>();
        request.put("RequestInfo", requestInfo != null ? requestInfo : RequestInfo.builder().build());
        Map<String, Object> response = serviceRequestClient.fetchResult(new StringBuilder(url), request, Map.class);
        Set<String> codes = new LinkedHashSet<>();
        codes.add(boundaryCode);
        Object tenantBoundaries = response == null ? null : response.get("TenantBoundary");
        if (tenantBoundaries instanceof List) {
            for (Object tenantBoundary : (List<?>) tenantBoundaries) {
                if (tenantBoundary instanceof Map) {
                    collectCodes(((Map<?, ?>) tenantBoundary).get("boundary"), codes);
                }
            }
        }
        return List.copyOf(new ArrayList<>(codes));
    }

    private static void collectCodes(Object boundaries, Set<String> codes) {
        if (!(boundaries instanceof List)) {
            return;
        }
        for (Object boundary : (List<?>) boundaries) {
            if (!(boundary instanceof Map)) {
                continue;
            }
            Object code = ((Map<?, ?>) boundary).get("code");
            if (code instanceof String && !((String) code).isBlank()) {
                codes.add(((String) code).trim());
            }
            collectCodes(((Map<?, ?>) boundary).get("children"), codes);
        }
    }
}
