package digit.repository.impl;

import digit.config.Configuration;
import digit.kafka.Producer;
import digit.repository.PlanRepository;
import digit.repository.querybuilder.PlanQueryBuilder;
import digit.repository.rowmapper.PlanRowMapper;
import digit.web.models.Plan;
import digit.web.models.PlanRequest;
import digit.web.models.PlanSearchCriteria;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.SingleColumnRowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Repository
public class PlanRepositoryImpl implements PlanRepository {

    private Producer producer;

    private PlanQueryBuilder planQueryBuilder;

    private PlanRowMapper planRowMapper;

    private JdbcTemplate jdbcTemplate;

    private Configuration config;

    public PlanRepositoryImpl(Producer producer, PlanQueryBuilder planQueryBuilder, PlanRowMapper planRowMapper,
                              JdbcTemplate jdbcTemplate, Configuration config) {
        this.producer = producer;
        this.planQueryBuilder = planQueryBuilder;
        this.planRowMapper = planRowMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.config = config;
    }

    /**
     * This method emits an event to the persister for it to save the plan in the database.
     * @param planRequest
     */
    @Override
    public void create(PlanRequest planRequest) {
		try {
			producer.push(config.getPlanCreateTopic(), planRequest);
		} catch (Exception e) {
			log.info("Pushing message to topic " + config.getPlanCreateTopic() + " failed.", e);
		}
    }

    /**
     * This method searches for plans based on the search criteria.
     * @param planSearchCriteria
     * @return
     */
    @Override
    public List<Plan> search(PlanSearchCriteria planSearchCriteria) {
        // Fetch plan ids from database
        List<String> planIds = queryDatabaseForPlanIds(planSearchCriteria);

        // Return empty list back as response if no plan ids are found
        if(CollectionUtils.isEmpty(planIds)) {
            log.info("No plan ids found for provided plan search criteria.");
        	return new ArrayList<>();
        }

        // Fetch plans from database based on the acquired ids
        List<Plan> plans = searchPlanByIds(planIds);

        return plans;
    }

    /**
     * This method emits an event to the persister for it to update the plan in the database.
     * @param planRequest
     */
    @Override
	public void update(PlanRequest planRequest) {
		try {
			producer.push(config.getPlanUpdateTopic(), planRequest);
		} catch (Exception e) {
			log.info("Pushing message to topic " + config.getPlanUpdateTopic() + " failed.", e);
		}
	}

    /**
     * Counts the number of plans based on the provided search criteria.
     * @param planSearchCriteria The search criteria for filtering plans.
     * @return The total count of plans matching the search criteria.
     */
    @Override
    public Integer count(PlanSearchCriteria planSearchCriteria) {
        List<Object> preparedStmtList = new ArrayList<>();
        String query = planQueryBuilder.getPlanCountQuery(planSearchCriteria, preparedStmtList);
        return jdbcTemplate.queryForObject(query, preparedStmtList.toArray(), Integer.class);
    }

    /**
     * Helper method to query database for plan ids based on the provided search criteria.
     * @param planSearchCriteria
     * @return
     */
    private List<String> queryDatabaseForPlanIds(PlanSearchCriteria planSearchCriteria) {
        List<Object> preparedStmtList = new ArrayList<>();
        String query = planQueryBuilder.getPlanSearchQuery(planSearchCriteria, preparedStmtList);
        log.info("Plan search query: " + query);
        return jdbcTemplate.query(query, new SingleColumnRowMapper<>(String.class), preparedStmtList.toArray());
    }

    /**
     * Helper method to search for plans based on the provided plan ids.
     * @param planIds
     * @return
     */
    private List<Plan> searchPlanByIds(List<String> planIds) {
        List<Object> preparedStmtList = new ArrayList<>();
        String query = planQueryBuilder.getPlanQuery(planIds, preparedStmtList);
        log.info("Plan query: " + query);
        return jdbcTemplate.query(query, planRowMapper, preparedStmtList.toArray());
    }

}
