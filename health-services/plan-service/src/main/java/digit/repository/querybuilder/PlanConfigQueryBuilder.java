package digit.repository.querybuilder;

import digit.config.Configuration;
import digit.util.QueryUtil;
import digit.web.models.PlanConfigurationSearchCriteria;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;

import java.util.List;

import static digit.config.ServiceConstants.PERCENTAGE_WILDCARD;

@Component
public class PlanConfigQueryBuilder {

    private Configuration config;

    private QueryUtil queryUtil;

    public PlanConfigQueryBuilder(Configuration config, QueryUtil queryUtil) {
        this.config = config;
        this.queryUtil = queryUtil;
    }

    private static final String PLAN_CONFIG_SEARCH_BASE_QUERY = "SELECT id FROM plan_configuration pc ";

    private static final String PLAN_CONFIG_QUERY = "SELECT pc.id as plan_configuration_id, pc.tenant_id as plan_configuration_tenant_id, pc.name as plan_configuration_name, pc.campaign_id as plan_configuration_campaign_id, pc.status as plan_configuration_status, pc.additional_details as plan_configuration_additional_details, pc.created_by as plan_configuration_created_by, pc.created_time as plan_configuration_created_time, pc.last_modified_by as plan_configuration_last_modified_by, pc.last_modified_time as plan_configuration_last_modified_time, \n" +
            "\t   pcf.id as plan_configuration_files_id, pcf.plan_configuration_id as plan_configuration_files_plan_configuration_id, pcf.filestore_id as plan_configuration_files_filestore_id, pcf.input_file_type as plan_configuration_files_input_file_type, pcf.template_identifier as plan_configuration_files_template_identifier, pcf.active as plan_configuration_files_active, pcf.created_by as plan_configuration_files_created_by, pcf.created_time as plan_configuration_files_created_time, pcf.last_modified_by as plan_configuration_files_last_modified_by, pcf.last_modified_time as plan_configuration_files_last_modified_time,\n" +
            "\t   pca.id as plan_configuration_assumptions_id, pca.key as plan_configuration_assumptions_key, pca.value as plan_configuration_assumptions_value, pca.source as plan_configuration_assumptions_source, pca.category as plan_configuration_assumptions_category, pca.active as plan_configuration_assumptions_active, pca.plan_configuration_id as plan_configuration_assumptions_plan_configuration_id, pca.created_by as plan_configuration_assumptions_created_by, pca.created_time as plan_configuration_assumptions_created_time, pca.last_modified_by as plan_configuration_assumptions_last_modified_by, pca.last_modified_time as plan_configuration_assumptions_last_modified_time,\n" +
            "\t   pco.id as plan_configuration_operations_id, pco.input as plan_configuration_operations_input, pco.operator as plan_configuration_operations_operator, pco.assumption_value as plan_configuration_operations_assumption_value, pco.output as plan_configuration_operations_output, pco.source as plan_configuration_operations_source, pco.category as plan_configuration_operations_category, pco.active as plan_configuration_operations_active, pco.execution_order as plan_configuration_execution_order, pco.show_on_estimation_dashboard as plan_configuration_operations_show_on_estimation_dashboard,pco.plan_configuration_id as plan_configuration_operations_plan_configuration_id, pco.created_by as plan_configuration_operations_created_by, pco.created_time as plan_configuration_operations_created_time, pco.last_modified_by as plan_configuration_operations_last_modified_by, pco.last_modified_time as plan_configuration_operations_last_modified_time,\n" +
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
            queryUtil.addClauseIfRequired(builder, preparedStmtList);
            builder.append(" pc.id IN ( ").append(queryUtil.createQuery(ids.size())).append(" )");
            queryUtil.addToPreparedStatement(preparedStmtList, ids);
        }

        appendActiveRecordsFilter(builder, preparedStmtList);

        return queryUtil.addOrderByClause(builder.toString(), PLAN_CONFIG_SEARCH_QUERY_ORDER_BY_CLAUSE);
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
        String query = buildPlanConfigSearchQuery(criteria, preparedStmtList);
        query = queryUtil.addOrderByClause(query, PLAN_CONFIG_SEARCH_QUERY_ORDER_BY_CLAUSE);
        query = getPaginatedQuery(query, criteria, preparedStmtList);

        return query;
    }

    public String getPlanConfigCountQuery(PlanConfigurationSearchCriteria criteria, List<Object> preparedStmtList) {
        String query = buildPlanConfigSearchQuery(criteria, preparedStmtList);
        return PLAN_CONFIG_SEARCH_QUERY_COUNT_WRAPPER + query + ") AS subquery";
    }

    /**
     * Constructs query based on the provided search criteria
     *
     * @param criteria         The criteria used for filtering PlanConfiguration objects.
     * @param preparedStmtList A list to store prepared statement parameters.
     * @return
     */
    private String buildPlanConfigSearchQuery(PlanConfigurationSearchCriteria criteria, List<Object> preparedStmtList) {
        StringBuilder builder = new StringBuilder(PLAN_CONFIG_SEARCH_BASE_QUERY);

        if (!ObjectUtils.isEmpty(criteria.getTenantId())) {
            queryUtil.addClauseIfRequired(builder, preparedStmtList);
            builder.append(" pc.tenant_id = ?");
            preparedStmtList.add(criteria.getTenantId());
        }

        if (!ObjectUtils.isEmpty(criteria.getId())) {
            queryUtil.addClauseIfRequired(builder, preparedStmtList);
            builder.append(" pc.id = ?");
            preparedStmtList.add(criteria.getId());
        }

        if (!CollectionUtils.isEmpty(criteria.getIds())) {
            queryUtil.addClauseIfRequired(builder, preparedStmtList);
            builder.append(" pc.id IN ( ").append(queryUtil.createQuery(criteria.getIds().size())).append(" )");
            queryUtil.addToPreparedStatement(preparedStmtList, criteria.getIds());
        }

        if (!ObjectUtils.isEmpty(criteria.getCampaignId())) {
            queryUtil.addClauseIfRequired(builder, preparedStmtList);
            builder.append(" pc.campaign_id = ?");
            preparedStmtList.add(criteria.getCampaignId());
        }

        if (!ObjectUtils.isEmpty(criteria.getName())) {
            queryUtil.addClauseIfRequired(builder, preparedStmtList);
            builder.append(" pc.name ILIKE ?");
            preparedStmtList.add(PERCENTAGE_WILDCARD + criteria.getName() + PERCENTAGE_WILDCARD);
        }

        if (!CollectionUtils.isEmpty(criteria.getStatus())) {
            queryUtil.addClauseIfRequired(builder, preparedStmtList);
            builder.append(" pc.status IN ( ").append(queryUtil.createQuery(criteria.getStatus().size())).append(" )");
            queryUtil.addToPreparedStatement(preparedStmtList, criteria.getStatus());
        }

        if (!ObjectUtils.isEmpty(criteria.getUserUuid())) {
            queryUtil.addClauseIfRequired(builder, preparedStmtList);
            builder.append(" pc.created_by = ?");
            preparedStmtList.add(criteria.getUserUuid());
        }

        return builder.toString();
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

        // Append limit to query builder
        paginatedQuery.append(" LIMIT ? ");
        preparedStmtList.add(ObjectUtils.isEmpty(planConfigurationSearchCriteria.getLimit()) ? config.getDefaultLimit() : Math.min(planConfigurationSearchCriteria.getLimit(), config.getMaxLimit()));

        return paginatedQuery.toString();
    }

    /**
     * Adds a WHERE clause to filter only active records (or records where `active` is NULL)
     * for multiple database tables.
     * This ensures that only relevant active records are included in the query.
     *
     * @param builder The StringBuilder to append the WHERE clause.
     * @param preparedStmtList The list of prepared statement parameters.
     */
    public void appendActiveRecordsFilter(StringBuilder builder, List<Object> preparedStmtList) {
        queryUtil.addClauseIfRequired(builder, preparedStmtList);
        builder.append(" ( pcf.active = ? OR pcf.active IS NULL )");
        preparedStmtList.add(Boolean.TRUE);

        queryUtil.addClauseIfRequired(builder, preparedStmtList);
        builder.append(" ( pca.active = ? OR pca.active IS NULL )");
        preparedStmtList.add(Boolean.TRUE);

        queryUtil.addClauseIfRequired(builder, preparedStmtList);
        builder.append(" ( pco.active = ? OR pco.active IS NULL )");
        preparedStmtList.add(Boolean.TRUE);

        queryUtil.addClauseIfRequired(builder, preparedStmtList);
        builder.append(" ( pcm.active = ? OR pcm.active IS NULL )");
        preparedStmtList.add(Boolean.TRUE);
    }


}

