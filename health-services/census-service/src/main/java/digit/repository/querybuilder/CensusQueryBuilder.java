package digit.repository.querybuilder;

import digit.config.Configuration;
import digit.util.QueryUtil;
import digit.web.models.CensusSearchCriteria;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;

import java.util.Collections;
import java.util.List;

@Component
public class CensusQueryBuilder {

    private QueryUtil queryUtil;

    private Configuration config;

    public CensusQueryBuilder(QueryUtil queryUtil, Configuration config) {
        this.config = config;
        this.queryUtil = queryUtil;
    }

    private static final String CENSUS_SEARCH_BASE_QUERY = "SELECT id FROM census cen";

    private static final String CENSUS_QUERY = "SELECT cen.id as census_id, cen.tenant_id as census_tenant_id, cen.hierarchy_type as census_hierarchy_type, cen.boundary_code as census_boundary_code, cen.type as census_type, cen.total_population as census_total_population, cen.effective_from as census_effective_from, cen.effective_to as census_effective_to, cen.source as census_source, cen.status as census_status, cen.assignee as census_assignee, cen.boundary_ancestral_path as census_boundary_ancestral_path, cen.facility_assigned as census_facility_assigned, cen.additional_details as census_additional_details, cen.created_by as census_created_by, cen.created_time as census_created_time, cen.last_modified_by as census_last_modified_by, cen.last_modified_time as census_last_modified_time, \n" +
            "\t   pbd.id as population_by_demographics_id, pbd.census_id as population_by_demographics_census_id, pbd.demographic_variable as population_by_demographics_demographic_variable, pbd.population_distribution as population_by_demographics_population_distribution, pbd.created_by as population_by_demographics_created_by, pbd.created_time as population_by_demographics_created_time, pbd.last_modified_by as population_by_demographics_last_modified_by, pbd.last_modified_time as population_by_demographics_last_modified_time, \n" +
            "\t   adf.id as additional_field_id, adf.census_id as additional_field_census_id, adf.key as additional_field_key, adf.value as additional_field_value, adf.show_on_ui as additional_field_show_on_ui, adf.editable as additional_field_editable, adf.order as additional_field_order \n" +
            "\t   FROM census cen \n" +
            "\t   LEFT JOIN population_by_demographics pbd ON cen.id = pbd.census_id \n" +
            "\t   LEFT JOIN additional_field adf ON cen.id = adf.census_id";

    private static final String CENSUS_SEARCH_QUERY_ORDER_BY_CLAUSE = " ORDER BY cen.last_modified_time DESC";

    private static final String CENSUS_SEARCH_QUERY_COUNT_WRAPPER = "SELECT COUNT(id) AS total_count FROM ( ";

    private static final String CENSUS_STATUS_COUNT_QUERY = "SELECT COUNT(id) as census_status_count, status as census_status FROM (SELECT id, status FROM census {INTERNAL_QUERY}) as census_status_map GROUP BY census_status";

    private static final String BULK_CENSUS_UPDATE_QUERY = "UPDATE census SET status = ?, assignee = ?, last_modified_by = ?, last_modified_time = ?, additional_details = ?, facility_assigned = ? WHERE id = ?";

    /**
     * Constructs a SQL query string for searching Census records based on the provided search criteria.
     * Also adds an ORDER BY clause and handles pagination.
     *
     * @param ids   The census ids used for filtering Census records.
     * @param preparedStmtList A list to store prepared statement parameters.
     * @return A complete SQL query string for searching Census records.
     */
    public String getCensusQuery(List<String> ids, List<Object> preparedStmtList) {
        String query = buildCensusQuery(ids, preparedStmtList);
        query = queryUtil.addOrderByClause(query, CENSUS_SEARCH_QUERY_ORDER_BY_CLAUSE);
        return query;
    }

