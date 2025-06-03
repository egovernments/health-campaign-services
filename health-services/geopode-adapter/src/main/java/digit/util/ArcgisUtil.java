package digit.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import digit.config.Configuration;
import digit.web.models.Arcgis.ArcgisResponse;
import digit.web.models.GeopodeBoundaryRequest;
import digit.web.models.boundaryService.*;
import digit.web.models.mdmsV2.MdmsCriteriaReqV2;
import digit.web.models.mdmsV2.MdmsCriteriaV2;
import digit.web.models.mdmsV2.MdmsResponseV2;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.request.RequestInfo;
import org.egov.tracer.model.CustomException;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static digit.config.ServiceConstants.*;

@Component
@Slf4j
public class ArcgisUtil {
    private Configuration config;
    private RestTemplate restTemplate;
    private MdmsV2Util mdmsV2Util;
    private ObjectMapper mapper;
    private ChildBoundaryCreationUtil childBoundaryCreationUtil;

    public ArcgisUtil(Configuration config, RestTemplate restTemplate, MdmsV2Util mdmsV2Util, ChildBoundaryCreationUtil childBoundaryCreationUtil,ObjectMapper mapper) {
        this.config = config;
        this.restTemplate = restTemplate;
        this.mdmsV2Util=mdmsV2Util;
        this.childBoundaryCreationUtil = childBoundaryCreationUtil;
        this.mapper=mapper;

    }

    public ResponseEntity<BoundaryResponse> createRoot(GeopodeBoundaryRequest request) {
        MdmsResponseV2 mdmsResponse = fetchMdmsData(request);
        Optional<String> countryName = extractCountryNameFromMdms(mdmsResponse, request.getGeopodeBoundary().getISOCode());

        if (countryName.isEmpty()) {
            throw new CustomException("COUNTRY_NAME_NOT_FOUND", "No country found for ISO code: " + request.getGeopodeBoundary().getISOCode());
        }

        fetchArcGisData(countryName.get()); // For now we are not using the response (e.g., geometry/rings)

        BoundaryRequest boundaryRequest = buildBoundaryRequest(countryName, request.getRequestInfo());
        BoundaryResponse response = new BoundaryResponse();
//        sendBoundaryRequest(boundaryRequest);
        childBoundaryCreationUtil.createChildrenAsync(request, countryName.get());
        return ResponseEntity.ok(response);
    }


     MdmsCriteriaReqV2 buildMdmsV2RequestForRoot(GeopodeBoundaryRequest request) {
        return MdmsCriteriaReqV2.builder()
                .requestInfo(request.getRequestInfo())
                .mdmsCriteriaV2(MdmsCriteriaV2.builder()
                        .tenantId(config.getTenantId())
                        .schemaCode(config.getSchemaCode())
                        .offset(Integer.parseInt(config.getDefaultOffset()))
                        .limit(Integer.parseInt(config.getDefaultLimit()))
                        .build())
                .build();
    }

    private MdmsResponseV2 fetchMdmsData(GeopodeBoundaryRequest request) {
        MdmsCriteriaReqV2 mdmsCriteriaReqV2 = buildMdmsV2RequestForRoot(request);
        String url = config.getMdmsHost() + config.getMdmsV2EndPoint();

        try {
            return restTemplate.postForObject(url, mdmsCriteriaReqV2, MdmsResponseV2.class);
        } catch (Exception e) {
            log.error(NO_MDMS_DATA_FOUND_FOR_GIVEN_TENANT_ISO_CODE, e);
            throw new CustomException("MDMS_FETCH_FAILED", "Failed to fetch MDMS data");
        }
    }


    public BoundaryRequest buildBoundaryRequest(Optional<String> countryName, RequestInfo requestInfo){
        return BoundaryRequest.builder()
                .requestInfo(requestInfo)
                .boundary(List.of(
                        Boundary.builder()
                                .code(countryName.orElse(null))
                                .tenantId(config.getTenantId())
                                .build()
                ))
                .build();
    }

    private Optional<String> extractCountryNameFromMdms(MdmsResponseV2 mdmsResponse, String targetIsoCode) {
        return mdmsResponse.getMdms().stream()
                .map(mdms -> mdms.getData())
                .filter(data -> data != null && data.has("isoCode") && targetIsoCode.equalsIgnoreCase(data.get("isoCode").asText()))
                .map(data -> data.has("name") ? data.get("name").asText() : null)
                .filter(Objects::nonNull)
                .findFirst();
    }

    private void fetchArcGisData(String countryName) {
        URI uri = UriComponentsBuilder.fromHttpUrl(config.getArcgisEndpoint())
                .queryParam("where", "ADM0_NAME='" + countryName + "'")
                .queryParam("outFields", COUNTRY_OUTFIELDS)
                .queryParam("f", FORMAT_VALUE)
                .queryParam("resultRecordCount", 1)
                .build().encode().toUri();

        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    uri, HttpMethod.GET, null, new ParameterizedTypeReference<>() {}
            );
            ArcgisResponse arcgisResponse = mapper.convertValue(response.getBody(), ArcgisResponse.class);

            // Currently not used, but could extract geometry here
        } catch (Exception e) {
            log.error("ERROR_IN_ARC_SEARCH", e);
        }
    }

    public BoundaryResponse sendBoundaryRequest(BoundaryRequest boundaryRequest) {
        //        List<List<List<Double>>> rings=null;
        //        // Access geometry
        //        if (arcresponse.getFeatures() != null && !arcresponse.getFeatures().isEmpty()) {
        //            Geometry geometry = arcresponse.getFeatures().get(0).getGeometry();  // First feature
        //            rings = geometry.getRings();  // Actual coordinates
        //        }
        //        ObjectMapper mapper = new ObjectMapper();
        //        JsonNode ringsNode = mapper.valueToTree(rings);
        String url = config.getBoundaryServiceHost() + config.getBoundaryEntityCreateEndpoint();

        try {
            return restTemplate.postForObject(url, boundaryRequest, BoundaryResponse.class);
        } catch (Exception e) {
            log.error(ERROR_FETCHING_FROM_BOUNDARY, e);
            throw new CustomException("BOUNDARY_CREATION_FAILED", "Failed to create boundary");
        }
    }



}
