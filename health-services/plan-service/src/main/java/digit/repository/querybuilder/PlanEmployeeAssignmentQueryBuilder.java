package digit.repository.querybuilder;

import digit.config.Configuration;
import digit.util.QueryUtil;
import digit.web.models.PlanEmployeeAssignmentSearchCriteria;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;

import static digit.config.ServiceConstants.PERCENTAGE_WILDCARD;

@Component
public class PlanEmployeeAssignmentQueryBuilder {

    private QueryUtil queryUtil;

    private Configuration config;

    public PlanEmployeeAssignmentQueryBuilder(QueryUtil queryUtil, Configuration config) {

        this.queryUtil = queryUtil;
        this.config = config;
    }

    private static final String PLAN_EMPLOYEE_ASSIGNMENT_SEARCH_BASE_QUERY = "SELECT id, tenant_id, plan_configuration_id, plan_configuration_name, employee_id, role, hierarchy_level, jurisdiction, additional_details, active, created_by, created_time, last_modified_by, last_modified_time FROM plan_employee_assignment ";

    private static final String UNIQUE_PLAN_EMPLOYEE_ASSIGNMENT_SEARCH_BASE_QUERY = "SELECT DISTINCT ON (plan_configuration_id) id, tenant_id, plan_configuration_id, plan_configuration_name, employee_id, role, hierarchy_level, jurisdiction, additional_details, active, created_by, created_time, last_modified_by, last_modified_time FROM plan_employee_assignment ";

    private static final String PLAN_EMPLOYEE_ASSIGNMENT_SEARCH_QUERY_ORDER_BY_CLAUSE = " ORDER BY last_modified_time DESC";

    private static final String UNIQUE_PLAN_EMPLOYEE_ASSIGNMENT_SEARCH_QUERY_ORDER_BY_CLAUSE = " ORDER BY plan_configuration_id, last_modified_time DESC";

    private static final String PLAN_EMPLOYEE_ASSIGNMENT_SEARCH_QUERY_COUNT_WRAPPER = "SELECT COUNT(id) AS total_count FROM ( ";

    private static final String LATEST_PLAN_EMPLOYEE_ASSIGNMENT_ORDER_BY_QUERY = "SELECT pa.id, pa.tenant_id, pa.plan_configuration_id, pa.plan_configuration_name, pa.employee_id, pa.role, pa.hierarchy_level, pa.jurisdiction, pa.additional_details, pa.active, pa.created_by, pa.created_time, pa.last_modified_by, pa.last_modified_time FROM ( {INTERNAL_QUERY} ) AS pa JOIN plan_configuration pc ON plan_configuration_id = pc.id ORDER BY pc.last_modified_time DESC";
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
        query = queryUtil.addOrderByClause(query, Boolean.TRUE.equals(searchCriteria.getFilterUniqueByPlanConfig()) ?
                UNIQUE_PLAN_EMPLOYEE_ASSIGNMENT_SEARCH_QUERY_ORDER_BY_CLAUSE : PLAN_EMPLOYEE_ASSIGNMENT_SEARCH_QUERY_ORDER_BY_CLAUSE);
        query = getPaginatedQuery(query, searchCriteria, preparedStmtList);
        return Boolean.TRUE.equals(searchCriteria.getFilterUniqueByPlanConfig()) ? LATEST_PLAN_EMPLOYEE_ASSIGNMENT_ORDER_BY_QUERY.replace("{INTERNAL_QUERY}", query) : query;
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
        StringBuilder builder = Boolean.TRUE.equals(searchCriteria.getFilterUniqueByPlanConfig()) ?
                new StringBuilder(UNIQUE_PLAN_EMPLOYEE_ASSIGNMENT_SEARCH_BASE_QUERY) : new StringBuilder(PLAN_EMPLOYEE_ASSIGNMENT_SEARCH_BASE_QUERY);

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

        if (searchCriteria.getPlanConfigurationName() != null) {
            queryUtil.addClauseIfRequired(builder, preparedStmtList);
            builder.append(" plan_configuration_name ILIKE ?");
            preparedStmtList.add(PERCENTAGE_WILDCARD + searchCriteria.getPlanConfigurationName() + PERCENTAGE_WILDCARD);
        }

        if (!CollectionUtils.isEmpty(searchCriteria.getEmployeeId())) {
            queryUtil.addClauseIfRequired(builder, preparedStmtList);
            builder.append(" employee_id IN ( ").append(queryUtil.createQuery(searchCriteria.getEmployeeId().size())).append(" )");
            queryUtil.addToPreparedStatement(preparedStmtList, new LinkedHashSet<>(searchCriteria.getEmployeeId()));
        }

        if (!CollectionUtils.isEmpty(searchCriteria.getRole())) {
            queryUtil.addClauseIfRequired(builder, preparedStmtList);
            builder.append(" role IN ( ").append(queryUtil.createQuery(searchCriteria.getRole().size())).append(" )");
            queryUtil.addToPreparedStatement(preparedStmtList, new LinkedHashSet<>(searchCriteria.getRole()));
        }

        if(searchCriteria.getHierarchyLevel() != null) {
            queryUtil.addClauseIfRequired(builder, preparedStmtList);
            builder.append(" hierarchy_level = ?");
            preparedStmtList.add(searchCriteria.getHierarchyLevel());
        }

        if (searchCriteria.getActive() != null) {
            queryUtil.addClauseIfRequired(builder, preparedStmtList);
            builder.append(" active = ?");
            preparedStmtList.add(searchCriteria.getActive());
        }

        //TODO
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

    private String getPaginatedQuery(String query, PlanEmployeeAssignmentSearchCriteria searchCriteria, List<Object> preparedStmtList) {
        StringBuilder paginatedQuery = new StringBuilder(query);

        // Append offset
        paginatedQuery.append(" OFFSET ? ");
        preparedStmtList.add(ObjectUtils.isEmpty(searchCriteria.getOffset()) ? config.getDefaultOffset() : searchCriteria.getOffset());

        // Append limit
        paginatedQuery.append(" LIMIT ? ");
        preparedStmtList.add(ObjectUtils.isEmpty(searchCriteria.getLimit()) ? config.getDefaultLimit() : searchCriteria.getLimit());

        return paginatedQuery.toString();
    }
}
