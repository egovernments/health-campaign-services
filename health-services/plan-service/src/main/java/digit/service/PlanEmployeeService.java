package digit.service;

import digit.config.Configuration;
import digit.kafka.Producer;
import digit.repository.PlanEmployeeAssignmentRepository;
import digit.service.enrichment.PlanEmployeeAssignmentEnricher;
import digit.service.validator.PlanEmployeeAssignmentValidator;
import digit.util.ResponseInfoFactory;
import digit.web.models.*;
import org.egov.common.utils.ResponseInfoUtil;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Service
public class PlanEmployeeService {

    Producer producer;

    Configuration config;

    ResponseInfoFactory responseInfoFactory;

    PlanEmployeeAssignmentRepository repository;

    PlanEmployeeAssignmentEnricher enricher;

    PlanEmployeeAssignmentValidator validator;

    public PlanEmployeeService(Producer producer, Configuration config, ResponseInfoFactory responseInfoFactory, PlanEmployeeAssignmentRepository repository, PlanEmployeeAssignmentEnricher enricher, PlanEmployeeAssignmentValidator validator) {
        this.producer = producer;
        this.config = config;
        this.responseInfoFactory = responseInfoFactory;
        this.repository = repository;
        this.enricher = enricher;
        this.validator = validator;
    }

    /**
     * Creates a new plan employee assignment based on the provided request.
     *
     * @param request The request containing the plan employee assignment details.
     * @return The response containing the created plan employee assignment.
     */
    public PlanEmployeeAssignmentResponse create(PlanEmployeeAssignmentRequest request) {
        validator.validateCreate(request);
        enricher.enrichCreate(request);
        repository.create(request);

        return PlanEmployeeAssignmentResponse.builder()
                .planEmployeeAssignment(Collections.singletonList(request.getPlanEmployeeAssignment()))
                .responseInfo(responseInfoFactory.createResponseInfoFromRequestInfo(request.getRequestInfo(), Boolean.TRUE))
                .build();
    }

    /**
     * Searches for plan employee assignment based on the provided search criteria.
     *
     * @param request The search request containing the criteria.
     * @return A list of plan employee assignments that matches the search criteria.
     */
    public PlanEmployeeAssignmentResponse search(PlanEmployeeAssignmentSearchRequest request) {
        // Delegate search request to repository
        List<PlanEmployeeAssignment> planEmployeeAssignmentList = repository.search(request.getPlanEmployeeAssignmentSearchCriteria());

        // Build and return response back to controller
        return PlanEmployeeAssignmentResponse.builder()
                .responseInfo(responseInfoFactory.createResponseInfoFromRequestInfo(request.getRequestInfo(), Boolean.TRUE))
                .planEmployeeAssignment(planEmployeeAssignmentList)
                .totalCount(repository.count(request.getPlanEmployeeAssignmentSearchCriteria()))
                .build();
    }

    /**
     * Updates an existing plan employee assignment based on the provided request.
     *
     * @param request The request containing the updated plan employee assignment details.
     * @return The response containing the updated plan employee assignment.
     */
    public PlanEmployeeAssignmentResponse update(PlanEmployeeAssignmentRequest request) {
        validator.validateUpdate(request);
        enricher.enrichUpdate(request);
        repository.update(request);

        return PlanEmployeeAssignmentResponse.builder()
                .responseInfo(ResponseInfoUtil.createResponseInfoFromRequestInfo(request.getRequestInfo(), Boolean.TRUE))
                .planEmployeeAssignment(Collections.singletonList(request.getPlanEmployeeAssignment()))
                .build();
    }
}
