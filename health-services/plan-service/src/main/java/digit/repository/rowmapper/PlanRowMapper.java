package digit.repository.rowmapper;

import digit.util.QueryUtil;
import digit.web.models.*;
import org.egov.common.contract.models.AuditDetails;
import org.postgresql.util.PGobject;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

@Component
public class PlanRowMapper implements ResultSetExtractor<List<Plan>> {

    private QueryUtil queryUtil;

    public PlanRowMapper(QueryUtil queryUtil) {
        this.queryUtil = queryUtil;
    }

    @Override
    public List<Plan> extractData(ResultSet rs) throws SQLException, DataAccessException {
        Map<String, Plan> planMap = new LinkedHashMap<>();
        Map<String, Activity> activityMap = new LinkedHashMap<>();
        Map<String, Condition> conditionMap = new LinkedHashMap<>();
        Map<String, Resource> resourceMap = new LinkedHashMap<>();
        Map<String, Target> targetMap = new LinkedHashMap<>();

        // Traverse through result set and create plan objects
        while (rs.next()) {
            String planId = rs.getString("plan_id");

            Plan planEntry = planMap.get(planId);

            if (ObjectUtils.isEmpty(planEntry)) {
                planEntry = new Plan();

                // Prepare audit details
                AuditDetails auditDetails = AuditDetails.builder()
                        .createdBy(rs.getString("plan_created_by"))
                        .createdTime(rs.getLong("plan_created_time"))
                        .lastModifiedBy(rs.getString("plan_last_modified_by"))
                        .lastModifiedTime(rs.getLong("plan_last_modified_time"))
                        .build();

                String commaSeparatedAssignee = rs.getString("plan_assignee");
                List<String> assignee = !ObjectUtils.isEmpty(commaSeparatedAssignee) ? Arrays.asList(commaSeparatedAssignee.split(",")) : null;

                // Prepare plan object
                planEntry.setId(planId);
                planEntry.setTenantId(rs.getString("plan_tenant_id"));
                planEntry.setLocality(rs.getString("plan_locality"));
                planEntry.setCampaignId(rs.getString("plan_campaign_id"));
                planEntry.setStatus(rs.getString("plan_status"));
                planEntry.setAssignee(assignee);
                planEntry.setPlanConfigurationId(rs.getString("plan_plan_configuration_id"));
                planEntry.setBoundaryAncestralPath(rs.getString("plan_boundary_ancestral_path"));
                planEntry.setAdditionalDetails(queryUtil.getAdditionalDetail((PGobject) rs.getObject("plan_additional_details")));
                planEntry.setAuditDetails(auditDetails);

            }

            addActivities(rs, planEntry, activityMap, conditionMap);
            addResources(rs, planEntry, resourceMap);
            addTargets(rs, planEntry, targetMap);
            planMap.put(planId, planEntry);
        }

        return new ArrayList<>(planMap.values());
    }

    private void addActivities(ResultSet rs, Plan plan,
                               Map<String, Activity> activityMap, Map<String, Condition> conditionMap) throws SQLException, DataAccessException {

        String activityId = rs.getString("plan_activity_id");

        if (!ObjectUtils.isEmpty(activityId) && activityMap.containsKey(activityId)) {
            addActivityConditions(rs, activityMap.get(activityId), conditionMap);
            return;
        } else if (ObjectUtils.isEmpty(activityId)) {
            // Set activities list to empty if no activity found
            plan.setActivities(new ArrayList<>());
            return;
        }

        String dependencies = rs.getString("plan_activity_dependencies");
        AuditDetails auditDetails = AuditDetails.builder()
                .createdBy(rs.getString("plan_activity_created_by"))
                .createdTime(rs.getLong("plan_activity_created_time"))
                .lastModifiedBy(rs.getString("plan_activity_last_modified_by"))
                .lastModifiedTime(rs.getLong("plan_activity_last_modified_time"))
                .build();

        Activity activity = Activity.builder()
                .id(activityId)
                .code(rs.getString("plan_activity_code"))
                .description(rs.getString("plan_activity_description"))
                .plannedStartDate(rs.getLong("plan_activity_planned_start_date"))
                .plannedEndDate(rs.getLong("plan_activity_planned_end_date"))
                .dependencies(ObjectUtils.isEmpty(dependencies) ? new ArrayList<>() : Arrays.asList(rs.getString("plan_activity_dependencies").split(",")))
                .build();

        addActivityConditions(rs, activity, conditionMap);

        // Creates a new Activity list if it is not already initialized in Plan object and adds the Activity object.
        if (CollectionUtils.isEmpty(plan.getActivities())) {
            List<Activity> activityList = new ArrayList<>();
            activityList.add(activity);
            plan.setActivities(activityList);
        } else {
            plan.getActivities().add(activity);
        }

        activityMap.put(activity.getId(), activity);

    }

