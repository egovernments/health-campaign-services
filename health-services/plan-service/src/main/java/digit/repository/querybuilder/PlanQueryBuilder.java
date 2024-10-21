package digit.repository.querybuilder;

import digit.config.Configuration;
import digit.util.QueryUtil;
import digit.web.models.PlanSearchCriteria;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;

@Component
public class PlanQueryBuilder {

    private Configuration config;

    private QueryUtil queryUtil;

    public PlanQueryBuilder(Configuration config, QueryUtil queryUtil) {
        this.config = config;
        this.queryUtil = queryUtil;
    }

    private static final String PLAN_SEARCH_BASE_QUERY = "SELECT id FROM plan ";

    private static final String PLAN_QUERY = "SELECT plan.id as plan_id, plan.tenant_id as plan_tenant_id, plan.locality as plan_locality, plan.campaign_id as plan_campaign_id, plan.plan_configuration_id as plan_plan_configuration_id, plan.boundary_ancestral_path as plan_boundary_ancestral_path, plan.additional_details as plan_additional_details, plan.created_by as plan_created_by, plan.created_time as plan_created_time, plan.last_modified_by as plan_last_modified_by, plan.last_modified_time as plan_last_modified_time,\n" +
            "\t   plan_activity.id as plan_activity_id, plan_activity.code as plan_activity_code, plan_activity.description as plan_activity_description, plan_activity.planned_start_date as plan_activity_planned_start_date, plan_activity.planned_end_date as plan_activity_planned_end_date, plan_activity.dependencies as plan_activity_dependencies, plan_activity.plan_id as plan_activity_plan_id, plan_activity.created_by as plan_activity_created_by, plan_activity.created_time as plan_activity_created_time, plan_activity.last_modified_by as plan_activity_last_modified_by, plan_activity.last_modified_time as plan_activity_last_modified_time,\n" +
            "\t   plan_activity_condition.id as plan_activity_condition_id, plan_activity_condition.entity as plan_activity_condition_entity, plan_activity_condition.entity_property as plan_activity_condition_entity_property, plan_activity_condition.expression as plan_activity_condition_expression, plan_activity_condition.activity_id as plan_activity_condition_activity_id, plan_activity_condition.is_active as plan_activity_condition_is_active, plan_activity_condition.created_by as plan_activity_condition_created_by, plan_activity_condition.created_time as plan_activity_condition_created_time, plan_activity_condition.last_modified_by as plan_activity_condition_last_modified_by, plan_activity_condition.last_modified_time as plan_activity_condition_last_modified_time,\n" +
            "\t   plan_resource.id as plan_resource_id, plan_resource.resource_type as plan_resource_resource_type, plan_resource.estimated_number as plan_resource_estimated_number, plan_resource.plan_id as plan_resource_plan_id, plan_resource.activity_code as plan_resource_activity_code, plan_resource.created_by as plan_resource_created_by, plan_resource.created_time as plan_resource_created_time, plan_resource.last_modified_by as plan_resource_last_modified_by, plan_resource.last_modified_time as plan_resource_last_modified_time,\n" +
            "\t   plan_target.id as plan_target_id, plan_target.metric as plan_target_metric, plan_target.metric_value as plan_target_metric_value, plan_target.metric_comparator as plan_target_metric_comparator, plan_target.metric_unit as plan_target_metric_unit, plan_target.plan_id as plan_target_plan_id, plan_target.activity_code as plan_target_activity_code, plan_target.created_by as plan_target_created_by, plan_target.created_time as plan_target_created_time, plan_target.last_modified_by as plan_target_last_modified_by, plan_target.last_modified_time as plan_target_last_modified_time\n" +
            "\t   FROM plan \n" +
            "\t   LEFT JOIN plan_activity ON plan.id = plan_activity.plan_id\n" +
            "\t   LEFT JOIN plan_activity_condition ON plan_activity.id = plan_activity_condition.activity_id\n" +
            "\t   LEFT JOIN plan_resource ON plan.id = plan_resource.plan_id\n" +
            "\t   LEFT JOIN plan_target ON plan.id = plan_target.plan_id";

    private static final String PLAN_SEARCH_QUERY_ORDER_BY_CLAUSE = " order by plan.last_modified_time desc ";

    public String getPlanQuery(List<String> ids, List<Object> preparedStmtList) {
        return buildPlanQuery(ids, preparedStmtList);
    }

