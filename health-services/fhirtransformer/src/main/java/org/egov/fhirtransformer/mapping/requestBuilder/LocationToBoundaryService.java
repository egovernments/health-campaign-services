package org.egov.fhirtransformer.mapping.requestBuilder;

import digit.web.models.*;
import org.egov.common.contract.request.RequestInfo;
import org.egov.fhirtransformer.common.Constants;
import org.egov.fhirtransformer.service.ApiIntegrationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service responsible for transforming FHIR Location–derived
 * {@link BoundaryRelation} data into DIGIT Boundary service requests.
 */
@Service
public class LocationToBoundaryService {

    @Autowired
    private ApiIntegrationService apiIntegrationService;

    @Autowired
    private GenericCreateOrUpdateService genericCreateOrUpdateService;

    @Value("${app.tenant-id}")
    private String tenantId;

    @Value("${boundary.create.url}")
    private String boundaryCreateUrl;

    @Value("${boundary.update.url}")
    private String boundaryUpdateUrl;

    private RequestInfo requestInfo;

    private static final Logger logger = LoggerFactory.getLogger(LocationToBoundaryService.class);


    /**
     * Transforms and persists BoundaryRelation records derived from Locations.
     * @param boundaryRelationMap map of boundary ID to BoundaryRelation data;
     *                            may be empty but not {@code null}
     * @return map containing processing metrics
     * @throws Exception if transformation or API invocation fails
     */
    public HashMap<String, Integer> transformLocationToBoundary(HashMap<String, BoundaryRelation> boundaryRelationMap, RequestInfo requestInfo) throws Exception {
        this.requestInfo = requestInfo;

        return genericCreateOrUpdateService.process(boundaryRelationMap,
                this::fetchExistingBoundaryIds,
                this::createBoundaries,
                this::updateBoundaries,
                boundaryCreateUrl,
                boundaryUpdateUrl,
                requestInfo,
                "Error in transformLocationToBoundary");
    }

    /**
     * Updates parent boundary references using in-memory BoundaryRelation data.
     * @param boundaryRelationMap map of boundary ID to BoundaryRelation data;
     *                            must not be {@code null}
     * @return updated map with resolved parent boundary codes
     * @throws Exception if parent resolution fails
     */
    public HashMap<String, BoundaryRelation> updateBoundaryRelationParent(HashMap<String, BoundaryRelation> boundaryRelationMap) throws Exception {
        try{
            for (String key : boundaryRelationMap.keySet()) {
                BoundaryRelation boundaryRelation = boundaryRelationMap.get(key);
                String parentId = boundaryRelation.getParent();
                if (boundaryRelationMap.containsKey(parentId)) {
                    BoundaryRelation parentBoundaryRelation = boundaryRelationMap.get(parentId);
                    boundaryRelation.setParent(parentBoundaryRelation.getCode());
                } else {
                    boundaryRelation.setParent(null);
                }
            }
        } catch (Exception e){
            throw new Exception("Error in updateBoundaryRelationParent: " + e.getMessage());
        }
        return boundaryRelationMap;
    }

    /**
     * Adapter: fetch existing boundary codes (flat list) by calling the boundary search API and extracting codes recursively.
     */
    public List<String> fetchExistingBoundaryIds(List<String> idList) throws Exception {
        try{
            BoundaryRelationshipSearchCriteria criteria = new BoundaryRelationshipSearchCriteria();
            criteria.setCodes(idList);
            criteria.setTenantId(tenantId);
            criteria.setHierarchyType(Constants.HIERARCHY_TYPE);
            criteria.setIncludeChildren(Constants.INCLUDE_CHILDREN);
            BoundarySearchResponse boundarySearchResponse = apiIntegrationService.fetchAllBoundaries(criteria, this.requestInfo);
            List<String> existingIds = new ArrayList<>();
            if (!boundarySearchResponse.getTenantBoundary().isEmpty()) {
                for (EnrichedBoundary boundary : boundarySearchResponse.getTenantBoundary().get(0).getBoundary()) {
                    extractBoundaryCodes(boundary, existingIds);
                }
            }
            return existingIds;
        } catch (Exception e){
            throw new Exception("Error in fetchExistingBoundaries: " + e.getMessage());
        }
    }

    /**
     * Adapter: create boundaries by iterating and sending single BoundaryRelationshipRequest per item (keeps previous behavior).
     */
    public void createBoundaries(List<BoundaryRelation> toCreate, String createUrl) throws Exception {
        try{
            if (toCreate == null || toCreate.isEmpty()) return;
            for (BoundaryRelation br : toCreate) {
                BoundaryRelationshipRequest boundaryRelationshipRequest = new BoundaryRelationshipRequest();
                boundaryRelationshipRequest.setRequestInfo(this.requestInfo);
                boundaryRelationshipRequest.setBoundaryRelationship(br);
                apiIntegrationService.sendRequestToAPI(boundaryRelationshipRequest, createUrl);
            }
        } catch (Exception e) {
            throw new Exception("Error in createBoundaries: " + e.getMessage());
        }
    }

    /**
     * Adapter: update boundaries by iterating and sending single BoundaryRelationshipRequest per item.
     */
    public void updateBoundaries(List<BoundaryRelation> toUpdate, String updateUrl) throws Exception {
        try{
            if (toUpdate == null || toUpdate.isEmpty()) return;
            for (BoundaryRelation br : toUpdate) {
                BoundaryRelationshipRequest boundaryRelationshipRequest = new BoundaryRelationshipRequest();
                boundaryRelationshipRequest.setRequestInfo(this.requestInfo);
                boundaryRelationshipRequest.setBoundaryRelationship(br);
                apiIntegrationService.sendRequestToAPI(boundaryRelationshipRequest, updateUrl);
            }
        } catch (Exception e) {
            throw new Exception("Error in updateBoundaries: " + e.getMessage());
        }
    }

    private void extractBoundaryCodes(EnrichedBoundary boundary, List<String> codes) {
        codes.add(boundary.getCode());
        if (boundary.getChildren() != null && !boundary.getChildren().isEmpty()) {
            for (EnrichedBoundary child : boundary.getChildren()) {
                extractBoundaryCodes(child, codes);
            }
        }
    }

}
