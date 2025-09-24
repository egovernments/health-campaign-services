package org.egov.excelingestion.util;

import lombok.extern.slf4j.Slf4j;
import org.egov.excelingestion.service.BoundaryService;
import org.egov.excelingestion.service.CampaignService;
import org.egov.excelingestion.web.models.*;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Utility class for boundary processing and enrichment logic.
 * Extracted from BoundaryHierarchySheetCreator to be reused across different sheet creators.
 */
@Slf4j
@Component
public class BoundaryUtil {

    private final CampaignService campaignService;
    private final BoundaryService boundaryService;

    public BoundaryUtil(CampaignService campaignService, BoundaryService boundaryService) {
        this.campaignService = campaignService;
        this.boundaryService = boundaryService;
    }

    /**
     * Processes and enriches boundary data based on the includeAllChildren flag.
     * This method handles the expansion of child boundaries when includeAllChildren = true.
     * 
     * @param boundaries The list of boundaries to process
     * @param codeToEnrichedBoundary Map of boundary codes to enriched boundary data
     * @param levelTypes List of level types for the hierarchy
     * @return List of processed boundary row data
     */
    public List<BoundaryRowData> processBoundariesWithEnrichment(List<Boundary> boundaries, 
                                                               Map<String, EnrichedBoundary> codeToEnrichedBoundary,
                                                               List<String> levelTypes) {
        List<BoundaryRowData> boundaryRows = new ArrayList<>();
        
        // Null check for boundaries - if null or empty, return empty list
        if (boundaries == null || boundaries.isEmpty()) {
            log.info("No boundaries provided for processing, returning empty list");
            return boundaryRows;
        }
        
        Set<String> processedCodes = new HashSet<>();
        
        // Find root boundary
        Boundary rootBoundary = boundaries.stream()
                .filter(b -> Boolean.TRUE.equals(b.getIsRoot()))
                .findFirst()
                .orElse(null);
                
        if (rootBoundary == null) {
            log.error("No root boundary found in the boundaries list");
            return boundaryRows;
        }
        
        // Process boundaries starting from root
        processBoundary(rootBoundary, boundaries, codeToEnrichedBoundary, boundaryRows, 
                       processedCodes, new ArrayList<>(), levelTypes);
        
        return boundaryRows;
    }

    /**
     * Filters boundaries based on provided boundaries configuration from additionalDetails.
     * If boundaries array is empty or not present, returns all boundaries.
     * If boundaries array exists, filters to include only those boundaries and their children (if includeChildren flag is true).
     * 
     * @param allBoundaries All available boundaries from boundary service
     * @param configuredBoundaries Boundaries from additionalDetails configuration
     * @param includeChildren Flag to include child boundaries
     * @return Filtered list of boundaries
     */
    public List<BoundaryRowData> filterBoundariesForDropdown(List<BoundaryRowData> allBoundaries,
                                                           List<Boundary> configuredBoundaries,
                                                           boolean includeChildren) {
        if (configuredBoundaries == null || configuredBoundaries.isEmpty()) {
            // If no boundaries configured, return empty list (no boundary columns should be added)
            return new ArrayList<>();
        }

        Set<String> allowedCodes = new HashSet<>();
        
        // Add all configured boundary codes
        for (Boundary boundary : configuredBoundaries) {
            allowedCodes.add(boundary.getCode());
            
            // If includeChildren is true for any boundary, add its children recursively
            if (Boolean.TRUE.equals(boundary.getIncludeAllChildren())) {
                addAllChildrenCodes(boundary, configuredBoundaries, allowedCodes);
            }
        }
        
        // Filter boundary rows to include only allowed codes
        return allBoundaries.stream()
                .filter(row -> allowedCodes.contains(row.getLastLevelCode()))
                .collect(Collectors.toList());
    }

    /**
     * Builds a map of boundary code to EnrichedBoundary for quick lookup
     */
    public Map<String, EnrichedBoundary> buildCodeToBoundaryMap(BoundarySearchResponse relationshipData) {
        Map<String, EnrichedBoundary> codeToEnrichedBoundary = new HashMap<>();
        if (relationshipData != null && relationshipData.getTenantBoundary() != null) {
            for (HierarchyRelation hr : relationshipData.getTenantBoundary()) {
                buildCodeToBoundaryMapRecursive(hr.getBoundary(), codeToEnrichedBoundary);
            }
        }
        return codeToEnrichedBoundary;
    }

