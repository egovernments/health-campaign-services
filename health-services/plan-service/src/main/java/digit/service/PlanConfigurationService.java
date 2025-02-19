package digit.service;

import digit.config.Configuration;
import digit.kafka.Producer;
import digit.repository.impl.PlanConfigurationRepositoryImpl;
import digit.service.enrichment.EnrichmentService;
import digit.service.validator.PlanConfigurationValidator;
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

    private Producer producer;

    private EnrichmentService enrichmentService;

    private Configuration config;

    private PlanConfigurationValidator validator;

    private PlanConfigurationRepositoryImpl repository;

    private ResponseInfoFactory responseInfoFactory;

    public PlanConfigurationService(Producer producer, EnrichmentService enrichmentService, Configuration config
            , PlanConfigurationValidator validator, PlanConfigurationRepositoryImpl repository, ResponseInfoFactory responseInfoFactory) {
        this.producer = producer;
        this.enrichmentService = enrichmentService;
        this.config = config;
        this.validator = validator;
        this.repository = repository;
        this.responseInfoFactory = responseInfoFactory;
    }

    /**
     * Creates a new plan configuration based on the provided request.
     * @param request The request containing the plan configuration details.
     * @return The created plan configuration request.
     */
    public PlanConfigurationRequest create(PlanConfigurationRequest request) {
        enrichmentService.enrichPlanConfigurationBeforeValidation(request);
        validator.validateCreate(request);
        enrichmentService.enrichCreate(request);
        repository.create(request);
        return request;
    }

    /**
     * Searches for plan configurations based on the provided search criteria.
     * @param request The search request containing the criteria.
     * @return A list of plan configurations that match the search criteria.
     */
    public PlanConfigurationResponse search(PlanConfigurationSearchRequest request) {
        validator.validateSearchRequest(request);
        PlanConfigurationResponse response = PlanConfigurationResponse.builder().
                responseInfo(responseInfoFactory.createResponseInfoFromRequestInfo(request.getRequestInfo(), true))
                .planConfiguration(repository.search(request.getPlanConfigurationSearchCriteria()))
                .totalCount(repository.count(request.getPlanConfigurationSearchCriteria()))
                .build();

        return response;
    }

    /**
     * Updates an existing plan configuration based on the provided request.
     * @param request The request containing the updated plan configuration details.
     * @return The response containing the updated plan configuration.
     */
    public PlanConfigurationResponse update(PlanConfigurationRequest request) {
        validator.validateUpdateRequest(request);
        enrichmentService.enrichUpdate(request);
        repository.update(request);

        // Build and return response back to controller
        return PlanConfigurationResponse.builder()
                .responseInfo(ResponseInfoUtil.createResponseInfoFromRequestInfo(request.getRequestInfo(), Boolean.TRUE))
                .planConfiguration(Collections.singletonList(request.getPlanConfiguration()))
                .build();
    }
}