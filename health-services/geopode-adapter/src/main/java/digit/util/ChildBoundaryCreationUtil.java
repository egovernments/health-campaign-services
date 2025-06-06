package digit.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import digit.config.Configuration;
import digit.web.models.Arcgis.ArcgisRequest;
import digit.web.models.Arcgis.ArcgisResponse;
import digit.web.models.Arcgis.Feature;
import digit.web.models.GeopodeBoundaryRequest;
import digit.web.models.boundaryService.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.protocol.types.Field;
import org.egov.common.contract.request.RequestInfo;
import org.egov.tracer.model.CustomException;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.*;
import java.util.stream.Collectors;

import static digit.config.ServiceConstants.*;

@Slf4j
@Service
public class ChildBoundaryCreationUtil {

    private BoundaryUtil boundaryUtil;
    private RestTemplate restTemplate;
    private Configuration config;
    private ObjectMapper mapper; // Inject ObjectMapper

    public ChildBoundaryCreationUtil(BoundaryUtil boundaryUtil,RestTemplate restTemplate,Configuration config,ObjectMapper mapper){
        this.boundaryUtil=boundaryUtil;
        this.restTemplate=restTemplate;
        this.config=config;
        this.mapper=mapper;
    }
    @Async
    /**
     * This method is for creating children
     */
    public void createChildrenAsync(GeopodeBoundaryRequest request, String parentCode) {
        BoundaryHierarchyDefinitionSearchRequest boundaryHierarchyDefinitonSearchRequest=boundaryUtil.buildBoundaryHierarchyDefinitionSearchRequest(request);
        BoundaryHierarchyDefinitionResponse boundaryHierarchyDefinitionResponse=null;

        try {
            boundaryHierarchyDefinitionResponse=boundaryUtil.fetchBoundaryHierarchyDefinition(boundaryHierarchyDefinitonSearchRequest);
        } catch (Exception e) {
            log.error(FAILED_TO_CREATE_CHILDREN, e);
        }

        List<String> boundaryHierarchyDefinitionArray=null;
        try{
            boundaryHierarchyDefinitionArray=extractOrderedHierarchy(boundaryHierarchyDefinitionResponse);
        }
        catch(Exception e){
            log.error(FAILED_TO_DESERIALIZE,e);
        }

        // This will store the final results for each level
        fetchLevelRecursively(boundaryHierarchyDefinitionArray, 0, null,parentCode,request);


    }

    /**
     * Main method to initiate the recursive fetch.
     */
//    public void fetchAllHierarchies(List<String> hierarchyLevels, Map<String, List<Object>> results) {
//        if (hierarchyLevels == null || hierarchyLevels.isEmpty()) return;
//
//        // Start recursion with root level (e.g., ADM0), no parent data
//        fetchLevelRecursively(hierarchyLevels, 0, null,results);
//    }

    /**
     Recursive function to fetch data for each level based on list of parents data.
     *
     * @param hierarchyLevels
     * @param currentIndex
     * @param parentList
     * @param rootCode
     * @param request
     */
    private void fetchLevelRecursively(List<String> hierarchyLevels, int currentIndex, List<List<String>>  parentList, String rootCode,GeopodeBoundaryRequest request) {

        if (currentIndex >= hierarchyLevels.size()) return;

        String currentLevel = hierarchyLevels.get(currentIndex);
        String parentLevel = currentIndex > 0 ? hierarchyLevels.get(currentIndex - 1) : null;

        // If root level (e.g., ADM0), make single batch-wise fetch
        // Will store current search data as [childName,UniqueChildName]
        List<List<String>>  currentChildNames=new ArrayList<>(); // Will hold unique features in format

        if (parentList == null) {
            List<Feature> childResults= fetchAllBatchesForLevel(currentLevel,parentLevel,(String) null,rootCode); // No parent filter
            currentChildNames=initializeBoundaryAndRelationship(childResults,null,currentLevel,request.getRequestInfo());

        } else {
            // For each parent element, make a batch fetch based on parent
            for (List<String> pair : parentList) {
                String parentName = pair.get(0);
                String parentUniqueName = pair.get(1);

                // Fetch all batches where query is like ?adm0=Mozambique or ?adm1=ProvinceName
                List<Feature> childResults = fetchAllBatchesForLevel(currentLevel,parentLevel, parentName,rootCode);
                // Accumulating all parents results
                currentChildNames.addAll(
                        initializeBoundaryAndRelationship(childResults, parentUniqueName, currentLevel, request.getRequestInfo())
                );
            }
        }

        // Proceed to the next level recursively
        // The current boundaryNames become the next-levels parent
        fetchLevelRecursively(hierarchyLevels, currentIndex + 1, currentChildNames, rootCode,request);
    }


