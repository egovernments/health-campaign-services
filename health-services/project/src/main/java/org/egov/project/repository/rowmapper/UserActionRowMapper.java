package org.egov.project.repository.rowmapper;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.egov.common.contract.models.AuditDetails;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.core.AdditionalFields;
import org.egov.common.models.project.UserActionEnum;
import org.egov.common.models.project.useraction.UserAction;
import org.egov.tracer.model.CustomException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

/**
 * RowMapper implementation for mapping rows of a ResultSet to UserAction objects.
 * This class is used to map the result of a SQL query to UserAction instances.
 */
@Component
@Slf4j
public class UserActionRowMapper implements RowMapper<UserAction> {

    private final ObjectMapper objectMapper;

    /**
     * Constructor for dependency injection of ObjectMapper.
     *
     * @param objectMapper The ObjectMapper used for converting JSON strings to objects.
     */
    @Autowired
    public UserActionRowMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Map a single row of the ResultSet to a UserAction object.
     *
     * @param resultSet The ResultSet containing data from the database.
     * @param rowNum    The number of the current row.
     * @return A UserAction object populated with data from the current row of the ResultSet.
     * @throws SQLException If there is an issue accessing the ResultSet data or processing JSON.
     */
    @Override
    public UserAction mapRow(ResultSet resultSet, int rowNum) throws SQLException {
        try {
            // Creating AuditDetails object with information from the ResultSet
            AuditDetails auditDetails = AuditDetails.builder()
                    .createdBy(resultSet.getString("createdBy"))
                    .createdTime(resultSet.getLong("createdTime"))
                    .lastModifiedBy(resultSet.getString("lastModifiedBy"))
                    .lastModifiedTime(resultSet.getLong("lastModifiedTime"))
                    .build();

            // Creating client-specific AuditDetails object with information from the ResultSet
            AuditDetails clientAuditDetails = AuditDetails.builder()
                    .createdTime(resultSet.getLong("clientCreatedTime"))
                    .createdBy(resultSet.getString("clientCreatedBy"))
                    .lastModifiedTime(resultSet.getLong("clientLastModifiedTime"))
                    .lastModifiedBy(resultSet.getString("clientLastModifiedBy"))
                    .build();

            // Building the UserAction object with data from the ResultSet
            UserAction userAction = UserAction.builder()
                    .id(resultSet.getString("id"))
                    .tenantId(resultSet.getString("tenantId"))
                    .clientReferenceId(resultSet.getString("clientReferenceId"))
                    .projectId(resultSet.getString("projectId"))
                    .latitude(resultSet.getDouble("latitude"))
                    .longitude(resultSet.getDouble("longitude"))
                    .locationAccuracy(resultSet.getDouble("locationAccuracy"))
                    .boundaryCode(resultSet.getString("boundaryCode"))
                    .action(UserActionEnum.fromValue(resultSet.getString("action")))
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
            String id = resultSet.getString("id");
            String errorMessage = "Error processing JSON for UserAction mapping. Row number: " + rowNum + ", id: " + (id != null ? id : "not available");
            log.error(errorMessage, e);
            throw new CustomException("HCM_PROJECT_USER_ACTION_ROW_MAPPER_INVALID_ERROR", errorMessage + ", " + e.getMessage());
        }
    }
}
