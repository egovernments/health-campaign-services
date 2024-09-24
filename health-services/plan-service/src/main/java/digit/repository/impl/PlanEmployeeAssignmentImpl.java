package digit.repository.impl;

import digit.config.Configuration;
import digit.kafka.Producer;
import digit.repository.PlanEmployeeAssignmentRepository;
import digit.repository.querybuilder.PlanEmployeeAssignmentQueryBuilder;
import digit.repository.rowmapper.PlanEmployeeAssignmentRowMapper;
import digit.util.CommonUtil;
import digit.web.models.PlanEmployeeAssignment;
import digit.web.models.PlanEmployeeAssignmentRequestDTO;
import digit.web.models.PlanEmployeeAssignmentRequest;
import digit.web.models.PlanEmployeeAssignmentSearchCriteria;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Repository
public class PlanEmployeeAssignmentImpl implements PlanEmployeeAssignmentRepository {

    private Producer producer;

    private Configuration config;

    private PlanEmployeeAssignmentQueryBuilder queryBuilder;

    private JdbcTemplate jdbcTemplate;

    private PlanEmployeeAssignmentRowMapper rowMapper;

    private CommonUtil commonUtil;

    public PlanEmployeeAssignmentImpl(Producer producer, Configuration config, PlanEmployeeAssignmentQueryBuilder queryBuilder, JdbcTemplate jdbcTemplate, PlanEmployeeAssignmentRowMapper rowMapper, CommonUtil commonUtil) {
        this.producer = producer;
        this.config = config;
        this.queryBuilder = queryBuilder;
        this.jdbcTemplate = jdbcTemplate;
        this.rowMapper = rowMapper;
        this.commonUtil = commonUtil;
    }

    /**
     * Pushes a new plan employee assignment to persister kafka topic.
     *
     * @param planEmployeeAssignmentRequest The request containing the plan employee assignment details.
     */
    @Override
    public void create(PlanEmployeeAssignmentRequest planEmployeeAssignmentRequest) {
        PlanEmployeeAssignmentRequestDTO requestDTO = commonUtil.convertToReqDTO(planEmployeeAssignmentRequest);
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

        // If searchCriteria has jurisdiction, filter the list of planEmployeeAssignments by jurisdiction
        if (!CollectionUtils.isEmpty(searchCriteria.getJurisdiction())) {
            planEmployeeAssignments = filterByJurisdiction(planEmployeeAssignments, searchCriteria.getJurisdiction());
        }

        return planEmployeeAssignments;
    }

    /**
     * Pushes an updated existing plan employee assignment to persister kafka topic.
     *
     * @param planEmployeeAssignmentRequest The request containing the updated plan employee assignment details.
     */
    @Override
    public void update(PlanEmployeeAssignmentRequest planEmployeeAssignmentRequest) {
        PlanEmployeeAssignmentRequestDTO requestDTO = commonUtil.convertToReqDTO(planEmployeeAssignmentRequest);
        producer.push(config.getPlanEmployeeAssignmentUpdateTopic(), requestDTO);
    }

    /**
     * This is a helper method to filter out list of PlanEmployeeAssignment by jurisdiction provided
     *
     * @param planEmployeeAssignments        List of planEmployeeAssignment based on search criteria
     * @param jurisdictionFromSearchCriteria jurisdiction provided in search criteria
     * @return a list of planEmployeeAssignment filtered by jurisdiction
     */
    private List<PlanEmployeeAssignment> filterByJurisdiction(List<PlanEmployeeAssignment> planEmployeeAssignments, List<String> jurisdictionFromSearchCriteria) {

        // Convert jurisdictionFromSearchCriteria to a Set
        Set<String> jurisdictionSet = new HashSet<>(jurisdictionFromSearchCriteria);

        return planEmployeeAssignments.stream().filter(assignment -> {
            for (String jurisdiction : assignment.getJurisdiction()) {
                if (jurisdictionSet.contains(jurisdiction)) {
                    return true;
                }
            }
            return false;
        }).collect(Collectors.toList());
    }
}
