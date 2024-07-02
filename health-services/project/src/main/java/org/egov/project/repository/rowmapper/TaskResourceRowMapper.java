package org.egov.project.repository.rowmapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import digit.models.coremodels.AuditDetails;
import org.egov.common.models.core.AdditionalFields;
import org.egov.common.models.project.TaskResource;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;

@Component
public class TaskResourceRowMapper implements RowMapper<TaskResource> {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public TaskResource mapRow(ResultSet resultSet, int i) throws SQLException {
        try {
            return TaskResource.builder()
                    .id(resultSet.getString("id"))
                    .isDeleted(resultSet.getBoolean("isDeleted"))
                    .tenantId(resultSet.getString("tenantId"))
                    .taskId(resultSet.getString("taskId"))
                    .productVariantId(resultSet.getString("productVariantId"))
                    .quantity(resultSet.getDouble("quantity"))
                    .isDelivered(resultSet.getBoolean("isDelivered"))
                    .deliveryComment(resultSet.getString("reasonIfNotDelivered"))
                    .clientReferenceId(resultSet.getString("clientReferenceId"))
                    .auditDetails(AuditDetails.builder()
                            .createdBy(resultSet.getString("createdBy"))
                            .createdTime(resultSet.getLong("createdTime"))
                            .lastModifiedBy(resultSet.getString("lastModifiedBy"))
                            .lastModifiedTime(resultSet.getLong("lastModifiedTime"))
                            .build())
                    .additionalFields(resultSet.getString("additionalDetails") == null ? null : objectMapper
                            .readValue(resultSet.getString("additionalDetails"), AdditionalFields.class))
                    .build();
        } catch (
                JsonProcessingException e) {
            throw new SQLException(e);
        }
    }
}