    private String buildCensusQuery(List<String> ids, List<Object> preparedStmtList) {
        StringBuilder builder = new StringBuilder(CENSUS_QUERY);

        if (!CollectionUtils.isEmpty(ids)) {
            queryUtil.addClauseIfRequired(builder, preparedStmtList);
            builder.append(" cen.id IN ( ").append(queryUtil.createQuery(ids.size())).append(" )");
            queryUtil.addToPreparedStatement(preparedStmtList, ids);
        }

        return builder.toString();
    }

    public String getCensusSearchQuery(CensusSearchCriteria censusSearchCriteria, List<Object> preparedStmtList) {
        String query = buildCensusSearchQuery(censusSearchCriteria, preparedStmtList);
        query = queryUtil.addOrderByClause(query, CENSUS_SEARCH_QUERY_ORDER_BY_CLAUSE);
        query = getPaginatedQuery(query, preparedStmtList, censusSearchCriteria);
        return query;
    }

    /**
     * Constructs the count query to get the total count of census based on search criteria.
     *
     * @param searchCriteria   The criteria used for filtering Census records.
     * @param preparedStmtList A list to store prepared statement parameters.
     * @return A SQL query string to get the total count of Census records for a given search criteria.
     */
    public String getCensusCountQuery(CensusSearchCriteria searchCriteria, List<Object> preparedStmtList) {
        String query = buildCensusSearchQuery(searchCriteria, preparedStmtList);
        return CENSUS_SEARCH_QUERY_COUNT_WRAPPER + query + ") AS subquery";
    }

    /**
     * Constructs the status count query to get the count of census based on their current status for the given search criteria
     *
     * @param searchCriteria   The criteria used for filtering Census records.
     * @param preparedStmtList A list to store prepared statement parameters.
     * @return A SQL query string to get the status count of Census records for a given search criteria.
     */
    public String getCensusStatusCountQuery(CensusSearchCriteria searchCriteria, List<Object> preparedStmtList) {
        StringBuilder builder = new StringBuilder();

        if (!ObjectUtils.isEmpty(searchCriteria.getTenantId())) {
            queryUtil.addClauseIfRequired(builder, preparedStmtList);
            builder.append(" tenant_id = ?");
            preparedStmtList.add(searchCriteria.getTenantId());
        }

        if (!ObjectUtils.isEmpty(searchCriteria.getSource())) {
            queryUtil.addClauseIfRequired(builder, preparedStmtList);
            builder.append(" source = ?");
            preparedStmtList.add(searchCriteria.getSource());
        }

        if (!CollectionUtils.isEmpty(searchCriteria.getJurisdiction())) {
            queryUtil.addClauseIfRequired(builder, preparedStmtList);
            builder.append(" ARRAY [ ").append(queryUtil.createQuery(searchCriteria.getJurisdiction().size())).append(" ]").append("::text[] ");
            builder.append(" && string_to_array(boundary_ancestral_path, '|') ");
            queryUtil.addToPreparedStatement(preparedStmtList, searchCriteria.getJurisdiction());
        }
        return CENSUS_STATUS_COUNT_QUERY.replace("{INTERNAL_QUERY}", builder);
    }

