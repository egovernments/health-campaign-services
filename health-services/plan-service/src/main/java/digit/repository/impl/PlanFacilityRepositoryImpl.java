package digit.repository.impl;

import digit.config.Configuration;
import digit.kafka.Producer;
import digit.repository.PlanFacilityRepository;
import digit.repository.querybuilder.PlanFacilityQueryBuilder;
import digit.repository.rowmapper.PlanFacilityRowMapper;
import digit.web.models.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.ArrayList;
import java.util.List;

@Repository
@Slf4j
public class PlanFacilityRepositoryImpl implements PlanFacilityRepository {

    private Producer producer;

    private JdbcTemplate jdbcTemplate;

    private PlanFacilityQueryBuilder planFacilityQueryBuilder;

    private PlanFacilityRowMapper planFacilityRowMapper;

    private Configuration config;

    public PlanFacilityRepositoryImpl(Producer producer, JdbcTemplate jdbcTemplate, PlanFacilityQueryBuilder planFacilityQueryBuilder, PlanFacilityRowMapper planFacilityRowMapper, Configuration config) {
        this.producer = producer;
        this.jdbcTemplate = jdbcTemplate;
        this.planFacilityQueryBuilder = planFacilityQueryBuilder;
        this.planFacilityRowMapper = planFacilityRowMapper;
        this.config = config;
    }

    @Override
    public void create(PlanFacilitySearchCriteria planFacilitySearchCriteria) {

    }

    /**
     * This method searches for plans based on the search criteria.
     *
     * @param planFacilitySearchCriteria
     * @return List<PlanFacility>
     */
    @Override
    public List<PlanFacility> search(PlanFacilitySearchCriteria planFacilitySearchCriteria) {
        // Fetch plan facility from database
        List<PlanFacility> planFacilities = queryDatabaseForPlanFacilities(planFacilitySearchCriteria);
        return planFacilities;
    }

    /**
     * This method emits an event to the persister for it to update the plan facility in the database.
     *
     * @param planFacilityRequest
     */
    @Override
    public void update(PlanFacilityRequest planFacilityRequest) {
        try {
            producer.push(config.getPlanFacilityUpdateTopic(), planFacilityRequest);
            log.info("Successfully pushed update for plan facility: {}", planFacilityRequest.getPlanFacility().getId());
        } catch (Exception e) {
            log.info("Failed to push message to topic {}. Error: {}", config.getPlanFacilityUpdateTopic(), e.getMessage(), e);
        }
    }

    /**
     * Helper method to query database for plan facilities based on the provided search criteria.
     *
     * @param planFacilitySearchCriteria
     * @return List<PlanFacility>
     */
    private List<PlanFacility> queryDatabaseForPlanFacilities(PlanFacilitySearchCriteria planFacilitySearchCriteria) {
        List<Object> preparedStmtList = new ArrayList<>();
        String query = planFacilityQueryBuilder.getPlanFacilitySearchQuery(planFacilitySearchCriteria, preparedStmtList);
        log.debug("Plan facility search {}", query);
        log.debug(preparedStmtList.toString());
        return jdbcTemplate.query(query, planFacilityRowMapper, preparedStmtList.toArray());
    }

}
