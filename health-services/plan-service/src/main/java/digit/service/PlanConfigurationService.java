package digit.service;

import digit.config.Configuration;
import digit.kafka.Producer;
import digit.repository.impl.PlanConfigurationRepositoryImpl;
import digit.service.enrichment.EnrichmentService;
import digit.service.validator.PlanConfigurationValidator;
import digit.web.models.PlanConfiguration;
import digit.web.models.PlanConfigurationRequest;

import digit.web.models.PlanConfigurationSearchRequest;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class PlanConfigurationService {

    private Producer producer;

    private EnrichmentService enrichmentService;

    private Configuration config;

    private PlanConfigurationValidator validator;

    private PlanConfigurationRepositoryImpl repository;

    public PlanConfigurationService(Producer producer, EnrichmentService enrichmentService, Configuration config
            , PlanConfigurationValidator validator, PlanConfigurationRepositoryImpl repository) {
        this.producer = producer;
        this.enrichmentService = enrichmentService;
        this.config = config;
        this.validator = validator;
        this.repository = repository;
    }

    public PlanConfigurationRequest create(PlanConfigurationRequest request) {
        enrichmentService.enrichCreate(request);
        validator.validateCreate(request);
        repository.create(request);
        return request;
    }

    public List<PlanConfiguration> search(PlanConfigurationSearchRequest request) {
        validator.validateSearchRequest(request);
        return repository.search(request.getPlanConfigurationSearchCriteria());
    }
}