    /**
     * Constructs query based on the provided search criteria
     *
     * @param criteria         The criteria used for filtering Census ids.
     * @param preparedStmtList A list to store prepared statement parameters.
     * @return SQL query string for searching Census ids based on search criteria
     */
    private String buildCensusSearchQuery(CensusSearchCriteria criteria, List<Object> preparedStmtList) {
        StringBuilder builder = new StringBuilder(CENSUS_SEARCH_BASE_QUERY);

        if (!ObjectUtils.isEmpty(criteria.getId())) {
            queryUtil.addClauseIfRequired(builder, preparedStmtList);
            builder.append(" id = ?");
            preparedStmtList.add(criteria.getId());
        }

        if (!CollectionUtils.isEmpty(criteria.getIds())) {
            queryUtil.addClauseIfRequired(builder, preparedStmtList);
            builder.append(" id IN ( ").append(queryUtil.createQuery(criteria.getIds().size())).append(" )");
            queryUtil.addToPreparedStatement(preparedStmtList, criteria.getIds());
        }

        if (!ObjectUtils.isEmpty(criteria.getTenantId())) {
            queryUtil.addClauseIfRequired(builder, preparedStmtList);
            builder.append(" tenant_id = ?");
            preparedStmtList.add(criteria.getTenantId());
        }

        if (!ObjectUtils.isEmpty(criteria.getSource())) {
            queryUtil.addClauseIfRequired(builder, preparedStmtList);
            builder.append(" source = ?");
            preparedStmtList.add(criteria.getSource());
        }

        if (!ObjectUtils.isEmpty(criteria.getStatus())) {
            queryUtil.addClauseIfRequired(builder, preparedStmtList);
            builder.append(" status = ?");
            preparedStmtList.add(criteria.getStatus());
        }

        if (!ObjectUtils.isEmpty(criteria.getFacilityAssigned())) {
            queryUtil.addClauseIfRequired(builder, preparedStmtList);
            builder.append(" facility_assigned = ?");
            preparedStmtList.add(criteria.getFacilityAssigned());
        }

        if (!ObjectUtils.isEmpty(criteria.getEffectiveTo())) {
            queryUtil.addClauseIfRequired(builder, preparedStmtList);
            if (criteria.getEffectiveTo() == 0) {
                builder.append(" effective_to IS NULL ");
            } else {
                builder.append(" effective_to = ?");
                preparedStmtList.add(criteria.getEffectiveTo());
            }
        }

        if (!CollectionUtils.isEmpty(criteria.getAreaCodes())) {
            queryUtil.addClauseIfRequired(builder, preparedStmtList);
            builder.append(" boundary_code IN ( ").append(queryUtil.createQuery(criteria.getAreaCodes().size())).append(" )");
            queryUtil.addToPreparedStatement(preparedStmtList, criteria.getAreaCodes());
        }

        if (!ObjectUtils.isEmpty(criteria.getAssignee())) {
            queryUtil.addClauseIfRequired(builder, preparedStmtList);
            builder.append(" ARRAY [ ").append(queryUtil.createQuery(Collections.singleton(criteria.getAssignee()).size())).append(" ]").append("::text[] ");
            builder.append(" && string_to_array(assignee, ',') ");
            queryUtil.addToPreparedStatement(preparedStmtList, Collections.singleton(criteria.getAssignee()));
        }

        if (!CollectionUtils.isEmpty(criteria.getJurisdiction())) {
            queryUtil.addClauseIfRequired(builder, preparedStmtList);
            builder.append(" ARRAY [ ").append(queryUtil.createQuery(criteria.getJurisdiction().size())).append(" ]").append("::text[] ");
            builder.append(" && string_to_array(boundary_ancestral_path, '|') ");
            queryUtil.addToPreparedStatement(preparedStmtList, criteria.getJurisdiction());
        }

        return builder.toString();
    }

    public String getBulkCensusQuery() {
        return BULK_CENSUS_UPDATE_QUERY;
    }

    /**
     * This method appends pagination i.e. limit and offset to the query.
     *
     * @param query            the query to which pagination is to be added.
     * @param preparedStmtList prepared statement list to add limit and offset.
     * @return a query with pagination
     */
    public String getPaginatedQuery(String query, List<Object> preparedStmtList, CensusSearchCriteria searchCriteria) {
        StringBuilder paginatedQuery = new StringBuilder(query);

        // Append offset
        paginatedQuery.append(" OFFSET ? ");
        preparedStmtList.add(!ObjectUtils.isEmpty(searchCriteria.getOffset()) ? searchCriteria.getOffset() : config.getDefaultOffset());

        // Append limit
        paginatedQuery.append(" LIMIT ? ");
        preparedStmtList.add(ObjectUtils.isEmpty(searchCriteria.getLimit()) ? config.getDefaultLimit() : Math.min(searchCriteria.getLimit(), config.getDefaultMaxLimit()));

        return paginatedQuery.toString();
    }
}
