package org.egov.project.repository.rowmapper;

import digit.models.coremodels.AuditDetails;
import org.egov.project.web.models.TaskResource;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;

@Component
public class TaskResourceRowMapper implements RowMapper<TaskResource> {

    @Override
    public TaskResource mapRow(ResultSet resultSet, int i) throws SQLException {
        return TaskResource.builder()
                .id(resultSet.getString("id"))
                .rowVersion(resultSet.getInt("rowVersion"))
                .isDeleted(resultSet.getBoolean("isDeleted"))
                .tenantId(resultSet.getString("tenantId"))
                .clientReferenceId(resultSet.getString("clientReferenceId"))
                .taskId(resultSet.getString("taskId"))
                .taskClientReferenceId(resultSet.getString("taskClientReferenceId"))
                .productVariantId(resultSet.getString("productVariantId"))
                .quantity(resultSet.getLong("quantity"))
                .isDelivered(resultSet.getBoolean("isDelivered"))
                .deliveryComment(resultSet.getString("isDelivered"))
                .auditDetails(AuditDetails.builder()
                        .createdBy(resultSet.getString("createdBy"))
                        .createdTime(resultSet.getLong("createdTime"))
                        .lastModifiedBy(resultSet.getString("lastModifiedBy"))
                        .lastModifiedTime(resultSet.getLong("lastModifiedTime"))
                        .build())
                .build();
    }
}