    /**
     *  Fetches all batches for a given level and optional parent filter
     *  The parentName will be passed as a query param (e.g., ?adm0=Mozambique)
     *
     * @param currentLevel
     * @param parentName
     * @param rootCode
     * @return
     */
    //TODO include limit, offset in search call
    private List<Feature> fetchAllBatchesForLevel(String currentLevel,String parentLevel, String parentName, String rootCode) {
        // Skip if it's a root level (e.g., ADM0)
        if (parentName == null) {
            String filter = "ADM0_NAME='" + rootCode + "'";
            return callArcgisAPI(currentLevel,filter);
        }

        // Build the 'where' clause: e.g., ADM0_NAME='Mozambique'
        String whereClause = parentLevel + "_NAME='" + parentName + "'";

        // Call ArcGIS API
        return callArcgisAPI(currentLevel, whereClause);

        //        int offset = 0;
        //        int limit = 100;

        //        while (true) {
        //            // Replace with actual API call logic
        //            List<Object> batch = callSearchAPI(level, parentName, offset, limit);
        //
        //            if (batch == null || batch.isEmpty()) break;
        //
        //            result.addAll(batch);
        //
        //            if (batch.size() < limit) break; // Last batch
        //
        //            offset += limit;
        //        }

    }
    /**
     * Calls Arcgis api endpoint
     * */
    private List<Feature> callArcgisAPI(String currentLevel, String whereClause) {
        List<Feature> resultList = new ArrayList<>();  // to store features from the response

        URI url = UriComponentsBuilder.fromHttpUrl(config.getArcgisEndpoint())
                .queryParam("where", whereClause)                      // e.g., ADM0_NAME='NIGERIA'
                .queryParam("outFields", currentLevel + "_NAME")       // e.g., ADM1_NAME
                .queryParam("f", "json")                               // response format
                .build()
                .encode()
                .toUri();

        try {
            // Make the HTTP call
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null, // GET call, no request body
                    new ParameterizedTypeReference<Map<String, Object>>() {}
            );

            // Deserialize into ArcgisResponse
            ArcgisResponse arcgisResponse = mapper.convertValue(response.getBody(), ArcgisResponse.class);

            if (arcgisResponse != null && arcgisResponse.getFeatures() != null) {
                resultList = arcgisResponse.getFeatures();  // extract the list of features
            }
        } catch (Exception e) {
            System.err.println("ArcGIS API error for level: " + currentLevel + " with where=" + whereClause);
            log.error(ERROR_IN_ARC_SEARCH, e);
            throw new CustomException(ERROR_IN_ARC_SEARCH, "Error when fetching from arcgis");
        }

