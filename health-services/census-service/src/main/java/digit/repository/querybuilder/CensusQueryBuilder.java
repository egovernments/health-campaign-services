package digit.repository.querybuilder;

import digit.config.Configuration;
import digit.util.QueryUtil;
import digit.web.models.CensusSearchCriteria;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;

import java.util.HashSet;
import java.util.List;

@Component
public class CensusQueryBuilder {

    private QueryUtil queryUtil;

    private Configuration config;

    public CensusQueryBuilder(QueryUtil queryUtil, Configuration config) {
        this.config = config;
        this.queryUtil = queryUtil;
    }

    private static final String CENSUS_SEARCH_BASE_QUERY = "SELECT cen.id as census_id, cen.tenant_id as census_tenant_id, cen.hierarchy_type as census_hierarchy_type, cen.boundary_code as census_boundary_code, cen.type as census_type, cen.total_population as census_total_population, cen.effective_from as census_effective_from, cen.effective_to as census_effective_to, cen.source as census_source, cen.status as census_status, cen.assignee as census_assignee, cen.boundary_ancestral_path as census_boundary_ancestral_path, cen.facility_assigned as census_facility_assigned, cen.additional_details as census_additional_details, cen.created_by as census_created_by, cen.created_time as census_created_time, cen.last_modified_by as census_last_modified_by, cen.last_modified_time as census_last_modified_time, \n" +
            "\t   pbd.id as population_by_demographics_id, pbd.census_id as population_by_demographics_census_id, pbd.demographic_variable as population_by_demographics_demographic_variable, pbd.population_distribution as population_by_demographics_population_distribution, pbd.created_by as population_by_demographics_created_by, pbd.created_time as population_by_demographics_created_time, pbd.last_modified_by as population_by_demographics_last_modified_by, pbd.last_modified_time as population_by_demographics_last_modified_time \n" +
            "\t   FROM census cen \n" +
            "\t   LEFT JOIN population_by_demographics pbd ON cen.id = pbd.census_id";

    private static final String CENSUS_SEARCH_QUERY_ORDER_BY_CLAUSE = " ORDER BY cen.last_modified_time DESC";

    private static final String CENSUS_SEARCH_QUERY_COUNT_WRAPPER = "SELECT COUNT(*) AS total_count FROM ( ";

    private static final String CENSUS_STATUS_COUNT_WRAPPER = "SELECT COUNT(census_id) as census_status_count, census_status FROM ({INTERNAL_QUERY}) as census_status_map GROUP BY census_status";

    /**
     * Constructs a SQL query string for searching Census records based on the provided search criteria.
     * Also adds an ORDER BY clause and handles pagination.
     *
     * @param searchCriteria   The criteria used for filtering Census records.
     * @param preparedStmtList A list to store prepared statement parameters.
     * @return A complete SQL query string for searching Census records.
     */
    public String getCensusQuery(CensusSearchCriteria searchCriteria, List<Object> preparedStmtList) {
        String query = buildCensusQuery(searchCriteria, preparedStmtList, Boolean.FALSE, Boolean.FALSE);
        query = queryUtil.addOrderByClause(query, CENSUS_SEARCH_QUERY_ORDER_BY_CLAUSE);
        query = getPaginatedQuery(query, preparedStmtList, searchCriteria);
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
        return buildCensusQuery(searchCriteria, preparedStmtList, Boolean.TRUE, Boolean.FALSE);
    }

    /**
     * Constructs the status count query to get the count of census based on their current status for the given search criteria
     *
     * @param searchCriteria   The criteria used for filtering Census records.
     * @param preparedStmtList A list to store prepared statement parameters.
     * @return A SQL query string to get the status count of Census records for a given search criteria.
     */
    public String getCensusStatusCountQuery(CensusSearchCriteria searchCriteria, List<Object> preparedStmtList) {
        CensusSearchCriteria censusSearchCriteria = CensusSearchCriteria.builder().tenantId(searchCriteria.getTenantId()).source(searchCriteria.getSource()).jurisdiction(searchCriteria.getJurisdiction()).build();
        return buildCensusQuery(censusSearchCriteria, preparedStmtList, Boolean.FALSE, Boolean.TRUE);
    }

