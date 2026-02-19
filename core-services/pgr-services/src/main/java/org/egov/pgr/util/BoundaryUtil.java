package org.egov.pgr.util;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.request.RequestInfo;
import org.egov.pgr.config.PGRConfiguration;
import org.egov.pgr.web.models.Service;
import org.egov.pgr.web.models.boundary.BoundarySearchResponse;
import org.egov.pgr.web.models.boundary.EnrichedBoundary;
import org.egov.pgr.web.models.boundary.HierarchyRelation;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class BoundaryUtil {

    private final RestTemplate restTemplate;
    private final PGRConfiguration config;

    public BoundaryUtil(RestTemplate restTemplate, PGRConfiguration config) {
        this.restTemplate = restTemplate;
        this.config = config;
    }

    /**
     * Fetches boundary relationship data from boundary service with parent hierarchy.
     *
     * @param requestInfo   request info from the request
     * @param boundaryCode  boundary code to search for
     * @param tenantId      tenant id
     * @param hierarchyType hierarchy type derived from the locality code
     * @return BoundarySearchResponse containing the hierarchy tree
     */
    public BoundarySearchResponse fetchBoundaryData(RequestInfo requestInfo, String boundaryCode, String tenantId, String hierarchyType) {
        Map<String, String> uriParameters = new HashMap<>();
        StringBuilder uri = new StringBuilder();
        uri.append(config.getBoundaryServiceHost())
                .append(config.getBoundaryRelationshipSearchEndPoint())
                .append("?codes={boundaryCode}&includeParents={includeParents}&includeChildren={includeChildren}&tenantId={tenantId}&hierarchyType={hierarchyType}");

        uriParameters.put("boundaryCode", boundaryCode);
        uriParameters.put("tenantId", tenantId);
        uriParameters.put("includeParents", "true");
        uriParameters.put("includeChildren", "false");
        uriParameters.put("hierarchyType", hierarchyType);

        BoundarySearchResponse boundarySearchResponse = new BoundarySearchResponse();

        try {
            log.debug("Fetching boundary data for code: {}, hierarchyType: {}", boundaryCode, hierarchyType);
            boundarySearchResponse = restTemplate.postForObject(uri.toString(), requestInfo, BoundarySearchResponse.class, uriParameters);
        } catch (Exception e) {
            log.error("Error while fetching boundary data for code: {}", boundaryCode, e);
        }

        return boundarySearchResponse;
    }

    /**
     * Enriches the locality code with the full parent hierarchy path.
     * Takes a partial code like "DISTRICT.BLOCK" and enriches it to "COUNTRY.PROVINCE.DISTRICT.BLOCK"
     * by fetching the parent hierarchy from the boundary service.
     * Also sets the full path in address.additionDetails as "boundaryCode".
     *
     * @param requestInfo request info from the request
     * @param service     the service object containing the address/locality
     */
    @SuppressWarnings("unchecked")
    public void enrichLocalityCode(RequestInfo requestInfo, Service service) {
        if (service.getAddress() == null || service.getAddress().getLocality() == null
                || service.getAddress().getLocality().getCode() == null) {
            return;
        }

        String currentCode = service.getAddress().getLocality().getCode();
        String tenantId = service.getTenantId();

        // Extract the first segment of the code (topmost level we have)
        String firstSegment = currentCode.split("\\.")[0];

        // Derive hierarchy type from the first segment (e.g. "CONSOLEHCM_NI" -> "CONSOLEHCM")
        String hierarchyType = firstSegment.split("_")[0];

        // Fetch boundary data with parents for the first segment
        BoundarySearchResponse response = fetchBoundaryData(requestInfo, firstSegment, tenantId, hierarchyType);

        if (response == null || CollectionUtils.isEmpty(response.getTenantBoundary())) {
            log.warn("No boundary data returned for code: {}", firstSegment);
            return;
        }

        HierarchyRelation hierarchyRelation = response.getTenantBoundary().get(0);
        if (CollectionUtils.isEmpty(hierarchyRelation.getBoundary())) {
            log.warn("No boundary hierarchy found for code: {}", firstSegment);
            return;
        }

        // Walk the tree to collect ancestor codes leading to our first segment
        List<String> ancestorCodes = new ArrayList<>();
        collectAncestorCodes(hierarchyRelation.getBoundary(), firstSegment, ancestorCodes);

        if (ancestorCodes.isEmpty()) {
            log.warn("Could not find code {} in boundary hierarchy tree", firstSegment);
            return;
        }

        // Remove the last element (it's the firstSegment itself, already in currentCode)
        ancestorCodes.remove(ancestorCodes.size() - 1);

        // Build the enriched code by prepending ancestors
        String enrichedCode;
        if (!ancestorCodes.isEmpty()) {
            String prefix = String.join(".", ancestorCodes);
            enrichedCode = prefix + "." + currentCode;
        } else {
            // No ancestors to prepend - code is already at root level
            enrichedCode = currentCode;
        }

        // Set enriched code on locality
        service.getAddress().getLocality().setCode(enrichedCode);

        // Set boundaryCode in address.additionDetails
        Object additionDetails = service.getAddress().getAdditionDetails();
        Map<String, Object> detailsMap;
        if (additionDetails instanceof Map) {
            detailsMap = (Map<String, Object>) additionDetails;
        } else {
            detailsMap = new LinkedHashMap<>();
        }
        detailsMap.put("boundaryCode", enrichedCode);
        service.getAddress().setAdditionDetails(detailsMap);

        log.info("Enriched locality code from '{}' to '{}'", currentCode, enrichedCode);
    }

    /**
     * Recursively walks the EnrichedBoundary tree to find the target code,
     * collecting all codes along the path from root to target.
     *
     * @param boundaries    list of boundary nodes at the current level
     * @param targetCode    the code to search for
     * @param pathCollector list collecting codes along the path (modified in place)
     * @return true if the target was found in this subtree
     */
    private boolean collectAncestorCodes(List<EnrichedBoundary> boundaries, String targetCode, List<String> pathCollector) {
        if (CollectionUtils.isEmpty(boundaries)) {
            return false;
        }

        for (EnrichedBoundary boundary : boundaries) {
            pathCollector.add(boundary.getCode());

            if (boundary.getCode().equals(targetCode)) {
                return true;
            }

            if (collectAncestorCodes(boundary.getChildren(), targetCode, pathCollector)) {
                return true;
            }

            // Backtrack - this path didn't lead to the target
            pathCollector.remove(pathCollector.size() - 1);
        }

        return false;
    }

}