        return resultList;
    }

    /**
     * Extracts the name field from the parent object.
     *
     * @param node
     * @param level
     * @return
     */
    private String extractNameFromFeature(Feature node, String level) {
        // Use switch to return the correct name based on the level
        return switch (level) {
            case "ADM0" -> node.getAttributes().getADM0_NAME();
            case "ADM1" -> node.getAttributes().getADM1_NAME();
            case "ADM2" -> node.getAttributes().getADM2_NAME();
            case "ADM3" -> node.getAttributes().getADM3_NAME();
            case "ADM4" -> node.getAttributes().getADM4_NAME();
            default -> null; // Handles unexpected levels
        };
    }


    //Note parent code is already unique

    /**
     * Method initializes boundary-Entity and boundary-relation
     * Generates unique-codes for each of the childResults
     * return list in form [[child1,uniqueChildCode1],[child2,uniqueChild2Code]]
     *
     * @param childResults
     * @param parentUniqueCode
     * @param currentLevel
     * @param requestInfo
     * @return
     */
    private List<List<String>> initializeBoundaryAndRelationship(List<Feature> childResults, String parentUniqueCode, String currentLevel, RequestInfo requestInfo){
        List<List<String>> uniqueChildNames = new ArrayList<>(); // Will hold in format [childName,UniqueChildName]
        Set<String> seenBoundaryNames = new HashSet<>();  // Will track boundary names we've already added
        // Stores child->parent unique code mappings in insertion order

        for(Feature child: childResults) {
            String childBoundaryName=extractNameFromFeature(child,currentLevel);

            if (!seenBoundaryNames.contains(childBoundaryName)) {
                List<String> result = boundaryUtil.createUniqueBoundaryName(childBoundaryName, currentLevel, requestInfo);
                String childCode = result.get(0);
                String childUniqueCode = result.get(1);
                seenBoundaryNames.add(childBoundaryName);      // Mark name as seen
                uniqueChildNames.add(Arrays.asList(childCode, childUniqueCode));// Adding in form [childName,UniqueChildName]
                initializeBoundary(childUniqueCode,currentLevel,requestInfo);

            }
            seenBoundaryNames.add(childBoundaryName);

        }
        // [childName,UniqueChildName]
        for (List<String> pair : uniqueChildNames) {
            String childCode = pair.get(0);
            String childUniqueCode = pair.get(1);

            initializeParentChildRelationShip(childUniqueCode, parentUniqueCode, currentLevel, requestInfo);
        }

        //
        return uniqueChildNames;
    }

    /**
     * Method initializes boundary-Entity
     *
     * @param childUniqueCode
     * @param currentLevel
     * @param requestInfo
     */
    private void initializeBoundary(String childUniqueCode, String currentLevel, RequestInfo requestInfo){
        BoundaryRequest boundaryRequest=boundaryUtil.buildBoundaryRequest( childUniqueCode, config.getTenantId(), requestInfo );
        boundaryUtil.sendBoundaryRequest(boundaryRequest);
    }

    /**
     *  Method initializes boundary-relation
     *
     * @param childUniqueCode
     * @param parentUniqueCode
     * @param currentLevel
     * @param requestInfo
     */
    private void initializeParentChildRelationShip(String childUniqueCode, String parentUniqueCode, String currentLevel,RequestInfo requestInfo){
        BoundaryRelationshipRequest boundaryRelationshipRequest =
                BoundaryRelationshipRequest.builder()
                        .requestInfo(requestInfo).boundaryRelationship(BoundaryRelation.builder()
                                .code(childUniqueCode)
                                .parent(parentUniqueCode)
                                .boundaryType(currentLevel)
                                .tenantId(config.getTenantId())
                                .hierarchyType(HIERARCHY_TYPE)
                                .build()).build();
        String url = config.getBoundaryServiceHost() + config.getBoundaryRelationshipCreateEndpoint();
        restTemplate.postForObject(url, boundaryRelationshipRequest, BoundaryRelationshipResponse.class);

    }

    /**
     * Extracts the hierarchy as a list from the BoundaryDefinitionSearchResponse.
     *
     * @param response the response object containing boundary definitions
     * @return a list representing the extracted hierarchy
     */

    public static List<String> extractOrderedHierarchy(BoundaryHierarchyDefinitionResponse response) {
        // Safety check
        if (response == null || response.getBoundaryHierarchy() == null || response.getBoundaryHierarchy().isEmpty()) {
            return Collections.emptyList(); // nothing to process
        }

        // Step 1: Get the first BoundaryHierarchy (you can loop over all if needed)
        BoundaryHierarchy hierarchy = response.getBoundaryHierarchy().get(0);
        List<BoundaryHierarchy.SimpleBoundary> boundaries = hierarchy.getBoundaryHierarchy();

        if (boundaries == null || boundaries.isEmpty()) {
            return Collections.emptyList();
        }

        // Step 2: Build a map of boundaryType -> SimpleBoundary for quick access
        Map<String, BoundaryHierarchy.SimpleBoundary> boundaryMap = boundaries.stream()
                .collect(Collectors.toMap(BoundaryHierarchy.SimpleBoundary::getBoundaryType, b -> b));

        // Step 3: Build a child -> parent map
        Map<String, String> childToParent = boundaries.stream()
                .filter(b -> b.getParentBoundaryType() != null)
                .collect(Collectors.toMap(BoundaryHierarchy.SimpleBoundary::getBoundaryType, BoundaryHierarchy.SimpleBoundary::getParentBoundaryType));

        // Step 4: Find the root node (parentBoundaryType == null)
        Optional<BoundaryHierarchy.SimpleBoundary> rootOpt = boundaries.stream()
                .filter(b -> b.getParentBoundaryType() == null)
                .findFirst();

        if (rootOpt.isEmpty()) {
            throw new IllegalStateException("No root boundary (with null parent) found in hierarchy");
        }

        String current = rootOpt.get().getBoundaryType();

        // Step 5: Walk through the hierarchy linearly and build the ordered list
        List<String> orderedList = new ArrayList<>();
        while (current != null) {
            orderedList.add(current);

            // Find the next child that has current as its parent
            String finalCurrent = current;
            String next = childToParent.entrySet().stream()
                    .filter(e -> e.getValue().equals(finalCurrent))
                    .map(Map.Entry::getKey)
                    .findFirst()
                    .orElse(null); // end of hierarchy

            current = next;
        }

        return orderedList;
    }


}