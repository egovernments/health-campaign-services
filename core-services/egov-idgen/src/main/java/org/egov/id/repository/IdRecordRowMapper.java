package org.egov.id.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.egov.common.contract.models.AuditDetails;
import org.egov.common.models.core.AdditionalFields;
import org.egov.common.models.idgen.IdRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;
import java.sql.ResultSet;
import java.sql.SQLException;

@Component
public class IdRecordRowMapper implements RowMapper<IdRecord> {

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public IdRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
        AuditDetails auditDetails = AuditDetails.builder()
                .createdBy(rs.getString("createdBy"))
                .createdTime(rs.getLong("createdTime"))
                .lastModifiedBy(rs.getString("lastModifiedBy"))
                .lastModifiedTime(rs.getLong("lastModifiedTime"))
                .build();

        Object additionalFieldObject = rs.getObject("additionalFields");
        AdditionalFields additionalFields = null;
        if (additionalFieldObject != null) {
            additionalFields = objectMapper.convertValue(additionalFieldObject, AdditionalFields.class);
        }

        return IdRecord
                .builder()
                .id(rs.getString("id"))
                .status(rs.getString("status"))
                .tenantId(rs.getString("tenantId"))
                .rowVersion(rs.getInt("rowVersion"))
                .additionalFields(additionalFields)
                .auditDetails(auditDetails)
                .build();
    }
}
