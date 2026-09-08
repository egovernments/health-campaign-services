package digit.util;

import digit.config.Configuration;
import digit.web.models.RequestInfoWrapper;
import digit.web.models.boundary.BoundarySearchResponse;
import digit.web.models.boundary.BoundaryTypeHierarchyResponse;
import digit.web.models.boundary.BoundaryTypeHierarchySearchCriteria;
import digit.web.models.boundary.BoundaryTypeHierarchySearchRequest;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.request.RequestInfo;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

import static digit.config.ServiceConstants.ERROR_WHILE_FETCHING_BOUNDARY_DETAILS;
import static digit.config.ServiceConstants.ERROR_WHILE_FETCHING_BOUNDARY_HIERARCHY_DETAILS;

@Slf4j
@Component
public class BoundaryUtil {

    private RestTemplate restTemplate;

    private Configuration configs;

    public BoundaryUtil(RestTemplate restTemplate, Configuration configs) {
        this.restTemplate = restTemplate;
        this.configs = configs;
    }

    /**
     * This method fetches boundary relationships from Boundary service for the provided boundaryCode and hierarchyType.
     *
     * @param requestInfo     request info from the request.
     * @param boundaryCode    boundary code from the request.
     * @param tenantId        tenant id from the request.
     * @param hierarchyType   hierarchy type from the request.
     * @param includeParents  is true if you want to include parent boundary.
     * @param includeChildren is true if you want to include child boundary.
     * @return returns the response from boundary service
     */
    public BoundarySearchResponse fetchBoundaryData(RequestInfo requestInfo, String boundaryCode, String tenantId, String hierarchyType, Boolean includeParents, Boolean includeChildren) {

        // Create Boundary Relationship search uri
        Map<String, String> uriParameters = new HashMap<>();
        StringBuilder uri = getBoundaryRelationshipSearchUri(uriParameters, boundaryCode, tenantId, hierarchyType, includeParents, includeChildren);

        // Create request body
        RequestInfoWrapper requestInfoWrapper = RequestInfoWrapper.builder().requestInfo(requestInfo).build();
        BoundarySearchResponse boundarySearchResponse = new BoundarySearchResponse();

        try {
            boundarySearchResponse = restTemplate.postForObject(uri.toString(), requestInfoWrapper, BoundarySearchResponse.class, uriParameters);
        } catch (Exception e) {
            log.error(ERROR_WHILE_FETCHING_BOUNDARY_DETAILS, e);
        }

        return boundarySearchResponse;
    }

    /**
     * This method creates Boundary service uri with query parameters
     *
     * @param uriParameters   map that stores values corresponding to the placeholder in uri
     * @param boundaryCode    boundary code from the request.
     * @param tenantId        tenant id from the request.
     * @param hierarchyType   hierarchy type from the request.
     * @param includeParents  is true if you want to include parent boundary.
     * @param includeChildren is true if you want to include child boundary.
     * @return a complete boundary service uri
     */
    private StringBuilder getBoundaryRelationshipSearchUri(Map<String, String> uriParameters, String boundaryCode, String tenantId, String hierarchyType, Boolean includeParents, Boolean includeChildren) {
        StringBuilder uri = new StringBuilder();
        uri.append(configs.getBoundaryServiceHost()).append(configs.getBoundaryRelationshipSearchEndpoint()).append("?codes={boundaryCode}&includeParents={includeParents}&includeChildren={includeChildren}&tenantId={tenantId}&hierarchyType={hierarchyType}");

        uriParameters.put("boundaryCode", boundaryCode);
        uriParameters.put("tenantId", tenantId);
        uriParameters.put("includeParents", includeParents.toString());
        uriParameters.put("includeChildren", includeChildren.toString());
        uriParameters.put("hierarchyType", hierarchyType);

        return uri;
    }

    public BoundaryTypeHierarchyResponse fetchBoundaryHierarchy(RequestInfo requestInfo, String tenantId, String hierarchyType) {

        // Create Boundary hierarchy search uri
        String uri = getBoundaryHierarchySearchUri();

        // Create request body
        BoundaryTypeHierarchySearchCriteria searchCriteria = BoundaryTypeHierarchySearchCriteria.builder().tenantId(tenantId).hierarchyType(hierarchyType).build();
        BoundaryTypeHierarchySearchRequest searchRequest = BoundaryTypeHierarchySearchRequest.builder().requestInfo(requestInfo).boundaryTypeHierarchySearchCriteria(searchCriteria).build();
        BoundaryTypeHierarchyResponse searchResponse = new BoundaryTypeHierarchyResponse();

        try {
            searchResponse = restTemplate.postForObject(uri, searchRequest, BoundaryTypeHierarchyResponse.class);
        } catch (Exception e) {
            log.error(ERROR_WHILE_FETCHING_BOUNDARY_HIERARCHY_DETAILS, e);
        }

        return searchResponse;
    }

    private String getBoundaryHierarchySearchUri() {
        StringBuilder uri = new StringBuilder();
        uri.append(configs.getBoundaryServiceHost()).append(configs.getBoundaryHierarchySearchEndpoint());
        return uri.toString();
    }
}
