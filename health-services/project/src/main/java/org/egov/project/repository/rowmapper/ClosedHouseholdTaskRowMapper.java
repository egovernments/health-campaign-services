package org.egov.project.repository.rowmapper;

import java.sql.ResultSet;
import java.sql.SQLException;

import com.fasterxml.jackson.databind.ObjectMapper;
import digit.models.coremodels.AuditDetails;
import org.egov.common.models.project.Task;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

@Component
public class ClosedHouseholdTaskRowMapper implements RowMapper<Task> {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * @param resultSet
     * @param rowNum
     * @return
     * @throws SQLException
     */
    @Override
    public Task mapRow(ResultSet resultSet, int rowNum) throws SQLException {
        AuditDetails auditDetails = AuditDetails.builder()
                .createdBy(resultSet.getString("createdBy"))
                .createdTime(resultSet.getLong("createdTime"))
                .lastModifiedBy(resultSet.getString("lastModifiedBy"))
                .lastModifiedTime(resultSet.getLong("lastModifiedTime"))
                .build();
        AuditDetails clientAuditDetails = AuditDetails.builder()
                .createdTime(resultSet.getLong("clientCreatedTime"))
                .createdBy(resultSet.getString("clientCreatedBy"))
                .lastModifiedTime(resultSet.getLong("clientLastModifiedTime"))
                .lastModifiedBy(resultSet.getString("clientLastModifiedBy"))
                .build();
        return null;
    }
}
