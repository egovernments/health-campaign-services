package digit.repository.rowmapper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import digit.web.models.PlanFacility;
import org.egov.common.contract.models.AuditDetails;
import org.egov.tracer.model.CustomException;
import org.postgresql.util.PGobject;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

@Component
public class PlanFacilityRowMapper implements ResultSetExtractor<List<PlanFacility>> {

    private final ObjectMapper objectMapper;

    public PlanFacilityRowMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public List<PlanFacility> extractData(ResultSet rs) throws SQLException, DataAccessException {
        Map<String, PlanFacility> planFacilityMap = new LinkedHashMap<>();
        while (rs.next()) {
            String planFacilityId = rs.getString("plan_facility_id");

            PlanFacility planFacilityEntry = planFacilityMap.get(planFacilityId);
            if (planFacilityEntry == null || ObjectUtils.isEmpty(planFacilityEntry)) {
                planFacilityEntry = new PlanFacility();

                // Prepare audit details
                AuditDetails auditDetails = AuditDetails.builder()
                        .createdBy(rs.getString("plan_facility_created_by"))
                        .createdTime(rs.getLong("plan_facility_created_time"))
                        .lastModifiedBy(rs.getString("plan_facility_last_modified_by"))
                        .lastModifiedTime(rs.getLong("plan_facility_last_modified_time"))
                        .build();

                // Prepare plan facility object
                planFacilityEntry.setId(planFacilityId);
                planFacilityEntry.setTenantId(rs.getString("plan_facility_tenant_id"));
                planFacilityEntry.setPlanConfigurationId(rs.getString("plan_facility_plan_configuration_id"));
                planFacilityEntry.setPlanConfigurationName(rs.getString("plan_facility_plan_configuration_name"));
                planFacilityEntry.setFacilityId(rs.getString("plan_facility_facility_id"));
                planFacilityEntry.setFacilityName(rs.getString("plan_facility_facility_name"));
                planFacilityEntry.setResidingBoundary(rs.getString("plan_facility_residing_boundary"));
                String serviceBoundaries = rs.getString("plan_facility_service_boundaries");
                planFacilityEntry.setServiceBoundaries(ObjectUtils.isEmpty(serviceBoundaries) ? new ArrayList<>() : Arrays.asList(serviceBoundaries.split(",")));
                planFacilityEntry.setAdditionalDetails(getAdditionalDetail((PGobject) rs.getObject("plan_facility_additional_details")));
                planFacilityEntry.setAuditDetails(auditDetails);
                planFacilityEntry.setActive(rs.getBoolean("plan_facility_active"));
            }

            planFacilityMap.put(planFacilityId, planFacilityEntry);
        }
        return new ArrayList<>(planFacilityMap.values());
    }

    private JsonNode getAdditionalDetail(PGobject pGobject) {
        JsonNode additionalDetail = null;

        try {
            if (!ObjectUtils.isEmpty(pGobject)) {
                additionalDetail = objectMapper.readTree(pGobject.getValue());
            }
        } catch (IOException e) {
            throw new CustomException("PARSING_ERROR", "Failed to parse additionalDetails object");
        }

        return additionalDetail;
    }
}
