package org.egov.project.repository.rowmapper;

import java.sql.ResultSet;
import java.sql.SQLException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import digit.models.coremodels.AuditDetails;
import org.egov.common.models.core.AdditionalFields;
import org.egov.common.models.project.TaskAction;
import org.egov.common.models.project.irs.LocationCapture;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

@Component
public class LocationCaptureRowMapper implements RowMapper<LocationCapture> {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * @param resultSet
     * @param rowNum
     * @return
     * @throws SQLException
     */
    @Override
    public LocationCapture mapRow(ResultSet resultSet, int rowNum) throws SQLException {

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

        LocationCapture locationCapture = null;
        try {
            locationCapture = LocationCapture.builder()
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
            throw new RuntimeException(e);
        }

        return locationCapture;
    }
}
