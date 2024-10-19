package digit.service;

import digit.repository.CensusRepository;
import digit.service.enrichment.CensusEnrichment;
import digit.service.enrichment.CensusTimeframeEnrichment;
import digit.service.validator.CensusValidator;
import digit.service.workflow.WorkflowService;
import digit.util.ResponseInfoFactory;
import digit.web.models.CensusRequest;
import digit.web.models.CensusResponse;
import digit.web.models.CensusSearchRequest;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Service
public class CensusService {

    private ResponseInfoFactory responseInfoFactory;

    private CensusRepository repository;

    private CensusValidator validator;

    private CensusEnrichment enrichment;

    private CensusTimeframeEnrichment timeframeEnrichment;

    private WorkflowService workflow;

    public CensusService(ResponseInfoFactory responseInfoFactory, CensusRepository repository, CensusValidator validator, CensusEnrichment enrichment, CensusTimeframeEnrichment timeframeEnrichment, WorkflowService workflow) {
        this.responseInfoFactory = responseInfoFactory;
        this.repository = repository;
        this.validator = validator;
        this.enrichment = enrichment;
        this.timeframeEnrichment = timeframeEnrichment;
        this.workflow = workflow;
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
        timeframeEnrichment.enrichPreviousTimeframe(request); // Enrich timeframe for previous census
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
        return CensusResponse.builder()
                .responseInfo(responseInfoFactory.createResponseInfoFromRequestInfo(request.getRequestInfo(), true))
                .census(repository.search(request.getCensusSearchCriteria()))
                .totalCount(repository.count(request.getCensusSearchCriteria()))
                .statusCount(repository.statusCount(request.getCensusSearchCriteria()))
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
        workflow.invokeWorkflowForStatusUpdate(request); // Call workflow transition API for status update
        repository.update(request); // Delegate update request to repository
        return CensusResponse.builder()
                .census(Collections.singletonList(request.getCensus()))
                .responseInfo(responseInfoFactory.createResponseInfoFromRequestInfo(request.getRequestInfo(), true))
                .build();
    }
}
