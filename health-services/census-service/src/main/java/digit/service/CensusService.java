package digit.service;

import digit.repository.CensusRepository;
import digit.service.enrichment.CensusEnrichment;
import digit.service.enrichment.CensusTimeframeEnrichment;
import digit.service.validator.CensusValidator;
import digit.service.workflow.WorkflowService;
import digit.util.ResponseInfoFactory;
import digit.web.models.BulkCensusRequest;
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

        // Validate census create request
        validator.validateCreate(request);

        // Enrich census create request
        enrichment.enrichCreate(request);

        // Enrich timeframe for previous census
        timeframeEnrichment.enrichPreviousTimeframe(request);

        // Delegate creation request to repository
        repository.create(request);

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

        // Validate census update request
        validator.validateUpdate(request);

        // Enrich census update request
        enrichment.enrichUpdate(request);

        // Call workflow transition API for status update
        workflow.invokeWorkflowForStatusUpdate(request);

        // Delegate update request to repository
        repository.update(request);

        return CensusResponse.builder()
                .census(Collections.singletonList(request.getCensus()))
                .responseInfo(responseInfoFactory.createResponseInfoFromRequestInfo(request.getRequestInfo(), true))
                .build();
    }

    public CensusResponse bulkUpdate(BulkCensusRequest request) {

        // Validate census bulk update request
        validator.validateBulkUpdate(request);

        // Call workflow transition for updating status and assignee
        workflow.invokeWorkflowForStatusUpdate(request);

        // Delegate bulk update request to repository
        repository.bulkUpdate(request);

        return CensusResponse.builder()
                .census(request.getCensus())
                .responseInfo(responseInfoFactory.createResponseInfoFromRequestInfo(request.getRequestInfo(), true))
                .build();
    }
}
