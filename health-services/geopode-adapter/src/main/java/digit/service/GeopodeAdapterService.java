package digit.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import digit.util.ArcgisUtil;
import digit.web.models.boundaryService.*;
import lombok.extern.slf4j.Slf4j;
import digit.config.Configuration;
import digit.util.BoundaryUtil;
import digit.web.models.Arcgis.ArcgisRequest;
import digit.web.models.Arcgis.ArcgisResponse;
import digit.web.models.GeopodeBoundaryRequest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.*;

@Service
@Slf4j
public class GeopodeAdapterService {

    private Configuration config;

    private ObjectMapper objectMapper;

    private BoundaryUtil boundaryUtil;

    private RestTemplate restTemplate;

    private ArcgisUtil arcgisUtil;

    public GeopodeAdapterService(ObjectMapper objectMapper, BoundaryUtil boundaryUtil, Configuration config, RestTemplate restTemplate, ArcgisUtil arcgisUtil) {
        this.objectMapper = objectMapper;
        this.boundaryUtil = boundaryUtil;
        this.config=config;
        this.restTemplate=restTemplate;
        this.arcgisUtil=arcgisUtil;
    }

    /**
     * This method processes the request to create the root and its children's data
     * @param request
     * @return
     */
    public ResponseEntity<String> createRootBoundaryData(GeopodeBoundaryRequest request)  {
        ResponseEntity<String> boundaryResponse=arcgisUtil.createRoot(request);
        return boundaryResponse;
    }

    /**
     * This method processes the request to search boundaryHierarchy definition's
     * @param request
     * @return
     */
    public BoundaryHierarchyDefinitionResponse searchBoundaryHierarchyDefinition(BoundaryHierarchyDefinitionSearchRequest request){
        BoundaryHierarchyDefinitionResponse boundaryHierarchyDefinitionResponse=boundaryUtil.fetchBoundaryHierarchyDefinition(request);
        return boundaryHierarchyDefinitionResponse;
    }

    /**
     * This method processes the request to search using arcgis queries
     * @param request
     * @return
     */
    public ArcgisResponse searchBoundary(ArcgisRequest request) {
        URI uri = searchArcgisRequestBuilder(request);

        ArcgisResponse argresponse = new ArcgisResponse();
        try {

            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    uri,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<Map<String, Object>>() {}
            );

            return argresponse = objectMapper.convertValue(response.getBody(), ArcgisResponse.class);

        } catch (Exception e) {
            log.error("ERROR_IN_ARC_SEARCH", e);
        }
        return argresponse;
    }

    public URI searchArcgisRequestBuilder(ArcgisRequest request){
        return UriComponentsBuilder.fromHttpUrl(config.getArcgisEndpoint())
                .queryParam("where", request.getWhere())          // e.g., ADM0_NAME='NIGERIA'
                .queryParam("outFields", request.getOutFields())  // e.g., ADM1_NAME
                .queryParam("f", request.getF())                  // e.g., json
                .build()
                .encode()
                .toUri();
    }
}
