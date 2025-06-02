package digit.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import digit.config.Configuration;
import digit.web.models.Arcgis.ArcgisRequest;
import digit.web.models.Arcgis.ArcgisResponse;
import digit.web.models.Arcgis.Feature;
import digit.web.models.GeopodeBoundaryRequest;
import digit.web.models.boundaryService.*;
import lombok.extern.slf4j.Slf4j;
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
    public void createChildrenAsync(GeopodeBoundaryRequest request, String parentCode) {
        BoundaryHierarchyDefinitionSearchRequest boundaryHierarchyDefinitonSearchRequest=buildBoundaryHierarchyDefinitionSearchRequest(request);
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
        Map<String, List<Feature>> results = new HashMap<>();
        fetchLevelRecursively(boundaryHierarchyDefinitionArray, 0, null,results,parentCode,request);
        System.out.println("Results from Child Async"+results);


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
     * Recursive function to fetch data for each level based on parent data.
     */
    private void fetchLevelRecursively(List<String> hierarchyLevels, int currentIndex, List<Feature> parentList, Map<String, List<Feature>> results, String rootCode,GeopodeBoundaryRequest request) {
        System.out.println("fetchLevelRecursively(, hierarchyLevels fetch recur= " + hierarchyLevels); // prints the list of hierarchy levels
        System.out.println("currentIndex = " + currentIndex);       // prints the current index
        System.out.println("parentList = " + parentList);           // prints the list of parent features
//        System.out.println("fetchLevelRecursively(, results = fetch recur" + results);                 // prints the map of results
        if (currentIndex >= hierarchyLevels.size()) return;

        if (currentIndex >= hierarchyLevels.size()-1) return; //TODO: write the memo logic

        String currentLevel = hierarchyLevels.get(currentIndex);
        String parentLevel = currentIndex > 0 ? hierarchyLevels.get(currentIndex - 1) : null;

        List<Feature> currentLevelData = new ArrayList<>();

        // If root level (e.g., ADM0), make single batch-wise fetch

        // Store current level data in the result map
        List<Feature> uniqueFeatures = new ArrayList<>(); // Will hold unique features
        Set<String> seenBoundaryNames = new HashSet<>();  // Will track boundary names we've already added

        if (parentList == null) {
            List<Feature> childResults= fetchAllBatchesForLevel(currentLevel,(String) null,rootCode); // No parent filter

            for(Feature child: childResults) {
                String featureName = extractNameFromFeature(child, currentLevel);
                if (!seenBoundaryNames.contains(featureName)) {
                    seenBoundaryNames.add(featureName);      // Mark name as seen
                    uniqueFeatures.add(child);               // Add feature only if name was not seen
                    initializeBoundary(child, currentLevel, request.getRequestInfo());
                }
                seenBoundaryNames.add(featureName);

            }
            System.out.println("parentList admin0"+uniqueFeatures);

            System.out.println("currentLevelData"+currentIndex+currentLevelData);
        } else {
            // For each parent element, make a batch fetch based on parent
            for (Feature parent : parentList) {
                // Extract parent name (e.g., "Mozambique") for query param
                String parentName = extractNameFromFeature( parent,parentLevel);

                // Fetch all batches where query is like ?adm0=Mozambique or ?adm1=ProvinceName
                List<Feature> childResults = fetchAllBatchesForLevel(currentLevel, parentName,rootCode);

                for(Feature child: childResults){
                    String featureName=extractNameFromFeature(child,currentLevel);
                    if (!seenBoundaryNames.contains(featureName)) {
                        seenBoundaryNames.add(featureName);      // Mark name as seen
                        uniqueFeatures.add(child);               // Add feature only if name was not seen
                        initializeBoundary(child, currentLevel, request.getRequestInfo());
                        initializeParentChildRelationShip(child,parentName,currentLevel,request.getRequestInfo());
                    }
                    seenBoundaryNames.add(featureName);

                }
                currentLevelData.addAll(childResults);
            }
        }


        results.put(currentLevel, uniqueFeatures);

        System.out.println("fetch rescursively results current level "+currentLevel+uniqueFeatures);

        // Proceed to the next level recursively
        fetchLevelRecursively(hierarchyLevels, currentIndex + 1, uniqueFeatures,results, rootCode,request);
    }
    /**
     * Fetches all batches for a given level and optional parent filter.
     * The parentName will be passed as a query param (e.g., ?adm0=Mozambique)
     */
    //TODO include limit, offset in search call
    private List<Feature> fetchAllBatchesForLevel(String currentLevel, String parentName, String rootCode) {
        System.out.println("fetchallbatchesForLevel currentLevels " + currentLevel + ", parentName: " + parentName);
        // Skip if it's a root level (e.g., ADM0)
        if (parentName == null) {
            String filter = "ADM0_NAME='" + rootCode + "'";
            return callArcgisAPI(currentLevel,filter);
        }
        String parentLevel = deriveParentLevel(currentLevel);

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
        System.out.println("callarcgisapi currentLevel = " + currentLevel + ", whereClause = " + whereClause);
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
                System.out.println("arcgisResponse"+currentLevel+ arcgisResponse.getFeatures());
            }
        } catch (Exception e) {
            System.err.println("ArcGIS API error for level: " + currentLevel + " with where=" + whereClause);
            log.error(ERROR_FETCHING_FROM_ARCGIS, e);
            throw new CustomException(ERROR_FETCHING_FROM_ARCGIS, "Error when fetching from arcgis");
        }

        return resultList;
    }


    private String deriveParentLevel(String currentLevel) {
        // Assumes levels are named like ADM0, ADM1, ADM2, ...
        try {
            int levelNum = Integer.parseInt(currentLevel.replaceAll("[^0-9]", ""));
            return "ADM" + (levelNum - 1);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid level format: " + currentLevel);
        }
    }



    /**
     * Extracts the name field from the parent object.
     * Modify this based on your actual response structure.
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



    private BoundaryHierarchyDefinitionSearchRequest buildBoundaryHierarchyDefinitionSearchRequest(GeopodeBoundaryRequest request) {
        return BoundaryHierarchyDefinitionSearchRequest.builder()
                .requestInfo(request.getRequestInfo()) // Extract request info from original request
                .boundaryTypeHierarchySearchCriteria( // Set the search criteria
                        BoundaryHierarchyDefinitionSearchCriteria.builder()
                                .hierarchyType(HIERARCHY_TYPE) // Use the defined hierarchy type
                                .build()
                )
                .build();
    }

    private void initializeBoundary(Feature child, String currentLevel, RequestInfo requestInfo){
        System.out.println("initialize child "+extractNameFromFeature(child,currentLevel)+currentLevel);
        BoundaryRequest boundaryRequest=boundaryUtil.buildBoundaryRequest( Optional.ofNullable(extractNameFromFeature(child,currentLevel)),requestInfo );
        boundaryUtil.sendBoundaryRequest(boundaryRequest);

    }

    private void initializeParentChildRelationShip(Feature child, String parentName, String currentLevel,RequestInfo requestInfo){

            BoundaryRelationshipRequest boundaryRelationshipRequest =
                    BoundaryRelationshipRequest.builder()
                            .requestInfo(requestInfo).boundaryRelationship(BoundaryRelation.builder()
                                    .code(extractNameFromFeature(child, currentLevel))
                                    .parent(parentName)
                                    .boundaryType(currentLevel)
                                    .hierarchyType(config.getHierarchyType())
                                    .build()).build();
            System.out.println("intitalize boundary realtionshiop"+boundaryRelationshipRequest);
            String url = config.getBoundaryServiceHost() + config.getBoundaryRelationshipCreateEndpoint();
            restTemplate.postForObject(url, boundaryRelationshipRequest, BoundaryRelationshipResponse.class);

    }

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
