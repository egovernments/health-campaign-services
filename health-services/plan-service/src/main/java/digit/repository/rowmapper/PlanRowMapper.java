package digit.repository.rowmapper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import digit.web.models.*;
import org.egov.common.contract.models.AuditDetails;
import org.egov.tracer.model.CustomException;
import org.postgresql.util.PGobject;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

@Component
public class PlanRowMapper implements ResultSetExtractor<List<Plan>> {

    private ObjectMapper objectMapper;

    public PlanRowMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public List<Plan> extractData(ResultSet rs) throws SQLException, DataAccessException {
        Map<String, Plan> planMap = new LinkedHashMap<>();

        // Traverse through result set and create plan objects
        while(rs.next()) {
            String planId = rs.getString("plan_id");

            Plan planEntry = planMap.get(planId);

            if(ObjectUtils.isEmpty(planEntry)) {
                planEntry = new Plan();

                // Prepare audit details
                AuditDetails auditDetails = AuditDetails.builder()
                        .createdBy(rs.getString("plan_created_by"))
                        .createdTime(rs.getLong("plan_created_time"))
                        .lastModifiedBy(rs.getString("plan_last_modified_by"))
                        .lastModifiedTime(rs.getLong("plan_last_modified_time"))
                        .build();

                // Prepare plan object
                planEntry.setId(planId);
                planEntry.setTenantId(rs.getString("plan_tenant_id"));
                planEntry.setLocality(rs.getString("plan_locality"));
                planEntry.setExecutionPlanId(rs.getString("plan_execution_plan_id"));
                planEntry.setPlanConfigurationId(rs.getString("plan_plan_configuration_id"));
                planEntry.setAdditionalDetails(getAdditionalDetail((PGobject) rs.getObject("plan_additional_details")));
                planEntry.setAuditDetails(auditDetails);

            }

            addActivities(rs, planEntry);
            addResources(rs, planEntry);
            addTargets(rs, planEntry);
            planMap.put(planId, planEntry);
        }

        return new ArrayList<>(planMap.values());
    }

    private void addActivities(ResultSet rs, Plan plan) throws SQLException, DataAccessException {
        String dependencies = rs.getString("plan_activity_dependencies");
        AuditDetails auditDetails = AuditDetails.builder()
                .createdBy(rs.getString("plan_activity_created_by"))
                .createdTime(rs.getLong("plan_activity_created_time"))
                .lastModifiedBy(rs.getString("plan_activity_last_modified_by"))
                .lastModifiedTime(rs.getLong("plan_activity_last_modified_time"))
                .build();

        Activity activity = Activity.builder()
                .id(rs.getString("plan_activity_id"))
                .code(rs.getString("plan_activity_code"))
                .description(rs.getString("plan_activity_description"))
                .plannedStartDate(rs.getLong("plan_activity_planned_start_date"))
                .plannedEndDate(rs.getLong("plan_activity_planned_end_date"))
                .dependencies(ObjectUtils.isEmpty(dependencies) ? new ArrayList<>() : Arrays.asList(rs.getString("plan_activity_dependencies").split(",")))
                .build();

        addActivityConditions(rs, activity);

        if (CollectionUtils.isEmpty(plan.getActivities())) {
            List<Activity> activityList = new ArrayList<>();
            activityList.add(activity);
            plan.setActivities(activityList);
        } else {
            plan.getActivities().add(activity);
        }

    }

    private void addActivityConditions(ResultSet rs, Activity activity) throws SQLException, DataAccessException {
        AuditDetails auditDetails = AuditDetails.builder()
                .createdBy(rs.getString("plan_activity_condition_created_by"))
                .createdTime(rs.getLong("plan_activity_condition_created_time"))
                .lastModifiedBy(rs.getString("plan_activity_condition_last_modified_by"))
                .lastModifiedTime(rs.getLong("plan_activity_condition_last_modified_time"))
                .build();

        Condition condition = Condition.builder()
                .id(rs.getString("plan_activity_condition_id"))
                .entity(rs.getString("plan_activity_condition_entity"))
                .entityProperty(rs.getString("plan_activity_condition_entity_property"))
                .expression(rs.getString("plan_activity_condition_expression"))
                .build();

        if(CollectionUtils.isEmpty(activity.getConditions())){
            List<Condition> conditionList = new ArrayList<>();
            conditionList.add(condition);
            activity.setConditions(conditionList);
        } else {
            activity.getConditions().add(condition);
        }

    }

    private void addResources(ResultSet rs, Plan planEntry) throws SQLException, DataAccessException {
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

    }

    private void addTargets(ResultSet rs, Plan planEntry) throws SQLException, DataAccessException {
        AuditDetails auditDetails = AuditDetails.builder()
                .createdBy(rs.getString("plan_target_created_by"))
                .createdTime(rs.getLong("plan_target_created_time"))
                .lastModifiedBy(rs.getString("plan_target_last_modified_by"))
                .lastModifiedTime(rs.getLong("plan_target_last_modified_time"))
                .build();

        MetricDetail metricDetail = MetricDetail.builder()
                .metricValue(rs.getBigDecimal("plan_target_metric_value"))
                .metricComparator(rs.getString("plan_target_metric_comparator"))
                .metricUnit(rs.getString("plan_target_metric_unit"))
                .build();

        Target target = Target.builder()
                .id(rs.getString("plan_target_id"))
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

    }

    private JsonNode getAdditionalDetail(PGobject pGobject){
        JsonNode additionalDetail = null;

        try {
            if(ObjectUtils.isEmpty(pGobject)){
                additionalDetail = objectMapper.readTree(pGobject.getValue());
            }
        }
        catch (IOException e){
            throw new CustomException("PARSING_ERROR", "Failed to parse additionalDetails object");
        }

        return additionalDetail;
    }

}
