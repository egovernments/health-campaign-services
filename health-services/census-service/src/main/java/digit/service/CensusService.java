package digit.service;

import digit.util.ResponseInfoFactory;
import digit.web.models.CensusRequest;
import digit.web.models.CensusResponse;
import digit.web.models.CensusSearchRequest;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Service
public class CensusService {

    private ResponseInfoFactory responseInfoFactory;

    /**
     * Creates a new census record based on the provided request.
     *
     * @param request The request containing the census data.
     * @return The created census reponse.
     */
    public CensusResponse create(CensusRequest request) {
        CensusResponse response = CensusResponse.builder()
                .census(Collections.singletonList(request.getCensus()))
                .responseInfo(responseInfoFactory.createResponseInfoFromRequestInfo(request.getRequestInfo(), true))
                .build();
        return response;
    }

    /**
     * Searches for census record based on the provided search criteria.
     *
     * @param request The search request containing the criteria.
     * @return A list of census record that matches the search criteria.
     */
    public CensusResponse search(CensusSearchRequest request) {
        CensusResponse response = CensusResponse.builder()
                .responseInfo(responseInfoFactory.createResponseInfoFromRequestInfo(request.getRequestInfo(), true))
                .build();

        return response;
    }

    /**
     * Updates an existing census record based on the provided request.
     *
     * @param request The request containing the updated census data.
     * @return The updated census response.
     */
    public CensusResponse update(CensusRequest request) {
        CensusResponse response = CensusResponse.builder()
                .census(Collections.singletonList(request.getCensus()))
                .responseInfo(responseInfoFactory.createResponseInfoFromRequestInfo(request.getRequestInfo(), true))
                .build();
        return response;
    }
}
