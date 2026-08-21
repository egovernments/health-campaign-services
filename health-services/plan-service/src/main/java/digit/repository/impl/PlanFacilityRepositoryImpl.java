package digit.repository.impl;

import digit.config.Configuration;
import digit.kafka.Producer;
import digit.repository.PlanFacilityRepository;
import digit.repository.querybuilder.PlanFacilityQueryBuilder;
import digit.repository.rowmapper.PlanFacilityRowMapper;
import digit.util.CommonUtil;
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

    private CommonUtil commonUtil;

    public PlanFacilityRepositoryImpl(Producer producer, JdbcTemplate jdbcTemplate, PlanFacilityQueryBuilder planFacilityQueryBuilder, PlanFacilityRowMapper planFacilityRowMapper, Configuration config, CommonUtil commonUtil) {
        this.producer = producer;
        this.jdbcTemplate = jdbcTemplate;
        this.planFacilityQueryBuilder = planFacilityQueryBuilder;
        this.planFacilityRowMapper = planFacilityRowMapper;
        this.config = config;
        this.commonUtil = commonUtil;
    }

    /**
     * This method emits an event to the persister for it to save the plan facility linkage in the database.
     * @param planFacilityRequest
     */
    @Override
    public void create(PlanFacilityRequest planFacilityRequest) {
        // Convert the incoming PlanFacilityRequest to PlanFacilityRequestDTO
        PlanFacilityRequestDTO requestDTO = convertToDTO(planFacilityRequest);

        // Push the requestDTO to the producer for processing
        producer.push(config.getPlanFacilityCreateTopic(), requestDTO);
    }

    public PlanFacilityRequestDTO convertToDTO(PlanFacilityRequest planFacilityRequest) {
        PlanFacility planFacility = planFacilityRequest.getPlanFacility();

        // Create a new PlanFacilityDTO
        PlanFacilityDTO planFacilityDTO = PlanFacilityDTO.builder()
                .id(planFacility.getId())
                .tenantId(planFacility.getTenantId())
                .planConfigurationId(planFacility.getPlanConfigurationId())
                .planConfigurationName(planFacility.getPlanConfigurationName())
                .facilityId(planFacility.getFacilityId())
                .facilityName(planFacility.getFacilityName())
                .jurisdictionMapping(planFacility.getJurisdictionMapping())
                .boundaryAncestralPath(planFacility.getBoundaryAncestralPath())
                .residingBoundary(planFacility.getResidingBoundary())
                .serviceBoundaries(commonUtil.convertArrayToString(planFacility.getServiceBoundaries()))
                .initiallySetServiceBoundaries(planFacility.getInitiallySetServiceBoundaries())
                .additionalDetails(planFacility.getAdditionalDetails())
                .active(planFacility.getActive())
                .auditDetails(planFacility.getAuditDetails())
                .build();

        // Return the complete PlanFacilityRequestDTO
        return PlanFacilityRequestDTO.builder()
                .requestInfo(planFacilityRequest.getRequestInfo())
                .planFacilityDTO(planFacilityDTO)
                .build();
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
        return queryDatabaseForPlanFacilities(planFacilitySearchCriteria);
    }

    /**
     * This method emits an event to the persister for it to update the plan facility in the database.
     *
     * @param planFacilityRequest
     */
    @Override
    public void update(PlanFacilityRequest planFacilityRequest) {
        PlanFacilityRequestDTO requestDTO = convertToDTO(planFacilityRequest);
        producer.push(config.getPlanFacilityUpdateTopic(), requestDTO);
    }

    /**
     * Counts the number of plan facilities based on the provided search criteria.
     *
     * @param planFacilitySearchCriteria The search criteria for filtering plan facilities.
     * @return The total count of plan facilities matching the search criteria.
     */
    @Override
    public Integer count(PlanFacilitySearchCriteria planFacilitySearchCriteria) {
        List<Object> preparedStmtList = new ArrayList<>();
        String query = planFacilityQueryBuilder.getPlanFacilityCountQuery(planFacilitySearchCriteria, preparedStmtList);

        return jdbcTemplate.queryForObject(query, preparedStmtList.toArray(), Integer.class);
    }

    /**
     * Helper method to query database for plan facilities based on the provided search criteria.
     *
     * @param planFacilitySearchCriteria Search criteria for plan facility search.
     * @return List of plan facilities for the given search criteria.
     */
    private List<PlanFacility> queryDatabaseForPlanFacilities(PlanFacilitySearchCriteria planFacilitySearchCriteria) {
        List<Object> preparedStmtList = new ArrayList<>();
        String query = planFacilityQueryBuilder.getPlanFacilitySearchQuery(planFacilitySearchCriteria, preparedStmtList);

        return jdbcTemplate.query(query, planFacilityRowMapper, preparedStmtList.toArray());
    }


}
