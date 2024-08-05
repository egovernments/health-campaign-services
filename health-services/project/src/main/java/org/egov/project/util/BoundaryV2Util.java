package org.egov.project.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.http.client.ServiceRequestClient;
import org.egov.common.models.core.Boundary;
import org.egov.project.web.models.boundary.BoundaryResponse;
import org.egov.tracer.model.CustomException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

/**
 * Utility class to validate boundary details.
 */
@Component
@Slf4j
public class BoundaryV2Util {

    // Injecting boundary host value from configuration
    @Value("${egov.boundary.host}")
    private String boundaryHost;

    // Injecting boundary search URL value from configuration
    @Value("${egov.boundary.search.url}")
    private String boundarySearchUrl;

    @Autowired
    private ServiceRequestClient serviceRequestClient;

    /**
     * Validates boundary details against the egov-location service response.
     *
     * @param boundaryTypeBoundariesMap A map of boundary types with lists of boundary codes
     * @param tenantId                 The tenant ID
     * @param requestInfo              Request information
     * @param hierarchyTypeCode        Hierarchy type code
     */
    public void validateBoundaryDetails(Map<String, List<String>> boundaryTypeBoundariesMap, String tenantId,
                                        RequestInfo requestInfo, String hierarchyTypeCode) {
        // Flatten the lists of boundary codes from the map values
        List<String> boundaries = boundaryTypeBoundariesMap.values().stream().flatMap(List::stream)
                .collect(Collectors.toList());
        if(CollectionUtils.isEmpty(boundaries)) return;
        try {
            // Fetch boundary details from the service
            log.debug("Fetching boundary details for tenantId: {}, boundaries: {}", tenantId, boundaries);
            BoundaryResponse boundarySearchResponse = serviceRequestClient.fetchResult(
                    new StringBuilder(boundaryHost
                            + boundarySearchUrl
                            + "?limit=" + boundaries.size()
                            + "&offset=0&tenantId=" + tenantId
                            + "&codes=" + String.join(",", boundaries)),
                    requestInfo,
                    BoundaryResponse.class
            );
            log.debug("Boundary details fetched successfully for tenantId: {}", tenantId);

            // Extract invalid boundary codes
            List<String> invalidBoundaryCodes = new ArrayList<>(boundaries);
            invalidBoundaryCodes.removeAll(boundarySearchResponse.getBoundary().stream()
                    .map(Boundary::getCode)
                    .collect(Collectors.toList())
            );

            // Throw exception if invalid boundary codes are found
            if (!invalidBoundaryCodes.isEmpty()) {
                log.error("The boundary data for the codes {} is not available.", invalidBoundaryCodes);
                throw new CustomException("INVALID_BOUNDARY_DATA", "The boundary data for the code "
                        + invalidBoundaryCodes + " is not available");
            }
        } catch (Exception e) {
            log.error("Exception while searching boundaries for tenantId: {}", tenantId, e);
            // Throw a custom exception if an error occurs during boundary search
            throw new CustomException("BOUNDARY_SERVICE_SEARCH_ERROR","Error in while fetching boundaries from Boundary Service : " + e.getMessage());
        }
    }
}
