package org.egov.facility.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
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
 * boundary-service (relationship search with includeChildren) and cached per tenant, hierarchy and code. Without a
 * hierarchy type boundary-service only places boundaries whose type is in its default hierarchy order, so when it
 * returns nothing the boundary's hierarchy is looked up and sent. If boundary-service cannot be read, only the code
 * itself is returned, which is how the search behaves without includeDescendants.
 */
@Component
@Slf4j
public class BoundaryDescendantService {

    private static final long CACHE_MILLIS = 5 * 60 * 1000L;
    // After a failed read, try boundary-service again after this long instead of on every search
    private static final long RETRY_AFTER_FAILURE_MILLIS = 30 * 1000L;
    private static final long HIERARCHY_LIST_CACHE_MILLIS = 30 * 60 * 1000L;
    private static final int HIERARCHY_PAGE_SIZE = 100;
    private static final int MAX_HIERARCHY_PAGES = 50;

    private final ServiceRequestClient serviceRequestClient;
    private final FacilityConfiguration configuration;
    private final Map<String, CachedCodes> cache = new ConcurrentHashMap<>();
    private final Map<String, CachedCodes> hierarchyTypes = new ConcurrentHashMap<>();
    // Hierarchies already found per tenant, tried first: most searches in one environment use the same hierarchy
    private final Map<String, Set<String>> foundHierarchies = new ConcurrentHashMap<>();

    private record CachedCodes(List<String> codes, long expiresAt) {
    }

    private record Subtree(List<String> codes, boolean found) {
    }

    public BoundaryDescendantService(ServiceRequestClient serviceRequestClient, FacilityConfiguration configuration) {
        this.serviceRequestClient = serviceRequestClient;
        this.configuration = configuration;
    }

    /** The boundary code itself followed by every code below it; never empty for a non-blank code. */
    public List<String> getCodesAtAndBelow(String tenantId, String boundaryCode, RequestInfo requestInfo) {
        return getCodesAtAndBelow(tenantId, boundaryCode, null, requestInfo);
    }