    /**
     * Recursively processes a boundary and its children based on includeAllChildren flag
     */
    private void processBoundary(Boundary boundary, List<Boundary> allBoundaries, 
                                Map<String, EnrichedBoundary> codeToEnrichedBoundary,
                                List<BoundaryRowData> boundaryRows, Set<String> processedCodes,
                                List<String> currentPath, List<String> levelTypes) {
        
        if (boundary == null || processedCodes.contains(boundary.getCode())) {
            return;
        }
        
        processedCodes.add(boundary.getCode());
        
        // Create new path with current boundary
        List<String> newPath = new ArrayList<>(currentPath);
        int levelIndex = getLevelIndex(boundary.getType(), levelTypes);
        
        // Ensure path has enough elements
        while (newPath.size() <= levelIndex) {
            newPath.add(null);
        }
        newPath.set(levelIndex, boundary.getCode());
        
        // If includeAllChildren is true, process all children from enriched boundary data
        if (Boolean.TRUE.equals(boundary.getIncludeAllChildren())) {
            EnrichedBoundary enrichedBoundary = codeToEnrichedBoundary.get(boundary.getCode());
            if (enrichedBoundary != null && enrichedBoundary.getChildren() != null) {
                processAllChildren(enrichedBoundary.getChildren(), codeToEnrichedBoundary, 
                                 boundaryRows, processedCodes, newPath, levelTypes);
            }
        } else {
            // Add current path as a row with boundary code
            boundaryRows.add(new BoundaryRowData(new ArrayList<>(newPath), boundary.getCode()));
            
            // Process only the boundaries that are in the input list and are children of current
            List<Boundary> children = allBoundaries.stream()
                    .filter(b -> boundary.getCode().equals(b.getParent()))
                    .collect(Collectors.toList());
                    
            for (Boundary child : children) {
                processBoundary(child, allBoundaries, codeToEnrichedBoundary, 
                              boundaryRows, processedCodes, newPath, levelTypes);
            }
        }
    }
    
    /**
     * Recursively processes all children when includeAllChildren is true
     */
    private void processAllChildren(List<EnrichedBoundary> children, 
                                   Map<String, EnrichedBoundary> codeToEnrichedBoundary,
                                   List<BoundaryRowData> boundaryRows, Set<String> processedCodes,
                                   List<String> currentPath, List<String> levelTypes) {
        
        if (children == null || children.isEmpty()) {
            return;
        }
        
        for (EnrichedBoundary child : children) {
            if (!processedCodes.contains(child.getCode())) {
                processedCodes.add(child.getCode());
                
                // Create new path with child
                List<String> newPath = new ArrayList<>(currentPath);
                int levelIndex = getLevelIndex(child.getBoundaryType(), levelTypes);
                
                // Ensure path has enough elements
                while (newPath.size() <= levelIndex) {
                    newPath.add(null);
                }
                newPath.set(levelIndex, child.getCode());
                
                // Add current path as a row with boundary code
                boundaryRows.add(new BoundaryRowData(new ArrayList<>(newPath), child.getCode()));
                
                // Recursively process children
                if (child.getChildren() != null && !child.getChildren().isEmpty()) {
                    processAllChildren(child.getChildren(), codeToEnrichedBoundary,
                                     boundaryRows, processedCodes, newPath, levelTypes);
                }
            }
        }
    }
    
    private void buildCodeToBoundaryMapRecursive(List<EnrichedBoundary> boundaries, Map<String, EnrichedBoundary> codeMap) {
        if (boundaries == null) return;
        
        for (EnrichedBoundary boundary : boundaries) {
            codeMap.put(boundary.getCode(), boundary);
            if (boundary.getChildren() != null && !boundary.getChildren().isEmpty()) {
                buildCodeToBoundaryMapRecursive(boundary.getChildren(), codeMap);
            }
        }
    }

    private void addAllChildrenCodes(Boundary boundary, List<Boundary> allBoundaries, Set<String> allowedCodes) {
        List<Boundary> children = allBoundaries.stream()
                .filter(b -> boundary.getCode().equals(b.getParent()))
                .collect(Collectors.toList());
        
        for (Boundary child : children) {
            allowedCodes.add(child.getCode());
            // Recursively add children's children
            addAllChildrenCodes(child, allBoundaries, allowedCodes);
        }
    }
    
