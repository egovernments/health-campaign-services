package digit.repository.impl;

import digit.config.Configuration;
import digit.kafka.Producer;
import digit.repository.PlanRepository;
import digit.repository.querybuilder.PlanQueryBuilder;
import digit.repository.rowmapper.PlanRowMapper;
import digit.web.models.*;
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
            PlanRequestDTO planRequestDTO = convertToPlanReqDTO(planRequest);
			producer.push(config.getPlanCreateTopic(), planRequestDTO);
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
            PlanRequestDTO planRequestDTO = convertToPlanReqDTO(planRequest);
            producer.push(config.getPlanUpdateTopic(), planRequestDTO);
		} catch (Exception e) {
			log.info("Pushing message to topic " + config.getPlanUpdateTopic() + " failed.", e);
		}
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

    /**
     * Converts the PlanRequest to a data transfer object (DTO)
     *
     * @param planRequest The request to be converted to DTO
     * @return a DTO for PlanRequest
     */
    private PlanRequestDTO convertToPlanReqDTO(PlanRequest planRequest) {
        Plan plan = planRequest.getPlan();

        // Creating a new data transfer object (DTO) for Plan
        PlanDTO planDTO = PlanDTO.builder()
                .id(plan.getId())
                .tenantId(plan.getTenantId())
                .locality(plan.getLocality())
                .campaignId(plan.getCampaignId())
                .planConfigurationId(plan.getPlanConfigurationId())
                .status(plan.getStatus())
                .assignee(plan.getAssignee())
                .additionalDetails(plan.getAdditionalDetails())
                .activities(plan.getActivities())
                .resources(plan.getResources())
                .targets(plan.getTargets())
                .auditDetails(plan.getAuditDetails())
                .boundaryAncestralPath(plan.getBoundaryAncestralPath())
                .build();

        // Returning the PlanRequestDTO
        return PlanRequestDTO.builder()
                .requestInfo(planRequest.getRequestInfo())
                .planDTO(planDTO)
                .build();
    }


}
