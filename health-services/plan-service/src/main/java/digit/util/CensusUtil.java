package digit.util;

import digit.config.Configuration;
import digit.web.models.census.CensusResponse;
import digit.web.models.census.CensusSearchRequest;
import lombok.extern.slf4j.Slf4j;
import org.egov.tracer.model.CustomException;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.web.client.RestTemplate;

import static digit.config.ServiceConstants.*;

@Slf4j
@Component
public class CensusUtil {

    private RestTemplate restTemplate;

    private Configuration config;

    public CensusUtil(RestTemplate restTemplate, Configuration config) {
        this.restTemplate = restTemplate;
        this.config = config;
    }

    /**
     * This method fetches data from Census based on the given census search request.
     *
     * @param searchRequest The census search request containing the search criteria.
     * @return returns the census response.
     */
    public CensusResponse fetchCensusRecords(CensusSearchRequest searchRequest) {

        // Get census search uri
        String uri = getCensusUri().toString();

        CensusResponse censusResponse = null;
        try {
            censusResponse = restTemplate.postForObject(uri, searchRequest, CensusResponse.class);
        } catch (Exception e) {
            log.error(ERROR_WHILE_FETCHING_FROM_CENSUS, e);
        }

        if (CollectionUtils.isEmpty(censusResponse.getCensus())) {
            throw new CustomException(NO_CENSUS_FOUND_FOR_GIVEN_DETAILS_CODE, NO_CENSUS_FOUND_FOR_GIVEN_DETAILS_MESSAGE);
        }

        return censusResponse;
    }

    /**
     * Builds the census search uri.
     *
     * @return returns the complete uri for census search.
     */
    private StringBuilder getCensusUri() {
        return new StringBuilder().append(config.getCensusHost()).append(config.getCensusSearchEndPoint());
    }
}
