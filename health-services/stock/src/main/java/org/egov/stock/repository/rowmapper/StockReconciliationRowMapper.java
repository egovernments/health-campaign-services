package org.egov.stock.repository.rowmapper;

import java.sql.ResultSet;
import java.sql.SQLException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import digit.models.coremodels.AuditDetails;
import org.egov.common.models.core.AdditionalFields;
import org.egov.common.models.stock.StockReconciliation;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

@Component
public class StockReconciliationRowMapper implements RowMapper<StockReconciliation> {

    private final ObjectMapper objectMapper;

    public StockReconciliationRowMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public StockReconciliation mapRow(ResultSet resultSet, int i) throws SQLException {
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
            return StockReconciliation.builder()
                    .id(resultSet.getString("id"))
                    .clientReferenceId(resultSet.getString("clientReferenceId"))
                    .tenantId(resultSet.getString("tenantId"))
                    .dateOfReconciliation(resultSet.getLong("dateOfReconciliation"))
                    .facilityId(resultSet.getString("facilityId"))
                    .calculatedCount(resultSet.getInt("calculatedCount"))
                    .commentsOnReconciliation(resultSet.getString("commentsOnReconciliation"))
                    .physicalCount(resultSet.getInt("physicalRecordedCount"))
                    .productVariantId(resultSet.getString("productVariantId"))
                    .referenceId(resultSet.getString("referenceId"))
                    .referenceIdType(resultSet.getString("referenceIdType"))
                    .rowVersion(resultSet.getInt("rowVersion"))
                    .isDeleted(resultSet.getBoolean("isDeleted"))
                    .additionalFields(resultSet.getString("additionalDetails") == null ? null : objectMapper
                            .readValue(resultSet.getString("additionalDetails"), AdditionalFields.class))
                    .auditDetails(auditDetails)
                    .clientAuditDetails(clientAuditDetails)
                    .build();
        } catch (JsonProcessingException e) {
            throw new SQLException(e);
        }
    }
}