    private void addActivityConditions(ResultSet rs, Activity activity, Map<String, Condition> conditionMap) throws SQLException, DataAccessException {
        String conditionId = rs.getString("plan_activity_condition_id");

        if (ObjectUtils.isEmpty(conditionId) || conditionMap.containsKey(conditionId)) {
            List<Condition> conditionList = new ArrayList<>();
            activity.setConditions(conditionList);
            return;
        }

        AuditDetails auditDetails = AuditDetails.builder()
                .createdBy(rs.getString("plan_activity_condition_created_by"))
                .createdTime(rs.getLong("plan_activity_condition_created_time"))
                .lastModifiedBy(rs.getString("plan_activity_condition_last_modified_by"))
                .lastModifiedTime(rs.getLong("plan_activity_condition_last_modified_time"))
                .build();

        Condition condition = Condition.builder()
                .id(conditionId)
                .entity(rs.getString("plan_activity_condition_entity"))
                .entityProperty(rs.getString("plan_activity_condition_entity_property"))
                .expression(rs.getString("plan_activity_condition_expression"))
                .build();

        // Creates a new Condition list if it is not already initialized in activity object and adds the Condition object.
        if (CollectionUtils.isEmpty(activity.getConditions())) {
            List<Condition> conditionList = new ArrayList<>();
            conditionList.add(condition);
            activity.setConditions(conditionList);
        } else {
            activity.getConditions().add(condition);
        }

        conditionMap.put(condition.getId(), condition);

    }

    private void addResources(ResultSet rs, Plan planEntry, Map<String, Resource> resourceMap) throws SQLException, DataAccessException {

        String resourceId = rs.getString("plan_resource_id");

        if (ObjectUtils.isEmpty(resourceId) || resourceMap.containsKey(resourceId)) {
            List<Resource> resourceList = new ArrayList<>();
            planEntry.setResources(resourceList);
            return;
        }

        AuditDetails auditDetails = AuditDetails.builder()
                .createdBy(rs.getString("plan_resource_created_by"))
                .createdTime(rs.getLong("plan_resource_created_time"))
                .lastModifiedBy(rs.getString("plan_resource_last_modified_by"))
                .lastModifiedTime(rs.getLong("plan_resource_last_modified_time"))
                .build();

        Resource resource = Resource.builder()
                .id(rs.getString("plan_resource_id"))
                .resourceType(rs.getString("plan_resource_resource_type"))
                .estimatedNumber(rs.getBigDecimal("plan_resource_estimated_number"))
                .activityCode(rs.getString("plan_resource_activity_code"))
                .build();

        // Creates a new Resource list if it is not already initialized in planEntry and adds the Resource object.
        if (CollectionUtils.isEmpty(planEntry.getResources())) {
            List<Resource> resourceList = new ArrayList<>();
            resourceList.add(resource);
            planEntry.setResources(resourceList);
        } else {
            planEntry.getResources().add(resource);
        }

        resourceMap.put(resource.getId(), resource);

    }

    private void addTargets(ResultSet rs, Plan planEntry, Map<String, Target> targetMap) throws SQLException, DataAccessException {
        String targetId = rs.getString("plan_target_id");

        if (ObjectUtils.isEmpty(targetId) || targetMap.containsKey(targetId)) {
            List<Target> targetList = new ArrayList<>();
            planEntry.setTargets(targetList);
            return;
        }

        AuditDetails auditDetails = AuditDetails.builder()
                .createdBy(rs.getString("plan_target_created_by"))
                .createdTime(rs.getLong("plan_target_created_time"))
                .lastModifiedBy(rs.getString("plan_target_last_modified_by"))
                .lastModifiedTime(rs.getLong("plan_target_last_modified_time"))
                .build();

        MetricDetail metricDetail = MetricDetail.builder()
                .metricValue(rs.getBigDecimal("plan_target_metric_value"))
                .metricComparator(MetricDetail.MetricComparatorEnum.fromValue(rs.getString("plan_target_metric_comparator")))
                .metricUnit(rs.getString("plan_target_metric_unit"))
                .build();

        Target target = Target.builder()
                .id(targetId)
                .metric(rs.getString("plan_target_metric"))
                .metricDetail(metricDetail)
                .activityCode(rs.getString("plan_target_activity_code"))
                .build();

        // Creates a new Target list if it is not already initialized in planEntry and adds the Target object.
        if (CollectionUtils.isEmpty(planEntry.getTargets())) {
            List<Target> targetList = new ArrayList<>();
            targetList.add(target);
            planEntry.setTargets(targetList);
        } else {
            planEntry.getTargets().add(target);
        }

        targetMap.put(target.getId(), target);

    }
}
