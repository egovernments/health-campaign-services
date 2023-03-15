package org.egov.stock.repository.rowmapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import digit.models.coremodels.AuditDetails;
import org.egov.stock.web.models.AdditionalFields;
import org.egov.stock.web.models.StockReconciliation;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;

@Component
public class StockReconciliationRowMapper implements RowMapper<StockReconciliation> {

    private final ObjectMapper objectMapper;

    public StockReconciliationRowMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public StockReconciliation mapRow(ResultSet resultSet, int i) throws SQLException {
        try {
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
                    .auditDetails(AuditDetails.builder()
                            .createdBy(resultSet.getString("createdBy"))
                            .createdTime(resultSet.getLong("createdTime"))
                            .lastModifiedBy(resultSet.getString("lastModifiedBy"))
                            .lastModifiedTime(resultSet.getLong("lastModifiedTime"))
                            .build())
                    .build();
        } catch (JsonProcessingException e) {
            throw new SQLException(e);
        }
    }
}
