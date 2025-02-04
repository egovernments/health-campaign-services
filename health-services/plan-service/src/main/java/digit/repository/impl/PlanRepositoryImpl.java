package digit.repository.impl;

import digit.config.Configuration;
import digit.kafka.Producer;
import digit.repository.PlanRepository;
import digit.repository.querybuilder.PlanQueryBuilder;
import digit.repository.rowmapper.PlanRowMapper;
import digit.repository.rowmapper.PlanStatusCountRowMapper;
import digit.service.workflow.WorkflowService;
import digit.web.models.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.SingleColumnRowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static digit.config.ServiceConstants.COMMA_DELIMITER;
import static digit.config.ServiceConstants.PLAN_ESTIMATION_BUSINESS_SERVICE;

@Slf4j
@Repository
public class PlanRepositoryImpl implements PlanRepository {

    private Producer producer;

    private PlanQueryBuilder planQueryBuilder;

    private PlanRowMapper planRowMapper;

    private JdbcTemplate jdbcTemplate;

    private Configuration config;

    private PlanStatusCountRowMapper statusCountRowMapper;

    private WorkflowService workflowService;

    public PlanRepositoryImpl(Producer producer, PlanQueryBuilder planQueryBuilder, PlanRowMapper planRowMapper,
                              JdbcTemplate jdbcTemplate, Configuration config, PlanStatusCountRowMapper statusCountRowMapper, WorkflowService workflowService) {
        this.producer = producer;
        this.planQueryBuilder = planQueryBuilder;
        this.planRowMapper = planRowMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.config = config;
        this.statusCountRowMapper = statusCountRowMapper;
        this.workflowService = workflowService;
    }

    /**
     * This method emits an event to the persister for it to save the plan in the database.
     * @param planRequest
     */
    @Override
    public void create(PlanRequest planRequest) {
        PlanRequestDTO planRequestDTO = convertToPlanReqDTO(planRequest);
        producer.push(config.getPlanCreateTopic(), planRequestDTO);
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
            log.debug("No plan ids found for provided plan search criteria.");
        	return new ArrayList<>();
        }

        // Fetch plans from database based on the acquired ids
        return searchPlanByIds(planIds);
    }

    /**
     * Counts the plan based on their current status for the provided search criteria.
     *
     * @param planSearchRequest The search criteria for filtering plans.
     * @return The status count of plans for the given search criteria.
     */
    @Override
    public Map<String, Integer> statusCount(PlanSearchRequest planSearchRequest) {
        List<Object> preparedStmtList = new ArrayList<>();
        List<String> statusList = workflowService.getStatusFromBusinessService(planSearchRequest.getRequestInfo(), PLAN_ESTIMATION_BUSINESS_SERVICE, planSearchRequest.getPlanSearchCriteria().getTenantId());

        String query = planQueryBuilder.getPlanStatusCountQuery(planSearchRequest.getPlanSearchCriteria(), preparedStmtList);
        Map<String, Integer> statusCountMap = jdbcTemplate.query(query, statusCountRowMapper, preparedStmtList.toArray());

        statusList.forEach(status -> {
            if(ObjectUtils.isEmpty(statusCountMap.get(status)))
                statusCountMap.put(status, 0);
        });

        return statusCountMap;
    }

    /**
     * This method emits an event to the persister for it to update the plan in the database.
     * @param planRequest
     */
    @Override
	public void update(PlanRequest planRequest) {
        PlanRequestDTO planRequestDTO = convertToPlanReqDTO(planRequest);
        producer.push(config.getPlanUpdateTopic(), planRequestDTO);
	}

    @Override
    public void bulkUpdate(BulkPlanRequest body) {
        // Get bulk plan update query
        String bulkPlanUpdateQuery = planQueryBuilder.getBulkPlanQuery();

        // Prepare rows for bulk update
        List<Object[]> rows = body.getPlans().stream().map(plan -> new Object[] {
                plan.getStatus(),
                !CollectionUtils.isEmpty(plan.getAssignee()) ? String.join(COMMA_DELIMITER, plan.getAssignee()) : plan.getAssignee(),
                plan.getAuditDetails().getLastModifiedBy(),
                plan.getAuditDetails().getLastModifiedTime(),
                plan.getId()
        }).toList();

        // Perform batch update
        jdbcTemplate.batchUpdate(bulkPlanUpdateQuery, rows);
        producer.push(config.getPlanBulkUpdateTopic(), body);
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

        String assignee = !CollectionUtils.isEmpty(plan.getAssignee()) ? String.join(",", plan.getAssignee()) : null;

        // Creating a new data transfer object (DTO) for Plan
        PlanDTO planDTO = PlanDTO.builder()
                .id(plan.getId())
                .tenantId(plan.getTenantId())
                .locality(plan.getLocality())
                .campaignId(plan.getCampaignId())
                .planConfigurationId(plan.getPlanConfigurationId())
                .status(plan.getStatus())
                .assignee(assignee)
                .additionalDetails(plan.getAdditionalDetails())
                .jurisdictionMapping(plan.getJurisdictionMapping())
                .additionalFields(plan.getAdditionalFields())
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
