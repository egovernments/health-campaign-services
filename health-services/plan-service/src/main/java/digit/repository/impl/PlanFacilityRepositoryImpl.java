package digit.repository.impl;

import digit.config.Configuration;
import digit.kafka.Producer;
import digit.repository.PlanFacilityRepository;
import digit.web.models.PlanFacility;
import digit.web.models.PlanFacilityRequest;
import digit.web.models.PlanFacilitySearchCriteria;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.SingleColumnRowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.util.CollectionUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import digit.repository.querybuilder.PlanFacilityQueryBuilder;
import digit.repository.rowmapper.PlanFacilityRowMapper;

import java.util.ArrayList;
import java.util.List;

@Repository
@Slf4j
public class PlanFacilityRepositoryImpl implements PlanFacilityRepository {
    private Producer producer;
    private Configuration config;
    private JdbcTemplate jdbcTemplate;
    private PlanFacilityQueryBuilder planFacilityQueryBuilder;
    private PlanFacilityRowMapper planFacilityRowMapper;

    public PlanFacilityRepositoryImpl(Producer producer, Configuration config,JdbcTemplate jdbcTemplate, PlanFacilityQueryBuilder planFacilityQueryBuilder, PlanFacilityRowMapper planFacilityRowMapper) {
        this.producer = producer;
        this.config = config;
        this.jdbcTemplate = jdbcTemplate;
        this.planFacilityQueryBuilder = planFacilityQueryBuilder;
        this.planFacilityRowMapper = planFacilityRowMapper;
    }

    @Override
    public List<PlanFacility> search(PlanFacilitySearchCriteria planFacilitySearchCriteria) {
        // Fetch plan facility ids from database
        List<String> planFacilityIds = planFacilitySearchCriteria.getIds().stream().toList();
        if(CollectionUtils.isEmpty(planFacilityIds))
            planFacilityIds = queryDatabaseForPlanFacilityIds(planFacilitySearchCriteria);

        // Return empty list back as response if no plan facility ids are found
        if(CollectionUtils.isEmpty(planFacilityIds)) {
            log.info("No planFacility ids found for provided plan facility search criteria.");
            return new ArrayList<>();
        }

        // Fetch plans facilities from database based on the acquired ids
        List<PlanFacility> planFacilities  = searchPlanFacilityByIds(planFacilityIds);

        return planFacilities;
    }

    private List<String> queryDatabaseForPlanFacilityIds(PlanFacilitySearchCriteria planFacilitySearchCriteria) {
        List<Object> preparedStmtList = new ArrayList<>();
        String query = planFacilityQueryBuilder.getPlanFacilitySearchQuery(planFacilitySearchCriteria, preparedStmtList);
        log.info("Plan search query: " + query);
        return jdbcTemplate.query(query, new SingleColumnRowMapper<>(String.class), preparedStmtList.toArray());
    }

    /**
     * This method emits an event to the persister for it to update the plan facility in the database.
     * @param planFacilityRequest
     */
    @Override
    public void update(PlanFacilityRequest planFacilityRequest) {
        try {
            producer.push(config.getPlanFacilityUpdateTopic(), planFacilityRequest);
        } catch (Exception e) {
            log.info("Pushing message to topic " + config.getPlanFacilityUpdateTopic() + " failed.", e);
        }
    }

    /**
     * Helper method to search for plans facility based on the provided plan ids.
     * @param planFacilityIds
     * @return
     */
    private List<PlanFacility> searchPlanFacilityByIds(List<String> planFacilityIds) {
        List<Object> preparedStmtList = new ArrayList<>();
        String query = planFacilityQueryBuilder.getPlanFacilityQuery(planFacilityIds, preparedStmtList);
        log.info("Plan facility query: " + query);
        return jdbcTemplate.query(query, planFacilityRowMapper, preparedStmtList.toArray());
    }
}