    private int getLevelIndex(String boundaryType, List<String> levelTypes) {
        for (int i = 0; i < levelTypes.size(); i++) {
            if (levelTypes.get(i).equalsIgnoreCase(boundaryType)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Inner class to hold boundary row data
     */
    public static class BoundaryRowData {
        private final List<String> boundaryPath;
        private final String lastLevelCode;
        
        public BoundaryRowData(List<String> boundaryPath, String lastLevelCode) {
            this.boundaryPath = boundaryPath;
            this.lastLevelCode = lastLevelCode;
        }
        
        public List<String> getBoundaryPath() {
            return boundaryPath;
        }
        
        public String getLastLevelCode() {
            return lastLevelCode;
        }
        
        public boolean isLastLevel(int lastLevelIndex) {
            // Check if the boundary path has a non-null value at the last level index
            return boundaryPath.size() > lastLevelIndex && boundaryPath.get(lastLevelIndex) != null;
        }
    }

    /**
     * Get all enriched boundary codes from campaign boundaries including all children
     * @param processId Process ID or generation ID for cache key
     * @param referenceId Reference ID for cache key
     * @param tenantId Tenant ID
     * @param hierarchyType Hierarchy type
     * @param requestInfo Request info
     * @return Set of all valid boundary codes (campaign boundaries + their children)
     */
    @Cacheable(value = "enrichedBoundaryCodes", key = "#processId + '_' + #referenceId")
    public Set<String> getEnrichedBoundaryCodesFromCampaign(String processId, String referenceId, 
                                                           String tenantId, String hierarchyType, RequestInfo requestInfo) {
        // Use the enriched boundaries function to get full boundary objects
        List<Boundary> enrichedBoundaries = getEnrichedBoundariesFromCampaign(processId, referenceId, tenantId, hierarchyType, requestInfo);
        
        // Extract codes from boundary objects
        Set<String> enrichedCodes = enrichedBoundaries.stream()
                .map(Boundary::getCode)
                .filter(code -> code != null)
                .collect(Collectors.toSet());
        
        log.info("Extracted {} boundary codes from enriched boundaries", enrichedCodes.size());
        
        return enrichedCodes;
    }

    /**
     * Recursively map all boundary nodes to their codes
     */
    private void mapBoundaryNodesFromEnriched(EnrichedBoundary boundary, Map<String, EnrichedBoundary> codeToNode) {
        if (boundary.getCode() != null) {
            codeToNode.put(boundary.getCode(), boundary);
        }
        
        if (boundary.getChildren() != null) {
            for (EnrichedBoundary child : boundary.getChildren()) {
                mapBoundaryNodesFromEnriched(child, codeToNode);
            }
        }
    }

    /**
     * Get all enriched boundaries from campaign boundaries including all children as full boundary objects
     * @param processId Process ID or generation ID for cache key
     * @param referenceId Reference ID for cache key
     * @param tenantId Tenant ID
     * @param hierarchyType Hierarchy type
     * @param requestInfo Request info
     * @return List of all enriched boundary objects (campaign boundaries + their children)
     */
    @Cacheable(value = "enrichedBoundaryObjects", key = "#processId + '_' + #referenceId")
    public List<Boundary> getEnrichedBoundariesFromCampaign(String processId, String referenceId,
                                                           String tenantId, String hierarchyType, RequestInfo requestInfo) {
        List<Boundary> enrichedBoundaries = new ArrayList<>();
        
        // Fetch campaign boundaries from campaign service
        List<CampaignSearchResponse.BoundaryDetail> campaignBoundaries = 
            campaignService.getBoundariesFromCampaign(referenceId, tenantId, requestInfo);
        
        if (campaignBoundaries == null || campaignBoundaries.isEmpty()) {
            log.warn("No campaign boundaries found for referenceId: {}", referenceId);
            return enrichedBoundaries;
        }
        
        // Fetch boundary response from boundary service
        BoundarySearchResponse boundaryResponse = boundaryService.fetchBoundaryRelationship(
            tenantId, hierarchyType, requestInfo);
        
        // Build a map of boundary code to EnrichedBoundary for quick lookup
        Map<String, EnrichedBoundary> codeToNode = new HashMap<>();
        if (boundaryResponse != null && boundaryResponse.getTenantBoundary() != null) {
            for (HierarchyRelation hr : boundaryResponse.getTenantBoundary()) {
                if (hr.getBoundary() != null) {
                    for (EnrichedBoundary boundary : hr.getBoundary()) {
                        mapBoundaryNodesFromEnriched(boundary, codeToNode);
                    }
                }
            }
        }
        
        Set<String> processedCodes = new HashSet<>();
        
        // For each campaign boundary, find all its children based on includeAllChildren flag
        for (CampaignSearchResponse.BoundaryDetail campaignBoundary : campaignBoundaries) {
            String campaignBoundaryCode = campaignBoundary.getCode();
            if (campaignBoundaryCode != null && codeToNode.containsKey(campaignBoundaryCode)) {
                EnrichedBoundary boundaryNode = codeToNode.get(campaignBoundaryCode);
                
                // Set includeAllChildren flag from campaign boundary
                boolean includeChildren = campaignBoundary.getIncludeAllChildren() != null ? 
                    campaignBoundary.getIncludeAllChildren() : false;
                
                collectAllBoundariesFromEnriched(boundaryNode, enrichedBoundaries, processedCodes, includeChildren, 
                        campaignBoundary.getParent());
            }
        }
        
        log.info("Enriched {} campaign boundaries to {} total boundary objects including children", 
                campaignBoundaries.size(), enrichedBoundaries.size());
        
        return enrichedBoundaries;
    }

    /**
     * Recursively collect all boundary objects from EnrichedBoundary hierarchy based on includeAllChildren flag
     */
    private void collectAllBoundariesFromEnriched(EnrichedBoundary enrichedBoundary, List<Boundary> boundaries, 
                                                 Set<String> processedCodes, boolean includeAllChildren, String parentCode) {
        if (enrichedBoundary.getCode() == null || processedCodes.contains(enrichedBoundary.getCode())) {
            return;
        }
        
        processedCodes.add(enrichedBoundary.getCode());
        
        // Convert EnrichedBoundary to Boundary
        Boundary boundary = new Boundary();
        boundary.setCode(enrichedBoundary.getCode());
        boundary.setName(enrichedBoundary.getCode());
        boundary.setType(enrichedBoundary.getBoundaryType());
        boundary.setIsRoot(parentCode == null);
        // Set parent from recursive call parameter
        boundary.setParent(parentCode);
        boundary.setIncludeAllChildren(includeAllChildren);
        
        boundaries.add(boundary);
        
        // If includeAllChildren is true, recursively process all children in the entire branch
        if (includeAllChildren && enrichedBoundary.getChildren() != null) {
            for (EnrichedBoundary child : enrichedBoundary.getChildren()) {
                collectAllBoundariesFromEnriched(child, boundaries, processedCodes, true, enrichedBoundary.getCode()); // Pass current boundary as parent
            }
        }
    }

    /**
     * Get only the lowest level boundary codes from campaign boundaries based on hierarchy levels
     * @param processId Process ID or generation ID for cache key
     * @param referenceId Reference ID for cache key
     * @param tenantId Tenant ID
     * @param hierarchyType Hierarchy type
     * @param requestInfo Request info
     * @return Set of lowest level boundary codes
     */
    @Cacheable(value = "enrichedBoundaryObjects", key = "#processId + '_' + #referenceId + '_lowest'")
    public Set<String> getLowestLevelBoundaryCodesFromCampaign(String processId, String referenceId,
                                                              String tenantId, String hierarchyType, RequestInfo requestInfo) {
        // Get boundary hierarchy to find lowest level type
        BoundaryHierarchyResponse hierarchyResponse = boundaryService.fetchBoundaryHierarchy(tenantId, hierarchyType, requestInfo);
        
        if (hierarchyResponse == null || hierarchyResponse.getBoundaryHierarchy() == null) {
            log.warn("No boundary hierarchy found for tenantId: {}, hierarchyType: {}", tenantId, hierarchyType);
            return new HashSet<>();
        }
        
        // Find the lowest level boundary type from hierarchy
        List<BoundaryHierarchyChild> hierarchy = hierarchyResponse.getBoundaryHierarchy().get(0).getBoundaryHierarchy();

        final String lowestLevelType = hierarchy.get(hierarchy.size() - 1).getBoundaryType();
        
        if (lowestLevelType == null) {
            log.warn("Could not determine lowest level boundary type from hierarchy");
            return new HashSet<>();
        }
        
        log.info("Lowest level boundary type: {}", lowestLevelType);
        
        // Get all enriched boundaries
        List<Boundary> enrichedBoundaries = getEnrichedBoundariesFromCampaign(processId, referenceId, tenantId, hierarchyType, requestInfo);
        
        // Filter only lowest level boundaries by type
        Set<String> lowestLevelCodes = enrichedBoundaries.stream()
                .filter(boundary -> lowestLevelType.equals(boundary.getType()))
                .map(Boundary::getCode)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        
        log.info("Found {} lowest level boundary codes of type '{}' out of {} total boundaries", 
                lowestLevelCodes.size(), lowestLevelType, enrichedBoundaries.size());
        
        return lowestLevelCodes;
    }
}