    /**
     * Constructs query based on the provided search criteria
     *
     * @param criteria         The criteria used for filtering Census records.
     * @param preparedStmtList A list to store prepared statement parameters.
     * @return SQL query string for searching Census records
     */
    private String buildCensusQuery(CensusSearchCriteria criteria, List<Object> preparedStmtList, Boolean isCount, Boolean isStatusCount) {
        StringBuilder builder = new StringBuilder(CENSUS_SEARCH_BASE_QUERY);

        if (!ObjectUtils.isEmpty(criteria.getId())) {
            queryUtil.addClauseIfRequired(builder, preparedStmtList);
            builder.append(" cen.id = ?");
            preparedStmtList.add(criteria.getId());
        }

        if (!ObjectUtils.isEmpty(criteria.getTenantId())) {
            queryUtil.addClauseIfRequired(builder, preparedStmtList);
            builder.append(" cen.tenant_id = ?");
            preparedStmtList.add(criteria.getTenantId());
        }

        if (!ObjectUtils.isEmpty(criteria.getStatus())) {
            queryUtil.addClauseIfRequired(builder, preparedStmtList);
            builder.append(" cen.status = ?");
            preparedStmtList.add(criteria.getStatus());
        }

        if (!ObjectUtils.isEmpty(criteria.getAssignee())) {
            queryUtil.addClauseIfRequired(builder, preparedStmtList);
            builder.append(" cen.assignee = ?");
            preparedStmtList.add(criteria.getAssignee());
        }

        if (!ObjectUtils.isEmpty(criteria.getSource())) {
            queryUtil.addClauseIfRequired(builder, preparedStmtList);
            builder.append(" cen.source = ?");
            preparedStmtList.add(criteria.getSource());
        }

        if (!ObjectUtils.isEmpty(criteria.getFacilityAssigned())) {
            queryUtil.addClauseIfRequired(builder, preparedStmtList);
            builder.append(" cen.facility_assigned = ?");
            preparedStmtList.add(criteria.getFacilityAssigned());
        }

        if (!ObjectUtils.isEmpty(criteria.getEffectiveTo())) {
            queryUtil.addClauseIfRequired(builder, preparedStmtList);
            if (criteria.getEffectiveTo() == 0) {
                builder.append(" cen.effective_to IS NULL ");
            } else {
                builder.append(" cen.effective_to = ?");
                preparedStmtList.add(criteria.getEffectiveTo());
            }
        }

        if (!CollectionUtils.isEmpty(criteria.getAreaCodes())) {
            queryUtil.addClauseIfRequired(builder, preparedStmtList);
            builder.append(" cen.boundary_code IN ( ").append(queryUtil.createQuery(criteria.getAreaCodes().size())).append(" )");
            queryUtil.addToPreparedStatement(preparedStmtList, new HashSet<>(criteria.getAreaCodes()));
        }

        if (!CollectionUtils.isEmpty(criteria.getJurisdiction())) {
            queryUtil.addClauseIfRequired(builder, preparedStmtList);
            builder.append(" ARRAY [ ").append(queryUtil.createQuery(criteria.getJurisdiction().size())).append(" ]").append("::text[] ");
            builder.append(" && string_to_array(boundary_ancestral_path, '|') ");
            queryUtil.addToPreparedStatement(preparedStmtList, new HashSet<>(criteria.getJurisdiction()));
        }

        StringBuilder countQuery = new StringBuilder();
        if (isCount) {
            countQuery.append(CENSUS_SEARCH_QUERY_COUNT_WRAPPER).append(builder);
            countQuery.append(") AS subquery");

            return countQuery.toString();
        }

        if (isStatusCount) {
            return CENSUS_STATUS_COUNT_WRAPPER.replace("{INTERNAL_QUERY}", builder);
        }

        return builder.toString();
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
        preparedStmtList.add(!ObjectUtils.isEmpty(searchCriteria.getLimit()) ? searchCriteria.getLimit() : config.getDefaultLimit());

        return paginatedQuery.toString();
    }

}
