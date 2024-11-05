package digit.repository.impl;

import digit.config.Configuration;
import digit.kafka.Producer;
import digit.repository.PlanFacilityRepository;
import digit.repository.querybuilder.PlanFacilityQueryBuilder;
import digit.repository.rowmapper.PlanFacilityRowMapper;
import digit.web.models.*;
import lombok.extern.slf4j.Slf4j;
import org.egov.tracer.model.CustomException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.ArrayList;
import java.util.List;
import static digit.config.ServiceConstants.*;

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
                .residingBoundary(planFacility.getResidingBoundary())
                .serviceBoundaries(convertArrayToString(planFacility.getServiceBoundaries()))
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
     * This is a helper function to convert an array of string to comma separated string
     *
     * @param stringList Array of string to be converted
     * @return a string
     */
    private String convertArrayToString(List<String> stringList) {
        return String.join(COMMA_DELIMITER, stringList);
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
        try {
            PlanFacilityRequestDTO requestDTO = convertToDTO(planFacilityRequest);
            producer.push(config.getPlanFacilityUpdateTopic(), requestDTO);
            log.info("Successfully pushed update for plan facility: {}", planFacilityRequest.getPlanFacility().getId());
        } catch (Exception e) {
            throw new CustomException(FAILED_MESSAGE,config.getPlanFacilityUpdateTopic());
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
        log.info("Plan facility search {}", query);
        log.info(preparedStmtList.toString());
        return jdbcTemplate.query(query, planFacilityRowMapper, preparedStmtList.toArray());
    }


}
