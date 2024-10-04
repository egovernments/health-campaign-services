package digit.repository.querybuilder;

import digit.util.QueryUtil;
import digit.web.models.CensusSearchCriteria;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.HashSet;
import java.util.List;

@Component
public class CensusQueryBuilder {

    private QueryUtil queryUtil;

    public CensusQueryBuilder(QueryUtil queryUtil) {
        this.queryUtil = queryUtil;
    }

    private static final String CENSUS_ID_SEARCH_BASE_QUERY = "SELECT id FROM census cen ";

    private static final String CENSUS_SEARCH_BASE_QUERY = "SELECT cen.id as census_id, cen.tenant_id as census_tenant_id, cen.hierarchy_type as census_hierarchy_type, cen.boundary_code as census_boundary_code, cen.type as census_type, cen.total_population as census_total_population, cen.effective_from as census_effective_from, cen.effective_to as census_effective_to, cen.source as census_source, cen.status as census_status, cen.assignee as census_assignee, cen.materialized_path as census_materialized_path, cen.additional_details as census_additional_details, cen.created_by as census_created_by, cen.created_time as census_created_time, cen.last_modified_by as census_last_modified_by, cen.last_modified_time as census_last_modified_time, \n" +
            "\t   pbd.id as population_by_demographics_id, pbd.census_id as population_by_demographics_census_id, pbd.demographic_variable as population_by_demographics_demographic_variable, pbd.population_distribution as population_by_demographics_population_distribution, pbd.created_by as population_by_demographics_created_by, pbd.created_time as population_by_demographics_created_time, pbd.last_modified_by as population_by_demographics_last_modified_by, pbd.last_modified_time as population_by_demographics_last_modified_time,\n" +
            "\t   FROM census cen \n" +
            "\t   LEFT JOIN population_by_demographics pbd ON cen.id = pbd.census_id";

    private static final String CENSUS_SEARCH_QUERY_ORDER_BY_CLAUSE = " ORDER BY cen.last_modified_time DESC";

    private static final String CENSUS_SEARCH_QUERY_COUNT_WRAPPER = "SELECT COUNT(*) AS total_count FROM ( ";

    /**
     * Constructs a SQL query string for searching Census records based on the provided search criteria.
     * Also adds an ORDER BY clause and handles pagination.
     *
     * @param censusSearchCriteria The criteria used for filtering Census records.
     * @param preparedStmtList     A list to store prepared statement parameters.
     * @return A complete SQL query string for searching Census records.
     */
    public String getCensusSearchQuery(CensusSearchCriteria censusSearchCriteria, List<Object> preparedStmtList) {
        String query = buildCensusSearchQuery(censusSearchCriteria, preparedStmtList, Boolean.FALSE);
        query = queryUtil.addOrderByClause(query, CENSUS_SEARCH_QUERY_ORDER_BY_CLAUSE);
        query = queryUtil.getPaginatedQuery(query, preparedStmtList);

        return query;
    }

    /**
     * Constructs query based on the provided search criteria
     *
     * @param criteria         The criteria used for filtering Census records.
     * @param preparedStmtList A list to store prepared statement parameters.
     * @return SQL query string for searching Census records
     */
    private String buildCensusSearchQuery(CensusSearchCriteria criteria, List<Object> preparedStmtList, Boolean isCount) {
        StringBuilder builder = new StringBuilder(CENSUS_ID_SEARCH_BASE_QUERY);

        if (criteria.getId() != null) {
            queryUtil.addClauseIfRequired(builder, preparedStmtList);
            builder.append(" cen.id = ?");
            preparedStmtList.add(criteria.getId());
        }

        if (criteria.getTenantId() != null) {
            queryUtil.addClauseIfRequired(builder, preparedStmtList);
            builder.append(" cen.tenant_id = ?");
            preparedStmtList.add(criteria.getTenantId());
        }

        if (criteria.getStatus() != null) {
            queryUtil.addClauseIfRequired(builder, preparedStmtList);
            builder.append(" cen.status = ?");
            preparedStmtList.add(criteria.getStatus());
        }

        if (criteria.getAssignee() != null) {
            queryUtil.addClauseIfRequired(builder, preparedStmtList);
            builder.append(" cen.assignee = ?");
            preparedStmtList.add(criteria.getAssignee());
        }

        if (!CollectionUtils.isEmpty(criteria.getAreaCodes())) {
            queryUtil.addClauseIfRequired(builder, preparedStmtList);
            builder.append(" cen.boundary_code IN ( ").append(queryUtil.createQuery(criteria.getAreaCodes().size())).append(" )");
            queryUtil.addToPreparedStatement(preparedStmtList, new HashSet<>(criteria.getAreaCodes()));
        }

        if (!CollectionUtils.isEmpty(criteria.getMaterializedPath())) {
            queryUtil.addClauseIfRequired(builder, preparedStmtList);
            builder.append(" ARRAY [ ").append(queryUtil.createQuery(criteria.getMaterializedPath().size())).append(" ]").append("::text[] ");
            builder.append(" && string_to_array(census_materialized_path, ',') ");
            queryUtil.addToPreparedStatement(preparedStmtList, new HashSet<>(criteria.getMaterializedPath()));
        }

        return builder.toString();
    }

    /**
     * Constructs a SQL query string for searching Census records based on the provided list of Census Ids.
     *
     * @param censusIds        The list of census ids for searching census records
     * @param preparedStmtList A list to store prepared statement parameters.
     * @return A complete SQL query string for searching Census records.
     */
    public String getCensusQuery(List<String> censusIds, List<Object> preparedStmtList) {
        return buildCensusQuery(censusIds, preparedStmtList);
    }

    /**
     * Constructs query based on the list of Census id provided.
     *
     * @param censusIds        The list of census ids for searching census records
     * @param preparedStmtList A list to store prepared statement parameters.
     * @return A complete SQL query string for searching Census records.
     */
    private String buildCensusQuery(List<String> censusIds, List<Object> preparedStmtList) {
        StringBuilder builder = new StringBuilder(CENSUS_SEARCH_BASE_QUERY);

        if (!CollectionUtils.isEmpty(censusIds)) {
            queryUtil.addClauseIfRequired(builder, preparedStmtList);
            builder.append(" cen.id IN ( ").append(queryUtil.createQuery(censusIds.size())).append(" )");
            queryUtil.addToPreparedStatement(preparedStmtList, new HashSet<>(censusIds));
        }

        return queryUtil.addOrderByClause(builder.toString(), CENSUS_SEARCH_QUERY_ORDER_BY_CLAUSE);
    }

}
