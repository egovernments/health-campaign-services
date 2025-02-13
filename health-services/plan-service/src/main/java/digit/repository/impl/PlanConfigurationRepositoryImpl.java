package digit.repository.impl;

import digit.config.Configuration;
import digit.kafka.Producer;
import digit.repository.PlanConfigurationRepository;
import digit.repository.querybuilder.PlanConfigQueryBuilder;
import digit.repository.rowmapper.PlanConfigRowMapper;
import digit.util.CommonUtil;
import digit.util.ResponseInfoFactory;
import digit.web.models.PlanConfiguration;
import digit.web.models.PlanConfigurationRequest;
import digit.web.models.PlanConfigurationSearchCriteria;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.SingleColumnRowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.util.CollectionUtils;

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

    /**
     * Pushes a new plan configuration to persister kafka topic.
     * @param planConfigurationRequest The request containing the plan configuration details.
     */
    @Override
    public void create(PlanConfigurationRequest planConfigurationRequest) {
        producer.push(config.getPlanConfigCreateTopic(), planConfigurationRequest);
    }

    /**
     * Searches for plan configurations based on the provided search criteria.
     * @param planConfigurationSearchCriteria The criteria to use for searching plan configurations.
     * @return A list of plan configurations that match the search criteria.
     */
    @Override
    public List<PlanConfiguration> search(PlanConfigurationSearchCriteria planConfigurationSearchCriteria) {
        // Fetch plan ids from database
        List<String> planConfigIds = queryDatabaseForPlanConfigIds(planConfigurationSearchCriteria);

        // Return empty list back as response if no plan ids are found
        if(CollectionUtils.isEmpty(planConfigIds)) {
        	log.info("No plan config ids found for provided plan configuration search criteria.");
            return new ArrayList<>();
        }

        return searchPlanConfigsByIds(planConfigIds);
    }

    /**
     * Counts the number of plan configurations based on the provided search criteria.
     *
     * @param planConfigurationSearchCriteria The search criteria for filtering plan configurations.
     * @return The total count of plan configurations matching the search criteria.
     */
    public Integer count(PlanConfigurationSearchCriteria planConfigurationSearchCriteria) {
        List<Object> preparedStmtList = new ArrayList<>();
        String query = planConfigQueryBuilder.getPlanConfigCountQuery(planConfigurationSearchCriteria, preparedStmtList);
        return jdbcTemplate.queryForObject(query, preparedStmtList.toArray(), Integer.class);
    }

    /**
     * Pushes an updated existing plan configuration to persister kafka topic.
     * @param planConfigurationRequest The request containing the updated plan configuration details.
     */
    @Override
    public void update(PlanConfigurationRequest planConfigurationRequest) {
        producer.push(config.getPlanConfigUpdateTopic(), planConfigurationRequest);
    }

    /**
     * Helper method to query database for plan ids based on the provided search criteria.
     * @param planConfigurationSearchCriteria
     * @return
     */
    private List<String> queryDatabaseForPlanConfigIds(PlanConfigurationSearchCriteria planConfigurationSearchCriteria) {
        List<Object> preparedStmtList = new ArrayList<>();
        String query = planConfigQueryBuilder.getPlanConfigSearchQuery(planConfigurationSearchCriteria, preparedStmtList);
        return jdbcTemplate.query(query, new SingleColumnRowMapper<>(String.class), preparedStmtList.toArray());
    }

    /**
     * Helper method to search for plan configs based on the provided plan config ids.
     * @param planConfigIds
     * @return
     */
    private List<PlanConfiguration> searchPlanConfigsByIds(List<String> planConfigIds) {
        List<Object> preparedStmtList = new ArrayList<>();
        String query = planConfigQueryBuilder.getPlanConfigQuery(planConfigIds, preparedStmtList);
        log.info("Plan Config query: " + query);
        return jdbcTemplate.query(query, planConfigRowMapper, preparedStmtList.toArray());
    }

}
