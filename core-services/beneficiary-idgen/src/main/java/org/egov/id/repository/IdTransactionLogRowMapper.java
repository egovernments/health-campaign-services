package org.egov.id.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.egov.common.contract.models.AuditDetails;
import org.egov.common.models.core.AdditionalFields;
import org.egov.common.models.idgen.IdTransactionLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;

@Component
public class IdTransactionLogRowMapper implements RowMapper<IdTransactionLog> {

    private final ObjectMapper objectMapper;

    public IdTransactionLogRowMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public IdTransactionLog mapRow(ResultSet rs, int rowNum) throws SQLException {

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

        return IdTransactionLog.builder()
                .id(rs.getString("id_reference"))
                .userUuid(rs.getString("user_uuid"))
                .deviceInfo(rs.getString("device_info"))
                .status(rs.getString("status"))
                .tenantId(rs.getString("tenantId"))
                .additionalFields(additionalFields)
                .deviceUuid(rs.getString("device_uuid"))
                .auditDetails(auditDetails)
                .rowVersion(rs.getInt("rowVersion"))
                .build();

    }
}