    private String buildPlanQuery(List<String> ids, List<Object> preparedStmtList) {
        StringBuilder builder = new StringBuilder(PLAN_QUERY);

        if (!CollectionUtils.isEmpty(ids)) {
            queryUtil.addClauseIfRequired(builder, preparedStmtList);
            builder.append(" plan.id IN ( ").append(queryUtil.createQuery(ids.size())).append(" )");
            queryUtil.addToPreparedStatement(preparedStmtList, new LinkedHashSet<>(ids));
        }

        return builder.toString();
    }

    public String getPlanSearchQuery(PlanSearchCriteria planSearchCriteria, List<Object> preparedStmtList) {
        String query = buildPlanSearchQuery(planSearchCriteria, preparedStmtList);
        query = queryUtil.addOrderByClause(query, PLAN_SEARCH_QUERY_ORDER_BY_CLAUSE);
        query = getPaginatedQuery(query, planSearchCriteria, preparedStmtList);
        return query;
    }

    /**
     * Method to build query dynamically based on the criteria passed to the method
     *
     * @param planSearchCriteria
     * @param preparedStmtList
     * @return
     */
    private String buildPlanSearchQuery(PlanSearchCriteria planSearchCriteria, List<Object> preparedStmtList) {
        StringBuilder builder = new StringBuilder(PLAN_SEARCH_BASE_QUERY);

        if (!ObjectUtils.isEmpty(planSearchCriteria.getTenantId())) {
            queryUtil.addClauseIfRequired(builder, preparedStmtList);
            builder.append(" tenant_id = ? ");
            preparedStmtList.add(planSearchCriteria.getTenantId());
        }

        if (!CollectionUtils.isEmpty(planSearchCriteria.getIds())) {
            queryUtil.addClauseIfRequired(builder, preparedStmtList);
            builder.append(" id IN ( ").append(queryUtil.createQuery(planSearchCriteria.getIds().size())).append(" )");
            queryUtil.addToPreparedStatement(preparedStmtList, planSearchCriteria.getIds());
        }

        if (!ObjectUtils.isEmpty(planSearchCriteria.getLocality())) {
            queryUtil.addClauseIfRequired(builder, preparedStmtList);
            builder.append(" locality = ? ");
            preparedStmtList.add(planSearchCriteria.getLocality());
        }

        if (!ObjectUtils.isEmpty(planSearchCriteria.getCampaignId())) {
            queryUtil.addClauseIfRequired(builder, preparedStmtList);
            builder.append(" campaign_id = ? ");
            preparedStmtList.add(planSearchCriteria.getCampaignId());
        }

        if (!ObjectUtils.isEmpty(planSearchCriteria.getPlanConfigurationId())) {
            queryUtil.addClauseIfRequired(builder, preparedStmtList);
            builder.append(" plan_configuration_id = ? ");
            preparedStmtList.add(planSearchCriteria.getPlanConfigurationId());
        }

        if (!ObjectUtils.isEmpty(planSearchCriteria.getStatus())) {
            queryUtil.addClauseIfRequired(builder, preparedStmtList);
            builder.append(" status = ? ");
            preparedStmtList.add(planSearchCriteria.getStatus());
        }

        if (!ObjectUtils.isEmpty(planSearchCriteria.getAssignee())) {
            queryUtil.addClauseIfRequired(builder, preparedStmtList);
            builder.append(" assignee = ? ");
            preparedStmtList.add(planSearchCriteria.getAssignee());
        }

        if (!CollectionUtils.isEmpty(planSearchCriteria.getJurisdiction())) {
            queryUtil.addClauseIfRequired(builder, preparedStmtList);
            builder.append(" ARRAY [ ")
                    .append(queryUtil.createQuery(planSearchCriteria.getJurisdiction().size()))
                    .append(" ]::text[] ");

            builder.append(" && string_to_array(boundary_ancestral_path, '|') ");
            queryUtil.addToPreparedStatement(preparedStmtList, new HashSet<>(planSearchCriteria.getJurisdiction()));
        }


        return builder.toString();
    }

    private String getPaginatedQuery(String query, PlanSearchCriteria planSearchCriteria, List<Object> preparedStmtList) {
        StringBuilder paginatedQuery = new StringBuilder(query);

        // Append offset
        paginatedQuery.append(" OFFSET ? ");
        preparedStmtList.add(ObjectUtils.isEmpty(planSearchCriteria.getOffset()) ? config.getDefaultOffset() : planSearchCriteria.getOffset());

        // Append limit
        paginatedQuery.append(" LIMIT ? ");
        preparedStmtList.add(ObjectUtils.isEmpty(planSearchCriteria.getLimit()) ? config.getDefaultLimit() : planSearchCriteria.getLimit());

        return paginatedQuery.toString();
    }

}
