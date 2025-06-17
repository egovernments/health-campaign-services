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

/**
 * RowMapper implementation to map rows from the `id_pool` table
 * into IdRecord model objects.
 */
@Component
public class IdRecordRowMapper implements RowMapper<IdRecord> {

    // Used to deserialize complex fields like JSONB additionalFields
    private final ObjectMapper objectMapper;

    public IdRecordRowMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Maps a single row of ResultSet to an IdRecord object.
     */
    @Override
    public IdRecord mapRow(ResultSet rs, int rowNum) throws SQLException {

        // Construct audit details from individual fields
        AuditDetails auditDetails = AuditDetails.builder()
                .createdBy(rs.getString("createdBy"))
                .createdTime(rs.getLong("createdTime"))
                .lastModifiedBy(rs.getString("lastModifiedBy"))
                .lastModifiedTime(rs.getLong("lastModifiedTime"))
                .build();

        // Handle additionalFields (typically a JSONB column)
        Object additionalFieldObject = rs.getObject("additionalFields");
        AdditionalFields additionalFields = null;
        if (additionalFieldObject != null) {
            // Convert database JSON object to AdditionalFields class
            additionalFields = objectMapper.convertValue(additionalFieldObject, AdditionalFields.class);
        }

        // Build and return the final IdRecord object
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