    /** As above, in the given hierarchy; a blank hierarchy type is looked up when boundary-service needs one. */
    public List<String> getCodesAtAndBelow(String tenantId, String boundaryCode, String hierarchyType, RequestInfo requestInfo) {
        String hierarchy = StringUtils.isBlank(hierarchyType) ? null : hierarchyType.trim();
        String key = tenantId + "|" + (hierarchy == null ? "" : hierarchy) + "|" + boundaryCode;
        CachedCodes cached = cache.get(key);
        long now = System.currentTimeMillis();
        if (cached != null && now < cached.expiresAt()) {
            return cached.codes();
        }
        try {
            List<String> codes = hierarchy != null ? fetch(tenantId, boundaryCode, hierarchy, requestInfo).codes()
                    : fetchFindingHierarchy(tenantId, boundaryCode, requestInfo);
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

    private List<String> fetchFindingHierarchy(String tenantId, String boundaryCode, RequestInfo requestInfo) {
        Subtree withoutHierarchy = fetch(tenantId, boundaryCode, null, requestInfo);
        if (withoutHierarchy.found() || !Boolean.TRUE.equals(configuration.getFindHierarchyForDescendants())) {
            return withoutHierarchy.codes();
        }
        Set<String> found = foundHierarchies.computeIfAbsent(tenantId, t -> ConcurrentHashMap.newKeySet());
        Set<String> order = new LinkedHashSet<>(found);
        order.addAll(getHierarchyTypes(tenantId, requestInfo));
        for (String hierarchy : order) {
            Subtree inHierarchy;
            try {
                inHierarchy = fetch(tenantId, boundaryCode, hierarchy, requestInfo);
            } catch (Exception e) {   // one hierarchy that cannot be read must not stop the lookup
                log.debug("Could not read boundary {} in hierarchy {}: {}", boundaryCode, hierarchy, e.getMessage());
                continue;
            }
            if (inHierarchy.found()) {
                found.add(hierarchy);
                log.info("Boundary {} of tenant {} is in hierarchy {}; {} boundaries at and below it", boundaryCode, tenantId,
                        hierarchy, inHierarchy.codes().size());
                return inHierarchy.codes();
            }
        }
        log.warn("Boundary {} of tenant {} was not found in any of {} hierarchies; searching that boundary only",
                boundaryCode, tenantId, order.size());
        return withoutHierarchy.codes();
    }

    private Subtree fetch(String tenantId, String boundaryCode, String hierarchyType, RequestInfo requestInfo) {
        String url = configuration.getBoundaryServiceHost() + configuration.getBoundaryRelationshipSearchUrl()
                + "?tenantId=" + URLEncoder.encode(tenantId, StandardCharsets.UTF_8)
                + "&codes=" + URLEncoder.encode(boundaryCode, StandardCharsets.UTF_8)
                + "&includeChildren=true"
                // not encoded here: the client's RestTemplate encodes the URL, so an encoded name would be encoded twice
                + (hierarchyType == null ? "" : "&hierarchyType=" + hierarchyType);
        Map<String, Object> request = new HashMap<>();
        request.put("RequestInfo", requestInfo != null ? requestInfo : RequestInfo.builder().build());
        Map<String, Object> response = serviceRequestClient.fetchResult(new StringBuilder(url), request, Map.class);
        Set<String> codes = new LinkedHashSet<>();
        codes.add(boundaryCode);
        Set<String> answered = new LinkedHashSet<>();
        Object tenantBoundaries = response == null ? null : response.get("TenantBoundary");
        if (tenantBoundaries instanceof List) {
            for (Object tenantBoundary : (List<?>) tenantBoundaries) {
                if (tenantBoundary instanceof Map) {
                    collectCodes(((Map<?, ?>) tenantBoundary).get("boundary"), answered);
                }
            }
        }
        codes.addAll(answered);
        return new Subtree(List.copyOf(new ArrayList<>(codes)), answered.contains(boundaryCode));
    }

    /** Every hierarchy type of the tenant, read in pages from boundary-service and cached. */
    private List<String> getHierarchyTypes(String tenantId, RequestInfo requestInfo) {
        CachedCodes cached = hierarchyTypes.get(tenantId);
        long now = System.currentTimeMillis();
        if (cached != null && now < cached.expiresAt()) {
            return cached.codes();
        }
        String url = configuration.getBoundaryServiceHost() + configuration.getBoundaryHierarchySearchUrl();
        Set<String> types = new LinkedHashSet<>();
        for (int page = 0; page < MAX_HIERARCHY_PAGES; page++) {
            Map<String, Object> criteria = new HashMap<>();
            criteria.put("tenantId", tenantId);
            criteria.put("limit", HIERARCHY_PAGE_SIZE);
            criteria.put("offset", page * HIERARCHY_PAGE_SIZE);
            Map<String, Object> request = new HashMap<>();
            request.put("RequestInfo", requestInfo != null ? requestInfo : RequestInfo.builder().build());
            request.put("BoundaryTypeHierarchySearchCriteria", criteria);
            Map<String, Object> response = serviceRequestClient.fetchResult(new StringBuilder(url), request, Map.class);
            Object hierarchies = response == null ? null : response.get("BoundaryHierarchy");
            int count = 0;
            if (hierarchies instanceof List) {
                for (Object hierarchy : (List<?>) hierarchies) {
                    count++;
                    Object type = hierarchy instanceof Map ? ((Map<?, ?>) hierarchy).get("hierarchyType") : null;
                    if (type instanceof String && !((String) type).isBlank()) {
                        types.add(((String) type).trim());
                    }
                }
            }
            if (count < HIERARCHY_PAGE_SIZE) {
                break;
            }
        }
        List<String> list = List.copyOf(new ArrayList<>(types));
        hierarchyTypes.put(tenantId, new CachedCodes(list, now + HIERARCHY_LIST_CACHE_MILLIS));
        return list;
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
