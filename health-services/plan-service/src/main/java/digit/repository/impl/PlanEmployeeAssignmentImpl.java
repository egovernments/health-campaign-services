package digit.repository.impl;

import digit.config.Configuration;
import digit.kafka.Producer;
import digit.repository.PlanEmployeeAssignmentRepository;
import digit.repository.querybuilder.PlanEmployeeAssignmentQueryBuilder;
import digit.repository.rowmapper.PlanEmployeeAssignmentRowMapper;
import digit.web.models.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Repository
public class PlanEmployeeAssignmentImpl implements PlanEmployeeAssignmentRepository {

    private Producer producer;

    private Configuration config;

    private PlanEmployeeAssignmentQueryBuilder queryBuilder;

    private JdbcTemplate jdbcTemplate;

    private PlanEmployeeAssignmentRowMapper rowMapper;

    public PlanEmployeeAssignmentImpl(Producer producer, Configuration config, PlanEmployeeAssignmentQueryBuilder queryBuilder, JdbcTemplate jdbcTemplate, PlanEmployeeAssignmentRowMapper rowMapper) {
        this.producer = producer;
        this.config = config;
        this.queryBuilder = queryBuilder;
        this.jdbcTemplate = jdbcTemplate;
        this.rowMapper = rowMapper;
    }

    /**
     * Pushes a new plan employee assignment to persister kafka topic.
     *
     * @param planEmployeeAssignmentRequest The request containing the plan employee assignment details.
     */
    @Override
    public void create(PlanEmployeeAssignmentRequest planEmployeeAssignmentRequest) {
        PlanEmployeeAssignmentRequestDTO requestDTO = convertToReqDTO(planEmployeeAssignmentRequest);
        producer.push(config.getPlanEmployeeAssignmentCreateTopic(), requestDTO);
    }

    /**
     * Searches for Plan employee assignments based on provided search criteria
     *
     * @param searchCriteria The criteria used for searching plan employee assignments
     * @return A list of Plan employee assignments that matches the search criteria
     */
    @Override
    public List<PlanEmployeeAssignment> search(PlanEmployeeAssignmentSearchCriteria searchCriteria) {
        List<Object> preparedStmtList = new ArrayList<>();
        String searchQuery = queryBuilder.getPlanEmployeeAssignmentQuery(searchCriteria, preparedStmtList);
        List<PlanEmployeeAssignment> planEmployeeAssignments = jdbcTemplate.query(searchQuery, rowMapper, preparedStmtList.toArray());

        return planEmployeeAssignments;
    }

    /**
     * Counts the number of plan employee assignments based on the provided search criteria.
     *
     * @param searchCriteria The search criteria for filtering plan employee assignments.
     * @return The total count of plan employee assignment matching the search criteria.
     */
    @Override
    public Integer count(PlanEmployeeAssignmentSearchCriteria searchCriteria) {
        List<Object> preparedStmtList = new ArrayList<>();
        String query = queryBuilder.getPlanEmployeeAssignmentCountQuery(searchCriteria, preparedStmtList);
        Integer count = jdbcTemplate.queryForObject(query, preparedStmtList.toArray(), Integer.class);

        return count;
    }

    /**
     * Pushes an updated existing plan employee assignment to persister kafka topic.
     *
     * @param planEmployeeAssignmentRequest The request containing the updated plan employee assignment details.
     */
    @Override
    public void update(PlanEmployeeAssignmentRequest planEmployeeAssignmentRequest) {
        PlanEmployeeAssignmentRequestDTO requestDTO = convertToReqDTO(planEmployeeAssignmentRequest);
        producer.push(config.getPlanEmployeeAssignmentUpdateTopic(), requestDTO);
    }

    /**
     * Converts the PlanEmployeeAssignmentRequest to a data transfer object (DTO)
     *
     * @param planEmployeeAssignmentRequest The request to be converted to DTO
     * @return a DTO for PlanEmployeeAssignmentRequest
     */
    public PlanEmployeeAssignmentRequestDTO convertToReqDTO(PlanEmployeeAssignmentRequest planEmployeeAssignmentRequest) {
        PlanEmployeeAssignment planEmployeeAssignment = planEmployeeAssignmentRequest.getPlanEmployeeAssignment();

        // Creating a new data transfer object (DTO) for PlanEmployeeAssignment
        PlanEmployeeAssignmentDTO planEmployeeAssignmentDTO = PlanEmployeeAssignmentDTO.builder()
                .id(planEmployeeAssignment.getId())
                .tenantId(planEmployeeAssignment.getTenantId())
                .planConfigurationId(planEmployeeAssignment.getPlanConfigurationId())
                .employeeId(planEmployeeAssignment.getEmployeeId())
                .role(planEmployeeAssignment.getRole())
                .planConfigurationName(planEmployeeAssignment.getPlanConfigurationName())
                .hierarchyLevel(planEmployeeAssignment.getHierarchyLevel())
                .jurisdiction(String.join(",", planEmployeeAssignment.getJurisdiction()))
                .additionalDetails(planEmployeeAssignment.getAdditionalDetails())
                .active(planEmployeeAssignment.getActive())
                .auditDetails(planEmployeeAssignment.getAuditDetails())
                .build();

        return PlanEmployeeAssignmentRequestDTO.builder()
                .requestInfo(planEmployeeAssignmentRequest.getRequestInfo())
                .planEmployeeAssignmentDTO(planEmployeeAssignmentDTO)
                .build();
    }

}
