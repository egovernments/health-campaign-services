package digit.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import digit.config.Configuration;
import digit.web.models.PlanFacilityRequest;
import digit.web.models.facility.FacilityResponse;
import digit.web.models.facility.FacilitySearchCriteria;
import digit.web.models.facility.FacilitySearchRequest;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.request.RequestInfo;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static digit.config.ServiceConstants.ERROR_WHILE_FETCHING_FROM_FACILITY;
import static digit.config.ServiceConstants.URI_TENANT_ID_PARAM;

@Slf4j
@Component
public class FacilityUtil {

    private RestTemplate restTemplate;
    private Configuration configs;
    private ObjectMapper mapper;

    public FacilityUtil(RestTemplate restTemplate, Configuration configs, ObjectMapper mapper) {
        this.restTemplate = restTemplate;
        this.configs = configs;
        this.mapper = mapper;
    }

    public FacilityResponse fetchFacilityData(PlanFacilityRequest planFacilityRequest) {
        String baseUri = configs.getFacilityHost()+ configs.getFacilitySearchEndPoint();

        // Retrieve tenantId from planFacilityRequest
        String tenantId = planFacilityRequest.getPlanFacility().getTenantId();

        // Retrieve the limit and offset from the configuration
        int limit = configs.getDefaultLimit();
        int offset = configs.getDefaultOffset();

        // Use UriComponentsBuilder to construct the URI with query parameters
        String uri = UriComponentsBuilder.fromHttpUrl(baseUri)
                .queryParam(URI_TENANT_ID_PARAM, tenantId)
                .queryParam("limit", limit)
                .queryParam("offset", offset)
                .toUriString();

        FacilitySearchRequest facilitySearchRequest = getFacilitySearchRequest(planFacilityRequest);
        FacilityResponse facilityResponse = new FacilityResponse();
        Object response = new HashMap<>();
        try {
            // Use postForObject to send the request with the URI containing query params
            response = restTemplate.postForObject(uri, facilitySearchRequest, Map.class);
            facilityResponse = mapper.convertValue(response , FacilityResponse.class);
        } catch (Exception e) {
            log.error(ERROR_WHILE_FETCHING_FROM_FACILITY, e);
        }
        return facilityResponse;
    }

    private FacilitySearchRequest getFacilitySearchRequest(PlanFacilityRequest planFacilityRequest) {
        // Retrieve facilityId,requestInfo from planFacilityRequest
        String facilityId = planFacilityRequest.getPlanFacility().getFacilityId();
        RequestInfo requestInfo = planFacilityRequest.getRequestInfo();

        FacilitySearchCriteria searchCriteria = FacilitySearchCriteria.builder()
                .id(Collections.singletonList(facilityId))
                .build();

        return FacilitySearchRequest.builder()
                .requestInfo(requestInfo)
                .facilitySearchCriteria(searchCriteria)
                .build();
    }



}
