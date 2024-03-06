package digit.repository.impl;

import digit.config.Configuration;
import digit.kafka.Producer;
import digit.repository.PlanConfigurationRepository;
import digit.web.models.PlanConfigurationRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@Slf4j
public class PlanConfigurationRepositoryImpl implements PlanConfigurationRepository {

    private Producer producer;
    private JdbcTemplate jdbcTemplate;
    private Configuration config;

    @Autowired
    public PlanConfigurationRepositoryImpl(Producer producer, JdbcTemplate jdbcTemplate,
                                           Configuration config) {
        this.producer = producer;
        this.jdbcTemplate = jdbcTemplate;
        this.config = config;
    }

    @Override
    public void create(PlanConfigurationRequest planConfigurationRequest) {
        producer.push(config.getPlanConfigCreateTopic(),planConfigurationRequest);
    }

    @Override
    public void update(PlanConfigurationRequest planConfigurationRequest) {
        producer.push(config.getPlanConfigUpdateTopic(),planConfigurationRequest);
    }
}
