package org.egov.project.repository.rowmapper;

import java.sql.ResultSet;
import java.sql.SQLException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import digit.models.coremodels.AuditDetails;
import org.egov.common.models.core.AdditionalFields;
import org.egov.common.models.project.TaskAction;
import org.egov.common.models.project.irs.UserAction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

@Component
public class UserActionRowMapper implements RowMapper<UserAction> {

    private final ObjectMapper objectMapper;

    @Autowired
    public UserActionRowMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * @param resultSet
     * @param rowNum
     * @return
     * @throws SQLException
     */
    @Override
    public UserAction mapRow(ResultSet resultSet, int rowNum) throws SQLException {
        try {
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

            UserAction userAction = UserAction.builder()
                    .id(resultSet.getString("id"))
                    .tenantId(resultSet.getString("tenantId"))
                    .clientReferenceId(resultSet.getString("clientReferenceId"))
                    .projectId(resultSet.getString("projectId"))
                    .latitude(resultSet.getDouble("latitude"))
                    .longitude(resultSet.getDouble("longitude"))
                    .locationAccuracy(resultSet.getDouble("locationAccuracy"))
                    .boundaryCode(resultSet.getString("boundaryCode"))
                    .action(TaskAction.fromValue(resultSet.getString("action")))
                    .status(resultSet.getString("status"))
                    .action(TaskAction.fromValue(resultSet.getString("action")))
                    .beneficiaryTag(resultSet.getString("beneficiaryTag"))
                    .resourceTag(resultSet.getString("resourceTag"))
                    .rowVersion(resultSet.getInt("rowVersion"))
                    .auditDetails(auditDetails)
                    .clientAuditDetails(clientAuditDetails)
                    .additionalFields(resultSet.getString("additionalDetails") == null ? null : objectMapper
                            .readValue(resultSet.getString("additionalDetails"), AdditionalFields.class))
                    .build();
            return userAction;
        } catch (JsonProcessingException e) {
            throw new SQLException(e);
        }
    }
}
