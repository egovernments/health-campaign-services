package org.egov.stock.repository.rowmapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import digit.models.coremodels.AuditDetails;
import org.egov.common.models.stock.AdditionalFields;
import org.egov.common.models.stock.Stock;
import org.egov.common.models.stock.TransactionReason;
import org.egov.common.models.stock.TransactionType;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;

@Component
public class StockRowMapper implements RowMapper<Stock> {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Stock mapRow(ResultSet resultSet, int i) throws SQLException {
        try {
            AuditDetails auditDetails = AuditDetails.builder()
                    .createdBy(resultSet.getString("createdBy"))
                    .createdTime(resultSet.getLong("createdTime"))
                    .lastModifiedBy(resultSet.getString("lastModifiedBy"))
                    .lastModifiedTime(resultSet.getLong("lastModifiedTime"))
                    .build();
            AuditDetails clientAuditDetails = AuditDetails.builder()
                    .createdTime(resultSet.getLong("clientCreatedTime"))
                    .lastModifiedTime(resultSet.getLong("clientLastModifiedTime"))
                    .build();
            return Stock.builder()
                    .id(resultSet.getString("id"))
                    .clientReferenceId(resultSet.getString("clientReferenceId"))
                    .tenantId(resultSet.getString("tenantId"))
                    .facilityId(resultSet.getString("facilityId"))
                    .productVariantId(resultSet.getString("productVariantId"))
                    .quantity(resultSet.getInt("quantity"))
                    .wayBillNumber(resultSet.getString("wayBillNumber"))
                    .referenceId(resultSet.getString("referenceId"))
                    .referenceIdType(resultSet.getString("referenceIdType"))
                    .transactionType(TransactionType.fromValue(resultSet.getString("transactionType")))
                    .transactionReason(TransactionReason.fromValue(resultSet.getString("transactionReason")))
                    .transactingPartyId(resultSet.getString("transactingPartyId"))
                    .transactingPartyType(resultSet.getString("transactingPartyType"))
                    .additionalFields(resultSet.getString("additionalDetails") == null ? null : objectMapper
                        .readValue(resultSet.getString("additionalDetails"), AdditionalFields.class))
                    .auditDetails(auditDetails)
                    .clientAuditDetails(clientAuditDetails)
                    .rowVersion(resultSet.getInt("rowVersion"))
                    .isDeleted(resultSet.getBoolean("isDeleted"))
                    .dateOfEntry(resultSet.getLong("dateOfEntry"))
                    .build();
        } catch (JsonProcessingException e) {
            throw new SQLException(e);
        }
    }
}
