package org.egov.transformer.aggregator.service;

import lombok.extern.slf4j.Slf4j;
import org.egov.transformer.aggregator.config.ServiceConfiguration;
import org.egov.transformer.aggregator.repository.ElasticSearchRepository;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class ProjectBeneficiaryService {

    private static final String PROJECT_BENEFICIARY_KEY = "projectBeneficiaryId";
    private final ServiceConfiguration config;
    private final ElasticSearchRepository elasticSearchRepository;

    public ProjectBeneficiaryService(ServiceConfiguration config, ElasticSearchRepository elasticSearchRepository) {
        this.config = config;
        this.elasticSearchRepository = elasticSearchRepository;
    }



}
