package digit.repository.impl;

import digit.config.Configuration;
import digit.kafka.Producer;
import digit.repository.PlanConfigurationRepository;
import digit.repository.querybuilder.PlanConfigQueryBuilder;
import digit.repository.rowmapper.PlanConfigRowMapper;
import digit.web.models.PlanConfiguration;
import digit.web.models.PlanConfigurationRequest;
import digit.web.models.PlanConfigurationSearchCriteria;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@Slf4j
public class PlanConfigurationRepositoryImpl implements PlanConfigurationRepository {

    private Producer producer;

    private JdbcTemplate jdbcTemplate;

    private Configuration config;

    private PlanConfigQueryBuilder planConfigQueryBuilder;

    private PlanConfigRowMapper planConfigRowMapper;

    public PlanConfigurationRepositoryImpl(Producer producer, JdbcTemplate jdbcTemplate,
                                           Configuration config, PlanConfigQueryBuilder planConfigQueryBuilder, PlanConfigRowMapper planConfigRowMapper) {
        this.producer = producer;
        this.jdbcTemplate = jdbcTemplate;
        this.config = config;
        this.planConfigQueryBuilder = planConfigQueryBuilder;
        this.planConfigRowMapper = planConfigRowMapper;
    }

    @Override
    public void create(PlanConfigurationRequest planConfigurationRequest) {
        producer.push(config.getPlanConfigCreateTopic(),planConfigurationRequest);
    }

    @Override
    public List<PlanConfiguration> search(PlanConfigurationSearchCriteria planConfigurationSearchCriteria) {
        List<Object> preparedStmtList = new ArrayList<>();
        String query = planConfigQueryBuilder.getPlanConfigSearchQuery(planConfigurationSearchCriteria, preparedStmtList);
        log.info("Plan Config query: " + query);
        log.info("preparedStmtList: " + preparedStmtList);
        return jdbcTemplate.query(query, preparedStmtList.toArray(), planConfigRowMapper);
    }

    @Override
    public void update(PlanConfigurationRequest planConfigurationRequest) {
        producer.push(config.getPlanConfigUpdateTopic(),planConfigurationRequest);
    }
}
