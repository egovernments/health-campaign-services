package digit.repository.rowmapper;

import digit.util.QueryUtil;
import digit.web.models.PlanEmployeeAssignment;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.models.AuditDetails;
import org.postgresql.util.PGobject;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

@Slf4j
@Component
public class PlanEmployeeAssignmentRowMapper implements ResultSetExtractor<List<PlanEmployeeAssignment>> {

    private QueryUtil queryUtil;

    public PlanEmployeeAssignmentRowMapper(QueryUtil queryUtil) {
        this.queryUtil = queryUtil;
    }

    @Override
    public List<PlanEmployeeAssignment> extractData(ResultSet rs) throws SQLException, DataAccessException {
        Map<String, PlanEmployeeAssignment> planEmployeeAssignmentMap = new LinkedHashMap<>();

        while (rs.next()) {
            String planEmployeeAssignmentId = rs.getString("id");

            PlanEmployeeAssignment planEmployeeAssignment = planEmployeeAssignmentMap.get(planEmployeeAssignmentId);

            if (ObjectUtils.isEmpty(planEmployeeAssignment)) {
                planEmployeeAssignment = new PlanEmployeeAssignment();

                // Prepare audit details
                AuditDetails auditDetails = AuditDetails.builder().createdBy(rs.getString("created_by")).createdTime(rs.getLong("created_time")).lastModifiedBy(rs.getString("last_modified_by")).lastModifiedTime(rs.getLong("last_modified_time")).build();

                // Converting jurisdiction from comma separated string to a list of string
                String jurisdiction = rs.getString("jurisdiction");
                List<String> jurisdictionList = Arrays.asList(jurisdiction.split(","));

                // Prepare PlanEmployeeAssignment object
                planEmployeeAssignment.setId(planEmployeeAssignmentId);
                planEmployeeAssignment.setTenantId(rs.getString("tenant_id"));
                planEmployeeAssignment.setPlanConfigurationId(rs.getString("plan_configuration_id"));
                planEmployeeAssignment.setEmployeeId(rs.getString("employee_id"));
                planEmployeeAssignment.setRole(rs.getString("role"));
                planEmployeeAssignment.setJurisdiction(jurisdictionList);
                planEmployeeAssignment.setActive(rs.getBoolean("active"));
                planEmployeeAssignment.setAdditionalDetails(queryUtil.getAdditionalDetail((PGobject) rs.getObject("additional_details")));
                planEmployeeAssignment.setAuditDetails(auditDetails);

                planEmployeeAssignmentMap.put(planEmployeeAssignmentId, planEmployeeAssignment);
            }
        }

        return new ArrayList<>(planEmployeeAssignmentMap.values());
    }
}
