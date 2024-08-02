package org.egov.project.repository.rowmapper;

import java.sql.ResultSet;
import java.sql.SQLException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import digit.models.coremodels.AuditDetails;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.core.AdditionalFields;
import org.egov.common.models.project.TaskAction;
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
public class LocationCaptureRowMapper implements RowMapper<UserAction> {

    private final ObjectMapper objectMapper;

    /**
     * Constructor for dependency injection of ObjectMapper.
     *
     * @param objectMapper The ObjectMapper used for converting JSON strings to objects.
     */
    @Autowired
    public LocationCaptureRowMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Map a single row of the ResultSet to a UserAction object.
     *
     * @param resultSet The ResultSet containing data from the database.
     * @param rowNum    The number of the current row.
     * @return A UserAction object populated with data from the current row of the ResultSet.
     * @throws SQLException If there is an issue accessing the ResultSet data.
     */
    @Override
    public UserAction mapRow(ResultSet resultSet, int rowNum) throws SQLException {

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

        UserAction locationCaptureUserAction;
        try {
            // Building the UserAction object with data from the ResultSet
            locationCaptureUserAction = UserAction.builder()
                    .id(resultSet.getString("id"))
                    .tenantId(resultSet.getString("tenantId"))
                    .clientReferenceId(resultSet.getString("clientReferenceId"))
                    .projectId(resultSet.getString("projectId"))
                    .latitude(resultSet.getDouble("latitude"))
                    .longitude(resultSet.getDouble("longitude"))
                    .locationAccuracy(resultSet.getDouble("locationAccuracy"))
                    .boundaryCode(resultSet.getString("boundaryCode"))
                    .action(TaskAction.fromValue(resultSet.getString("action")))
                    .auditDetails(auditDetails)
                    .clientAuditDetails(clientAuditDetails)
                    .additionalFields(resultSet.getString("additionalDetails") == null ? null : objectMapper
                            .readValue(resultSet.getString("additionalDetails"), AdditionalFields.class))
                    .build();
        } catch (JsonProcessingException e) {
            // Throwing a RuntimeException if there's an error processing JSON
            log.error("Error processing Additional detail JSON in Location capture UserAction ", e);
            throw new CustomException("JSON_PROCESSING_ERROR", "Error processing JSON: " + e.getMessage());
        }

        return locationCaptureUserAction;
    }
}
