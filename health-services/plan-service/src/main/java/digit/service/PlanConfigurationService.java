package digit.service;

import digit.repository.PlanConfigurationRepository;
import digit.service.enrichment.EnrichmentService;
import digit.service.validator.PlanConfigurationValidator;
import digit.service.validator.WorkflowValidator;
import digit.service.workflow.WorkflowService;
import digit.util.CommonUtil;
import digit.util.ResponseInfoFactory;
import digit.web.models.PlanConfiguration;
import digit.web.models.PlanConfigurationRequest;
import digit.web.models.PlanConfigurationResponse;
import digit.web.models.PlanConfigurationSearchRequest;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.utils.ResponseInfoUtil;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Service
@Slf4j
public class PlanConfigurationService {

    private EnrichmentService enrichmentService;

    private PlanConfigurationValidator validator;

    private PlanConfigurationRepository repository;

    private ResponseInfoFactory responseInfoFactory;

    private WorkflowValidator workflowValidator;

    private WorkflowService workflowService;

    private CommonUtil commonUtil;

    public PlanConfigurationService(EnrichmentService enrichmentService, PlanConfigurationValidator validator, PlanConfigurationRepository repository, ResponseInfoFactory responseInfoFactory, WorkflowService workflowService, WorkflowValidator workflowValidator, CommonUtil commonUtil) {
        this.enrichmentService = enrichmentService;
        this.validator = validator;
        this.repository = repository;
        this.responseInfoFactory = responseInfoFactory;
        this.workflowService = workflowService;
        this.workflowValidator = workflowValidator;
        this.commonUtil = commonUtil;
    }

    /**
     * Creates a new plan configuration based on the provided request.
     *
     * @param request The request containing the plan configuration details.
     * @return The created plan configuration request.
     */
    public PlanConfigurationResponse create(PlanConfigurationRequest request) {
        // Sets active status to true for Files, Operations, Assumptions, and Resource Mappings without an ID before validation.
        enrichmentService.enrichPlanConfigurationBeforeValidation(request);

        // Validates plan configuration create request
        validator.validateCreate(request);

        // Enriches plan configuration create request
        enrichmentService.enrichCreate(request);

        // Delegate creation request to repository
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
        // Delegate search request to repository
        List<PlanConfiguration> planConfigurations = repository.search(request.getPlanConfigurationSearchCriteria());

        // Build and return response back to controller
        return PlanConfigurationResponse.builder()
                .responseInfo(responseInfoFactory.createResponseInfoFromRequestInfo(request.getRequestInfo(), true))
                .planConfiguration(planConfigurations)
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
        // Validates plan configuration update request
        validator.validateUpdateRequest(request);

        // Enriches plan configuration update request
        enrichmentService.enrichUpdate(request);

        // Validates census/plan/facility catchment data on the basis of workflow action
        workflowValidator.validateWorkflow(request);

        // Call workflow transition API for status update
        workflowService.invokeWorkflowForStatusUpdate(request);

        // Delegate updation request to repository
        repository.update(request);

        // Build and return response back to controller
        return PlanConfigurationResponse.builder()
                .responseInfo(ResponseInfoUtil.createResponseInfoFromRequestInfo(request.getRequestInfo(), Boolean.TRUE))
                .planConfiguration(Collections.singletonList(request.getPlanConfiguration()))
                .build();
    }
}