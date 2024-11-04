package digit.repository.querybuilder;

import digit.config.Configuration;
import digit.util.QueryUtil;
import digit.web.models.PlanFacilitySearchCriteria;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
public class PlanFacilityQueryBuilder {

    private Configuration config;

    private QueryUtil queryUtil;

    public PlanFacilityQueryBuilder(Configuration config, QueryUtil queryUtil) {
        this.config = config;
        this.queryUtil = queryUtil;
    }

    private static final String PLAN_FACILITY_QUERY =
            "SELECT plan_facility_linkage.id as plan_facility_id, " +
                    "plan_facility_linkage.tenant_id as plan_facility_tenant_id, " +
                    "plan_facility_linkage.plan_configuration_id as plan_facility_plan_configuration_id, " +
                    "plan_facility_linkage.plan_configuration_name as plan_facility_plan_configuration_name, " +
                    "plan_facility_linkage.facility_id as plan_facility_facility_id, " +
                    "plan_facility_linkage.residing_boundary as plan_facility_residing_boundary, " +
                    "plan_facility_linkage.service_boundaries as plan_facility_service_boundaries, " +
                    "plan_facility_linkage.additional_details as plan_facility_additional_details, " +
                    "plan_facility_linkage.created_by as plan_facility_created_by, " +
                    "plan_facility_linkage.created_time as plan_facility_created_time, " +
                    "plan_facility_linkage.last_modified_by as plan_facility_last_modified_by, " +
                    "plan_facility_linkage.last_modified_time as plan_facility_last_modified_time, " +
                    "plan_facility_linkage.active as plan_facility_active " +
                    "FROM plan_facility_linkage";

    private static final String PLAN_FACILITY_SEARCH_QUERY_ORDER_BY_CLAUSE = " order by plan_facility_linkage.last_modified_time desc ";

    public String getPlanFacilitySearchQuery(PlanFacilitySearchCriteria planFacilitySearchCriteria, List<Object> preparedStmtList) {
        String query = buildPlanFacilitySearchQuery(planFacilitySearchCriteria, preparedStmtList);
        query = queryUtil.addOrderByClause(query, PLAN_FACILITY_SEARCH_QUERY_ORDER_BY_CLAUSE);
        query = getPaginatedQuery(query, planFacilitySearchCriteria, preparedStmtList);
        return query;
    }

    private String buildPlanFacilitySearchQuery(PlanFacilitySearchCriteria planFacilitySearchCriteria, List<Object> preparedStmtList) {
        StringBuilder builder = new StringBuilder(PLAN_FACILITY_QUERY);

        if (!CollectionUtils.isEmpty(planFacilitySearchCriteria.getIds())) {
            Set<String> ids = planFacilitySearchCriteria.getIds();
            queryUtil.addClauseIfRequired(builder, preparedStmtList);
            builder.append(" id IN ( ").append(queryUtil.createQuery(ids.size())).append(" )");
            queryUtil.addToPreparedStatement(preparedStmtList, new LinkedHashSet<>(ids));
        }

        if (!ObjectUtils.isEmpty(planFacilitySearchCriteria.getTenantId())) {
            queryUtil.addClauseIfRequired(builder, preparedStmtList);
            builder.append(" tenant_id = ? ");
            preparedStmtList.add(planFacilitySearchCriteria.getTenantId());
        }

        if (!ObjectUtils.isEmpty(planFacilitySearchCriteria.getPlanConfigurationId())) {
            queryUtil.addClauseIfRequired(builder, preparedStmtList);
            builder.append(" plan_configuration_id = ? ");
            preparedStmtList.add(planFacilitySearchCriteria.getPlanConfigurationId());
        }

        if (!ObjectUtils.isEmpty(planFacilitySearchCriteria.getPlanConfigurationName())) {
            queryUtil.addClauseIfRequired(builder, preparedStmtList);
            builder.append(" plan_configuration_name = ? ");
            preparedStmtList.add(planFacilitySearchCriteria.getPlanConfigurationName());
        }

        if (!ObjectUtils.isEmpty(planFacilitySearchCriteria.getFacilityId())) {
            queryUtil.addClauseIfRequired(builder, preparedStmtList);
            builder.append(" facility_id = ? ");
            preparedStmtList.add(planFacilitySearchCriteria.getFacilityId());
        }

        if (!ObjectUtils.isEmpty(planFacilitySearchCriteria.getResidingBoundaries())) {
            List<String> residingBoundaries = planFacilitySearchCriteria.getResidingBoundaries();
            queryUtil.addClauseIfRequired(builder, preparedStmtList);
            builder.append(" residing_boundary IN ( ").append(queryUtil.createQuery(residingBoundaries.size())).append(" )");
            queryUtil.addToPreparedStatement(preparedStmtList, new LinkedHashSet<>(residingBoundaries));
        }

        if(!CollectionUtils.isEmpty(planFacilitySearchCriteria.getFiltersMap())) {
            queryUtil.addClauseIfRequired(builder, preparedStmtList);
            builder.append(" additional_details @> CAST( ? AS jsonb )");
            String partialQueryJsonString = queryUtil.preparePartialJsonStringFromFilterMap(planFacilitySearchCriteria.getFiltersMap());
            preparedStmtList.add(partialQueryJsonString);
        }

        return builder.toString();
    }

    private String getPaginatedQuery(String query, PlanFacilitySearchCriteria planFacilitySearchCriteria, List<Object> preparedStmtList) {
        StringBuilder paginatedQuery = new StringBuilder(query);

        // Append offset
        paginatedQuery.append(" OFFSET ? ");
        preparedStmtList.add(ObjectUtils.isEmpty(planFacilitySearchCriteria.getOffset()) ? config.getDefaultOffset() : planFacilitySearchCriteria.getOffset());

        // Append limit
        paginatedQuery.append(" LIMIT ? ");
        preparedStmtList.add(ObjectUtils.isEmpty(planFacilitySearchCriteria.getLimit()) ? config.getDefaultLimit() : planFacilitySearchCriteria.getLimit());

        return paginatedQuery.toString();
    }

}
