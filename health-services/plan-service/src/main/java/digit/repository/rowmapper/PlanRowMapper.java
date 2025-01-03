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
        Set<String> conditionSet = new HashSet<>();
        Set<String> resourceSet = new HashSet<>();
        Set<String> targetSet = new HashSet<>();

        // Traverse through result set and create plan objects
        while (rs.next()) {
            String planId = rs.getString("plan_id");

            Plan planEntry = planMap.get(planId);

            if (ObjectUtils.isEmpty(planEntry)) {
                planEntry = new Plan();
                activityMap.clear();
                conditionSet.clear();
                resourceSet.clear();
                targetSet.clear();

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

            addActivities(rs, planEntry, activityMap, conditionSet);
            addResources(rs, planEntry, resourceSet);
            addTargets(rs, planEntry, targetSet);
            planMap.put(planId, planEntry);
        }

        return new ArrayList<>(planMap.values());
    }

    private void addActivities(ResultSet rs, Plan plan,
                               Map<String, Activity> activityMap, Set<String> conditionSet) throws SQLException, DataAccessException {

        String activityId = rs.getString("plan_activity_id");

        if (!ObjectUtils.isEmpty(activityId) && activityMap.containsKey(activityId)) {
            addActivityConditions(rs, activityMap.get(activityId), conditionSet);
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

        addActivityConditions(rs, activity, conditionSet);

        if (CollectionUtils.isEmpty(plan.getActivities())) {
            List<Activity> activityList = new ArrayList<>();
            activityList.add(activity);
            plan.setActivities(activityList);
        } else {
            plan.getActivities().add(activity);
        }

        activityMap.put(activity.getId(), activity);

    }

    private void addActivityConditions(ResultSet rs, Activity activity, Set<String> conditionSet) throws SQLException, DataAccessException {
        String conditionId = rs.getString("plan_activity_condition_id");

        if (ObjectUtils.isEmpty(conditionId) || conditionSet.contains(conditionId)) {
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

        if (CollectionUtils.isEmpty(activity.getConditions())) {
            List<Condition> conditionList = new ArrayList<>();
            conditionList.add(condition);
            activity.setConditions(conditionList);
        } else {
            activity.getConditions().add(condition);
        }

        conditionSet.add(condition.getId());

    }

    private void addResources(ResultSet rs, Plan planEntry, Set<String> resourceSet) throws SQLException, DataAccessException {

        String resourceId = rs.getString("plan_resource_id");

        if (ObjectUtils.isEmpty(resourceId) || resourceSet.contains(resourceId)) {
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

        if (CollectionUtils.isEmpty(planEntry.getResources())) {
            List<Resource> resourceList = new ArrayList<>();
            resourceList.add(resource);
            planEntry.setResources(resourceList);
        } else {
            planEntry.getResources().add(resource);
        }

        resourceSet.add(resource.getId());

    }

    private void addTargets(ResultSet rs, Plan planEntry, Set<String> targetSet) throws SQLException, DataAccessException {
        String targetId = rs.getString("plan_target_id");

        if (ObjectUtils.isEmpty(targetId) || targetSet.contains(targetId)) {
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

        if (CollectionUtils.isEmpty(planEntry.getTargets())) {
            List<Target> targetList = new ArrayList<>();
            targetList.add(target);
            planEntry.setTargets(targetList);
        } else {
            planEntry.getTargets().add(target);
        }

        targetSet.add(target.getId());

    }
}
