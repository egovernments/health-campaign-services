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

import static digit.config.ServiceConstants.*;

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
        this.config = config;
        this.restTemplate = restTemplate;
        this.arcgisUtil = arcgisUtil;
    }

    /**
     * This method processes the request to create the root and its children's data
     *
     * @param request
     * @return
     */
    public String createRootBoundaryData(GeopodeBoundaryRequest request) {
        String countryName = arcgisUtil.createRoot(request);
        return countryName;
    }




}
