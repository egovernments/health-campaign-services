package digit.repository.querybuilder;

import digit.util.QueryUtil;
import digit.web.models.PlanEmployeeAssignmentSearchCriteria;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;

@Component
public class PlanEmployeeAssignmentQueryBuilder {

    private QueryUtil queryUtil;

    public PlanEmployeeAssignmentQueryBuilder(QueryUtil queryUtil) {
        this.queryUtil = queryUtil;
    }

    private static final String PLAN_EMPLOYEE_ASSIGNMENT_SEARCH_BASE_QUERY = "SELECT id, tenant_id, plan_configuration_id, employee_id, role, hierarchy_type, jurisdiction, additional_details, active, created_by, created_time, last_modified_by, last_modified_time FROM plan_employee_assignment ";

    private static final String PLAN_EMPLOYEE_ASSIGNMENT_SEARCH_QUERY_ORDER_BY_CLAUSE = " ORDER BY last_modified_time DESC";

    private static final String PLAN_EMPLOYEE_ASSIGNMENT_SEARCH_QUERY_COUNT_WRAPPER = "SELECT COUNT(*) AS total_count FROM ( ";

    /**
     * Constructs a SQL query string for searching PlanEmployeeAssignment objects based on the provided search criteria.
     * Also adds an ORDER BY clause and handles pagination.
     *
     * @param searchCriteria   The criteria used for filtering PlanEmployeeAssignment objects.
     * @param preparedStmtList A list to store prepared statement parameters.
     * @return A complete SQL query string for searching PlanEmployeeAssignment objects.
     */
    public String getPlanEmployeeAssignmentQuery(PlanEmployeeAssignmentSearchCriteria searchCriteria, List<Object> preparedStmtList) {
        String query = buildPlanEmployeeAssignmentQuery(searchCriteria, preparedStmtList, Boolean.FALSE);
        query = queryUtil.addOrderByClause(query, PLAN_EMPLOYEE_ASSIGNMENT_SEARCH_QUERY_ORDER_BY_CLAUSE);
        query = queryUtil.getPaginatedQuery(query, preparedStmtList);
        return query;
    }

    /**
     * Constructs the count query to get the total count of plan employee assignments based on search criteria
     *
     * @param searchCriteria   The criteria used for filtering PlanEmployeeAssignment objects.
     * @param preparedStmtList A list to store prepared statement parameters.
     * @return A SQL query string to get the total count of plan employee assignments
     */
    public String getPlanEmployeeAssignmentCountQuery(PlanEmployeeAssignmentSearchCriteria searchCriteria, List<Object> preparedStmtList) {
        String query = buildPlanEmployeeAssignmentQuery(searchCriteria, preparedStmtList, Boolean.TRUE);
        return query;
    }

    /**
     * Constructs query based on the provided search criteria
     *
     * @param searchCriteria   The criteria used for filtering PlanEmployeeAssignment objects.
     * @param preparedStmtList A list to store prepared statement parameters.
     * @param isCount          is true if count query is required for the provided search criteria
     * @return A SQL query string for searching planEmployeeAssignment
     */
    private String buildPlanEmployeeAssignmentQuery(PlanEmployeeAssignmentSearchCriteria searchCriteria, List<Object> preparedStmtList, Boolean isCount) {
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

        if (searchCriteria.getEmployeeId() != null) {
            queryUtil.addClauseIfRequired(builder, preparedStmtList);
            builder.append(" employee_id = ?");
            preparedStmtList.add(searchCriteria.getEmployeeId());
        }

        if (!CollectionUtils.isEmpty(searchCriteria.getRole())) {
            queryUtil.addClauseIfRequired(builder, preparedStmtList);
            builder.append(" role IN ( ").append(queryUtil.createQuery(searchCriteria.getRole().size())).append(" )");
            queryUtil.addToPreparedStatement(preparedStmtList, new LinkedHashSet<>(searchCriteria.getRole()));
        }

        if(searchCriteria.getHierarchyType() != null) {
            queryUtil.addClauseIfRequired(builder, preparedStmtList);
            builder.append(" hierarchy_type = ?");
            preparedStmtList.add(searchCriteria.getHierarchyType());
        }

        if (searchCriteria.getActive() != null) {
            queryUtil.addClauseIfRequired(builder, preparedStmtList);
            builder.append(" active = ?");
            preparedStmtList.add(searchCriteria.getActive());
        }

        if (!CollectionUtils.isEmpty(searchCriteria.getJurisdiction())) {
            queryUtil.addClauseIfRequired(builder, preparedStmtList);
            builder.append(" ARRAY [ ").append(queryUtil.createQuery(searchCriteria.getJurisdiction().size())).append(" ]").append("::text[] ");
            builder.append(" && string_to_array(jurisdiction, ',') ");
            queryUtil.addToPreparedStatement(preparedStmtList, new HashSet<>(searchCriteria.getJurisdiction()));
        }

        StringBuilder countQuery = new StringBuilder();
        if (isCount) {
            countQuery.append(PLAN_EMPLOYEE_ASSIGNMENT_SEARCH_QUERY_COUNT_WRAPPER).append(builder);
            countQuery.append(") AS subquery");

            return countQuery.toString();
        }

        return builder.toString();
    }
}
