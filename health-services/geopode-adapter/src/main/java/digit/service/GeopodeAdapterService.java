package digit.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
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

import java.net.URI;
import java.util.*;
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
    public BoundaryResponse createRootBoundaryData(GeopodeBoundaryRequest request) throws JsonProcessingException {
        BoundaryResponse boundaryResponse=arcgisUtil.createRoot(request);
        return boundaryResponse;
    }

    /**
     *
     * @param request
     */
    public BoundaryHierarchyDefinitionResponse searchBoundaryHierarchyDefinition(BoundaryHierarchyDefinitonSearchRequest request){
        BoundaryHierarchyDefinitionResponse boundaryHierarchyDefinitionResponse=boundaryUtil.fetchBoundaryHierarchyDefinition(request);
        return boundaryHierarchyDefinitionResponse;
    }

    /**
     *
     * @param request
     * @param typeCodeToFeature
     */
    private void geopodeHierarchyCreate(GeopodeBoundaryRequest request, Map<String, String> typeCodeToFeature) throws JsonProcessingException {
        //TODO Create request to geopode API to get hierarchy

        String jsonString = RESPONSE_FROM_GEOPODE_API;
        List<GeopdeHierarchyLevel> hierarchyLevels = objectMapper.readValue(jsonString, new TypeReference<List<GeopdeHierarchyLevel>>() {});
        List<BoundaryTypeHierarchy> boundaryHierarchyList = new LinkedList<>();

        hierarchyLevels.forEach(hierarchyLevel -> {
            typeCodeToFeature.put(hierarchyLevel.getTypeCode(), hierarchyLevel.getFeature());

            BoundaryTypeHierarchy boundaryTypeHierarchy = BoundaryTypeHierarchy.builder()
                    .parentBoundaryType(hierarchyLevel.getParent())
                    .boundaryType(hierarchyLevel.getTypeCode())
                    .active(Boolean.TRUE)
                    .build();

            boundaryHierarchyList.add(boundaryTypeHierarchy);
        });
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
    }
}
