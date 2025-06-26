package digit.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import digit.config.Configuration;
import digit.repository.ServiceRequestRepository;
import digit.web.models.Arcgis.ArcgisRequest;
import digit.web.models.Arcgis.ArcgisResponse;
import digit.web.models.GeopodeBoundaryRequest;
import digit.web.models.boundaryService.*;
import digit.web.models.mdmsV2.MdmsCriteriaReqV2;
import digit.web.models.mdmsV2.MdmsCriteriaV2;
import digit.web.models.mdmsV2.MdmsResponseV2;
import lombok.extern.slf4j.Slf4j;
import org.egov.tracer.model.CustomException;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Map;
import java.util.Objects;

import static digit.config.ServiceConstants.*;

@Component
@Slf4j
public class ArcgisUtil {

    private Configuration config;

    private RestTemplate restTemplate;

    private MdmsV2Util mdmsV2Util;

    private BoundaryUtil boundaryUtil;

    private ServiceRequestRepository serviceRequestRepository;

    private ChildBoundaryCreationUtil childBoundaryCreationUtil;

    private ObjectMapper objectMapper;

    public ArcgisUtil(Configuration config, RestTemplate restTemplate, MdmsV2Util mdmsV2Util, BoundaryUtil boundaryUtil, ServiceRequestRepository serviceRequestRepository, ChildBoundaryCreationUtil childBoundaryCreationUtil,ObjectMapper objectMapper) {
        this.config = config;
        this.restTemplate = restTemplate;
        this.mdmsV2Util = mdmsV2Util;
        this.boundaryUtil = boundaryUtil;
        this.serviceRequestRepository = serviceRequestRepository;
        this.childBoundaryCreationUtil = childBoundaryCreationUtil;
        this.objectMapper=objectMapper;
    }

    /**
     * This method creates root and initializes the children
     *
     * @param request
     * @return
     */
    public String createRoot(GeopodeBoundaryRequest request) {
        MdmsResponseV2 mdmsResponse = mdmsV2Util.fetchMdmsDataForIsoCode(request);
        String countryName = extractCountryNameFromMdms(mdmsResponse, request.getGeopodeBoundary().getISOCode());

        if (countryName.isEmpty()) {
            throw new CustomException(COUNTRY_NAME_NOT_FOUND, "No country found for ISO code: " + request.getGeopodeBoundary().getISOCode());
        }

        serviceRequestRepository.fetchArcGisData(countryName); //TODO: Add geometry in from response (e.g., geometry/rings)
        BoundaryRequest boundaryRequest = boundaryUtil.buildBoundaryRequest(countryName, config.getTenantId(), request.getRequestInfo());
        childBoundaryCreationUtil.createChildrenAsync(request, countryName);

        // return response with status 200 and message

        return countryName;
    }

    /**
     * This method processes the request to search using arcgis queries
     *
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
                    new ParameterizedTypeReference<Map<String, Object>>() {
                    }
            );
            argresponse = objectMapper.convertValue(response.getBody(), ArcgisResponse.class);

        } catch (Exception e) {
            log.error("ERROR_IN_ARC_SEARCH", e);
            throw new CustomException(ERROR_IN_ARC_SEARCH,"Error when fetching from arcgis api");
        }
        return argresponse;
    }

    public URI searchArcgisRequestBuilder(ArcgisRequest request) {
        return UriComponentsBuilder.fromHttpUrl(config.getArcgisHost() + config.getArcgisEnpoint())
                .queryParam(QUERY_PARAM_WHERE, request.getWhere())          // e.g., ADM0_NAME='NIGERIA'
                .queryParam(QUERY_PARAM_OUT_FIELDS, request.getOutFields())  // e.g., ADM1_NAME
                .queryParam(QUERY_PARAM_FORMAT, request.getF())                  // e.g., json
                .build()
                .encode()
                .toUri();
    }

    /**
     * This method is for getting rootName from mdms response
     *
     * @param mdmsResponse
     * @param targetIsoCode
     * @return
     */
    private String extractCountryNameFromMdms(MdmsResponseV2 mdmsResponse, String targetIsoCode) {
        try {
            return mdmsResponse.getMdms().stream()
                    .map(mdms -> mdms.getData()) // Get data node from each MDMS entry
                    .filter(data -> data != null
                            && data.has(MDMS_ISO_CODE)
                            && targetIsoCode.equalsIgnoreCase(data.get(MDMS_ISO_CODE).asText())) // Match ISO code
                    .map(data -> data.has(MDMS_NAME) ? data.get(MDMS_NAME).asText() : null) // Extract name if present
                    .filter(Objects::nonNull) // Filter out nulls
                    .findFirst() // Take the first match
                    .orElseThrow(() -> new CustomException(
                            "COUNTRY_NAME_NOT_FOUND",
                            "No country found for ISO code: " + targetIsoCode
                    ));
        } catch (Exception e) {
            // Log technical error and rethrow a clean custom exception
            log.error("Error while extracting country name from MDMS for ISO code: {}", targetIsoCode, e);
            throw new CustomException(
                    "MDMS_EXTRACTION_ERROR",
                    "Failed to extract country name from MDMS for ISO code: " + targetIsoCode
            );
        }
    }


}
