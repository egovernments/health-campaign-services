package digit.service;

import digit.repository.CensusRepository;
import digit.service.enrichment.CensusEnrichment;
import digit.service.validator.CensusValidator;
import digit.util.ResponseInfoFactory;
import digit.web.models.Census;
import digit.web.models.CensusRequest;
import digit.web.models.CensusResponse;
import digit.web.models.CensusSearchRequest;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Service
public class CensusService {

    private ResponseInfoFactory responseInfoFactory;

    private CensusRepository repository;

    private CensusValidator validator;

    private CensusEnrichment enrichment;

    public CensusService(ResponseInfoFactory responseInfoFactory, CensusRepository repository, CensusValidator validator, CensusEnrichment enrichment) {
        this.responseInfoFactory = responseInfoFactory;
        this.repository = repository;
        this.validator = validator;
        this.enrichment = enrichment;
    }

    /**
     * Creates a new census record based on the provided request.
     *
     * @param request The request containing the census data.
     * @return The created census reponse.
     */
    public CensusResponse create(CensusRequest request) {
        validator.validateCreate(request); // Validate census create request
        enrichment.enrichCreate(request); // Enrich census create request
        repository.create(request); // Delegate creation request to repository
        return CensusResponse.builder()
                .census(Collections.singletonList(request.getCensus()))
                .responseInfo(responseInfoFactory.createResponseInfoFromRequestInfo(request.getRequestInfo(), true))
                .build();
    }

    /**
     * Searches for census record based on the provided search criteria.
     *
     * @param request The search request containing the criteria.
     * @return A list of census record that matches the search criteria.
     */
    public CensusResponse search(CensusSearchRequest request) {
        validator.validateSearch(request); // Validate census search request
        List<Census> censusList = repository.search(request.getCensusSearchCriteria()); // Delegate search request to repository
        return CensusResponse.builder()
                .responseInfo(responseInfoFactory.createResponseInfoFromRequestInfo(request.getRequestInfo(), true))
                .census(censusList)
                .build();
    }

    /**
     * Updates an existing census record based on the provided request.
     *
     * @param request The request containing the updated census data.
     * @return The updated census response.
     */
    public CensusResponse update(CensusRequest request) {
        validator.validateUpdate(request); // Validate census update request
        enrichment.enrichUpdate(request); // Enrich census update request
        repository.update(request); // Delegate update request to repository
        return CensusResponse.builder()
                .census(Collections.singletonList(request.getCensus()))
                .responseInfo(responseInfoFactory.createResponseInfoFromRequestInfo(request.getRequestInfo(), true))
                .build();
    }
}
