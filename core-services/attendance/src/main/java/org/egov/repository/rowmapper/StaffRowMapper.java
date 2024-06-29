package org.egov.repository.rowmapper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import digit.models.coremodels.AuditDetails;
import org.egov.tracer.model.CustomException;
import org.egov.web.models.StaffPermission;
import org.postgresql.util.PGobject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class StaffRowMapper implements ResultSetExtractor<List<StaffPermission>> {

    @Autowired
    private ObjectMapper mapper;

    @Override
    public List<StaffPermission> extractData(ResultSet rs) throws SQLException, DataAccessException {
        Map<String, StaffPermission> attendanceStaffMap = new LinkedHashMap<>();
        while (rs.next()) {
            String id = rs.getString("id");
            String tenantId = rs.getString("tenantid");
            String individuaId = rs.getString("individual_id");
            String registerId = rs.getString("register_id");
            BigDecimal enrollmentDate = rs.getBigDecimal("enrollment_date");
            BigDecimal deenrollmentDate = rs.getBigDecimal("deenrollment_date");
            String createdby = rs.getString("createdby");
            String lastmodifiedby = rs.getString("lastmodifiedby");
            Long createdtime = rs.getLong("createdtime");
            Long lastmodifiedtime = rs.getLong("lastmodifiedtime");

            AuditDetails auditDetails = AuditDetails.builder().createdBy(createdby).createdTime(createdtime)
                    .lastModifiedBy(lastmodifiedby).lastModifiedTime(lastmodifiedtime)
                    .build();

            JsonNode additionalDetails = getAdditionalDetail("additionaldetails", rs);

            StaffPermission attendanceStaff = StaffPermission.builder()
                    .additionalDetails(additionalDetails)
                    .id(id)
                    .tenantId(tenantId)
                    .userId(individuaId)
                    .registerId(registerId)
                    .additionalDetails(additionalDetails)
                    .enrollmentDate(enrollmentDate)
                    .denrollmentDate(deenrollmentDate)
                    .auditDetails(auditDetails)
                    .build();

            if (!attendanceStaffMap.containsKey(id)) {
                attendanceStaffMap.put(id, attendanceStaff);
            }
        }
        return new ArrayList<>(attendanceStaffMap.values());
    }


    private JsonNode getAdditionalDetail(String columnName, ResultSet rs) throws SQLException {
        JsonNode additionalDetails = null;
        try {
            PGobject obj = (PGobject) rs.getObject(columnName);
            if (obj != null) {
                additionalDetails = mapper.readTree(obj.getValue());
            }
        } catch (IOException e) {
            throw new CustomException("PARSING ERROR", "Failed to parse additionalDetail object");
        }
        if (additionalDetails.isEmpty())
            additionalDetails = null;
        return additionalDetails;
    }
}
