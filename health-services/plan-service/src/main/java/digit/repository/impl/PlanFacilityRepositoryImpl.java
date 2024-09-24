package digit.repository.impl;

import digit.config.Configuration;
import digit.kafka.Producer;
import digit.repository.PlanFacilityRepository;
import digit.repository.querybuilder.PlanFacilityQueryBuilder;
import digit.repository.querybuilder.PlanQueryBuilder;
import digit.repository.rowmapper.PlanFacilityRowMapper;
import digit.repository.rowmapper.PlanRowMapper;
import digit.web.models.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.SingleColumnRowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.util.CollectionUtils;

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
    public void create(PlanFacilityRequest planFacilityRequest) {

    }

    /**
     * This method searches for plans based on the search criteria.
     * @param planFacilitySearchCriteria
     * @return
     */
    @Override
    public List<PlanFacility> search(PlanFacilitySearchCriteria planFacilitySearchCriteria) {
        // Fetch plan facility ids from database
        List<String> planFacilityIds = queryDatabaseForPlanFacilityIds(planFacilitySearchCriteria);

        // Return empty list back as response if no plan facility ids are found
        if(CollectionUtils.isEmpty(planFacilityIds)) {
            log.info("No planFacility ids found for provided plan facility search criteria.");
            return new ArrayList<>();
        }

        // Fetch plans facilities from database based on the acquired ids
        List<PlanFacility> planFacilities  = searchPlanFacilityByIds(planFacilityIds);

        return planFacilities;
    }

    @Override
    public void update(PlanFacilityRequest planFacilityRequest) {

    }

    /**
     * Helper method to query database for plan facility ids based on the provided search criteria.
     * @param planFacilitySearchCriteria
     * @return
     */
    private List<String> queryDatabaseForPlanFacilityIds(PlanFacilitySearchCriteria planFacilitySearchCriteria) {
        List<Object> preparedStmtList = new ArrayList<>();
        String query = planFacilityQueryBuilder.getPlanFacilitySearchQuery(planFacilitySearchCriteria, preparedStmtList);
        log.info("Plan search query: " + query);
        return jdbcTemplate.query(query, new SingleColumnRowMapper<>(String.class), preparedStmtList.toArray());
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
