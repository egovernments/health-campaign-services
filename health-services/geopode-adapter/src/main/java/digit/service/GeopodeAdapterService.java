package digit.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import digit.util.ArcgisUtil;
import digit.web.models.boundaryService.*;
import lombok.extern.slf4j.Slf4j;
import digit.config.Configuration;
import digit.util.BoundaryUtil;
import digit.web.models.Arcgis.ArcgisRequest;
import digit.web.models.Arcgis.ArcgisResponse;
import digit.web.models.GeopdeHierarchyLevel;
import digit.web.models.GeopodeBoundaryRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

<<<<<<< HEAD
import java.net.URI;
import java.util.*;
=======
import java.util.*;
import java.util.stream.Collectors;

>>>>>>> a426ce93c8ba0af2c7a32739c191133af8188959
import static digit.config.ServiceConstants.RESPONSE_FROM_GEOPODE_API;

@Service
@Slf4j
public class GeopodeAdapterService {
    private Configuration config;
    private ObjectMapper objectMapper;
    private BoundaryUtil boundaryUtil;
    private RestTemplate restTemplate;
    private ArcgisUtil arcgisUtil;
    @Autowired
    private ObjectMapper mapper; // Inject ObjectMapper

    public GeopodeAdapterService(ObjectMapper objectMapper, BoundaryUtil boundaryUtil, Configuration config, RestTemplate restTemplate, ArcgisUtil arcgisUtil) {
        this.objectMapper = objectMapper;
        this.boundaryUtil = boundaryUtil;
        this.config=config;
        this.restTemplate=restTemplate;
        this.arcgisUtil=arcgisUtil;
    }

    /**
     *
     * @param request
     * @throws JsonProcessingException
     */
    public ResponseEntity<BoundaryResponse> createRootBoundaryData(GeopodeBoundaryRequest request) throws JsonProcessingException {
        ResponseEntity<BoundaryResponse> boundaryResponse=arcgisUtil.createRoot(request);
        return boundaryResponse;
    }

<<<<<<< HEAD
    /**
     *
     * @param request
     */
    public BoundaryHierarchyDefinitionResponse searchBoundaryHierarchyDefinition(BoundaryHierarchyDefinitionSearchRequest request){
        BoundaryHierarchyDefinitionResponse boundaryHierarchyDefinitionResponse=boundaryUtil.fetchBoundaryHierarchyDefinition(request);
        return boundaryHierarchyDefinitionResponse;
=======
        Map<String, String> typeCodeToFeature = new LinkedHashMap<>();
        List<GeopdeHierarchyLevel> hierarchyLevels = new ArrayList<>();

        // Fetches hierarchy from GeoPoDe api and creates hierarchy in Boundary service.
        geopodeHierarchyCreate(request, typeCodeToFeature, hierarchyLevels);

        // Fetches boundaries from GeoPoDe api and creates boundary tree in Boundary service.
        geopodeBoundaryCreate(request, typeCodeToFeature, hierarchyLevels);
>>>>>>> a426ce93c8ba0af2c7a32739c191133af8188959
    }

    /**
     *
     * @param typeCodeToFeature
     * @param hierarchyLevels
     */
    private void geopodeBoundaryCreate(GeopodeBoundaryRequest request, Map<String, String> typeCodeToFeature, List<GeopdeHierarchyLevel> hierarchyLevels) {
        Map<String, String> parentToChildMap = createParentToChildMap(hierarchyLevels);

        String rootHierarchy = hierarchyLevels.stream()
                .filter(level -> level.getParent() == null)
                .findFirst()
                .map(GeopdeHierarchyLevel::getTypeCode)
                .orElse(null);

    }

    /**
     * Fetches and processes boundary hierarchy data from the Geopode API response,
     * enriches the typeCode-to-feature mapping, and prepares the boundary hierarchy
     * structure to be created in the boundary service.
     *
     * @param request the original request containing metadata required for boundary hierarchy creation.
     * @param typeCodeToFeature a map to be populated with typeCode as key and corresponding feature as value.
     * @param hierarchyLevels an initially empty list that will be populated with parsed hierarchy levels.
     * @throws JsonProcessingException if the Geopode API response JSON cannot be parsed.
     */
    private void geopodeHierarchyCreate(GeopodeBoundaryRequest request, Map<String, String> typeCodeToFeature, List<GeopdeHierarchyLevel> hierarchyLevels) throws JsonProcessingException {
        //TODO : Create request to geopode API to get hierarchy

        String jsonString = RESPONSE_FROM_GEOPODE_API;
        hierarchyLevels = objectMapper.readValue(jsonString, new TypeReference<List<GeopdeHierarchyLevel>>() {});
        List<BoundaryTypeHierarchy> boundaryHierarchyList = new LinkedList<>();

        hierarchyLevels.forEach(hierarchyLevel -> {
            // Enrich typeCodeToFeature map
            typeCodeToFeature.put(hierarchyLevel.getTypeCode(), hierarchyLevel.getFeature());

            BoundaryTypeHierarchy boundaryTypeHierarchy = BoundaryTypeHierarchy.builder()
                    .parentBoundaryType(hierarchyLevel.getParent())
                    .boundaryType(hierarchyLevel.getTypeCode())
                    .active(Boolean.TRUE)
                    .build();

            boundaryHierarchyList.add(boundaryTypeHierarchy);
        });
<<<<<<< HEAD
        boundaryUtil.createBoundaryHierarchy(request, boundaryHierarchyList);
    }

    public ArcgisResponse searchBoundary(ArcgisRequest request) {
        URI uri = UriComponentsBuilder.fromHttpUrl(config.getArcgisEndpoint())
                .queryParam("where", request.getWhere())          // e.g., ADM0_NAME='NIGERIA'
                .queryParam("outFields", request.getOutFields())  // e.g., ADM1_NAME
                .queryParam("f", request.getF())                  // e.g., json
                .build()
                .encode()
                .toUri();

        ArcgisResponse argresponse = new ArcgisResponse();
        try {

            // Exchange method allows full control, and this way we use a dynamic Map type
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    uri,
                    HttpMethod.GET,
                    null, // no request body
                    new ParameterizedTypeReference<Map<String, Object>>() {}
            );

            argresponse = mapper.convertValue(response.getBody(), ArcgisResponse.class);


        } catch (Exception e) {
            log.error("ERROR_IN_ARC_SEARCH", e);
        }
        return argresponse;
=======

        // Call boundary service hierarchy create api to create hierarchy in boundary service.
        boundaryUtil.createBoundaryHierarchy(request, boundaryHierarchyList);
    }

    /**
     * Creates a mapping of parent hierarchy type codes to their corresponding child type codes
     * from a list of GeopdeHierarchyLevel objects.
     *
     * @param hierarchyLevels the list of hierarchy levels to process.
     * @return a map where each key is a parent type code and the value is its corresponding child type code.
     */
    private Map<String, String> createParentToChildMap(List<GeopdeHierarchyLevel> hierarchyLevels) {
        return hierarchyLevels.stream()
                .filter(h -> h.getParent() != null)
                .collect(Collectors.toMap(
                        GeopdeHierarchyLevel::getParent,
                        GeopdeHierarchyLevel::getTypeCode));
>>>>>>> a426ce93c8ba0af2c7a32739c191133af8188959
    }
}
