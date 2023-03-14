package org.egov.project.repository.rowmapper;

import digit.models.coremodels.AuditDetails;
import org.egov.common.models.project.TaskResource;
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
                .isDeleted(resultSet.getBoolean("isDeleted"))
                .tenantId(resultSet.getString("tenantId"))
                .taskId(resultSet.getString("taskId"))
                .productVariantId(resultSet.getString("productVariantId"))
                .quantity(resultSet.getLong("quantity"))
                .isDelivered(resultSet.getBoolean("isDelivered"))
                .deliveryComment(resultSet.getString("isDelivered"))
                .clientReferenceId(resultSet.getString("clientReferenceId"))
                .auditDetails(AuditDetails.builder()
                        .createdBy(resultSet.getString("createdBy"))
                        .createdTime(resultSet.getLong("createdTime"))
                        .lastModifiedBy(resultSet.getString("lastModifiedBy"))
                        .lastModifiedTime(resultSet.getLong("lastModifiedTime"))
                        .build())
                .build();
    }
}
