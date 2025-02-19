package digit.repository.querybuilder;

import digit.config.Configuration;

import digit.util.QueryUtil;
import digit.web.models.PlanConfigurationSearchCriteria;
import java.util.LinkedHashSet;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;

@Component
public class PlanConfigQueryBuilder {

    private Configuration config;

    public PlanConfigQueryBuilder(Configuration config) {
        this.config = config;
    }

    private static final String PLAN_CONFIG_SEARCH_BASE_QUERY = "SELECT id FROM plan_configuration pc ";

    private static final String PLAN_CONFIG_QUERY = "SELECT pc.id as plan_configuration_id, pc.tenant_id as plan_configuration_tenant_id, pc.name as plan_configuration_name, pc.execution_plan_id as plan_configuration_execution_plan_id, pc.status as plan_configuration_status, pc.created_by as plan_configuration_created_by, pc.created_time as plan_configuration_created_time, pc.last_modified_by as plan_configuration_last_modified_by, pc.last_modified_time as plan_configuration_last_modified_time, \n" +
            "\t   pcf.id as plan_configuration_files_id, pcf.plan_configuration_id as plan_configuration_files_plan_configuration_id, pcf.filestore_id as plan_configuration_files_filestore_id, pcf.input_file_type as plan_configuration_files_input_file_type, pcf.template_identifier as plan_configuration_files_template_identifier, pcf.active as plan_configuration_files_active, pcf.created_by as plan_configuration_files_created_by, pcf.created_time as plan_configuration_files_created_time, pcf.last_modified_by as plan_configuration_files_last_modified_by, pcf.last_modified_time as plan_configuration_files_last_modified_time,\n" +
            "\t   pca.id as plan_configuration_assumptions_id, pca.key as plan_configuration_assumptions_key, pca.value as plan_configuration_assumptions_value, pca.active as plan_configuration_assumptions_active, pca.plan_configuration_id as plan_configuration_assumptions_plan_configuration_id, pca.created_by as plan_configuration_assumptions_created_by, pca.created_time as plan_configuration_assumptions_created_time, pca.last_modified_by as plan_configuration_assumptions_last_modified_by, pca.last_modified_time as plan_configuration_assumptions_last_modified_time,\n" +
            "\t   pco.id as plan_configuration_operations_id, pco.input as plan_configuration_operations_input, pco.operator as plan_configuration_operations_operator, pco.assumption_value as plan_configuration_operations_assumption_value, pco.output as plan_configuration_operations_output, pco.active as plan_configuration_operations_active, pco.plan_configuration_id as plan_configuration_operations_plan_configuration_id, pco.created_by as plan_configuration_operations_created_by, pco.created_time as plan_configuration_operations_created_time, pco.last_modified_by as plan_configuration_operations_last_modified_by, pco.last_modified_time as plan_configuration_operations_last_modified_time,\n" +
            "\t   pcm.id as plan_configuration_mapping_id, pcm.filestore_id as plan_configuration_mapping_filestore_id,  pcm.mapped_from as plan_configuration_mapping_mapped_from, pcm.mapped_to as plan_configuration_mapping_mapped_to, pcm.active as plan_configuration_mapping_active, pcm.plan_configuration_id as plan_configuration_mapping_plan_configuration_id, pcm.created_by as plan_configuration_mapping_created_by, pcm.created_time as plan_configuration_mapping_created_time, pcm.last_modified_by as plan_configuration_mapping_last_modified_by, pcm.last_modified_time as plan_configuration_mapping_last_modified_time\n" +
            "\t   FROM plan_configuration pc\n" +
            "\t   LEFT JOIN plan_configuration_files pcf ON pc.id = pcf.plan_configuration_id\n" +
            "\t   LEFT JOIN plan_configuration_assumptions pca ON pc.id = pca.plan_configuration_id\n" +
            "\t   LEFT JOIN plan_configuration_operations pco ON pc.id = pco.plan_configuration_id\n" +
            "\t   LEFT JOIN plan_configuration_mapping pcm ON pc.id = pcm.plan_configuration_id";

    private static final String PLAN_CONFIG_SEARCH_QUERY_ORDER_BY_CLAUSE = " ORDER BY pc.last_modified_time DESC";

    private static final String PLAN_CONFIG_SEARCH_QUERY_COUNT_WRAPPER = "SELECT COUNT(*) AS total_count FROM ( ";

    public String getPlanConfigQuery(List<String> ids, List<Object> preparedStmtList) {
        return buildPlanConfigQuery(ids, preparedStmtList);
    }

    private String buildPlanConfigQuery(List<String> ids, List<Object> preparedStmtList) {
        StringBuilder builder = new StringBuilder(PLAN_CONFIG_QUERY);

        if (!CollectionUtils.isEmpty(ids)) {
            QueryUtil.addClauseIfRequired(builder, preparedStmtList);
            builder.append(" pc.id IN ( ").append(QueryUtil.createQuery(ids.size())).append(" )");
            QueryUtil.addToPreparedStatement(preparedStmtList, new LinkedHashSet<>(ids));
        }

        addActiveWhereClause(builder, preparedStmtList);

        return QueryUtil.addOrderByClause(builder.toString(), PLAN_CONFIG_SEARCH_QUERY_ORDER_BY_CLAUSE);
    }

