package digit.repository.querybuilder;

import digit.config.Configuration;
import digit.util.QueryUtil;
import digit.web.models.PlanEmployeeAssignmentSearchCriteria;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PlanEmployeeAssignmentQueryBuilder {

    private QueryUtil queryUtil;

    public PlanEmployeeAssignmentQueryBuilder(QueryUtil queryUtil) {
        this.queryUtil = queryUtil;
    }

    private static final String PLAN_EMPLOYEE_ASSIGNMENT_SEARCH_BASE_QUERY = "SELECT id, tenant_id, plan_configuration_id, employee_id, role, jurisdiction, additional_details, active, created_by, created_time, last_modified_by, last_modified_time FROM plan_employee_assignment ";

    private static final String PLAN_EMPLOYEE_ASSIGNMENT_SEARCH_QUERY_ORDER_BY_CLAUSE = " ORDER BY last_modified_time DESC";

    /**
     * Constructs a SQL query string for searching PlanEmployeeAssignment objects based on the provided search criteria.
     * Also adds an ORDER BY clause and handles pagination.
     *
     * @param searchCriteria   The criteria used for filtering PlanEmployeeAssignment objects.
     * @param preparedStmtList A list to store prepared statement parameters.
     * @return A complete SQL query string for searching PlanEmployeeAssignment objects.
     */
    public String getPlanEmployeeAssignmentQuery(PlanEmployeeAssignmentSearchCriteria searchCriteria, List<Object> preparedStmtList) {
        String query = buildPlanEmployeeAssignmentQuery(searchCriteria, preparedStmtList);
        query = queryUtil.addOrderByClause(query, PLAN_EMPLOYEE_ASSIGNMENT_SEARCH_QUERY_ORDER_BY_CLAUSE);
        query = queryUtil.getPaginatedQuery(query, preparedStmtList);
        return query;
    }

    /**
     * Constructs query based on the provided search criteria
     *
     * @param searchCriteria   The criteria used for filtering PlanEmployeeAssignment objects.
     * @param preparedStmtList A list to store prepared statement parameters.
     * @return
     */
    private String buildPlanEmployeeAssignmentQuery(PlanEmployeeAssignmentSearchCriteria searchCriteria, List<Object> preparedStmtList) {
        StringBuilder builder = new StringBuilder(PLAN_EMPLOYEE_ASSIGNMENT_SEARCH_BASE_QUERY);

        if (searchCriteria.getId() != null) {
            queryUtil.addClauseIfRequired(builder, preparedStmtList);
            builder.append(" id = ?");
            preparedStmtList.add(searchCriteria.getId());
        }

        if (searchCriteria.getTenantId() != null) {
            queryUtil.addClauseIfRequired(builder, preparedStmtList);
            builder.append(" tenant_id = ?");
            preparedStmtList.add(searchCriteria.getTenantId());
        }

        if (searchCriteria.getPlanConfigurationId() != null) {
            queryUtil.addClauseIfRequired(builder, preparedStmtList);
            builder.append(" plan_configuration_id = ?");
            preparedStmtList.add(searchCriteria.getPlanConfigurationId());
        }

        if (searchCriteria.getRole() != null) {
            queryUtil.addClauseIfRequired(builder, preparedStmtList);
            builder.append(" role = ?");
            preparedStmtList.add(searchCriteria.getRole());
        }

        if (searchCriteria.getActive() != null) {
            queryUtil.addClauseIfRequired(builder, preparedStmtList);
            builder.append(" active = ?");
            preparedStmtList.add(Boolean.TRUE);
        }

        return builder.toString();
    }

}
