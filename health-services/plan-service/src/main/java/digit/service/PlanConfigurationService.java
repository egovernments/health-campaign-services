package digit.service;

import digit.repository.PlanConfigurationRepository;
import digit.service.enrichment.EnrichmentService;
import digit.service.validator.PlanConfigurationValidator;
import digit.service.workflow.WorkflowService;
import digit.util.ResponseInfoFactory;
import digit.web.models.PlanConfigurationRequest;
import digit.web.models.PlanConfigurationResponse;
import digit.web.models.PlanConfigurationSearchRequest;

import java.util.Collections;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.utils.ResponseInfoUtil;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class PlanConfigurationService {

    private EnrichmentService enrichmentService;

    private PlanConfigurationValidator validator;

    private PlanConfigurationRepository repository;

    private ResponseInfoFactory responseInfoFactory;

    private WorkflowService workflowService;

    public PlanConfigurationService(EnrichmentService enrichmentService, PlanConfigurationValidator validator, PlanConfigurationRepository repository, ResponseInfoFactory responseInfoFactory, WorkflowService workflowService) {
        this.enrichmentService = enrichmentService;
        this.validator = validator;
        this.repository = repository;
        this.responseInfoFactory = responseInfoFactory;
        this.workflowService = workflowService;
    }

    /**
     * Creates a new plan configuration based on the provided request.
     *
     * @param request The request containing the plan configuration details.
     * @return The created plan configuration request.
     */
    public PlanConfigurationResponse create(PlanConfigurationRequest request) {
        enrichmentService.enrichPlanConfigurationBeforeValidation(request);
        validator.validateCreate(request);
        enrichmentService.enrichCreate(request);
        repository.create(request);

        return PlanConfigurationResponse.builder()
                .planConfiguration(Collections.singletonList(request.getPlanConfiguration()))
                .responseInfo(responseInfoFactory.createResponseInfoFromRequestInfo(request.getRequestInfo(), true))
                .build();
    }

    /**
     * Searches for plan configurations based on the provided search criteria.
     *
     * @param request The search request containing the criteria.
     * @return A list of plan configurations that match the search criteria.
     */
    public PlanConfigurationResponse search(PlanConfigurationSearchRequest request) {
        validator.validateSearchRequest(request);

        return PlanConfigurationResponse.builder().
                responseInfo(responseInfoFactory.createResponseInfoFromRequestInfo(request.getRequestInfo(), true))
                .planConfiguration(repository.search(request.getPlanConfigurationSearchCriteria()))
                .totalCount(repository.count(request.getPlanConfigurationSearchCriteria()))
                .build();
    }

    /**
     * Updates an existing plan configuration based on the provided request.
     *
     * @param request The request containing the updated plan configuration details.
     * @return The response containing the updated plan configuration.
     */
    public PlanConfigurationResponse update(PlanConfigurationRequest request) {
        validator.validateUpdateRequest(request);
        enrichmentService.enrichUpdate(request);
        workflowService.invokeWorkflowForStatusUpdate(request);
        repository.update(request);

        return PlanConfigurationResponse.builder()
                .responseInfo(ResponseInfoUtil.createResponseInfoFromRequestInfo(request.getRequestInfo(), Boolean.TRUE))
                .planConfiguration(Collections.singletonList(request.getPlanConfiguration()))
                .build();
    }
}