    /**
     * Constructs a SQL query string for searching PlanConfiguration objects based on the provided search criteria.
     * Also adds an ORDER BY clause and handles pagination.
     *
     * @param criteria         The criteria used for filtering PlanConfiguration objects.
     * @param preparedStmtList A list to store prepared statement parameters.
     * @return A complete SQL query string for searching PlanConfiguration objects.
     */
    public String getPlanConfigSearchQuery(PlanConfigurationSearchCriteria criteria, List<Object> preparedStmtList) {
        String query = buildPlanConfigSearchQuery(criteria, preparedStmtList, Boolean.FALSE);
        query = QueryUtil.addOrderByClause(query, PLAN_CONFIG_SEARCH_QUERY_ORDER_BY_CLAUSE);
        query = getPaginatedQuery(query, criteria, preparedStmtList);

        return query;
    }

    public String getPlanConfigCountQuery(PlanConfigurationSearchCriteria criteria, List<Object> preparedStmtList) {
        String query = buildPlanConfigSearchQuery(criteria, preparedStmtList, Boolean.TRUE);
        return query;
    }

    /**
     * Constructs query based on the provided search criteria
     *
     * @param criteria         The criteria used for filtering PlanConfiguration objects.
     * @param preparedStmtList A list to store prepared statement parameters.
     * @return
     */
    private String buildPlanConfigSearchQuery(PlanConfigurationSearchCriteria criteria, List<Object> preparedStmtList, Boolean isCount) {
        StringBuilder builder = new StringBuilder(PLAN_CONFIG_SEARCH_BASE_QUERY);

        if (criteria.getTenantId() != null) {
            addClauseIfRequired(preparedStmtList, builder);
            builder.append(" pc.tenant_id = ?");
            preparedStmtList.add(criteria.getTenantId());
        }

        if (criteria.getId() != null) {
            addClauseIfRequired(preparedStmtList, builder);
            builder.append(" pc.id = ?");
            preparedStmtList.add(criteria.getId());
        }

        if (criteria.getExecutionPlanId() != null) {
            addClauseIfRequired(preparedStmtList, builder);
            builder.append(" pc.execution_plan_id = ?");
            preparedStmtList.add(criteria.getExecutionPlanId());
        }

        if (criteria.getName() != null) {
            addClauseIfRequired(preparedStmtList, builder);
            builder.append(" pc.name = ?");
            preparedStmtList.add(criteria.getName());
        }

        if (criteria.getStatus() != null) {
            addClauseIfRequired(preparedStmtList, builder);
            builder.append(" pc.status = ?");
            preparedStmtList.add(criteria.getStatus());
        }

        if (criteria.getUserUuid() != null) {
            addClauseIfRequired(preparedStmtList, builder);
            builder.append(" pc.created_by = ?");
            preparedStmtList.add(criteria.getUserUuid());
        }

        StringBuilder countQuery = new StringBuilder();
        if (isCount) {

            countQuery.append(PLAN_CONFIG_SEARCH_QUERY_COUNT_WRAPPER).append(builder);

            countQuery.append(") AS subquery");

            return countQuery.toString();
        }

        return builder.toString();
    }

    private void addClauseIfRequired(List<Object> preparedStmtList, StringBuilder queryString) {
        if (preparedStmtList.isEmpty()) {
            queryString.append(" WHERE ");
        } else {
            queryString.append(" AND");
        }
    }

    /**
     * @param query                           prepared Query
     * @param planConfigurationSearchCriteria plan config search criteria
     * @param preparedStmtList                values to be replaced on the query
     * @return the query by replacing the placeholders with preparedStmtList
     */
    private String getPaginatedQuery(String query, PlanConfigurationSearchCriteria planConfigurationSearchCriteria, List<Object> preparedStmtList) {
        StringBuilder paginatedQuery = new StringBuilder(query);

        // Append offset
        paginatedQuery.append(" OFFSET ? ");
        preparedStmtList.add(ObjectUtils.isEmpty(planConfigurationSearchCriteria.getOffset()) ? config.getDefaultOffset() : planConfigurationSearchCriteria.getOffset());

        // Append limit
        paginatedQuery.append(" LIMIT ? ");
        preparedStmtList.add(ObjectUtils.isEmpty(planConfigurationSearchCriteria.getLimit()) ? config.getDefaultLimit() : planConfigurationSearchCriteria.getLimit());

        return paginatedQuery.toString();
    }

    public void addActiveWhereClause(StringBuilder builder, List<Object> preparedStmtList)
    {
        addClauseIfRequired(preparedStmtList, builder);
        builder.append(" pcf.active = ?");
        preparedStmtList.add(Boolean.TRUE);

        addClauseIfRequired(preparedStmtList, builder);
        builder.append(" pca.active = ?");
        preparedStmtList.add(Boolean.TRUE);

        addClauseIfRequired(preparedStmtList, builder);
        builder.append(" pco.active = ?");
        preparedStmtList.add(Boolean.TRUE);

        addClauseIfRequired(preparedStmtList, builder);
        builder.append(" pcm.active = ?");
        preparedStmtList.add(Boolean.TRUE);
    }


}

