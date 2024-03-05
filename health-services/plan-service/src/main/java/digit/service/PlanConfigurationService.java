package digit.service;

import digit.config.Configuration;
import digit.validators.PlanConfigurationValidator;
import digit.web.models.PlanConfigurationRequest;

import org.egov.common.producer.Producer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class PlanConfigurationService {

    @Autowired
    private final Producer producer;

    @Autowired
    private final EnrichmentService enrichmentService;

    @Autowired
    private final Configuration config;

    @Autowired
    private final PlanConfigurationValidator validator;

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
        return new PlanConfigurationRequest();
    }
}