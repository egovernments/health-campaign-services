package digit.repository.impl;

import digit.config.Configuration;
import digit.kafka.Producer;
import digit.repository.PlanEmployeeAssignmentRepository;
import digit.repository.querybuilder.PlanEmployeeAssignmentQueryBuilder;
import digit.repository.rowmapper.PlanEmployeeAssignmentRowMapper;
import digit.util.ServiceUtil;
import digit.web.models.PlanEmployeeAssignment;
import digit.web.models.PlanEmployeeAssignmentRequestDTO;
import digit.web.models.PlanEmployeeAssignmentRequest;
import digit.web.models.PlanEmployeeAssignmentSearchCriteria;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

@Repository
public class PlanEmployeeAssignmentImpl implements PlanEmployeeAssignmentRepository {

    private Producer producer;

    private Configuration config;

    private PlanEmployeeAssignmentQueryBuilder queryBuilder;

    private JdbcTemplate jdbcTemplate;

    private PlanEmployeeAssignmentRowMapper rowMapper;

    private ServiceUtil serviceUtil;

    public PlanEmployeeAssignmentImpl(Producer producer, Configuration config, PlanEmployeeAssignmentQueryBuilder queryBuilder, JdbcTemplate jdbcTemplate, PlanEmployeeAssignmentRowMapper rowMapper, ServiceUtil serviceUtil) {
        this.producer = producer;
        this.config = config;
        this.queryBuilder = queryBuilder;
        this.jdbcTemplate = jdbcTemplate;
        this.rowMapper = rowMapper;
        this.serviceUtil = serviceUtil;
    }

    /**
     * Pushes a new plan employee assignment to persister kafka topic.
     *
     * @param planEmployeeAssignmentRequest The request containing the plan employee assignment details.
     */
    @Override
    public void create(PlanEmployeeAssignmentRequest planEmployeeAssignmentRequest) {
        PlanEmployeeAssignmentRequestDTO requestDTO = serviceUtil.convertToReqDTO(planEmployeeAssignmentRequest);
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
        return jdbcTemplate.query(searchQuery, rowMapper, preparedStmtList.toArray());
    }

    /**
     * Pushes an updated existing plan employee assignment to persister kafka topic.
     *
     * @param planEmployeeAssignmentRequest The request containing the updated plan employee assignment details.
     */
    @Override
    public void update(PlanEmployeeAssignmentRequest planEmployeeAssignmentRequest) {
        PlanEmployeeAssignmentRequestDTO requestDTO = serviceUtil.convertToReqDTO(planEmployeeAssignmentRequest);
        producer.push(config.getPlanEmployeeAssignmentUpdateTopic(), requestDTO);
    }
}
