package digit.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import digit.config.Configuration;
import digit.web.models.facility.FacilityResponse;
import digit.web.models.facility.FacilitySearchCriteria;
import digit.web.models.facility.FacilitySearchRequest;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.request.RequestInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static digit.config.ServiceConstants.ERROR_WHILE_FETCHING_FROM_FACILITY;

@Slf4j
@Component
public class FacilityUtil {

    private RestTemplate restTemplate;
    private Configuration configs;
    @Autowired
    private ObjectMapper mapper;

    public FacilityUtil(RestTemplate restTemplate, Configuration configs) {
        this.restTemplate = restTemplate;
        this.configs = configs;
    }

    public FacilityResponse fetchFacilityData(RequestInfo requestInfo, String facilityId, String tenantId) {
        String baseUri = configs.getFacilityHost()+ configs.getFacilitySearchEndPoint();

        // Retrieve the limit and offset from the configuration
        int limit = configs.getDefaultLimit();
        int offset = configs.getDefaultOffset();

        // Use UriComponentsBuilder to construct the URI with query parameters
        String uri = UriComponentsBuilder.fromHttpUrl(baseUri)
                .queryParam("tenantId", tenantId)
                .queryParam("limit", limit)
                .queryParam("offset", offset)
                .toUriString();

        FacilitySearchRequest facilitySearchRequest = getSearchReq(requestInfo, facilityId);
        FacilityResponse facilityResponse = new FacilityResponse();
        Object response = new HashMap<>();
        try {
            // Use postForObject to send the request with the URI containing query params
            response = restTemplate.postForObject(uri, facilitySearchRequest, Map.class);
            facilityResponse = mapper.convertValue(response , FacilityResponse.class);
        } catch (Exception e) {
            log.error(ERROR_WHILE_FETCHING_FROM_FACILITY, e);
        }
        log.info(facilityResponse.toString());
        return facilityResponse;
    }

    private FacilitySearchRequest getSearchReq(RequestInfo requestInfo, String facilityId) {
        FacilitySearchCriteria searchCriteria = FacilitySearchCriteria.builder()
                .id(Collections.singletonList(facilityId))
                .build();

        return FacilitySearchRequest.builder()
                .requestInfo(requestInfo)
                .facilitySearchCriteria(searchCriteria)
                .build();
    }



}
