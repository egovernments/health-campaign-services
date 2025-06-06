package digit.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import digit.config.Configuration;
import digit.web.models.Arcgis.ArcgisResponse;
import lombok.extern.slf4j.Slf4j;
import org.egov.tracer.model.CustomException;
import org.egov.tracer.model.ServiceCallException;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Repository;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Map;

import static digit.config.ServiceConstants.*;

@Repository
@Slf4j
public class ServiceRequestRepository {

    private ObjectMapper mapper;

    private RestTemplate restTemplate;

    private Configuration config;

    public ServiceRequestRepository(ObjectMapper mapper, RestTemplate restTemplate,Configuration config) {
        this.mapper = mapper;
        this.restTemplate = restTemplate;
        this.config=config;
    }

    public Object fetchResult(StringBuilder uri, Object request) {
        mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        Object response = null;
        try {
            response = restTemplate.postForObject(uri.toString(), request, Map.class);
        } catch (HttpClientErrorException e) {
            log.error(EXTERNAL_SERVICE_EXCEPTION, e);
            throw new ServiceCallException(e.getResponseBodyAsString());
        } catch (Exception e) {
            log.error(SEARCHER_SERVICE_EXCEPTION, e);
        }

        return response;
    }

    /**
     * This method is for creating request to arcGis search based on countryName
     *
     * @param countryName
     */
    public void fetchArcGisData(String countryName) {
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
            throw new CustomException(ERROR_IN_ARC_SEARCH,"Error during arcGis search");
        }
    }
}