package org.egov.repository.rowmapper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import digit.models.coremodels.AuditDetails;
import org.egov.tracer.model.CustomException;
import org.egov.web.models.AttendanceRegister;
import org.egov.web.models.Status;
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
public class RegisterRowMapper implements ResultSetExtractor<List<AttendanceRegister>> {

    @Autowired
    private ObjectMapper mapper;

    @Override
    public List<AttendanceRegister> extractData(ResultSet rs) throws SQLException, DataAccessException {

        Map<String, AttendanceRegister> attendanceRegisterMap = new LinkedHashMap<>();

        while (rs.next()) {
            String id = rs.getString("id");
            String tenantId = rs.getString("tenantid");
            String registerNumber = rs.getString("registernumber");
            String name = rs.getString("name");
            BigDecimal startDate = rs.getBigDecimal("startdate");
            BigDecimal endDate = rs.getBigDecimal("enddate");
            String status = rs.getString("status");
            String createdby = rs.getString("createdby");
            String lastmodifiedby = rs.getString("lastmodifiedby");
            Long createdtime = rs.getLong("createdtime");
            Long lastmodifiedtime = rs.getLong("lastmodifiedtime");
            String referenceId = rs.getString("referenceid");
            String serviceCode = rs.getString("servicecode");

            AuditDetails auditDetails = AuditDetails.builder().createdBy(createdby).createdTime(createdtime)
                    .lastModifiedBy(lastmodifiedby).lastModifiedTime(lastmodifiedtime)
                    .build();

            JsonNode additionalDetails = getAdditionalDetail("additionaldetails", rs);

            AttendanceRegister attendanceRegister = AttendanceRegister.builder()
                    .additionalDetails(additionalDetails)
                    .id(id)
                    .tenantId(tenantId)
                    .status(Status.fromValue(status))
                    .registerNumber(registerNumber)
                    .referenceId(referenceId)
                    .serviceCode(serviceCode)
                    .name(name)
                    .startDate(startDate)
                    .endDate(endDate)
                    .auditDetails(auditDetails)
                    .build();

            if (!attendanceRegisterMap.containsKey(id)) {
                attendanceRegisterMap.put(id, attendanceRegister);
            }
        }
        return new ArrayList<>(attendanceRegisterMap.values());

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
