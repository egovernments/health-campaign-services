package digit.service;

import digit.config.Configuration;
import digit.kafka.Producer;
import digit.service.enrichment.EnrichmentService;
import digit.service.validator.PlanConfigurationValidator;
import digit.web.models.PlanConfigurationRequest;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class PlanConfigurationService {

    private Producer producer;

    private EnrichmentService enrichmentService;

    private Configuration config;

    private PlanConfigurationValidator validator;

    public PlanConfigurationService(Producer producer, EnrichmentService enrichmentService, Configuration config, PlanConfigurationValidator validator) {
        this.producer = producer;
        this.enrichmentService = enrichmentService;
        this.config = config;
        this.validator = validator;
    }

    public PlanConfigurationRequest create(PlanConfigurationRequest request) {
        enrichmentService.enrichCreate(request);
        validator.validateCreate(request);
        producer.push(config.getPlanConfigCreateTopic() ,request);
        return request;
    }
}